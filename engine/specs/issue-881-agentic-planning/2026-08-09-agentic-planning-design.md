# Agentic Planning — Engine-Hosted Pattern Execution

**Epic:** engine#881
**Children:** #882 (re-planning), #883 (checkpointing), #884 (constraints), #886 (backends)
**Depends on:** engine#802 (hierarchical planning — landed), blocks#60 Phase 5 (HTN SPI — landed), engine#823–826 (scoped worker lifecycle — landed)

## Problem

The agentic pattern infrastructure (blocks `io.casehub.blocks.agentic`) has a rich
builder DSL (`debate().debaters(a,b,c).judge(j).build()`) and a five-strategy
composition model (routing × decomposition × activation × aggregation × termination).
But execution is in-process only — `OrchestratedDriver` runs a while loop in memory
with no persistence, no recovery, no re-planning on failure, and no constraint
awareness.

Without durability and adaptive planning, the DSL looks like "just another workflow
DSL." The value proposition is the composition model — but it needs an execution
layer that provides production-grade durability without rebuilding what the engine
already provides.

## Architecture

**Core principle:** Blocks owns the DSL and composition model. The engine owns
execution and durability. The `WorkerFunctionHandler` SPI is the interface.

```
┌─ Blocks (DSL layer) ─────────────────────────────┐
│  debate().debaters(a,b,c).judge(j).build()        │
│  → ExecutionModel (5 SPIs + config)               │
│  → ExecutionBackend selects runtime               │
└───────────────────────────────────────────────────┘
         │                           │
    reactive()                  engineHosted()
         │                           │
    ┌────▼────┐              ┌───────▼───────────────────┐
    │ In-proc │              │ Engine Worker Boundary     │
    │ Driver  │              │  ┌─────────────────────┐  │
    │ (tests, │              │  │ PatternWorkerFn     │  │
    │  atomic)│              │  │  → Driver.execute() │  │
    │         │              │  │  → EngineInvoker    │  │
    └─────────┘              │  │    → WorkerRuntime  │  │
                             │  └─────────────────────┘  │
                             │  PlanItem lifecycle        │
                             │  EventLog audit            │
                             │  Retry via RetryPolicies   │
                             │  Recovery on restart       │
                             └───────────────────────────┘
```

### Why this architecture

Four options were evaluated from first principles:

1. **Blocks owns execution** — requires rebuilding persistence, retry, recovery in
   blocks. Duplicates engine infrastructure. AgentInvoker can't dispatch to engine
   workers without heavy coupling.

2. **Engine owns execution (blocks compiles to engine primitives)** — impedance
   mismatch between blocks' iterative 5-SPI model and engine's reactive binding
   dispatch. Aggregation (MajorityVote, CollectAll) has no engine equivalent.
   Modeling DEBATE as compounds + bindings is painfully verbose.

3. **Clean split by pattern shape** — correct insight (workflow-shaped vs agentic
   patterns have different needs) but incomplete: doesn't specify how agentic
   patterns get durability without custom checkpointing.

4. **Engine as host, blocks driver as worker (chosen)** — the engine provides the
   durability envelope. The blocks driver runs inside a worker function. Sub-agents
   dispatch via `WorkerRuntime.execute()` through the full engine pipeline. No
   duplication, minimal changes to both layers, existing tests unchanged.

### Module: casehub-engine-agentic

New optional module (directory: `agentic-engine/`). Follows the same pattern as
`casehub-engine-flow` — activated by adding it to the consumer's classpath.

**Compile dependencies:**
- `casehub-blocks` (ExecutionModel, drivers, AgentRef)
- `casehub-engine-common` (WorkerFunctionHandler SPI, WorkerExecutor)
- `casehub-engine-api` (WorkerFunction, DecompositionStrategy)
- `casehub-worker-api`

No dependency on `casehub-engine` (runtime) — same constraint as flow/a2a/mcp
modules.

## Phase 1: Configurable Execution Backends (#886)

### ExecutionBackend extension

`ExecutionBackend` already exists as a `@FunctionalInterface` in blocks with a
single method: `Uni<ExecutionResult> execute(ExecutionModel<T>, T)`.

**New factories:**
- `ExecutionBackend.reactive()` — renamed from `orchestrated()`. In-process,
  immediate. For tests and atomic single-shot patterns. `orchestrated()` stays as
  a deprecated alias.
- `ExecutionBackend.engineHosted()` — wraps `ExecutionModel` in
  `PatternWorkerFunction`, dispatches through the engine's worker pipeline.

**Auto-selection:** `AbstractPatternBuilder.execute()` currently defaults to
`new OrchestratedDriver<>()` when no backend is set. New default: check if
`EngineHostedBackendProvider` is discoverable via `ServiceLoader`. If present
(engine-agentic module on classpath), use engine-hosted. If absent, fall back to
reactive. This means:
- Blocks standalone (tests, no engine) → reactive automatically
- Inside engine runtime (production) → engine-hosted automatically
- Explicit `.backend()` call overrides either

Builder API:
```java
Patterns.debate()
    .debaters(a, b, c)
    .judge(j)
    .maxRounds(5)
    .backend(ExecutionBackend.engineHosted())
    .execute(initialContext);
```

### PatternWorkerFunction

Record implementing `WorkerFunction<Map, Map>`. Holds the `ExecutionModel`
reference (constructed at definition build time, not serialized).

```java
public record PatternWorkerFunction(
    ExecutionModel<?> model,
    PatternType patternType,
    boolean checkpointingEnabled
) implements WorkerFunction<Map, Map> {
    @Override public Class<Map> inputType() { return Map.class; }
    @Override public Class<Map> outputType() { return Map.class; }
}
```

### PatternWorkerFunctionHandler

`@ApplicationScoped`, implements `WorkerFunctionHandler`. Runs the driver on
`@VirtualThreads ExecutorService` with timeout enforcement.

```java
@ApplicationScoped
public class PatternWorkerFunctionHandler implements WorkerFunctionHandler {

    @Override
    public boolean supports(WorkerFunction<?, ?> function) {
        return function instanceof PatternWorkerFunction;
    }

    @Override
    public HandlerResult execute(WorkerFunction<?, ?> function, Object inputData,
                                  WorkerScope scope) {
        var patternFn = (PatternWorkerFunction) function;
        var invoker = new EngineAgentInvoker<>(scope);
        var driver = new OrchestratedDriver<>(invoker);
        var result = driver.execute(patternFn.model(), inputData)
            .await().atMost(timeout);
        return toHandlerResult(result, patternFn.patternType());
    }
}
```

### EngineAgentInvoker

Bridges `AgentRef` to the engine's worker dispatch via `WorkerScope`:

| AgentRef variant | Dispatch path |
|-----------------|---------------|
| `ExternalAgent` | Call function directly (no engine overhead) |
| `WorkerAgent` | `scope.execute(worker.name(), input)` |
| `ComposedAgent` | Recursive via same backend |
| `ChannelAgent` | Cast to `WorkerRuntime` → channel post via `WorkerContext` |
| `HumanAgent` | Cast to `WorkerRuntime` → WorkItem creation via `spawnCase` |

`ExternalAgent` stays direct — it's a lambda, no retry/audit needed, and it
preserves blocks' independent testability.

`ChannelAgent` and `HumanAgent` require `WorkerRuntime` (the engine-specific
extension of `WorkerScope`). The invoker casts and fails fast with a clear
error if the scope is not a `WorkerRuntime` — these variants are only valid
in engine-hosted mode.

### PatternWorkerFunctionProvider

`@ApplicationScoped`, implements `WorkerFunctionProvider`. Detects `pattern:`
YAML blocks on worker definitions, constructs `PatternWorkerFunction`.

YAML schema:
```yaml
workers:
  - name: analyst-debate
    capabilities: [analysis]
    pattern:
      type: debate
      maxRounds: 5
      agents: [analyst-a, analyst-b, analyst-c]
      judge: senior-analyst
      backend: engine-hosted
```

### CaseDefinition integration

`CaseDefinition` gains `executionBackend` (nullable String). Resolved via
`EngineStrategyResolver`. YAML: `executionBackend:` under `spec:`. When null,
auto-select per the `ServiceLoader` mechanism.

## Phase 2: Planning Under Constraints (#884)

### PlanningConstraints

New record in `engine-api` (`io.casehub.engine.plan`):

```java
public record PlanningConstraints(
    Duration timeBudget,
    Integer resourceLimit,
    Map<String, Double> weights
) {
    public static PlanningConstraints unconstrained() {
        return new PlanningConstraints(null, null, Map.of());
    }
}
```

v1 scope: `timeBudget` (max duration for the pattern execution) and
`resourceLimit` (max concurrent agents per iteration). Cost budget (LLM tokens,
API quotas) deferred — requires token tracking infrastructure.

### DecompositionContext extension

```java
public interface DecompositionContext<T> {
    T state();
    int depth();
    default PlanningConstraints constraints() {
        return PlanningConstraints.unconstrained();
    }
}
```

Default method — backward compatible. `GoalDecompositionContext` carries
constraints from the `CaseDefinition`.

### LLM prompt integration

`LlmDecompositionStrategy` includes constraints in the user prompt when non-null:

> "You have 30 minutes and 3 available agents. Decompose this goal into steps
> that can complete within these constraints. Prefer parallelism when resource
> limits allow."

### Driver-side enforcement

`PatternWorkerFunctionHandler` tracks constraint consumption during execution:

- **Time budget:** Checks elapsed time before each iteration. If exceeded, the
  driver terminates with `ExecutionResult.Failed("time budget exceeded")`. The
  remaining time is threaded to `EngineAgentInvoker`, which passes it as the
  worker timeout — agents dispatched late get proportionally less time.
- **Resource limit:** Caps concurrent agent dispatch within an iteration. If the
  routing strategy selects more agents than `resourceLimit`, only the first N are
  dispatched; the rest are deferred to the next iteration.

### CaseDefinition config

```yaml
spec:
  planningConstraints:
    timeBudget: PT30M
    resourceLimit: 3
```

Builder: `definition.planningConstraints(PlanningConstraints.of(Duration.ofMinutes(30), 3))`.

### Module placement

`PlanningConstraints` in `engine-api` (consumers inspect it, plan-definition
type per PP-20260727-5267d2). Driver enforcement in `casehub-engine-agentic`
(execution infrastructure).

## Phase 3: Dynamic Re-Planning on Step Failure (#882)

### New failure action: REPLAN

Added to `FailurePolicy` in blocks. When a step fails and all retries are
exhausted, instead of FAIL/ESCALATE/RETRY_BROADER, the driver calls the
decomposition strategy to produce a revised plan for the remaining work.

Re-planning triggers *after* retries are exhausted. Sequence: agent fails →
retry N times (per `ExecutionPolicy`) → all retries exhausted → consult
`FailurePolicy` → REPLAN.

### DecompositionStrategy.replan()

Default method on the SPI interface — opt-in:

```java
default Uni<DagPlan<LeafTask<T>>> replan(
        TaskNode<T> task,
        DecompositionContext<T> context,
        ReplanContext<T> replanContext) {
    return Uni.createFrom().failure(
        new UnsupportedOperationException("Re-planning not supported by " + id()));
}
```

### ReplanContext

New record in `engine-api`:

```java
public record ReplanContext<T>(
    List<CompletedStep<T>> completedSteps,
    FailedStep<T> failedStep,
    DagPlan<LeafTask<T>> originalPlan,
    int replanCount
) {}
```

- `CompletedStep<T>` — step ID, result, elapsed time. These are inputs to the
  replanner (it knows what succeeded) but never re-executed.
- `FailedStep<T>` — step ID, error message, exception cause, retry attempt count.

### Scoped re-planning

The replanner produces a plan for the *remaining* work only. Completed steps are
preserved — the driver splices the new plan into execution. Completed step states
stay terminal. The failed step and all downstream steps are replaced by the new
plan.

### Driver integration

`AbstractExecutionDriver.executeIteration()` gains a new branch in the failure
handling phase:

1. Agent returns failure result
2. Consult `FailurePolicy`: if action is `REPLAN`:
   a. Build `ReplanContext` from completed results + failed step
   b. Call `decomposition.replan(task, context, replanContext)`
   c. If replan succeeds: replace remaining plan, reset iteration for new steps,
      continue loop
   d. If replan fails: fall back to `replanFallback` action (configurable,
      default FAIL)
3. Guard: `maxReplans` on `FailurePolicy` (default 2). Prevents infinite replan
   loops.

### LlmDecompositionStrategy.replan()

LLM-backed implementation:

- System prompt: "You are revising a plan after a step failure. Produce a revised
  plan that achieves the same goal using the remaining capabilities."
- User prompt includes: original goal, completed steps with results, failed step
  with error, available capabilities, current constraints
- Response schema: same as `decompose()` — `{ "steps": [...] }`
- The LLM can reorder remaining steps, substitute capabilities, add intermediate
  steps, or skip optional steps

### YAML configuration

```yaml
spec:
  failurePolicy:
    onAgentFailure: replan
    maxReplans: 2
    replanFallback: escalate
```

## Phase 4: Partial Plan Execution with Checkpointing (#883)

### Phase 4a (MVP): No checkpointing

Pattern runs as a worker function. If the process crashes, the engine's retry
infrastructure re-dispatches the worker. The driver re-executes from the start.

Acceptable for short-lived patterns (single-shot VOTING, shallow PARALLEL).

### Phase 4b: Iteration-level checkpointing

#### Checkpoint granularity

Per iteration. After each complete iteration of the driver loop, the driver
persists its state. Within an iteration, agent dispatches go through
`WorkerScope.execute()` (synchronous) — if the process crashes mid-iteration,
the engine's idempotent dispatch prevents duplicate work.

#### PatternExecutionCheckpoint

New record in `engine-common`:

```java
public record PatternExecutionCheckpoint(
    UUID caseId,
    String patternId,
    int completedIterations,
    List<AgentResultRecord> results,
    Set<String> excludedAgents,
    DagPlanSnapshot currentPlan,
    int replanCount,
    Map<String, Object> driverState
) {}
```

`AgentResultRecord` is the serializable projection of blocks' `AgentResult` —
agent ID, output (as `JsonNode`), success/failure, duration. No function
references or lambdas.

#### Storage: EventLog-backed

Checkpoints serialize as EventLog metadata with a `PATTERN_CHECKPOINT` event
type. No new JPA entity, no new table.

On recovery, the handler queries for the latest `PATTERN_CHECKPOINT` event for
the case/pattern.

The existing `InMemoryExecutionSnapshotStore` stays as `@DefaultBean` for
observability. Checkpointing uses the EventLog directly — it's the durable
audit trail that already exists.

#### Driver checkpoint protocol

```
Iteration N completes
  → Driver serializes state to PatternExecutionCheckpoint
  → Writes PATTERN_CHECKPOINT EventLog entry
  → Continues to iteration N+1
```

#### Recovery protocol

```
Worker retry after crash
  → PatternWorkerFunctionHandler.execute()
  → Query EventLog for latest PATTERN_CHECKPOINT for this case/pattern
  → If found:
      → Reconstruct driver state from checkpoint
      → Skip iterations 0..N (already completed)
      → Resume from iteration N+1
  → If not found:
      → Start from scratch (Phase 4a behavior)
```

#### Idempotent agent dispatch

On recovery, the driver skips completed iterations entirely. For the iteration
that was in-flight during the crash, agents may have already been dispatched.
The engine's existing idempotent dispatch (PlanItem deduplication) prevents
double-execution.

#### PERSISTENT lifecycle scope

Patterns expected to run for hours (DEBATE with human judges, SUPERVISOR with
escalation chains) use `LifecycleScope.PERSISTENT` + `ExecutionMode.PERSISTENT`.
The driver runs on a virtual thread, waits for external events via the
`PersistentScope` mailbox, and `ScopedWorkerRegistry` tracks the session.

Checkpointing still happens at iteration boundaries — the mailbox handles the
wait, checkpointing handles the crash.

#### CaseDefinition config

```yaml
workers:
  - name: analyst-debate
    pattern:
      type: debate
      maxRounds: 5
      agents: [analyst-a, analyst-b, analyst-c]
      judge: senior-analyst
      checkpointing: true
    lifecycleScope: case
    executionMode: persistent
```

#### What is NOT checkpointed (deliberate)

- In-flight agent execution state — agents are full engine workers with their own
  retry/recovery
- LLM conversation history — the LLM is called fresh on each replan, with
  completed steps as context
- The `ExecutionModel` itself — reconstructed from `CaseDefinition` at recovery
  time, not serialized

## Testing

### Unit tests (blocks, unchanged)

Existing blocks tests continue to use `ExternalAgent` + `reactive()` backend.
No modifications needed. These test the DSL, composition model, and driver
logic in isolation.

### Unit tests (new, casehub-engine-agentic)

1. **`PatternWorkerFunctionHandlerTest`** — handler supports PatternWorkerFunction,
   runs driver, returns HandlerResult with pattern metadata.
2. **`EngineAgentInvokerTest`** — dispatch table: ExternalAgent direct,
   WorkerAgent via scope, ComposedAgent recursive.
3. **`PatternWorkerFunctionProviderTest`** — detects `pattern:` YAML, constructs
   PatternWorkerFunction with correct config.

### Unit tests (re-planning, blocks)

4. **`ReplanContextTest`** — record validation, completed/failed step construction.
5. **`ReplanDriverTest`** — driver calls replan on REPLAN action, splices new plan,
   respects maxReplans guard, falls back on replan failure.
6. **`LlmReplanTest`** — LLM receives failure context, returns revised plan,
   handles LLM errors gracefully.

### Unit tests (constraints, engine-api)

7. **`PlanningConstraintsTest`** — record validation, unconstrained factory.
8. **`ConstraintEnforcementTest`** — driver terminates on time budget exceeded,
   caps agent dispatch on resource limit.

### Unit tests (checkpointing, engine-common)

9. **`PatternExecutionCheckpointTest`** — serialization round-trip, AgentResultRecord
   projection from AgentResult.
10. **`CheckpointRecoveryTest`** — driver resumes from checkpoint, skips completed
    iterations, handles missing checkpoint (fresh start).

### Integration tests (@QuarkusTest, casehub-engine-agentic)

11. **`PatternExecutionIntegrationTest`** — full flow: case with pattern worker →
    start → driver runs → agents dispatch via engine pipeline → result.
    Mock `ChatModelProvider` with canned responses. Uses
    `casehub-persistence-memory`.
12. **`ReplanIntegrationTest`** — agent fails → retries exhausted → replan fires →
    revised plan executes → case completes.
13. **`CheckpointRecoveryIntegrationTest`** — pattern starts → checkpoint written →
    simulate crash (kill handler) → retry fires → resumes from checkpoint.

## Scope Boundaries

**In scope:**
- `casehub-engine-agentic` module with PatternWorkerFunction, handler, invoker, provider
- `ExecutionBackend.reactive()` rename + `engineHosted()` factory
- Auto-selection via ServiceLoader
- `PlanningConstraints` on `DecompositionContext`
- Driver-side time budget and resource limit enforcement
- `REPLAN` failure action + `DecompositionStrategy.replan()`
- `ReplanContext` and `LlmDecompositionStrategy.replan()`
- `PatternExecutionCheckpoint` with EventLog storage
- Driver checkpoint/recovery protocol
- YAML `pattern:` block on worker definitions

**v1 limitations (deliberate):**
- Cost budget constraints deferred (requires token tracking infrastructure)
- SWF compilation for workflow-shaped patterns deferred (`ExecutionBackend.swf()`)
- Only `OrchestratedDriver` supported in engine-hosted mode (ChoreographedDriver
  needs full event-bus integration per its own deferred-work comment)
- Checkpointing is opt-in per pattern (`checkpointing: true`)
- No cross-pattern coordination (each pattern is an independent worker)

**Out of scope (future work):**
- `ExecutionBackend.swf()` — compile workflow-shaped patterns to SWF definitions
- ChoreographedDriver engine integration (requires Vert.x EventBus bridging)
- Cost budget constraints and LLM token tracking
- Cross-pattern coordination and multi-case orchestration
- Pattern observability REST endpoints (extends existing PlanResource)
- Wiring blocks' `FailurePolicy.AgentRetryPolicy` (backoff strategies defined
  but not connected in the driver — separate from engine-level retry)

## Implementation Phases

| Phase | Issue | Deliverables | Dependencies |
|-------|-------|-------------|--------------|
| 1 | #886 | `casehub-engine-agentic` module, `PatternWorkerFunction`, handler, invoker, provider, `ExecutionBackend.engineHosted()`, auto-selection, YAML | None |
| 2 | #884 | `PlanningConstraints`, `DecompositionContext.constraints()`, LLM prompt, driver enforcement | Phase 1 |
| 3 | #882 | `REPLAN` action, `replan()` SPI method, `ReplanContext`, `LlmDecompositionStrategy.replan()`, `maxReplans` | Phase 1 |
| 4 | #883 | `PatternExecutionCheckpoint`, EventLog storage, checkpoint/recovery protocol, PERSISTENT scope config | Phase 1 |

Phases 2 and 3 are independent of each other. Phase 4 depends on Phase 1 only.

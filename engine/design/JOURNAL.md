# Design Journal — issue-881-agentic-planning

## 2026-08-09 — Architecture decision: engine-hosted pattern execution

**Decision:** Blocks DSL produces ExecutionModel; engine provides durability via
WorkerFunctionHandler SPI boundary. New `casehub-engine-agentic` module bridges
the two.

**Four options evaluated from first principles:**
1. Blocks owns execution — rejected (duplicates engine persistence/retry/recovery)
2. Engine owns execution (DSL compiles to primitives) — rejected (impedance mismatch:
   blocks' iterative 5-SPI model vs engine's reactive binding dispatch; no aggregation
   equivalent in engine)
3. Clean split by pattern shape — partially right (workflow vs agentic distinction is
   real) but incomplete (doesn't specify how agentic patterns get durability)
4. **Engine as host, blocks driver as worker** — chosen. OrchestratedDriver runs inside
   PatternWorkerFunctionHandler. EngineAgentInvoker bridges AgentRef to
   WorkerRuntime.execute(). ExternalAgent stays direct (no engine overhead).

**Key design review findings (light, 3 dimensions + cross-cutting, 38 issues):**
- WorkerFunctionHandler.execute() takes (WorkerContext, timeoutMs, ExecutionMetadata),
  not WorkerScope — handler creates its own WorkerRuntime via WorkerRuntimeFactory
- Re-planning scoped to HTN patterns only — iteration-based driver has no "plan" to revise
- ReplanPolicy is a separate field on FailurePolicy, not a new RoutingFailureAction
- Engine retry handles per-agent retry; blocks replan handles plan-level recovery
- Checkpoint driverState uses string keys (AgentRef not serializable)
- WorkerRuntime.execute() has no deduplication — mid-iteration crash re-runs the iteration

**Phase 1 (#886) implemented:** PatternWorkerFunction, PatternWorkerFunctionHandler,
EngineAgentInvoker, PatternWorkerFunctionProvider, EngineHostedBackend + ServiceLoader.
21 tests (5 unit classes + 1 integration), all green.

## 2026-08-10 — Phase 2: Planning under constraints (#884)

**Decision:** `PlanningConstraints` is a plan-definition type in `engine-api` (per
PP-20260727-5267d2), not execution infrastructure. Enforcement is split: decomposition
strategies receive constraints via `DecompositionContext.constraints()` (informational —
LLM prompt guidance); `PatternWorkerFunctionHandler` enforces constraints at runtime
(time budget via timeout, resource limit via routing wrapper).

**Enforcement approach — two levels discovered during implementation:**
- **Time budget:** Reduces effective timeout passed to `Future.get()`. Required moving
  driver execution to a virtual thread — `Uni.createFrom().item()` runs synchronously
  on the subscriber thread, so `atMost()` cannot interrupt it (garden entry
  GE-20260810-07a4ac). `CompletableFuture.supplyAsync()` also failed in Quarkus due to
  ForkJoinPool classloader issues (GE-20260810-ee9b0c). Solution:
  `Executors.newVirtualThreadPerTaskExecutor()` + `Future.get(timeout)`.
- **Resource limit:** Wraps the `ExecutionModel`'s routing strategy — `Selected.agents()`
  truncated to `resourceLimit`. Used record reconstruction to decorate the immutable
  `ExecutionModel` (technique GE-20260810-06aee1).

**Phase 2 implemented:** PlanningConstraints record, DecompositionContext.constraints()
default method, GoalDecompositionContext constraint threading, LlmDecompositionStrategy
prompt integration, PatternWorkerFunctionHandler enforcement, YAML parsing
(spec.planningConstraints + pattern.constraints). 7 commits, 1378 tests green across
api/planning/agentic-engine.

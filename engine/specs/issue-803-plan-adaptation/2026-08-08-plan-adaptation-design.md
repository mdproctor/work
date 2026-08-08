# Plan Adaptation — Revise Active Plans Based on New Observations

**Issue:** engine#803
**Epic:** engine#800 (Sub-epic B — Agent Reflection & Planning)
**Depends on:** engine#802 (hierarchical planning — landed)

## Problem

`GoalDecomposer` decomposes agent goals into `DagPlan<LeafTask>` at case start
via `LlmDecompositionStrategy`. Plans are materialized as compound
`PlanItemDefinition`s and executed by the existing planning dispatch loop
(CompoundStrategyDispatcher + CHOREOGRAPHED). But once materialized, plans are
static — new observations (worker outcomes, context changes, reflection insights)
may invalidate assumptions the plan was based on. Agents need to detect when
their current plan is no longer valid and revise it, keeping completed steps and
replacing pending ones.

## Design Principles

1. **Orthogonal composable foundations.** Trigger evaluation (should we replan?)
   and plan revision (what should the new plan be?) are genuinely independent
   dimensions. Separate SPIs, separately configurable, independently testable.
2. **DSL wrappers for common presets.** YAML supports both explicit two-field
   config and string shorthands for common combinations.
3. **Worker-outcome driven.** v1 triggers after worker completions within
   decomposed compounds. Future triggers (reflection, signals) use the same SPI
   without changes.
4. **Shared materialization.** Initial decomposition and plan revision share the
   same plan materialization logic — extracted into `PlanMaterializer`.

## Architecture

### Lifecycle phases

Plan adaptation is "plan monitoring and repair" — four phases:

1. **Monitor** — detects that a step completed (infrastructure, always runs)
2. **Evaluate** — given what happened, is the current plan still valid?
   (configurable `AdaptationTrigger`)
3. **Revise** — produce a replacement plan for remaining work (configurable
   `PlanRevisionStrategy`)
4. **Apply** — materialise the revision in the runtime (infrastructure, fixed)

Phases 1 and 4 are engine infrastructure. Phases 2 and 3 are the orthogonal
configurable strategies.

### Call site

```
Worker completes within decomposed compound
  → PlanItemCompletionHandler marks PlanItem COMPLETED (success path)
    OR WorkerRetryExhaustionHandler marks PlanItem FAULTED (failure path)
  → PlanAdaptationEvaluator.evaluateAdaptation(...)   ← NEW (both paths)
      1. Is this step in a decomposed compound? (parentCompoundId != null)
      2. Acquire per-compound ReentrantLock
      3. Build AdaptationContext (completed steps + outputs, pending, running)
      4. Resolve AdaptationTrigger → evaluate(context) → AdaptationSignal
      5. If Skip → release lock, return
      6. Resolve PlanRevisionStrategy → revise(revisionContext) → RevisedPlan
      7. Apply: obsolete pending, materialise new steps
      8. Write PLAN_ADAPTED EventLog
      9. Release lock
  → CompoundCompletionEvaluator evaluates (with potentially revised children)
  → CONTEXT_CHANGED published
```

Adaptation fires AFTER step status change but BEFORE compound completion
evaluation. This ensures the compound evaluates the revised plan, not the stale
one. Both success and failure paths trigger adaptation — `OnFailureTrigger`
relies on the failure path; `EveryStepTrigger` fires on both.

## Foundation SPIs (engine-api)

### AdaptationTrigger

```java
public interface AdaptationTrigger extends NamedStrategy {
    AdaptationSignal evaluate(AdaptationContext context);

    @Override
    default String id() { return "every-step"; }
}
```

**`AdaptationSignal`** — sealed interface:
- `Proceed(AdaptationCause cause)` — adaptation warranted
- `Skip` — current plan is still valid

**`AdaptationCause`** — sealed interface capturing what triggered adaptation:
- `StepCompleted(String stepId, String capabilityName, Map<String, Object> output)`
  — normal completion with new information
- `StepFailed(String stepId, String reason)` — step failed, plan assumption
  violated

Extension points for future triggers (not implemented in v1):
- `ContextDiverged(Set<String> changedKeys)` — external context change
- `InsightReceived(String insight)` — reflection produced insight

**`AdaptationContext`** — record carrying evaluation state:

```java
public record AdaptationContext(
    UUID caseId,
    String tenancyId,
    String compoundId,
    String goalName,
    List<CompletedStep> completedSteps,
    List<GoalStep> pendingSteps,
    List<GoalStep> runningSteps,
    JsonNode currentContext,
    CaseDefinition definition,
    TaskStatus latestStatus,
    String latestBindingName
)
```

**`CompletedStep`** — record for completed step history:

```java
public record CompletedStep(
    String stepId,
    String capabilityName,
    String description,
    Map<String, Object> output,
    Instant completedAt
)
```

### PlanRevisionStrategy

```java
public interface PlanRevisionStrategy extends NamedStrategy {
    Uni<RevisedPlan> revise(RevisionContext context);

    @Override
    default String id() { return "forward-replan"; }
}
```

**`RevisionContext`** — extends `AdaptationContext` with revision-specific fields:

```java
public record RevisionContext(
    AdaptationContext adaptationContext,
    AdaptationCause cause,
    List<Capability> capabilities,
    List<RetrievedMemory> memories
)
```

**`RevisedPlan`** — record carrying the revision result:

```java
public record RevisedPlan(
    List<GoalStep> steps,
    String rationale
)
```

`steps` is the complete forward plan — all pending steps to materialise. The
orchestrator marks ALL existing pending steps OBSOLETE and creates new ones from
`steps`. No diffing — full replacement is correct, simple, and avoids edge
cases. The "waste" of recreating equivalent steps is negligible (PlanItem
records, not running work).

`rationale` is the LLM's explanation for the revision (nullable, for audit).

### Relationship to DecompositionStrategy

`PlanRevisionStrategy` does NOT delegate to `DecompositionStrategy`. They are
separate concerns with different prompt requirements:

- **DecompositionStrategy**: "Given a goal and capabilities, plan from scratch"
- **PlanRevisionStrategy**: "Given completed steps and their outputs, revised
  context, and remaining capabilities, plan the remaining work"

`ForwardReplanRevision` constructs its own prompt and interacts with
`ChatModelProvider` directly. This avoids coupling revision to decomposition
prompt construction.

## Built-in Strategy Implementations (planning module)

### Triggers

**`EveryStepTrigger`** (id=`"every-step"`, default)
Always returns `Proceed` after any step completion. Maximum responsiveness,
every step outcome is evaluated for potential plan revision.

**`OnFailureTrigger`** (id=`"on-failure"`)
Returns `Proceed` only when the latest outcome is non-success
(`Declined`/`Failed`/`Expired`). Returns `Skip` on success. More conservative —
only replans when something went wrong.

### Revision strategies

**`ForwardReplanRevision`** (id=`"forward-replan"`, default)
Re-invokes LLM with completed step history + current context + available
capabilities. Asks for remaining steps only. Prompt structure:

```
System: "You are a planning assistant. A plan is in progress. Some steps have
completed. Given the current state and remaining capabilities, produce an
updated plan for the remaining work. Each step must reference exactly one
capability."

User: "Goal: {goalName}
Completed steps:
  1. {step.description} → Output: {step.output}
  2. ...
Current context: {contextSnapshot}
Available capabilities: {capabilities}
Produce the remaining steps as a JSON 'steps' array."
```

Response parsing reuses the same JSON structure as `LlmDecompositionStrategy`
(steps array with id, description, capabilityName, dependsOn). Creates
`GoalStep` instances from parsed response.

Injected dependencies:
- `Instance<ChatModelProvider>` — transparent no-op when absent
- Returns `Uni.createFrom().failure()` when no ChatModelProvider available

## Shared Infrastructure

### PlanMaterializer (planning module)

Extracted from `DefaultGoalDecomposer.decomposeGoal()` lines 193–225. Shared
utility for both initial decomposition and adaptation.

```java
@ApplicationScoped
public class PlanMaterializer {

    @Inject PlanItemStore planItemStore;

    public void materialise(
        UUID caseId, String tenancyId, String goalName,
        PlanItemDefinition.Compound compound,
        List<GoalStep> steps,
        CasePlanModel casePlanModel) { ... }

    public List<String> obsoletePending(
        UUID caseId, String tenancyId, String compoundId,
        CasePlanModel casePlanModel) { ... }
}
```

**`materialise()`:** Creates `PlanItemDefinition.Primitive` per step, registers
them as children of the compound via `casePlanModel.addChild()`, saves
PlanItems via `planItemStore.save()`. Uses the compound's existing registration
— does NOT re-register the compound itself.

**`obsoletePending()`:** Finds all PlanItems under the compound that are
PENDING or AVAILABLE (not COMPLETED, RUNNING, or terminal). Marks them OBSOLETE
via `planItemStore.updateStatus()`. Removes them from `CasePlanModel` via
`removePlanItem()`. Returns the list of obsoleted planItemIds (for audit).

**Refactoring `DefaultGoalDecomposer`:** After extracting `PlanMaterializer`,
`DefaultGoalDecomposer.decomposeGoal()` delegates to
`planMaterializer.materialise()` for the compound creation + PlanItem save
step. The validation, LLM call, and scope resolution logic remain in
`GoalDecomposer`.

## Orchestrator — PlanAdaptationEvaluator

### SPI (common/spi/)

```java
public interface PlanAdaptationEvaluator {
    void evaluateAdaptation(
        CaseInstance instance,
        CaseDefinition definition,
        MutableCaseContext context,
        String completedBindingName,
        TaskStatus completedStatus);
}
```

Follows the `GoalDecomposer` pattern: SPI in common, parameters use only
common/api types, implementation in planning. `TaskStatus` replaces
`WorkerOutcome<?>` — both `PlanItemCompletionHandler` (success) and
`WorkerRetryExhaustionHandler` (failure) know the terminal status. The
evaluator reconstructs failure reason from EventLog metadata when building
`AdaptationCause.StepFailed`.

### Implementation — DefaultPlanAdaptationEvaluator (planning module)

`@ApplicationScoped`, injected dependencies:
- `EngineStrategyResolver` — resolves trigger + revision strategies
- `BlackboardRegistry` — access CasePlanModel
- `PlanItemStore` — query PlanItems, update status
- `EventLogRepository` — query completed step outputs, write audit
- `PlanMaterializer` — shared materialisation
- `Instance<AgentMemoryRetriever>` — optional memory retrieval
- `GoalAbandonmentEvaluator` — active goal filtering

**Per-compound serialization:** `ConcurrentHashMap<String, ReentrantLock>`
keyed by `compoundId`. Prevents concurrent adaptations when two steps in the
same compound complete near-simultaneously. Lock is acquired before context
building and released in a finally block.

**In-flight step handling:** When building `AdaptationContext`, separates steps
into completed / pending / running based on PlanItem status. RUNNING steps are
included in context (so the LLM knows they're in progress) but never marked
OBSOLETE. When a running step later completes, it triggers another adaptation
cycle.

**Completed step output reconstruction:** Queries `EventLogRepository` for
`WORKER_EXECUTION_COMPLETED` events matching the compound's binding names.
Extracts output from EventLog payload. Same approach as
`CbrCaseRetainObserver` for plan trace reconstruction.

**Timeout:** Reuses `casehub.engine.decomposition.timeout-ms` (default 30000).
Per-revision — one timeout doesn't block other compounds. Graceful degradation:
on timeout or failure, existing plan continues unmodified, warning logged.

**Idempotency:** The per-compound lock serializes evaluations. After acquiring
the lock, re-checks whether the triggering step is still relevant (hasn't been
obsoleted by a concurrent adaptation that completed just before lock
acquisition).

### Call site integration

`PlanItemCompletionHandler` (planning module) — after marking PlanItem
COMPLETED, before calling `CompoundCompletionEvaluator`:

```java
// Existing: mark PlanItem COMPLETED
planItemStore.updateStatus(planItemId, TaskStatus.COMPLETED, tenancyId);

// NEW: evaluate adaptation if step is in a decomposed compound
if (planAdaptationEvaluator.isResolvable()) {
    planAdaptationEvaluator.get().evaluateAdaptation(
        caseInstance, definition, context, bindingName, outcome);
}

// Existing: evaluate compound completion
completionEvaluator.evaluate(caseId, tenancyId, plan, changedItemId);
```

The adaptation evaluator is injected via `Instance<PlanAdaptationEvaluator>` —
transparent no-op when the planning module is absent.

## CaseDefinition Configuration

`CaseDefinition` gains:
- `adaptationTrigger` (String, nullable) — strategy ID for `AdaptationTrigger`
- `planRevisionStrategy` (String, nullable) — strategy ID for
  `PlanRevisionStrategy`

Both nullable — null means adaptation is disabled.

Builder:
```java
CaseDefinition.builder()
    .decompositionStrategy("llm")
    .adaptationTrigger("every-step")
    .planRevisionStrategy("forward-replan")
    .build();
```

### YAML — explicit configuration

```yaml
spec:
  decompositionStrategy: llm
  adaptation:
    trigger: every-step
    revision: forward-replan
```

### YAML — preset shorthands

```yaml
spec:
  decompositionStrategy: llm
  adaptation: adaptive
```

Presets:
- `adaptive` → trigger: `every-step` + revision: `forward-replan`
- `conservative` → trigger: `on-failure` + revision: `forward-replan`
- `off` or absent → no adaptation

`CaseDefinitionYamlMapper` detects whether `adaptation` is a string (preset) or
object (explicit config). Missing `trigger` or `revision` fields in explicit
config fall back to defaults (`every-step` and `forward-replan` respectively).

### Validation

- `adaptation` without `decompositionStrategy` → warning log (adaptation
  requires initial decomposition to produce a compound to adapt)
- Unknown preset name → build-time `IllegalArgumentException`
- Absent `adaptation` → no adaptation (default, opt-in)

## EventLog Audit

New event type: `CaseHubEventType.PLAN_ADAPTED`

Metadata:
- `goalName` — which goal's plan was revised
- `compoundId` — which compound was adapted
- `triggerStrategy` — which trigger strategy fired (e.g., `"every-step"`)
- `cause` — structured cause (e.g., `{type: "StepCompleted", stepId: "...",
  capabilityName: "..."}`)
- `revisionStrategy` — which revision strategy produced the new plan
- `previousStepCount` — number of pending steps before revision
- `newStepCount` — number of new steps materialised
- `obsoletedSteps` — list of planItemIds marked OBSOLETE
- `materializedSteps` — list of new planItemIds created
- `rationale` — LLM's explanation for the revision (nullable)

## Module Placement

| Type | Module | Rationale (PP-20260727-5267d2) |
|------|--------|-------------------------------|
| `AdaptationTrigger` | engine-api | Consumer-implementable NamedStrategy SPI |
| `PlanRevisionStrategy` | engine-api | Consumer-implementable NamedStrategy SPI |
| `AdaptationSignal`, `AdaptationCause` | engine-api | SPI result types |
| `AdaptationContext`, `CompletedStep` | engine-api | SPI parameter types |
| `RevisionContext`, `RevisedPlan` | engine-api | SPI parameter/result types |
| `PlanAdaptationEvaluator` (SPI) | common/spi | Cross-module SPI |
| `DefaultPlanAdaptationEvaluator` | planning | Execution infrastructure |
| `PlanMaterializer` | planning | Shared internal utility |
| `EveryStepTrigger` | planning | Built-in strategy |
| `OnFailureTrigger` | planning | Built-in strategy |
| `ForwardReplanRevision` | planning | Built-in strategy |
| `PLAN_ADAPTED` event type | common | Event constant |

**`EngineStrategyResolver` update required:** Adding `AdaptationTrigger` and
`PlanRevisionStrategy` as new strategy SPI types requires updating
`EngineStrategyResolver`'s constructor with per-domain `Instance<>` injection
(per GE-20260704-d6aacc). Two new injected fields and resolution branches.

## Testing

### Unit tests

1. **`AdaptationTriggerTest`** — tests for each trigger:
   - `EveryStepTrigger` always returns `Proceed` with correct cause type
   - `OnFailureTrigger` returns `Proceed` only for non-success outcomes
   - `OnFailureTrigger` returns `Skip` for success outcomes
   - Both produce correct `AdaptationCause` variant

2. **`ForwardReplanRevisionTest`** — LLM interaction:
   - Produces RevisedPlan from structured response
   - Includes completed step history in prompt
   - Handles empty response (no steps needed)
   - No-op when ChatModelProvider absent
   - Invalid JSON → Uni failure
   - Unknown capabilities filtered (same as LlmDecompositionStrategy)

3. **`PlanMaterializerTest`** — shared materialisation:
   - `materialise()` creates PlanItemDefinitions and PlanItems correctly
   - `obsoletePending()` marks only PENDING/AVAILABLE items OBSOLETE
   - `obsoletePending()` leaves COMPLETED and RUNNING items untouched
   - `obsoletePending()` removes items from CasePlanModel

4. **`DefaultPlanAdaptationEvaluatorTest`** — orchestrator logic:
   - Skips when completedBindingName is not in a decomposed compound
   - Skips when adaptation not configured (no trigger/revision on definition)
   - Calls trigger → Skip → no revision called
   - Calls trigger → Proceed → calls revision → applies result
   - Obsoletes pending steps before materialising new ones
   - Leaves RUNNING steps untouched during adaptation
   - Writes PLAN_ADAPTED EventLog with correct metadata
   - Per-compound lock prevents concurrent adaptation
   - Timeout → existing plan continues, warning logged
   - Exception isolation → existing plan continues, warning logged
   - Re-checks step relevance after acquiring lock (idempotency)

5. **`CaseDefinitionYamlMapperTest`** — YAML parsing:
   - Parses explicit adaptation config (trigger + revision)
   - Parses preset shorthand (`adaptation: adaptive`)
   - Parses preset shorthand (`adaptation: conservative`)
   - Missing adaptation → null fields
   - Adaptation without decompositionStrategy → warning
   - Unknown preset → error
   - Partial explicit config → defaults for missing fields

6. **`AdaptationContextTest`**, **`CompletedStepTest`**, **`RevisedPlanTest`**:
   - Record validation, null checks, immutability

### Integration test

7. **`PlanAdaptationIntegrationTest`** (`@QuarkusTest`):
   - Full flow: case with goals + LLM strategy + adaptation → start → step
     completes → adaptation fires → pending steps replaced → new steps execute
   - Mock `ChatModelProvider` with canned JSON (initial plan + revised plan)
   - EventLog contains both `GOAL_DECOMPOSED` and `PLAN_ADAPTED`
   - Compound completes when revised plan finishes
   - Verify OBSOLETE steps are not re-dispatched

8. **`GoalDecomposerRefactoringTest`** — regression:
   - Existing GoalDecomposer tests still pass after PlanMaterializer extraction
   - Same PlanItems created, same EventLog entries

## Scope Boundaries

**In scope:**
- `AdaptationTrigger` + `PlanRevisionStrategy` SPIs (engine-api)
- `PlanAdaptationEvaluator` SPI + `DefaultPlanAdaptationEvaluator` (common + planning)
- `EveryStepTrigger`, `OnFailureTrigger` (planning)
- `ForwardReplanRevision` (planning)
- `PlanMaterializer` extraction from GoalDecomposer (planning)
- CaseDefinition config + YAML + presets
- EventLog audit (`PLAN_ADAPTED`)
- Per-compound locking
- In-flight step handling

**v1 constraints (deliberate):**
- Linear chain plans only (same as #802)
- Single adaptation per compound per step completion (serialized)
- Full pending-step replacement (no diffing/merging)
- No adaptation during initial decomposition

**Out of scope (future work):**
- `ContextDiffTrigger` — adapt based on context key changes
- `IncrementalRevision` — LLM returns targeted modifications
- `FullReplanRevision` — LLM replans everything, engine diffs
- Reflection-triggered adaptation (#808 may drive this)
- Parallel plan adaptation (non-linear compounds)
- Adaptation cost budgeting (limit LLM calls per case)
- Adaptation history visualization / REST endpoints

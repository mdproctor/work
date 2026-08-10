# Goal Revision — Modify Goal Parameters Based on Outcomes

**Issue:** engine#806
**Epic:** engine#800 (Sub-epic C — Goal Lifecycle Management)
**Depends on:** eidos#101 (goal querying — landed), neocortex#184 (agent experience stream — landed)

## Problem

Agent goals (`AgentGoal` on `AgentDescriptor`) are static once declared. As
agents accumulate experience — successful completions and failures across their
capabilities — goal descriptions and priorities should be revisable. A SECONDARY
goal with consistently high success rates should be promoted. A PRIMARY goal
with persistent failures should be demoted. Goal descriptions should be
refinable to better capture what the agent actually accomplishes.

The existing goal lifecycle infrastructure tracks signals but never acts on
them beyond abandonment:
- `GoalFailureRecorder` records DECLINE signals per goal on failure (but not
  SUCCESS signals on success)
- `GoalAbandonmentEvaluator` drops goals that exceed a DECLINE threshold
- `AgentGoalCompletionMarker` writes binary completion flags to case context
- `AgentExperienceRecorder` records structured outcome events and triggers
  reflection

Nobody computes goal effectiveness ratios. Nobody proposes or applies changes
to goal parameters.

## Design

### Architecture

Evaluator-per-completion pattern — `GoalRevisionEvaluator` is called from
`WorkflowExecutionCompletedHandler` on every worker completion (both success
and failure paths), same as the other recorders. It accumulates per-agent
outcome state internally and evaluates revision when configurable thresholds
are met. Priority adjustment is rule-based (deterministic threshold
comparison). Description refinement delegates to a `GoalRevisionStrategy` SPI
(LLM-backed default). Changes are applied via `AgentRegistry.register()` with
an updated `AgentDescriptor`.

```
Worker completes (success or failure)
  -> WorkflowExecutionCompletedHandler
    -> GoalOutcomeRecorder.record()           [RENAMED from GoalFailureRecorder]
        records SUCCESS or DECLINE per goal in BehavioralSignalStore
    -> GoalRevisionEvaluator.record()          [NEW]
        accumulates per-agent outcome count + importance
        when threshold met -> spawn virtual thread:
          1. Query BehavioralSignalStore -> per-goal success/failure counts
          2. Compute GoalEffectivenessMetrics per goal
          3. Priority adjustment: rule-based threshold comparison
          4. Description refinement: delegate to GoalRevisionStrategy SPI
          5. Build updated AgentDescriptor via toBuilder() + modified goals
          6. AgentRegistry.register(updatedDescriptor)
          7. Write GOAL_REVISED EventLog
```

### Trigger mechanism

Per-agent threshold tracking using `ConcurrentHashMap<String, RevisionState>`
keyed by `agentId|tenancyId` — same pattern as
`AgentExperienceRecorder.ReflectionState`. Counters accumulate on every worker
completion. When either `outcomeCount >= config.minOutcomes()` or
`cumulativeImportance >= config.importanceThreshold()`, counters reset and
evaluation runs on a virtual thread.

Thresholds are runtime-configurable via `GoalRevisionConfig` on
`CaseDefinition` with sensible defaults.

## Components

### GoalOutcomeRecorder (rename from GoalFailureRecorder)

`runtime/internal/routing/`, `@ApplicationScoped`. Generalization of
`GoalFailureRecorder` to record both SUCCESS and DECLINE signals per goal.

**Changes from GoalFailureRecorder:**
1. Rename class, field in `WorkflowExecutionCompletedHandler`, test class
2. Remove early return on Success — instead map to `BehavioralSignal.SUCCESS`
3. Signal mapping:
   - `WorkerOutcome.Success` / `WorkerOutcome.Completed` ->
     `BehavioralSignal.SUCCESS`
   - `WorkerOutcome.Declined` / `WorkerOutcome.Failed` /
     `WorkerOutcome.Expired` -> `BehavioralSignal.DECLINE`
4. Add call on success path in `WorkflowExecutionCompletedHandler` (currently
   only called on failure path)

Everything else unchanged: `__goal__` sentinel capability, per-goal capability
filtering, `BehavioralSignalStore` interaction. `GoalAbandonmentEvaluator`
continues to work unchanged — it only queries DECLINE counts.

### GoalRevisionEvaluator (new)

`runtime/internal/routing/`, `@ApplicationScoped`.

**Injected dependencies:**
- `Instance<AgentRegistry>` — read current descriptor, write updated one
- `Instance<BehavioralSignalStore>` — query per-goal outcome counts
- `CaseDefinitionRegistry` — resolve CaseDefinition, look up AgentDescriptor
- `EngineStrategyResolver` — resolve GoalRevisionStrategy by ID
- `EventLogRepository` — write GOAL_REVISED audit entries
- `CaseInstanceRepository` — resolve CaseInstance for EventLog

**Method:** `record(CaseInstance, String workerName, String capabilityName,
WorkerOutcome<?>, String bindingName)`

**Internal state:**

```java
private static class RevisionState {
    int outcomeCount;
    double cumulativeImportance;
    Instant lastRevisionTime;
}
```

**Trigger evaluation** (inside `ConcurrentHashMap.compute()` — atomic):
1. Increment outcomeCount and cumulativeImportance
2. If `outcomeCount >= config.minOutcomes()` OR
   `cumulativeImportance >= config.importanceThreshold()` -> trigger
3. Reset counters, record lastRevisionTime
4. Spawn virtual thread for evaluation

**Evaluation logic** (on virtual thread):
1. `agentRegistry.findById(agentId, tenancyId)` -> current AgentDescriptor
2. For each goal on the descriptor:
   - Query `signalStore.count(agentId, tenancyId, "__goal__", goalName,
     SUCCESS)` and same for DECLINE
   - Build `GoalEffectivenessMetrics(goalName, successCount, failureCount)`
3. **Priority adjustment** (rule-based, always runs):
   - SECONDARY goal with `successRate > config.promotionSuccessRate()` ->
     promote to PRIMARY
   - PRIMARY goal with `failureRate > config.demotionFailureRate()` -> demote
     to SECONDARY
   - Minimum outcome guard: skip goals with < `config.minOutcomes()` total
     outcomes in the signal store
4. **Description refinement** (if strategy configured):
   - Build `GoalRevisionContext` with metrics, outcome descriptions, case
     context, definition
   - Call `strategy.revise(context)` -> `GoalRevisionProposal`
   - Extract proposed description changes
5. **Merge:** rule-based priority takes precedence over strategy-proposed
   priority when both propose a change (deterministic wins over LLM)
6. **Apply** — if any changes: build modified goals list using
   `goal.toBuilder().priority(newPriority).description(newDescription).build()`,
   create updated descriptor via `descriptor.toBuilder().goals(updatedGoals)
   .build()`, call `agentRegistry.register(updatedDescriptor)`
7. **Audit** — write GOAL_REVISED EventLog with per-goal revision metadata

**Importance weights:** Reuses `ReflectionTriggerConfig.DEFAULT_IMPORTANCE_WEIGHTS`
(SUCCESS->0.3, DECLINED->0.6, FAILED->0.8, EXPIRED->0.5) or reads from
`GoalRevisionConfig` if overridden.

**Concurrency:** Per-agent `ReentrantLock` via `ConcurrentHashMap<String,
ReentrantLock>` keyed by `agentId|tenancyId`. Prevents concurrent revision
for the same agent when multiple workers complete near-simultaneously.
Acquired before AgentRegistry read, released in finally block.

**Guard rails:**
- `isResolvable()` guard on `AgentRegistry` and `BehavioralSignalStore` —
  transparent no-op when absent
- All exceptions caught and logged — never blocks case progression
- `GoalRevisionConfig` null on CaseDefinition -> return early (disabled by
  default)
- Strategy failure -> priority-only revision still applies

### GoalRevisionStrategy SPI (engine-api)

`io.casehub.api.spi.goal`, extends `NamedStrategy`.

```java
public interface GoalRevisionStrategy extends NamedStrategy {
    Uni<GoalRevisionProposal> revise(GoalRevisionContext context);

    @Override
    default String id() { return "llm"; }
}
```

**GoalRevisionContext** — what the strategy receives:

```java
public record GoalRevisionContext(
    String agentId,
    String tenancyId,
    AgentGoal goal,
    GoalEffectivenessMetrics metrics,
    List<String> recentOutcomeDescriptions,
    JsonNode currentContext,
    CaseDefinition definition
)
```

`recentOutcomeDescriptions` — human-readable summaries of recent outcomes for
this goal's capabilities. Gives the LLM concrete outcome history to reason
about.

**GoalEffectivenessMetrics** — per-goal metrics:

```java
public record GoalEffectivenessMetrics(
    String goalName,
    int successCount,
    int failureCount
) {
    public int totalOutcomes() { return successCount + failureCount; }
    public double successRate() {
        return totalOutcomes() == 0 ? 0.0 : (double) successCount / totalOutcomes();
    }
    public double failureRate() {
        return totalOutcomes() == 0 ? 0.0 : (double) failureCount / totalOutcomes();
    }
}
```

**GoalRevisionProposal** — what the strategy returns:

```java
public record GoalRevisionProposal(
    List<RevisedGoal> revisions,
    String rationale
) {
    public record RevisedGoal(
        String goalName,
        String revisedDescription,    // nullable - null means no change
        GoalPriority revisedPriority, // nullable - null means no change
        String revisionReason
    ) {}
}
```

### LlmGoalRevisionStrategy (runtime)

`runtime/internal/routing/`, `@ApplicationScoped`, id=`"llm"`.

**Injected:** `Instance<ChatModelProvider>` — transparent no-op when absent.

**Prompt construction:**
- System: "You are a goal effectiveness analyst. Given an agent's goal, its
  performance metrics, and recent outcomes, evaluate whether the goal
  description should be refined to better capture what the agent accomplishes.
  Only propose changes when the current description is meaningfully
  misaligned with observed outcomes."
- User: goal name, description, priority, effectiveness metrics (success rate,
  failure rate, total outcomes), recent outcome descriptions
- Response schema (enforced via structured output):

```json
{
  "type": "object",
  "properties": {
    "revisions": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "goalName": { "type": "string" },
          "revisedDescription": { "type": ["string", "null"] },
          "revisedPriority": { "type": ["string", "null"] },
          "revisionReason": { "type": "string" }
        },
        "required": ["goalName", "revisionReason"]
      }
    },
    "rationale": { "type": "string" }
  },
  "required": ["revisions", "rationale"]
}
```

**Error handling:**
- ChatModelProvider absent -> `Uni.createFrom().failure(new
  UnsupportedOperationException(...))`
- Invalid JSON -> `Uni.createFrom().failure(new AgentException(...))`
- Empty revisions -> valid result (no changes needed)

## Configuration

### GoalRevisionConfig (engine-api)

`io.casehub.api.model`, per-case configuration on `CaseDefinition`.

```java
public record GoalRevisionConfig(
    boolean enabled,
    String strategy,                  // GoalRevisionStrategy ID, nullable -> default "llm"
    int minOutcomes,                  // minimum outcomes before evaluation per goal
    double importanceThreshold,       // cumulative importance to trigger evaluation
    double promotionSuccessRate,      // SECONDARY -> PRIMARY when successRate exceeds this
    double demotionFailureRate        // PRIMARY -> SECONDARY when failureRate exceeds this
) {
    public static final int DEFAULT_MIN_OUTCOMES = 10;
    public static final double DEFAULT_IMPORTANCE_THRESHOLD = 3.0;
    public static final double DEFAULT_PROMOTION_RATE = 0.8;
    public static final double DEFAULT_DEMOTION_RATE = 0.7;
}
```

**CaseDefinition** gains `goalRevisionConfig` (nullable `GoalRevisionConfig`).
Null means disabled. Builder: `.goalRevisionConfig(new GoalRevisionConfig(...))`.

### YAML

```yaml
spec:
  goalRevision:
    enabled: true
    strategy: llm
    minOutcomes: 10
    importanceThreshold: 3.0
    promotionSuccessRate: 0.8
    demotionFailureRate: 0.7
```

Absent `goalRevision:` block -> null config -> disabled. Partial block fills
missing fields from defaults.

**Validation:**
- `promotionSuccessRate` must be in (0.0, 1.0]
- `demotionFailureRate` must be in (0.0, 1.0]
- `minOutcomes` must be > 0
- `goalRevision` without any workers having `AgentDescriptor` with goals ->
  warning at parse time

## EventLog Audit

New event type: `CaseHubEventType.GOAL_REVISED`

One EventLog entry per evaluation cycle (may cover multiple goals revised).

**Metadata:**

| Key | Type | Description |
|-----|------|-------------|
| `agentId` | String | Which agent's goals were revised |
| `strategyId` | String | Which GoalRevisionStrategy was used |
| `revisions` | List | Per-goal revision records |
| `revisions[].goalName` | String | Goal that was revised |
| `revisions[].changeType` | String | `PRIORITY_PROMOTED`, `PRIORITY_DEMOTED`, `DESCRIPTION_REFINED`, `PRIORITY_AND_DESCRIPTION` |
| `revisions[].previousPriority` | String | Before (nullable) |
| `revisions[].newPriority` | String | After (nullable) |
| `revisions[].previousDescription` | String | Before (nullable, only when changed) |
| `revisions[].newDescription` | String | After (nullable) |
| `revisions[].reason` | String | Rule rationale or LLM rationale |
| `revisions[].metrics` | Object | `{successCount, failureCount, successRate}` at revision time |
| `totalGoalsEvaluated` | int | How many goals were examined |
| `totalGoalsRevised` | int | How many actually changed |

## Cross-repo Dependency

**AgentGoal.toBuilder()** in eidos-api. `AgentGoal` is an immutable record
with no copy-with-modifications method. The engine must construct new
`AgentGoal` instances with modified fields. Direct constructor use is fragile
(breaks when fields are added). `AgentDescriptor` already has `toBuilder()` —
same pattern needed on `AgentGoal`.

File an eidos issue as a prerequisite. Small change — add `Builder` inner class
and `toBuilder()` method to `AgentGoal`.

No other cross-repo changes needed. `AgentRegistry.register()`,
`BehavioralSignalStore`, and `BehavioralSignal.SUCCESS` already exist.

## Module Placement

| Type | Module | Rationale |
|------|--------|-----------|
| `GoalRevisionStrategy` | engine-api | Consumer-implementable NamedStrategy SPI |
| `GoalRevisionContext`, `GoalRevisionProposal` | engine-api | SPI parameter/result types |
| `GoalEffectivenessMetrics` | engine-api | SPI parameter type |
| `GoalRevisionConfig` | engine-api | CaseDefinition config record |
| `GoalOutcomeRecorder` | runtime | Rename of GoalFailureRecorder |
| `GoalRevisionEvaluator` | runtime | Execution infrastructure |
| `LlmGoalRevisionStrategy` | runtime | Built-in strategy implementation |
| `GOAL_REVISED` event type | common | Event constant |

## Testing

### Unit tests

1. **`GoalOutcomeRecorderTest`** (rename from GoalFailureRecorderTest):
   - SUCCESS outcome records `BehavioralSignal.SUCCESS` per goal
   - COMPLETED outcome records `BehavioralSignal.SUCCESS` per goal
   - DECLINED/FAILED/EXPIRED record `BehavioralSignal.DECLINE` (preserved)
   - Capability filtering unchanged (preserved)
   - No signal store -> no-op (preserved)

2. **`GoalRevisionEvaluatorTest`** — core logic:
   - Skips when `GoalRevisionConfig` is null (disabled)
   - Skips when `AgentRegistry` not resolvable
   - Accumulates outcomes, does not trigger below threshold
   - Triggers when `outcomeCount >= minOutcomes`
   - Triggers when `cumulativeImportance >= importanceThreshold`
   - Resets counters after trigger
   - Priority promotion: SECONDARY -> PRIMARY when successRate > threshold
   - Priority demotion: PRIMARY -> SECONDARY when failureRate > threshold
   - No change when rates are between thresholds
   - Minimum outcome guard: skips goals with fewer than minOutcomes total
   - Delegates to GoalRevisionStrategy for description refinement
   - Merges rule-based priority with strategy-proposed changes (rule wins)
   - Calls `AgentRegistry.register()` with updated descriptor
   - Does NOT call register when no changes proposed
   - Writes GOAL_REVISED EventLog with correct metadata
   - Per-agent lock prevents concurrent revision
   - Exception isolation: strategy failure -> priority-only still applies
   - Exception isolation: register failure -> logged, case continues
   - All exceptions caught — never blocks case progression

3. **`GoalEffectivenessMetricsTest`**:
   - successRate and failureRate computation
   - Zero-outcome guard (no division by zero)

4. **`GoalRevisionProposalTest`**, **`GoalRevisionConfigTest`**:
   - Record validation, defaults, null handling
   - Config validation (rates in range, minOutcomes positive)

5. **`LlmGoalRevisionStrategyTest`**:
   - Produces GoalRevisionProposal from structured LLM response
   - No-op when ChatModelProvider absent
   - Invalid JSON -> Uni failure
   - Empty revisions when LLM says no changes needed

6. **`CaseDefinitionYamlMapperTest`** additions:
   - Parses `goalRevision:` block with all fields
   - Partial block fills defaults
   - Absent block -> null config
   - Validation errors for out-of-range thresholds

### Integration test

7. **`GoalRevisionIntegrationTest`** (`@QuarkusTest`):
   - Full flow: case with agent goals + goalRevision config -> multiple worker
     completions -> threshold met -> goals revised -> EventLog contains
     GOAL_REVISED
   - Mock ChatModelProvider with canned description refinement
   - Verify AgentRegistry contains updated descriptor after revision
   - Verify SECONDARY goal promoted after repeated successes
   - Verify PRIMARY goal demoted after repeated failures

## Scope Boundaries

**In scope:**
- GoalOutcomeRecorder (rename + generalization of GoalFailureRecorder)
- GoalRevisionEvaluator with threshold-based trigger
- GoalRevisionStrategy SPI + LlmGoalRevisionStrategy
- GoalRevisionConfig on CaseDefinition + YAML
- GoalEffectivenessMetrics, GoalRevisionProposal, GoalRevisionContext
- Priority adjustment (rule-based) + description refinement (LLM-based)
- EventLog audit (GOAL_REVISED)
- Cross-repo: AgentGoal.toBuilder() in eidos-api

**v1 constraints (deliberate):**
- Single revision strategy per case definition
- No capability list revision (add/remove capabilities based on outcomes)
- No visibility revision
- No goal parameter rollback mechanism (audit trail supports manual rollback)
- No cascading revision (revising one goal does not trigger re-evaluation of others)

**Out of scope (future work):**
- Capability list revision based on discovered capability-goal associations
- Goal parameter rollback / undo
- Cascading revision across related goals
- Cross-agent goal coordination (revising shared goals)
- Goal revision visualization / REST endpoints
- PreferenceStore integration for per-tenancy default overrides

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

## Design Principles

1. **Descriptor immutability.** Declared goals on `AgentDescriptor` remain
   immutable (identity). Revised goal state (priority changes, description
   refinements) is stored in `GoalLifecycleStore` — a separate learned-state
   store that merges with declared goals at evaluation time. This follows the
   `DispositionSignalStore` pattern established in the epic-800 architecture
   spec: base state on descriptor + learned state in separate store.

2. **Agent-level, not per-case.** Goal revision is an agent-level concern.
   Thresholds and strategy are configured via `@ConfigProperty` defaults
   (not per-CaseDefinition). An agent serving multiple case types accumulates
   a single goal lifecycle state. Per the epic-800 architecture spec: "Goal
   formation and revision are agent-level concerns driven by reflection
   output, not case-definition configuration."

3. **Windowed metrics.** Effectiveness is computed over outcomes since the
   last revision, not cumulative lifetime counts. This prevents the
   irreversibility problem where a promoted goal cannot be corrected without
   an implausible volume of subsequent failures.

4. **Per-goal error isolation.** When building revised goals, each goal is
   processed independently. An invalid LLM description for one goal does not
   prevent valid priority changes for other goals.

5. **Coordinated with abandonment.** Goal revision and goal abandonment
   operate on the same signal data. When a goal is revised (priority change),
   the revision clears the abandonment signal count for that goal — the
   revision represents a fresh start. Abandoned goals are skipped by the
   revision evaluator.

## Architecture

Evaluator-per-completion pattern — `GoalRevisionEvaluator` is called from
`WorkflowExecutionCompletedHandler` on every worker completion (both success
and failure paths), same as the other recorders. It accumulates per-agent
outcome state internally and evaluates revision when configurable thresholds
are met. Priority adjustment is rule-based (deterministic threshold
comparison). Description refinement delegates to a `GoalRevisionStrategy` SPI
(LLM-backed default). Changes are stored in `GoalLifecycleStore` — the
descriptor is never mutated.

```
Worker completes (success or failure)
  -> WorkflowExecutionCompletedHandler
    -> GoalOutcomeRecorder.record()           [RENAMED from GoalFailureRecorder]
        records SUCCESS or DECLINE per goal in BehavioralSignalStore
    -> GoalRevisionEvaluator.record()          [NEW]
        1. Resolve workerName -> agentId via CaseDefinition.agentDescriptorFor()
        2. Accumulate per-agent outcome count + importance
        3. When threshold met -> spawn virtual thread:
           a. Query BehavioralSignalStore -> per-goal success/failure counts
           b. Compute windowed GoalEffectivenessMetrics (delta since last revision)
           c. Filter abandoned goals via GoalAbandonmentEvaluator
           d. Priority adjustment: rule-based threshold comparison
           e. Description refinement: delegate to GoalRevisionStrategy SPI
           f. Store revisions via GoalLifecycleStore.reviseGoal()
           g. Clear abandonment signals for revised goals
           h. Write GOAL_REVISED EventLog

At routing time:
  -> GoalSignalProvider (existing, id="goal")
    -> GoalLifecycleStore.effectiveGoals(descriptor)
       merges declared goals + revised state -> effective goals for scoring
```

### Trigger mechanism

Per-agent threshold tracking using `ConcurrentHashMap<String, RevisionState>`
keyed by `agentId|tenancyId` — same pattern as
`AgentExperienceRecorder.ReflectionState`. Counters accumulate on every worker
completion. When either `outcomeCount >= minOutcomes` or
`cumulativeImportance >= importanceThreshold`, counters reset and evaluation
runs on a virtual thread.

Thresholds are configured via `@ConfigProperty` with sensible defaults
(agent-level, not per-case). This follows the `GoalAbandonmentEvaluator`
pattern which uses `casehub.engine.goal.abandonment-threshold`.

### v1 trigger simplification

The epic-800 architecture spec triggers goal evolution from
`@ObservesAsync ReflectionRecorded` — after reflection synthesizes insights.
This spec triggers from raw worker completion outcomes (threshold-based).
This is a deliberate v1 simplification: reflection is not always enabled,
and per-completion triggering provides more responsive goal tuning. Future
work can add a reflection-triggered evaluation path alongside the
completion-triggered one, using the same `GoalRevisionStrategy` SPI.

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
5. **Null capability guard:** When `capabilityName` is null, skip recording
   entirely. Null capability means no matching binding — recording for all
   goals on null capability inflates signal counts with phantom data.
   GoalFailureRecorder's existing behavior (record for all goals when null)
   was tolerable when only recording DECLINEs on the failure path, but
   extending to SUCCESS would create false promotions.

Everything else unchanged: `__goal__` sentinel capability, per-goal capability
filtering, `BehavioralSignalStore` interaction. `GoalAbandonmentEvaluator`
continues to work unchanged — it only queries DECLINE counts.

### GoalRevisionEvaluator (new)

`runtime/internal/routing/`, `@ApplicationScoped`.

**Injected dependencies:**
- `Instance<GoalLifecycleStore>` — store revised goal state
- `Instance<BehavioralSignalStore>` — query per-goal outcome counts
- `CaseDefinitionRegistry` — resolve CaseDefinition, look up AgentDescriptor
- `EngineStrategyResolver` — resolve GoalRevisionStrategy by ID
- `EventLogRepository` — write GOAL_REVISED audit entries
- `GoalAbandonmentEvaluator` — filter abandoned goals before revision

**Method:** `record(CaseInstance, String workerName, String capabilityName,
WorkerOutcome<?>)`

No `bindingName` parameter — revision does not use it.

**workerName -> agentId resolution:** Same path as `GoalFailureRecorder` —
`CaseDefinitionRegistry.getCaseDefinition()` then
`definition.agentDescriptorFor(workerName)`. Early return when no descriptor
or no goals. The agentId and tenancyId are extracted from the descriptor
and CaseInstance respectively.

**Internal state:**

```java
private static class RevisionState {
    int outcomeCount;
    double cumulativeImportance;
    Instant lastRevisionTime;
    Map<String, SignalSnapshot> signalSnapshots; // goalName -> counts at last revision
}

private record SignalSnapshot(int successCount, int failureCount) {}
```

`signalSnapshots` enables windowed metrics: after a revision, the evaluator
stores the current BehavioralSignalStore counts per goal. On the next
evaluation, `windowedSuccess = currentSuccess - snapshot.successCount`.
On first evaluation (no snapshot), cumulative counts are used. On JVM
restart, snapshots are lost — first post-restart evaluation uses cumulative
counts, then tracks deltas from there. Acceptable for v1 (single-instance).

**Trigger evaluation** (inside `ConcurrentHashMap.compute()` — atomic):
1. Resolve agentId from workerName via CaseDefinitionRegistry
2. Increment outcomeCount and cumulativeImportance on RevisionState
3. If `outcomeCount >= minOutcomes` OR
   `cumulativeImportance >= importanceThreshold` -> trigger
4. Reset counters, record lastRevisionTime
5. Spawn virtual thread for evaluation

**Importance mapping:** Uses `WorkerOutcome` variant names via the same
`outcomeKindName()` pattern as `AgentExperienceRecorder`. Maps:
SUCCESS->0.3, COMPLETED->0.3, DECLINED->0.6, FAILED->0.8, EXPIRED->0.5
(from `ReflectionTriggerConfig.DEFAULT_IMPORTANCE_WEIGHTS`).

**Evaluation logic** (on virtual thread):
1. Read current AgentDescriptor goals (from the descriptor on
   CaseDefinition, not from AgentRegistry — goals are immutable on the
   descriptor)
2. Filter abandoned goals via `GoalAbandonmentEvaluator.isAbandoned()`
3. For each non-abandoned goal:
   a. Query `signalStore.count(agentId, tenancyId, "__goal__", goalName,
      SUCCESS)` and same for DECLINE
   b. Compute windowed counts: `current - snapshot` (or cumulative if no
      snapshot)
   c. Build `GoalEffectivenessMetrics(goalName, windowedSuccess,
      windowedFailure)`
   d. Skip goals with `totalOutcomes < minOutcomes` (insufficient data)
4. **Priority adjustment** (rule-based, always runs):
   - SECONDARY goal with `successRate > promotionSuccessRate` ->
     promote to PRIMARY
   - PRIMARY goal with `failureRate > demotionFailureRate` -> demote
     to SECONDARY
5. **Description refinement** (if strategy configured and
   GoalRevisionStrategy resolvable):
   - Build `GoalRevisionContext` with ALL non-abandoned goals and their
     metrics (strategy sees the full picture)
   - Call `strategy.revise(context)` -> `GoalRevisionProposal`
   - Filter proposals: only accept revisions for goals that were in the
     context (ignore LLM proposals for unknown goal names)
6. **Merge:** rule-based priority takes precedence over strategy-proposed
   priority when both propose a change (deterministic wins over LLM)
7. **Apply per goal** (error isolation): for each goal with changes:
   a. Validate proposed description against `AgentDescriptorValidator`
      constraints (max length). Invalid descriptions are discarded with
      a warning — the priority change still applies.
   b. Build `GoalRevision(description, priority, reason)`
   c. Call `GoalLifecycleStore.reviseGoal(agentId, tenancyId, goalName,
      revision)`
   d. If priority changed, clear abandonment signals:
      `signalStore.clear(agentId, tenancyId, "__goal__",
      BehavioralSignal.DECLINE)` for that goal's qualifier
8. **Update snapshots:** store current signal counts in
   `RevisionState.signalSnapshots` for windowed delta on next evaluation
9. **Audit** — write GOAL_REVISED EventLog with per-goal revision metadata

**Concurrency:** Per-agent `ReentrantLock` via `ConcurrentHashMap<String,
ReentrantLock>` keyed by `agentId|tenancyId`. Prevents concurrent revision
for the same agent when multiple workers complete near-simultaneously.
Acquired before evaluation step 1, released in finally block. No
read-modify-write race on AgentDescriptor since the descriptor is never
mutated — `GoalLifecycleStore.reviseGoal()` is an additive write.

**Guard rails:**
- `isResolvable()` guard on `GoalLifecycleStore` and `BehavioralSignalStore`
  — transparent no-op when absent
- All exceptions caught and logged — never blocks case progression
- Strategy failure -> priority-only revision still applies
- Per-goal error isolation — one failed goal does not abort others

### GoalRevisionStrategy SPI (engine-api)

`io.casehub.api.spi.routing`, alongside existing routing-adjacent strategies.
Goal revision affects routing outcomes (revised priorities influence
`GoalSignalProvider` scoring).

```java
public interface GoalRevisionStrategy extends NamedStrategy {
    Uni<GoalRevisionProposal> revise(GoalRevisionContext context);

    @Override
    default String id() { return "llm"; }
}
```

**GoalRevisionContext** — carries ALL non-abandoned goals and their metrics.
The strategy sees the full goal landscape to reason about inter-goal
relationships (e.g., a goal should not be promoted if a competing goal with
the same capabilities is already PRIMARY).

```java
public record GoalRevisionContext(
    String agentId,
    String tenancyId,
    List<AgentGoal> goals,
    List<GoalEffectivenessMetrics> metrics,
    CaseDefinition definition
)
```

Goals and metrics are parallel lists (same ordering, same size). The strategy
can correlate `goals.get(i)` with `metrics.get(i)`.

**GoalEffectivenessMetrics** — per-goal windowed metrics:

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

**GoalRevisionProposal** — per-goal proposed changes. The strategy returns
only goals it wants to change — absent goals mean "no change."

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

The evaluator filters proposals: only goals present in the context are
accepted. LLM proposals referencing unknown goal names are discarded.

### LlmGoalRevisionStrategy (runtime)

`runtime/internal/routing/`, `@ApplicationScoped`, id=`"llm"`.

**Injected:** `Instance<ChatModelProvider>` — transparent no-op when absent.

**Prompt construction:**
- System: "You are a goal effectiveness analyst. Given an agent's goals
  and their performance metrics, evaluate whether any goal descriptions
  should be refined to better capture what the agent accomplishes. Only
  propose changes when a description is meaningfully misaligned with
  observed outcomes. Do not propose changes for goals with insufficient
  data."
- User: all goals with their names, descriptions, priorities, and
  effectiveness metrics (success rate, failure rate, total outcomes).
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

Goal revision is agent-level. Configuration uses `@ConfigProperty` with
sensible defaults, following the `GoalAbandonmentEvaluator` pattern.

| Property | Default | Description |
|----------|---------|-------------|
| `casehub.engine.goal.revision.enabled` | `false` | Master switch |
| `casehub.engine.goal.revision.strategy` | `"llm"` | GoalRevisionStrategy ID |
| `casehub.engine.goal.revision.min-outcomes` | `10` | Minimum outcomes before evaluation |
| `casehub.engine.goal.revision.importance-threshold` | `3.0` | Cumulative importance to trigger |
| `casehub.engine.goal.revision.promotion-success-rate` | `0.8` | SECONDARY -> PRIMARY threshold |
| `casehub.engine.goal.revision.demotion-failure-rate` | `0.7` | PRIMARY -> SECONDARY threshold |

No `GoalRevisionConfig` on `CaseDefinition`. No YAML block. This is
deliberate — goal state is agent-level, not case-level.

## Cross-repo Dependency

**GoalLifecycleStore SPI** in eidos-api. The epic-800 architecture spec
defines this interface but it has not been implemented yet. This is the
primary cross-repo dependency:

```java
public interface GoalLifecycleStore {
    void reviseGoal(String agentId, String tenancyId, String goalName,
                    GoalRevision revision);
    void updatePriority(String agentId, String tenancyId, String goalName,
                        GoalPriority priority);
    List<EffectiveGoal> effectiveGoals(AgentDescriptor descriptor);
}

public record EffectiveGoal(AgentGoal goal, GoalSource source) {}
public enum GoalSource { DECLARED, DISCOVERED }

public record GoalRevision(
    String description,    // nullable
    GoalPriority priority, // nullable
    String reason
)
```

`NoOpGoalLifecycleStore` (`@DefaultBean @ApplicationScoped`) in eidos-api
returns descriptor goals wrapped as `EffectiveGoal(goal, DECLARED)` with
no-op write methods. Real implementation in eidos-runtime.

**Not needed:** `AgentGoal.toBuilder()`. Goals on the descriptor are immutable.
Revisions are stored in `GoalLifecycleStore`, not by reconstructing
`AgentGoal` instances.

**Not needed:** `AgentRegistry.register()` for goal mutation. The descriptor
is never rewritten.

The `GoalLifecycleStore` SPI includes `addDiscoveredGoal()` and
`discoveredGoals()` methods for #805 (goal formation). This issue implements
only `reviseGoal()`, `updatePriority()`, and `effectiveGoals()`. The full
interface is defined now for coherence with the epic-800 architecture.

**GoalSignalProvider evolution:** The existing `GoalSignalProvider`
(id=`"goal"`) currently scores candidates via
`GoalAbandonmentEvaluator.activeGoals()` against `descriptor.goals()`. It
evolves to use `GoalLifecycleStore.effectiveGoals(descriptor)` which merges
declared goals with revised state (priorities, descriptions). This is a
small change in the existing provider — inject `Instance<GoalLifecycleStore>`
and call `effectiveGoals()` instead of `descriptor.goals()`.

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
| `revisions[].metrics` | Object | `{successCount, failureCount, successRate}` windowed metrics at revision time |
| `totalGoalsEvaluated` | int | How many goals were examined |
| `totalGoalsRevised` | int | How many actually changed |

## Module Placement

| Type | Module | Rationale |
|------|--------|-----------|
| `GoalRevisionStrategy` | engine-api | Consumer-implementable NamedStrategy SPI |
| `GoalRevisionContext`, `GoalRevisionProposal` | engine-api | SPI parameter/result types |
| `GoalEffectivenessMetrics` | engine-api | SPI parameter type |
| `GoalOutcomeRecorder` | runtime | Rename of GoalFailureRecorder |
| `GoalRevisionEvaluator` | runtime | Execution infrastructure |
| `LlmGoalRevisionStrategy` | runtime | Built-in strategy implementation |
| `GOAL_REVISED` event type | common | Event constant |
| `GoalLifecycleStore` | eidos-api | Cross-repo SPI (epic-800 architecture) |
| `GoalRevision`, `EffectiveGoal` | eidos-api | GoalLifecycleStore types |
| `NoOpGoalLifecycleStore` | eidos-api | @DefaultBean no-op |

## Testing

### Unit tests

1. **`GoalOutcomeRecorderTest`** (rename from GoalFailureRecorderTest):
   - SUCCESS outcome records `BehavioralSignal.SUCCESS` per goal
   - COMPLETED outcome records `BehavioralSignal.SUCCESS` per goal
   - DECLINED/FAILED/EXPIRED record `BehavioralSignal.DECLINE` (preserved)
   - Capability filtering unchanged (preserved)
   - Null capabilityName skips recording entirely (NEW)
   - No signal store -> no-op (preserved)

2. **`GoalRevisionEvaluatorTest`** — core logic:
   - Skips when `GoalLifecycleStore` not resolvable (disabled)
   - Skips when revision not enabled (`casehub.engine.goal.revision.enabled`)
   - workerName -> agentId resolution via CaseDefinition.agentDescriptorFor()
   - Early return when no descriptor or no goals
   - Accumulates outcomes, does not trigger below threshold
   - Triggers when `outcomeCount >= minOutcomes`
   - Triggers when `cumulativeImportance >= importanceThreshold`
   - Resets counters after trigger
   - Filters abandoned goals before evaluation
   - Windowed metrics: uses delta since last revision snapshot
   - Windowed metrics: first evaluation uses cumulative counts (no snapshot)
   - Priority promotion: SECONDARY -> PRIMARY when successRate > threshold
   - Priority demotion: PRIMARY -> SECONDARY when failureRate > threshold
   - No change when rates are between thresholds
   - Minimum outcome guard: skips goals with fewer than minOutcomes windowed
   - Delegates to GoalRevisionStrategy for description refinement (all goals)
   - Filters strategy proposals: ignores unknown goal names
   - Merges rule-based priority with strategy-proposed changes (rule wins)
   - Calls `GoalLifecycleStore.reviseGoal()` per revised goal (NOT
     AgentRegistry.register())
   - Clears abandonment signals on priority change
   - Does NOT call reviseGoal when no changes proposed
   - Writes GOAL_REVISED EventLog with correct metadata
   - Per-agent lock prevents concurrent revision
   - Per-goal error isolation: invalid LLM description discarded, priority
     change still applies for that goal, other goals unaffected
   - Exception isolation: strategy failure -> priority-only revision
   - All exceptions caught — never blocks case progression

3. **`GoalEffectivenessMetricsTest`**:
   - successRate and failureRate computation
   - Zero-outcome guard (no division by zero)

4. **`GoalRevisionProposalTest`**:
   - Record validation, null handling

5. **`LlmGoalRevisionStrategyTest`**:
   - Produces GoalRevisionProposal from structured LLM response
   - Receives all goals in context (not per-goal invocation)
   - No-op when ChatModelProvider absent
   - Invalid JSON -> Uni failure
   - Empty revisions when LLM says no changes needed

6. **`GoalLifecycleStoreContractTest`** (eidos-api, abstract):
   - reviseGoal stores revision, effectiveGoals reflects it
   - updatePriority changes priority in effective goals
   - Multiple revisions to same goal: latest wins
   - effectiveGoals with no revisions returns declared goals as-is
   - Revision with null description preserves original description
   - Revision with null priority preserves original priority

### Integration test

7. **`GoalRevisionIntegrationTest`** (`@QuarkusTest`):
   - Full flow: case with agent goals -> multiple worker completions ->
     threshold met -> goals revised -> EventLog contains GOAL_REVISED
   - Mock ChatModelProvider with canned description refinement
   - Verify GoalLifecycleStore contains revision after evaluation
   - Verify effectiveGoals() returns revised priority/description
   - Verify SECONDARY goal promoted after repeated successes
   - Verify PRIMARY goal demoted after repeated failures
   - Verify abandoned goals skipped by evaluator
   - Verify abandonment signals cleared on priority change

## Scope Boundaries

**In scope:**
- GoalOutcomeRecorder (rename + generalization of GoalFailureRecorder)
- GoalRevisionEvaluator with threshold-based trigger + windowed metrics
- GoalRevisionStrategy SPI + LlmGoalRevisionStrategy
- GoalEffectivenessMetrics, GoalRevisionProposal, GoalRevisionContext
- Priority adjustment (rule-based) + description refinement (LLM-based)
- EventLog audit (GOAL_REVISED)
- Agent-level config via @ConfigProperty
- GoalSignalProvider evolution to use effectiveGoals()
- Per-goal error isolation
- Abandonment signal coordination
- Cross-repo: GoalLifecycleStore SPI + NoOp + contract tests in eidos-api

**v1 constraints (deliberate):**
- Per-completion trigger (not reflection-triggered — deliberate simplification)
- Single strategy selection via global config (not per-agent)
- No capability list revision (add/remove capabilities based on outcomes)
- No visibility revision
- No goal parameter rollback mechanism (audit trail supports manual rollback)
- No cascading revision (revising one goal does not trigger re-evaluation)
- Windowed metrics are in-memory (lost on JVM restart, reseeded from
  cumulative counts) — acceptable for single-instance v1 deployment

**Out of scope (future work):**
- Reflection-triggered evaluation path (`@ObservesAsync ReflectionRecorded`)
- Capability list revision based on discovered capability-goal associations
- Goal parameter rollback / undo
- Cascading revision across related goals
- Cross-agent goal coordination (revising shared goals)
- Goal revision visualization / REST endpoints
- PreferenceStore integration for per-tenancy threshold overrides
- Durable windowed metrics for multi-instance deployment
- GoalLifecycleStore JPA implementation (eidos-runtime)

## Review Findings Addressed

| Finding | Source | Resolution |
|---------|--------|------------|
| Descriptor mutation contradicts GoalLifecycleStore architecture | Str R1-01 | Replaced AgentRegistry.register() with GoalLifecycleStore.reviseGoal() |
| GoalAbandonmentEvaluator interaction unaddressed | Coh R1-05, Str R1-02, Rob R1-05 | Filter abandoned goals before revision; clear abandonment signals on priority change |
| Per-case GoalRevisionConfig contradicts epic-800 | Str R1-03 | Moved to agent-level @ConfigProperty; removed CaseDefinition config |
| Cumulative signal counts make demotion impossible | Rob R1-01 | Windowed metrics via SignalSnapshot delta tracking |
| GoalRevisionContext/Proposal cardinality mismatch | Coh R1-03, Rob R1-06 | Context carries all goals; strategy returns subset of revisions |
| workerName -> agentId resolution unspecified | Coh R1-04 | Explicit CaseDefinition.agentDescriptorFor() resolution documented |
| Cross-case config ambiguity for multi-case agents | Rob R1-02 | Eliminated: config is agent-level, not per-case |
| Read-modify-write race on AgentDescriptor | Rob R1-03 | Eliminated: descriptor never mutated; GoalLifecycleStore is additive |
| LLM description validation failure loses all changes | Rob R1-04 | Per-goal error isolation: validate before storing, discard invalid |
| GoalRevisionConfig.enabled creates redundant disable | Coh R1-06 | Single mechanism: @ConfigProperty master switch |
| Null capabilityName inflates signal counts | Rob R1-07 | GoalOutcomeRecorder skips recording when capabilityName is null |
| bindingName parameter unused | Coh R1-08 | Removed from GoalRevisionEvaluator.record() signature |
| recentOutcomeDescriptions redundant with metrics | Coh R1-09 | Removed: metrics carry the same data; LLM gets metrics directly |
| GoalRevisionStrategy SPI package doesn't exist | Str R1-05 | Placed in io.casehub.api.spi.routing (existing package) |
| Importance weight mapping inconsistency | Coh R1-07 | Explicit: uses outcomeKindName() pattern from AgentExperienceRecorder |
| Revision trigger bypasses reflection pipeline | Str R1-07 | Documented as deliberate v1 simplification; future work tracked |
| GoalOutcomeRecorder/GoalRevisionEvaluator split state | Str R1-04 | Accepted: established pattern (matches AgentExperienceRecorder) |
| WorkflowExecutionCompletedHandler god class | Str R1-06 | Acknowledged: structural hotspot, not addressed in this issue |

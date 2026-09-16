# Cognition Scheduler SPI — Design Spec

**Issue:** casehubio/blocks#161
**Date:** 2026-09-16
**Branch:** issue-161-innerlife-scheduler-spi

## Overview

Formalise the implicit tick ordering in `CognitionCore` as a declarative phase
model, bring `InnerLifeOrchestrator` into the tick cycle as a first-class
TERMINAL phase participant, fix the data staleness in drive ordering, and
expose an open participation SPI for consumers to register custom tick
participants.

## Problems Addressed

1. **Ordering hardcoded** — `CognitionCore.tick()` has the orchestrator
   sequence baked into imperative code. Adding or reordering requires
   editing the method body.
2. **InnerLife excluded** — sits outside the tick cycle, redundantly
   re-ticks `DriveOrchestrator` for freshness (line 97 of `doTick()`).
   `evaluateProactive()` can be called without a prior `tick()`, risking
   stale drive data.
3. **Drive ordering bug** — drives tick at position 4 (before strategy,
   userModel, mentalModel). 3 of 4 drive sources see one-tick-stale data.
   Contradicts #136 spec: "source orchestrators → DriveOrchestrator →
   GoalProposalOrchestrator."
4. **No consumer extension** — consumers cannot inject custom tick
   participants without modifying `CognitionCore`.

## Architecture

### Phase Enum

```java
public enum CognitionPhase {
    FOUNDATION,         // mood, memoryHygiene — no orchestrator deps
    SOURCE,             // narrative, strategy — populate caches from stores
    SOURCE_PER_SUBJECT, // userModel, mentalModel — per-subject iteration (special)
    DERIVED,            // drives — reads all source + foundation orchestrators
    TERMINAL            // goals, innerLife — reads drives
}
```

Phases execute in enum declaration order. Within a phase, participants
execute in registration order (deterministic, insertion-ordered).

### Orchestrator-to-Phase Mapping

| Phase | Orchestrators | Dependencies |
|-------|--------------|-------------|
| FOUNDATION | MoodOrchestrator, MemoryHygieneOrchestrator | None (stores only) |
| SOURCE | NarrativeOrchestrator, StrategyLearningOrchestrator | Foundation stores |
| SOURCE_PER_SUBJECT | UserModelOrchestrator, MentalModelOrchestrator | Per-subject, iterated via SubjectResolver |
| DERIVED | DriveOrchestrator | mood, memoryHygiene, narrative, strategy, userModel, mentalModel |
| TERMINAL | GoalProposalOrchestrator, InnerLifeOrchestrator | drives |

This corrects the current ordering: drives now runs after all its source
orchestrators (fixing the staleness bug identified in D6).

### CognitionTickParticipant SPI

```java
@FunctionalInterface
public interface CognitionTickParticipant {
    void tick(CognitionTickContext context);
}
```

```java
public record CognitionTickContext(
    String agentId,
    String tenantId,
    @Nullable AgentDescriptor descriptor
) {}
```

Single method, single context record. The context carries everything a
participant needs. Built-in orchestrators are wrapped into participants
internally by CognitionCore — they don't implement the SPI themselves.

### CognitionCore Changes

#### Participant Registration

```java
public class CognitionCore {
    private final Map<CognitionPhase, List<CognitionTickParticipant>> customParticipants;

    public void addParticipant(CognitionPhase phase, CognitionTickParticipant participant) { ... }
}
```

Registration is at construction time or early lifecycle (before the
first `tick()` call). Not thread-safe for concurrent registration —
consumers add participants during wiring, not mid-tick. Registration
is additive — custom participants run after built-in orchestrators
within their phase. `SOURCE_PER_SUBJECT` does not accept
custom participants (throws `IllegalArgumentException`). Consumers
needing per-subject custom ticking register a global participant at
another phase and iterate subjects internally — they can capture the
`SubjectResolver` from CognitionCore's `tick()` call if needed by
wrapping CognitionCore in a coordinator that exposes the resolver.

#### Tick Method

```java
public void tick(String agentId, String tenantId,
                 @Nullable AgentDescriptor descriptor,
                 SubjectResolver resolver) {
    var context = new CognitionTickContext(agentId, tenantId, descriptor);

    // FOUNDATION
    if (config.moodEnabled()) safeRun(() -> tickMood(agentId, tenantId));
    if (config.memoryHygieneEnabled() && memoryHygiene != null)
        safeRun(() -> memoryHygiene.tick(agentId, tenantId));
    runCustomParticipants(CognitionPhase.FOUNDATION, context);

    // SOURCE
    if (config.narrativeEnabled() && narrative != null)
        safeRun(() -> narrative.tick(agentId, tenantId));
    if (config.strategyEnabled() && strategy != null)
        safeRun(() -> strategy.tick(agentId, tenantId));
    runCustomParticipants(CognitionPhase.SOURCE, context);

    // SOURCE_PER_SUBJECT (no custom participants)
    for (String subjectId : resolver.relevantSubjects(agentId, tenantId)) {
        if (config.userModelEnabled() && userModel != null)
            safeRun(() -> userModel.tick(agentId, subjectId, tenantId));
        if (config.mentalModelEnabled() && mentalModel != null)
            safeRun(() -> mentalModel.tick(agentId, subjectId, tenantId));
    }

    // DERIVED
    if (config.drivesEnabled() && descriptor != null)
        safeRun(() -> drives.tick(agentId, tenantId, descriptor));
    runCustomParticipants(CognitionPhase.DERIVED, context);

    // TERMINAL
    if (config.goalsEnabled() && goals != null && descriptor != null)
        safeRun(() -> goals.tick(agentId, tenantId, descriptor));
    if (innerLife != null && descriptor != null)
        safeRun(() -> innerLife.tick(descriptor, null));
    runCustomParticipants(CognitionPhase.TERMINAL, context);
}
```

Built-in orchestrators run first within each phase, then custom
participants. `CognitionConfig` flags gate built-in orchestrators only —
custom participants are always invoked (consumers manage their own
enable/disable logic).

#### Error Isolation

`safeRun()` wraps every participant call (built-in and custom) in
try/catch that logs and continues. This preserves current behaviour:
one participant failing does not crash the tick cycle. The isolation is
per-participant, not per-phase — a failing FOUNDATION participant does
not skip the remaining FOUNDATION participants.

### CognitionConfig Coexistence

`CognitionConfig` remains unchanged — it gates built-in orchestrators.
Custom participants are not subject to `CognitionConfig` flags. This is
the correct separation: config controls what blocks ships; consumers
control what they add.

No new config fields are needed. The existing `with()` / `without()`
API continues to work. If `drivesEnabled=false`, the built-in drive
tick is skipped, but a custom DERIVED participant still runs.

### InnerLife Integration

#### Changes to InnerLifeOrchestrator

1. **Remove `driveOrchestrator.tick()` call** from `doTick()` line 97.
   Drives are structurally guaranteed to be current — TERMINAL runs
   after DERIVED.

2. **CognitionCore ticks InnerLife** with `channelContext = null` during
   the regular tick cycle. InnerLife's content quality gate
   short-circuits most ticks at microsecond cost (empty buffer check,
   observation count, novelty threshold). The LLM call only fires when
   observations accumulate past all gates.

3. **`evaluateProactive()` reads cached state** instead of triggering
   evaluation. After the tick cycle, InnerLife has already evaluated
   and cached its result. `ProactiveSpeechSupport` reads the cached
   `InnerLifeTick` from the last tick rather than calling `tick()`
   again.

#### Changes to InnerLifeOrchestrator API

The tick method signature stays as-is: `tick(AgentDescriptor, @Nullable String channelContext)`.
CognitionCore calls it with `channelContext = null` during scheduled
ticks. When a consumer needs proactive evaluation with channel context,
they call `evaluateProactive()` which passes the context.

New method for cached result access:

```java
public @Nullable InnerLifeTick lastTick(String agentId, String tenantId) { ... }
```

`ProactiveSpeechSupport.evaluateProactive()` changes from calling
`innerLife.tick(descriptor, channelContext)` to reading
`innerLife.lastTick(agentId, tenantId)` and, if Initiated, returning
the content. If the consumer needs channel-specific evaluation, they
can still call `tick()` directly — but after CognitionCore has ticked,
the cached result is usually sufficient.

#### Changes to SocialAvatarCognition

`evaluateProactive()` simplifies: instead of calling
`ProactiveSpeechSupport` → `innerLife.tick()`, it reads
`innerLife.lastTick()`. InnerLife is already ticked in the
CognitionCore cycle.

### InnerLife Constructor Change

Remove the `DriveOrchestrator` constructor parameter. InnerLife no
longer needs a direct reference — it doesn't tick drives itself.
This breaks the bidirectional dependency between InnerLife and
DriveOrchestrator.

## Ordering Fix (D6)

The drive tick moves from position 4 (before strategy, userModel,
mentalModel) to DERIVED phase (after SOURCE and SOURCE_PER_SUBJECT).
This fixes the data staleness for:

- `CompetenceDrive` → reads `StrategyLearningOrchestrator.engagementTrend()`
- `AffiliationDrive` → reads `UserModelOrchestrator.activeProfiles()`
- `AutonomyDrive` → reads `MentalModelOrchestrator.activeSnapshots()`

All three now see current-tick data instead of one-tick-stale data.
`CuriosityDrive` → `MemoryHygieneOrchestrator.knowledgeGaps()` is
unaffected (memoryHygiene is FOUNDATION, already ran first).

This aligns with the #136 spec ordering constraint: "source
orchestrators → DriveOrchestrator → GoalProposalOrchestrator."

## Scope Boundary

**In scope:**
- `CognitionPhase` enum in `blocks-core`
- `CognitionTickParticipant` and `CognitionTickContext` in `blocks-core`
- Refactored `CognitionCore.tick()` with phase-based dispatch
- `addParticipant()` registration on CognitionCore
- InnerLife as TERMINAL participant (remove drive re-tick, add `lastTick()`)
- Drive ordering fix
- `ProactiveSpeechSupport` / `SocialAvatarCognition` adaptation

**Out of scope:**
- Timer-based scheduling (consumer concern — they use
  `ScheduledExecutorService` or Quarkus `@Scheduled`)
- Event-based tick triggering (consumer concern)
- YAML configuration of phases (future #246 scope)
- Per-subject custom participants

## Testing Strategy

- **CognitionCoreTest**: verify phase ordering — mock orchestrators,
  assert tick call order via `InOrder`
- **CognitionCoreTest**: verify custom participant execution at each
  phase, including error isolation
- **CognitionCoreTest**: verify `SOURCE_PER_SUBJECT` rejects custom
  participants
- **CognitionCoreTest**: verify `CognitionConfig` disable flags skip
  built-in orchestrators but not custom participants
- **InnerLifeOrchestratorTest**: verify `driveOrchestrator.tick()` is
  no longer called from `doTick()`
- **InnerLifeOrchestratorTest**: verify `lastTick()` returns cached
  result
- **InnerLifeOrchestratorTest**: verify tick with `channelContext=null`
  short-circuits when buffer is empty

## References

- CognitionCore.java — current tick ordering (lines 97-137)
- InnerLifeOrchestrator.java — redundant drive tick (line 97), content quality gate (lines 130-143)
- DriveOrchestrator.java — drive source dependencies (CuriosityDrive, CompetenceDrive, AffiliationDrive, AutonomyDrive)
- SocialAvatarCognition.java — evaluateProactive() invocation pattern
- ProactiveSpeechSupport.java — InnerLife tick delegation
- PipelineTickScheduler.java — existing tick pattern in summarisation
- #136 spec — ordering constraint: "source orchestrators → DriveOrchestrator → GoalProposalOrchestrator"
- ADR-0001 — compositor pattern (tick without record)
- Decision review R1-01/R1-02/R1-03 — InnerLife TERMINAL integration rationale

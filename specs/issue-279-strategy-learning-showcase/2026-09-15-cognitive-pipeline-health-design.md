# Cognitive Pipeline Health — Design Spec

**Issue:** casehubio/blocks#279 (broadened from strategy learning to full pipeline audit)
**Date:** 2026-09-15
**Scope:** Fix all "wired but not flowing" gaps across 8 cognitive orchestrators, add LLM proof tests

## Problem

The cognitive pipeline (CognitionCore → orchestrators → PromptSections) has structural gaps that prevent several subsystems from contributing to the showcase conversation. An audit of all 8 orchestrators revealed:

| Orchestrator | Status | Prompt contributes? | Root blocker |
|---|---|---|---|
| Mood | WORKING | Yes | — |
| MentalModel | WORKING | Yes | Heuristic path works; LLM inference blocked but non-critical |
| Drives | PARTIAL | Lopsided | CompetenceDrive=0 (cascading from Strategy), CuriosityDrive=baseline |
| UserModel | PARTIAL | Heuristic only | All signals NEUTRAL, LLM synthesis late, 1hr cooldown |
| Narrative | PARTIAL | Yes | CognitionStack bypass, not production pipeline |
| Strategy | GAP | Never | Signals never batch, reflect() never called, no seeding |
| Goals | GAP | Rarely | Cascading from Strategy, 60min cooldown, no CuriosityGoalMapper |
| MemoryHygiene | N/A | No section | Not wired in CognitionStack |

**Cascade chain:** Strategy broken → CompetenceDrive stuck → Goals blocked.

**Common structural pattern:** 1-signal-per-tick prevents batch thresholds from being reached. Each tick drains the signal buffer, but only 1 signal arrives between ticks, so batch-dependent processing never fires.

**Compile break:** ConversationRunner calls `recordInteraction()` with 5 args, but CognitionCore (post-#282) requires 6 args with `@Nullable CognitiveImpact`.

## Solution

### 1. Signal correlation + ConversationOutcome checkpoints (D1)

**Problem:** `CognitionCore.recordInteraction()` creates each `EngagementEvent` with a random UUID as `caseId`. `StrategyLearningOrchestrator.tick()` matches `TurnOutcome` signals to `ConversationOutcome` signals by `conversationId == caseId`. Random UUIDs mean turns can never be correlated with a conversation.

**Fix in CognitionCore:**
- Carry conversationId via `CognitiveImpact.strategySignal()`. When the caller provides a `CognitiveImpact` with a pre-built `EngagementSignal.TurnOutcome` (containing the shared caseId on its `EngagementEvent`), CognitionCore uses that signal directly instead of constructing one with a random UUID. This is the existing override path from #282 — no new parameters needed.
- When `impact` is null or `impact.strategySignal()` is null, CognitionCore falls back to constructing a TurnOutcome with a random UUID (current behavior, backward compatible).

**Fix in ConversationRunner:**
- Generate a stable conversation ID at the start of `run()` (e.g., `tenantId + "-conv-" + UUID`).
- Pass it through every `recordInteraction()` call so all turns share the same caseId.
- At conversation end (and optionally at checkpoint intervals), record a `ConversationOutcome` signal with the same conversationId:
  ```java
  strategy.record(new EngagementSignal.ConversationOutcome(
      conversationId, "Conversation summary", turnCount),
      agentId, subjectId, tenantId);
  ```
- For this branch, a "conversation" is the full ConversationRunner run. Platform SPI for conversation boundary detection is a follow-on.

**Fix the compile break:** Update ConversationRunner's `recordInteraction` call to pass 6 args (add `null` for `CognitiveImpact`, or construct one with the conversationId).

### 2. Auto-trigger reflect() asynchronously in tick() (D2)

**Problem:** `reflect()` is the only method that produces `StrategyProfile` with guidelines, but nobody calls it.

**Fix in StrategyLearningOrchestrator.tick():**
After storing one or more CBR cases (the `Learned` outcome), check if reflection should fire:
```java
if (casesStored > 0) {
    int totalCases = countAgentCases(state.agentId, state.tenantId);
    boolean enoughCases = totalCases >= config.minCasesForReflection();
    boolean cooldownElapsed = state.lastReflectTimestamp == null
        || Duration.between(state.lastReflectTimestamp, clock.instant())
            .compareTo(config.staleStateTimeout()) > 0;
    if (enoughCases && cooldownElapsed) {
        CompletableFuture.runAsync(() -> reflect(state.agentId, state.tenantId));
    }
}
```

**Async semantics:** `reflect()` runs in the background. `currentStrategy()` returns the old profile until the new one is stored. The existing `ReentrantLock` prevents concurrent reflect+tick conflicts. The next turn picks up the updated guidelines.

**Why async:** `tick()` runs synchronously in `CognitionCore.tick()` alongside mood, drives, userModel, etc. A synchronous LLM call (5-30s) would make one turn unpredictably slow. Async keeps tick latency predictable.

### 3. Neurocortex priming for cold-start (D3)

**Problem:** With no stored cases, reflect() never fires, so StrategyPromptSection never contributes.

**Fix — prime CBR store at CognitionStack construction:**

When `CognitionStack.from()` creates the `StrategyLearningOrchestrator` (at `Stage.REAL_DRIVES+`), also prime the CBR store with synthetic interaction cases derived from the agent's descriptor:

```java
private static void primeStrategyCases(CbrCaseMemoryStore store,
                                        AgentDescriptor descriptor,
                                        StrategyLearningConfig config) {
    var constraints = descriptor.constraints();
    if (constraints == null || constraints.isEmpty()) return;

    // Generate varied synthetic cases from constraints
    // Each case models a different interaction scenario
    // with deliberately varied feature vectors for trend analysis
    for (int i = 0; i < config.minCasesForReflection(); i++) {
        var features = buildSyntheticFeatures(descriptor, i);
        var summary = buildCaseSummary(constraints, i);
        var cbrCase = new FeatureVectorCbrCase(
            summary, "-", null, null, features, null, descriptor.name());
        store.store(cbrCase, config.engagementCaseType(),
            descriptor.name(), config.memoryDomain(), "showcase", null, Path.root());
    }
}
```

**Variance strategy:** Synthetic cases must have varied feature vectors so `TrendAnalyzer` produces meaningful trends:
- Vary `continuationRate` (0.6–0.9) — models different engagement levels
- Vary `meanAffectShift` (-0.1 to +0.3) — models emotional range
- Vary `avgResponseLength` (50–300) — models response depth variation
- Derive dimension snapshots from constraint semantics (e.g., precision constraint → high formality, low verbosity)

**First tick:** The auto-reflect (from fix 2) detects enough primed cases, fires async reflect. The LLM synthesises initial guidelines from the primed memories. By turn 2, `StrategyPromptSection` renders guidelines.

### 4. UserModel quality from mood appraisal (D4)

**Problem:** `CognitionCore.recordInteraction()` hardcodes `QualitySignal.NEUTRAL` for every interaction signal to UserModel.

**Fix in CognitionCore.recordInteraction():**
When `impact == null || impact.userModelSignal() == null` (default path), derive quality from the mood appraisal just recorded:

```java
QualitySignal quality = QualitySignal.NEUTRAL;
var currentMood = mood.currentMood(agentId, tenantId);
if (currentMood.isPresent()) {
    var m = currentMood.get();
    double pleasure = m.pleasure();
    double arousal = m.arousal();
    if (arousal > 0.3 && pleasure > 0.1) {
        quality = QualitySignal.POSITIVE;   // engaged + positive
    } else if (arousal < -0.1 && pleasure < -0.1) {
        quality = QualitySignal.NEGATIVE;   // bored + negative
    }
}
var signal = new InteractionSignal.CustomSignal(userMessage, quality);
```

**Why pleasure+arousal:** Pleasure alone conflates emotional valence with relationship quality. Challenging intellectual debates (Tesla vs Leonardo) produce low pleasure (cognitive strain) but high arousal (engagement). High arousal + positive pleasure = genuine positive quality. Low arousal + negative pleasure = boredom/disengagement = genuine negative quality. The CognitiveImpact.userModelSignal() path (#282) remains the explicit override for consumers needing full control.

### 5. Goals — cooldown, CuriosityDrive chain, and missing mapper (D5)

**Problem 5a — 60-minute cooldown:** `GoalProposalConfig.defaults().cooldown()` is `Duration.ofMinutes(60)`. The showcase completes in minutes.

**Fix:** The YAML-compiled `CompiledCognition` should specify a showcase-appropriate cooldown. Add a turn-based or short-duration cooldown in the cognition YAML (e.g., `cooldown: 30s` or a turn-count-based mechanism). If the config model doesn't support turn-based cooldown, use a short wall-clock duration (30 seconds) as an approximation.

**Problem 5b — CuriosityDrive not wired:** `CognitionStack.from()` uses a flat lambda `(a,t) -> 0.5` for curiosity instead of the real `CuriosityDrive`. The real implementation requires `MemoryHygieneOrchestrator`, which is passed as `null` to `CognitionCore`.

**Fix — full wiring chain:**
```java
// In CognitionStack.from(), at REAL_DRIVES+ stage:
var memoryHygiene = new MemoryHygieneOrchestrator(
    new CompositeConfidenceScorer(List.of(new ArousalScorer(), new SurpriseScorer())),
    RetentionConfig.defaults(),
    cbrStore);
var curiosityDrive = new CuriosityDrive(memoryHygiene);

// Pass memoryHygiene to CognitionCore (instead of null)
var core = new CognitionCore(mood, drives, userModel, mentalModel,
    strategy, narrative, goals, memoryHygiene, agentProvider);
```

At `Stage.FULL`, also wire `CuriosityGoalMapper`:
```java
var mapperList = List.<DriveGoalMapper>of(
    new CuriosityGoalMapper(curiosityDrive),  // NEW
    new CompetenceGoalMapper(competence),
    new AffiliationGoalMapper(affiliation, 0.3, Duration.ofHours(1)),
    new AutonomyGoalMapper(autonomy, 0.5));
```

### 6. LLM proof tests (D6)

**Per-orchestrator tests** in `HistoricalEncounterLlmTest`, complementing existing stage tests:

| Test | Asserts | Turns |
|------|---------|-------|
| `strategyContributesByTurn4()` | StrategyPromptSection returns non-null text by turn 4 (primed cases → async reflect → guidelines) | 4 |
| `moodEvolvesAcrossTurns()` | MoodPromptSection shows different PAD values at turn 1 vs turn 6 | 6 |
| `mentalModelFormsBeliefsAndDesires()` | MentalModelPromptSection renders beliefs + desires for the other agent by turn 4 | 4 |
| `userModelDifferentiatesQuality()` | UserModelPromptSection shows non-NEUTRAL quality signals by turn 6 | 6 |
| `drivesReflectRealSources()` | DrivePromptSection shows non-zero competence and curiosity by turn 6 | 6 |
| `goalsProposedByTurn6()` | GoalPromptSection renders at least one goal proposal by turn 6 | 6 |
| `narrativeFormsEpisodesAndThemes()` | NarrativePromptSection renders episodes + theme by turn 6 | 6 |

**Nondeterminism handling:** Use soft assertions with retry tolerance. Each test runs the conversation twice; assert the pipeline contributes in at least 1 of 2 runs. This bounds flakiness while proving the pipeline works.

**Cost:** ~7 tests × 2 runs × 4-6 turns × ~3 LLM calls/turn = ~250-500 LLM calls. Run under `-Pllm` on demand, not every CI build.

### 7. Additional fixes (no design decision needed)

- **Compile break:** Update ConversationRunner to call 6-arg `recordInteraction()` with `null` CognitiveImpact (or a constructed one carrying the conversation ID).
- **CompetenceDrive cascade:** Automatically unblocked by fixes 1+2+3. Once strategy produces a profile, `engagementTrend()` returns data, CompetenceDrive computes real intensity.

## Dependency order

```
Fix 1 (signal correlation) → Fix 2 (async auto-reflect) → Fix 3 (priming)
                                                          ↗
Fix 4 (quality signals) ————————————————————————————————→ Fix 6 (tests)
Fix 5 (goals chain) ———————————————————————————————————→
Fix 7 (compile break) — first, enables all other fixes
```

Fix 7 (compile break) goes first — nothing else compiles without it.
Fixes 1→2→3 are the strategy cascade chain.
Fixes 4, 5 are independent.
Fix 6 (tests) comes last — proves everything works.

## Testing

Plain JUnit 5 (no Quarkus runtime, per project convention) for unit tests of the modified orchestrator methods. LLM tests under `-Pllm` profile for end-to-end proof.

**Unit tests (no LLM):**
- `StrategyLearningOrchestratorTest` — verify tick() auto-triggers async reflect when case count crosses threshold; verify ConversationOutcome matching with shared conversationId
- `CognitionCoreTest` — verify recordInteraction with conversationId sets caseId correctly; verify quality derivation from mood pleasure+arousal
- `GoalProposalOrchestratorTest` — verify turn-based or short cooldown allows sequential proposals
- `CognitionStackTest` — verify MemoryHygieneOrchestrator is wired; verify CuriosityDrive + CuriosityGoalMapper construction at FULL stage; verify synthetic case priming populates CBR store

**LLM tests (-Pllm):**
- Per-orchestrator proof tests as specified in Fix 6

## Migration

No public API changes. All fixes are internal to:
- `CognitionCore.recordInteraction()` — add conversationId plumbing, quality derivation
- `StrategyLearningOrchestrator.tick()` — add async auto-reflect check
- `CognitionStack.from()` — wire MemoryHygieneOrchestrator, CuriosityDrive, CuriosityGoalMapper, prime CBR store
- `ConversationRunner.run()` — stable conversation ID, ConversationOutcome emission, 6-arg recordInteraction

## Follow-on issues

- **#283** — Directive-minimal architecture: shift cognitive data from briefing to neurocortex. This branch demonstrates the principle for strategy (primed memories → emergent guidelines). #283 generalises to goals, beliefs, and the full knowledge import pipeline.
- **Conversation boundary SPI** — Platform contract for conversation boundary detection and ConversationOutcome emission. Production consumers need this; the showcase uses a simple "one conversation = one run" contract.
- **Adaptive quality strategy** — A/B testing for UserModel quality signal derivation. When mood-derived quality isn't differentiating well, switch to InteractionMapper path.
- **Strategy dimension extensibility** — Make DEFAULT_DIMENSIONS configurable via StrategyLearningConfig instead of hardcoded.

## References

- `StrategyLearningOrchestrator.java:40-606` — full strategy orchestrator (signal recording, tick, reflect)
- `StrategyPromptSection.java:8-25` — prompt rendering from StrategyProfile
- `StrategyProfile.java:26-33` — toPromptSection() returns empty when guidelines empty
- `CognitionCore.java:33-372` — orchestrator composition, recordInteraction, tick dispatch
- `CognitionStack.java:59-513` — test-scope orchestrator wiring with Stage enum
- `ConversationRunner.java:22-208` — conversation loop driving the showcase
- `GoalProposalConfig.java` — 60-minute cooldown default
- `CuriosityDrive.java` — requires MemoryHygieneOrchestrator
- `EngagementSignal.java:9-36` — TurnOutcome + ConversationOutcome sealed hierarchy
- Issue #282 spec — CognitiveImpact and 6-arg recordInteraction
- Issue #258 spec — historical encounter showcase architecture
- Issue #261 spec — measurement-driven cognition stages
- Issue #283 — directive-minimal architecture (follow-on)

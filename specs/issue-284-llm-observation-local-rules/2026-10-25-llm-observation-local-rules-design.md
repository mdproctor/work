# LLM-Driven Observation & Local Rules — Design Spec

**Issue:** casehubio/agency#284
**Parent epic:** casehubio/engine#1104 (Hive Mind)
**Date:** 2026-10-25

## Summary

LLM-backed interpretation layer for the engine's stigmergy model. Agents use LLM reasoning to interpret full environment state and decide actions — replacing static pattern matching with semantic understanding. Built as compositions of existing summarisation primitives, not a new framework. Mirrors the engine's two-phase pipeline (observe then decide) with separated observation and action summarisers by default.

## Architecture

The LLM layer sits **alongside** the engine's synchronous tick loop, not inside it. The engine's `EnvironmentObserver` SPI is designed for bounded-latency deterministic observers (100ms timeout). LLM calls have unbounded latency (30s to 60min). The event-driven model avoids blocking the engine's sequential evaluation pipeline (`observations()` → `localRules()` → `convergenceDetection()`), which would stall the entire case for the duration of the LLM call.

The default composition mirrors the engine's two-phase pipeline:

1. **Observation phase** — accumulate context changes, send batch to LLM, produce `List<Observation>`
2. **Action phase** — read observations + environment state, send to LLM, produce `List<RuleAction>`
3. **Write-back** — async subscriber deposits results into the engine environment, triggering a new tick

```
Context Changes (engine events)
    │
    ├──→ Engine Tick Loop (synchronous, bounded, deterministic)
    │    ├── observers (ThresholdObserver, CorrelationObserver, etc.)
    │    ├── localRules (RuleCondition → RuleAction)
    │    └── convergenceDetection
    │
    └──→ LLM Interpretation Layer (async, unbounded, agency-owned)
         │
         ├── Phase 1: Observation
         │    ├── EventAccumulator (buffer context changes)
         │    ├── EmissionPolicy (change-gated: only invoke when watched keys changed)
         │    ├── Compactor (dedup redundant changes)
         │    ├── ContentSummariser template (LLM → List<Observation>)
         │    ├── Concurrency guard (skip if previous call in-flight)
         │    └── EventStreamBus → EnvironmentWriteBack (async)
         │              │
         │              ▼ observations stored in ObservationRegistry
         │
         ├── Phase 2: Action (optional, triggered by observation results)
         │    ├── Reads observations + environment state
         │    ├── ContentSummariser template (LLM → List<RuleAction>)
         │    └── EventStreamBus → EnvironmentWriteBack (async)
         │              │
         │              ▼ signals deposited, context written
         │
         └── triggers new engine tick ───► deterministic rules react
```

## Core Principle: Composition via Builder Templates

No new runner types. The LLM interpretation layer is a **named configuration** of existing summarisation primitives. `LlmSummarisers` provides factory methods that produce pre-configured `ContentSummariser` implementations. These plug into the existing `SummarisationRunner` via `asSummariser()`.

End users can:
- Use named templates for common use cases (observation, action, combined, narration)
- Compose custom configurations from the same builder for novel use cases
- Mix LLM-backed and deterministic summarisers in the same pipeline

## New Types

### `ObservationResult` (record)

The output of an LLM observation phase. Carries detected patterns and batch correlation metadata.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public record ObservationResult(
    List<Observation> observations,
    double confidence,
    String reasoning,
    Instant batchStart,
    Instant batchEnd,
    int batchEventCount,
    InvocationMetadata invocationMetadata
) {}
```

### `RecommendedAction` (record)

Wraps an engine `RuleAction` with LLM attribution — which observations justify this action.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public record RecommendedAction(
    RuleAction action,
    List<String> justifyingPatternIds,
    double confidence
) {}
```

### `ActionResult` (record)

The output of an LLM action phase. Carries recommended actions with per-action attribution and batch correlation metadata.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public record ActionResult(
    List<RecommendedAction> recommendedActions,
    double confidence,
    String reasoning,
    InvocationMetadata invocationMetadata
) {}
```

### `InterpretationResult` (record)

The output of a combined LLM call (observation + action in one pass). Used by the `combined()` template when callers explicitly opt into coupling.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public record InterpretationResult(
    List<Observation> observations,
    List<RecommendedAction> recommendedActions,
    double confidence,
    String reasoning,
    Instant batchStart,
    Instant batchEnd,
    int batchEventCount,
    InvocationMetadata invocationMetadata
) {}
```

### `LlmInvocationPolicy` (record)

Caller-supplied latency expectations. Only the caller who builds the prompt knows the expected complexity.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public record LlmInvocationPolicy(
    Duration expectedDuration,
    Duration warnAfter,
    Duration errorAfter
) {
    public static LlmInvocationPolicy of(
            Duration expected, Duration warn, Duration error) {
        return new LlmInvocationPolicy(expected, warn, error);
    }
}
```

The summariser template monitors actual invocation duration against these thresholds. Logs warnings and cancels/errors at the caller's specified limits.

### `LlmSummarisers` (factory class)

Named templates that produce pre-configured `ContentSummariser` implementations.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public final class LlmSummarisers {

    // Phase 1: Observation — interpret environment, produce Observation records
    public static <E> ContentSummariser<E, ObservationResult> observation(
            AgentProvider agentProvider,
            String systemPrompt,
            LlmInvocationPolicy policy,
            ObservationRenderer<E> promptRenderer) { ... }

    // Phase 2: Action — read observations + state, produce RuleAction recommendations
    public static ContentSummariser<ObservationResult, ActionResult> action(
            AgentProvider agentProvider,
            String systemPrompt,
            LlmInvocationPolicy policy) { ... }

    // Combined: observation + action in one call (explicit opt-in)
    public static <E> ContentSummariser<E, InterpretationResult> combined(
            AgentProvider agentProvider,
            String systemPrompt,
            LlmInvocationPolicy policy,
            ObservationRenderer<E> promptRenderer) { ... }

    // Builder for custom configurations
    public static <E, R> InterpretationBuilder<E, R> builder(AgentProvider agentProvider) { ... }
}
```

Each template internally:
1. Renders the batch into a prompt using the observation pipeline infrastructure (`ObservationRenderer<E>`, `AffordanceRenderer`, `CognitiveObservationSections`) — NOT a parallel rendering path
2. Prepends the system prompt with phase-specific instructions
3. If previous result exists, includes it for continuity
4. Invokes the LLM via `StructuredAgentInvoker`
5. Monitors invocation duration against `LlmInvocationPolicy`
6. Wraps the response with batch correlation metadata

### `InterpretationBuilder<E, R>` (builder)

For custom compositions beyond the named templates.

```java
public static class InterpretationBuilder<E, R> {
    InterpretationBuilder<E, R> systemPrompt(String prompt);
    InterpretationBuilder<E, R> invocationPolicy(LlmInvocationPolicy policy);
    InterpretationBuilder<E, R> promptRenderer(ObservationRenderer<E> renderer);
    InterpretationBuilder<E, R> model(String modelId);
    InterpretationBuilder<E, R> outputMode(OutputMode mode);
    InterpretationBuilder<E, R> watchedKeys(Set<String> keys);
    ContentSummariser<E, R> build();
}
```

### `EnvironmentWriteBack` (helper)

Pre-built `EventStreamBus` subscriber that routes results to the engine environment. Dispatches asynchronously via `CompletableFuture.runAsync()` to avoid blocking the LLM thread during signal deposits, context writes, and observation storage.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public final class EnvironmentWriteBack {

    public static void subscribeObservations(
            EventStreamBus<ObservationResult> bus,
            ObservationRegistry observationRegistry,
            UUID caseId,
            String agentId,
            ExecutorService executor) { ... }

    public static void subscribeActions(
            EventStreamBus<ActionResult> bus,
            SignalRegistry signalRegistry,
            UUID caseId,
            ExecutorService executor) { ... }

    public static void subscribeCombined(
            EventStreamBus<InterpretationResult> bus,
            ObservationRegistry observationRegistry,
            SignalRegistry signalRegistry,
            UUID caseId,
            String agentId,
            ExecutorService executor) { ... }
}
```

Each subscriber:
1. Dispatches write-back via `CompletableFuture.runAsync(() -> ..., executor)`
2. For observations: stores in `ObservationRegistry` for the agent
3. For actions: deposits signals, writes context
4. Logs batch correlation metadata for observability

### `OutputMode` (enum)

```java
public enum OutputMode {
    OBSERVATIONS_ONLY,
    ACTIONS_ONLY,
    OBSERVATIONS_AND_ACTIONS
}
```

### `ChangeGatedEmissionPolicy<E>` (EmissionPolicy implementation)

Custom `EmissionPolicy` that gates LLM invocation on watched-key changes. Composes with `WindowPolicy`-based time/count gating.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public class ChangeGatedEmissionPolicy<E> implements EmissionPolicy<E, Object> {

    public ChangeGatedEmissionPolicy(
            Set<String> watchedKeys,
            Function<LevelEvent<E>, Set<String>> changedKeyExtractor,
            EmissionPolicy<E, ?> delegate) { ... }

    @Override
    public boolean shouldEmit(List<LevelEvent<E>> buffer, Object state, long now) {
        // Check delegate (time/count window) AND watched-key intersection
    }
}
```

### `InFlightGuard<E, R>` (concurrency guard)

Wraps a `ContentSummariser` to skip invocation when a previous call is still in-flight. The accumulator continues collecting events; they will be processed in the next batch.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public class InFlightGuard<E, R> implements ContentSummariser<E, R> {

    public InFlightGuard(ContentSummariser<E, R> delegate) { ... }

    @Override
    public CompletionStage<R> summarise(List<E> items, R previous) {
        // If previous call in-flight, return previous result unchanged
        // Otherwise delegate and track in-flight status
    }
}
```

## Structured JSON Output & Conversion

The LLM produces structured JSON conforming to LLM-facing response records. Templates convert these into engine-facing records. This decouples the prompt schema from engine internals and enables per-item attribution.

### LLM-Facing Records (what the LLM produces)

```java
// Observation phase JSON output
record LlmObservationResponse(List<LlmObservation> observations) {}

record LlmObservation(
    String patternId,
    double confidence,
    List<String> evidence,
    List<String> derivedFrom,
    Map<String, Object> details,
    String reasoning
) {}

// Action phase JSON output
record LlmActionResponse(List<LlmRecommendation> actions) {}

record LlmRecommendation(
    String actionType,                 // "DepositSignal", "WriteContext", etc.
    Map<String, Object> parameters,    // action-type-specific params
    List<String> justifyingPatternIds, // which observations justify this
    double confidence,
    String reasoning
) {}
```

### Conversion: LLM Records → Engine Records

Templates handle this conversion internally:

**`LlmObservation` → `Observation`** (engine record):
- `patternId` → `Observation.patternId()`
- `confidence` → `Observation.confidence()`
- `evidence` + `derivedFrom` + `details` + `reasoning` → `Observation.details()` (serialized as `Map<String, JsonNode>`)
- `Instant.now()` → `Observation.timestamp()`
- `derivedFrom` enables hierarchical observations: leaf observations have empty `derivedFrom`, derived observations reference other `patternId`s they build on

**`LlmRecommendation` → `RecommendedAction`**:
- `actionType` + `parameters` → `RuleAction` variant (e.g., `actionType: "DepositSignal"`, `parameters: {name: "anomaly", strength: 0.85}` → `new RuleAction.DepositSignal("anomaly", 0.85, null)`)
- `justifyingPatternIds` → `RecommendedAction.justifyingPatternIds()`
- `confidence` → `RecommendedAction.confidence()`

The conversion from `actionType` + `parameters` to the correct `RuleAction` sealed variant uses a switch on `actionType`:
- `"DepositSignal"` → `new RuleAction.DepositSignal(name, strength, halfLife)`
- `"WriteContext"` → `new RuleAction.WriteContext(key, value)`
- `"RegisterInterest"` → `new RuleAction.RegisterInterest(declaration)`
- `"DeregisterInterest"` → `new RuleAction.DeregisterInterest(interestId)`
- `"Leave"` → `new RuleAction.Leave()`

### Prompt Assembly & Key Identification

Context changes are presented to the LLM with identifiable structure so the LLM can cite them as evidence. The `Compactor` ensures each key appears at most once per batch (latest value wins), keeping the prompt focused:

```
Environment state (5 keys changed since last evaluation):
  risk.score: 0.82 (was: 0.45)
  transaction.amount: 150000
  transaction.currency: "USD"
  counterparty.jurisdiction: "high-risk"
  account.balance: 3200000

Active signals:
  velocity-alert: strength 0.7 (deposited 30s ago)
```

The LLM cites what it based each observation on. `evidence` is free-form — could be key names, event descriptions, or quotes from the prompt. `derivedFrom` gives structural hierarchy between observations:
```json
{
  "observations": [
    {
      "patternId": "risk-spike",
      "confidence": 0.92,
      "evidence": ["risk.score"],
      "derivedFrom": [],
      "details": {"riskDelta": 0.37, "previousValue": 0.45},
      "reasoning": "Risk score jumped 82% in a single evaluation cycle"
    },
    {
      "patternId": "high-value-high-risk",
      "confidence": 0.88,
      "evidence": ["transaction.amount", "counterparty.jurisdiction"],
      "derivedFrom": ["risk-spike"],
      "details": {"threshold": "regulatory"},
      "reasoning": "Risk spike coincides with high-value transaction to high-risk jurisdiction"
    }
  ]
}
```

### Attribution Trace

The full causal chain from environment change to deposited signal, with hierarchical observations:

```
context keys (risk.score)
    │  LlmObservation.evidence
    ▼
Observation (patternId: "risk-spike", confidence: 0.92, derivedFrom: [])
    │  LlmObservation.derivedFrom
    ▼
context keys (transaction.amount, counterparty.jurisdiction) + derivedFrom: ["risk-spike"]
    │  LlmObservation.evidence + derivedFrom
    ▼
Observation (patternId: "high-value-high-risk", confidence: 0.88)
    │  RecommendedAction.justifyingPatternIds
    ▼
RecommendedAction (action: DepositSignal("aml:escalate", 0.88), justifying: ["high-value-high-risk"])
    │  EnvironmentWriteBack
    ▼
Signal deposited in engine → deterministic rules react
```

## Usage Example — Separated (Default)

```java
// Phase 1: Observation summariser
ContentSummariser<ContextChange, ObservationResult> observer =
    LlmSummarisers.observation(
        agentProvider,
        "You are analyzing financial transaction patterns. "
            + "Report detected anomalies as observations with confidence scores.",
        LlmInvocationPolicy.of(
            Duration.ofSeconds(30),    // expected
            Duration.ofMinutes(1),     // warn
            Duration.ofMinutes(5)),    // error
        transactionObservationRenderer);

// Phase 2: Action summariser
ContentSummariser<ObservationResult, ActionResult> advisor =
    LlmSummarisers.action(
        agentProvider,
        "Given these observations, recommend actions: "
            + "deposit signals for patterns, write context for state updates.",
        LlmInvocationPolicy.of(
            Duration.ofSeconds(45),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10)));

// Wire observation pipeline
var obsBus = new EventStreamBus<ObservationResult>();
var obsRunner = SummarisationRunner.builder(
        new InFlightGuard<>(observer).asSummariser(), obsBus, OBSERVATION_LEVEL)
    .emissionPolicy(new ChangeGatedEmissionPolicy<>(
        Set.of("transaction.*", "risk.*"),
        e -> e.payload().changedKeys(),
        EmissionPolicy.anyOf(WindowPolicy.of(5_000, 10))))
    .compactor(contextChangeCompactor)
    .build();

// Wire action pipeline (triggered by observation results)
var actBus = new EventStreamBus<ActionResult>();
var actRunner = SummarisationRunner.builder(
        advisor.asSummariser(), actBus, ACTION_LEVEL)
    .windowPolicy(WindowPolicy.ofCount(1))  // act on each observation result
    .build();

// Connect: observation output feeds action input
obsBus.subscribe(e -> true, obsEvent -> {
    actRunner.collect(new LevelEvent<>(obsEvent.payload(),
        System.currentTimeMillis(), OBSERVATION_LEVEL, tenancyId));
});

// Wire environment write-back (async)
EnvironmentWriteBack.subscribeObservations(obsBus, observationRegistry, caseId, agentId, executor);
EnvironmentWriteBack.subscribeActions(actBus, signalRegistry, caseId, executor);

// Feed events and tick
obsRunner.collect(new LevelEvent<>(contextChange, timestamp, INPUT_LEVEL, tenancyId));
obsRunner.tick(now);
actRunner.tick(now);
```

## Relationship to Existing Patterns

| Pattern | Uses | Role |
|---------|------|------|
| `ContentSummariser<T,R>` | Directly implements | The processing SPI — batch + previous → result |
| `.asSummariser()` | Bridge | Converts to `StatefulSummariser` for the runner |
| `SummarisationRunner` | Unchanged | Handles accumulation, windowing, compaction, ticking |
| `EventStreamBus` | Unchanged | Output routing (async write-back subscribers) |
| `EventAccumulator` | Via runner | Event buffering |
| `WindowPolicy` / `EmissionPolicy` | Via runner | Emission gating (change-gated for LLM) |
| `Compactor` | Via runner | Pre-LLM deduplication |
| `StructuredAgentInvoker` | Internal to template | LLM invocation with structured output |
| `InvocationMetadata` | Captured | Token usage, cost, duration |
| `ObservationRenderer<E>` | Prompt assembly | Existing observation rendering for LLM prompts |
| `AffordanceRenderer` | Prompt assembly | Entity/action grounding chains |
| `CognitiveObservationSections` | Prompt assembly | Goals, beliefs, trust, norms sections |

## What This Does NOT Do

- Does NOT implement `EnvironmentObserver` — event-driven, not synchronous SPI
- Does NOT change `SummarisationRunner` — composes existing infrastructure
- Does NOT create a parallel "digest" concept — named templates for `ContentSummariser`
- Does NOT hardcode timeouts — caller supplies `LlmInvocationPolicy`
- Does NOT change the engine — writes back via standard signals/context/observations
- Does NOT create parallel rendering infrastructure — integrates with existing observation pipeline

## Testing Strategy

Plain JUnit 5 with Mockito (no Quarkus runtime in agency tests):

1. **Result record construction and validation** — `ObservationResult`, `ActionResult`, `InterpretationResult` semantics
2. **`LlmInvocationPolicy` monitoring** — test warning/error thresholds against mock durations
3. **`LlmSummarisers` templates** — mock `AgentProvider`, verify prompt assembly, response parsing
4. **Separated pipeline** — verify observation output feeds action input correctly
5. **Combined template** — verify single-call mode produces both observations and actions
6. **Prompt rendering integration** — verify `ObservationRenderer` / `AffordanceRenderer` output in prompts
7. **`EnvironmentWriteBack`** — mock registries, verify async dispatch, observations stored, signals deposited
8. **`InFlightGuard`** — verify skip when previous call in-flight, resume after completion
9. **`ChangeGatedEmissionPolicy`** — verify watched-key intersection gating, no invocation on irrelevant changes
10. **End-to-end composition** — full runner pipeline with mock LLM, verify accumulation → processing → write-back
11. **Previous result continuity** — verify `StatefulSummariser` state management across ticks
12. **Compactor integration** — verify redundant context changes are removed before LLM invocation

## Dependencies

**Compile:** `casehub-agency-summarisation-api` (EventAccumulator, WindowPolicy, EmissionPolicy, ContentSummariser, SummarisationRunner, EventStreamBus, LevelEvent), `casehub-engine-api` (Observation, RuleAction, ObservationRegistry, SignalRegistry)
**Provided:** `casehub-platform-agent-api` (AgentProvider, AgentSessionConfig)
**Internal:** `StructuredAgentInvoker`, `InvocationMetadata` (agency-core invoke package), `ObservationRenderer`, `AffordanceRenderer`, `CognitiveObservationSections` (agency-core perception packages)

## References

- engine `CaseContextChangedEventHandler.observations()` — tick loop architecture, 100ms hardcoded timeout, virtual threads
- engine `CaseContextChangedEventHandler.evaluateAndDispatch()` — sequential pipeline: observations → localRules → convergenceDetection
- engine `EnvironmentObserver`, `Observation`, `ObservationContext` — SPIs we integrate with (not implement)
- engine `LocalRule`, `RuleAction`, `RuleSpace` — action model for LLM recommendations
- engine `SignalRegistry`, `ObservationRegistry` — environment write-back targets
- agency `ContentSummariser<T,R>`, `SummarisationRunner` — composition primitives
- agency `EmissionPolicy<IN,S>` — composable emission gating
- agency `ObservationRenderer<E>`, `AffordanceRenderer`, `CognitiveObservationSections` — prompt assembly
- agency `StructuredAgentInvoker` — LLM invocation utility
- wacky-manor `NarratorAgent` (examples/wacky-manor) — existing pattern for tick-driven LLM batch processing
- casehubio/engine#1104 — parent Hive Mind epic
- casehubio/engine#1105 — Environment observation SPI (closed)
- casehubio/engine#1109 — Local rule evaluation (closed)

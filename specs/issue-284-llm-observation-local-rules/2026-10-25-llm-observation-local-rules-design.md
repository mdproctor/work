# LLM-Driven Observation & Local Rules — Design Spec

**Issue:** casehubio/agency#284
**Parent epic:** casehubio/engine#1104 (Hive Mind)
**Date:** 2026-10-25

## Summary

LLM-backed interpretation layer for the engine's stigmergy model. Agents use LLM reasoning to interpret full environment state and decide actions — replacing static pattern matching with semantic understanding. Built as compositions of existing summarisation primitives, not a new framework.

## Architecture

The LLM layer sits **alongside** the engine's synchronous tick loop, not inside it. The engine's `EnvironmentObserver` SPI is designed for bounded-latency deterministic observers (100ms timeout). LLM calls have unbounded latency (30s to 60min). Rather than forcing an impedance mismatch, the LLM layer:

1. Subscribes to context change events (same trigger as the tick loop)
2. Accumulates changes via `EventAccumulator` with `WindowPolicy` gating
3. Optionally compacts redundant changes via `Compactor`
4. Sends the batch to an LLM via `ContentSummariser<E, InterpretationResult>`
5. Publishes results to `EventStreamBus`
6. A subscriber writes results back to the engine environment (signals, context, observations)
7. The environment write triggers a new tick — deterministic observers and rules react to the LLM output

```
Context Changes (engine events)
    │
    ├──→ Engine Tick Loop (synchronous, bounded, deterministic)
    │    ├── observers (ThresholdObserver, CorrelationObserver, etc.)
    │    ├── localRules (RuleCondition → RuleAction)
    │    └── convergenceDetection
    │
    └──→ LLM Interpretation Layer (async, unbounded, agency-owned)
         ├── EventAccumulator (buffer context changes)
         ├── WindowPolicy / EmissionPolicy (gate when to invoke LLM)
         ├── Compactor (dedup redundant changes)
         ├── ContentSummariser template (LLM call)
         ├── EventStreamBus (output routing)
         └── Subscriber (write-back to engine environment)
                └── triggers new tick ───► deterministic rules react
```

## Core Principle: Composition via Builder Templates

No new runner types. The LLM interpretation layer is a **named configuration** of existing summarisation primitives. `LlmSummarisers` provides factory methods that produce pre-configured `ContentSummariser` implementations. These plug into the existing `SummarisationRunner` via `asSummariser()`.

End users can:
- Use named templates for common use cases (interpretation, narration, classification)
- Compose custom configurations from the same builder for novel use cases
- Mix LLM-backed and deterministic summarisers in the same pipeline

## New Types

### `InterpretationResult` (record)

The output of an LLM environment interpretation. Carries both the interpretation and batch correlation metadata.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public record InterpretationResult(
    List<Observation> observations,
    List<RuleAction> recommendedActions,
    double confidence,
    String reasoning,
    Instant batchStart,
    Instant batchEnd,
    int batchEventCount,
    InvocationMetadata invocationMetadata
) {}
```

- `observations` — detected patterns as engine `Observation` records (patternId, confidence, details)
- `recommendedActions` — LLM-suggested actions as engine `RuleAction` instances (DepositSignal, WriteContext, RegisterInterest, etc.)
- `confidence` — overall interpretation confidence [0.0, 1.0]
- `reasoning` — human-readable explanation of the interpretation
- Batch metadata — which events produced this interpretation (correlation for traceability)
- `invocationMetadata` — token usage, cost, duration from `StructuredAgentInvoker`

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

    public static <E> ContentSummariser<E, InterpretationResult> interpretation(
            AgentProvider agentProvider,
            String systemPrompt,
            LlmInvocationPolicy policy,
            Function<List<E>, String> promptRenderer) { ... }

    public static <E> ContentSummariser<E, InterpretationResult> interpretation(
            AgentProvider agentProvider,
            String systemPrompt,
            LlmInvocationPolicy policy) { ... }

    // Builder for custom configurations
    public static <E> InterpretationBuilder<E> builder(AgentProvider agentProvider) { ... }
}
```

Each template internally:
1. Renders the batch into a prompt using the supplied `promptRenderer` (or a default JSON serializer)
2. Prepends the system prompt with interpretation instructions
3. If previous result exists, includes it for continuity ("Your previous interpretation was: ...")
4. Invokes the LLM via `StructuredAgentInvoker.invoke(agentProvider, config, InterpretationResult.class)`
5. Monitors invocation duration against `LlmInvocationPolicy`
6. Wraps the response in `InterpretationResult` with batch correlation metadata

### `InterpretationBuilder<E>` (builder)

For custom compositions beyond the named templates.

```java
public static class InterpretationBuilder<E> {
    InterpretationBuilder<E> systemPrompt(String prompt);
    InterpretationBuilder<E> invocationPolicy(LlmInvocationPolicy policy);
    InterpretationBuilder<E> promptRenderer(Function<List<E>, String> renderer);
    InterpretationBuilder<E> model(String modelId);
    InterpretationBuilder<E> outputMode(OutputMode mode);  // OBSERVATIONS_AND_ACTIONS, OBSERVATIONS_ONLY
    ContentSummariser<E, InterpretationResult> build();
}
```

### `EnvironmentWriteBack` (helper)

Pre-built `EventStreamBus` subscriber that routes `InterpretationResult` to engine environment.

**Package:** `io.casehub.agency.stigmergy.llm`

```java
public final class EnvironmentWriteBack {

    public static void subscribe(
            EventStreamBus<InterpretationResult> bus,
            SignalRegistry signalRegistry,
            ObservationRegistry observationRegistry,
            UUID caseId,
            String agentId) { ... }
}
```

The subscriber:
1. Stores `observations` in `ObservationRegistry` for the agent
2. Executes `recommendedActions` — deposits signals, writes context, etc.
3. Logs batch correlation metadata for observability

### `OutputMode` (enum)

```java
public enum OutputMode {
    OBSERVATIONS_AND_ACTIONS,
    OBSERVATIONS_ONLY
}
```

## Usage Example

```java
// 1. Build the summariser template
ContentSummariser<ContextChange, InterpretationResult> interpreter =
    LlmSummarisers.interpretation(
        agentProvider,
        "You are analyzing financial transaction patterns...",
        LlmInvocationPolicy.of(
            Duration.ofSeconds(45),    // expected
            Duration.ofMinutes(2),     // warn
            Duration.ofMinutes(10)),   // error
        changes -> renderTransactionContext(changes));

// 2. Wire the pipeline (existing infrastructure)
var bus = new EventStreamBus<InterpretationResult>();
var runner = SummarisationRunner.builder(
        interpreter.asSummariser(), bus, INTERPRETATION_LEVEL)
    .windowPolicy(WindowPolicy.of(5_000, 10))  // 5s or 10 events
    .compactor(contextChangeCompactor)
    .build();

// 3. Wire the output
EnvironmentWriteBack.subscribe(bus, signalRegistry, observationRegistry, caseId, agentId);

// 4. Feed events and tick
runner.collect(new LevelEvent<>(contextChange, timestamp, INPUT_LEVEL, tenancyId));
runner.tick(now);
```

## Relationship to Existing Patterns

| Pattern | Uses | Role |
|---------|------|------|
| `ContentSummariser<T,R>` | Directly implements | The processing SPI — batch + previous → result |
| `.asSummariser()` | Bridge | Converts to `StatefulSummariser` for the runner |
| `SummarisationRunner` | Unchanged | Handles accumulation, windowing, compaction, ticking |
| `EventStreamBus` | Unchanged | Output routing |
| `EventAccumulator` | Via runner | Event buffering |
| `WindowPolicy` | Via runner | Emission gating |
| `Compactor` | Via runner | Pre-LLM deduplication |
| `StructuredAgentInvoker` | Internal to template | LLM invocation with structured output |
| `InvocationMetadata` | Captured | Token usage, cost, duration |

## What This Does NOT Do

- Does NOT implement `EnvironmentObserver` — event-driven, not synchronous SPI
- Does NOT change `SummarisationRunner` — composes existing infrastructure
- Does NOT create a parallel "digest" concept — named templates for `ContentSummariser`
- Does NOT hardcode timeouts — caller supplies `LlmInvocationPolicy`
- Does NOT change the engine — writes back via standard signals/context/observations

## Testing Strategy

Plain JUnit 5 with Mockito (no Quarkus runtime in agency tests):

1. **`InterpretationResult` construction and validation** — record semantics, batch metadata
2. **`LlmInvocationPolicy` monitoring** — test warning/error thresholds against mock durations
3. **`LlmSummarisers` templates** — mock `AgentProvider`, verify prompt assembly, response parsing
4. **Prompt rendering** — verify environment state is correctly serialized into prompts
5. **`EnvironmentWriteBack`** — mock registries, verify observations stored and signals deposited
6. **End-to-end composition** — full runner pipeline with mock LLM, verify accumulation → processing → write-back
7. **Previous result continuity** — verify `StatefulSummariser` state management across ticks
8. **Compactor integration** — verify redundant context changes are removed before LLM invocation

## Dependencies

**Compile:** `casehub-agency-summarisation-api` (EventAccumulator, WindowPolicy, ContentSummariser, SummarisationRunner, EventStreamBus, LevelEvent), `casehub-engine-api` (Observation, RuleAction, ObservationRegistry, SignalRegistry)
**Provided:** `casehub-platform-agent-api` (AgentProvider, AgentSessionConfig)
**Internal:** `StructuredAgentInvoker`, `InvocationMetadata` (agency-core invoke package)

## References

- engine `CaseContextChangedEventHandler.observations()` — tick loop architecture, 100ms timeout, virtual threads
- engine `EnvironmentObserver`, `Observation`, `ObservationContext` — SPIs we integrate with (not implement)
- engine `LocalRule`, `RuleAction`, `RuleSpace` — action model for LLM recommendations
- engine `SignalRegistry`, `ObservationRegistry` — environment write-back targets
- agency `ContentSummariser<T,R>`, `SummarisationRunner` — composition primitives
- agency `StructuredAgentInvoker` — LLM invocation utility
- wacky-manor `NarratorAgent` — existing pattern for tick-driven LLM batch processing
- casehubio/engine#1104 — parent Hive Mind epic
- casehubio/engine#1105 — Environment observation SPI (closed)
- casehubio/engine#1109 — Local rule evaluation (closed)

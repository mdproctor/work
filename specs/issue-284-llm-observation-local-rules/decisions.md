# Design Decisions — #284 LLM Observation & Local Rules

## D1: Execution model — event-driven, not synchronous SPI

**Choice:** The LLM layer sits alongside the engine's tick loop, not inside it. It does NOT implement `EnvironmentObserver`.
**Alternatives:**
- Implement `EnvironmentObserver` SPI with cache — impedance mismatch; LLM has unbounded latency, SPI assumes bounded (100ms timeout)
- Modify engine SPI to support async — unnecessary coupling; engine's synchronous model is correct for deterministic observers
**Rationale:** LLM calls have unbounded latency (30s to 60min). The engine's tick loop is designed for bounded-latency deterministic observers. Forcing an unbounded operation into a bounded interface creates a category error. The event-driven model lets the LLM take as long as it needs, writing results back into the environment to trigger subsequent ticks.
**Trade-offs:** One-tick latency between environment change and LLM response (events must accumulate before being processed). Deterministic observers react instantly; LLM interpretation lags.
**Sources:** engine `CaseContextChangedEventHandler.observations()` (100ms hardcoded timeout), engine `EnvironmentObserver` SPI
**Exploration:** deep-analysis
**Status:** captured

## D2: Built on summarisation pipeline infrastructure

**Choice:** The LLM interpretation layer reuses agency's existing summarisation primitives (`EventAccumulator`, `WindowPolicy`, `Compactor`, `Summariser`, `EventStreamBus`).
**Alternatives:**
- Custom accumulation/batching infrastructure — duplicates existing well-tested patterns
- Direct per-event LLM calls — too slow; LLM throughput improved by batching
**Rationale:** The summarisation framework already solves event accumulation, windowed emission, pre-summarisation compaction, and async dispatch. Wacky-manor's `NarratorAgent` demonstrates this exact pattern: events collect, window policy triggers, compactor deduplicates, LLM-backed summariser processes the batch.
**Trade-offs:** Couples the LLM interpretation layer to the summarisation-api module. But this is a natural dependency — the pattern IS summarisation.
**Sources:** `SummarisationRunner`, `EventAccumulator`, `WindowPolicy`, wacky-manor `NarratorAgent`
**Exploration:** quick
**Status:** captured

## D3: Reusable `LlmEventDigest` pattern with batch correlation

**Choice:** Introduce `LlmEventDigest<E, R>` as a first-class reusable pattern — wraps the 7-component wiring into a single concept. Results are correlated via `DigestResult<R>` carrying batch ID, time range, event count, and invocation metadata.
**Alternatives:**
- Manual wiring per consumer — too much ceremony, pattern not obvious
- Extend `SummarisationRunner` directly — adds LLM-specific concerns (latency policy, correlation) to a general-purpose component
**Rationale:** The "accumulate → batch → LLM → correlated result" pattern is reusable across stigmergy, wacky-manor narration, and future consumers. A first-class abstraction makes the pattern discoverable and reduces wiring errors. Batch-level correlation (not per-event) matches how the LLM processes — it interprets the batch holistically, not event by event.
**Trade-offs:** New type to maintain. But it composes existing primitives rather than replacing them.
**Sources:** wacky-manor `NarratorAgent` pattern, `SummarisationRunner` API
**Exploration:** deep-analysis
**Status:** captured

## D4: Caller-supplied latency policy

**Choice:** The caller who creates the prompt provides expected duration, warning threshold, and error threshold. The framework does not guess or hardcode timeouts.
**Alternatives:**
- Framework-hardcoded timeout — wrong for every use case; LLM latency depends on model, prompt complexity, provider load
- No timeout at all — no way to detect stuck calls
**Rationale:** Only the caller knows the expected complexity. A simple classification prompt takes ~30s; a deep multi-key analysis might take 5 minutes. The framework tracks actual duration, logs warnings, and cancels/errors at caller-specified thresholds.
**Trade-offs:** Requires callers to estimate complexity. But callers who build the prompt already have this intuition.
**Sources:** engine `RuleConfig.ruleEvaluationTimeoutMs()` (fixed timeout anti-pattern for LLM)
**Exploration:** deep-analysis
**Status:** captured

## D5: Combined observation + action by default

**Choice:** Single LLM call does both interpretation ("what do I observe?") and action recommendation ("what should I do?"). Observation-only mode available as an option.
**Alternatives:**
- Two separate calls (observe → decide) — mirrors engine's two-phase pipeline but 2x latency and token cost
- Action-only — misses the observation value (other agents can't react to LLM observations)
**Rationale:** LLMs naturally reason about observation and action holistically — splitting them is artificial. Single call is more token-efficient and produces more coherent reasoning. Observation-only mode is available for cases where LLM output should feed into other agents' deterministic rules.
**Trade-offs:** Coupled interpretation — can't use different models for observation vs. action. Observation-only mode available when separation is needed.
**Sources:** engine `observations()` → `localRules()` two-phase pipeline
**Exploration:** quick
**Depends on:** D1 (event-driven model makes this possible — no SPI boundary forces separation)
**Status:** captured

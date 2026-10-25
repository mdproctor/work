# Design Decisions — #284 LLM Observation & Local Rules

## D1: Execution model — event-driven, not synchronous SPI

**Choice:** The LLM layer sits alongside the engine's tick loop, not inside it. It does NOT implement `EnvironmentObserver`.
**Alternatives:**
- Implement `EnvironmentObserver` SPI with cache — impedance mismatch; LLM has unbounded latency, SPI assumes bounded (100ms timeout)
- Modify engine SPI to support async — unnecessary coupling; engine's synchronous model is correct for deterministic observers
**Rationale:** LLM calls have unbounded latency (30s to 60min). The engine's tick loop is designed for bounded-latency deterministic observers. Forcing an unbounded operation into a bounded interface creates a category error. The event-driven model lets the LLM take as long as it needs, writing results back into the environment to trigger subsequent ticks.
**Trade-offs:** Latency between environment change and LLM response — events must accumulate before being processed. Deterministic observers react instantly; LLM interpretation lags by design.
**Sources:** engine `CaseContextChangedEventHandler.observations()` (100ms hardcoded timeout), engine `EnvironmentObserver` SPI
**Exploration:** deep-analysis
**Status:** captured

## D2: Composition from summarisation primitives via builder templates

**Choice:** The LLM interpretation layer is composed from existing summarisation primitives (`EventAccumulator`, `WindowPolicy`, `Compactor`, `ContentSummariser`, `SummarisationRunner`, `EventStreamBus`) via named template builders. No new runner type.
**Alternatives:**
- New `LlmEventDigest` type — duplicates accumulation/windowing/ticking infrastructure; creates parallel concept alongside summarisation
- Custom infrastructure — ignores existing well-tested primitives
**Rationale:** The summarisation framework's primitives (accumulation, windowing, compaction, tick-driven processing) are exactly what LLM interpretation needs. `ContentSummariser<T, R>` has the right shape: batch + previous result → single output with continuity. `asSummariser()` bridges into `StatefulSummariser` for the runner. Named templates (e.g., `LlmSummarisers.interpretation(...)`) make specific compositions discoverable while keeping the primitives open for novel use cases.
**Trade-offs:** Depends on the summarisation-api module. But this is a natural dependency — the pattern IS event batching + processing.
**Sources:** `ContentSummariser<T,R>`, `SummarisationRunner`, `EventStreamBus`, wacky-manor `NarratorAgent`
**Exploration:** deep-analysis
**Status:** captured

## D3: Named templates as pre-configured ContentSummariser compositions

**Choice:** `LlmSummarisers` factory class provides named templates that produce ready-to-use `ContentSummariser` implementations. Each template handles prompt assembly, LLM invocation (via `StructuredAgentInvoker`), response parsing, and invocation policy monitoring internally.
**Alternatives:**
- Single generic class with config flags — less discoverable, harder to find the right configuration
- New SPI hierarchy (`Interpreter<E,R>`) — creates semantic tension with existing `ContentSummariser` which has the same shape
**Rationale:** The `ContentSummariser<T, R>` contract (`summarise(List<T> items, @Nullable R previous) → CompletionStage<R>`) is identical to what LLM interpretation needs. Named templates (`interpretation`, `narration`, `classification`) give concrete entry points for common use cases while the builder allows custom compositions for use cases we haven't anticipated.
**Trade-offs:** Templates become a catalog to maintain. But each template is thin — it configures prompt assembly and response parsing, delegating the heavy lifting to `StructuredAgentInvoker`.
**Sources:** `ContentSummariser<T,R>.asSummariser()`, `StructuredAgentInvoker`, wacky-manor `NarratorSummariser`
**Exploration:** deep-analysis
**Depends on:** D2 (composition from summarisation primitives)
**Status:** captured

## D4: Caller-supplied latency policy

**Choice:** The caller who creates the prompt provides expected duration, warning threshold, and error threshold via `LlmInvocationPolicy`. The framework does not guess or hardcode timeouts.
**Alternatives:**
- Framework-hardcoded timeout — wrong for every use case; LLM latency depends on model, prompt complexity, provider load
- No timeout at all — no way to detect stuck calls
**Rationale:** Only the caller knows the expected complexity. A simple classification prompt takes ~30s; a deep multi-key analysis might take 5 minutes. The policy is embedded in the summariser template — it monitors actual invocation duration, logs warnings, and cancels/errors at caller-specified thresholds.
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

## D6: Output routing via EventStreamBus subscriber — caller chooses destination

**Choice:** LLM interpretation results publish to `EventStreamBus` like any summarisation output. A subscriber handles environment-specific write-back (signal deposits, context writes, observation storage). The caller chooses the subscriber.
**Alternatives:**
- Custom output mechanism — would require new runner type or output abstraction
- Hardcoded write-back — not composable; different consumers need different routing
**Rationale:** `EventStreamBus` is the standard output mechanism for all summarisation runners. Using it for interpretation means no runner changes. The subscriber pattern lets different consumers route output differently: pipeline flow for summarisation, environment write-back for interpretation, custom callback for anything else. Pre-built `EnvironmentWriteBack` subscriber helper handles the common case.
**Trade-offs:** One level of indirection (bus → subscriber → environment). But the bus is lightweight (synchronous dispatch, CopyOnWriteArrayList) and enables adding audit/metrics subscribers later.
**Sources:** `EventStreamBus`, wacky-manor `NarratorAgent` output wiring
**Exploration:** deep-analysis
**Depends on:** D2 (composition from summarisation primitives)
**Status:** captured

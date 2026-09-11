# Decisions — issue-241-decision-narratives

## D1: Persistent state for SummarisationRunner

**Choice:** `StateStore<S>` SPI injected into `SummarisationRunner` as optional constructor parameter
**Alternatives:**
- Externalize state entirely (no cache) — adds latency on hot path for consumers who don't need persistence
- Post-tick callback — doesn't help with load-on-startup, state lost on restart
**Rationale:** Opt-in via constructor. When absent, falls back to existing in-memory `ConcurrentHashMap` — zero cost for current consumers. When present, in-memory map becomes a write-through cache (read from cache first, fall back to store on miss, write to both on update). Consumers provide `CbrStateStore`, `JpaStateStore`, or whatever fits their persistence layer.
**Trade-offs:** Adds a new SPI to `summarisation-api`. Consumers who need persistence must implement `StateStore` — but this is the work they'd do anyway.
**Sources:** `SummarisationRunner.java:20` (partitionState ConcurrentHashMap), `CbrNarrativeStore.java` (existing CBR persistence pattern), GE-20260825-ba18b3 (sealed hierarchy CBR serialization)
**Exploration:** quick
**Status:** captured

## D2: Richer gating via EmissionPolicy

**Choice:** `EmissionPolicy<IN, S>` SPI replacing `WindowPolicy` as the gating mechanism in `SummarisationRunner`
**Alternatives:**
- Layered policies (WindowPolicy base + GatePolicy overlay) — two objects to configure, can't express quiet-period bypass
- Extend WindowPolicy with optional predicates — outgrows its name, increasing complexity
**Rationale:** Single `@FunctionalInterface`: `boolean shouldEmit(List<LevelEvent<IN>> buffered, @Nullable S currentState, long now)`. Has access to both buffered events AND current state — enabling novelty scoring, state-aware gating, and composite triggers. `WindowPolicy` becomes a concrete `EmissionPolicy` implementation (backward compatible). Composite factory: `EmissionPolicy.anyOf(...)` for OR composition.
**Trade-offs:** `EventAccumulator` becomes a pure buffer (loses its policy role). Existing code using `WindowPolicy` needs no change — it implements the new interface.
**Sources:** `WindowPolicy.java`, `EventAccumulator.java`, `NarrativeSynthesisGate.java` (count + novelty + quiet period — the use case that motivates richer gating)
**Exploration:** quick
**Status:** captured

## D3: Post-summarisation output processing

**Choice:** `OutputProcessor<OUT, S>` SPI on `SummarisationRunner` for post-summarisation output processing
**Alternatives:**
- Let Summariser implementations handle pruning internally — simpler but tangles pruning with synthesis logic, not reusable
**Rationale:** `@FunctionalInterface`: `List<OUT> process(List<OUT> outputs, @Nullable S currentState)`. Called after `summarise()` returns, before publishing to output bus and state update. Has access to current state for capacity enforcement against accumulated total. Optional constructor parameter — when absent, outputs pass through. Symmetric with `Compactor<IN>` (input-side).
**Trade-offs:** Adds another optional SPI to SummarisationRunner constructor. For simple cases where the summariser naturally produces bounded output, this is unnecessary — but it keeps the pipeline composable.
**Sources:** `NarrativeSynthesiser.java:191-192` (pruneEpisodes, pruneThemes — the use case), `Compactor.java` (input-side analog)
**Exploration:** quick
**Status:** captured

## D4: Defer identity narrative retrofit

**Choice:** Build decision narratives on the enhanced pipeline now. Retrofit identity narratives (`NarrativeSynthesiser`) in a separate issue (#259), queued after #241 in the .plan.
**Alternatives:**
- Retrofit now — proves both use cases in one shot but doubles scope, makes debugging harder
**Rationale:** Decision narratives prove the pipeline enhancements on greenfield code. No risk of breaking the existing social cognition stack. Identity narrative retrofit (#259) references this work as proof the pattern works.
**Trade-offs:** Two narrative systems coexist temporarily — `NarrativeSynthesiser` (identity) and the summarisation pipeline (decision). Unified after #259.
**Sources:** `NarrativeSynthesiser.java` (working, tested, consumed by SocialAvatarCognition), blocks#259
**Exploration:** quick
**Status:** captured

## D5: Decision signal type design

**Choice:** Single sealed `DecisionSignal` hierarchy as pipeline input
**Alternatives:**
- Heterogeneous input with multiple `EventStreamBus` sources merged — loses type safety, summariser receives untyped payloads
**Rationale:** `sealed interface DecisionSignal` with variants: `RoutingDecision`, `CbrRetrieval`, `TrustAssessment`, `DeliberationOutcome`, `StepOutcome`. Each is a record carrying relevant data from its platform source. Flows as `LevelEvent<DecisionSignal>` into the pipeline. `NarrativeSignalStrategy` SPI produces these events from platform sources — domain repos (fsitrading) implement it. Exhaustive `switch` matching in the summariser.
**Trade-offs:** All signal types must implement `DecisionSignal` — new signal sources require adding a permit to the sealed interface. But sealed hierarchies are the project's standard pattern for this.
**Sources:** casehubio/blocks#241 (issue body — listed 5 fragment types, reinterpreted as signal types), `NarrativeFragment.java` (existing sealed pattern)
**Exploration:** quick
**Status:** captured

## D6: Pipeline output type

**Choice:** Structured `DecisionNarrative` record — separate from `NarrativeFragment`
**Alternatives:**
- Prose String output — simplest but loses structure for timeline/UI consumption
- Extend NarrativeFragment with DecisionEpisode variant — conflates identity and operational concerns, every NarrativeFragment consumer gains an unwanted switch branch
**Rationale:** New record: `DecisionNarrative(caseId, stepName, explanation, evidenceSources, confidence, producedAt)`. Lives in its own package — NOT in social cognition `narrative` package. Pipeline is `SummarisationRunner<DecisionSignal, DecisionNarrative>`. Timeline component and Ops Centre consume `DecisionNarrative` directly. Clean separation: `NarrativeFragment` = identity narrative, `DecisionNarrative` = decision explanation.
**Trade-offs:** Two separate narrative type systems. But they serve different purposes (reflective identity vs. operational explanation) and have different consumers.
**Sources:** `NarrativeFragment.java` (sealed hierarchy to NOT extend), `NarrativePromptSection.java` (existing consumer undisturbed)
**Exploration:** quick
**Status:** captured

## D7: Pipeline topology

**Choice:** Two-level pipeline — L1 per-step heuristic accumulation, L2 per-case LLM synthesis
**Alternatives:**
- Three levels (add L3 per-case arc summary) — premature, can be added later if needed
- Single level (flat LLM) — sends every raw signal to LLM, high cost, noisy input
**Rationale:** L1: raw `DecisionSignal` events grouped by `caseId + stepName` via `KeyedSummarisationRunner`. Heuristic summariser — no LLM, microsecond latency. Produces `StepDecisionSummary` (structured record). L2: `StepDecisionSummary` accumulated per case, `ContentSummariser<StepDecisionSummary, DecisionNarrative>` — LLM-backed with `@Nullable` previous state. L1→L2 split keeps LLM calls batched and infrequent. Mirrors clinical example pattern.
**Trade-offs:** L1 heuristic summariser must be written per domain (or generically). Two-level adds wiring complexity vs. single level. But cost savings from not sending raw signals to LLM outweigh this.
**Sources:** `ClinicalPipelineTest.java` (L1→L2→L3→L4 precedent), `KeyedSummarisationRunner.java` (grouping by key)
**Exploration:** quick
**Status:** captured

## D8: YAML example scope

**Choice:** Self-contained test example in blocks test sources, fsitrading-inspired domain signals
**Alternatives:**
- Separate examples/ module with runnable mini-app — heavier, breaks existing convention of test-class examples
**Rationale:** Like existing `ClinicalPipelineTest` and `LogisticsPipelineTest` in `src/test/java/.../summarisation/examples/`. Uses fsitrading-inspired signal types (trading decisions, analyst routing, trade outcomes) defined locally in the test — no dependency on fsitrading. YAML pipeline definition lives alongside the test. Proves pipeline end-to-end. When fsitrading wires up for real (Batch 4), it follows this pattern with actual types.
**Trade-offs:** Example is a test, not a standalone app — less visible to newcomers. But matches existing convention.
**Sources:** `ClinicalPipelineTest.java`, `LogisticsPipelineTest.java` (existing example convention)
**Exploration:** quick
**Status:** captured

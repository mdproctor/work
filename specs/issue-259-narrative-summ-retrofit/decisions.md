# Decisions — #259 Narrative Summarisation Retrofit

## D1: Pull-to-push adapter location

**Choice:** Self-contained adapter in the narrative package
**Alternatives:**
- Modify reflection producer (MemoryHygieneOrchestrator) to publish directly — couples memory hygiene to summarisation pipeline, leaks EventStreamBus into unrelated package
**Rationale:** Keeps #259 self-contained. Matches established ChannelEventAdapter pattern. The adapter is tick-driven (called from orchestrator cycle), queries ReflectionQueryStore.findSince() with a watermark, publishes each as LevelEvent<ReflectionEntry>.
**Trade-offs:** Still fundamentally poll-based under the hood, but the orchestrator tick cycle already drives the cadence and the adapter is trivial.
**Sources:** ChannelEventAdapter.java, NarrativeSynthesiser.java (existing pull logic), issue #259 ("pull-to-push adapter")
**Exploration:** quick
**Status:** captured

## D2: EmissionPolicy integration with SummarisationRunner

**Choice:** Runner-level evaluation — SummarisationRunner evaluates EmissionPolicy in tick(), EventAccumulator gets peekBuffer(), stays a dumb buffer
**Alternatives:**
- Accumulator generification (EventAccumulator<E, S>) — pollutes accumulator's type signature with state it doesn't own
- New PolicyAccumulator type — adds composition glue type without clear value
**Rationale:** Puts policy evaluation where the state lives (the runner). EventAccumulator remains simple. WindowPolicy backward compat via adapter that ignores state. Atomicity relaxation between peek and drain is harmless — tick() is synchronized, only collect() can interleave (drains one extra event at most).
**Trade-offs:** peekBuffer() exposes internal buffer as read-only snapshot — minor API surface addition to EventAccumulator.
**Sources:** SummarisationRunner.java (tick/invokeSummariser), EventAccumulator.java (shouldEmit/drainIfReady)
**Exploration:** quick
**Status:** captured

## D3: SummarisationRunner API shape for new SPIs

**Choice:** Builder pattern — SummarisationRunner.builder(summariser, outputBus, outputLevel) with fluent .emissionPolicy(), .stateStore(), .outputProcessor(), .compactor(), .onFailure(). Existing WindowPolicy constructors unchanged. Apply same pattern to KeyedSummarisationRunner.
**Alternatives:**
- Overloaded constructors — combinatorial explosion with 3 optional params × with/without compactor × with/without onFailure
- Config record — pushes complexity into a separate SummarisationRunnerConfig type without adding clarity
**Rationale:** Builders handle optional parameters cleanly, existing code is untouched, extensible for future SPIs. Idiomatic Java.
**Trade-offs:** Builder adds inner class boilerplate. Existing constructors coexist (minor API surface duplication during migration).
**Depends on:** D2 (EmissionPolicy integration at runner level)
**Sources:** SummarisationRunner.java (4 existing constructors), KeyedSummarisationRunner.java (4 existing constructors)
**Exploration:** quick
**Status:** captured

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

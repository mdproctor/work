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

# Handoff — casehub-blocks

**Branch:** main (clean)
**Last closed:** `issue-19-choreographed-event-bus` — landed as 0aac8f33 on main
**Issue:** casehubio/blocks#19
**Date:** 2026-08-12

## Recent Cross-Repo Changes

### 2026-08-20 — feat/per-event-level-resolver (from examples session)

- **Branch:** `feat/per-event-level-resolver` (uncommitted to main)
- **Commit:** 8677e2a — adds `Function<E, EventLevel>` level-resolver constructor overload to `PartitionedObservationService`. Existing single-level constructor delegates with constant function. Backward-compatible.
- **Also fixes:** pre-existing `ChannelObserver` compile error (missing `payload` param in `MessageView` constructor)
- **Refs:** casehubio/examples#7
- **Status:** needs review, test suite has pre-existing compile errors in channel/negotiation tests (unrelated — `MessageReceivedEvent` constructor changed in qhorus-api)

## Last Session

Two changes landed on main:

1. **Uni audit** (refactor) — removed Uni wrapping from all agentic SPIs (RoutingStrategy, ActivationRule, AggregationStrategy, TerminationCondition, DecompositionHeuristic, ExecutionDriver.cancel(), AbstractExecutionDriver.runLoop()). 82 files, -143 lines. Uni kept only at API boundaries (execute, invoke, converse) and for concurrent composition (Uni.join.all). Aligned with platform's move to virtual threads.

2. **ChoreographedDriver event-bus integration** (feat) — added event-driven mode with BlockingQueue + EventSource SPI + composable EventConcurrencyPolicy. Two modes: legacy (no EventSource, continuous loop) and event-driven (dormant between iterations, woken by events). New types: DriverEvent, EventSource, EventConcurrencyPolicy. ExecutionBackend.choreographed() factories.

## Key Design Decisions

- Virtual threads make Uni unnecessary for synchronous SPIs — blocking is free
- EventSource (lightweight SPI) chosen over Multi (overkill for a blocking queue consumer)
- Serialize (actor-mailbox) as default policy, composable via .then()
- Cancellation via queue poison pill, not thread interruption

## What's Next

| Item | Scale | Complexity |
|------|-------|------------|
| Update consumer/contributor guides (docs/guides/) with Uni removal and new types | S | Low |
| Engine-adapter: wire EventSource to CorrelationObserver/WorkItemObserver for ChannelAgent/HumanAgent | M | Med |
| Consumer repos: verify no direct SPI calls broken by Uni removal (all consumers use pattern builders, so likely clean) | S | Low |

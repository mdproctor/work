# Handover — 2026-07-14

**Branch:** `main` (clean)

## Last Session

Completed qhorus channel summarisation (#40). Four new types: `KeyedAccumulator<K,E>` and `KeyedSummarisationRunner<K,IN,OUT>` extend the summarisation framework with grouped windowing (completion predicate + stale timeout). `ChannelEventAdapter<E>` and `ChannelEventPublisher<E>` bridge qhorus MessageObserver/MessageDispatcher to EventStreamBus. Design review (3 rounds, 14 issues, $12.54) hardened staleness semantics, null-key contract, and caught the same-channel feedback loop risk.

## Immediate Next Step

Pick from What's Next. Run `/work` to start.

## What's Left

- **engine#101 revision** — Update issue to reflect Gaps 2+3 done, close or narrow to remaining Gap 1. · XS · Low

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| engine#695 | DAG-aware parallel execution driver | L | High | P1 — depends on engine#694 (done) |
| #46 | Output contracts on TaskNode | M | Med | Depends on engine#694 (done) |
| #54 | Recursive LLM decomposition | M | Med | LLM returns CompoundTasks |
| #56 | SHOP-style forward reasoning with effects | S | Med | Apply PrimitiveTask effects during planning |
| #57 | Task tree construction DSL | S | Low | compound/primitive factory methods |
| #53 | Predecessor-specific plan composition analysis | S | Med | Blocked by engine#718 |

## Cross-Module

**Engine issues from blocks sessions:**
- engine#695–698 — parallel driver, recovery, versioning, context isolation (all open)
- engine#101 — LLM supervisor mode: Gap 1 reframed by engine#700
- engine#717 — ExperiencePlanStep.stepOutcome() should return RoutingOutcome
- engine#718 — CbrCaseRetainObserver should set meaningful plan trace priorities

## References

- Spec: `docs/specs/2026-07-14-qhorus-channel-summarisation-design.md`
- Design review: `~/adr/casehub-blocks/qhorus-channel-summarisation-20260714-041452/`
- Blog: `blog/2026-07-14-mdp01-the-grouped-window-nobody-had.md`

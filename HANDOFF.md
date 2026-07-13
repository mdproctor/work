# Handover — 2026-07-13

**Branch:** `main` (clean)

## Last Session

Completed routing CBR enhancements — three issues on `issue-37-routing-cbr-enhancements`: configurable outcome weights + similarity weighting (#37), RoutingSignalProvider SPI (#34), PlanCompositionAnalyser (#32). Design spec passed 4-round adversarial review (19 issues, all resolved). New SPI types added to engine-api (`RoutingSignal`, `RoutingSignalProvider`, `RoutingSignalAssembler`). Branch squashed to 3 commits, pushed to upstream, closed.

## Immediate Next Step

Pick from What's Next. Run `/work` to start.

## What's Left

- **engine#101 revision** — Update issue to reflect Gaps 2+3 done, close or narrow to remaining Gap 1 (LlmPlanningStrategy as composition, not greenfield). · XS · Low

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| engine#695 | DAG-aware parallel execution driver | L | High | P1 — depends on engine#694 (done) |
| #45 | Hybrid decomposition (ChatHTN-style) | M | Med | Depends on #13 (done) |
| #46 | Output contracts on TaskNode | M | Med | Depends on engine#694 (done) |
| #40 | Qhorus channel integration for summarisation | L | High | Alongside qhorus#328 |
| #53 | Predecessor-specific plan composition analysis | S | Med | Blocked by engine#718 |

## Cross-Module

**Engine issues from blocks sessions:**
- engine#695–698 — parallel driver, recovery, versioning, context isolation (all open)
- engine#101 — LLM supervisor mode: Gap 1 reframed by engine#700
- engine#717 — ExperiencePlanStep.stepOutcome() should return RoutingOutcome (filed this session)
- engine#718 — CbrCaseRetainObserver should set meaningful plan trace priorities (filed this session)

**Engine-api commit this session:**
- `4c53ed55` — RoutingSignal + RoutingSignalProvider + RoutingSignalAssembler (on engine branch `issue-714-xs-s-cleanup-batch`)

## References

- Spec: `docs/specs/2026-07-13-routing-cbr-enhancements-design.md`
- Design review: `~/adr/casehub-blocks/routing-cbr-enhancements-20260713-024343/`

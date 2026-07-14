# Handover — 2026-07-14

**Branch:** `main` (clean)

## Last Session

Completed hybrid decomposition (#45). `HybridDecomposition<T>` composes static + LLM strategies — try static first, fall back to LLM on `NoMethodMatchedException`. `HtnBuilder.flatten()` refactored to delegate to configured strategy (was dead code). Design review (4 rounds, 12 issues) caught the HtnBuilder dead code path and pushed constructor types from concrete to interface. Compared CaseHub's decomposition architecture against langchain4j's HTN PR (#5584, Mario Fusco) — CaseHub's plan-as-data DAG model is architecturally superior; SHOP-style forward reasoning is unnecessary with an LLM in the loop.

## Immediate Next Step

Pick from What's Next. Run `/work` to start.

## What's Left

- **engine#101 revision** — Update issue to reflect Gaps 2+3 done, close or narrow to remaining Gap 1. · XS · Low

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| engine#695 | DAG-aware parallel execution driver | L | High | P1 — depends on engine#694 (done) |
| #46 | Output contracts on TaskNode | M | Med | Depends on engine#694 (done) |
| #54 | Recursive LLM decomposition | M | Med | Filed this session — LLM returns CompoundTasks |
| #56 | SHOP-style forward reasoning with effects | S | Med | Filed this session — apply PrimitiveTask effects during planning |
| #57 | Task tree construction DSL | S | Low | Filed this session — compound/primitive factory methods |
| #40 | Qhorus channel integration for summarisation | L | High | Alongside qhorus#328 |
| #53 | Predecessor-specific plan composition analysis | S | Med | Blocked by engine#718 |

## Cross-Module

**Engine issues from blocks sessions:**
- engine#695–698 — parallel driver, recovery, versioning, context isolation (all open)
- engine#101 — LLM supervisor mode: Gap 1 reframed by engine#700
- engine#717 — ExperiencePlanStep.stepOutcome() should return RoutingOutcome
- engine#718 — CbrCaseRetainObserver should set meaningful plan trace priorities

## References

- Spec: `docs/specs/2026-07-13-hybrid-decomposition-design.md`
- Design review: `~/adr/casehub-blocks/hybrid-decomposition-20260713-223739/`
- Research: `docs/research/2026-07-09-task-decomposition-and-agent-planning-landscape.md`
- langchain4j HTN PR: langchain4j/langchain4j#5584

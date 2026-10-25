# HANDOFF — casehub-agency

## Last Session

Implemented #284 (LLM-driven observation & local rules) across all 4 batches (7 tasks), then completed 2 deferred items (#358). Full pipeline landed on main: foundation records/SPIs, EmissionPolicy.window() factory, ChangeGatedEmissionPolicy, InFlightGate, ObservationDispatcher with topological sort, LlmResponseConverter, LlmSummarisers templates, InterpretationBuilder (spec-aligned), InvocationPolicyMonitor. 69 tests total. Both issues closed.

## Immediate Next Step

No outstanding work on this repo. Check `work find` or the issue tracker for what's next.

## References

- Spec: `specs/issue-284-llm-observation-local-rules/2026-10-25-llm-observation-local-rules-design.md`
- Decisions: `specs/issue-284-llm-observation-local-rules/decisions.md` (D1-D10)
- Plan: `plans/2026-10-25-llm-observation-local-rules.md` (4 batches, 7 tasks — all complete)
- New package: `agency-core/src/main/java/io/casehub/agency/stigmergy/llm/` (19 production files)
- Consumer guide updated: `docs/guides/consumer-guide.md` (stigmergy.llm section added)

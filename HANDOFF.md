# HANDOFF — casehub-agency

## Last Session

Design session for #284 (LLM-driven observation & local rules). Produced 10 architectural decisions, a reviewed design spec, and a 7-task implementation plan across 4 batches. Key insight: LLM interpretation sits alongside the engine's tick loop (event-driven, not synchronous SPI) and composes existing summarisation primitives via builder templates — no new runner type.

## Immediate Next Step

Implement Batch 1 — foundation records, SPIs, and `EmissionPolicy.window()` factory. Plan at `plans/2026-10-25-llm-observation-local-rules.md`.

## References

- Spec: `specs/issue-284-llm-observation-local-rules/2026-10-25-llm-observation-local-rules-design.md`
- Decisions: `specs/issue-284-llm-observation-local-rules/decisions.md` (D1-D10)
- Plan: `plans/2026-10-25-llm-observation-local-rules.md` (4 batches, 7 tasks)
- Blog: `blog/2026-10-25-mdp01-the-llm-doesnt-belong-in-the-tick-loop.md`
- Journal: `JOURNAL.md`

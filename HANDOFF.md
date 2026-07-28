# Handover — 2026-07-28

**Branch:** `main` (clean — `issue-72-cdi-bean-leak-fix` closed)

## Last Session

Fixed #72 — removed the Jandex maven plugin from blocks. The Jandex index was unconditionally exposing blocks' CDI beans (routing strategies, channel summarisers) to all Quarkus consumers, causing `UnsatisfiedResolutionException` for EntityManager in consumers like wacky-manor that only needed pure types.

## Immediate Next Step

**Build breakage on main.** After rebasing onto upstream, blocks fails to compile — engine-api restructured `io.casehub.engine.plan.*` types (DecompositionStrategy, TaskNode, etc.) as part of the #60 unified execution model work. Fix the imports/references in blocks' agentic decomposition package before any other work.

## What's Left

- **Engine-api API breakage** — blocks references `io.casehub.engine.plan.DecompositionStrategy`, `TaskNode`, `DecompositionContext`, `DecompositionMethod` which no longer exist at those locations. Align blocks' decomposition package with the new engine-api structure. · M · Med
- **Unified execution model doc cleanup** — verify pointer from blocks spec copy to engine canonical is correct · XS · Low

## What's Next

*Unchanged — `git show HEAD~1:HANDOFF.md`*

## Cross-Module

**Engine issues from blocks sessions:**
- engine#696, #697 — recovery, versioning (both open)
- engine#101 — LLM supervisor mode: Gaps 2+3 done, Gap 1 subsumed by #60
- engine#717 — `ExperiencePlanStep.stepOutcome()` should return RoutingOutcome

## References

- Slot 38: `worktrees/38/` (engine + blocks, branch `issue-60-unified-execution-model`)

# Handoff — casehub-blocks

**Branch:** issue-142-narrative-identity
**Issue:** casehubio/blocks#142
**Date:** 2026-08-25

## Status

#143 (NarrativeOrchestrator + NarrativeSynthesiser) complete — 4 new files, 31 new tests, 1845 total passing. Queue advanced to #144 (CbrNarrativeStore). Ready for brainstorming + implementation.

## What Was Built

Two-component narrative synthesis architecture (D3 split):

- **NarrativeOrchestrator** — compositor. Reads NarrativeStore, caches NarrativeState per agent, detects changes via `synthesisedAt` timestamp. `@Nullable previous` on first load (R1-04). Per-agent ReentrantLock.

- **NarrativeSynthesiser** — `@ApplicationScoped` effectful. Composite synthesis gate (count + novelty + quiet period bypass). LLM synthesis via AgentProvider producing flat JSON (episodes + themes). Incremental episode merge, full theme re-derivation (D14). Capacity pruning (oldest episodes, lowest-salience themes, salience floor). Watermark preservation on capped reflections (R1-02). Degenerate response guard (R1-03). Case-insensitive thematic tag matching including GroupEpisodes (R1-05/R1-06).

**Foundation changes:** TokenJaccardDistance made public (D16). NarrativeModulation per-axis clamping to [-1,1] (D19). NarrativeTick.Updated.previous made @Nullable (R1-04).

## Key Design Decisions

- **D13/D14 (hybrid incremental):** Episodes additive, themes fully re-derived each synthesis — themes need the full episode arc for coherent salience scores
- **D15 (label anchoring):** Existing theme labels included in prompt so LLM produces stable labels across synthesis cycles
- **D19 (clamping):** Surfaced by adversarial review — unbounded sum could reach 10x with maxThemes=10
- **D21 (graceful degradation):** Compositor sees stale state on synthesis failure — consistent with ADR-0001

## What's Next

| Item | Scale | Complexity |
|------|-------|------------|
| #144 CbrNarrativeStore — CBR-backed persistence | S | Low |
| #145 NarrativeFeedback — wire modulation + GoalPriorityEscalationPolicy | M | High |
| #146 SocialNormDetector — norm detection from CBR | L | High |
| #147 CollectiveGoalFormation — drive alignment to JointIntention | L | High |
| #148 GroupNarrativeOrchestrator — collective self-story | L | High |

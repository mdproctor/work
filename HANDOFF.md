# Handover — 2026-07-02

**Branch:** `main` (cross-repo consolidation session, no branch)

## Last Session

Cross-repo scan of all 26 CaseHub repos for shared patterns. Created consolidation epic #28 with 6 children. Transferred 5 issues from drafthouse/openclaw/engine, closed 4 duplicates, merged claudony#141 into blocks#23. Implemented #17 (trust routing YAML) across blocks/aml/devtown/life. Fixed incomplete neocortex memory rename (#57) across aml/devtown/clinical/soc — 46 files. Updated CLAUDE.md with blocks scope criteria, trust routing architecture, consolidation tracking.

## Immediate Next Step

Pick the next consolidation item from epic #28. #22 or #23 are both ready but L/High. Run `/work` to start a branch.

## Cross-Module

**We're blocking:**
- fsitrading — first consumer of shared `TrustRoutingPolicyKeys` (#17 landed)

## What's Left

- Compliance record consolidation (`RoutingDecisionRecord`, `TrustRoutingRequirement`, `RequirementStatus`) deferred from #17 — shared types exist in blocks but aml/devtown haven't migrated. Requires adding blocks dep to aml-api module. · S · Med
- Parent BOM missing `casehub-neocortex-memory-*` artifacts — consumer POMs use explicit `${casehub.version}`. File on parent. · XS · Low

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #22 | Debate channel infrastructure from drafthouse | L | High | EntryType redesign needed |
| #23 | Oversight gate lifecycle + risk classification | L | High | 7+ consumer repos, GatePolicy unification |
| #24 | Universal pluggable routing strategy | L | High | Design-first — audit needed |
| #25 | Worker data coordination (DataExchange/DataChannel) | L | High | Blocked on engine#528 |
| #27 | Layered event summarisation from quarkmind | M | Med | Not yet — quarkmind still baking |
| #13 | LlmDecomposition — goal-to-plan | M | High | Builds on HTN pre-decomposition |
| #20 | Inter-agent channel setup during execution | M | Med | Deferred from #15 |

## References

- Consolidation epic: blocks#28 (scope criteria, tracking table in CLAUDE.md)
- Blog: `blog/2026-07-02-mdp01-what-belongs-in-a-shared-library.md`

*Updated: #23 closed — removed from backlog.*

# Handover — 2026-07-02

**Branch:** `main` (consolidation session, no branch)

## Last Session

Closed #22 (debate channel infrastructure) and #23 (oversight gate lifecycle + risk classification). #23 moved oversight + routing SPIs to engine-api, leaving blocks as AI-only. Epic #28 is now 3/6 done.

## Immediate Next Step

Pick next consolidation item from epic #28. #30 (AI routing strategy impls) is ready but blocked on engine#634. #13 or #20 are unblocked alternatives. Run `/work` to start a branch.

## Cross-Module

**We're blocking:**
- fsitrading — first consumer of shared `TrustRoutingPolicyKeys` (#17 landed)

## What's Left

- Compliance record consolidation (`RoutingDecisionRecord`, `TrustRoutingRequirement`, `RequirementStatus`) deferred from #17 — shared types exist in blocks but aml/devtown haven't migrated. Requires adding blocks dep to aml-api module. · S · Med
- Parent BOM missing `casehub-neocortex-memory-*` artifacts — consumer POMs use explicit `${casehub.version}`. File on parent. · XS · Low

## Consolidation Epic #28 — Full Status

| # | Title | Scale | Complexity | Ready? |
|---|-------|-------|------------|--------|
| #17 | Trust routing YAML | M | Med | **Done** |
| #22 | Debate channel infrastructure | L | High | **Done** |
| #23 | Oversight gate lifecycle + risk classification | L | High | **Done** |
| #24 | Universal pluggable routing strategy | L | High | Design-first |
| #25 | Worker data coordination (DataExchange/DataChannel) | L | High | Blocked on engine#528 |
| #27 | Layered event summarisation | M | Med | Not yet — quarkmind still baking |

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #13 | LlmDecomposition — goal-to-plan | M | High | Builds on HTN pre-decomposition |
| #20 | Inter-agent channel setup during execution | M | Med | Deferred from #15 |
| #16 | RAG-enriched routing — CbrCaseMemoryStore | M | High | Depends on neural-text#20 |

## References

- Consolidation epic: blocks#28 (scope criteria + tracking table in CLAUDE.md)
- #22 close comment: full traceability to drafthouse#79, #80, #81, #82, #83
- Blog: `blog/2026-07-02-mdp01-what-belongs-in-a-shared-library.md`

# Handover — 2026-07-02

**Branch:** `main` (cross-repo consolidation session, no branch)

## Last Session

Cross-repo scan of all 26 CaseHub repos for shared patterns. Created consolidation epic #28 with 6 children. Transferred 5 issues from drafthouse/openclaw/engine, closed 4 duplicates, merged claudony#141 into blocks#23. Implemented #17 (trust routing YAML) across blocks/aml/devtown/life. Fixed incomplete neocortex memory rename (#57) across aml/devtown/clinical/soc — 46 files. Updated CLAUDE.md with blocks scope criteria, trust routing architecture, consolidation tracking. Second scan for CBR/LLM patterns confirmed existing issues cover the space — filed platform#134 (cosine similarity) and neural-text#64 (event-to-memory bridge) for non-blocks patterns. Cross-linked all 15 consumer issues across 7 repos back to their blocks dependencies.

## Immediate Next Step

Pick the next consolidation item from epic #28. #22 or #23 are both ready but L/High. Run `/work` to start a branch.

## Cross-Module

**We're blocking:**
- fsitrading — first consumer of shared `TrustRoutingPolicyKeys` (#17 landed)

## What's Left

- Compliance record consolidation (`RoutingDecisionRecord`, `TrustRoutingRequirement`, `RequirementStatus`) deferred from #17 — shared types exist in blocks but aml/devtown haven't migrated. Requires adding blocks dep to aml-api module. · S · Med
- Parent BOM missing `casehub-neocortex-memory-*` artifacts — consumer POMs use explicit `${casehub.version}`. File on parent. · XS · Low

## Consolidation Epic #28 — Full Status

| # | Title | Scale | Complexity | Ready? | Destination | Migrates from | Downstream consumers |
|---|-------|-------|------------|--------|-------------|---------------|---------------------|
| #17 | Trust routing YAML | M | Med | **Done** | blocks | aml, devtown, clinical, life, ops, soc | aml, devtown, clinical, life, ops, soc, fsitrading |
| #22 | Debate channel infrastructure | L | High | **Done** | blocks | drafthouse | drafthouse, devtown, clinical, aml, claudony |
| #23 | Oversight gate lifecycle + risk classification | L | High | Yes but large | blocks | openclaw, engine-api | openclaw, aml, soc, life, devtown, clinical, iot, claudony |
| #24 | Universal pluggable routing strategy | L | High | Design-first | blocks | engine, work | engine, work, qhorus, eidos |
| #25 | Worker data coordination (DataExchange/DataChannel) | L | High | Blocked on engine#528 | blocks | engine | engine, workers, desiredstate |
| #27 | Layered event summarisation | M | Med | Not yet — quarkmind still baking | blocks | quarkmind | quarkmind, iot, aml, clinical |

## Cross-Linked Consumer Issues (by blocks issue)

| Blocks # | Consumer issues (linked both ways) |
|----------|-----------------------------------|
| #10 | engine#101, aml#8, clinical#9, quarkmind#180 |
| #13 | engine#110 |
| #16 | aml#61, clinical#78, devtown#75, quarkmind#192 |
| #22 | claudony#158, drafthouse#71, drafthouse#72 |
| #24 | engine#505, engine#501, platform#134 |
| #27 | quarkmind#180, quarkmind#181 |

## Non-Blocks Issues Filed This Session

- platform#134 — shared cosine similarity utility (engine + work duplication)
- neural-text#64 — event-to-memory bridge (engine + devtown CaseMemoryStore wiring pattern)

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #22 | Debate channel infrastructure from drafthouse | L | High | EntryType redesign needed |
| #23 | Oversight gate lifecycle + risk classification | L | High | 7+ consumer repos, GatePolicy unification |
| #13 | LlmDecomposition — goal-to-plan | M | High | Builds on HTN pre-decomposition |
| #20 | Inter-agent channel setup during execution | M | Med | Deferred from #15 |
| #16 | RAG-enriched routing — CbrCaseMemoryStore | M | High | Depends on neural-text#20 |

## References

- Consolidation epic: blocks#28 (scope criteria + tracking table in CLAUDE.md)
- Blog: `blog/2026-07-02-mdp01-what-belongs-in-a-shared-library.md`
- Garden: GE-20260702-7c79fe (stale IntelliJ index gotcha), GE-20260702-74d065 (workspace scanning technique)

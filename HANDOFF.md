# Handover — 2026-07-02

**Branch:** `main` (cross-repo consolidation session, no branch)

## Last Session

Cross-repo scan of all 26 CaseHub repos to find duplicated patterns worth extracting to blocks. Created consolidation epic #28 with 6 child issues. Transferred 5 issues from drafthouse, openclaw, and engine to blocks. Closed 4 duplicates/superseded issues. Merged claudony#141 content into blocks#23. Implemented #17 (trust routing YAML consolidation) across 4 repos — blocks, aml, devtown, life.

Updated CLAUDE.md with blocks scope criteria, trust routing architecture, consolidation tracking, cross-repo conventions.

## Immediate Next Step

Pick the next consolidation item from epic #28. #22 (debate channel infrastructure from drafthouse) or #23 (oversight gate lifecycle) are both ready but large (L/High). #27 (layered event summarisation) is not ready yet — quarkmind is still evolving it. #25 is blocked on engine#528. #24 needs a design phase first. Run `/work` to start a branch for whichever is chosen.

## What's Left

Epic #28 remaining items:

| # | Title | Scale | Complexity | Ready? | Destination | Migrates from | Downstream consumers |
|---|-------|-------|------------|--------|-------------|---------------|---------------------|
| #22 | Debate channel infrastructure | L | High | Yes but large | blocks | drafthouse | drafthouse, devtown, clinical, aml |
| #23 | Oversight gate lifecycle + risk classification | L | High | Yes but large | blocks | openclaw, engine-api | openclaw, aml, soc, life, devtown, clinical, iot, claudony |
| #24 | Universal pluggable routing strategy | L | High | Design-first | blocks | engine, work | engine, work, qhorus, eidos |
| #25 | Worker data coordination (DataExchange/DataChannel) | L | High | Blocked on engine#528 | blocks | engine | engine, workers, desiredstate |
| #27 | Layered event summarisation | M | Med | Not yet | blocks | quarkmind | quarkmind, iot, aml, clinical |

- Compliance record consolidation (RoutingDecisionRecord, TrustRoutingRequirement, RequirementStatus) deferred from #17 — shared types exist in blocks but aml/devtown haven't migrated to them yet. Requires adding blocks dependency to aml-api module, which is a heavier decision. · S · Med

## Cross-Module

**We're blocking:**
- fsitrading — first consumer of the shared `TrustRoutingPolicyKeys` pattern (blocks#17 landed)

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #13 | LlmDecomposition — goal-to-plan | M | High | Builds on HTN pre-decomposition |
| #20 | Inter-agent channel setup during execution | M | Med | Deferred from #15 |
| #16 | RAG-enriched routing — CbrCaseMemoryStore | M | High | Depends on neural-text#20 |
| — | PlatformAgentInvoker | M | Med | Blocked on qhorus#315, work#285 |

## Issues Moved/Closed This Session

**Transferred to blocks:**
- drafthouse#76 → blocks#22, drafthouse#42 → closed (superseded by #22), blocks#4 → closed (superseded by #22)
- openclaw#31 → blocks#23, engine#438 → closed (superseded by #23)
- engine#442 → blocks#24
- engine#532 → blocks#25
- engine#584 → blocks#29 → closed (already implemented)
- claudony#141 → merged into blocks#23, closed
- quarkmind#182 → blocks#27 (new, tracking only — not ready to move)

## Pre-existing Compilation Issues in Peer Repos

Encountered during #17 migration — not caused by blocks changes:
- **aml:** `Worker.Builder.capabilities()` API mismatch in `AmlOversightCaseHub.java`
- **devtown:** missing version for `casehub-memory-inmem` in app/pom.xml
- **life:** `LifeChannelInitializer` — qhorus runtime store API changes

## References

- Consolidation epic: blocks#28
- CLAUDE.md: updated with scope criteria, architecture, consolidation tracking
- Design spec: `docs/superpowers/specs/2026-07-01-routing-enrichment-accountability-design.md`

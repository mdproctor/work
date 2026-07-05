# Handover — 2026-07-05

**Branch:** `main` (issue-30-ai-routing-strategy-impls closed)

## Last Session

Closed #30 (AI routing strategy implementations). Two new `AgentRoutingStrategy` impls landed in blocks: `LlmAgentRoutingStrategy` (id: "llm") and `CbrAgentRoutingStrategy` (id: "cbr"). Both use optional trust classification via `Instance<T>`. Also filed engine#650 (AgentAssignment rationale), engine#651 (AgentRoutingContext tenantId), ledger#172 (OutcomeRecord supplementary data) — all three landed during the session. Epic #28 consolidation is now 4/6 done.

Also created two new garden protocols: `casehub-dependency-tier-order` and rewrote `platform-api-scope` — establishing platform-api as the universal shared dependency. Updated PLATFORM.md to match.

## Immediate Next Step

Pick next work from #28 or backlog. Run `/work` to start a branch.

## Cross-Module

**Consumer repos need routing strategy migration (engine#644):**
- Add `id()` to ActionRiskClassifier implementations: devtown, aml, clinical, life, soc, iot
- Add `id()` to TrustRoutingPolicyProvider implementations: devtown, aml, clinical, life, quarkmind, ops
- casehub-work#287 — retrofit WorkerSelectionStrategy, InstanceAssignmentStrategy, ClaimSlaPolicy

## What's Left

- blocks#31 — AI routing strategy hardening: MP config for topK/minSimilarity, parser hardening, mixed-pool edge case tests · S · Low
- Compliance record consolidation deferred from #17 — shared types exist but aml/devtown haven't migrated · S · Med

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #13 | LlmDecomposition — goal-to-plan | M | High | Builds on HTN pre-decomposition |
| #20 | Inter-agent channel setup during execution | M | Med | Deferred from #15 |
| #27 | Layered event summarisation | M | Med | Not yet — quarkmind still baking |
| #16 | RAG-enriched routing — CbrCaseMemoryStore | M | High | Depends on neural-text#20 |

## References

- Design spec: `docs/specs/2026-07-04-ai-routing-strategies-design.md` (project)
- Dependency tier order protocol: `garden/docs/protocols/casehub/casehub-dependency-tier-order.md`
- Routing audit: `engine/docs/specs/2026-07-02-universal-routing-strategy-audit.md`

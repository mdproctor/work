# HANDOFF — casehub-neocortex

## Last Session

Implemented #1119 — memory hygiene parameter adaptation via consolidation phase. `HygieneAdaptationPhase` (@Priority(48)) reads eviction pressure from `KnowledgeGapSummary`, adjusts `retentionThreshold` via `AdaptiveRetentionConfig` holder, prevents oscillation through direction tracking. `RetentionConfigSupplier` SPI in cognition-api; `MemoryHygieneOrchestratorImpl` wired to read config per tick via `tenantGapSummary()` aggregation. Decision review caught 3 blocking issues (priority collision, missing agentId, CDI bean placement) — all resolved before implementation.

Also resolved: #1141 (DispositionSignalWriterTest eidos API break — fixed during rebase), #1139 and #1140 (closed by prior session, marked done in queue).

Filed follow-on issues: #1143 (CBR persistence for learned retention parameters), #1144 (per-tenant RetentionConfig adaptation).

## Immediate Next Step

Run `work continue` — queue has 4 open items, next in order is #1132 (implement LivedDispositionSource).

## Cross-Repo State

| Repo | Issue | Status |
|------|-------|--------|
| eidos | #245 | **Landed** — LivedDispositionSource SPI + findEffective() |
| neocortex | #1132 | **Open** — implement LivedDispositionSource (unblocked by eidos#245) |
| neocortex | #1135 | **Open** — extend bridge for AMPD facets (depends on #1132) |
| neocortex | #1143 | **Open** — CBR persistence for learned retention parameters |
| neocortex | #1144 | **Open** — per-tenant RetentionConfig adaptation |

## References

- Design spec: `docs/specs/issue-1119-hygiene-adaptation-phase/2026-11-18-hygiene-adaptation-phase-design.md`
- Blog: `docs/blog/2026-11-18-mdp11-the-threshold-that-tuned-itself.md`

# Handover — 2026-07-05

**Branch:** `main`

## Last Session

Completed compliance record consolidation (trailing from #17). Created `RequirementStatus`, `RoutingDecisionRecord`, `TrustRoutingRequirement` in `io.casehub.blocks.routing`. Migrated aml (branch `issue-91-aml-workbench-ui`, commit `a85feb7`) and devtown (branch `issue-094-housekeeping-batch`, commit `8d6e89c`). Design decision: shared record captures routing decision facts (5 fields); domain-specific evidence quality metadata (`reconstructed`/`observerFailed`) stays in domain services. Discovered neural-text#20 is CLOSED — unblocks #16.

## Immediate Next Step

Pick #16 (RAG-enriched routing) — now unblocked. Run `/work` to start a branch.

## Cross-Module

**Breaking change from prior session — still pending consumer updates:**
- `ExecutionEventListener.onExecutionComplete` signature changed: `(ExecutionResult)` → `(ExecutionResult, Duration, int)`. `@Override` will cause a compile error.

**Commits landed on peer repo branches (will merge with their branch work):**
- aml `issue-91-aml-workbench-ui` — migrated compliance records to blocks types
- devtown `issue-094-housekeeping-batch` — migrated compliance records to blocks types

## What's Left

*Nothing trailing.*

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #16 | RAG-enriched routing — CbrCaseMemoryStore | M | High | **Unblocked** — neural-text#20 closed |
| #13 | LlmDecomposition — goal-to-plan | M | High | Builds on HTN pre-decomposition |
| #20 | Inter-agent channel setup during execution | M | Med | Deferred from #15 |
| #27 | Layered event summarisation | M | Med | Not yet — quarkmind still baking |

## References

- Diary: `blog/2026-07-05-mdp01-where-the-line-is.md` (workspace)
- Garden entry: `jvm/GE-20260705-01788f.md` (NullNode.toString() gotcha, prior session)

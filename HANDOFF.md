# Handover — 2026-07-06

**Branch:** `main`

## Last Session

Completed RAG-enriched routing (#16). Built composable prompt enrichment pipeline (RoutingPromptSection SPI + RoutingPromptAssembler), CBR-backed outcome recording via engine-api RoutingOutcomeRecorder SPI, and domain-pluggable feature extraction (RoutingFeatureExtractor). Design review caught a critical data-flow bug — WorkerOutcomeResolvedEvent fires only for failures (garden entry GE-20260706-56a75c). Cross-repo: engine-api SPI + engine runtime integration committed to engine main.

## Immediate Next Step

Pick from What's Next — #13 (LlmDecomposition) or #20 (inter-agent channels). Run `/work` to start.

## Cross-Module

**Breaking change from prior session — still pending consumer updates:**
- `ExecutionEventListener.onExecutionComplete` signature changed: `(ExecutionResult)` → `(ExecutionResult, Duration, int)`. `@Override` will cause a compile error.

**Commits landed on engine main this session:**
- `afd452a9` — RoutingOutcomeRecorder SPI in engine-api
- `09d69394` — WorkflowExecutionCompletedHandler integration (records routing outcomes)

**Commits landed on peer repo branches (will merge with their branch work):**
- aml `issue-91-aml-workbench-ui` — migrated compliance records to blocks types
- devtown `issue-094-housekeeping-batch` — migrated compliance records to blocks types

**Follow-up issues filed from design review:**
- #32 — Plan-Based CBR plan composition matching
- #33 — Structured feature extraction for specific domains
- #34 — General signal enrichment for non-LLM strategies
- #35 — Semantic embedding enrichment
- #36 — Gate rejection bias in outcome recording

## What's Left

*Nothing trailing.*

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #13 | LlmDecomposition — goal-to-plan | M | High | Builds on HTN pre-decomposition |
| #20 | Inter-agent channel setup during execution | M | Med | Deferred from #15 |
| #27 | Layered event summarisation | M | Med | Not yet — quarkmind still baking |

## References

- Diary: `blog/2026-07-06-mdp01-composable-routing-enrichment.md` (workspace)
- Spec: `docs/specs/2026-07-06-rag-enriched-routing-design.md` (project)
- Garden: `casehub-engine/GE-20260706-56a75c.md` (WorkerOutcomeResolvedEvent gotcha)

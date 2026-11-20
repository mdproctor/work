# HANDOFF — casehub-neocortex

## Last Session

Edge extraction scaling (#547, M/Med) — full brainstorm → design → plan → implement → review → squash cycle.

**Problem:** Knowledge graph edge/node ratio dropped from 1.20 to 0.76 as entity extraction improved — 64% more entities but only 3% more edges. Root cause: per-passage extraction isolation means entities from different passages never get connected.

**Solution:** Hybrid co-occurrence + LLM edge inference in both pipelines.

1. **Java `EdgeInferencePhase`** — new consolidation phase at `@Priority(22)`. Finds under-connected nodes, builds co-occurrence from `CaseMemoryStore` shared subjects/related_entities, infers edge types via `SessionPipeline` + `ParallelLlmExecutor`. `@IfBuildProperty` opt-in (disabled by default). `EdgeInferenceConfig`: degree-threshold=1, max-candidates=30. 7 unit tests.

2. **Python `infer_cross_passage_edges()`** — new function in `biographies/scripts/extract_structure.py`. Builds co-occurrence from memory related_entities, sends top-N candidates directly to Anthropic API. Configurable via `--edge-inference-top-n` (default 50).

**Review fixes applied:** hoisted memory scan outside per-node loop (was O(N) identical scans), replaced substring `contains()` with exact set membership to prevent false co-occurrence ("Art" matching "Arthur"), Python uses direct API call instead of `call_llm()` wrapper.

Filed: #1189 (Python call_llm refactoring), #1190 (parseRelationships edge-case tests), #1191 (embedding-based candidate identification). All queued.

Blog: `docs/blog/2026-11-20-mdp01-the-graph-that-forgot-its-edges.md`

## Immediate Next Step

Run `work continue` — queue advances to #1172 (CognitionTestHarness CAPS trajectory accessor). Fresh context recommended — topic change from knowledge graph to test harness.

## Queue Status

| # | Issue | Scale | Status |
|---|-------|-------|--------|
| 547 | Edge extraction not scaling with entity growth | M/Med | **Done** — landed |
| 1172 | CognitionTestHarness CAPS trajectory accessor | — | **Active** — next |
| 1171 | CAPS settling trajectory assertions | — | Open |
| 1165 | DevelopmentalPhaseResolver MindMapNode tests | — | Open |
| 1164 | SituationalSnapshotAccessorProducer tests | — | Open |
| 964 | AmpdFacets YAML round-trip test | — | Open |
| 1153 | SituationalPersonalityParticipant coverage | — | Open |
| 1079 | EffectiveCognitiveDefaults field classification test | — | Open |
| 1123 | Personality evolution integration tests | — | Open |
| 930 | CognitionCore 300-tick scenario validation | — | Open |
| 1183 | Extend evictIdle to remaining orchestrators | S/Low | Open |
| 1184 | Wire registerTenant into tick-time participants | XS/Low | Open |
| 1189 | Python call_llm refactoring | XS/Low | Open (filed this session) |
| 1190 | parseRelationships edge-case tests | XS/Low | Open (filed this session) |
| 1191 | Embedding-based candidate identification | S/Med | Open (filed this session) |

## References

- Blog: `docs/blog/2026-11-20-mdp01-the-graph-that-forgot-its-edges.md`
- Spec: `docs/specs/issue-547-edge-extraction-scaling/2026-11-20-edge-extraction-scaling-design.md`
- Plan: `plans/2026-11-20-edge-extraction-scaling.md` (workspace)

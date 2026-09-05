# Design Journal — issue-233-summarisation-yaml-surface

## 2026-09-05 — Unified summarisation model + Batch 1 implementation

### Design phase

Brainstormed and designed a YAML pipeline surface for the summarisation framework (#233, #231, #232). The design started as three new modules layered on existing types, but evolved into a full unification of blocks' three batch-transform subsystems (temporal, content, observation) after recognising they shared the same accumulate→batch→transform pattern.

Key design decisions (14 total, captured in decisions.md):
- **D1:** LevelEvent gains tenancyId — clean break, no convenience factory
- **D13:** StatefulSummariser<IN, OUT, S> — framework-managed state per partition, eliminates ad-hoc state in phase-detect and ContentSummariser
- **D14:** ContentSummariser<T, R> — generified result type breaks qhorus dependency; asSummariser() default method eliminates the bridge adapter

Decision review (standard, 3 rounds) revised 8 of 10 original decisions and surfaced 2 new ones (tick interval as deployment concern, key extractor widening).

Post-spec review (standard, 3 rounds) added: built-in output schemas, phase-detect lifecycle details, tenant partitioning as framework guarantee, keyed completion context specification.

### Implementation — Batch 1 (5 tasks)

1. Created `casehub-blocks-summarisation-api` module — 10 types extracted via IntelliJ ide_move_file
2. Added tenancyId to LevelEvent — 248 call sites updated via IntelliJ structural search/replace
3. Widened KeyedAccumulator key extractor to Function<LevelEvent<E>, K>
4. Added StatefulSummariser SPI with framework-managed state in SummarisationRunner
5. Generified ContentSummariser<T, R>, moved to summarisation-api, deleted ContentSummariserToSummariser bridge

All tests green: 103 (api) + 1945 (blocks) = 2048 tests passing.

### Remaining work

4 batches remain: CloudEvent bridge (#232), YAML canonical model + registry, PipelineCompiler + 5 built-in types, Quarkus deployment module + integration test.

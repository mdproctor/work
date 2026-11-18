# HANDOFF — casehub-neocortex

## Last Session

Completed three issues in one session — the memory hygiene migration plus two immediate follow-ups. All landed on main, published to origin.

**#1115 — Receive memory hygiene implementation from agency#447** (M/Med)

- 11 types in cognition-api: 4 SPIs (`ConfidenceScorer`, `ConsolidationSummariser`, `IntegrityChecker`, `SemanticIntegrityChecker`) + 7 value types (`RetentionConfig`, `RetentionScore`, `HygieneEvent`, `HygieneTick`, `MaintenanceTick`, `IntegrityViolation`, `ViolationType`)
- 8 implementations in cognition: `MemoryHygieneOrchestratorImpl`, `MemoryHygieneScheduler`, `ArousalScorer`, `SurpriseScorer`, `CompositeConfidenceScorer`, `WeightedScorer`, `DefaultIntegrityChecker`, `NoOpSemanticIntegrityChecker`, `NoOpConsolidationSummariser`
- 5 default bean producers in `CognitionDefaultBeans`
- Design spec + implementation plan in workspace

**#1121 — Code review cleanups** (S/Low)

- Striped lock array (16 stripes) replacing unbounded `ConcurrentHashMap<String, ReentrantLock>`
- Canonical `Subject.of("memory", entityId)` replacing deprecated 4-arg `EraseRequest`
- O(n) `Set<String>` lookup replacing O(n²) stream scan for survivor filtering

**#1120 — LLM-backed ConsolidationSummariser** (S/Low)

- `LlmConsolidationSummariser` using `SessionPipeline` with `ClearingPolicy.EVERY_CALL`
- CDI producer auto-selects LLM when `SessionPipeline` available, NoOp otherwise
- Enables the consolidation path in `MemoryHygieneOrchestratorImpl` — previously dead under NoOp

**Key design decisions:**

- Evolution types (11 files) excluded — they implement agency-specific SPIs. Tracked in #1119 for a neocortex-native consolidation phase approach.
- Agency's `ReflectionStore` replaced by existing neocortex `ReflectionOrchestrator` — no capability lost.
- Agency's `ContentSummariser` replaced by `ConsolidationSummariser` SPI — simpler text-in/text-out contract.

**Tests:** 30 across 5 test files, full cognition module (1196 tests) green.

**Blog:** "The Migration That Needed a Scalpel" — revised with follow-up sections on the cleanups and LLM summariser.

## Issues Filed This Session

| # | Title | Scale | Complexity | Status |
|---|-------|-------|------------|--------|
| #1119 | Memory hygiene parameter adaptation via consolidation phase | M | Med | Open — scheduled in queue |
| #1120 | LLM-backed ConsolidationSummariser implementation | S | Low | **Closed** |
| #1121 | Memory hygiene: tickLocks + EraseRequest + filter | S | Low | **Closed** |

## Also This Session — CognitiveLifecycle follow-ups

**#1111 — Defensive tag fallback** (XS/Low) — `getSimpleName()` returns `""` for anonymous classes; falls back to `getName()`. Landed as `46705bb8`.

**#1110 — Salience computation** (S/Low) — `CognitionPromptRenderer` gains `default SectionTier sectionTier()`. `contributeBlocks()` maps CORE→1.0, CONTEXTUAL→0.6, SUPPLEMENTARY→0.3, null→0.5. Landed as `00cc4281`.

Both from #1105 (CognitiveLifecycle SPI, landed earlier this session).

## Cross-Repo State

| Repo | Issue | Status |
|------|-------|--------|
| eidos | #240 | **Landed** — `AssembledPrompt.model` field |
| neocortex | #1105 | **Closed** — CognitiveLifecycle SPI |
| neocortex | #1115 | **Closed** — Memory hygiene received |
| neocortex | #1110, #1111 | **Closed** — Salience + tag fallback |
| agency | #447 | **OPEN** — Move remaining cognitive code (memory hygiene landed in neocortex; evolution types excluded, tracked in #1119) |
| agency | #490 | **OPEN** — Adopt CognitiveLifecycle, delete dead speech-api types. Blocked by #447 |

## Immediate Next Step

Queue has remaining items including #887 (Image capture), #888 (Captcha inbox), #1119 (hygiene adaptation), #1110 (closed), #1111 (closed). Agency#447 is unblocked and ready for a fresh session.

## References

- `specs/issue-1115-receive-memory-hygiene/2026-11-18-receive-memory-hygiene-design.md` — design spec
- `plans/2026-11-18-receive-memory-hygiene.md` — implementation plan
- `blog/2026-11-18-mdp10-the-migration-that-needed-a-scalpel.md` — diary entry
- `blog/2026-11-18-mdp09-the-interface-that-was-already-there.md` — CognitiveLifecycle diary
- `specs/issue-1105-unify-prompt-assembly/2026-11-18-unify-prompt-assembly-design.md` — prompt unification spec
- casehubio/agency#447 — parent migration issue
- casehubio/agency#490 — blocked downstream (adopt CognitiveLifecycle)

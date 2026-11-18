# HANDOFF — casehub-neocortex

## Last Session

Completed **#1115 — Receive memory hygiene implementation from agency#447**. Landed on main, published to origin.

**What was built:**

- **Memory hygiene value types + SPIs in cognition-api:** `ConfidenceScorer`, `ConsolidationSummariser` (new — replaces agency's `ContentSummariser`), `IntegrityChecker`, `SemanticIntegrityChecker` SPIs. `RetentionConfig`, `RetentionScore`, `HygieneEvent`, `HygieneTick`, `MaintenanceTick`, `IntegrityViolation`, `ViolationType` value types.

- **Implementations in cognition:** `MemoryHygieneOrchestratorImpl` (implements the pre-existing `MemoryHygieneOrchestrator` SPI — retention scoring, eviction, consolidation, per-agent locking), `MemoryHygieneScheduler` (composes orchestrator + `ReflectionOrchestrator` + `IntegrityChecker`), `ArousalScorer`/`SurpriseScorer` (dual `ContentScorer` + `ConfidenceScorer`), `CompositeConfidenceScorer`/`WeightedScorer`, `DefaultIntegrityChecker`, `NoOpSemanticIntegrityChecker`, `NoOpConsolidationSummariser`. 5 default bean producers in `CognitionDefaultBeans`.

- **25 tests** across 4 test files, 1196 total cognition module tests green.

**Key design decisions:**

- Evolution types (7 agency `social/memory/evolution/` + 4 `social/cognition/evolution/`) excluded — they implement agency-specific SPIs (`ImprovementProposalSource`, `RegressionEvaluator`, etc.). Tracked in #1119 for a neocortex-native consolidation phase approach instead.
- Agency's `ReflectionStore`/`ReflectionQueryStore` replaced by existing neocortex `ReflectionOrchestrator` — no capability lost.
- Agency's `ContentSummariser` replaced by `ConsolidationSummariser` SPI — simpler text-in/text-out contract.
- Agency's `KeyedLock` replaced by inline `ConcurrentHashMap<String, ReentrantLock>`.

**Code review findings (filed):** #1121 — unbounded tickLocks growth, deprecated `EraseRequest` constructor, O(n²) survivor filtering. Three inline fixes applied (null guard, stale gap state, redundant condition).

**Blog:** "The Migration That Needed a Scalpel" — decomposing cross-repo migrations, first-principles analysis of evolution types.

## Issues Filed This Session

| # | Title | Scale | Complexity |
|---|-------|-------|------------|
| #1119 | Memory hygiene parameter adaptation via consolidation phase | M | Med |
| #1120 | LLM-backed ConsolidationSummariser implementation | S | Low |
| #1121 | Memory hygiene: unbounded tickLocks + deprecated EraseRequest + O(n²) filter | S | Low |

## Immediate Next Step

Queue position 5/7. Check `.queue` for the next active issue.

## References

- `specs/issue-1115-receive-memory-hygiene/2026-11-18-receive-memory-hygiene-design.md` — design spec
- `plans/2026-11-18-receive-memory-hygiene.md` — implementation plan
- `blog/2026-11-18-mdp10-the-migration-that-needed-a-scalpel.md` — diary entry
- casehubio/agency#447 — parent migration issue
- casehubio/agency#490 — blocked downstream (adopt CognitiveLifecycle)

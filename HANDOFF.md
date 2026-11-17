# HANDOFF — casehub-agency

## Session summary (2026-11-17, session 2)

Landed 2 issues, filed 2 follow-ups:

| # | SHA | Summary |
|---|-----|---------|
| #481 | `1f1e9727` | Pass `smoothingAlpha` as 14th arg to `DriveConfig` canonical constructor |
| #484 | `f8cffec5` | Route all 3 CognitionCore assembly sites through CognitivePipelineBuilder / CognitionCoreFactory |

**Cross-repo:** neocortex `3d20b9ad` — CognitionCore constructors package-private + 4 test file migrations

**Filed:**
- #485 — expose `smoothingAlpha` in `DriveConfigSpec` for YAML configuration
- #486 — wire `SituationClassifier` and `NeedTierMappingProvider` through Spring builder path

**Blog entries:**
- `docs/blog/2026-11-17-mdp04-the-field-your-yaml-never-sees.md` — YAML compilation layer as a stability boundary
- `docs/blog/2026-11-17-mdp05-the-factory-nobody-called.md` — CognitionCore factory migration

### Queue — 2 remaining items

| # | Title | Scale | Complexity | Notes |
|---|-------|-------|------------|-------|
| #447 | Remove residual cognitive code, complete neocortex migration | L | High | Next active — depends on #484 (now done) |
| #469 | Neocortex budget integration, wire platform budget types | M | Med | Independent |

### Key context for next session

**CognitionCore is now builder-only.** All three constructors are package-private in neocortex. External code must use `CognitivePipelineBuilder` or `CognitionCoreFactory`. The builder wires 14 `configure*()` calls automatically — appraisal, regulation, gut feeling, drive-goal bridge, effective defaults, entity rendering, etc.

**AgencyBeans SPI bridge pattern.** Three producers bridge agency types for the neocortex factory's CDI resolution: `MemoryHygieneSpiAdapter` → `neocortex.MemoryHygieneOrchestrator`, `EngagementRecorderCore::record` → `Consumer<EngagementEvent>`, and `CognitiveGoalOrchestrator` (with mood-to-PAD lambda). These return null when dependencies are absent — the factory's `Instance.isResolvable()` handles both cases.

**Pre-existing neocortex test failures.** 4 tests in `AppraisalStatePropagation`, `EmotionRegulationTickParticipant`, and `RecurringThoughtIntegration` fail on neocortex main independent of this work (confirmed via stash+test).

**Neocortex snapshot.** The locally installed neocortex snapshot includes the package-private constructors. If another session installs the remote snapshot (older), agency will regress to compile errors from `new CognitionCore(...)` in the removed hand-build code. Push the neocortex change first.

### Unpublished

agency project: 4 commits on main. agency workspace: 28 commits. neocortex: 1 commit. Publish with `canonical_sync.py publish`.

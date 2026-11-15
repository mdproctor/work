# HANDOFF — casehub-neocortex

## Last Session

Completed #958, #959, #970, #971 on branch `issue-958-drive-activation-curiosity-gap`. Three fixes, one verification, one closure of an already-resolved issue.

The session's most significant change: eliminating a double mood bridging bug that caused a negative attractor lock in cross-tick cognitive trajectories. Both `AppraisalTickParticipant.bridgeToMood()` and `CognitionCore.bridgeAppraisalToMood()` fired on the same appraisal result, doubling every mood signal. Combined with observation-primacy framing added to `LlmAppraisalStrategy`, this should break the negative mood basin that scored 2.3 on crossTickTrajectory eval.

### Issues completed

| # | Title | Scale | Outcome |
|---|-------|-------|---------|
| #958 | Drive activation from single-tick appraisal curiosity gap | S/Low | Tests only — chain works after #957's HOPE rule |
| #959 | Cross-tick trajectory negative attractor lock | M/High | Removed duplicate mood bridge + observation-primacy framing |
| #970 | DriveProfileDerivationPhase personality-aware classification | S/Med | Wired disposition metadata, eval test written |
| #971 | Fix eval test compilation failures from 926 | XS/Low | Already resolved — closed |

### What was built

- **Double bridge fix** — removed `bridgeToMood(result, context)` from `AppraisalTickParticipant` line 195; `CognitionCore.bridgeAppraisalToMood()` retained as canonical single bridge point. Capacity frustration bridge (line 344) preserved — it handles a separate appraisal result.
- **Observation-primacy framing** — `LlmAppraisalStrategy.buildUserMessage()` mood injection now labels itself "background — appraise the situation itself, not this mood" instead of raw unlabelled PAD numbers.
- **HOPE drive verification** — 3 tests: `hopeBoostsCuriosityAboveTriggerThreshold`, `hopeStrongerCuriosityDriverThanJoy`, `highNoveltyAppraisalDrivesCuriosityViaHope` (end-to-end SEC→drive trigger override).
- **Single-bridge mood test** — `appraisalMoodBridgeProducesProportionalShift` verifies FEAR(0.6) produces mood pleasure between -0.8 and -0.1 (not the -1.0+ from double bridging).
- **Personality-aware drive derivation** — `DriveProfileDerivationPhase` now accepts `CognitiveDefaultsRegistry`, adds `disposition.*` keys to classifier metadata. `DriveProfilePersonalityEvalTest` (llm-eval) compares personality-aware vs agnostic derivation.

### Key finding

The double mood bridge was invisible to unit tests because both methods were correct in isolation. The emergent feedback loop — mood→appraisal→emotions→double bridge→worse mood→anchored appraisal — only surfaced through cross-stage pipeline coherence evaluation. This reinforces that cognitive pipeline quality requires evaluation at the trace level, not the component level.

### Pending verification

- crossTickTrajectory eval score (was 2.3) should improve — needs LLM eval run
- DriveProfilePersonalityEvalTest needs LLM eval run to decide keep/remove for #970 wiring

### Next queue items

- #972 — Tension threshold sensitivity analysis (S/Med)
- #973 — CBR disposition filter isolation test
- #983 — SHAME affiliation suppression

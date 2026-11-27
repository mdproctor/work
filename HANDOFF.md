# HANDOFF — casehub-neocortex (slot 230)

## Completed: #1251 C8 EMA double-smoothing fix

Branch `issue-1251-ema-double-smoothing-fix` — 1 commit, ready to land.

### What was done

Fixed EMA smoothing applying twice per cognitive tick in `CognitionCore.tick()`. The core called `drives.tick()` twice — once pre-DERIVED phase and again post-appraisal — both applying EMA smoothing. The second tick smoothed against the already-smoothed first-tick result instead of the previous core-tick's profile, over-dampening drive deactivation (0.524 vs correct 0.650).

**Fix:** Extracted shared logic into `DriveOrchestrator.evaluateAndCompose()` with explicit smoothing baseline. `tick()` saves the pre-tick profile; new `recomposeWithModulations()` reuses that baseline so smoothing applies exactly once. `CognitionCore` now calls `recomposeWithModulations()` for the post-appraisal pass.

**Files changed:** `DriveOrchestrator.java` (refactored + new method), `CognitionCore.java` (1-line caller change), `DriveOrchestratorTest.java` (2 new tests).

### Queue status

#1251 done on branch. Next active: #1252 (F12 per-type confidence decay).

### Notes for next session

- Pre-existing test failure: `EmotionMappingIntegrationTest.shameWithUrgencyHasElevatedArousalAboveBaseline` — not related to this change, from commit `2aa181532`.
- The workspace and project repos have diverged on main (workspace has workspace-only commits, project has design/plan commits from prior branches). This caused `workspace_not_level` during `work start` — was resolved with `start_anyway_from_the_stale_base`.

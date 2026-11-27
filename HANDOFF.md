# HANDOFF — casehub-neocortex (slot 230)

## Completed: #1252 F12 per-type confidence decay

Branch `issue-1252-per-type-confidence-decay` — 1 commit, ready to land.

### What was done

Fixed `ConfidenceDecayDecorator` to use per-edge-type half-life from vocabulary. Previously, all edges decayed at the global `defaultHalfLifeDays` rate, ignoring `EdgeTypeDefinition.defaultDecayHalfLifeDays()` from registered vocabulary.

**Fix:** Added `resolveHalfLife(String edgeType)` to `ConfidenceDecayDecorator`. It looks up the edge type (by canonical name or alias) in `delegate().registeredEdgeTypes()` and uses the per-type half-life when non-null, falling back to the global default.

**Files changed:** `ConfidenceDecayDecorator.java` (new `resolveHalfLife()` method, updated `withDecayedConfidence(MindMapEdge)`), `ConfidenceDecayDecoratorTest.java` (4 new tests).

### Queue status

Queue is empty — #1252 was the last item. All items done and ready to land.

### Notes for next session

- Pre-existing test failure: `EmotionMappingIntegrationTest.shameWithUrgencyHasElevatedArousalAboveBaseline` — not related to this change, from commit `2aa181532`.
- #1251 branch `issue-1251-ema-double-smoothing-fix` was landed to main in a prior session. #1252 branch was created from main.

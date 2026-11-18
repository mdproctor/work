# HANDOFF — casehub-agency (slot 223)

## Completed — #458 Author 23 conversational body poses (CLOSED)

**Branch:** `issue-458-author-23-conversational-body-poses` (landed on main, squashed 153 → 5 commits)
**Landed SHA:** `fbf4a5ce`

All 23 original poses addressed. 68 animations in `m2m-animations.glb`.

### What was built

**Authored/fixed poses:** finger-pointing-up, finger-pointing-forward, finger-wag (T-1000 pendulum), shrug (animated trap-style + morph frown), shrug-casual, head-shake (animated), head-shake-casual, one-hand-raised, self-hug (from shiver), arms-wide-open, hands-converge (steeple + finger ripple), hands-expand (animated wider idle-talking), hands-clasped-front (guitar mirror), hands-behind-back (mirrored), hands-on-hips (greeting mirror), hands-on-head, hands-over-mouth, hand-over-mouth, facepalm, double-facepalm, ninjajump (3 clips combined)

**Deleted (redundant or not authorable):** hands-alternating, hands-interlocked-chest, shocked-recoil, thinker, open-palms-presenting, hands-base-to-chest, hit-head, hit-chest

### Key techniques discovered

- **Quaternion mirror formula `(x,-y,-z,w)`** — tested all 4 sign combinations, this one produces correct symmetric poses for Mixamo rigs
- **Morph target injection** — added Wolf3D_Head/Wolf3D_Teeth mesh nodes with 72 morph targets to the bone-only animation bundle. First clips with facial expressions (shrug frowns)
- **Source clip reuse** — most poses built from existing animation frames (interact peak, guitar-playing, shiver, confused, greeting, pray) rather than from scratch
- **setFromUnitVectors for direction** — reliable for arm/forearm direction, but doesn't control twist. Must add separate twist via forearm axis rotation
- **Finger overlap verification** — always measure LeftHandMiddle2 vs RightHandMiddle2 distance for clasped/overlapping hand poses

### What does NOT work

- Blender IK round-trip — exports corrupt bone rotations
- Mixing body from one clip with arms from another — spine mismatch changes arm positions
- Guessing rotation sign — always test both directions or all 4 formulas
- `setFromUnitVectors` alone for hand orientation — controls direction but not twist

### Taxonomy restructured

5 categories: **conversational** (stance, hand-gesture, agreement, disagreement, emphasis, uncertainty), **idle** (relaxed, restless, self-amusing, dance, attention), **reactive** (positive, negative, greeting, distress), **dramatic** (death, combat, theatrical), **seated** (idle, talking)

**Allegory gesture tags:** tightrope-walk=careful, moonwalk=rewind, ninjajump=sneaky, meditate=calm, pray=hope, drumming=suspense

**Multi-listed clips:** dab (positive + self-amusing), clapping (agreement + positive), knock (emphasis + attention), dance-simple/dance-arm-pump (dance + positive)

## Active — #457 Integrate M2M animations with TalkingHead

**Status:** IN PROGRESS — Batch 1 complete, Batches 2–6 remain
**Branch:** `issue-457-integrate-m2m-animation-library`

### What this session delivered

**TalkingHead fork (#491) — COMPLETE, pushed to GitHub:**
- Forked `met4citizen/TalkingHead` into `casehubio/TalkingHead`
- 4 extension points added (207 lines, non-breaking):
  - **Bone ownership (D20):** canonical vocabulary, `claimBones`/`releaseBones`/`claimBoneGroup`/`releaseBoneGroup`, guards in `updatePoseBase`/`updatePoseDelta`/neck/hips
  - **Morph layer registry (D21):** `registerMorphLayer`/`unregisterMorphLayer`, ordered blend modes (`replace`/`additive`/`max`), runs after `updateMorphTargets`
  - **External mixer registration (D22):** `registerMixer`/`unregisterMixer`, coordinated frame update with auto bone claiming
  - **Lifecycle events (D23):** EventTarget delegation, `statechange`/`moodchange`/`audiostart`/`audioend`
- Bug fix: cache invalidation on avatar change (stale `_morphMeshes` and `_registeredMixers`)
- Vendored into agency at `speech-demo/.../vendor/talkinghead.mjs`, import map updated

**#457 Batch 1: Foundation — COMPLETE (3 tasks):**
- `PerformanceVocabulary` SPI in speech-api (gestureTags, moods, clips)
- `avatar-performance.json` — 68 clips, full taxonomy, pools, 55 gesture tags, 8 moods
- `JsonPerformanceVocabulary` — Jackson-backed implementation (7 tests)
- Protocol types: `Segment`, `TurnComplete`, `Gesture`, `Mood`, `PlaybackComplete`, `UserActivity`, `Cue` sealed interface (6 new codec tests, 126 total passing)
- `UtteranceSegmenter` — server-side `[gesture:tag]`/`[mood:tag]` parser with vocabulary validation, sentence splitting, forward binding (13 tests, 139 total passing)

**Design work:**
- Brainstormed fork approach (D18–D24), revised D6/D11/D14/D17
- Updated spec and implementation plan for fork approach
- Verification spike replaced by fork, `speakAudio` adopted as playback host

**Filed issues:**
- #491 — Fork TalkingHead with composable extension points (DONE)
- #492 — UtteranceSegmenter position tracking fragile (deferred from review)

### What's next — Batch 2: Server Session Refactor

This is the heaviest batch. It restructures `SpeechSession` per D10:
- **Task 5:** Capture/Turn split, `TurnExecutor`, `FloorState` — the core concurrency fix
- **Task 6:** `PromptSection` composition, `GesturePromptSection` — wires vocabulary into prompts

### Prerequisites still open

1. ~~Land #458~~ ✅ Done
2. ~~TalkingHead fork~~ ✅ Done (#491)
3. **Connectors issue** — `TextMark`/`MarkTiming` in voice-spi (blocks Batch 3)
4. **Neocortex issue** — proactive prompt sections, non-verbal outcomes (non-blocking)

### Known issues from code review

- #492: UtteranceSegmenter `indexOf` fragile with duplicate sentences; trailing tag `+2` lookahead is a magic number; space collapse shifts tag positions. Non-blocking for Batch 1, becomes important at Batch 3 (mark timing)
- Pre-existing: `ModelProvisioningService.ensureQwen3SharedLibrary()` stubbed out (connectors dependency gap)

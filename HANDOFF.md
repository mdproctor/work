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

**Status:** IN PROGRESS — Batches 1–2 complete, Batches 3+ remain
**Branch:** `issue-457-integrate-m2m-animation-library`

### What previous sessions delivered

**TalkingHead fork (#491) — COMPLETE, pushed to GitHub:**
- Forked `met4citizen/TalkingHead` into `casehubio/TalkingHead`
- 4 extension points added (207 lines, non-breaking): bone ownership (D20), morph layer registry (D21), external mixer registration (D22), lifecycle events (D23)
- Bug fix: cache invalidation on avatar change
- Vendored into agency at `speech-demo/.../vendor/talkinghead.mjs`

**Batch 1: Foundation — COMPLETE:**
- `PerformanceVocabulary` SPI in speech-api (gestureTags, moods, clips)
- `avatar-performance.json` — 68 clips, full taxonomy, pools, 55 gesture tags, 8 moods
- `JsonPerformanceVocabulary` — Jackson-backed implementation
- Protocol types: `Segment`, `TurnComplete`, `Gesture`, `Mood`, `PlaybackComplete`, `UserActivity`, `Cue` sealed interface
- `UtteranceSegmenter` — server-side tag parser with vocabulary validation, sentence splitting, forward binding

### What this session delivered — Batch 2: Server Session Refactor

**PromptSection composition (D7):**
- `SpeechPromptAssembler.assemble()` gains `PromptContext` overload (default method, backward compatible)
- `DefaultPromptAssembler` accepts composable `List<PromptSection>` — sections contribute text to system prompt when context is provided
- `GesturePromptSection` renders promptable gesture tags and moods from vocabulary into LLM prompt with placement rules

**Capture/Turn split (D10, D13):**
- `UserTurn` — immutable record from capture side to turn side
- `FloorState` — volatile lock-free state tracking for proactive suppression (listening, turn-in-flight, unacknowledged turns with expiry tracking)
- `TurnExecutor` — single virtual-thread executor serialising turn processing. Owns history, prompt assembly, LLM, TTS, outbound messages. Emits Segment + TurnComplete (new protocol) alongside legacy Response + Phonemes for client backward compat. Proactive turns suppressed during floor activity and dropped when stale at dequeue
- `SpeechSession` refactored from 535 lines to thin capture-side coordinator. Turn processing fully delegated to TurnExecutor
- `SpeechWebSocket` dispatches `PlaybackComplete` and `UserActivity` messages; proactive tick checks `FloorState.shouldSuppressProactive()` before evaluation

**Review fixes:**
- `recordCognitionInteraction` was broken (always passed empty lastAssistant after history moved to TurnExecutor) — fixed via `lastAssistantText()` on TurnExecutor
- FloorState transition ordering: swapped `onTurnComplete` before `onTurnEnd` to close a brief suppression gap

**Tests:** 173 total passing (34 new: 8 section composition, 4 GesturePromptSection, 12 FloorState, 14 TurnExecutor, minus 4 timing tests adapted to verify TurnComplete)

**Filed issues:**
- #495 — TurnExecutor unacknowledged-turn expiry timer (FloorState has state, no timer polls it)
- #496 — Wire StreamingTextToSpeech path in TurnExecutor (only non-streaming TTS ported)
- #497 — TurnExecutor.executeUserTurn swallows InterruptedException
- #498 — SilenceAttentionPolicy (D8) — idle attention bids after silence
- #499 — Remove legacy Response/Phonemes/Timing emission after client update

**Blog entry:** "The session that couldn't listen and talk" — capture/turn split architecture, threading model, FloorState coordination

### What's next — Batch 3+

The remaining work for #457 is client-side wiring and TTS timing:
- **Client-side floor states** (D13) — idle/listening/thinking/speaking pool switching in body-animations.mjs, driven by Segment/TurnComplete messages
- **Animation layering** (D11) — base layer + gesture layer with ducking in BodyAnimationSystem
- **Face compositing** (D6, D14) — morph layer registry for expression curves from body animations
- **Mark timing** (D4) — `TextMark`/`MarkTiming` in voice-spi for gesture synchronisation (BLOCKED by connectors peer issue)
- **Mood ownership** (D16) — cognition vs LLM mood with hysteresis mapping

### Prerequisites still open

1. ~~Land #458~~ ✅ Done
2. ~~TalkingHead fork~~ ✅ Done (#491)
3. ~~Server session refactor~~ ✅ Done (Batch 2)
4. **Connectors issue** — `TextMark`/`MarkTiming` in voice-spi (blocks Batch 3 mark timing)
5. **Neocortex issue** — proactive prompt sections, non-verbal outcomes (non-blocking)

### Known issues from code review

- #492: UtteranceSegmenter `indexOf` fragile with duplicate sentences; trailing tag `+2` lookahead; space collapse shifts tag positions
- #497: TurnExecutor.executeUserTurn catches Exception broadly (swallows InterruptedException)
- Pre-existing: `ModelProvisioningService.ensureQwen3SharedLibrary()` stubbed out (connectors dependency gap)
- Legacy protocol messages (Response, Phonemes) still emitted alongside new protocol — #499 tracks removal

# HANDOFF — casehub-agency (slot 223)

## Completed — #458 Author 23 conversational body poses (CLOSED)

**Branch:** `issue-458-author-23-conversational-body-poses` (landed on main, squashed 153 → 5 commits)
**Landed SHA:** `fbf4a5ce`

All 23 original poses addressed. 68 animations in `m2m-animations.glb`.

## Active — #457 Integrate M2M animations with TalkingHead

**Status:** LANDED — `467c25de` on canonical main
**Branch:** `issue-457-integrate-m2m-animation-library` (landed, 21 commits)
**Tests:** 182 passing (speech-ws)

### #490 reconciliation — completed

Ported D7 prompt composition to eidos-api types. `GesturePromptSection` became a standalone class with `render()`. `DefaultPromptAssembler` takes an optional `GesturePromptSection` directly. `SpeechPromptAssembler` returned to its single-method `@FunctionalInterface` form. `PromptContext` parameter removed from `TurnExecutor`.

### What previous sessions delivered

**TalkingHead fork (#491) — COMPLETE, pushed to GitHub:**
- Forked `met4citizen/TalkingHead` into `casehubio/TalkingHead`
- 4 extension points: bone ownership (D20), morph layer registry (D21), mixer registration (D22), lifecycle events (D23)
- Vendored into agency at `speech-demo/.../vendor/talkinghead.mjs`

**Batch 1: Foundation — COMPLETE:**
- `PerformanceVocabulary` SPI in speech-api
- `avatar-performance.json` — 68 clips, full taxonomy, 4 pools (idle/listening/thinking/speaking), 55 gesture tags, 8 moods
- Protocol types: `Segment`, `TurnComplete`, `Gesture`, `Mood`, `PlaybackComplete`, `UserActivity`, `Cue`
- `UtteranceSegmenter` — server-side tag parser with vocabulary validation

**Batch 2: Server Session Refactor — COMPLETE:**
- `PromptSection` composition in `DefaultPromptAssembler` + `GesturePromptSection` (D7)
- Capture/turn split: `UserTurn`, `FloorState`, `TurnExecutor` (D10, D13)
- `SpeechSession` refactored from 535 lines to thin capture-side coordinator

### What this session delivered — Batch 3: Client-Side Floor States + Cleanup Sweep

**Client-side floor states (D13):**
- `BodyAnimationSystem` rewritten — data-driven from `avatar-performance.json`, floor state machine (idle/listening/thinking/speaking), base+gesture animation layering with weight ducking (D11)
- Module reduced from 407 → 169 lines (data moved to JSON)
- Deleted: hardcoded `TAXONOMY`, `GESTURE_MAP`, `KEYWORD_GESTURES`, `parseGestureTags`, `classifyResponse`

**Client protocol handling (D3, D5):**
- `index.html` handles `segment`, `turnComplete`, `gesture`, `mood` messages
- Parses 8-byte binary audio frame headers for turn/segment pairing
- Floor state transitions: thinking (send/stop), listening (mic), speaking (first audio), idle (turn complete + all played)
- Sends `playbackComplete` and `userActivity` back to server
- Registers body mixer with TalkingHead via `registerMixer`/`boneClaim` (D22)
- Deleted: custom viseme rAF loop, viseme mesh discovery, tag parsing, mood detection, separate tickBody loop

**Bug fixes:**
- #492: UtteranceSegmenter — cumulative offsets (duplicate sentences), boundary-based trailing (variable whitespace), collapse-aware positions
- #495: Turn expiry timer — virtual thread polls FloorState every 2s
- #497: InterruptedException handling — restored interrupted status in TurnExecutor
- #499: Removed legacy Response/Phonemes emission, added 8-byte binary frame header
- #496: Wired StreamingTextToSpeech path in TurnExecutor
- #498: SilenceAttentionPolicy — opt-in idle attention bids after 30s/75s silence

**Code review fixes:**
- `handleStop` missing `recordCognitionInteraction` (mic-based speech skipped cognition memory)
- `SilenceAttentionPolicy` bid reset when silence drops below threshold
- Removed unused `THREE` import from index.html

**Tests:** 184 total passing (11 new this session)

**Blog entry:** "The floor the avatar never had" — floor state machine, protocol bridge, animation layering

**Cross-repo issues filed:**
- casehubio/connectors#303 (CLOSED) — TextMark/MarkTiming in voice-spi
- casehubio/neocortex#1136 (CLOSED) — Proactive prompt sections, non-verbal outcomes

### What this session delivered — Integration batch (#505-#512)

All 8 follow-up issues from Batch 3 completed and closed. 10 commits on main, 183 tests passing.

**Protocol cleanup (#505):** Removed dead `Response`, `Phonemes`, `Animation`, `Timing` message types — records, codec cases, tests. Updated CLAUDE.md protocol listing.

**Lip sync (#506):** Adopted TalkingHead's `speakAudio()` for viseme rendering. Converts `VisemeFrame` format to flat arrays (`visemes[]`, `vtimes[]`, `vdurations[]`). Gesture/mood cues route as timed marker callbacks. Custom `playQueue` retained as fallback for audio-only mode.

**Streaming race fix (#507):** `isFinal` flag in binary frame header byte 1 prevents early `PlaybackComplete` with streaming TTS. One-behind buffer on server ensures only last chunk carries `isFinal=1`. Client gates segment completion on final chunk in both speakAudio and fallback paths.

**Cue type safety (#508):** Explicit `"type"` discriminator in Cue JSON serialization (`"gesture"` / `"mood"`). Client checks `cue.type` instead of field presence.

**Duration-aware expiry (#509):** Turn expiry tracks cumulative estimated audio duration (`chars x 80ms`). Formula: `completedAt + totalAudioMs / minPlaybackRate + 5s`. `minPlaybackRate` configurable via `casehub.avatar.min-playback-rate` (default 0.6).

**Face compositing (#510):** Morph target tracks extracted from GLB clips at load time, converted to named expressions via reverse `morphTargetDictionary`, filtered to expression allow-list. `gestureExpressions` morph layer registered via TalkingHead D21 registry with additive blending. Expression weights follow gesture ducking.

**Mood ownership (#511):** Two paths per D16. Cognition: `AvatarCognition.currentAffect()` → `MoodMapper` PAD→discrete with dead-zone/hysteresis/dwell. LLM: turn-scoped `MoodCue` with 3s decay. Client priority: turn override > baseline > neutral.

**Mark timing (#512):** `Cue` records gain `charOffset` from segmenter tag positions. `TurnExecutor` builds `TextMark` entries, passes to `SynthesisOptions.marks`, applies `MarkTiming` from TTS result. Client computes `trigger = max(0, offsetMs - strokeMs - 150)`.

**Review fixes:** Streaming cue dedup (visemes/cues attached only to first chunk per segment). Morph layer lifecycle (unregister on bodyAnim dispose).

**Blog:** "The protocol that couldn't feel" — protocol correctness vs experiential completeness.

### Follow-up issues filed (4 items in queue)

| # | Title | Scale | Complexity |
|---|-------|-------|------------|
| #513 | MoodMapper dead-zone-to-null transition lacks dwell | XS | Low |
| #514 | Streaming TTS mark timing — word-boundary fallback | S | Med |
| #515 | Verify WebSocket thread safety for concurrent sends | XS | Low |
| #516 | MorphTargetInfluences regex — support named targets | XS | Low |

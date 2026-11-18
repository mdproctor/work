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

### Follow-up issues filed (8 items in queue)

| # | Title | Priority |
|---|-------|----------|
| #505 | Remove dead protocol types (Response, Phonemes, Animation, Timing) | XS — cleanup |
| #506 | Adopt speakAudio for viseme rendering (lips frozen) | M — avatar quality |
| #507 | Streaming TTS early PlaybackComplete (shared segmentIndex) | S — correctness |
| #508 | Add type discriminator to Cue serialization | XS — robustness |
| #509 | Audio-duration-aware turn expiry (not fixed 5s) | S — correctness |
| #510 | Face compositing — morph layer registry for expression curves (D6) | M — avatar quality |
| #511 | Mood ownership — cognition PAD mapping + LLM turn-scoped (D16) | M — feature |
| #512 | Mark timing — gesture stroke sync with TTS word onsets (D4) | M — feature |

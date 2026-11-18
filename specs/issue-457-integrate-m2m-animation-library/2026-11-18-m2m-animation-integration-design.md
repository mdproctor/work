# M2M Animation Library Integration — Design Spec

**Issue:** casehubio/agency#457
**Date:** 2026-11-18
**Decisions:** See `decisions.md` for full rationale, alternatives, and trade-offs (D1–D17, reviewed across 3 adversarial rounds; D18–D24 for TalkingHead fork approach).

## Overview

Wire the M2M animation library (68 clips authored in #458) into TalkingHead's speech pipeline with proper audio-gesture synchronisation, turn framing, morph target compositing, and mood ownership. The result is a production-grade avatar speech pipeline, not a demo integration.

## Prerequisites (D1, D17, D24)

1. **Land #458** via `work-end` first. Recreate #457 branch from updated main.
2. **TalkingHead fork issue** (D24) — fork casehubio/TalkingHead, implement four extension points (D20–D23: bone ownership, morph layer registry, external mixer registration, lifecycle events), vendor built `talkinghead.mjs` into agency. Replaces the verification spike.
3. **Connectors peer issue** (mark timing SPI) must land before #457 implementation starts.
4. **Neocortex peer issue** (proactive prompt sections, non-verbal outcomes) follows #457, non-blocking.

## Architecture

```
LLM → response text with [gesture:tag] and [mood:tag]
  ↓
UtteranceSegmenter (D2): single parser for all speech paths
  → ParsedSegment(cleanText, List<Cue>)
  ↓
Per consumer form (D9):
  TTS         → clean text
  Client      → Segment(text, visemes, cues) + binary audio w/ header
  History     → canonical markup (validated tags only)
  Vocabulary  → clean text (no tag pollution)
  Cognition   → clean text
```

### Protocol (D3)

Replaces `Phonemes` and `Response` with turn-framed messages:

| Message | Direction | Purpose |
|---------|-----------|---------|
| `Segment(turnSeq, index, text, hasAudio, visemes, cues)` | server→client | Per-sentence: display text + visemes + gesture/mood cues |
| `TurnComplete(turnSeq, segmentCount, origin)` | server→client | End-of-turn signal |
| `Gesture(tag, origin)` | server→client | Non-speech gestures (attention bids, cognition) |
| `Mood(state)` | server→client | Persistent mood baseline (cognition-owned) |
| `PlaybackComplete(turnSeq)` | client→server | Client finished playing turn's audio |
| `UserActivity(kind)` | client→server | Typing, visibility changes |

Binary audio frames carry an 8-byte header: `u8 version, u8 reserved, u16 segmentIndex, u32 turnSeq`. Client pairs frames to segments by identity, not arrival order.

### Gesture Timing (D4)

The server owns speech time via SSML-style mark timing:
- `SynthesisOptions` gains `List<TextMark>` marks
- `SynthesisResult` gains `List<MarkTiming>` with millisecond offsets
- **VITS: exact, guarded** — espeak phonemisation + duration output word boundaries, with consistency guard
- **Phoneme-only engines: phoneme-proportional** fallback
- **No phonemes: character-proportional** fallback
- `MarkTimingEnricher` decorator provides fallback tiers

The client owns animation time: `trigger = max(0, offsetMs/rate − strokeMs − crossfade/2)` where `strokeMs` is per-clip metadata from the performance vocabulary.

### Session Concurrency (D10)

SpeechSession splits into two sides:
- **Capture side** (inbound thread): STT stream, ring buffer, speaker ID, partial polling
- **Turn side** (single-thread TurnExecutor): history, prompt assembly, LLM, TTS, outbound messages

They share no mutable state. The capture side hands immutable `UserTurn` requests to the executor. One active turn per session. Proactive turns are suppressed during listening/thinking/unacknowledged playback, and re-checked at dequeue for staleness.

### Client Floor States (D13)

| State | Entered on | Base pool |
|-------|-----------|-----------|
| idle | TurnComplete + all audio played | `idle` |
| listening | mic start, UserActivity(typing) | `listening` |
| thinking | user stop/text sent | `thinking` (neutral attentive stances) |
| speaking | first audio-bearing segment plays | `speaking` |

### Animation Layering (D11, D22)

`BodyAnimationSystem`'s `AnimationMixer` is registered with TalkingHead via `head.registerMixer(bodyMixer, { boneClaim: [...] })` (D22). TalkingHead calls `bodyMixer.update(dt)` in its frame loop, eliminating the separate `tickBody` rAF loop.

Two concurrent layers within our mixer:
- **Base layer**: looping pool clip. Pool changes only swap the base.
- **Gesture layer**: one-shot clip. Base ducks to weight 0 during gesture, fades back when gesture finishes.

A pool change during a gesture swaps the base underneath with no visible effect.

### Face Compositing (D6, D14, D21)

The TalkingHead fork's morph layer registry (D21) orders all face writers per frame:
1. Layer 0 (TalkingHead internal): blink, mood, eyes (base)
2. Viseme layer (registered, `replace` mode): `viseme_*` channels
3. Gesture expression layer (registered, `additive` mode): expression channels, clamped to [0,1], weight follows gesture layer

This replaces the `scene.onBeforeRender` workaround from the original design. Each layer is registered via `head.registerMorphLayer()` and TalkingHead runs them in declared order.

Morph target tracks are stripped from body clips at load. Expression curves (brow, mouthFrown, mouthSmile, cheek, noseSneer, mouthPucker) are extracted name-keyed from the animation bundle's mesh dictionary, applied through the avatar's dictionary.

### Performance Vocabulary (D12)

Single source of truth: `avatar-performance.json`
- `clips`: every GLB clip with `strokeMs` and optional expression-curve overrides
- `taxonomy`: categories → subcategories → weighted clips
- `pools`: per floor state → weighted subcategory refs
- `gestures`: tag + description + target + `promptable` flag
- `moods`: TalkingHead mood set with descriptions and `promptable`

`body-animations.mjs` holds no data — pure playback logic. Java validates via `PerformanceVocabulary` SPI in speech-api.

### LLM Prompt (D7)

`GesturePromptSection` (speech-api `PromptSection`) generated from vocabulary:
- Each promptable tag with one-line description
- Rules: place tag before its word, at most one per sentence, none when nothing fits
- `DefaultPromptAssembler` gains `PromptSection` composition (first production composer of that SPI)
- `SpeechPromptAssembler.assemble` gains `PromptContext` parameter

### Mood Ownership (D16)

One owner per session, by configuration:
- **Cognition with mood**: `AvatarCognition.currentAffect()` → PAD vector → discrete mood via octant mapping with hysteresis + dead-zone. `Mood(state)` message on change.
- **No cognition**: LLM owns mood. `[mood:X]` → turn-scoped `MoodCue`, decays after playback + 3s hold.

### Idle Attention (D8)

Server-owned, opt-in via `AvatarConfig`:
- **With cognition**: non-verbal bids via neocortex (D17 follow-up). Until then, no attention gestures.
- **Without cognition**: `SilenceAttentionPolicy` — 30s subtle bid, 75s stronger bid, then nothing.
- Activity resets: user messages, `PlaybackComplete`, throttled `UserActivity`.

### Keyword Fallback — Dropped (D15)

No keyword fallback. The speaking pool provides continuous motion. Tag adherence is logged per turn. Revisit data-driven if models ignore instructions.

## TalkingHead Fork (D18–D24)

The original design included a verification spike to test whether TalkingHead's existing API was sufficient. Source analysis of TalkingHead 1.8 (frame order: `updatePoseBase` → `mixer.update` → `updatePoseDelta` → dynamic bones → `opt.update(dt)` → `updateMorphTargets` → render) confirmed that the existing hooks are insufficient: bones are overwritten before the hook, morphs are overwritten after.

**Resolution:** Fork met4citizen/TalkingHead into casehubio/TalkingHead with four clean extension points designed for upstream contribution:

1. **Bone ownership (D20)** — canonical bone vocabulary with rig mappings (Mixamo, RPM, VRM). `claimBones()` / `releaseBones()` by canonical name. Convenience groups (`claimBoneGroup('body')`). `updatePoseBase`/`updatePoseDelta` skip claimed bones.
2. **Morph layer registry (D21)** — ordered layers with blend modes (`replace`, `additive`, `max`). TalkingHead's internal writers are layer 0. External systems register additional layers.
3. **External mixer registration (D22)** — `registerMixer(mixer, { boneClaim })`. TalkingHead calls `mixer.update(dt)` at the correct frame position. Eliminates separate rAF loops.
4. **Lifecycle events (D23)** — EventTarget / addEventListener. `statechange`, `moodchange`, `audiostart`/`audioend`.

**Distribution (D19):** Vendored `talkinghead.mjs` checked into agency repo at `speech-demo/.../vendor/talkinghead.mjs`. Import map updated from jsdelivr CDN to local path. No CI changes. When upstream accepts the PRs, revert to upstream CDN URL.

**Playback host: `speakAudio` adopted.** With the fork providing bone ownership and morph layers, all three original spike conditions are met by construction. The custom audio queue is deleted. Gesture cues become `speakAudio` markers.

## Cross-Repo Changes

| Repo | Change | Blocking? |
|------|--------|-----------|
| casehubio/TalkingHead (fork) | Bone ownership API (D20), morph layer registry (D21), external mixer registration (D22), lifecycle events (D23) | Yes — blocks #457 (separate issue, D24) |
| connectors | `TextMark`/`MarkTiming` in voice-spi, `MarkTimingEnricher`, VITS guarded-exact | Yes — blocks #457 |
| agency (speech-api) | `PerformanceVocabulary` SPI, `PromptSection` composition, `PromptContext` param, `Affect` record, `AvatarCognition.currentAffect` | In-repo |
| agency (speech-ws) | `UtteranceSegmenter`, `Segment`/`TurnComplete`/`Gesture`/`Mood` protocol, capture/turn split, `TurnExecutor`, `SilenceAttentionPolicy`, `GesturePromptSection`, `MoodMapper` | In-repo |
| agency (speech-demo) | Vendored `talkinghead.mjs` (D19), `avatar-performance.json`, `body-animations.mjs` vocabulary-driven rewrite with registered mixer + morph layers, `index.html` floor states + `speakAudio` playback + new protocol | In-repo |
| neocortex | Caller-supplied proactive prompt sections, non-verbal proactive outcome, delivery feedback | No — follows #457 |

## Testing

- **Build-time**: vocabulary ↔ GLB clip inventory, taxonomy resolution, expression channel names
- **Unit**: `UtteranceSegmenter` parsing (forward binding, trailing tags, malformed markup, concurrent tags)
- **Unit**: `GestureExtraction` per consumer form (clean, canonical, vocabulary-filtered)
- **Unit**: Turn serialisation (capture/turn split, proactive suppression, dequeue staleness check)
- **Unit**: `MoodMapper` PAD→discrete with hysteresis and dead-zone
- **Unit**: `MarkTimingEnricher` fallback tiers
- **Integration**: `Segment`/audio pairing by header identity
- **Manual**: gesture fires at correct word during speech
- **Manual**: TTS does not vocalize tags
- **Manual**: floor state transitions (idle→listening→thinking→speaking→idle)
- **Manual**: morph target expressions during idle, clean visemes during speech
- **Manual**: attention bids at 30s/75s, reset on activity

## References

- [SpeechSession.java] — server speech pipeline, handleStop L239-245, handleProactiveSpeech L175-176
- [AvatarMessage.java] — current WebSocket protocol (to be replaced)
- [body-animations.mjs (#458)] — BodyAnimationSystem, GESTURE_MAP, TAXONOMY
- [index.html (#458)] — TalkingHead 1.8 integration, playback queue, viseme loop
- [casehub-connectors-voice-spi] — SynthesisOptions, SynthesisResult, LipSyncEnricher
- [CognitionAvatarAdapter] — wrapAssembler L51-75, evaluateProactive L94-103
- [MoodOrchestrator] — currentMood(agentId, tenantId) → Optional<MoodState>
- [Azure avatar SSML gestures](https://learn.microsoft.com/en-nz/azure/ai-services/speech-service/text-to-speech-avatar/avatar-gestures-with-ssml)
- [AIAvatarKit](https://github.com/uezo/aiavatarkit) — LLM → animation pipeline
- [SARGes](https://arxiv.org/pdf/2503.20202) — LLM ethogram-based gesture labels
- [TANDE](https://arxiv.org/pdf/2607.13357) — structured JSON gesture output
- McNeill 1992 / Kendon 2004 — gesture stroke–affiliate synchrony

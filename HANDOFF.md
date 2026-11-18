# HANDOFF — casehub-agency (slot 223)

## Completed — #458 Author 23 conversational body poses (CLOSED)

**Branch:** `issue-458-author-23-conversational-body-poses`

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

**Status:** OPEN — design and planning complete, ready for implementation
**Branch:** `issue-457-integrate-m2m-animation-library` (empty — needs rebase after #458 lands)

### Design completed (17 decisions, 3 adversarial review rounds)

Full spec: `specs/issue-457-integrate-m2m-animation-library/2026-11-18-m2m-animation-integration-design.md`
Full decisions: `specs/issue-457-integrate-m2m-animation-library/decisions.md`
Implementation plan: `plans/2026-11-18-m2m-animation-integration.md` (14 tasks, 7 batches)

### Architecture summary

- **Server-side `UtteranceSegmenter`** — single parser for `[gesture:tag]`/`[mood:tag]`, strips before TTS, all 3 speech paths
- **Turn-framed protocol** — replaces `Phonemes`/`Response` with `Segment`/`TurnComplete`/`Gesture`/`Mood`. Binary audio frames carry 8-byte identity headers.
- **Mark timing** — SSML-style `TextMark`/`MarkTiming` via connectors voice-spi for word-level gesture sync
- **Session capture/turn split** — capture side (STT, speaker ID) and turn side (LLM, TTS, outbound) share no mutable state via `TurnExecutor`
- **Performance vocabulary** — `avatar-performance.json` as single source of truth for taxonomy, pools, gestures, moods. `PerformanceVocabulary` SPI in speech-api.
- **Animation layering** — base layer (pool cycling) + gesture layer (one-shots). Base ducks under gestures.
- **Face compositor** — single `scene.onBeforeRender` hook: TalkingHead base → visemes → gesture expression curves (additive, clamped)
- **Floor states** — idle/listening/thinking/speaking with per-state pools
- **Mood ownership** — cognition-owned (PAD→discrete with hysteresis) or LLM-owned (turn-scoped with decay)
- **Keyword fallback dropped** — adherence logging instead, speaking pool provides continuous motion

### Prerequisites before implementation

1. **Land #458** via `work-end` — code not on main yet
2. **File + land connectors issue** — `TextMark`/`MarkTiming` in `casehub-connectors-voice-spi` (blocks Batch 3)
3. **File neocortex issue** — proactive prompt sections, non-verbal outcomes (non-blocking, follows #457)
4. **TalkingHead spike** — verify `speakAudio` API (Batch 0, Task 1)

### Key scope changes from review

- Protocol is a breaking change (Phonemes/Response removed)
- Keyword fallback dropped (D15) — was in original scope
- Cognition mood ownership added (D16) — not in original scope
- Cross-repo dependency on connectors (D17)

### Ideas logged

- Contribute body animation system (BodyAnimationSystem + M2M CC0 library + gesture tag protocol) to met4citizen/TalkingHead upstream — see IDEAS.md

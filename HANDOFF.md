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

**Status:** OPEN — design needs revision before implementation
**Branch:** `issue-457-integrate-m2m-animation-library` (fresh from main, includes all #458 work)

### What this session did

1. Landed #458 — queue surgery (uncheck, reactivate), squashed 153 → 5 commits, rebased onto main, resolved AgencyBeans.java conflict
2. Synced all repos — fixed conflicts in connectors (.idea/compiler.xml), wsp-casehub-blocks (HANDOFF.md), wsp-casehub-connectors (HANDOFF.md), wsp-casehub-neocortex (HANDOFF.md, .work-progress, .queue)
3. Published everything to GitHub — agency (10 commits to casehubio + fork), connectors (1), 3 workspace repos (54 + 3 + 63 commits)
4. Recreated #457 branch from updated main

### Design revision needed — fork TalkingHead instead of overlay

Late-session decision: the overlay approach (BodyAnimationSystem alongside TalkingHead, compositing via onBeforeRender hack) fights TalkingHead's design at too many seams. A fork of met4citizen/TalkingHead is cleaner:

**Why fork:**
- The overlay requires a spike (Batch 0) just to check if body bone ownership can be separated — unknown seam
- Face compositor is bolted on via `scene.onBeforeRender` post-processing hack
- Custom audio pipeline bypasses TalkingHead's speak/speakAudio entirely
- Protocol replaces TalkingHead's built-in Phonemes/Response handling
- We're already routing around most of TalkingHead's speech pipeline

**What the fork adds:**
- Body animation as a first-class layer with configurable bone ownership (eliminates spike)
- Face compositor built into the render loop properly
- Mark timing / gesture callback support in the audio pipeline natively
- Segment protocol support in speakAudio

**Contribution path:** fork → build #457 against it → submit body animation system + API changes as PR to met4citizen/TalkingHead. If accepted, fork dissolves back to CDN. If not, maintain a thin layered delta — the work exists either way.

**Impact on existing plan:** several decisions assumed overlay — D14 (speakAudio), D6 (expression curves), D11 (layered animation) would simplify or change. The spec and plan need a brainstorm pass before implementation.

### Prerequisites

1. ~~Land #458~~ ✅ Done
2. **Brainstorm fork approach** — revise design spec and plan for TalkingHead fork
3. **File + land connectors issue** — `TextMark`/`MarkTiming` in `casehub-connectors-voice-spi` (blocks Batch 3)
4. **File neocortex issue** — proactive prompt sections, non-verbal outcomes (non-blocking, follows #457)

### Ideas logged

- Contribute body animation system (BodyAnimationSystem + M2M CC0 library + gesture tag protocol) to met4citizen/TalkingHead upstream — see IDEAS.md

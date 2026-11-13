# HANDOFF — casehub-agency (slot 223)

## Active Issue — #457 Avatar demo animation integration

### Current state — Animation integration done, pose authoring next

**52 M2M animations retargeted and integrated into speech-demo.** Body animation system with taxonomy-based picker, idle cycle, gesture tags, TalkingHead mood wiring, and head bone masking — all committed on branch `issue-455-cc0-motion-base-tagged-animation-library`.

### What was done this session

1. **Batch retarget** — wrote vitest script using PoseAlignedRetargeter from M2M PR #160. Retargeted 52 animations onto RPM avatar with proportion correction. Output: `m2m-animations.glb` (6.4 MB) in speech-demo animations dir. No runtime retarget needed — clips use RPM bone names directly.

2. **Body animation module** (`body-animations.mjs`) —
   - Three-level taxonomy: category → subcategory → clips (with weights)
   - Categories: conversational, idle, reactive, dramatic, seated
   - Subcategories: stance, hand-gesture, agreement, disagreement, emphasis, uncertainty, relaxed, personality, dance, etc.
   - Speaking pool draws from `conversational.*`, idle pool draws from `idle.*`
   - Gesture tags resolve to specific clips or pick randomly from subcategory variants
   - `startSpeakingCycle()` / `startIdleCycle()` / `setSpeaking()` API ready but not yet wired to audio playback
   - Head/Neck/Eye bones masked from all body clips — TalkingHead owns gaze

3. **index.html integration** —
   - Replaced old FBX keyword-match system with M2M body animations
   - `[gesture:dab]` tags parsed and stripped from LLM response text
   - `[mood:happy]` tags drive TalkingHead facial expressions via `head.setMood()`
   - Keyword fallback for responses without explicit tags
   - Animation picker dropdown updated with full clip catalog
   - Separate THREE.AnimationMixer on `head.armature` — doesn't interfere with TalkingHead

4. **Pose brainstorm** — 23 missing poses identified and filed on #457 (4 comments). Organised into conversational stances (5), hand gesture cycle (4), reactive gestures (6), emotional poses (8).

### What to do next

**1. Author missing poses in Blender (23 clips)**
Create on M2M skeleton, export as GLB, retarget onto RPM using the batch script. Key groups:

| Group | Poses | Priority |
|-------|-------|----------|
| Conversational stances | hands-clasped-front, hands-behind-back, thinker (fist under chin), hands-on-hips, open-palms-presenting | P1 |
| Hand gesture cycle | hands-base-to-chest, hands-expand, hands-converge, hands-alternating | P1 |
| Reactive gestures | head-shake, finger-wag, shrug, one-hand-raised, thumbs-up, finger-pointing-up | P2 |
| Emotional poses | self-hug, arms-wide-open, facepalm, shocked-recoil, hands-over-mouth, hand-over-mouth, hands-on-head, hands-interlocked-chest | P2 |

**2. Wire speaking-state to audio playback**
Hook `bodyAnim.startSpeakingCycle()` into `playNextQueued()` (audio starts) and `bodyAnim.startIdleCycle()` into audio `onended`. Currently the API exists but nothing calls it.

**3. Add variant depth per subcategory**
Each subcategory should have 3-5 variants (e.g. agreement: enthusiastic, casual, defensive, reluctant, contemplative). Currently most have 1-2. This is what makes the avatar feel natural vs repetitive.

**4. LLM gesture density**
Future: system prompt instructs LLM to embed `[gesture:X]` tags throughout responses, not just one per message. Italian-style body language — gestures punctuating speech continuously. Taxonomy and parsing already support multiple tags per response.

**5. Contribute to TalkingHead**
Package `m2m-animations.glb` + `body-animations.mjs` as a contribution back to met4citizen/TalkingHead. Pre-retargeted to RPM skeleton (all RPM variants share bone names/rest pose). CC0 license.

### Key files

| File | What |
|------|------|
| `speech-demo/.../animations/m2m-animations.glb` | 52 pre-retargeted M2M clips (6.4 MB) |
| `speech-demo/.../animations/body-animations.mjs` | Taxonomy, pools, picker, crossfade, gesture tag parsing |
| `speech-demo/.../index.html` | Integration: imports module, wires to TalkingHead + WebSocket |
| `/tmp/mesh2motion-app/src/retarget/batch-retarget-speech-demo.test.ts` | Batch retarget script (vitest) |
| `/tmp/mesh2motion-app` | M2M app with retarget fix, branch `fix/bind-correction-retarget` |

### Commits this session

| SHA | Message |
|-----|---------|
| `12fb3d3d` | wip(#457): add 52 M2M retargeted animations for RPM avatar |
| `1b1506fa` | wip(#457): wire body animation system with idle cycle and gesture tags |
| `39a263ca` | wip(#457): wire TalkingHead mood to conversation sentiment |
| `5b882397` | wip(#457): add speaking pool and idle/speaking state to body animations |
| `35ed21bf` | wip(#457): restructure body animations to category→subcategory→clip taxonomy |

### What NOT to do

- Do NOT use TalkingHead's `head.mixer` for body animations — it recreates the mixer per `playAnimation()` call
- Do NOT animate Head/Neck/Eye bones from body clips — TalkingHead writes them every frame
- Do NOT ship M2M-space clips to the browser — always pre-retarget to RPM skeleton
- The "bow" animation in M2M is archery (combat/ranged), not a greeting bow — mapped to head-nod as fallback

# HANDOFF — casehub-agency (slot 223)

## Active Issue — #457 Avatar demo animation integration

### Current state — Retarget done, ready to integrate into avatar demo

**Retarget (M2M → RPM) is complete.** `PoseAlignedRetargeter` + `LimbProportionCorrector` + body clearance detection. PR Mesh2Motion/mesh2motion-app#160 ready (181 tests passing). Fork: `mdproctor/mesh2motion-app` branch `fix/bind-correction-retarget`.

### What to do next — issue #457

**1. Retarget selected M2M animations onto RPM avatar**
Use the M2M app (`/tmp/mesh2motion-app`, dev server port 5176) to retarget and export the animations listed in #457. Bundle as GLB in `speech-demo/src/main/resources/META-INF/resources/animations/`.

**2. Wire idle cycle in speech-demo**
- Weighted random picker from idle animation pool (idle-a, idle-subtle, idle-foldarms, meditate, guitar-playing, drumming, confused, etc.)
- Crossfade between poses every 5-15 seconds via THREE.js AnimationMixer
- Trigger when LLM is not sending — avatar looks alive during downtime
- Include occasional "personality" animations (moonwalk, ninja sequence, dance) at low probability

**3. Wire LLM gesture tags**
- LLM emits `[gesture:dab]`, `[gesture:bow]`, `[gesture:confused]` etc. inline with speech text
- Parse tags before sending to TTS, trigger corresponding animation
- Animation crossfades from current pose, returns to idle when done

**4. Maximise TalkingHead facial expressions**
- TalkingHead already supports moods (neutral/happy/angry/sad/fear/disgust/love/sleep) via `setMood()`
- Already does lip sync via visemes and automatic blinks
- Wire mood changes to conversation context (LLM response sentiment)
- Author combo gestures: wink+smile+head-tilt as coordinated morph target + bone tracks (~3-4 keyframes, ~0.5s clips)
- Layer facial expressions on top of body animations (blendshapes don't conflict with bone tracks)

**5. Combine body with head movements**
- TalkingHead drives head/eye gaze (`lookAt()`, `lookAtCamera()`)
- Body animations drive torso/arms/legs
- Ensure head bone tracks from body animations don't override TalkingHead's gaze
- May need to mask head/neck bones from body animation clips and let TalkingHead own them

### Key files
| File | What |
|------|------|
| `speech-demo/.../animations/retarget.mjs` | Existing retarget for cc0 animations (Mixamo-space) |
| `speech-demo/.../animations/cc0-animations.glb` | Current 24 CC0 clips |
| `/tmp/mesh2motion-app` | M2M app with retarget fix, dev server port 5176 |
| `casehub-mosaic/node_modules/talkinghead/modules/talkinghead.mjs` | TalkingHead — moods, gestures, lip sync |
| #450 animation system | Tagged classifier + picker (done) |

### What was tried this session

**Root cause identified: RPM avatar is A-pose (arms 54° below horizontal), M2M is T-pose.**

The R post-multiply = 103°, decomposing into 54° swing (genuine arm direction difference) + 89° twist (bone axis convention). Three approaches tested visually in Playwright:

| Approach | Rest pose | Animation motion | Visual result |
|----------|-----------|-----------------|---------------|
| **Bind correction (L×Q×R)** | Correct (A-pose) | 54° constant world-direction offset | Arms too wide — don't reach center for clap |
| **L-only (drop R)** | Wrong (T-pose bones, 103° from bind) | 0° world-direction error | Severe mesh distortion — worse than original |
| **Swing-twist Retargeter** | Correct (respects neutral pose) | Matches source swing/twist vectors | Arms still too wide — similar to bind correction |

**Swing-twist producing similar results to bind correction is unexpected.** Possible causes:
1. The swing/twist axis definitions in `RigItem` default to Z-swing/Y-twist — may not match M2M's actual bone axes
2. The shoulder was missing from the arm chain (fixed: added `clavicle_l`/`clavicle_r` to `armL`/`armR` in `HumanChainConfig`) — adding it didn't fix the width issue
3. The `applyChain()` method uses `getWorldQuaternion()` on the source skeleton — this might not account for the animation mixer's bone-local application correctly

### What was fixed previously

**Coordinate space mismatch** (prior session): `world_quat()` now walks ALL bone ancestors including M2M root bone's Z-up→Y-up rotation. **Canonical REST disproved**: M2M animation keyframes are in M2M skeleton rest space, not Mixamo canonical space.

### What to investigate next

1. **Check RigItem swing/twist axis definitions** — the defaults (swing=Z, twist=Y) may be wrong for M2M's Blender-convention bones. Inspect `Rig.fromConfig()` → `fromJoint()` to see what axes are actually computed for arm bones.
2. **Verify the source animation mixer is driving bones correctly** — add debug logging to `Retargeter.applyChain()` to dump source world quaternions during clapping and compare with expected arm positions.
3. **Compare with retarget.mjs** — retarget.mjs produces correct arm retarget for cc0-animations.glb (Mixamo-space) on the same RPM avatar. The key difference is that cc0 animations match the RPM A-pose convention; M2M animations are T-pose.
4. **Consider T-pose normalization** — convert the RPM avatar to T-pose before retargeting (rotate the arm bones to match M2M's T-pose), then the bind correction R values become small and the formula works correctly.

### What exists

| Item | Where | Status |
|------|-------|--------|
| Draft PR #160 | Mesh2Motion/mesh2motion-app#160 | Draft — body/legs work, arms need fixing |
| BindCorrectionRetargeter.ts | `/tmp/mesh2motion-app/src/retarget/human-retargeting/` | `world_quat` (all ancestors) + hemisphere correction. NOT currently wired — kept for reference |
| BindCorrectionRetargeter.test.ts | Same dir | 9 tests pass (including M2M→RPM integration) |
| arm-correction-diagnostic.test.ts | Same dir | Dumps L/R corrections and bone direction tracking for M2M→RPM vs canonical→RPM |
| swing-r-diagnostic.test.ts | Same dir | Compares full R, swing-only R, and L-only bone direction tracking |
| AnimationRetargetService.ts | `/tmp/mesh2motion-app/src/retarget/` | NOW uses HumanClipRetargeter (swing-twist Retargeter) |
| HumanChainConfig.ts | Same dir | Arm chains now include clavicle/shoulder bones |
| Fork | mdproctor/mesh2motion-app branch `fix/bind-correction-retarget` | All commits pushed |
| M2M clone | `/tmp/mesh2motion-app` | Branch `fix/bind-correction-retarget`, 146 tests passing |
| RPM avatar | `/tmp/mesh2motion-app/static/test-files/retarget testing/rpm-avatar.glb` | For Playwright testing |
| RPM avatar (Playwright copy) | `agency/.playwright-mcp/rpm-avatar.glb` | Playwright sandbox-safe copy |
| Debug hook | `StepLoadSourceSkeleton.ts` line 170 | `window.__m2m_scene` and `__m2m_source_armature` exposed for console debugging |

### Key insights (cumulative)

1. **Animation keyframes are in M2M rest space** — source rest MUST come from M2M skeleton, not canonical constants
2. **Root bone MUST be included** in source world quaternion — converts Z-up to Y-up
3. **L×Q×R is algebraically correct** — preserves world-frame deltas (proven). But world-frame delta preservation ≠ visually correct when rest poses differ by 54° (T-pose vs A-pose)
4. **R decomposes into 54° swing + 89° twist** — the twist is the bone axis convention difference between Blender (M2M) and Mixamo (RPM)
5. **Dropping R distorts mesh** — the RPM mesh is skinned in A-pose; forcing T-pose bones (103° off bind) causes severe mesh distortion
6. **Swing-only R is WORSE** than full R for inward motions (42-96° error vs 54° constant)
7. **retarget.mjs works** because its cc0 animations match the RPM A-pose convention — same skeleton family, small R values
8. **M2M source armature is visible** in the scene — hide via `StepLoadSourceSkeleton` line 155

### What NOT to do

- Do NOT use `CANONICAL_REST` from retarget.mjs as source rest — M2M animations are NOT in canonical space
- Do NOT exclude unmapped bones from source world quaternion — creates Z-up/Y-up mismatch
- Do NOT drop R for arm bones — causes severe mesh distortion (103° from bind pose)
- Do NOT use swing-only R — produces worse errors than full R for inward motions

### Upstream context

- Mesh2Motion/mesh2motion-app#159 — original bug report (arms twisted behind back)
- Mesh2Motion/mesh2motion-app#160 — draft PR
- M2M dev server: `cd /tmp/mesh2motion-app && npx vite --port 5174`
- Playwright testing: upload rpm-avatar.glb → Auto-Map → Continue → filter animation → Play

## Previous work (complete)

- #450 Animation system — protocol, classifier, picker, layering. Closed.
- `retarget.mjs` — runtime retarget library with hardcoded canonical rest (works for cc0-animations.glb, NOT for M2M library animations)

## Key files

| File | What |
|------|------|
| `speech-demo/.../animations/retarget.mjs` | Working retarget for cc0 animations (different keyframe space than M2M library) |
| `speech-demo/.../animations/cc0-animations.glb` | 24 CC0 clips — in Mixamo space, works with retarget.mjs |
| `speech-demo/.../cc0-retarget-test.html` | Test page using retarget.mjs |
| `speech-demo/.../avatar.glb` | RPM avatar for testing |

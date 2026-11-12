# HANDOFF — casehub-agency (slot 223)

## Active Issue — #455 CC0 motion base

### Current state

24 CC0 animations from Mesh2Motion exist in `cc0-animations-v2.glb` with correct Mixamo bone names. Keyframe rotations are in Mesh2Motion's bone axis space. The Blender retarget renamed bones and set rest poses but only partially converted keyframes: **Hips keyframes were converted to avatar space, child bone keyframes were NOT.**

### What to do next — fix specific bone twists

The full L/R retarget using `retargetClipToObject` with M2M world rests computed from Idle_Subtle frame 0 (the "v4 approach" from the 2026-11-12 session) produced **mostly correct results**: Greeting showed a natural wave, Idle Talking had expressive gestures. The remaining issue was **specific bone twist errors** — notably one hand twisted 180° in Head Nod.

**Strategy: go back to v4 and fix just the twisted bones.**

1. Start from the v4 approach: extract M2M rest from Idle_Subtle_RT frame 0, compute world rests through the bone hierarchy, apply full L/R bind corrections (M2M→CANONICAL), then `retargetClipToObject` (CANONICAL→avatar).

2. Identify which specific bones have twist errors by visual inspection (likely LeftHand, RightHand, or specific forearm bones).

3. For those bones only, override the Idle_Subtle-derived rest with the Blender-extracted M2M rest values (from `/tmp/mesh2motion-assets/rigs/human/animation-human-rest-pose.blend`). The Blender rest is reliable for ALL bones; the issue was only with the world rest computation and root chain, not the local rest values.

4. The fix is surgical — don't rework the entire retarget pipeline. Just correct the source rest for the ~3-5 bones that have visible twist issues.

### Key findings from 2026-11-12 session

1. **Hips keyframes already converted**: Bow frame 0 Hips = `[-0.030, 0, 0, 0.9996]` (= avatar rest). Blender retarget converted Hips but not children.

2. **Frame 0 varies per clip**: LeftHand frame 0 quaternion is different in every animation — frame 0 is NOT a reliable "rest pose" for all bones. Use Blender-extracted rest as fallback for unreliable bones.

3. **M2M rig uses drivers, not constraints**: `bpy.ops.nla.bake(visual_keying=True)` doesn't capture deformation bone animation because M2M drives them via Python drivers, not bone constraints. Raw M2M blend files can't be cleanly re-exported.

4. **M2M source rest rotations available**: Extracted from `animation-human-rest-pose.blend` via Blender glTF export. Values are in `/tmp/m2m_canonical_rest.json`. The M2M rest differs dramatically from the avatar rest (e.g., M2M LeftArm: `[0.192, 0.661, -0.268, 0.675]` vs avatar LeftArm: `[0.385, 0.069, 0.038, 0.920]`).

5. **M2M root bone**: The M2M skeleton has a `root` bone with `-90° X rotation` (Z-to-Y conversion) as parent of `pelvis` (Hips). This was folded into the Hips keyframes by the Blender retarget.

6. **Test page**: `cc0-retarget-test.html` serves as the iteration testbed. HTTP server: `python3 -m http.server 8099 --directory speech-demo/src/main/resources/META-INF/resources`

### What NOT to do

- ❌ Generate UniMate animations — quality too poor, already tested and rejected
- ❌ Delete cc0-animations.glb or cc0-animations.fbx
- ❌ Download new animations from Mesh2Motion web tool — same bone roll bug
- ❌ Try full L/R retarget without `retargetClipToObject` — the two-pass approach (M2M→CANONICAL + CANONICAL→avatar) is the correct architecture
- ❌ Use the Blender rest-pose file's Hips value as source rest — Hips keyframes are already converted, skip Hips in the pre-correction
- ❌ Try to bake from raw M2M blend files — drivers aren't captured by visual keying

### Pending external

Mesh2Motion/mesh2motion-app#159 — replied to Scott with avatar bone rest rotations, awaiting response. He may fix the tool or explain the rig convention.

### Alternative CC0 sources (investigated, not tried)

- **Quaternius** — 250+ CC0 animations, Blender-style bone names (needs mapping), combat/locomotion focused, few conversational gestures
- **KayKit** — 161 CC0 animations, own rig, low-poly focused
- **Mixamo** — 2000+ animations with correct bone names, works perfectly but NOT redistributable (can't include in git)

## Previous work (complete)

- #450 Animation system — protocol, classifier, picker, layering. Closed.
- UniMate pipeline — conda env at `/tmp/unimate`, converter `unimate2glb.py`. Works but motion quality insufficient. Infrastructure only.
- `retarget.mjs` — runtime retarget library, handles bone name canonicalization and bind corrections. Working correctly.

## Key files

| File | What |
|------|------|
| `speech-demo/.../animations/cc0-animations.glb` | 24 CC0 clips, Mixamo names, axis error in keyframes |
| `speech-demo/.../animations/cc0-animations-v2.glb` | Same animations, different export (THREE.GLTFExporter) |
| `speech-demo/.../animations/cc0-animations.fbx` | FBX version of same |
| `speech-demo/.../animations/retarget.mjs` | Runtime retarget library |
| `speech-demo/.../cc0-retarget-test.html` | Test page for iterating on retarget corrections |
| `speech-demo/.../cc0-preview.html` | TalkingHead-based preview (broken playback binding) |
| `/tmp/mesh2motion-assets/rigs/human/` | Raw M2M blend files with original skeleton |
| `/tmp/m2m_canonical_rest.json` | M2M bone rest rotations mapped to canonical names |

## Blog

- `blog/2026-11-11-mdp01-the-body-language-problem.md` — animation work diary

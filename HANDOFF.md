# HANDOFF — casehub-agency (slot 223)

## Active Issue — #455 CC0 motion base

### Current state

24 CC0 animations from Mesh2Motion exist in `cc0-animations.glb` with correct Mixamo bone names. They are contorted on the avatar because the keyframe rotations are in Mesh2Motion's bone axis space, not the avatar's. The Blender retarget that produced the GLB renamed bones and set rest poses but did NOT re-express keyframes in the new axes.

### What to do next — T-pose delta retarget

**Read issue #455 comments before doing anything.** The issue body has a "What NOT to Do" section.

The keyframe values at a neutral-pose frame (e.g., frame 0 of `Idle_Subtle_RT`) encode the original Mesh2Motion rest rotations. Use them as the source rest:

```
source_rest = keyframe_values_at_neutral_frame   // Mesh2Motion's axes
delta = inverse(source_rest) × keyframe_at_frame_N
corrected = target_rest × delta                   // avatar's axes
```

Apply per-bone, per-frame. Test with Greeting and Idle_Talking first — arms should bend forward.

### What NOT to do

- ❌ Generate UniMate animations — quality too poor, already tested and rejected
- ❌ Delete cc0-animations.glb or cc0-animations.fbx
- ❌ Download new animations from Mesh2Motion web tool — same bone roll bug
- ❌ Try to fix retarget.mjs bind corrections — they're correct, the input data is wrong

### Pending external

Mesh2Motion/mesh2motion-app#159 — replied to Scott with avatar bone rest rotations, awaiting response. He may fix the tool or explain the rig convention.

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
| `speech-demo/.../animations/unimate2glb.py` | UniMate .npy → GLB converter (infrastructure) |
| `speech-demo/.../index.html` | Speech demo with animation system |

## Blog

- `blog/2026-11-11-mdp01-the-body-language-problem.md` — animation work diary

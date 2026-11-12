# HANDOFF — casehub-agency (slot 223)

## Active Issue — #455 CC0 motion base

### Current state — retarget approaches exhausted

24 CC0 animations from Mesh2Motion exist in `cc0-animations-v2.glb`. Every runtime retarget approach produces systematically wrong arm angles and hand twists. The fundamental problem: the Blender retarget that produced this GLB renamed bones and partially converted rest poses but did NOT properly convert keyframe axis space. We don't have the M2M rig's Python driver pipeline to re-export correctly.

### What was tried (2026-11-12 sessions)

**Five retarget approaches, none acceptable:**

| Approach | Result |
|----------|--------|
| v4: Idle_Subtle frame 0 as M2M rest, full L/R corrections | Arms 45° too high (Greeting reaches ceiling), left hand 180° twist in Head Nod |
| Hybrid: frame 0 local rest + GLB world rest | Arm ANGLE correct but hand orientation 90° off (palm down instead of forward) |
| GLB node.quaternion as M2M rest | Near-zero corrections (GLB rest ≈ avatar rest), raw uncorrected keyframes |
| Blender-extracted M2M rest for all bones | Completely broken — different coordinate space from GLB keyframes |
| Two-pass premultiply (cc0-preview.html approach) | Arms twist behind back — local-only correction can't handle world rest differences |

**Root cause**: the v4 L/R correction formula (`L = Rt × Wt⁻¹ × Ws × Rs⁻¹`, `R = Ws⁻¹ × Wt`) is mathematically correct but the source rest (Rs, Ws) is wrong. Idle_Subtle frame 0 has 74° of asymmetric idle animation baked in. The Blender-extracted rest is in a different coordinate frame. The GLB node.rotation is too close to avatar rest (produces identity corrections).

**Blender re-export attempted**: loaded M2M rig + animation .blend files, stripped control rig bones, exported clean GLB. Result was upside down — the M2M deformation bones are driven by Python drivers in the control rig, and without those drivers the animation doesn't transfer to the deformation skeleton. The cc0-animations-v2.glb IS the output of those drivers — it can't be recreated from the raw .blend files.

### What to do next — author animations directly

**Recommended path: hand-author keyframe animations for the canonical skeleton.**

TalkingHead proved this works — it has ~10 poses and ~8 gestures, all hand-authored as Euler rotation dictionaries for the Mixamo skeleton. No retargeting needed because the animations are authored for the target skeleton.

For CaseHub, we need 10-15 conversational animations (greeting, head nod, idle variations, confused, happy, angry, fold arms, pointing, etc.). Each is 3-6 keyframes over 1-4 seconds.

Reference renders exist at `/tmp/m2m-refs/` (14 animations rendered from M2M Blender files at mid-frame) and are served at `http://localhost:8099/m2m-refs.html`. Use these as visual targets for the hand-authored poses.

**Alternative paths:**
- **Motion2MixamoRig** (github.com/MaxLiu22/Motion2MixamoRig) — video→Mixamo retarget via neural pose estimation. Could feed M2M animation renders as video input. Needs ~5GB weights, untested.
- **Ask Scott** (mesh2motion-app#159) — he has the rig pipeline and could provide correctly converted exports
- **Mixamo clips** — work perfectly but not redistributable

### What NOT to do

- ❌ Try more runtime retarget approaches on cc0-animations-v2.glb — the source data is fundamentally incomplete (partial axis conversion)
- ❌ Use Blender-extracted M2M rest (`/tmp/m2m_canonical_rest.json`) as source rest — different coordinate space from GLB keyframes
- ❌ Try to bake from raw M2M blend files — drivers aren't captured by visual keying
- ❌ Generate UniMate animations — quality too poor
- ❌ Delete cc0-animations.glb or cc0-animations.fbx

### Key findings

1. **TalkingHead has no retarget** — its animations are hand-authored Euler poses for the Mixamo skeleton, interpolated procedurally. No M2M source, no clip files.
2. **M2M rig uses Python drivers** — deformation bones are computed from control bones via drivers. `nla.bake(visual_keying=True)` doesn't capture them. Can't re-export without the driver pipeline.
3. **cc0-animations-v2.glb is the best M2M output we have** — produced by M2M's own conversion (which ran the drivers). Can't be recreated.
4. **Blender rest vs GLB rest vs frame 0**: three different coordinate spaces. None can be substituted for the others in the retarget formula.
5. **The asymmetric idle animation** causes the left hand 180° twist: LeftArm and RightArm at Idle_Subtle frame 0 differ by 31° when mirrored. Mirroring LeftArm from RightArm fixes the twist but doesn't fix the systematic arm angle offset.
6. **M2M reference renders**: 14 animations rendered from Blender source at `/tmp/m2m-refs/`. Gallery at `m2m-refs.html`. Bow, Salute, Cheer, Help, Insult are NOT in the CC0 pack (web-tool generated, no .blend source).

### Pending external

Mesh2Motion/mesh2motion-app#159 — awaiting response from Scott.

## Previous work (complete)

- #450 Animation system — protocol, classifier, picker, layering. Closed.
- UniMate pipeline — conda env at `/tmp/unimate`, converter `unimate2glb.py`. Works but motion quality insufficient.
- `retarget.mjs` — runtime retarget library, handles bone name canonicalization and bind corrections. Working correctly for properly-sourced animations.

## Key files

| File | What |
|------|------|
| `speech-demo/.../animations/cc0-animations.glb` | 24 CC0 clips, Mixamo names, axis error in keyframes |
| `speech-demo/.../animations/cc0-animations-v2.glb` | Same animations, different export (THREE.GLTFExporter) |
| `speech-demo/.../animations/retarget.mjs` | Runtime retarget library |
| `speech-demo/.../cc0-retarget-test.html` | Test page with v4/hybrid/GLB toggle |
| `speech-demo/.../m2m-refs.html` | M2M reference render gallery |
| `/tmp/mesh2motion-assets/rigs/human/` | Raw M2M blend files with original skeleton |
| `/tmp/m2m_canonical_rest.json` | M2M bone rest rotations (Blender coordinate space — NOT compatible with GLB keyframes) |

## Blog

- `blog/2026-11-11-mdp01-the-body-language-problem.md` — animation work diary

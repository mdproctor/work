# HANDOFF — casehub-agency (slot 223)

## Active Issue — #455 CC0 motion base

### Current state — pivot from retarget to authoring

M2M CC0 animations can't be runtime-retargeted — the GLB has partially-converted keyframes and we don't have the M2M Python driver pipeline to re-export. Five retarget approaches tried, none acceptable. TalkingHead doesn't retarget either — it hand-authors poses for the canonical skeleton.

### What to do next

**Hand-author keyframe animations directly for the canonical Mixamo skeleton.**

1. Define 10-15 conversational animation clips as multi-keyframe THREE.AnimationClip data
2. Author bone rotations using the M2M reference renders as visual targets (`/tmp/m2m-refs/`, served at `m2m-refs.html`)
3. No retargeting needed — animations are authored for the target skeleton
4. `retargetClipToObject` handles canonical→avatar mapping (this path is well-tested and works)

**For complex motions, cherry-pick CMU MoCap BVH:**
- Import BVH into Blender → retarget to Mixamo skeleton → export GLB
- CMU has: wave (106_16), shrug (111_25), pointing (139_25), happy/laughing (79_69), surprised (79_73), thinking (74_12), bow (111_02)
- License: permissive (free for commercial use, no resale of raw data — retargeted clips in a product repo is fine)

**Alternative:** Motion2MixamoRig (github.com/MaxLiu22/Motion2MixamoRig) — video→Mixamo via neural pose estimation. Could feed M2M reference renders as video. Untested, needs ~5GB weights.

### CC0 animation library survey (2026-11-12)

| Source | License | Count | Useful for us? |
|--------|---------|-------|----------------|
| ~~Mesh2Motion~~ | CC0 | 220+ | **Unusable** — retarget proven broken, 5 approaches failed |
| **CMU MoCap** | Permissive | 2,605 | Cherry-pick specific clips, heavy retarget |
| **KayKit** | CC0 | 161 | Wave, cheer, sit. Missing emotions/gestures |
| **Quaternius** | CC0 | ~45 | M2M is a superset |
| **KinemIQ** | CC0+CC-BY | 460 | Mostly locomotion |
| **Motifect** | Unclear | 30 | Perfect content — contact author re: redistribution |
| Rokoko | No redistribution | 263 | Cannot include in git |
| Mixamo | No redistribution | 2000+ | Works perfectly but can't distribute |

### Gap analysis (M2M coverage)

**M2M has (14):** greeting, head-nod, yes, angry, confused, clapping, victory, victory-fist-pump, bow, reject, idle-subtle, idle-talking, idle-listening, idle-foldarms

**Missing (8):** shrug, pointing, happy, surprised, thinking, hands-on-hips, thumbs-up, salute — author these or cherry-pick from CMU MoCap

### Why retarget failed

The cc0-animations-v2.glb was produced by M2M's Blender retarget which:
1. Renamed M2M bones to Mixamo names
2. Set rest poses to an intermediate state (not M2M rest, not avatar rest)
3. Converted Hips keyframes to avatar space
4. Did NOT convert child bone keyframes — they're still in M2M axis space
5. The M2M deformation bones are computed by Python drivers — can't re-export without the driver pipeline

Five runtime correction approaches all fail because:
- Frame 0 rest: 74° of asymmetric idle animation baked in → arms 45° too high
- GLB node.rotation: too close to avatar rest → near-zero corrections
- Blender-extracted M2M rest: different coordinate space from GLB keyframes
- Hybrid (frame0 local + GLB world): arm angle OK but hand orientation 90° off
- Two-pass premultiply: can't handle world rest differences → arms behind back

### What NOT to do

- ❌ More runtime retarget approaches on cc0-animations-v2.glb
- ❌ Blender re-export from raw M2M .blend files (drivers not captured)
- ❌ Use Blender-extracted rest as source rest (wrong coordinate space)
- ❌ Rokoko free clips (no redistribution rights)
- ❌ Mixamo clips in git (no redistribution rights)

### Pending external

- Mesh2Motion/mesh2motion-app#159 — awaiting response from Scott re: bone axis convention
- Could contact Motifect re: redistribution rights for their 30 emote/social clips

## Previous work (complete)

- #450 Animation system — protocol, classifier, picker, layering. Closed.
- `retarget.mjs` — runtime retarget library. Works correctly for properly-sourced animations.

## Key files

| File | What |
|------|------|
| `speech-demo/.../animations/cc0-animations-v2.glb` | 24 CC0 clips — unusable due to axis conversion bug |
| `speech-demo/.../animations/retarget.mjs` | Runtime retarget library (working) |
| `speech-demo/.../cc0-retarget-test.html` | Test page with v4/hybrid/GLB toggle |
| `speech-demo/.../m2m-refs.html` | M2M reference render gallery (14 animations) |
| `/tmp/mesh2motion-assets/rigs/human/` | Raw M2M blend files |
| `/tmp/m2m-refs/` | Rendered reference frames from M2M source |

## Blog

- `blog/2026-11-11-mdp01-the-body-language-problem.md` — animation work diary

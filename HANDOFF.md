# HANDOFF — casehub-agency (slot 223)

## Active Issue — #455 CC0 motion base

### Current state — pivot from retarget to authoring

M2M CC0 animations can't be runtime-retargeted — the GLB has partially-converted keyframes and we don't have the M2M Python driver pipeline to re-export. Five retarget approaches tried, none acceptable. TalkingHead doesn't retarget either — it hand-authors poses for the canonical skeleton.

### What to do next — fix M2M's export tool

**Primary path: find and fix the retarget bug in mesh2motion-app.**

The M2M export tool already produces 90% correct output — bones renamed, Hips converted, mesh skinned. The bug is specific: it converts Hips keyframes to avatar space but skips child bones. This is likely a one-line fix in a retarget loop.

**Dedicated session plan:**
1. Clone `mesh2motion-app`, find the export/retarget code
2. Identify the loop that converts Hips but skips children
3. Fix it, test against M2M reference renders at `/tmp/m2m-refs/`
4. Re-export all 220+ CC0 animations with the fix
5. PR the fix back to M2M — benefits the whole community

**Fallback if codebase is too complex:** write a standalone ~50-line Blender Python script that reads deformation bone transforms per frame (`scene.frame_set(N)` evaluates all drivers automatically — no `nla.bake()` needed) and exports clean GLB with Mixamo bone names.

**Additional sources for gap-filling:**
- CMU MoCap BVH for complex motions: shrug (111_25), pointing (139_25), happy (79_69), surprised (79_73), thinking (74_12)
- Hand-author simple pose-holds (thumbs-up, hands-on-hips, salute) directly for the canonical skeleton
- Motion2MixamoRig (video→Mixamo) as a last resort

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

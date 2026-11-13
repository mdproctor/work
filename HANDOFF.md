# HANDOFF — casehub-agency (slot 223)

## Active Issue — #455 CC0 motion base

### Current state — M2M retarget: body/legs fixed, arms still wrong

The bind correction retarget now produces correct body posture (upright standing), correct leg positions (both feet planted), and correct hip scaling. **The fundamental coordinate space issue is solved.** Arms are in the right general direction (forward, not behind) but arm orientations are still wrong — arms stretch too wide and pass through the body during clapping-type animations.

### What was fixed this session

**Root cause found and fixed: coordinate space mismatch in world quaternion computation.**

The `mapped_world_quat()` function excluded unmapped bones (M2M's `root` bone with -90° X rotation) from the source world quaternion. This left source world quaternions in Z-up space while target was in Y-up, creating near-180° corrections for leg bones that caused them to flip unpredictably.

**Fix:** changed to `world_quat()` which walks ALL bone ancestors including the unmapped root. The root bone's Z-up→Y-up rotation converts source world quaternions to Y-up (same as target). The L correction absorbs the coordinate transform; R corrections become small and stable.

Also added target-rest hemisphere correction: after applying L × Q × R, the output quaternion's dot product with the target rest quaternion is checked. If negative (wrong hemisphere), the quaternion is negated to prevent slerp taking the long path.

### What was disproved this session

**The canonical REST approach is WRONG for M2M's animation library.** The previous handover said to use `CANONICAL_REST` from retarget.mjs as source rest. This was tested and failed completely (avatar floating, rotated, limbs contorted).

**Proof:** M2M animation keyframe values are in M2M skeleton rest space, NOT Mixamo canonical space:
```
pelvis keyframe frame0: [0.783, 0, 0, 0.622]  ← matches M2M rest [0.790, 0, 0, 0.613]
Canonical Hips rest:    [0.001, 0, 0, 1.000]  ← completely different
```

retarget.mjs works because its source animations (`cc0-animations.glb` in speech-demo) ARE in Mixamo space. M2M's animation library files are authored on the M2M skeleton and use M2M-space keyframe values.

### What to do next — fix arm retarget quality

Body and legs retarget correctly. Arms are in the right general direction but rotations are wrong — arms stretch too wide and penetrate the body during animations with arm movement (clapping, victory).

Investigation approach:
1. Compare arm correction values (L, R for LeftArm/LeftForeArm/LeftHand) with what retarget.mjs produces for the same RPM target
2. The issue might be in how the M2M arm bones' rest rotations map to RPM — the shoulder/arm chain has different orientations between rigs
3. Check if the M2M source skeleton's arm bones have correct parent chains when extracted via `create_skeleton_from_group_object`
4. Consider whether `AnimationUtility.clean_track_data()` (called before retarget) modifies arm tracks in ways that affect retargeting

The M2M source armature is also still visible in the scene during animation playback — it should be hidden. This is in `StepLoadSourceSkeleton.add_skeleton_and_helper_to_scene()` line 155.

### What exists

| Item | Where | Status |
|------|-------|--------|
| Draft PR #160 | Mesh2Motion/mesh2motion-app#160 | Draft — body/legs work, arms need fixing |
| BindCorrectionRetargeter.ts | `/tmp/mesh2motion-app/src/retarget/human-retargeting/` | `world_quat` (all ancestors) + hemisphere correction |
| BindCorrectionRetargeter.test.ts | Same dir | 9 tests pass (including M2M→RPM integration) |
| AnimationRetargetService.ts | `/tmp/mesh2motion-app/src/retarget/` | Uses BindCorrectionRetargeter with root bone position correction |
| Fork | mdproctor/mesh2motion-app branch `fix/bind-correction-retarget` | All commits pushed |
| M2M clone | `/tmp/mesh2motion-app` | Branch `fix/bind-correction-retarget`, 146 tests passing |
| RPM avatar | `/tmp/mesh2motion-app/static/test-files/retarget testing/rpm-avatar.glb` | For Playwright testing |
| RPM avatar (Playwright copy) | `agency/.playwright-mcp/rpm-avatar.glb` | Playwright sandbox-safe copy |
| Debug hook | `StepLoadSourceSkeleton.ts` line 170 | `window.__m2m_scene` and `__m2m_source_armature` exposed for console debugging |

### Key insights for the next session

1. **Animation keyframes are in M2M rest space** — source rest MUST come from M2M skeleton, not canonical constants
2. **Root bone MUST be included** in source world quaternion — it converts Z-up to Y-up, matching the target's coordinate space
3. **The formula is correct** — L × Q × R round-trips rest-pose keyframes perfectly. The remaining arm issue is about animated (non-rest) keyframes
4. **M2M source armature is visible** in the scene alongside the target avatar — creates confusing "two figures" visual. Hide it during animation playback
5. **retarget.mjs bindCorrections (line 1034)** is the reference — compare its output for the RPM avatar with our BindCorrectionRetargeter output to find the arm divergence

### What NOT to do

- Do NOT use `CANONICAL_REST` from retarget.mjs as source rest — M2M animations are NOT in canonical space
- Do NOT exclude unmapped bones from source world quaternion — creates Z-up/Y-up mismatch
- Do NOT apply root bone quaternion to position tracks when using canonical rest — but DO apply it when using dynamic M2M rest (positions are in root-local Z-up space)

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

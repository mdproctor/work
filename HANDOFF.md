# HANDOFF — casehub-agency (slot 223)

## Active Issue — #455 CC0 motion base

### Current state — M2M retarget: body/legs fixed, arm R-correction dropped for T→A-pose

The bind correction retarget now produces correct body posture, legs, and hip scaling. **Arms have been fixed by dropping the R post-multiply correction for bones where R > 60°.** This preserves the source's world-space bone direction exactly (0° error) instead of the previous constant 54° offset from the A-pose starting position. Trade-off: arm rest-pose output is T-pose (matches M2M source) instead of A-pose (RPM native), but during animation every frame matches the source's world direction. Needs visual verification in the browser.

### What was fixed this session (current)

**Root cause: RPM avatar is A-pose, M2M is T-pose — 54° arm direction difference.**

The RPM avatar's LeftArm bone direction in world space is (0.577, -0.816, -0.042) — pointing left AND down (A-pose). M2M's upperarm_l direction is (1.000, -0.005, -0.026) — pointing pure left (T-pose). This 54° bone direction difference made R = Ws⁻¹ × Wt = 103°, decomposing into 54° swing + 89° twist.

The L×Q×R formula is algebraically correct (world-frame delta preservation proven). But the 54° A-pose offset means "swing inward for clap" on T-pose becomes "swing inward from a different starting position" on A-pose — the arm doesn't reach center, appearing "too wide."

**Fix:** Drop R for bones where R > 60° (`BindCorrectionRetargeter.ts`). L-only preserves the source's world-space bone direction exactly (0° error at every frame). Spine/leg bones (R < 15°) keep full L×Q×R.

Diagnostic tests in `arm-correction-diagnostic.test.ts` and `swing-r-diagnostic.test.ts` prove:
- Full R: constant 54.4° bone direction offset (arms stay wide)
- Swing-only R: 42-96° (WORSE for inward motions)
- L-only (this fix): 0.0° direction match at every frame

### What was fixed previously

**Coordinate space mismatch** (prior session): `world_quat()` now walks ALL bone ancestors including M2M root bone's Z-up→Y-up rotation. **Canonical REST disproved**: M2M animation keyframes are in M2M skeleton rest space, not Mixamo canonical space.

### What to do next — visual verification

1. **Test the arm fix visually in the browser** — load RPM avatar, retarget a clapping or victory animation, verify arms come together correctly. The arms will start in T-pose (not A-pose) which may slightly distort the shoulder mesh.
2. **If T-pose rest looks bad**: consider a hybrid approach — apply the R correction ONLY to the first/last keyframes to blend the rest pose, while keeping L-only for animated frames. Or switch to the existing `Retargeter` swing-twist approach which handles T→A-pose natively.
3. **Hide M2M source armature** during playback — `StepLoadSourceSkeleton.add_skeleton_and_helper_to_scene()` line 155.
4. **Consider the full swing-twist Retargeter** as the long-term solution — it already exists in the codebase and correctly handles arbitrary rest-pose differences via per-bone swing/twist axis definitions.

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

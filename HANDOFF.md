# HANDOFF — casehub-agency (slot 223)

## Active Issue — #455 CC0 motion base

### Current state — M2M retarget fix: dynamic approach failed, hardcoded approach next

We cloned mesh2motion-app, found the retarget bug (swing-twist decomposition can't handle bone roll differences), wrote a bind correction fix, and PR'd it. Visual testing with Playwright showed the fix partially works (arms forward instead of behind back) but fails on hands and elbows. **Dynamic rest-pose computation from the skeleton doesn't converge** — M2M's skeleton structure (root bone, Z-up, 90°-rotated fingers) makes each fix break something else.

### What to do next — port the hardcoded canonical rest approach

**The retarget.mjs approach works because it uses hardcoded `CANONICAL_REST` values, not dynamically computed ones.** Port this into M2M's `BindCorrectionRetargeter`.

Specifically:
1. Extract `CANONICAL_REST` and `CANONICAL_REST_WORLD` from `retarget.mjs` (lines 714-821) — these are the M2M source skeleton's rest-pose quaternions in canonical (Mixamo) bone names
2. In `BindCorrectionRetargeter.compute_bind_corrections()`, use these hardcoded values as `Rs`/`Ws` instead of reading from the source skeleton's bones
3. Compute `Rt`/`Wt` from the uploaded target skeleton at runtime (this part works — retarget.mjs does exactly this via `canonicalRestMapFromObject` and `canonicalWorldRestMapFromObject`)
4. The L/R formula is the same: `L = Rt × Wt⁻¹ × Ws × Rs⁻¹`, `R = Ws⁻¹ × Wt`
5. Position track needs root bone's -90° X rotation applied (Z-up → Y-up conversion)
6. Hip scale = `targetHipY / sourceHipY` using canonical rest position data

### Why dynamic computation fails

M2M's skeleton has:
- A `root` bone above `pelvis` with quaternion `[-0.707, 0, 0, 0.707]` (-90° X, Z-up → Y-up)
- This bone is NOT in the bone mapping — no RPM equivalent exists
- Finger bones with 90° Y rotations (`index_01_l: [0, 0.707, 0, 0.707]`) vs RPM near-identity
- Walking the hierarchy through mapped-only ancestors fixes some issues but introduces others

Three fixes attempted, each broke something:
1. `getWorldQuaternion()` — includes root bone rotation, contaminates all corrections with 90° X
2. `mapped_world_quat()` (walk only mapped ancestors) — skips root correctly for torso but finger world quaternions still wrong
3. Hemisphere normalization — fixes some hand flips but breaks others mid-animation

### Why hardcoded values work

retarget.mjs's `CANONICAL_REST` values were captured from a specific known-good M2M→Mixamo configuration. They represent what M2M's bone rotations look like AFTER proper conversion to the canonical Mixamo coordinate space. By using frozen constants:
- No root bone to worry about (already factored out)
- No hierarchy walk (world quats pre-computed)  
- No hemisphere ambiguity (values are stable)
- Same formula, just better inputs

### Key insight for the next session

The bind correction formula `L = Rt × Wt⁻¹ × Ws × Rs⁻¹` is correct. The unit tests pass. The integration test with real skeleton data passes. The issue is exclusively in how `Rs` and `Ws` (source rest poses) are obtained. Replace dynamic computation with the hardcoded `CANONICAL_REST`/`CANONICAL_REST_WORLD` constants from retarget.mjs and the fix works.

### What exists

| Item | Where | Status |
|------|-------|--------|
| Draft PR #160 | Mesh2Motion/mesh2motion-app#160 | Draft — dynamic approach, doesn't converge |
| BindCorrectionRetargeter.ts | `/tmp/mesh2motion-app/src/retarget/human-retargeting/` | Has correct formula + tests, wrong rest-pose source |
| BindCorrectionRetargeter.test.ts | Same dir | 9 tests pass (including real M2M→RPM integration test) |
| AnimationRetargetService.ts | `/tmp/mesh2motion-app/src/retarget/` | Modified to use BindCorrectionRetargeter |
| Fork | mdproctor/mesh2motion-app branch `fix/bind-correction-retarget` | All commits pushed |
| M2M clone | `/tmp/mesh2motion-app` | Branch `fix/bind-correction-retarget`, npm installed, vitest passing |
| RPM avatar in M2M test files | `/tmp/mesh2motion-app/static/test-files/retarget testing/rpm-avatar.glb` | For Playwright testing |
| Canonical rest values | `retarget.mjs` lines 714-929 | `CANONICAL_REST`, `CANONICAL_REST_WORLD`, `CANONICAL_REST_POSITION`, `CANONICAL_PARENT` |
| Spec | `wsp-casehub-blocks/specs/issue-455-.../2026-11-12-m2m-retarget-bind-correction-design.md` | Needs updating for hardcoded approach |
| Plan | `wsp-casehub-blocks/plans/2026-11-12-m2m-retarget-bind-correction.md` | Needs rewriting for hardcoded approach |

### What NOT to do

- ❌ Compute source rest poses from the M2M skeleton at runtime — doesn't work
- ❌ Walk the bone hierarchy to derive world quaternions — root bone contaminates
- ❌ Use `getWorldQuaternion()` on detached skeletons — parent chain is incomplete
- ❌ Apply hemisphere normalization to corrected keyframe output — breaks mid-animation values

### Upstream context

- Mesh2Motion/mesh2motion-app#159 — original bug report (arms twisted behind back)
- Mesh2Motion/mesh2motion-app#160 — draft PR with comment explaining the dynamic approach failure
- M2M dev server: `cd /tmp/mesh2motion-app && npx vite --port 5174`
- Playwright testing: upload avatar.glb → Auto-Map → Continue → filter "clap" → Play

## Previous work (complete)

- #450 Animation system — protocol, classifier, picker, layering. Closed.
- `retarget.mjs` — runtime retarget library with hardcoded canonical rest. Works correctly.

## Key files

| File | What |
|------|------|
| `speech-demo/.../animations/retarget.mjs` | Working retarget with hardcoded CANONICAL_REST — the reference implementation |
| `speech-demo/.../animations/cc0-animations.glb` | 24 CC0 clips from M2M (need re-export with fixed retarget) |
| `speech-demo/.../cc0-retarget-test.html` | Test page using retarget.mjs (working comparison baseline) |
| `speech-demo/.../avatar.glb` | RPM avatar for testing |

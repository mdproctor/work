# HANDOFF — casehub-agency (slot 223)

## Active Issue — #458 Author 23 conversational body poses

### Current state — 4 poses working, pipeline proven

**Branch:** `issue-458-author-23-conversational-body-poses` (based on #455 tip)

**Working poses (approved by user):**
- `head-shake` — oscillating head turn, no body masking needed
- `hand-over-mouth` — right hand at mouth, palm facing face, fingers adducted
- `hands-over-mouth` — both hands, left slightly forward resting on right
- `facepalm` — static hand on forehead, both eyes visible

**Approved GLB saved at:** `/tmp/m2m-facepalm-v5.glb` (the current production bundle with all 4 working poses + 52 original clips)

### Immediate next task — Animated facepalm

The static facepalm is approved. It needs an animation: starts from `idle-talking` pose, hand sweeps up to the approved forehead position. Three failed attempts at this — the issue is extracting start/end poses from the approved GLB and interpolating between them without coordinate corruption.

**What went wrong:**
1. Re-importing the bundle GLB into Blender corrupts the action data — bone name paths don't match after round-tripping
2. Binary-level quaternion interpolation (slerp at glTF level) produced a "crumpled into a ball" result — likely wrong slerp across quaternion double-cover boundaries
3. The approved facepalm end pose must NOT be re-solved via IK — use the exact values from the approved bundle

**Recommended approach for next session:**
- Extract BOTH poses (idle-talking mid-frame, facepalm mid-frame) from the approved GLB at the binary level
- Handle quaternion double-cover: ensure dot product > 0 before slerp (negate if needed)
- Test the interpolation at frame 0 (should match idle-talking exactly) and frame 59 (should match facepalm exactly) before deploying
- The hold-transition-hold timeline: 0-15 hold idle-talking, 15-40 transition, 40-59 hold facepalm

### Proven pipeline — Blender IK on RPM avatar

The pipeline that works for pose authoring:

```
1. Import RPM avatar.glb into Blender
2. Set IK target at desired hand world position
3. IK constraint on hand bone, chain_count=3
4. NLA bake (visual_keying=True, clear_constraints=True)
5. Post-bake adjustments:
   - Palm rotation: Q((0,1,0), radians(60)) for right hand facing face
   - Finger adduction: Z-axis rotations (Index1=+15°, Ring1=-15°, Pinky1=-25°)
   - Shoulder rotation: Q((1,0,0), radians(-5)) to adjust elbow angle
6. Export as GLB — Blender handles coordinate conversion
7. Merge into bundle via Python GLB binary merge script
```

**Key scripts (all in /tmp/):**
- `author-single-pose-simple.py` — template for single-hand IK poses (hand-over-mouth)
- `author-hands-over-mouth.py` — two-hand variant
- `author-facepalm.py` — static facepalm (forehead IK)

**What does NOT work:**
- Authoring on the Mixamo/CC0 skeleton then retargeting — T-pose vs A-pose rest mismatch
- Extracting quaternions from Blender and plugging into a Python GLB builder — coordinate space mismatch
- Slerping between reference clip quaternions at the glTF binary level — unpredictable visual results
- Re-importing the approved bundle GLB into Blender to extract action data — round-trip corruption

### Testing workflow

```bash
# Serve static files for testing
python3 -m http.server 8090 --directory speech-demo/target/classes/META-INF/resources

# Test page with URL bookmarking (clip + camera position in hash)
http://localhost:8090/animation-test.html#clip=facepalm

# Pose workshop for interactive blending
http://localhost:8090/pose-workshop.html
```

The test page strips head bone tracks by default (for TalkingHead compatibility). Head-shake works because the masking was removed in the test page. The `animation-test.html` has URL hash state: `#clip=name&cx=..&cy=..&cz=..&tx=..&ty=..&tz=..` — refreshing preserves both the active clip and camera position.

### Remaining poses (19 of 23)

After facepalm animation, these still need authoring via the Blender IK pipeline:

**Stances (looping):** hands-clasped-front, hands-behind-back, thinker, hands-on-hips, open-palms-presenting
**Hand gestures (cycling):** hands-base-to-chest, hands-expand, hands-converge, hands-alternating
**Reactive (one-shot):** finger-wag, shrug, one-hand-raised, finger-pointing-up
**Emotional (one-shot):** self-hug, arms-wide-open, shocked-recoil, hands-on-head, hands-interlocked-chest

Note: `thumbs-up` is redundant with existing `yes` clip.

### Key files

| File | What |
|------|------|
| `speech-demo/.../animations/m2m-animations.glb` | Production bundle (75 clips, approved version at `/tmp/m2m-facepalm-v5.glb`) |
| `speech-demo/.../animations/body-animations.mjs` | Taxonomy, pools, gesture tags (already updated for all 23) |
| `speech-demo/.../animations/author-poses.py` | Blender authoring script (needs updating per new pipeline) |
| `speech-demo/.../avatar.glb` | RPM avatar — IK target reference for bone positions |
| `/tmp/m2m-merged.glb` | Base 52-clip bundle (no new poses) for clean rebuilds |

### What NOT to do

- Do NOT re-import the approved GLB into Blender to extract poses — round-trip corrupts actions
- Do NOT author on the Mixamo skeleton — always use RPM avatar.glb directly
- Do NOT guess quaternion axis directions — use IK targets with world coordinates
- Do NOT claim a pose looks right without taking a screenshot and checking it honestly
- The "bow" animation in M2M is archery (combat/ranged), not a greeting bow

---

## Recently Landed — Orchestration hardening (2026-11-17)

Four issues landed on main outside the queue workflow:

| # | SHA | Summary |
|---|-----|---------|
| #474 | `a4651fd0` | Constructor-time acyclicity validation in `HierarchicalTopology.parentMap` |
| #465 | `87c45db1` | `TokenRecordable` interface replaces instanceof cascade in `CentralisedTopology` |
| #467 | `feb565e7` | `modelId` parameter on `DecisionLoopFactory` (was hardcoded `"unknown"`) |
| #464 | `752c9f6d` | Per-loop bridge bus emits `ActionDispatched` via `loopEventSink` |

**Filed:** #477 — validate parentMap entries reference known agentRoles (added to queue)
**Blog:** `docs/blog/2026-11-17-mdp01-the-gaps-that-tests-dont-find.md`

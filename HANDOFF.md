# HANDOFF — casehub-agency (slot 223)

## Completed — #458 Author 23 conversational body poses (CLOSED)

**Branch:** `issue-458-author-23-conversational-body-poses`

All 23 original poses addressed. 68 animations in `m2m-animations.glb`.

### What was built

**Authored/fixed poses:** finger-pointing-up, finger-pointing-forward, finger-wag (T-1000 pendulum), shrug (animated trap-style + morph frown), shrug-casual, head-shake (animated), head-shake-casual, one-hand-raised, self-hug (from shiver), arms-wide-open, hands-converge (steeple + finger ripple), hands-expand (animated wider idle-talking), hands-clasped-front (guitar mirror), hands-behind-back (mirrored), hands-on-hips (greeting mirror), hands-on-head, hands-over-mouth, hand-over-mouth, facepalm, double-facepalm, ninjajump (3 clips combined)

**Deleted (redundant or not authorable):** hands-alternating, hands-interlocked-chest, shocked-recoil, thinker, open-palms-presenting, hands-base-to-chest, hit-head, hit-chest

### Key techniques discovered

- **Quaternion mirror formula `(x,-y,-z,w)`** — tested all 4 sign combinations, this one produces correct symmetric poses for Mixamo rigs
- **Morph target injection** — added Wolf3D_Head/Wolf3D_Teeth mesh nodes with 72 morph targets to the bone-only animation bundle. First clips with facial expressions (shrug frowns)
- **Source clip reuse** — most poses built from existing animation frames (interact peak, guitar-playing, shiver, confused, greeting, pray) rather than from scratch
- **setFromUnitVectors for direction** — reliable for arm/forearm direction, but doesn't control twist. Must add separate twist via forearm axis rotation
- **Finger overlap verification** — always measure LeftHandMiddle2 vs RightHandMiddle2 distance for clasped/overlapping hand poses

### What does NOT work

- Blender IK round-trip — exports corrupt bone rotations
- Mixing body from one clip with arms from another — spine mismatch changes arm positions
- Guessing rotation sign — always test both directions or all 4 formulas
- `setFromUnitVectors` alone for hand orientation — controls direction but not twist

### Taxonomy restructured

5 categories: **conversational** (stance, hand-gesture, agreement, disagreement, emphasis, uncertainty), **idle** (relaxed, restless, self-amusing, dance, attention), **reactive** (positive, negative, greeting, distress), **dramatic** (death, combat, theatrical), **seated** (idle, talking)

**Allegory gesture tags:** tightrope-walk=careful, moonwalk=rewind, ninjajump=sneaky, meditate=calm, pray=hope, drumming=suspense

**Multi-listed clips:** dab (positive + self-amusing), clapping (agreement + positive), knock (emphasis + attention), dance-simple/dance-arm-pump (dance + positive)

## Next Issue — #457 Integrate M2M animations with TalkingHead

**Status:** OPEN, scaffolded, ready for next session

### What exists

- `body-animations.mjs` has the complete playback system: taxonomy, pool selection (speaking/idle), gesture tag resolution (40+ mappings), keyword fallback (10 patterns), crossfade, head bone exclusion
- All 68 clips tested in animation-test.html

### What needs building

1. **Speaking-state wiring** — connect TalkingHead speech start/stop to speaking/idle pool switching
2. **LLM gesture tag parsing** — extract `[gesture:tag]` from response text, route to GESTURE_MAP, trigger clip
3. **Keyword fallback** — scan responses without tags for keyword matches
4. **Morph target layering** — compose body animation frowns with TalkingHead's viseme/expression system
5. **Idle attention** — silence timeout triggers knock/interact from idle.attention pool

### Key files

| File | What |
|------|------|
| `speech-demo/.../animations/m2m-animations.glb` | 68-clip production bundle |
| `speech-demo/.../animations/body-animations.mjs` | Full taxonomy + playback system |
| `speech-demo/target/.../animation-test.html` | Visual test page (not in src — target only) |
| `speech-demo/.../avatar.glb` | RPM avatar |

### Testing

```bash
# Serve test page
python3 -m http.server 8888 --directory speech-demo/target/classes/META-INF/resources
# Open http://localhost:8888/animation-test.html
```

The test page has URL hash state for clip + camera position. Cache-busting on GLB URL.

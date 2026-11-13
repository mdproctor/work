# HANDOFF — casehub-agency (slot 223)

## Active Issue — #458 Author 23 conversational body poses

### Current state — All 23 poses authored and integrated

**#458 complete.** 23 conversational body poses authored programmatically via Blender Python scripting on the M2M skeleton, retargeted to RPM via the batch pipeline, bundled into m2m-animations.glb (75 total clips, up from 52). Taxonomy, gesture tags, speaking/idle pools, and keyword fallbacks all updated in body-animations.mjs.

### What was done

1. Wrote `author-poses.py` — Blender Python script that creates all 23 poses by composing quaternion rotations from calibrated reference poses (idle-breathe, greeting-wave, pointing, sad)
2. Exported 23 individual GLBs (breathing loops for stances, gesture cycles, oscillations for head-shake/finger-wag, one-shot motions for reactive/emotional)
3. Added entries to batch-retarget-speech-demo.test.ts and ran the retarget pipeline (M2M → RPM skeleton)
4. Updated body-animations.mjs: new subcategories (distress), 23 taxonomy entries, 14 new gesture tag mappings, updated pool weights, 4 new keyword fallbacks

### Issue chain

| # | Title | Status | Depends on |
|---|-------|--------|------------|
| #457 | Avatar demo animation integration | Paused — resumes after #458 | #458 |
| #458 | Author 23 conversational body poses | **Done** | — |
| #459 | Wire speaking-state to audio playback | Open | #458 ✓ |
| #460 | LLM system prompt for continuous gesture tags | Open | #458 ✓, #459 |
| #461 | Contribute body animation system to TalkingHead | Open | #458 ✓, #460 |

### Next — resume #457 / start #459

- Wire speaking-state to audio lifecycle (#459) — now unblocked
- Add variant depth (3-5 clips per subcategory)
- LLM gesture density system prompt (#460)
- Visual testing of all 23 new poses in the browser with TalkingHead

### Key files

| File | What |
|------|------|
| `speech-demo/.../animations/m2m-animations.glb` | 75 pre-retargeted clips (10 MB) |
| `speech-demo/.../animations/body-animations.mjs` | Taxonomy (73 clips), pools, picker, 38 gesture tags |
| `speech-demo/.../animations/author-poses.py` | Blender authoring script for the 23 poses |
| `speech-demo/.../index.html` | Integration with TalkingHead + WebSocket |
| `/tmp/mesh2motion-app/src/retarget/batch-retarget-speech-demo.test.ts` | Batch retarget script (75 entries) |

### What NOT to do

- Do NOT use TalkingHead's `head.mixer` for body animations — it recreates the mixer per `playAnimation()` call
- Do NOT animate Head/Neck/Eye bones from body clips — TalkingHead writes them every frame
- Do NOT ship M2M-space clips to the browser — always pre-retarget to RPM skeleton
- The "bow" animation in M2M is archery (combat/ranged), not a greeting bow
- Do NOT edit the 23 authored GLBs manually — re-run author-poses.py and the retarget pipeline instead

# HANDOFF — casehub-agency (slot 223)

## Active Issue — #458 Author 23 conversational body poses

### Current state — Issue filed, ready to start authoring

**#457 integration work is done.** 52 M2M animations retargeted, body animation module with taxonomy, idle/speaking pools, gesture tags, mood wiring. All committed on branch `issue-455-cc0-motion-base-tagged-animation-library`.

**#458 is the next session.** Author 23 missing conversational body poses in Blender on M2M skeleton, export as GLB, retarget onto RPM. Then #457 resumes for speaking-state wiring and variant depth.

### What to do — #458

1. **Author poses in Blender** on M2M skeleton. P1 first (5 stances + 4 hand gestures), then P2 (6 reactive + 8 emotional).
2. **Export each as GLB** from Blender.
3. **Retarget onto RPM** using batch script at `/tmp/mesh2motion-app/src/retarget/batch-retarget-speech-demo.test.ts` — add new clips to the ANIMATIONS array and re-run.
4. **Copy updated `m2m-animations.glb`** to speech-demo animations dir.
5. **Add clips to taxonomy** in `body-animations.mjs` — drop into the right subcategory.
6. **Add gesture tag mappings** for new clips (shrug→`[gesture:shrug]`, facepalm→`[gesture:facepalm]`, etc.)

### After #458 — resume #457

- Wire `startSpeakingCycle()` to audio playback start/end
- Add variant depth (3-5 variants per subcategory, e.g. agreement: enthusiastic/casual/defensive/reluctant)
- LLM gesture density (system prompt for continuous `[gesture:X]` tags)
- Contribute to TalkingHead

### Key files

| File | What |
|------|------|
| `speech-demo/.../animations/m2m-animations.glb` | 52 pre-retargeted M2M clips (6.4 MB) |
| `speech-demo/.../animations/body-animations.mjs` | Taxonomy, pools, picker, crossfade, gesture tag parsing |
| `speech-demo/.../index.html` | Integration with TalkingHead + WebSocket |
| `/tmp/mesh2motion-app/src/retarget/batch-retarget-speech-demo.test.ts` | Batch retarget script |
| `/tmp/mesh2motion-app` | M2M app, branch `fix/bind-correction-retarget` |

### What NOT to do

- Do NOT use TalkingHead's `head.mixer` for body animations — it recreates the mixer per `playAnimation()` call
- Do NOT animate Head/Neck/Eye bones from body clips — TalkingHead writes them every frame
- Do NOT ship M2M-space clips to the browser — always pre-retarget to RPM skeleton
- The "bow" animation in M2M is archery (combat/ranged), not a greeting bow

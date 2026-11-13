# HANDOFF — casehub-agency (slot 223)

## Active Issue — #458 Author 23 conversational body poses

### Current state — Integration done, pose authoring next

**#457 integration complete.** 52 M2M animations retargeted, body animation module with three-level taxonomy (category→subcategory→clip), idle/speaking pools, gesture tags, TalkingHead mood wiring, head bone masking. Code reviewed — regex lastIndex bug, rAF leak, listener cleanup all fixed. Blog entry written.

### Issue chain

| # | Title | Status | Depends on |
|---|-------|--------|------------|
| #457 | Avatar demo animation integration | Paused — resumes after #458 | #458 |
| #458 | Author 23 conversational body poses | **Next** | — |
| #459 | Wire speaking-state to audio playback | Open | #458 |
| #460 | LLM system prompt for continuous gesture tags | Open | #458, #459 |
| #461 | Contribute body animation system to TalkingHead | Open | #458, #460 |

### Next session — #458

1. Author 23 missing poses in Blender on M2M skeleton (see #458 for full list)
2. Export as GLB, retarget onto RPM using batch script
3. Add clips to taxonomy in `body-animations.mjs`
4. Add gesture tag mappings for new clips

### After #458 — resume #457

- Wire speaking-state to audio lifecycle (#459)
- Add variant depth (3-5 clips per subcategory)
- LLM gesture density system prompt (#460)

### Key files

| File | What |
|------|------|
| `speech-demo/.../animations/m2m-animations.glb` | 52 pre-retargeted M2M clips (6.4 MB) |
| `speech-demo/.../animations/body-animations.mjs` | Taxonomy, pools, picker, crossfade, gesture tags |
| `speech-demo/.../index.html` | Integration with TalkingHead + WebSocket |
| `/tmp/mesh2motion-app/src/retarget/batch-retarget-speech-demo.test.ts` | Batch retarget script |

### What NOT to do

- Do NOT use TalkingHead's `head.mixer` for body animations — it recreates the mixer per `playAnimation()` call
- Do NOT animate Head/Neck/Eye bones from body clips — TalkingHead writes them every frame
- Do NOT ship M2M-space clips to the browser — always pre-retarget to RPM skeleton
- The "bow" animation in M2M is archery (combat/ranged), not a greeting bow

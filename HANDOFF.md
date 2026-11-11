# HANDOFF — casehub-agency (slot 223)

## Last Session

Completed agency#450 (Richer avatar body animation via Mixamo FBX).
Issue closed and landed on main. CC0 motion base work begun, tracked in #455.

### What was built

**Animation system** — full body animation pipeline for the speech demo avatar:
- `AvatarMessage.Animation` sealed record in speech-ws protocol (server-side animation cues)
- Client-side keyword classifier replaces 3 `playGesture` calls with 10-category animation system
- Context-dependent layering — body animations play alongside lip-sync visemes, exclusive flag for full-body animations
- Idle animation loop with 5-8s jitter
- Animation picker UI for manual triggering
- Animation state reset after playback (`setTimeout` to clear `currentAnimation` and `animationExclusive`)

**CC0 animation sourcing (in progress → #455):**
- Mesh2Motion (mesh2motion.org) identified as primary CC0 source — 178 animations, CC0 1.0 / MIT
- 24 conversational animations selected and downloaded via their retarget tool
- Retarget bone roll bug: arms contorted on Mixamo-rigged avatars — filed as Mesh2Motion/mesh2motion-app#159
- `@three-ws/retarget` npm library bundled (retarget.mjs) for runtime bone correction — partially working
- cc0-preview.html wired up for testing all 24 CC0 animations
- UniMate (text-to-motion) identified as alternative path — cloned at /tmp/unimate, miniconda installed

**Taxonomy defined for #455:**
- 23 animations across 11 categories with 3 emotional registers (composed/warm/enthusiastic)
- Sub-categories needed: e.g., agreement-enthusiastic vs agreement-reluctant vs agreement-neutral
- Context tags per animation (children, formal, clinical, casual, etc.)

### Key files changed

| File | What |
|------|------|
| `speech-ws/.../AvatarMessage.java` | Added `Animation` record |
| `speech-ws/.../MessageCodec.java` | Encode for Animation |
| `speech-demo/.../index.html` | Animation system (classifier, idle loop, picker, layering) |
| `speech-demo/.../animations/cc0-animations.glb` | Mesh2Motion CC0 retargeted GLB (24 clips, bone roll issue) |
| `speech-demo/.../animations/cc0-animations-v2.glb` | Same with different correction attempt |
| `speech-demo/.../animations/retarget.mjs` | Bundled @three-ws/retarget |
| `speech-demo/.../cc0-preview.html` | Preview page for CC0 animations |
| `speech-demo/.../animations/convert-blend-to-glb.py` | Blender batch conversion script |

### What's next — #455 CC0 motion base

Two viable paths (try in order):

1. **Wait for Mesh2Motion fix** — mesh2motion-app#159 filed. If Scott fixes the Mixamo retarget bone roll, re-download and it should work.

2. **UniMate offline generation** — generate animations directly on Mixamo skeleton from text prompts. No retargeting needed.
   - Repo cloned at `/tmp/unimate`
   - Miniconda installed, conda env `unimate` created with Python 3.10
   - PyTorch CPU installed (no CUDA on macOS — MPS might work)
   - Still needs: remaining deps (`pip install -r requirements.txt` modified for non-CUDA), model checkpoint download (~2GB from HuggingFace)
   - Key command: `python -m unimate.inference.sample --exp_dir outputs/unimate_uniml3d_f60_v3 --asset mixamo --prompt "An object waves hello."`

### Installed tools

- Blender 5.3.0 at `/opt/homebrew/bin/blender`
- Miniconda with `unimate` conda env (Python 3.10, PyTorch CPU)
- Mesh2Motion assets cloned at `/tmp/mesh2motion-assets`

### Blog

- `blog/2026-11-11-mdp01-the-body-language-problem.md` — diary entry covering the animation work, Mixamo limitations, CC0 landscape, retargeting challenge, and motion base taxonomy

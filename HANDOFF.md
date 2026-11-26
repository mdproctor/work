# HANDOFF — casehub-agency (slot 223)

## Last Session

Three sessions on #537 (avatar eye contact). Eye contact gaze tracking is now **working** — three-part system: head rotation + eye fine-tuning + vertical offset. Remaining queue items (#530-#536) need to continue.

## What Was Fixed (working)

- **Lip sync on second message** — order-of-operations in `_handleSegment`: moved `_resetTurnState()` before segment store
- **Gesture/mood cue drop** — `firstChunk` flag captured before `_turnCuesAttached.add(key)`, was always false
- **Voice selector greyed out** — `_pollModelStatus` engine-prefix key mismatch (`kokoro:af_heart` vs `kokoro`)
- **Session pooling** — switched to `casehub-platform-agent-session` CDI module, added pool config to `agent-config.yaml`
- **TTS parallelism** — `ttsExecutor` in TurnExecutor so LLM streaming continues while TTS runs
- **Disruptive poses removed** — idle/speaking pools stripped to relaxed + conversational stances only
- **Parent pom** — removed dead jboss nexus snapshots repo (was causing 404 hangs on Maven builds)
- **Eye contact gaze tracking** — three-part system in `avatar-component.mjs`, active during speech only

## Eye Contact — RESOLVED

Three-part gaze system in `avatar-component.mjs`:

1. **Head rotation** (`_onAnimFrame`, opt.update callback): Direct angle from avatar origin to camera position → `system` on `bodyRotateX`/`bodyRotateY` with boosted smoothing (`maxv=0.008`, 8x default). No bone-chain feedback loop.

2. **Eye fine-tuning** (`_registerGazeLayer`, morph layer order 100, replace blend): Computes residual camera direction in head-local space via full bone chain quaternion. Sets eye look morphs to compensate for whatever offset the head hasn't fully resolved. Runs after `updateMorphTargets` — bypasses smoothing pipeline.

3. **Vertical offset** (`GAZE_V_OFFSET = -0.06`): Slight upward head tilt bias for natural eye-level contact.

**Key architectural findings:**

- TalkingHead's built-in eye contact system (lines 2830-2836) only compensates for head rotation — doesn't track camera position. Insufficient for orbited cameras.
- `lookAtCamera()` / `lookAt()` create one-shot animations that compete with the state machine queue. Not suitable for continuous tracking.
- `system` morph values go through acceleration-based exponential smoothing (`acc`, `maxv`, `v`). Default `maxv` for `bodyRotateX/Y` is 0.001 — far too slow (1.6%/frame). Boosted to 0.008.
- Morph layer with `replace` blend (order 100) directly sets `morphTargetInfluences` AFTER `updateMorphTargets` — bypasses the entire smoothing pipeline for eyes.
- `EXPRESSION_ALLOW` in `body-animations.mjs` already excludes `eye*` curves at clip load time — the `eye*` filter in `gestureExpressions` morph layer is redundant (but harmless).

## Gaze Test Utility

A standalone test page exists at `speech-demo/src/main/resources/META-INF/resources/gaze-test.html`. No LLM, no Quarkus — just TalkingHead + avatar + gaze code, served with:

```bash
# Requires symlinks (already created):
# speech-demo/.../resources/vendor → speech-ws/.../resources/vendor
# speech-demo/.../resources/avatar-component.mjs → speech-ws/.../resources/avatar-component.mjs

python3 -m http.server 8077 --directory speech-demo/src/main/resources/META-INF/resources
open http://localhost:8077/gaze-test.html
```

Features: Gaze ON/OFF toggle, Scale slider, Vertical offset slider, Camera view buttons, real-time debug panel. Useful for tuning gaze parameters without rebuilding.

## Running Container (Podman)

A speech-demo container is **already running** on port 8099 with the latest gaze code.

```bash
# Check status
podman ps | grep speech

# View logs
podman logs --tail 20 speech-demo

# Test in browser
open http://localhost:8099
```

**To rebuild and redeploy after code changes:**
```bash
podman stop speech-demo && podman rm speech-demo
mvn clean install -DskipTests          # full project — speech-demo needs deployment modules
mvn clean package -pl speech-demo -DskipTests -Dquarkus.package.jar.type=uber-jar
podman build --no-cache --layers=false -t speech-demo -f speech-demo/Containerfile speech-demo
podman run -d --name speech-demo -p 8099:8080 \
  -e CLAUDE_CODE_USE_VERTEX=1 -e CLOUD_ML_REGION=us-east5 \
  -e ANTHROPIC_VERTEX_PROJECT_ID=itpc-gcp-cp-pe-eng-claude \
  -v ~/.config/gcloud:/home/avatar/.config/gcloud:ro speech-demo
```

**Note:** `-o` (offline) for Maven may fail on dependency resolution — use online build. If only speech-ws changed, `mvn clean install -pl speech-ws -DskipTests` then the speech-demo package step is enough.

**Verify jar before deploying:**
```python
python3 -c "
import zipfile
with zipfile.ZipFile('speech-demo/target/casehub-agency-speech-demo-0.4-SNAPSHOT-runner.jar') as z:
    data = z.read('META-INF/resources/avatar-component.mjs').decode('utf-8')
    print('gazeOverride' in data and 'GAZE_V_OFFSET' in data)
"
```

**Startup takes ~40s** (Kokoro + STT model provisioning). Container uses Vertex via mounted gcloud credentials.

**Playwright is available** for UI testing — use `mcp__playwright__browser_navigate` to `http://localhost:8099`.

**IMPORTANT: Do not `kill $(lsof -ti :8080)` or similar broad kills** — other sessions may have processes on nearby ports. Only kill specific PIDs you started.

## Immediate Next Steps

Continue queue items. Test each in Podman after implementation:

1. **#530** — Restore playback rate in headless audio path (active branch)
2. **#531** — Pass configSampleRate from avatar config
3. **#532** — Populate avatar and animation selectors
4. **#533** — Deduplicate avatar YAML discovery
5. **#534** — Add Quarkus deployment test for SpeechAvatarProcessor
6. **#535** — Drive ModelProvisioningService from TtsModelRegistry
7. **#536** — Avatar pipeline as composable playbook

## Cross-Module

- **Qwen3 TTS blocked** — requires Python + PyTorch inference server, cannot run in the Corretto 22 container.
- **Epic #540** (voice endpoint detection) — 6 child issues filed (#541-#546). Separate session.
- **Parent pom** — jboss nexus removed and pushed to origin (commit cbe71252 on parent main).

## Garden Entries

4 entries captured across sessions:
- GE-20261124-b96bb4 — `_handleSegment` order-of-operations (web)
- GE-20261124-530701 — `firstChunk` always false (web)
- GE-20261124-96889a — TalkingHead eye contact is probability not lock (web)
- GE-20261124-acbc2d — agent-session-core vs agent-session pool disabled (jvm)

## References

- Client component: `speech-ws/src/main/resources/META-INF/resources/avatar-component.mjs`
- Gaze test utility: `speech-demo/src/main/resources/META-INF/resources/gaze-test.html`
- TalkingHead source: `speech-ws/src/main/resources/META-INF/resources/vendor/talkinghead.mjs`
  - Eye contact system: lines 2820-2860
  - `lookAtCamera()`: lines 4132-4241
  - `lookAt()`: lines 4249-4330
  - `makeEyeContact()`: lines 4083-4087
  - Animation template eyes: lines 390-420
  - `opt.update(dt)` call: line 2927
  - Morph layer registry: lines 1685-1738
  - Morph target smoothing: lines 1904-1931 (acc/maxv/v)
- Animation config: `speech-demo/src/main/resources/META-INF/resources/animations/avatar-performance.json`
- Container: `speech-demo/Containerfile`
- Avatar config: `speech-demo/src/main/resources/META-INF/avatar.yaml`
- Agent config: `speech-demo/src/main/resources/META-INF/eidos/agent-config.yaml`

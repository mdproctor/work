# HANDOFF — casehub-agency (slot 223)

## Last Session

Two sessions of work on #537. First session fixed lip sync, voice selector, session pooling, TTS parallelism, and filed epic #540. Second session focused on avatar eye contact — **not resolved, needs next session**.

## What Was Fixed (working)

- **Lip sync on second message** — order-of-operations in `_handleSegment`: moved `_resetTurnState()` before segment store
- **Gesture/mood cue drop** — `firstChunk` flag captured before `_turnCuesAttached.add(key)`, was always false
- **Voice selector greyed out** — `_pollModelStatus` engine-prefix key mismatch (`kokoro:af_heart` vs `kokoro`)
- **Session pooling** — switched to `casehub-platform-agent-session` CDI module, added pool config to `agent-config.yaml`
- **TTS parallelism** — `ttsExecutor` in TurnExecutor so LLM streaming continues while TTS runs
- **Disruptive poses removed** — idle/speaking pools stripped to relaxed + conversational stances only
- **Parent pom** — removed dead jboss nexus snapshots repo (was causing 404 hangs on Maven builds)

## Eye Contact Problem — NOT SOLVED

**The goal:** Avatar should look directly at the viewer while speaking. During idle, mostly at camera with occasional natural glances.

**What was tried (all failed or partially failed):**

1. `avatarSpeakingEyeContact: 1.0` — this is a **probability weight** in TalkingHead's animation state machine, not a lock. Even at 1.0, the `dt` timing `[0, [3000,10000], [2000,5000]]` with `eyeContact: [1, null]` allows gaze drift after 3-10s.

2. `lookAtCamera(t)` once at speech start — expires after `t` ms, gaze drifts back to default.

3. `makeEyeContact(30000)` once at speech start — sets `eyeContact: [1]` animation flag, but doesn't prevent body animation morphs from overriding eye direction.

4. `speakTo = {x, y, z}` target at eye height — `lookAtCamera()` reads `speakTo` once at call time, computes rotation, queues a **static** animation. Clearing `speakTo` after the call means the queued animation still uses the default path. Keeping `speakTo` set doesn't help because the animation values are baked at queue time.

5. `lookAtCamera(800)` every 500ms via `opt.update(dt)` callback — per-frame cooldown. Each call should read live camera position. **Did not visually verify this worked.** The deployed jar was stale for some iterations (Quarkus uber-jar caching issue — need `clean` on both speech-ws AND speech-demo).

6. Raised camera presets to eye level (upper y: 0.801→0.92, head y: 0.911→0.95) — so "look at camera" means looking at eye height. **Did not verify framing impact.**

7. Filtered `eye*` morph targets from body animation `gestureExpressions` layer — Mixamo/CC0 clips bake in eye positions as facial expressions. The additive morph layer was applying them on top of TalkingHead's eye contact, pulling gaze off-center. **Latest fix, committed but not verified.**

**Key architectural findings for next session:**

- TalkingHead's eye contact system (lines 2830-2836 of `vendor/talkinghead.mjs`) sets eye morph targets based on head quaternion to compensate for head rotation. It uses `system` property on morph targets.
- The `gestureExpressions` morph layer (order 10, additive) runs AFTER TalkingHead's internal morph processing via `_runMorphLayers()`. It was adding eye values on top of the system values.
- The `opt.update(dt)` callback (line 2927 of talkinghead.mjs) fires every frame — confirmed as the right integration point.
- `lookAtCamera()` without `speakTo` calls `lookAt(null, null, t)` which projects avatar eyes to screen space and computes rotation accounting for `camera.rotation`. This should track camera orbiting.
- `lookAtCamera()` with `speakTo` computes 3D direction to the target — but bakes the rotation values into the queued animation, not live-tracking.

**Stale jar trap:** The Quarkus uber-jar caches dependency jars. When speech-ws resources change, you MUST `clean` both speech-ws and speech-demo before packaging. Use: `mvn -o clean install -pl speech-ws && mvn -o clean package -pl speech-demo -Dquarkus.package.jar.type=uber-jar`. Always verify the jar contents before deploying: `python3 -c "import zipfile; ..."` to read the method body from the jar.

## Running Container (Podman)

A speech-demo container is **already running** on port 8099. No need to rebuild from scratch.

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
mvn -o clean install -pl speech-ws -DskipTests
mvn -o clean package -pl speech-demo -DskipTests -Dquarkus.package.jar.type=uber-jar
podman build --no-cache --layers=false -t speech-demo -f speech-demo/Containerfile speech-demo
podman run -d --name speech-demo -p 8099:8080 \
  -e CLAUDE_CODE_USE_VERTEX=1 -e CLOUD_ML_REGION=us-east5 \
  -e ANTHROPIC_VERTEX_PROJECT_ID=itpc-gcp-cp-pe-eng-claude \
  -v ~/.config/gcloud:/home/avatar/.config/gcloud:ro speech-demo
```

**Use `-o` (offline) for Maven** — the jboss nexus repo was removed from parent but cached pom may still try it. Offline avoids 404 hangs.

**Verify jar before deploying** — Quarkus uber-jar caches dependency jars. After changing speech-ws, you MUST `clean` both modules:
```python
python3 -c "
import zipfile
with zipfile.ZipFile('speech-demo/target/casehub-agency-speech-demo-0.4-SNAPSHOT-runner.jar') as z:
    data = z.read('META-INF/resources/avatar-component.mjs').decode('utf-8')
    idx = data.find('_onAnimFrame(dt) {')
    print(data[idx:idx+200] if idx >= 0 else 'NOT FOUND')
"
```

**Startup takes ~40s** (Kokoro + STT model provisioning). Container uses Vertex via mounted gcloud credentials.

**Playwright is available** for UI testing — use `mcp__playwright__browser_navigate` to `http://localhost:8099`, send messages, take screenshots to verify gaze direction.

## Immediate Next Step

1. **Verify the eye morph filter fix** — the last commit filters `eye*` from body animation expressions. Test in Playwright: send a message, take screenshots during speech, check if eyes face camera. If still wrong, the problem is deeper in TalkingHead's morph pipeline.
2. If filtering works, **verify camera preset framing** — raised y values may crop the head differently.
3. If filtering doesn't work, consider **replacing TalkingHead's eye template entirely** with a custom morph layer that directly sets eye morph targets based on camera position each frame.

## Cross-Module

- **Qwen3 TTS blocked** — requires Python + PyTorch inference server, cannot run in the Corretto 22 container.
- **Epic #540** (voice endpoint detection) — 6 child issues filed (#541-#546). Separate session.
- **Parent pom** — jboss nexus removed and pushed to origin (commit cbe71252 on parent main).

## Garden Entries

4 entries captured this session:
- GE-20261124-b96bb4 — `_handleSegment` order-of-operations (web)
- GE-20261124-530701 — `firstChunk` always false (web)
- GE-20261124-96889a — TalkingHead eye contact is probability not lock (web)
- GE-20261124-acbc2d — agent-session-core vs agent-session pool disabled (jvm)

## References

- Client component: `speech-ws/src/main/resources/META-INF/resources/avatar-component.mjs`
- TalkingHead source: `speech-ws/src/main/resources/META-INF/resources/vendor/talkinghead.mjs`
  - Eye contact system: lines 2820-2860
  - `lookAtCamera()`: lines 4132-4241
  - `lookAt()`: lines 4249-4330
  - `makeEyeContact()`: lines 4083-4087
  - Animation template eyes: lines 390-420
  - `opt.update(dt)` call: line 2927
  - Morph layer registry: lines 1685-1738
- Animation config: `speech-demo/src/main/resources/META-INF/resources/animations/avatar-performance.json`
- Container: `speech-demo/Containerfile`
- Avatar config: `speech-demo/src/main/resources/META-INF/avatar.yaml`
- Agent config: `speech-demo/src/main/resources/META-INF/eidos/agent-config.yaml`

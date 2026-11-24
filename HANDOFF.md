# HANDOFF — casehub-agency (slot 223)

## Last Session

Fixed lip sync consistency — root cause was an order-of-operations bug in `_handleSegment` where `_resetTurnState()` cleared the just-stored segment on new turns. Also fixed `firstChunk` flag always being false (silently dropping gesture/mood cues). Fixed greyed-out voice selector (engine-prefix key mismatch with status endpoint). Enabled LLM session pooling (`agent-session` CDI module + pool config in agent-config.yaml). Pipelined TTS synthesis in parallel with LLM streaming (separate `ttsExecutor`). Locked avatar eye contact via explicit `lookAtCamera()` on speech start. Stripped disruptive poses (jumping, dancing, sitting) from idle/speaking pools. Created epic #540 for voice endpoint detection and interrupt handling (6 child issues).

## Immediate Next Step

Test the eye contact and pose fixes in the running container (port 8099 — built with latest code). Then continue with queue item #531 (pass configSampleRate from avatar config).

## Cross-Module

- **Qwen3 TTS blocked** — requires Python + PyTorch inference server, cannot run in the current Corretto 22 container. Needs a sidecar or separate inference container. Commented out in avatar.yaml for now.
- **Epic #540** (voice endpoint detection) — 6 child issues filed. #541 (RecognitionStream SPI) touches connectors voice-spi, not agency. Implementation is a new session.

## Garden Entries Consulted

GE-20260827-a19839 — TalkingHead speakAudio microtask context (partially relevant — led us to check client-side, but actual root cause was different)

## References

- Container: `speech-demo/Containerfile` — Corretto 22, whisper.cpp + espeak-ng from source
- Avatar config: `speech-demo/src/main/resources/META-INF/avatar.yaml`
- Agent config: `speech-demo/src/main/resources/META-INF/eidos/agent-config.yaml` (session pool config)
- Animation config: `speech-demo/src/main/resources/META-INF/resources/animations/avatar-performance.json`
- Client component: `speech-ws/src/main/resources/META-INF/resources/avatar-component.mjs`

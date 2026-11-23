# HANDOFF — casehub-agency (slot 223)

## Current state

Branch `issue-530-restore-playback-rate-headless` on main. Many commits spanning #529, #530, #537.

**Active issue: #530 (restore playback rate) — DONE**
**#537 (speech-demo build fixes) — IN PROGRESS, lip sync not working**

## What this session delivered

### #529 — Default voice selection (DONE)
- `avatar.yaml` gets `tts-voice: kokoro:af_heart`
- Frontend checks localStorage first, then config default, then first option
- Voice selection persisted to localStorage on change

### #530 — Playback rate in headless audio path (DONE)
- `AvatarEngine` gains `playbackRate` property, applied in `_playNextQueued()`
- `AvatarControls` speed slider wires to `engine.playbackRate`

### #537 — Speech-demo build + container fixes (IN PROGRESS)

**Build fixes (all working):**
- Exclude `casehub-agency` from speech-deployment's agentic-yaml dep (Jandex index miss)
- Add `.setRuntimeInit()` to RUNTIME_INIT synthetic beans in SpeechAvatarProcessor
- Add quarkus-jdbc-h2 + H2 datasource for eidos JPA stores
- Add agent-session-core, quarkus-rest-jackson dependencies
- Index agentic-yaml for AvatarConfigResource REST discovery
- Exclude AgencySpecsResource from Arc
- Correct Qwen3 variant to `0.6b-customvoice`
- Vendor stubs for dynamicbones.mjs and retargeter.mjs

**WebSocket fixes (working):**
- Connection-scoped sessions via ConcurrentHashMap (was singleton, closing one connection killed all)
- Graceful degradation when LLM provider unavailable (try-catch around agentProvider)
- Netty --add-opens flags for container JDK compatibility

**Container/Podman setup (working):**
- Containerfile with Corretto 22, Claude CLI via npm, non-root user
- Multi-stage build: whisper.cpp v1.7.3 + FFI shim compiled from source (OpenMP disabled)
- espeak-ng v1.52.0 compiled from source (copy .so.1.1.51 explicitly, create symlinks)
- lipsync-en.mjs vendor module from TalkingHead repo
- Agent-config with `defaults.backend: claude`
- Podman VM set to 8GB RAM, container capped at 6GB
- `-Dcasehub.speech.auto-download=true` for Whisper model provisioning

**Platform fixes (landed on platform repo):**
- casehubio/platform#934: ModelDescriptor apiModelId defaults, router fallback to default backend
- ClaudeAgentClient: pass model from AgentSessionConfig to CLIOptions (both invoke and openSession paths)

**UI changes (working):**
- Vertical split layout: avatar left (50%), chat right (50%)
- Auto-load avatar on WebSocket connect
- Three camera view buttons: Full / Upper / Head
- User-captured camera presets normalised to avatar height
- Full body scales proportionally with panel aspect ratio
- Capture button copies camera coordinates to clipboard for tuning
- `AvatarEngine.setView(view)` public API

**Lip sync — NOT WORKING (next session's focus):**
1. Server pipeline works: espeak-ng → LipSyncEnricher → visemes in Segment messages (verified: 26-32 visemes per segment)
2. Client receives visemes in `_handleSegment` (verified via interceptor)
3. `speakAudio` now receives `r.words` + `r.visemes` (required by TalkingHead's `if (r.words)` gate)
4. `lipsyncLang = 'en'` set explicitly after showAvatar
5. Avatar has 72 morph targets including `viseme_sil`, `viseme_PP`, etc.
6. First message lip sync works WITH DELAY, second message does NOT work
7. Race condition fix: `firstChunk` flag deferred until seg data available — may not be sufficient
8. Body animation morph layer (order 10, additive) might interfere — not investigated

**Root cause hypothesis for next session:** The streaming TTS path check (`instanceof StreamingTextToSpeech`) — if Kokoro wrapped by LipSyncEnricher still somehow enters the streaming path on subsequent calls, visemes would be text-estimated (not phoneme-based) and r.words wouldn't be set. Verify which TTS path is taken per segment. Also check if TalkingHead's internal animation queue conflicts with the second message's viseme processing.

### Issues filed this session
| # | Title |
|---|-------|
| #537 | Fix speech-demo Quarkus augmentation failures |
| platform#934 | RoutingAgentProvider fallback + ModelDescriptor schema |

### ModelProvisioningService stripped to Kokoro-only
The provisioner was stripped to just Kokoro + Whisper STT for safe container testing. Full voice list needs to be restored when merging.

### Commits on branch (many — squash needed before landing)
~25 commits on `issue-530-restore-playback-rate-headless`. Mix of #529, #530, #537 work. Will need semantic squash.

### Next session priorities
1. **Fix lip sync** — debug why second message fails, check streaming vs non-streaming TTS path
2. **Top-alignment refinement** — user wants less gap above head in upper/head views (captured presets help but may need tuning)
3. **Squash commits** — branch has ~25 commits that need collapsing

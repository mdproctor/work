# HANDOFF — casehub-agency (slot 223)

## Last Session

Completed agency#451 (Qwen3-TTS multi-voice server) from epic #454.
Issue closed and landed on main.

### What was built

**Qwen3VoicePool** — pooled multi-voice .qvoice server management in
connectors-voice-sherpa. The `qwen_tts` binary only loads one .qvoice
per server instance, so the pool manages multiple server processes with
LRU eviction (max 3 active, ~850MB each). Voices start lazily on first
request (~2s cold start).

Key classes (all in `connectors-voice-sherpa`):
- `Qwen3VoicePool` — pool manager with LRU eviction, health recovery
- `Qwen3VoicePoolConfig` — config record (voiceDir, modelVariant, maxActive, basePort)
- `Qwen3ClonedVoice` — `StreamingTextToSpeech` delegate to pool
- `Qwen3TextToSpeech` — fixed null speaker handling for cloned voices

Demo integration: `SpeechProducers.java` creates a pool and registers
all discovered voices as `qwen3:clone:<name>`. HTML dropdown updated
with 10 cloned voice options.

### Connectors repo commits (on main)

- `ba445d18` — feat: add Qwen3VoicePool
- `a33dae03` — fix: use 'clone' label in log messages

### Agency repo (landed on main)

- `e9856366` — feat: register cloned voices from voice pool in demo
- `45fab5a1` — feat: add cloned voice options to demo dropdown

### Next session — #453 Panama/FFM shared library integration

This is L scale / High complexity. Replace the HTTP subprocess
architecture in `Qwen3TextToSpeech` with native FFM/Panama bindings
to the `qwen_tts` C library. The binary's help output and API
endpoints are documented in the spec at
`specs/issue-451-qwen3-tts-multi-voice-server/`.

Remaining epic #454 queue: #453, #450, #452, #449.

## Next Session — connectors voice work

Priority: finish connectors#187 (epic) and #202 (manifest discovery).

### connectors#202 — Voice manifest discovery

Current state: loads a single hardcoded classpath resource instead of scanning
`META-INF/voice-catalog.yaml` across all provider modules. Seed catalog is in
voice-spi where it shouldn't be.

What to do:
1. Move seed catalog from voice-spi to voice-sherpa (or voice-ref)
2. Implement META-INF/voice-catalog.yaml scanning across provider JARs
3. Each provider module declares its own catalog entries

### connectors#187 — remaining children

| Child | Scale | What |
|-------|-------|------|
| voice-whisper | M | OpenAI Whisper cloud API STT (separate from local whisper.cpp in voice-sherpa) |
| voice-tts | M | OpenAI TTS / Google Cloud TTS cloud providers |
| MCP tools | S | `voice_transcribe`, `voice_speak` tools |
| GraphQL wiring | S | ConnectorVoiceApi |
| Per-provider split | L | Optional — split voice-sherpa into per-engine modules |

### After connectors voice work

- agency#448 — speech-demo: replace hand-rolled Vertex with agent-config manifest
- agency#447 — cognitive code migration to neocortex (L/High)

## Queue (epic #364)

- [x] #376, #361, #363, #441, #442, #443, #439
- [ ] #438 — L3 LLM-mediated conflict resolution
- [ ] #440 — counterfactual learning multi-swarm

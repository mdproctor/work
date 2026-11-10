# HANDOFF — casehub-agency (slot 223)

## Last Session

Migrated speech-sherpa from agency to connectors voice-sherpa (connectors#204,
agency#446). 133 Java files + 5 native modules moved. Both landed on main,
issues closed. Filed agency#447 (cognitive code migration to neocortex),
agency#448 (speech-demo LLM modernisation).

Patched CuriosityDrive constructor in AgencyBeans.java for neocortex API
change (#447) — placeholder, proper fix needs cognitive migration.

Blog written: `docs/blog/2026-11-10-mdp01-the-code-that-lived-in-the-wrong-repo.md`

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

# HANDOFF — casehub-agency (slot 223)

## Active — #519 Migrate speech-demo SpeechProducers to AvatarDefinition YAML

**Branch:** `issue-519-migrate-speechproducers-to-avatar-yaml`
**Status:** Complete — ready to land.

### What this session delivered

**#519 — Migrate speech-demo SpeechProducers to AvatarDefinition YAML (DONE)**

7 commits on the branch (squashed from 11):

1. `0451a95e` fix(#521): suppress null fields in Start/Text wire output (pre-existing)
2. `d0440aa2` refactor(#519): relocate TtsModelRegistry and CorrectionHooks to speech-api
3. `7fd78293` feat(#519): add LipSyncMode enum and update TtsEngineSpec
4. `e5d2804f` feat(#519): add AvatarCompiler with per-engine-type registries
5. `7bee0271` feat(#519): add AvatarRecorder and AvatarSpeechProcessor for synthetic bean registration
6. `a4c3c32f` feat(#519): migrate speech-demo to avatar.yaml
7. `78c6f9bb` feat(#519): populate voice dropdown from /api/avatar/config

**Key changes:**
- **AvatarCompiler** — compiles `AvatarDefinition` YAML into `CompiledAvatar` record (TtsModelRegistry, TextToSpeech, StreamingSpeechToText, CleanupConfig) in a single pass. Supports vits, sherpa, kokoro, qwen3 engine types.
- **LipSyncMode** — `auto`/`enabled`/`disabled` per-engine lip-sync control via YAML
- **TtsEngineSpec** — `List<String> clonedVoices` → `boolean cloneDiscovery` for Qwen3 voice pool auto-discovery
- **Type relocations** — `TtsModelRegistry` and `CorrectionHooks` moved from speech-ws to speech-api for deployment recorder visibility
- **AvatarRecorder + AvatarSpeechProcessor** — Quarkus deployment pipeline for synthetic bean registration with `BeanDiscoveryFinishedBuildItem` introspection (skips when app producer exists)
- **speech-demo** — 322-line `SpeechProducers.java` → 86-line `avatar.yaml` + `AvatarSpeechProducer` (YAML reader) + slim `SpeechProducers` (AgentProvider only)
- **Frontend** — voice dropdown populates dynamically from `/api/avatar/config`
- **Three-tier precedence** — `@DefaultBean` defaults → YAML → CDI override
- connectors#330 landed — `SpeakerProducers` removed, @DefaultBean in connectors-voice-sherpa

### Issues filed

| # | Repo | Title | Scale | Complexity |
|---|------|-------|-------|------------|
| #525 | agency | Add /api/avatar/voices endpoint for runtime-discovered voice list | S | Low |
| #526 | agency | Zero-Java avatar: self-wiring Quarkus extension for speech pipeline | L | High |
| #527 | agency | Update speech-demo index.html to consume /api/avatar/config | XS | Low |
| #330 | connectors | Add @DefaultBean producers for speech infrastructure beans | S | Med |

### Queue

```
- [x] #521 — Gson→Jackson migration + kebab aliases for speech-ws (landed)
- [x] #519 — Migrate speech-demo SpeechProducers to AvatarDefinition YAML ← done
- [ ] #424 — multi-swarm-review-findings
- [ ] #523 — Remove Gson from speech-sherpa
- [ ] #524 — NON_NULL audit remaining records
- [ ] #525 — Add /api/avatar/voices endpoint
- [ ] #527 — Update index.html config consumption
- [ ] #526 — Zero-Java avatar self-wiring extension
```

### Design direction noted

The user wants to reach **zero Java code** for avatar deployment — any Pages + Mosaic app gets a talking avatar by adding a dependency and dropping config. This requires: (1) narrow speech deployment module without agency-core transitive dep, (2) declarative AgentProvider via platform agent-config, (3) Mosaic avatar component. Tracked in #526.

### Published

- agency: 7 commits (squash-reviewed)
- public/agency: 1 commit (diary entry)
- connectors: landed #330

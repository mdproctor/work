# HANDOFF — casehub-agency (slot 223)

## Completed this session

**#527 — Update index.html config consumption (DONE)**

Frontend now consumes `/api/avatar/config` for LLM model default, speed slider min from minPlaybackRate, and default voice applied after loadVoices(). Removed redundant `populateVoiceDropdown()`. Fixed VoicesEndpoint import path after #519 package relocation.

**#526 — Zero-Java avatar self-wiring extension (DONE)**

Five work items delivered:
1. New `speech-deployment` module — standalone Quarkus deployment module with agency-core excluded. `SpeechAvatarProcessor` discovers `META-INF/avatar.yaml`, registers TTS/STT/cleanup beans via `AvatarRecorder`.
2. Infrastructure relocation — `VoicesEndpoint`, `JsonPerformanceVocabulary`, `DiagnosticEndpoint` moved from speech-demo to speech-ws. Fixed with `Instance<>` guards for optional injection.
3. LLM wired via eidos descriptors + agent-config — deleted hardcoded `SpeechProducers.java` and `AvatarSpeechProducer.java`. Consumer uses `descriptors.yaml` + `agent-config.yaml` + `casehub.avatar.agent-id` in application.properties.
4. Frontend extraction — `avatar-component.mjs` ES module (520 lines) with `AvatarEngine` + `AvatarControls` classes. index.html reduced from 736 to 200 lines.
5. Dependency convergence — excluded `casehub-platform-schema-generator` from speech-deployment to fix enforcer.

### What previous sessions delivered

**#519 — Migrate speech-demo SpeechProducers to AvatarDefinition YAML (LANDED)**

AvatarCompiler, LipSyncMode, AvatarRecorder + AvatarSpeechProcessor deployment pipeline.

**#525 — Add /api/avatar/voices endpoint (LANDED)**

`GET /api/avatar/voices` returns sorted TtsModelRegistry keys.

### Queue

```
- [x] #525 — Add /api/avatar/voices endpoint (landed)
- [x] #527 — Update index.html config consumption (done)
- [x] #526 — Zero-Java avatar self-wiring extension (done)
- [ ] #528 — Add voice metadata to voices response
- [ ] #529 — Default voice selection in dynamic dropdown
- [ ] #530 — Restore playback rate in headless audio path
- [ ] #531 — Pass configSampleRate from avatar config to startRecording
- [ ] #532 — Populate avatar and animation selectors in AvatarControls
- [ ] #533 — Deduplicate avatar YAML discovery between deployment modules
- [ ] #534 — Add Quarkus deployment test for SpeechAvatarProcessor
- [ ] #535 — Drive ModelProvisioningService from TtsModelRegistry
```

### Issues filed this session

| # | Title | Scale | Complexity |
|---|-------|-------|------------|
| #528 | Add voice metadata to /api/avatar/voices response | S | Med |
| #529 | Default voice selection in dynamic dropdown | XS | Low |
| #530 | Restore playback rate in headless audio path | XS | Low |
| #531 | Pass configSampleRate from avatar config to startRecording | XS | Low |
| #532 | Populate avatar and animation selectors in AvatarControls | S | Low |
| #533 | Deduplicate avatar YAML discovery between deployment modules | S | Med |
| #534 | Add Quarkus deployment test for SpeechAvatarProcessor | S | Med |
| #535 | Drive ModelProvisioningService from TtsModelRegistry | S | Med |

### Design direction

Zero-Java avatar deployment achieved: consumer adds dependencies, drops 3 YAML files (avatar.yaml, descriptors.yaml, agent-config.yaml), writes zero Java. speech-demo went from 7 Java files to 2 (ModelProvisioningService + ModelStatusEndpoint). Remaining queue items are UX polish (#528-#532) and infrastructure hardening (#533-#535).

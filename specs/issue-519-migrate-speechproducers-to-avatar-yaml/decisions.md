## D1: Bridge architecture — SPI registry pattern

**Choice:** SPI registry — define factory interfaces in speech-api, speech-sherpa provides CDI implementations per engine type, framework-level producer discovers factories and assembles runtime beans from YAML spec.
**Alternatives:**
- Deployment synthetic beans — Quarkus recorder creates beans at RUNTIME_INIT. Quarkus-native but awkward for FFM native calls and requires deployment→speech-sherpa coupling.
- Direct coupling module — single module depends on both agentic-yaml and speech-sherpa. Simple but not extensible — adding engine types means editing the compiler.
**Rationale:** Extensible by design — adding a new TTS engine type means implementing one SPI, no changes to compilation or framework. Follows the existing platform pattern of pluggable strategies.
**Trade-offs:** More indirection than direct coupling; factory discovery via CDI adds a layer.
**Sources:** `SpeechProducers.java`, `AvatarDefinition.java`, `AgenticYamlProcessor.java`
**Exploration:** quick
**Status:** captured

## D2: SPI interfaces live in speech-api

**Choice:** Factory SPIs (`TtsEngineFactory`, `SttFactory`, etc.) live in `speech-api` module.
**Alternatives:**
- connectors-voice-spi — closest to the produced types (TextToSpeech etc.) but it's a peer repo.
- agentic-yaml — alongside spec records but adds engine-aware SPIs to a pure spec module.
**Rationale:** speech-api is already the SPI module for the speech pipeline. speech-sherpa already depends on it. Natural home alongside `SpeechPromptAssembler`, `PhonemeAligner`, etc.
**Trade-offs:** speech-api gains a dependency on agentic-yaml spec records (for `TtsEngineSpec`, `SttSpec`).
**Sources:** `speech-api/src/main/java/io/casehub/agency/speech/`
**Exploration:** quick
**Depends on:** D1 (SPI registry pattern)
**Status:** captured

## D3: Framework-level producer in speech-ws

**Choice:** `AvatarRuntimeProducer` CDI bean in speech-ws. Injects `AvatarConfigBean` + all `TtsEngineFactory` impls via CDI, produces `TtsModelRegistry`, `StreamingSpeechToText`, `CleanupConfig`, `TextToSpeech`.
**Alternatives:**
- New avatar-runtime module — cleanest separation but adds a module when speech-ws is the only consumer.
- agentic-yaml-deployment — build-time wiring via recorder, complex for FFM runtime calls.
**Rationale:** speech-ws is already the WebSocket module that consumes these beans. Any app using speech-ws with an avatar.yaml gets automatic wiring. Needs new dependency on agentic-yaml (spec records).
**Trade-offs:** speech-ws gains agentic-yaml dependency; if a non-WebSocket consumer appears, may need extraction.
**Sources:** `SpeechWebSocket.java`, `AvatarConfigBean.java`
**Exploration:** quick
**Depends on:** D1 (SPI registry), D2 (SPI location)
**Status:** captured

## D4: Infrastructure beans as @DefaultBean in speech-sherpa

**Choice:** speech-sherpa provides `@DefaultBean` CDI producers for speaker diarization, embedding extraction, speaker registry, voiceprint store, transcript correction, conversation vocabulary, and correction hooks. Any app with speech-sherpa on classpath gets sensible defaults.
**Alternatives:**
- Extend YAML spec — add diarization, speaker, correction sections to AvatarDefinition. Full control but rarely varies.
- Keep in SpeechProducers — only migrate TTS/STT/cleanup to YAML. Leaves hardcoded producers in demo app.
**Rationale:** These are infrastructure, not configuration. They rarely vary between deployments. `@DefaultBean` lets apps override when needed without YAML boilerplate.
**Trade-offs:** Less explicit than YAML; apps that need non-default wiring must provide CDI overrides.
**Sources:** `SpeechProducers.java` lines 124-319
**Exploration:** quick
**Depends on:** D1 (SPI registry)
**Status:** captured

## D5: Lip-sync wrapping is factory-internal

**Choice:** Each `TtsEngineFactory` auto-wraps with `LipSyncEnricher` when the engine returns empty phonemes and an aligner is available. Zero configuration.
**Alternatives:**
- YAML configured — `lip-sync: true/false` per engine. Explicit but noisy for a capability that should just work.
**Rationale:** The factory knows whether its engine produces phoneme timing. Auto-wrapping is the right default; no reason to expose this as config.
**Trade-offs:** Less explicit — an app can't disable lip-sync per engine without overriding the factory.
**Sources:** `SpeechProducers.java` lines 29-34, 44-48
**Exploration:** quick
**Depends on:** D1 (SPI registry), D2 (SPI location)
**Status:** captured

## D6: Three-tier precedence — defaults, YAML, CDI override

**Choice:** Three-tier bean resolution: (1) speech-sherpa `@DefaultBean`s for zero-config sensible defaults, (2) `avatar.yaml` for declarative customisation — only needed when diverging from defaults, (3) app-level CDI `@Produces` for programmatic control. Higher tier displaces lower. YAML is the customisation layer, not boilerplate — if defaults work, skip it entirely.
**Alternatives:**
- YAML-only — no default beans, YAML always required. Boilerplate for the common case.
- CDI-only — keep current approach. Defeats the purpose of this issue.
- Two-tier (YAML + CDI) — no zero-config path. Apps must write either YAML or producers.
**Rationale:** Convention over configuration. Most deployments want "working avatar with defaults." YAML adds value only when you need specific voices, non-default parameters, or custom cleanup rules. CDI override is the escape hatch for things YAML can't express (Vertex AI wiring, custom factories).
**Trade-offs:** Three precedence tiers to understand; @DefaultBean priority rules must be clear in docs.
**Sources:** `SpeechProducers.java`, Quarkus `@DefaultBean` semantics
**Exploration:** quick
**Depends on:** D3 (producer in speech-ws), D4 (default beans in speech-sherpa)
**Status:** captured

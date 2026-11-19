# Design: Migrate SpeechProducers to AvatarDefinition YAML

**Issue:** casehubio/agency#519
**Branch:** `issue-519-migrate-speechproducers-to-avatar-yaml`

## Problem

`SpeechProducers.java` in `speech-demo` hardcodes 12 CDI producers for voice engines, STT, cleanup, speaker services, and transcript correction. The `AvatarDefinition` YAML infrastructure exists (#449) but only parses and serves config — it doesn't produce runtime beans. Apps must write CDI producers to wire speech pipelines.

## Goal

Drop an `avatar.yaml` in `META-INF/` and get a fully wired avatar speech pipeline. No CDI producers required in the app for voice/STT/cleanup. Three-tier precedence: sensible defaults → YAML customisation → CDI override.

## Architecture

### Three-Tier Bean Resolution

```
Tier 3 (highest): App CDI @Produces        → AgentProvider (Vertex AI), custom engines
Tier 2:           avatar.yaml → Recorder    → specific voices, sample rate, cleanup
Tier 1 (lowest):  @DefaultBean              → sensible zero-config defaults
```

Each YAML section independently produces its bean. Missing sections fall back to tier 1.

### Component Overview

```
avatar.yaml ──▶ AgenticYamlProcessor ──▶ AvatarConfigBuildItem
                     (build time)              │
                                               ▼
                                       AgenticRecorder ──▶ Synthetic CDI beans
                                        (RUNTIME_INIT)     (TtsModelRegistry,
                                               │            StreamingSpeechToText,
                                               │            CleanupConfig,
                                               │            CorrectionHooks)
                                               │
                                       AvatarCompiler
                                               │
                              ┌────────────────┼────────────────┐
                              ▼                ▼                ▼
                      TtsEngineRegistry   SttRegistry    CleanupRegistry
                       (per engine type)
```

## Module Changes

### agentic-yaml — New: AvatarCompiler + engine registries

**Location:** `io.casehub.agency.core.yaml.compiler`

**AvatarCompiler** — composition root. Takes an `AvatarDefinition`, delegates to per-type registries, returns assembled runtime beans.

```java
public class AvatarCompiler {
    public Map<String, TextToSpeech> compileTtsRegistry(TtsSpec tts);
    public StreamingSpeechToText compileStt(SttSpec stt);
    public CleanupConfig compileCleanup(CleanupConfigSpec cleanup);
    public CorrectionHooks compileCorrectionHooks();
}
```

**TtsEngineRegistry** — resolves `TtsEngineSpec` → `Map<String, TextToSpeech>`. Dispatches on `type`:

| Type | Construction | Key format |
|------|-------------|------------|
| `vits` | `VitsTextToSpeech.withDefaults(model)` per voice. Native phoneme timing. | `{voiceName}` |
| `sherpa` | `SherpaOnnxTextToSpeech.withDefaults(model)` per voice. Auto-wraps with `LipSyncEnricher` per `lipSync` field. | `sherpa:{voiceName}` |
| `kokoro` | One shared `KokoroTextToSpeech.withDefaults(0)`. Each voice → `kokoro.forVoice(index)`. Name→index mapping internal. | `kokoro:{voiceName}` |
| `qwen3` | Static voices: `Qwen3TextToSpeech.withDefaults(name)`. Cloned: `Qwen3VoicePool.createProvisioned()` → `pool.forVoice(name)`. | `qwen3:{name}`, `qwen3:clone:{name}` |

Lip-sync wrapping follows the `lipSync` field on `TtsEngineSpec`:
- `auto` (default): wrap when engine returns empty phonemes and `EspeakPhonemeAligner` is available
- `enabled`: wrap (error if no aligner)
- `disabled`: skip

**SttRegistry** — resolves `SttSpec` → `StreamingSpeechToText`:

| Type | Construction |
|------|-------------|
| `whisper` | `WhisperSpeechToText.withDefaults()` |
| `zipformer` | `SherpaOnnxStreamingSpeechToText.withDefaults()` |
| `whisper-fallback` (default) | Whisper with Zipformer fallback |

**CleanupRegistry** — resolves `CleanupConfigSpec` → `CleanupConfig`. Discovers available `TextFilter` implementations (filler removal, casing, punctuation, GECToR) and filters by `maxDestructiveness`.

Follows the existing `KokoroConfigRegistry`/`SherpaConfigRegistry` pattern — stateless resolvers, spec record → runtime instance.

### agentic-yaml — Spec changes

**TtsEngineSpec** — add optional `lipSync` field:

```java
public record TtsEngineSpec(
        String type,
        List<TtsVoiceSpec> voices,
        @Nullable List<String> clonedVoices,
        @Nullable LipSyncMode lipSync) {
    // lipSync defaults to AUTO when null
}
```

```java
public enum LipSyncMode { AUTO, ENABLED, DISABLED }
```

### agentic-yaml-deployment — Build step extensions

New build steps in `AgenticYamlProcessor`:

```java
@BuildStep
@Record(RUNTIME_INIT)
SyntheticBeanBuildItem registerTtsModelRegistry(
        AgenticRecorder recorder, AvatarConfigBuildItem avatarConfig) {
    if (avatarConfig.definition() == null || avatarConfig.definition().tts() == null)
        return null;
    // recorder creates TtsModelRegistry from definition
}

@BuildStep
@Record(RUNTIME_INIT)
SyntheticBeanBuildItem registerStt(
        AgenticRecorder recorder, AvatarConfigBuildItem avatarConfig) {
    if (avatarConfig.definition() == null || avatarConfig.definition().stt() == null)
        return null;
    // recorder creates StreamingSpeechToText from definition
}

@BuildStep
@Record(RUNTIME_INIT)
SyntheticBeanBuildItem registerCleanupConfig(
        AgenticRecorder recorder, AvatarConfigBuildItem avatarConfig) {
    if (avatarConfig.definition() == null || avatarConfig.definition().cleanup() == null)
        return null;
    // recorder creates CleanupConfig from definition
}

@BuildStep
@Record(RUNTIME_INIT)
SyntheticBeanBuildItem registerDefaultTts(
        AgenticRecorder recorder, AvatarConfigBuildItem avatarConfig) {
    if (avatarConfig.definition() == null) return null;
    // first voice in first engine as default TextToSpeech
}

@BuildStep
@Record(RUNTIME_INIT)
SyntheticBeanBuildItem registerCorrectionHooks(
        AgenticRecorder recorder, AvatarConfigBuildItem avatarConfig) {
    if (avatarConfig.definition() == null) return null;
    // recorder creates CorrectionHooks from TranscriptCorrector + ConversationVocabulary
}
```

Each build step checks its section independently — partial YAML is valid.

### agentic-yaml runtime — Recorder extensions

New methods in `AgenticRecorder`:

```java
@Recorder
public class AgenticRecorder {
    // existing methods...

    public RuntimeValue<TtsModelRegistry> createTtsModelRegistry(AvatarDefinition definition) {
        var compiler = new AvatarCompiler();
        var models = compiler.compileTtsRegistry(definition.tts());
        return new RuntimeValue<>(new TtsModelRegistry(models));
    }

    public RuntimeValue<StreamingSpeechToText> createStt(AvatarDefinition definition) {
        var compiler = new AvatarCompiler();
        return new RuntimeValue<>(compiler.compileStt(definition.stt()));
    }

    public RuntimeValue<CleanupConfig> createCleanupConfig(AvatarDefinition definition) {
        var compiler = new AvatarCompiler();
        return new RuntimeValue<>(compiler.compileCleanup(definition.cleanup()));
    }

    public RuntimeValue<TextToSpeech> createDefaultTts(AvatarDefinition definition) {
        var compiler = new AvatarCompiler();
        var models = compiler.compileTtsRegistry(definition.tts());
        // first entry as default
        return new RuntimeValue<>(models.values().iterator().next());
    }

    public RuntimeValue<CorrectionHooks> createCorrectionHooks() {
        var compiler = new AvatarCompiler();
        return new RuntimeValue<>(compiler.compileCorrectionHooks());
    }
}
```

### speech-api — Type relocations (D9)

Move from `speech-ws` to `speech-api`:

- **`TtsModelRegistry`** — depends only on `TextToSpeech` from `connectors-voice-spi` (already a speech-api dependency). Package: `io.casehub.agency.speech`.
- **`CorrectionHooks`** — depends only on JDK functional types. Package: `io.casehub.agency.speech`.

Existing references in `speech-ws` (`SpeechWebSocket`, `SpeechSession`) update import paths. No API change.

### connectors-voice-sherpa (peer repo) — @DefaultBean producers

New `DefaultSpeechProducers` class with `@DefaultBean @ApplicationScoped` producers:

| Bean | Default |
|------|---------|
| `TextToSpeech` | `VitsTextToSpeech.withDefaults()` |
| `StreamingSpeechToText` | Whisper with Zipformer fallback |
| `CleanupConfig` | filler + casing + punctuation + GECToR (graceful degradation) |
| `SpeakerEmbeddingExtractor` | campplus model |
| `SpeakerRegistry` | `CosineDistanceSpeakerRegistry` |
| `VoiceprintStore` | `FileVoiceprintStore` at `~/.casehub/voiceprints` |
| `SpeakerDiarizationService` | sherpa diarization (segmentation + campplus) |
| `ConversationVocabulary` | empty vocabulary |
| `TranscriptCorrector` | SymSpell + phonetic + ngram (graceful degradation) |

These fire only when no YAML-driven or app-level bean exists.

### speech-demo — Simplified

**`SpeechProducers.java`** shrinks to just the `AgentProvider` producer:

```java
@ApplicationScoped
public class SpeechProducers {
    @Produces @ApplicationScoped
    AgentProvider agentProvider() { /* Vertex AI wiring — stays hardcoded */ }
}
```

**New `META-INF/avatar.yaml`:**

```yaml
agent: speech-demo
model: claude-haiku-4-5@20251001
sample-rate: 16000
speed: 0.9
min-playback-rate: 0.6

tts:
  engines:
    - type: vits
      voices:
        - lessac-medium
        - { name: lessac-high, model: vits-piper-en_US-lessac-high }
        - { name: amy, model: vits-piper-en_US-amy-medium }
        - { name: ryan, model: vits-piper-en_US-ryan-high }
        - { name: jenny, model: vits-piper-en_GB-jenny_dioco-medium }
    - type: sherpa
      voices:
        - lessac-medium
        - { name: lessac-high, model: vits-piper-en_US-lessac-high }
        - { name: amy, model: vits-piper-en_US-amy-medium }
        - { name: ryan, model: vits-piper-en_US-ryan-high }
        - { name: jenny, model: vits-piper-en_GB-jenny_dioco-medium }
    - type: kokoro
      voices:
        - af_alloy
        - af_aoede
        - af_bella
        - af_heart
        - af_jessica
        - af_kore
        - af_nicole
        - af_nova
        - af_river
        - af_sarah
        - af_sky
        - am_adam
        - am_echo
        - am_eric
        - am_fenrir
        - am_liam
        - am_michael
        - am_onyx
        - am_puck
        - am_santa
        - bf_alice
        - bf_emma
        - bf_isabella
        - bf_lily
        - bm_daniel
        - bm_fable
        - bm_george
        - bm_lewis
        - ef_dora
        - em_alex
        - ff_siwis
        - hf_alpha
        - hf_beta
        - hm_omega
        - hm_psi
        - if_sara
        - im_nicola
        - jf_alpha
        - jf_gongitsune
        - jf_nezumi
        - jf_tebukuro
        - jm_kumo
        - pf_dora
        - pm_alex
        - pm_santa
        - zf_xiaobei
        - zf_xiaoni
        - zf_xiaoxiao
        - zf_xiaoyi
        - zm_yunjian
        - zm_yunxi
        - zm_yunxia
        - zm_yunyang
    - type: qwen3
      voices: [ryan, aiden, eric, dylan, serena, vivian]
      cloned-voices: true

stt:
  type: whisper-fallback

cleanup:
  max-destructiveness: 3
```

### AvatarConfig (properties) scope (D7)

`AvatarConfig` retains runtime/platform fields only. Physical-layer fields (`sampleRate`, `minPlaybackRate`) are deprecated — YAML is authoritative for these.

**Retained in AvatarConfig:** `systemPrompt`, `agentId`, `tenantId`, `proactiveTickIntervalSeconds`, `attentionEnabled`, `attentionSubtleSeconds`, `attentionStrongSeconds`, `maxDestructiveness`.

**Authoritative in AvatarDefinition YAML:** `sampleRate`, `minPlaybackRate`, `speed`, `ttsVoice`, `cameraView`, `model`, `tts`, `stt`, `cleanup`.

`SpeechWebSocket.onOpen()` reads physical-layer values from `AvatarConfigBean.definition()` when available, falls back to `AvatarConfig` for backward compatibility during migration.

## Testing

- **AvatarCompiler unit tests** — each registry method with known specs → verify correct engine types and voice mappings
- **TtsEngineRegistry** — test each engine type (vits, sherpa, kokoro, qwen3) with voice specs
- **LipSync wrapping** — test auto/enabled/disabled modes
- **Partial YAML** — verify that missing sections fall back to @DefaultBean
- **Full integration** — speech-demo with avatar.yaml, verify `/api/avatar/config` serves correct config and `SpeechWebSocket` connects successfully
- **Backward compatibility** — speech-demo with no avatar.yaml, verify @DefaultBean path works

## Migration Path

1. Type relocations (TtsModelRegistry, CorrectionHooks → speech-api)
2. Add engine registries and AvatarCompiler to agentic-yaml
3. Extend deployment processor and recorder
4. Add @DefaultBean producers to connectors-voice-sherpa
5. Create avatar.yaml in speech-demo
6. Slim SpeechProducers to AgentProvider only
7. Verify all 205 speech-ws tests pass
8. Verify `/api/avatar/config` endpoint

## Scope Boundaries

**In scope:**
- AvatarCompiler + per-engine-type registries
- Deployment/recorder extensions for synthetic bean production
- Type relocations (TtsModelRegistry, CorrectionHooks)
- @DefaultBean producers in connectors-voice-sherpa
- avatar.yaml in speech-demo
- SpeechProducers cleanup

**Out of scope:**
- Extending YAML for speaker diarization/embedding/registry (D4 — these are @DefaultBean infrastructure)
- Frontend index.html changes (separate follow-up — config endpoint already exists)
- AgentProvider YAML surface (deployment-specific, stays CDI)

## References

- `SpeechProducers.java` — current hardcoded producers (322 lines)
- `AvatarDefinition.java`, `TtsSpec.java`, `TtsEngineSpec.java`, `TtsVoiceSpec.java`, `SttSpec.java`, `CleanupConfigSpec.java` — existing YAML spec records
- `KokoroConfigRegistry.java`, `SherpaConfigRegistry.java` — existing registry pattern
- `AgenticYamlProcessor.java` — existing deployment processor with `discoverAvatarConfig()` build step
- `AgenticRecorder.java` — existing recorder with `createAvatarConfigBean()` method
- `AvatarConfigBean.java`, `AvatarConfigResource.java` — existing runtime bean + REST endpoint
- `SpeechWebSocket.java` — consumer of all speech beans
- `AvatarConfig.java` — SmallRye config mapping for runtime/platform fields
- `TtsModelRegistry.java` — record to relocate from speech-ws to speech-api
- `CorrectionHooks.java` — record to relocate from speech-ws to speech-api

# Avatar YAML Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #519 — Migrate speech-demo SpeechProducers to use AvatarDefinition YAML
**Issue group:** #519

**Goal:** Replace hardcoded CDI producers in speech-demo with a YAML-driven deployment pipeline that auto-produces speech beans from `avatar.yaml`.

**Architecture:** An `AvatarCompiler` in agentic-yaml translates `AvatarDefinition` spec records into runtime beans (`TtsModelRegistry`, `StreamingSpeechToText`, `CleanupConfig`) via per-engine-type registries. The Quarkus deployment processor creates synthetic CDI beans from the compiled result at `RUNTIME_INIT`. Three-tier precedence: `@DefaultBean` defaults → YAML synthetic beans → app CDI overrides.

**Tech Stack:** Java 22 (FFM/Panama), Quarkus deployment/recorder, Jackson YAML, JUnit 5 + AssertJ

## Global Constraints

- JDK 22+ required for `AvatarCompiler` and engine registries — add to `maven-compiler-plugin` excludes in default profile, include in `jdk22+` profile
- `connectors-voice-sherpa` is a peer repo — `@DefaultBean` producers for infrastructure beans (D4) are out of scope; file a follow-up issue on `connectors`
- `speech-demo` retains its `AgentProvider` CDI producer (Vertex AI — deployment-specific, not YAML)
- All new registries follow the stateless `KokoroConfigRegistry`/`SherpaConfigRegistry` pattern
- Synthetic beans registered as normal-priority; build steps skip registration when app-level producer exists (via `BeanDiscoveryFinishedBuildItem`)

---

## Batch 1: Foundation — Type relocations and spec changes

### Task 1: Relocate TtsModelRegistry and CorrectionHooks to speech-api

**Files:**
- Move: `speech-ws/.../ws/TtsModelRegistry.java` → `speech-api/.../speech/TtsModelRegistry.java` (use `ide_move_file`)
- Move: `speech-ws/.../ws/CorrectionHooks.java` → `speech-api/.../speech/CorrectionHooks.java` (use `ide_move_file`)
- Modify: `speech-ws/.../ws/SpeechWebSocket.java` — update imports
- Modify: `speech-ws/.../ws/SpeechSession.java` — update imports
- Modify: `speech-demo/.../demo/SpeechProducers.java` — update imports

**Interfaces:**
- Produces: `io.casehub.agency.speech.TtsModelRegistry` (record, same API, new package)
- Produces: `io.casehub.agency.speech.CorrectionHooks` (record, same API, new package)

- [ ] **Step 1: Move TtsModelRegistry to speech-api**

Use `ide_move_file` to move `speech-ws/src/main/java/io/casehub/agency/speech/ws/TtsModelRegistry.java` to `speech-api/src/main/java/io/casehub/agency/speech/TtsModelRegistry.java`. IntelliJ will update imports in all dependent modules.

- [ ] **Step 2: Move CorrectionHooks to speech-api**

Use `ide_move_file` to move `speech-ws/src/main/java/io/casehub/agency/speech/ws/CorrectionHooks.java` to `speech-api/src/main/java/io/casehub/agency/speech/CorrectionHooks.java`. IntelliJ will update imports.

- [ ] **Step 3: Verify compilation**

Run: `mvn --batch-mode compile -pl speech-api,speech-ws,speech-demo`
Expected: BUILD SUCCESS — all imports resolved

- [ ] **Step 4: Run speech-ws tests**

Run: `mvn --batch-mode test -pl speech-ws`
Expected: All 205 tests pass

- [ ] **Step 5: Commit**

```bash
git add speech-api/ speech-ws/ speech-demo/
git commit -m "refactor(#519): relocate TtsModelRegistry and CorrectionHooks to speech-api

Move both records from speech-ws (io.casehub.agency.speech.ws) to
speech-api (io.casehub.agency.speech). Required for deployment recorder
visibility — agentic-yaml depends on speech-api, not speech-ws.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 2: Add LipSyncMode enum and update TtsEngineSpec

**Files:**
- Create: `agentic-yaml/.../spec/LipSyncMode.java`
- Modify: `agentic-yaml/.../spec/TtsEngineSpec.java`
- Test: `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/spec/TtsEngineSpecTest.java`

**Interfaces:**
- Produces: `LipSyncMode` enum (`AUTO`, `ENABLED`, `DISABLED`)
- Produces: Updated `TtsEngineSpec` with `boolean cloneDiscovery` and `@Nullable LipSyncMode lipSync`

- [ ] **Step 1: Write failing test for new TtsEngineSpec fields**

```java
package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TtsEngineSpecTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    void deserializes_cloneDiscovery_boolean() throws Exception {
        String yaml = """
                type: qwen3
                voices:
                  - ryan
                cloned-voices: true
                """;
        var spec = YAML.readValue(yaml, TtsEngineSpec.class);
        assertThat(spec.type()).isEqualTo("qwen3");
        assertThat(spec.cloneDiscovery()).isTrue();
        assertThat(spec.voices()).hasSize(1);
    }

    @Test
    void cloneDiscovery_defaults_to_false() throws Exception {
        String yaml = """
                type: vits
                voices:
                  - lessac-medium
                """;
        var spec = YAML.readValue(yaml, TtsEngineSpec.class);
        assertThat(spec.cloneDiscovery()).isFalse();
    }

    @Test
    void deserializes_lipSync_mode() throws Exception {
        String yaml = """
                type: sherpa
                voices:
                  - lessac-medium
                lip-sync: disabled
                """;
        var spec = YAML.readValue(yaml, TtsEngineSpec.class);
        assertThat(spec.lipSync()).isEqualTo(LipSyncMode.DISABLED);
    }

    @Test
    void lipSync_defaults_to_null_meaning_auto() throws Exception {
        String yaml = """
                type: vits
                voices:
                  - lessac-medium
                """;
        var spec = YAML.readValue(yaml, TtsEngineSpec.class);
        assertThat(spec.lipSync()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=TtsEngineSpecTest`
Expected: FAIL — `cloneDiscovery` and `lipSync` fields don't exist yet

- [ ] **Step 3: Create LipSyncMode enum**

Create `agentic-yaml/src/main/java/io/casehub/agency/core/yaml/spec/LipSyncMode.java`:

```java
package io.casehub.agency.core.yaml.spec;

public enum LipSyncMode {
    AUTO, ENABLED, DISABLED
}
```

- [ ] **Step 4: Update TtsEngineSpec**

Replace the existing `TtsEngineSpec` record to change `clonedVoices` from `@Nullable List<String>` to `boolean cloneDiscovery`, and add `@Nullable LipSyncMode lipSync`:

```java
package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record TtsEngineSpec(
        String type,
        List<TtsVoiceSpec> voices,
        @JsonAlias("cloned-voices") boolean cloneDiscovery,
        @JsonAlias("lip-sync") @Nullable LipSyncMode lipSync) {

    public TtsEngineSpec {
        Objects.requireNonNull(type, "type");
        voices = voices != null ? List.copyOf(voices) : List.of();
    }

    @JsonCreator
    static TtsEngineSpec fromJson(
            @JsonProperty("type") String type,
            @JsonProperty("voices") @Nullable List<TtsVoiceSpec> voices,
            @JsonProperty("cloneDiscovery") @JsonAlias("cloned-voices") @Nullable Boolean cloneDiscovery,
            @JsonProperty("lipSync") @JsonAlias("lip-sync") @Nullable LipSyncMode lipSync) {
        return new TtsEngineSpec(
                type,
                voices,
                cloneDiscovery != null && cloneDiscovery,
                lipSync);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=TtsEngineSpecTest`
Expected: PASS

- [ ] **Step 6: Run full agentic-yaml tests**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: All existing tests pass (the `clonedVoices` field name change may break existing serialization tests — fix any failures)

- [ ] **Step 7: Commit**

```bash
git add agentic-yaml/
git commit -m "feat(#519): add LipSyncMode enum and update TtsEngineSpec

Replace List<String> clonedVoices with boolean cloneDiscovery for YAML
boolean deserialization. Add optional LipSyncMode (auto/enabled/disabled)
for per-engine lip-sync control.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## Batch 2: Compiler — AvatarCompiler and engine registries

### Task 3: Implement AvatarCompiler with per-engine-type registries

**Files:**
- Create: `agentic-yaml/.../compiler/AvatarCompiler.java`
- Create: `agentic-yaml/.../compiler/CompiledAvatar.java`
- Test: `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/compiler/AvatarCompilerTest.java`
- Modify: `agentic-yaml/pom.xml` — add compiler + test to JDK 22+ profile exclusion/inclusion

**Interfaces:**
- Consumes: `AvatarDefinition`, `TtsSpec`, `TtsEngineSpec`, `TtsVoiceSpec`, `SttSpec`, `CleanupConfigSpec`, `LipSyncMode`
- Produces: `AvatarCompiler.compile(AvatarDefinition) → CompiledAvatar`
- Produces: `CompiledAvatar` record (`@Nullable TtsModelRegistry ttsModelRegistry`, `@Nullable TextToSpeech defaultTts`, `@Nullable StreamingSpeechToText stt`, `@Nullable CleanupConfig cleanupConfig`)

- [ ] **Step 1: Add AvatarCompiler and test to Maven exclusion list**

Modify `agentic-yaml/pom.xml`: add `<exclude>**/compiler/AvatarCompiler.java</exclude>` and `<exclude>**/compiler/CompiledAvatar.java</exclude>` to the default profile's `maven-compiler-plugin` excludes. The `jdk22+` profile's `combine.self="override"` already removes all excludes.

Add `<exclude>**/compiler/AvatarCompilerTest.java</exclude>` to `testExcludes` in the default profile.

- [ ] **Step 2: Write failing test**

Create `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/compiler/AvatarCompilerTest.java`:

```java
package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.core.yaml.spec.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

class AvatarCompilerTest {

    @Test
    void compiles_vits_engines_to_tts_registry() {
        var spec = new TtsSpec(List.of(
                new TtsEngineSpec("vits", List.of(new TtsVoiceSpec("lessac-medium", null)), false, null)));
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, spec, null, null);

        var result = new AvatarCompiler().compile(def);

        assertThat(result.ttsModelRegistry()).isNotNull();
        assertThat(result.ttsModelRegistry().models()).containsKey("lessac-medium");
        assertThat(result.defaultTts()).isNotNull();
    }

    @Test
    void compiles_stt_whisper_fallback() {
        var sttSpec = new SttSpec("whisper-fallback");
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, null, sttSpec, null);

        var result = new AvatarCompiler().compile(def);

        assertThat(result.stt()).isNotNull();
    }

    @Test
    void compiles_cleanup_with_max_destructiveness() {
        var cleanupSpec = CleanupConfigSpec.fromJson(2);
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, null, null, cleanupSpec);

        var result = new AvatarCompiler().compile(def);

        assertThat(result.cleanupConfig()).isNotNull();
    }

    @Test
    void null_sections_produce_null_fields() {
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, null, null, null);

        var result = new AvatarCompiler().compile(def);

        assertThat(result.ttsModelRegistry()).isNull();
        assertThat(result.defaultTts()).isNull();
        assertThat(result.stt()).isNull();
        assertThat(result.cleanupConfig()).isNull();
    }

    @Test
    void empty_engines_produces_empty_registry_and_null_default() {
        var spec = new TtsSpec(List.of());
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, spec, null, null);

        var result = new AvatarCompiler().compile(def);

        assertThat(result.ttsModelRegistry()).isNotNull();
        assertThat(result.ttsModelRegistry().models()).isEmpty();
        assertThat(result.defaultTts()).isNull();
    }

    @Test
    void compiles_kokoro_voices() {
        var spec = new TtsSpec(List.of(
                new TtsEngineSpec("kokoro", List.of(
                        new TtsVoiceSpec("af_alloy", null),
                        new TtsVoiceSpec("af_bella", null)), false, null)));
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, spec, null, null);

        var result = new AvatarCompiler().compile(def);

        assertThat(result.ttsModelRegistry().models())
                .containsKey("kokoro:af_alloy")
                .containsKey("kokoro:af_bella");
    }

    @Test
    void compiles_sherpa_voices_with_lip_sync_disabled() {
        var spec = new TtsSpec(List.of(
                new TtsEngineSpec("sherpa", List.of(
                        new TtsVoiceSpec("lessac-medium", null)), false, LipSyncMode.DISABLED)));
        var def = new AvatarDefinition("test", "model", null, null, 16000, 0.9, 0.6, spec, null, null);

        var result = new AvatarCompiler().compile(def);

        assertThat(result.ttsModelRegistry().models()).containsKey("sherpa:lessac-medium");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=AvatarCompilerTest -Pjdk22+`
Expected: FAIL — `AvatarCompiler` class doesn't exist

- [ ] **Step 4: Create CompiledAvatar record**

Create `agentic-yaml/src/main/java/io/casehub/agency/core/yaml/compiler/CompiledAvatar.java`:

```java
package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import org.jspecify.annotations.Nullable;

public record CompiledAvatar(
        @Nullable TtsModelRegistry ttsModelRegistry,
        @Nullable TextToSpeech defaultTts,
        @Nullable StreamingSpeechToText stt,
        @Nullable CleanupConfig cleanupConfig) {}
```

- [ ] **Step 5: Implement AvatarCompiler**

Create `agentic-yaml/src/main/java/io/casehub/agency/core/yaml/compiler/AvatarCompiler.java`:

```java
package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.core.yaml.spec.*;
import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextFilter;
import io.casehub.connectors.voice.spi.TextToSpeech;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class AvatarCompiler {

    private static final System.Logger LOG = System.getLogger("avatar-compiler");

    public CompiledAvatar compile(AvatarDefinition definition) {
        TtsModelRegistry ttsRegistry = null;
        TextToSpeech defaultTts = null;
        StreamingSpeechToText stt = null;
        CleanupConfig cleanupConfig = null;

        if (definition.tts() != null) {
            var models = compileTts(definition.tts());
            ttsRegistry = new TtsModelRegistry(models);
            defaultTts = models.isEmpty() ? null : models.values().iterator().next();
        }
        if (definition.stt() != null) {
            stt = compileStt(definition.stt());
        }
        if (definition.cleanup() != null) {
            cleanupConfig = compileCleanup(definition.cleanup());
        }
        return new CompiledAvatar(ttsRegistry, defaultTts, stt, cleanupConfig);
    }

    private Map<String, TextToSpeech> compileTts(TtsSpec spec) {
        var models = new LinkedHashMap<String, TextToSpeech>();
        io.casehub.connectors.voice.spi.PhonemeAligner sharedAligner = null;
        try {
            sharedAligner = io.casehub.connectors.voice.sherpa.EspeakPhonemeAligner.withDefaults();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "EspeakPhonemeAligner unavailable — lip-sync disabled: " + e.getMessage());
        }
        for (var engine : spec.engines()) {
            models.putAll(compileEngine(engine, sharedAligner));
        }
        return models;
    }

    private Map<String, TextToSpeech> compileEngine(
            TtsEngineSpec engine,
            io.casehub.connectors.voice.spi.PhonemeAligner aligner) {
        return switch (engine.type()) {
            case "vits" -> compileVits(engine);
            case "sherpa" -> compileSherpa(engine, aligner);
            case "kokoro" -> compileKokoro(engine, aligner);
            case "qwen3" -> compileQwen3(engine);
            default -> throw new IllegalArgumentException("Unknown TTS engine type: " + engine.type());
        };
    }

    // Per-engine compilation methods — each follows SpeechProducers patterns
    private Map<String, TextToSpeech> compileVits(TtsEngineSpec engine) {
        var result = new LinkedHashMap<String, TextToSpeech>();
        for (var voice : engine.voices()) {
            var model = voice.model();
            var tts = model != null
                    ? io.casehub.connectors.voice.sherpa.VitsTextToSpeech.withDefaults(model)
                    : io.casehub.connectors.voice.sherpa.VitsTextToSpeech.withDefaults();
            result.put(voice.name(), tts);
        }
        return result;
    }

    private Map<String, TextToSpeech> compileSherpa(
            TtsEngineSpec engine,
            io.casehub.connectors.voice.spi.PhonemeAligner aligner) {
        var result = new LinkedHashMap<String, TextToSpeech>();
        boolean wrapLipSync = shouldWrapLipSync(engine.lipSync(), aligner);
        for (var voice : engine.voices()) {
            var model = voice.model();
            TextToSpeech tts = model != null
                    ? io.casehub.connectors.voice.sherpa.SherpaOnnxTextToSpeech.withDefaults(model)
                    : io.casehub.connectors.voice.sherpa.SherpaOnnxTextToSpeech.withDefaults();
            if (wrapLipSync && aligner != null) {
                tts = io.casehub.connectors.voice.spi.LipSyncEnricher.wrap(tts, aligner);
            }
            result.put("sherpa:" + voice.name(), tts);
        }
        return result;
    }

    private Map<String, TextToSpeech> compileKokoro(
            TtsEngineSpec engine,
            io.casehub.connectors.voice.spi.PhonemeAligner aligner) {
        var result = new LinkedHashMap<String, TextToSpeech>();
        var kokoro = io.casehub.connectors.voice.sherpa.KokoroTextToSpeech.withDefaults(0);
        boolean wrapLipSync = shouldWrapLipSync(engine.lipSync(), aligner);
        for (var voice : engine.voices()) {
            int voiceId = resolveKokoroVoiceId(voice.name());
            TextToSpeech tts = kokoro.forVoice(voiceId);
            if (wrapLipSync && aligner != null) {
                tts = io.casehub.connectors.voice.spi.LipSyncEnricher.wrap(tts, aligner);
            }
            result.put("kokoro:" + voice.name(), tts);
        }
        return result;
    }

    private Map<String, TextToSpeech> compileQwen3(TtsEngineSpec engine) {
        var result = new LinkedHashMap<String, TextToSpeech>();
        // Static voices
        for (var voice : engine.voices()) {
            try {
                result.put("qwen3:" + voice.name(),
                        io.casehub.connectors.voice.sherpa.Qwen3TextToSpeech.withDefaults(voice.name()));
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Qwen3-TTS voice '{0}' unavailable: {1}", voice.name(), e.getMessage());
            }
        }
        // Cloned voices
        if (engine.cloneDiscovery()) {
            try {
                var pool = io.casehub.connectors.voice.sherpa.Qwen3VoicePool.createProvisioned(
                        io.casehub.connectors.voice.sherpa.Qwen3VoicePoolConfig.defaults());
                for (String voice : pool.availableVoices()) {
                    result.put("qwen3:clone:" + voice, pool.forVoice(voice));
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Qwen3-TTS voice pool unavailable: " + e.getMessage());
            }
        }
        return result;
    }

    private boolean shouldWrapLipSync(LipSyncMode mode,
                                      io.casehub.connectors.voice.spi.PhonemeAligner aligner) {
        if (mode == null || mode == LipSyncMode.AUTO) return aligner != null;
        if (mode == LipSyncMode.ENABLED) {
            if (aligner == null) throw new IllegalStateException(
                    "lip-sync: enabled but no PhonemeAligner available");
            return true;
        }
        return false; // DISABLED
    }

    private StreamingSpeechToText compileStt(SttSpec spec) {
        return switch (spec.type()) {
            case "whisper" -> io.casehub.connectors.voice.sherpa.WhisperSpeechToText.withDefaults();
            case "zipformer" -> io.casehub.connectors.voice.sherpa.SherpaOnnxStreamingSpeechToText.withDefaults();
            case "whisper-fallback" -> compileWhisperFallback();
            default -> throw new IllegalArgumentException("Unknown STT type: " + spec.type());
        };
    }

    private StreamingSpeechToText compileWhisperFallback() {
        try {
            if (io.casehub.connectors.voice.sherpa.WhisperLibrary.isAvailable()) {
                return io.casehub.connectors.voice.sherpa.WhisperSpeechToText.withDefaults();
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Whisper init failed, falling back: " + e.getMessage());
        }
        return io.casehub.connectors.voice.sherpa.SherpaOnnxStreamingSpeechToText.withDefaults();
    }

    private CleanupConfig compileCleanup(CleanupConfigSpec spec) {
        var filters = new ArrayList<TextFilter>();
        // Filters in order of increasing destructiveness
        filters.add(new io.casehub.connectors.voice.sherpa.FillerRemovalFilter()); // 0
        filters.add(new io.casehub.connectors.voice.sherpa.CasingFilter()); // 1
        if (spec.maxDestructiveness() >= 2) {
            try {
                filters.add(io.casehub.connectors.voice.sherpa.PunctuationFilter.withDefaults());
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "PunctuationFilter unavailable: " + e.getMessage());
            }
        }
        if (spec.maxDestructiveness() >= 3) {
            try {
                filters.add(io.casehub.connectors.voice.sherpa.GectorFilter.withDefaults());
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "GECToR unavailable: " + e.getMessage());
            }
        }
        return CleanupConfig.of(filters.toArray(TextFilter[]::new));
    }

    private static int resolveKokoroVoiceId(String name) {
        // Kokoro voice name → index mapping
        return switch (name) {
            case "af_alloy" -> 0; case "af_aoede" -> 1; case "af_bella" -> 2;
            case "af_heart" -> 3; case "af_jessica" -> 4; case "af_kore" -> 5;
            case "af_nicole" -> 6; case "af_nova" -> 7; case "af_river" -> 8;
            case "af_sarah" -> 9; case "af_sky" -> 10; case "am_adam" -> 11;
            case "am_echo" -> 12; case "am_eric" -> 13; case "am_fenrir" -> 14;
            case "am_liam" -> 15; case "am_michael" -> 16; case "am_onyx" -> 17;
            case "am_puck" -> 18; case "am_santa" -> 19; case "bf_alice" -> 20;
            case "bf_emma" -> 21; case "bf_isabella" -> 22; case "bf_lily" -> 23;
            case "bm_daniel" -> 24; case "bm_fable" -> 25; case "bm_george" -> 26;
            case "bm_lewis" -> 27; case "ef_dora" -> 28; case "em_alex" -> 29;
            case "ff_siwis" -> 30; case "hf_alpha" -> 31; case "hf_beta" -> 32;
            case "hm_omega" -> 33; case "hm_psi" -> 34; case "if_sara" -> 35;
            case "im_nicola" -> 36; case "jf_alpha" -> 37; case "jf_gongitsune" -> 38;
            case "jf_nezumi" -> 39; case "jf_tebukuro" -> 40; case "jm_kumo" -> 41;
            case "pf_dora" -> 42; case "pm_alex" -> 43; case "pm_santa" -> 44;
            case "zf_xiaobei" -> 45; case "zf_xiaoni" -> 46; case "zf_xiaoxiao" -> 47;
            case "zf_xiaoyi" -> 48; case "zm_yunjian" -> 49; case "zm_yunxi" -> 50;
            case "zm_yunxia" -> 51; case "zm_yunyang" -> 52;
            default -> throw new IllegalArgumentException("Unknown Kokoro voice: " + name);
        };
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=AvatarCompilerTest -Pjdk22+`
Expected: PASS (note: some tests may fail if native models aren't provisioned — these tests validate structure, not native calls. Adjust test expectations if engine instantiation requires provisioning.)

- [ ] **Step 7: Run full agentic-yaml tests**

Run: `mvn --batch-mode test -pl agentic-yaml -Pjdk22+`
Expected: All tests pass

- [ ] **Step 8: Commit**

```bash
git add agentic-yaml/
git commit -m "feat(#519): add AvatarCompiler with per-engine-type registries

Compiles AvatarDefinition YAML into runtime speech beans in a single pass.
Supports vits, sherpa, kokoro, and qwen3 engine types with configurable
lip-sync wrapping (auto/enabled/disabled). Follows existing
KokoroConfigRegistry/SherpaConfigRegistry pattern.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 4: Extend deployment processor and recorder

**Files:**
- Modify: `agentic-yaml-deployment/.../deployment/AgenticYamlProcessor.java`
- Modify: `agentic-yaml/.../runtime/AgenticRecorder.java`
- Modify: `agentic-yaml-deployment/pom.xml` — may need `BeanDiscoveryFinishedBuildItem` import

**Interfaces:**
- Consumes: `AvatarConfigBuildItem`, `AvatarCompiler`, `CompiledAvatar`
- Produces: Synthetic CDI beans for `TtsModelRegistry`, `TextToSpeech`, `StreamingSpeechToText`, `CleanupConfig`, `CorrectionHooks`

- [ ] **Step 1: Add recorder methods to AgenticRecorder**

Add methods to `agentic-yaml/src/main/java/io/casehub/agency/core/yaml/runtime/AgenticRecorder.java`:

```java
public RuntimeValue<CompiledAvatar> compileAvatar(AvatarDefinition definition) {
    return new RuntimeValue<>(new AvatarCompiler().compile(definition));
}

public RuntimeValue<TtsModelRegistry> extractTtsModelRegistry(
        RuntimeValue<CompiledAvatar> compiled) {
    return new RuntimeValue<>(compiled.getValue().ttsModelRegistry());
}

public RuntimeValue<TextToSpeech> extractDefaultTts(
        RuntimeValue<CompiledAvatar> compiled) {
    return new RuntimeValue<>(compiled.getValue().defaultTts());
}

public RuntimeValue<StreamingSpeechToText> extractStt(
        RuntimeValue<CompiledAvatar> compiled) {
    return new RuntimeValue<>(compiled.getValue().stt());
}

public RuntimeValue<CleanupConfig> extractCleanup(
        RuntimeValue<CompiledAvatar> compiled) {
    return new RuntimeValue<>(compiled.getValue().cleanupConfig());
}

public java.util.function.Function<io.quarkus.arc.SyntheticCreationalContext<CorrectionHooks>, CorrectionHooks>
        correctionHooksCreator() {
    return ctx -> {
        var corrector = io.quarkus.arc.Arc.container()
                .instance(io.casehub.connectors.voice.sherpa.correction.TranscriptCorrector.class).get();
        var vocabulary = io.quarkus.arc.Arc.container()
                .instance(io.casehub.connectors.voice.sherpa.correction.ConversationVocabulary.class).get();
        return new CorrectionHooks(
                corrector::correct,
                response -> {
                    vocabulary.addFromText(response);
                    corrector.addVocabulary(vocabulary.terms().toArray(String[]::new));
                },
                vocabulary::asPromptHint);
    };
}
```

- [ ] **Step 2: Add build step to AgenticYamlProcessor**

Add a new build step to `agentic-yaml-deployment/src/main/java/io/casehub/agency/core/yaml/deployment/AgenticYamlProcessor.java`:

```java
@BuildStep
@Record(RUNTIME_INIT)
List<SyntheticBeanBuildItem> registerAvatarSpeechBeans(
        AgenticRecorder recorder,
        AvatarConfigBuildItem avatarConfig,
        io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem beanDiscovery) {
    if (avatarConfig.definition() == null) return List.of();

    var def = avatarConfig.definition();
    RuntimeValue<CompiledAvatar> compiled = recorder.compileAvatar(def);
    var beans = new java.util.ArrayList<SyntheticBeanBuildItem>();

    if (def.tts() != null && !hasAppBean(beanDiscovery, TtsModelRegistry.class)) {
        beans.add(SyntheticBeanBuildItem.configure(TtsModelRegistry.class)
                .scope(jakarta.inject.Singleton.class)
                .runtimeValue(recorder.extractTtsModelRegistry(compiled))
                .done());
    }

    if (def.tts() != null && !def.tts().engines().isEmpty()
            && !hasAppBean(beanDiscovery, TextToSpeech.class)) {
        beans.add(SyntheticBeanBuildItem.configure(TextToSpeech.class)
                .scope(jakarta.inject.Singleton.class)
                .runtimeValue(recorder.extractDefaultTts(compiled))
                .done());
    }

    if (def.stt() != null && !hasAppBean(beanDiscovery, StreamingSpeechToText.class)) {
        beans.add(SyntheticBeanBuildItem.configure(StreamingSpeechToText.class)
                .scope(jakarta.inject.Singleton.class)
                .runtimeValue(recorder.extractStt(compiled))
                .done());
    }

    if (def.cleanup() != null && !hasAppBean(beanDiscovery, CleanupConfig.class)) {
        beans.add(SyntheticBeanBuildItem.configure(CleanupConfig.class)
                .scope(jakarta.inject.Singleton.class)
                .runtimeValue(recorder.extractCleanup(compiled))
                .done());
    }

    if (!hasAppBean(beanDiscovery, CorrectionHooks.class)) {
        beans.add(SyntheticBeanBuildItem.configure(CorrectionHooks.class)
                .scope(jakarta.inject.Singleton.class)
                .createWith(recorder.correctionHooksCreator())
                .done());
    }

    return beans;
}

private static boolean hasAppBean(
        io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem beanDiscovery,
        Class<?> beanType) {
    return beanDiscovery.beanStream()
            .withBeanType(beanType)
            .filter(b -> !b.isDefaultBean() && !b.isSynthetic())
            .findAny()
            .isPresent();
}
```

- [ ] **Step 3: Add necessary imports and verify compilation**

Add imports to both files. The recorder needs imports for `CompiledAvatar`, `AvatarCompiler`, `TtsModelRegistry`, `CorrectionHooks`, `TextToSpeech`, `StreamingSpeechToText`, `CleanupConfig`. The processor needs `BeanDiscoveryFinishedBuildItem`, `RuntimeValue`, `CompiledAvatar`, and the speech types.

Run: `mvn --batch-mode compile -pl agentic-yaml,agentic-yaml-deployment -Pjdk22+`
Expected: BUILD SUCCESS

- [ ] **Step 4: Run agentic-yaml-deployment tests**

Run: `mvn --batch-mode test -pl agentic-yaml-deployment -Pjdk22+`
Expected: All existing tests pass

- [ ] **Step 5: Commit**

```bash
git add agentic-yaml/ agentic-yaml-deployment/
git commit -m "feat(#519): extend deployment processor with avatar speech bean registration

Single build step compiles AvatarDefinition once via recorder, registers
synthetic CDI beans for TtsModelRegistry, TextToSpeech, StreamingSpeechToText,
CleanupConfig, and CorrectionHooks. Skips registration when app-level
producer exists (via BeanDiscoveryFinishedBuildItem introspection).

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## Batch 3: Consumer migration — speech-demo

### Task 5: Create avatar.yaml and slim SpeechProducers

**Files:**
- Create: `speech-demo/src/main/resources/META-INF/avatar.yaml`
- Modify: `speech-demo/.../demo/SpeechProducers.java` — remove everything except AgentProvider

**Interfaces:**
- Consumes: Synthetic beans from deployment pipeline (TtsModelRegistry, StreamingSpeechToText, CleanupConfig, CorrectionHooks)

- [ ] **Step 1: Create avatar.yaml**

Create `speech-demo/src/main/resources/META-INF/avatar.yaml`:

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

- [ ] **Step 2: Slim SpeechProducers to AgentProvider only**

Replace `speech-demo/src/main/java/io/casehub/agency/speech/demo/SpeechProducers.java` with:

```java
package io.casehub.agency.speech.demo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class SpeechProducers {

    private static final String DEFAULT_MODEL = "claude-haiku-4-5@20251001";

    @Produces
    @ApplicationScoped
    io.casehub.platform.agent.AgentProvider agentProvider() {
        String region    = System.getenv("CLOUD_ML_REGION");
        String projectId = System.getenv("ANTHROPIC_VERTEX_PROJECT_ID");
        if (region == null || projectId == null) {
            throw new IllegalStateException(
                    "CLOUD_ML_REGION and ANTHROPIC_VERTEX_PROJECT_ID required for Vertex AI");
        }
        var httpClient = java.net.http.HttpClient.newHttpClient();
        var gson       = new com.google.gson.Gson();

        return new io.casehub.platform.agent.AgentProvider() {
            @Override
            public io.smallrye.mutiny.Multi<io.casehub.platform.agent.AgentEvent> invoke(
                    io.casehub.platform.agent.AgentSessionConfig config) {
                return io.smallrye.mutiny.Multi.createFrom().item(() -> {
                    String modelId = config.model() != null ? config.model() : DEFAULT_MODEL;
                    String text = callVertex(httpClient, gson, region, projectId, modelId,
                                             config.systemPrompt(), config.userPrompt());
                    return (io.casehub.platform.agent.AgentEvent)
                                   new io.casehub.platform.agent.AgentEvent.TextDelta(text);
                });
            }

            @Override
            public io.casehub.platform.agent.AgentSession openSession(
                    io.casehub.platform.agent.AgentSessionInit init) {
                throw new UnsupportedOperationException("Use invoke() for avatar");
            }
        };
    }

    private static String callVertex(java.net.http.HttpClient httpClient, com.google.gson.Gson gson,
                                     String region, String projectId, String modelId,
                                     String systemPrompt, String userPrompt) {
        try {
            String token = getAccessToken();
            String url = "https://" + region + "-aiplatform.googleapis.com/v1/projects/"
                         + projectId + "/locations/" + region
                         + "/publishers/anthropic/models/" + modelId + ":rawPredict";

            var body = new com.google.gson.JsonObject();
            body.addProperty("anthropic_version", "vertex-2023-10-16");
            body.addProperty("max_tokens", 80);
            body.addProperty("system", systemPrompt);
            var messages = new com.google.gson.JsonArray();
            var msg      = new com.google.gson.JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", userPrompt);
            messages.add(msg);
            body.add("messages", messages);

            var request = java.net.http.HttpRequest.newBuilder()
                                                   .uri(java.net.URI.create(url))
                                                   .header("Authorization", "Bearer " + token)
                                                   .header("Content-Type", "application/json")
                                                   .POST(java.net.http.HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                                                   .build();

            var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Vertex API error " + response.statusCode() + ": " + response.body());
            }

            var responseJson = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
            return responseJson.getAsJsonArray("content")
                               .get(0).getAsJsonObject()
                               .get("text").getAsString();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Vertex AI call failed: " + e.getMessage(), e);
        }
    }

    private static String getAccessToken() {
        try {
            var credentials = com.google.auth.oauth2.GoogleCredentials.getApplicationDefault()
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get access token via ADC: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn --batch-mode compile -pl speech-demo -Pjdk22+`
Expected: BUILD SUCCESS

- [ ] **Step 4: Verify full build**

Run: `mvn --batch-mode install -Pjdk22+`
Expected: BUILD SUCCESS across all modules

- [ ] **Step 5: File follow-up issues**

File two follow-up issues before closing #519:
1. On `connectors` repo: "Add @DefaultBean producers for speech infrastructure beans (speaker diarization, embedding, transcript correction)" — referenced by D4
2. On `agency` repo: "Update speech-demo index.html to consume /api/avatar/config endpoint" — referenced by issue #519 bullet 5

- [ ] **Step 6: Commit**

```bash
git add speech-demo/
git commit -m "feat(#519): migrate speech-demo to avatar.yaml

Replace 12 hardcoded CDI producers with declarative avatar.yaml.
SpeechProducers.java retains only the Vertex AI AgentProvider producer.
All speech engine beans (TTS, STT, cleanup, correction hooks) are now
produced by the deployment pipeline from YAML configuration.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## References

- [2026-11-19-avatar-yaml-migration-design.md] — design spec this plan implements
- `SpeechProducers.java` — current hardcoded producers being replaced
- `KokoroConfigRegistry.java`, `SherpaConfigRegistry.java` — existing registry pattern
- `AgenticYamlProcessor.java:126-139` — existing `discoverAvatarConfig()` build step
- `AgenticRecorder.java:50-54` — existing `createAvatarConfigBean()` recorder method
- `TtsEngineSpec.java` — spec record being modified
- `CognitionCompiler.java` — existing compiler pattern for reference
- `agentic-yaml/pom.xml:148-196` — JDK 22+ profile and exclusion configuration
- GitHub #519 — focal issue

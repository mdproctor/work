# Avatar Pipeline as Composable Playbook — Design Spec

**Issue:** casehubio/agency#536
**Date:** 2026-11-26
**Status:** Draft

## Overview

Extend `avatar.yaml` with an optional `pipeline:` section that makes the speech pipeline composable. Users can insert custom text-processing steps between the built-in stages (STT, cleanup, LLM, TTS, lip-sync), reorder cleanup passes, and compose multiple filters declaratively. When the `pipeline:` section is absent, the compiler infers the default pipeline from the flat config — fully backward compatible.

The design builds on the existing `TextFilter`/`CleanupConfig` infrastructure from `connectors-voice-spi` rather than creating a parallel text-filtering SPI. Custom text steps implement `TextFilter` directly. Discovery uses a registry pattern (`TextFilterRegistry`) following the established `SummariserRegistry`/`PipelineCompiler` precedent.

## YAML Surface

### Current (unchanged, backward compatible)

```yaml
agent: speech-demo
model: claude-haiku-4-5@20251001
tts-voice: kokoro:af_heart
sample-rate: 16000
speed: 0.9

tts:
  engines:
    - type: kokoro
      voices: [af_heart, am_adam]

stt:
  type: whisper-fallback

cleanup:
  max-destructiveness: 3
```

### Extended (with pipeline section)

```yaml
agent: speech-demo
model: claude-haiku-4-5@20251001
sample-rate: 16000
speed: 0.9

tts:
  engines:
    - type: kokoro
      voices: [af_heart, am_adam]

stt:
  type: whisper-fallback

pipeline:
  post-stt:
    - filler-removal: {}
    - casing: {}
    - punctuation: {}
    - grammar: {}
    - vocabulary-filter:          # custom TextFilter, resolved by registry
        dictionary: medical-terms
  post-llm:
    - profanity-filter: {}        # custom TextFilter
  post-segmenter:
    - emphasis-marker: {}         # custom TextFilter operating per-segment
```

### Semantics

- Steps within each insertion point execute in YAML list order
- Built-in cleanup filters (`filler-removal`, `casing`, `punctuation`, `grammar`) are pre-registered in the `TextFilterRegistry`
- When `pipeline:` is absent, the compiler uses `cleanup.max-destructiveness` to build the default post-STT chain (the current behaviour)
- When `pipeline:` is present, it overrides the `cleanup:` section — the pipeline is the source of truth

## Architecture

### Insertion Points

The speech pipeline has three insertion points, each with different granularity dictated by the streaming architecture:

| Insertion Point | Granularity | Where in Code | What Steps See |
|-----------------|-------------|---------------|----------------|
| `post-stt` | Per-turn | `SpeechSession.handleStop()` line 230 | Full STT transcription text |
| `post-llm` | Per-sentence-callback | `TurnExecutor.processUserTurn()` line 123 | One sentence at a time (streaming path) |
| `post-segmenter` | Per-segment | `TurnExecutor.synthesiseAndSendSegment()` | Individual `ParsedSegment` text |

**Streaming constraint:** In the streaming path, `streamingGenerator.generate()` fires a callback per sentence as the LLM produces it. `segmenter.segment(sentence)` runs inside that callback. Post-LLM steps see one sentence at a time — they cannot see the full turn. This is an explicit design constraint, not a limitation to fix later.

### New Types (in `avatar-yaml`)

```
SpeechPipelineSpec           — record: postStt, postLlm, postSegmenter (all List<StepSpec>)
StepSpec                     — record: name, config (Map<String, Object>)
SpeechPipelineCompiler       — compiles SpeechPipelineSpec → CompiledSpeechPipeline
CompiledSpeechPipeline       — record: postSttChain, postLlmChain, postSegmenterChain (all CleanupConfig)
TextFilterRegistry           — name→factory map (same shape as SummariserRegistry)
```

### TextFilterRegistry

Follows the `SummariserRegistry` pattern:

```java
public class TextFilterRegistry {
    private final Map<String, TextFilterFactory> factories = new ConcurrentHashMap<>();

    public TextFilterRegistry() {
        // Built-in types
        register("filler-removal", config -> new FillerRemovalFilter());
        register("casing", config -> new CasingFilter());
        register("punctuation", config -> PunctuationFilter.withDefaults());
        register("grammar", config -> GectorFilter.withDefaults());
    }

    public void register(String name, TextFilterFactory factory) {
        factories.put(name, factory);
    }

    public TextFilter create(String name, Map<String, Object> config) {
        var factory = factories.get(name);
        if (factory == null) {
            throw new IllegalArgumentException(
                "Unknown text filter: " + name + ". Registered: " + factories.keySet());
        }
        return factory.create(config);
    }
}

@FunctionalInterface
public interface TextFilterFactory {
    TextFilter create(Map<String, Object> config);
}
```

Consumers register custom filters at application startup:

```java
@ApplicationScoped
public class MyFilterRegistrar {
    void onStart(@Observes StartupEvent ev, TextFilterRegistry registry) {
        registry.register("vocabulary-filter", config -> new VocabularyFilter(
            (String) config.get("dictionary")));
    }
}
```

### Compilation Flow

```
avatar.yaml
    │
    ├── AvatarDefinition (existing)
    │       ├── tts → AvatarCompiler.compileTts() → TtsModelRegistry (existing)
    │       ├── stt → AvatarCompiler.compileStt() → StreamingSpeechToText (existing)
    │       └── cleanup → (used only when pipeline: absent)
    │
    └── SpeechPipelineSpec (new, optional)
            └── SpeechPipelineCompiler.compile(spec, registry) → CompiledSpeechPipeline
                    ├── postSttChain → CleanupConfig
                    ├── postLlmChain → CleanupConfig
                    └── postSegmenterChain → CleanupConfig
```

Each chain compiles to a `CleanupConfig` (which is `TextFilter[]` under the hood). This means the existing `SpeechSession.handleStop()` and `TurnExecutor` code paths receive the same type they already use — no changes to the runtime protocol.

### Default Pipeline (backward compatibility)

When `pipeline:` is absent, `SpeechPipelineCompiler` synthesises the default pipeline from `AvatarDefinition.cleanup()`:

```java
public CompiledSpeechPipeline compileDefault(CleanupConfigSpec cleanup) {
    // Replicates current AvatarCompiler.compileCleanup() logic
    var filters = new ArrayList<TextFilter>();
    filters.add(new FillerRemovalFilter());
    filters.add(new CasingFilter());
    if (cleanup.maxDestructiveness() >= 2) {
        filters.add(PunctuationFilter.withDefaults());
    }
    if (cleanup.maxDestructiveness() >= 3) {
        filters.add(GectorFilter.withDefaults());
    }
    return new CompiledSpeechPipeline(
        CleanupConfig.of(filters.toArray(TextFilter[]::new)),
        CleanupConfig.of(),   // empty post-LLM
        CleanupConfig.of());  // empty post-segmenter
}
```

### Runtime Integration

`SpeechWebSocket.onOpen()` and `SpeechSession` receive a `CompiledSpeechPipeline` instead of a single `CleanupConfig`. The changes are surgical:

**SpeechSession.handleStop()** (post-STT):
```java
// Before: String cleanText = ensureTrailingPunctuation(cleanupConfig.apply(raw));
// After:
String cleanText = ensureTrailingPunctuation(pipeline.postSttChain().apply(raw));
```

**TurnExecutor streaming callback** (post-LLM):
```java
responseText = streamingGenerator.generate(prompt, sentence -> {
    String filtered = pipeline.postLlmChain().apply(sentence);  // NEW
    var segments = segmenter.segment(filtered);
    // ... existing segment processing
});
```

**TurnExecutor.synthesiseAndSendSegment()** (post-segmenter):
```java
private void synthesiseAndSendSegment(TextToSpeech tts, ParsedSegment seg, int turnSeq, int idx) {
    String text = pipeline.postSegmenterChain().apply(seg.text());  // NEW
    // ... existing TTS synthesis with filtered text
}
```

### Compile-time Validation

`SpeechPipelineCompiler` validates at compile time:
1. All step names resolve in the `TextFilterRegistry`
2. No step appears in multiple insertion points (a filter instance is per-position)
3. `pipeline:` and `cleanup:` are mutually exclusive — if both present, error with guidance

### Async Side-Effect Steps

Steps that should not block the turn pipeline (logging, analytics, topic detection for context) declare themselves via `TextFilter.destructiveness()` returning a negative value (convention: -1 = async side-effect). The compiled chain runs these on a separate virtual thread and does not wait for their completion. The step receives the text but its return value is ignored — the unmodified text continues through the chain.

This reuses the existing `destructiveness()` method without adding a new interface. Negative destructiveness is currently unused (all built-in filters use 0-3).

## Module Changes

| Module | Changes |
|--------|---------|
| `avatar-yaml` | Add `SpeechPipelineSpec`, `StepSpec` records. Add `SpeechPipelineCompiler`, `CompiledSpeechPipeline`, `TextFilterRegistry`, `TextFilterFactory`. |
| `avatar-yaml` (compiler) | Modify `AvatarCompiler` to compile `pipeline:` section when present, fall back to `compileCleanup()` when absent. |
| `speech-ws` | Modify `SpeechSession` constructor to accept `CompiledSpeechPipeline`. Modify `handleStop()`, `handleText()` to use `postSttChain`. Modify `TurnExecutor` to use `postLlmChain` and `postSegmenterChain`. |
| `speech-ws` (tests) | Add pipeline integration tests. Update existing tests for `CompiledSpeechPipeline`. |
| `connectors-voice-spi` | No changes — `TextFilter` and `CleanupConfig` used as-is. |

## Testing Strategy

1. **Unit tests (avatar-yaml):**
   - `TextFilterRegistry` — register, create, unknown-name error
   - `SpeechPipelineCompiler` — compile explicit pipeline, compile default from cleanup spec, mutual exclusion validation
   - `StepSpec` YAML deserialization — kebab-case, config passthrough

2. **Unit tests (speech-ws):**
   - `SpeechSession` with custom post-STT chain — verify filters apply before LLM
   - `TurnExecutor` with custom post-LLM chain — verify per-sentence filtering in streaming path
   - `TurnExecutor` with custom post-segmenter chain — verify per-segment filtering before TTS
   - Async side-effect step — verify non-blocking, text passes through unmodified

3. **Integration test (Podman):**
   - Deploy speech-demo with custom pipeline in `avatar.yaml`
   - Verify full roundtrip: speak → STT → custom filter → LLM → TTS → audio out
   - Verify backward compatibility: remove `pipeline:` section, verify default behaviour

## Migration Path

1. No breaking changes — `pipeline:` is optional
2. Existing `avatar.yaml` files continue to work without modification
3. Consumers using `CleanupConfig` directly (via CDI injection in `SpeechWebSocket`) continue to work — the pipeline wraps it
4. The `cleanup:` section remains valid as a shorthand for the default post-STT chain

## References

- `connectors-voice-spi: TextFilter` — existing text→text SPI (4 implementations)
- `connectors-voice-spi: CleanupConfig` — existing filter composition with destructiveness ordering
- `avatar-yaml/compiler/AvatarCompiler.java` — existing compilation pattern
- `avatar-yaml/spec/AvatarDefinition.java` — existing YAML spec records
- `summarisation-yaml/SummariserRegistry.java` — registry pattern precedent
- `summarisation-yaml/PipelineCompiler.java` — registry-based compilation precedent
- `speech-ws/SpeechSession.handleStop()` line 230 — post-STT cleanup chain
- `speech-ws/TurnExecutor.processUserTurn()` line 123 — streaming sentence callback
- `speech-ws/UtteranceSegmenter.java` — sentence segmentation
- casehubio/agency#526 — zero-Java avatar self-wiring (predecessor)

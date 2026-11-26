# Avatar Pipeline as Composable Playbook — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #536 — Avatar pipeline as composable playbook
**Issue group:** #536

**Goal:** Add an optional `pipeline:` section to `avatar.yaml` that lets users compose text-processing steps at three insertion points (post-STT, post-LLM, post-segmenter), with a registry for custom `TextFilter` implementations.

**Architecture:** New spec records (`SpeechPipelineSpec`, `StepSpec`) in `avatar-yaml`, compiled by `SpeechPipelineCompiler` into `CompiledSpeechPipeline` using a `TextFilterRegistry`. `FilterChain` preserves YAML insertion order (unlike `CleanupConfig` which sorts by destructiveness). Runtime changes in `speech-ws` wire the compiled pipeline into `SpeechSession` and `TurnExecutor`.

**Tech Stack:** Java 22, Jackson YAML, JUnit 5, AssertJ, Mockito

## Global Constraints

- `TextFilter` interface from `connectors-voice-spi` — no modifications to that module
- `CleanupConfig` used only for backward-compat default post-STT chain (destructiveness-sorted)
- `FilterChain` preserves YAML insertion order — no sorting
- `pipeline:` and `cleanup:` coexist — each insertion point independently defaulted
- All new types in package `io.casehub.agency.core.yaml` (avatar-yaml's package)
- Existing test suites must continue passing — no behavioural changes without pipeline section

---

## Batch 1: Foundation — FilterChain + TextFilterRegistry + spec records

### Task 1: FilterChain — ordered text filter composition

**Files:**
- Create: `avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/FilterChain.java`
- Test: `avatar-yaml/src/test/java/io/casehub/agency/agentic/yaml/compiler/FilterChainTest.java`

**Interfaces:**
- Consumes: `io.casehub.connectors.voice.spi.TextFilter` (from library)
- Produces: `FilterChain` — `of(TextFilter...)`, `apply(String)`, `EMPTY` constant. Used by Task 3 (compiler) and Task 5 (runtime).

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.agency.core.yaml.compiler;

import io.casehub.connectors.voice.spi.TextFilter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FilterChainTest {

    private static TextFilter append(String suffix) {
        return new TextFilter() {
            public String apply(String text) { return text + suffix; }
            public String name() { return "append-" + suffix; }
            public int destructiveness() { return 0; }
        };
    }

    @Test
    void emptyChainReturnsInputUnchanged() {
        assertThat(FilterChain.EMPTY.apply("hello")).isEqualTo("hello");
    }

    @Test
    void singleFilterApplied() {
        var chain = FilterChain.of(append("!"));
        assertThat(chain.apply("hello")).isEqualTo("hello!");
    }

    @Test
    void multipleFiltersApplyInInsertionOrder() {
        var chain = FilterChain.of(append("-A"), append("-B"), append("-C"));
        assertThat(chain.apply("x")).isEqualTo("x-A-B-C");
    }

    @Test
    void orderPreservedRegardlessOfDestructiveness() {
        TextFilter high = new TextFilter() {
            public String apply(String t) { return t + "-HIGH"; }
            public String name() { return "high"; }
            public int destructiveness() { return 10; }
        };
        TextFilter low = new TextFilter() {
            public String apply(String t) { return t + "-LOW"; }
            public String name() { return "low"; }
            public int destructiveness() { return 0; }
        };
        // High before low — FilterChain must NOT sort by destructiveness
        var chain = FilterChain.of(high, low);
        assertThat(chain.apply("x")).isEqualTo("x-HIGH-LOW");
    }

    @Test
    void ofWithCleanupConfigDelegates() {
        var cleanup = io.casehub.connectors.voice.spi.CleanupConfig.of(append("!"));
        var chain = FilterChain.of(cleanup);
        assertThat(chain.apply("hello")).isEqualTo("hello!");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl avatar-yaml -Dtest=FilterChainTest`
Expected: compilation failure — `FilterChain` does not exist

- [ ] **Step 3: Implement FilterChain**

```java
package io.casehub.agency.core.yaml.compiler;

import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.TextFilter;

public record FilterChain(TextFilter[] filters) {

    public static final FilterChain EMPTY = new FilterChain(new TextFilter[0]);

    public String apply(String text) {
        for (TextFilter filter : filters) {
            text = filter.apply(text);
        }
        return text;
    }

    public static FilterChain of(TextFilter... filters) {
        return filters.length == 0 ? EMPTY : new FilterChain(filters.clone());
    }

    public static FilterChain of(CleanupConfig cleanup) {
        return new FilterChain(cleanup.filters().toArray(TextFilter[]::new));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl avatar-yaml -Dtest=FilterChainTest`
Expected: all 5 tests PASS

- [ ] **Step 5: Commit**

```bash
git add avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/FilterChain.java avatar-yaml/src/test/java/io/casehub/agency/agentic/yaml/compiler/FilterChainTest.java
git commit -m "feat(#536): add FilterChain — ordered text filter composition

Preserves YAML insertion order unlike CleanupConfig which sorts by
destructiveness. Includes CleanupConfig bridge for backward compat.

Refs casehubio/agency#536"
```

### Task 2: TextFilterRegistry + TextFilterFactory + SpeechPipelineSpec + StepSpec

**Files:**
- Create: `avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/TextFilterRegistry.java`
- Create: `avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/TextFilterFactory.java`
- Create: `avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/SpeechPipelineSpec.java`
- Create: `avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/StepSpec.java`
- Test: `avatar-yaml/src/test/java/io/casehub/agency/agentic/yaml/compiler/TextFilterRegistryTest.java`
- Test: `avatar-yaml/src/test/java/io/casehub/agency/agentic/yaml/spec/SpeechPipelineSpecTest.java`

**Interfaces:**
- Consumes: `TextFilter` (from library)
- Produces: `TextFilterRegistry` — `register(String, TextFilterFactory)`, `create(String, Map)`, `hasType(String)`. `SpeechPipelineSpec` — `postStt()`, `postLlm()`, `postSegmenter()` (all `@Nullable List<StepSpec>`). `StepSpec` — `name()`, `config()`. Used by Task 3 (compiler).

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.agency.core.yaml.compiler;

import io.casehub.connectors.voice.spi.TextFilter;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class TextFilterRegistryTest {

    private static TextFilter identity() {
        return new TextFilter() {
            public String apply(String t) { return t; }
            public String name() { return "identity"; }
            public int destructiveness() { return 0; }
        };
    }

    @Test
    void createRegisteredFilter() {
        var registry = new TextFilterRegistry();
        registry.register("test-filter", config -> identity());
        var filter = registry.create("test-filter", Map.of());
        assertThat(filter.apply("hello")).isEqualTo("hello");
    }

    @Test
    void createUnknownFilterThrows() {
        var registry = new TextFilterRegistry();
        assertThatThrownBy(() -> registry.create("no-such-filter", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-filter");
    }

    @Test
    void configPassedToFactory() {
        var registry = new TextFilterRegistry();
        registry.register("suffix", config -> {
            String suffix = (String) config.get("suffix");
            return new TextFilter() {
                public String apply(String t) { return t + suffix; }
                public String name() { return "suffix"; }
                public int destructiveness() { return 0; }
            };
        });
        var filter = registry.create("suffix", Map.of("suffix", "!"));
        assertThat(filter.apply("hello")).isEqualTo("hello!");
    }

    @Test
    void hasTypeReturnsCorrectly() {
        var registry = new TextFilterRegistry();
        registry.register("exists", config -> identity());
        assertThat(registry.hasType("exists")).isTrue();
        assertThat(registry.hasType("missing")).isFalse();
    }
}
```

```java
package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SpeechPipelineSpecTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    void deserializesPostSttSteps() throws Exception {
        String yaml = """
                post-stt:
                  - name: filler-removal
                  - name: custom-filter
                    config:
                      dictionary: medical
                """;
        var spec = YAML.readValue(yaml, SpeechPipelineSpec.class);
        assertThat(spec.postStt()).hasSize(2);
        assertThat(spec.postStt().get(0).name()).isEqualTo("filler-removal");
        assertThat(spec.postStt().get(1).name()).isEqualTo("custom-filter");
        assertThat(spec.postStt().get(1).config()).containsEntry("dictionary", "medical");
    }

    @Test
    void nullInsertionPointsForPartialPipeline() throws Exception {
        String yaml = """
                post-llm:
                  - name: profanity-filter
                """;
        var spec = YAML.readValue(yaml, SpeechPipelineSpec.class);
        assertThat(spec.postStt()).isNull();
        assertThat(spec.postLlm()).hasSize(1);
        assertThat(spec.postSegmenter()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl avatar-yaml -Dtest="TextFilterRegistryTest,SpeechPipelineSpecTest"`
Expected: compilation failure — types do not exist

- [ ] **Step 3: Implement types**

`TextFilterFactory.java`:
```java
package io.casehub.agency.core.yaml.compiler;

import io.casehub.connectors.voice.spi.TextFilter;
import java.util.Map;

@FunctionalInterface
public interface TextFilterFactory {
    TextFilter create(Map<String, Object> config);
}
```

`TextFilterRegistry.java`:
```java
package io.casehub.agency.core.yaml.compiler;

import io.casehub.connectors.voice.spi.TextFilter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextFilterRegistry {

    private final Map<String, TextFilterFactory> factories = new ConcurrentHashMap<>();

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

    public boolean hasType(String name) {
        return factories.containsKey(name);
    }
}
```

`StepSpec.java`:
```java
package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record StepSpec(String name, Map<String, Object> config) {
    @JsonCreator
    public StepSpec(@JsonProperty("name") String name,
                    @JsonProperty("config") Map<String, Object> config) {
        this.name = name;
        this.config = config != null ? config : Map.of();
    }
}
```

`SpeechPipelineSpec.java`:
```java
package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import java.util.List;

public record SpeechPipelineSpec(
        @Nullable List<StepSpec> postStt,
        @Nullable List<StepSpec> postLlm,
        @Nullable List<StepSpec> postSegmenter) {

    @JsonCreator
    public SpeechPipelineSpec(
            @JsonProperty("postStt") @JsonAlias("post-stt") @Nullable List<StepSpec> postStt,
            @JsonProperty("postLlm") @JsonAlias("post-llm") @Nullable List<StepSpec> postLlm,
            @JsonProperty("postSegmenter") @JsonAlias("post-segmenter") @Nullable List<StepSpec> postSegmenter) {
        this.postStt = postStt;
        this.postLlm = postLlm;
        this.postSegmenter = postSegmenter;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl avatar-yaml -Dtest="TextFilterRegistryTest,SpeechPipelineSpecTest"`
Expected: all tests PASS

- [ ] **Step 5: Commit**

```bash
git add avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/TextFilterRegistry.java avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/TextFilterFactory.java avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/SpeechPipelineSpec.java avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/StepSpec.java avatar-yaml/src/test/java/io/casehub/agency/agentic/yaml/compiler/TextFilterRegistryTest.java avatar-yaml/src/test/java/io/casehub/agency/agentic/yaml/spec/SpeechPipelineSpecTest.java
git commit -m "feat(#536): add TextFilterRegistry, StepSpec, SpeechPipelineSpec

Registry follows SummariserRegistry pattern — name-to-factory mapping.
Spec records support partial pipeline with nullable insertion points.

Refs casehubio/agency#536"
```

### Task 3: SpeechPipelineCompiler + CompiledSpeechPipeline

**Files:**
- Create: `avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/CompiledSpeechPipeline.java`
- Create: `avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/SpeechPipelineCompiler.java`
- Modify: `avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/CompiledAvatar.java` — add `CompiledSpeechPipeline` field
- Modify: `avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/AvatarDefinition.java` — add `@Nullable SpeechPipelineSpec pipeline` field
- Modify: `avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/AvatarCompiler.java` — compile pipeline section
- Test: `avatar-yaml/src/test/java/io/casehub/agency/agentic/yaml/compiler/SpeechPipelineCompilerTest.java`

**Interfaces:**
- Consumes: `FilterChain` (Task 1), `TextFilterRegistry` (Task 2), `SpeechPipelineSpec` (Task 2), `CleanupConfigSpec`
- Produces: `CompiledSpeechPipeline` — `postSttChain()`, `postLlmChain()`, `postSegmenterChain()` (all `FilterChain`), `EMPTY` constant. `SpeechPipelineCompiler` — `compile(SpeechPipelineSpec, TextFilterRegistry)`, `compileDefault(CleanupConfigSpec)`. Used by Task 4 (deployment) and Task 5 (runtime).

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.core.yaml.spec.CleanupConfigSpec;
import io.casehub.agency.core.yaml.spec.SpeechPipelineSpec;
import io.casehub.agency.core.yaml.spec.StepSpec;
import io.casehub.connectors.voice.spi.TextFilter;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class SpeechPipelineCompilerTest {

    private static TextFilter suffix(String s) {
        return new TextFilter() {
            public String apply(String t) { return t + s; }
            public String name() { return "suffix-" + s; }
            public int destructiveness() { return 0; }
        };
    }

    private TextFilterRegistry registry() {
        var r = new TextFilterRegistry();
        r.register("upper", config -> new TextFilter() {
            public String apply(String t) { return t.toUpperCase(); }
            public String name() { return "upper"; }
            public int destructiveness() { return 0; }
        });
        r.register("exclaim", config -> suffix("!"));
        return r;
    }

    @Test
    void compilesExplicitPostSttChain() {
        var spec = new SpeechPipelineSpec(
                List.of(new StepSpec("upper", Map.of()), new StepSpec("exclaim", Map.of())),
                null, null);
        var pipeline = new SpeechPipelineCompiler().compile(spec, registry());
        assertThat(pipeline.postSttChain().apply("hello")).isEqualTo("HELLO!");
        assertThat(pipeline.postLlmChain().apply("hello")).isEqualTo("hello");
        assertThat(pipeline.postSegmenterChain().apply("hello")).isEqualTo("hello");
    }

    @Test
    void compilesPartialPipeline() {
        var spec = new SpeechPipelineSpec(
                null,
                List.of(new StepSpec("exclaim", Map.of())),
                null);
        var pipeline = new SpeechPipelineCompiler().compile(spec, registry());
        assertThat(pipeline.postSttChain().apply("hello")).isEqualTo("hello");
        assertThat(pipeline.postLlmChain().apply("hello")).isEqualTo("hello!");
    }

    @Test
    void unknownStepNameThrows() {
        var spec = new SpeechPipelineSpec(
                List.of(new StepSpec("no-such-filter", Map.of())),
                null, null);
        assertThatThrownBy(() -> new SpeechPipelineCompiler().compile(spec, registry()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-filter");
    }

    @Test
    void compileDefaultFromCleanupSpec() {
        var pipeline = new SpeechPipelineCompiler().compileDefault(new CleanupConfigSpec(1));
        assertThat(pipeline.postSttChain()).isNotNull();
        assertThat(pipeline.postLlmChain().apply("x")).isEqualTo("x");
        assertThat(pipeline.postSegmenterChain().apply("x")).isEqualTo("x");
    }

    @Test
    void emptyPipelineIsIdentity() {
        assertThat(CompiledSpeechPipeline.EMPTY.postSttChain().apply("hi")).isEqualTo("hi");
        assertThat(CompiledSpeechPipeline.EMPTY.postLlmChain().apply("hi")).isEqualTo("hi");
        assertThat(CompiledSpeechPipeline.EMPTY.postSegmenterChain().apply("hi")).isEqualTo("hi");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl avatar-yaml -Dtest=SpeechPipelineCompilerTest`
Expected: compilation failure — types do not exist

- [ ] **Step 3: Implement CompiledSpeechPipeline and SpeechPipelineCompiler**

`CompiledSpeechPipeline.java`:
```java
package io.casehub.agency.core.yaml.compiler;

public record CompiledSpeechPipeline(
        FilterChain postSttChain,
        FilterChain postLlmChain,
        FilterChain postSegmenterChain) {

    public static final CompiledSpeechPipeline EMPTY =
            new CompiledSpeechPipeline(FilterChain.EMPTY, FilterChain.EMPTY, FilterChain.EMPTY);
}
```

`SpeechPipelineCompiler.java`:
```java
package io.casehub.agency.core.yaml.compiler;

import io.casehub.agency.core.yaml.spec.CleanupConfigSpec;
import io.casehub.agency.core.yaml.spec.SpeechPipelineSpec;
import io.casehub.agency.core.yaml.spec.StepSpec;
import io.casehub.connectors.voice.spi.TextFilter;
import org.jspecify.annotations.Nullable;
import java.util.List;

public class SpeechPipelineCompiler {

    public CompiledSpeechPipeline compile(SpeechPipelineSpec spec, TextFilterRegistry registry) {
        return new CompiledSpeechPipeline(
                compileChain(spec.postStt(), registry),
                compileChain(spec.postLlm(), registry),
                compileChain(spec.postSegmenter(), registry));
    }

    public CompiledSpeechPipeline compileDefault(CleanupConfigSpec cleanup) {
        FilterChain postStt = FilterChain.of(AvatarCompiler.compileCleanup(cleanup));
        return new CompiledSpeechPipeline(postStt, FilterChain.EMPTY, FilterChain.EMPTY);
    }

    private FilterChain compileChain(@Nullable List<StepSpec> steps, TextFilterRegistry registry) {
        if (steps == null || steps.isEmpty()) return FilterChain.EMPTY;
        TextFilter[] filters = steps.stream()
                .map(step -> registry.create(step.name(), step.config()))
                .toArray(TextFilter[]::new);
        return FilterChain.of(filters);
    }
}
```

- [ ] **Step 4: Modify AvatarDefinition to add pipeline field**

Add `@Nullable SpeechPipelineSpec pipeline` to `AvatarDefinition` record and `fromJson` factory.

- [ ] **Step 5: Modify CompiledAvatar to add pipeline field**

Add `@Nullable CompiledSpeechPipeline pipeline` to `CompiledAvatar` record.

- [ ] **Step 6: Modify AvatarCompiler to compile pipeline**

In `AvatarCompiler.compile()`, after existing compilation, add:

```java
CompiledSpeechPipeline pipeline;
if (definition.pipeline() != null) {
    var registry = new TextFilterRegistry();
    // Register built-in filters
    registerBuiltinFilters(registry);
    var pipelineCompiler = new SpeechPipelineCompiler();
    pipeline = pipelineCompiler.compile(definition.pipeline(), registry);
    // For undeclared positions, fall back to cleanup default
    if (definition.pipeline().postStt() == null && definition.cleanup() != null) {
        pipeline = new CompiledSpeechPipeline(
                FilterChain.of(compileCleanup(definition.cleanup())),
                pipeline.postLlmChain(),
                pipeline.postSegmenterChain());
    }
} else if (definition.cleanup() != null) {
    pipeline = new SpeechPipelineCompiler().compileDefault(definition.cleanup());
} else {
    pipeline = CompiledSpeechPipeline.EMPTY;
}
```

Make `compileCleanup()` static (it's currently private instance — needs to be accessible from `SpeechPipelineCompiler.compileDefault()`).

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl avatar-yaml`
Expected: all tests PASS (new + existing)

- [ ] **Step 8: Commit**

```bash
git add avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/CompiledSpeechPipeline.java avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/SpeechPipelineCompiler.java avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/CompiledAvatar.java avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/AvatarDefinition.java avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/compiler/AvatarCompiler.java avatar-yaml/src/test/java/io/casehub/agency/agentic/yaml/compiler/SpeechPipelineCompilerTest.java
git commit -m "feat(#536): add SpeechPipelineCompiler with partial pipeline support

Compiles SpeechPipelineSpec into CompiledSpeechPipeline with FilterChain
per insertion point. Undeclared positions fall back to cleanup default.
AvatarDefinition and CompiledAvatar extended with pipeline field.

Refs casehubio/agency#536"
```

## Batch 2: Runtime wiring — deployment + speech-ws integration

### Task 4: Deployment module — register CompiledSpeechPipeline as CDI bean

**Files:**
- Modify: `avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AvatarRecorder.java` — add `extractPipeline()`
- Modify: `speech-deployment/src/main/java/io/casehub/agency/speech/deployment/SpeechAvatarProcessor.java` — register `CompiledSpeechPipeline` bean
- Modify: `speech-deployment/src/test/resources/META-INF/avatar.yaml` — add pipeline section to test fixture
- Modify: `speech-deployment/src/test/java/io/casehub/agency/speech/deployment/SpeechAvatarProcessorTest.java` — test pipeline parsing

**Interfaces:**
- Consumes: `CompiledAvatar.pipeline()` (Task 3), `CompiledSpeechPipeline` (Task 3)
- Produces: CDI synthetic bean of type `CompiledSpeechPipeline` available for injection in `SpeechWebSocket`

- [ ] **Step 1: Write failing test — avatar.yaml with pipeline section parses correctly**

Add to `SpeechAvatarProcessorTest`:
```java
@Test
void discoversAvatarYamlWithPipelineSection() throws Exception {
    try (InputStream in = getClass().getClassLoader()
            .getResourceAsStream("META-INF/avatar-with-pipeline.yaml")) {
        assertThat(in).isNotNull();
        var def = YAML.readValue(in, AvatarDefinition.class);
        assertThat(def.pipeline()).isNotNull();
        assertThat(def.pipeline().postLlm()).hasSize(1);
        assertThat(def.pipeline().postLlm().get(0).name()).isEqualTo("test-filter");
        assertThat(def.pipeline().postStt()).isNull();
    }
}
```

Create test fixture `speech-deployment/src/test/resources/META-INF/avatar-with-pipeline.yaml`:
```yaml
agent: test-avatar
model: claude-haiku-4-5@20251001
sample-rate: 16000
speed: 0.9

tts:
  engines:
    - type: kokoro
      voices:
        - af_heart

stt:
  type: whisper-fallback

cleanup:
  max-destructiveness: 2

pipeline:
  post-llm:
    - name: test-filter
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl speech-deployment -Dtest="SpeechAvatarProcessorTest#discoversAvatarYamlWithPipelineSection"`
Expected: FAIL — `pipeline()` method does not exist on `AvatarDefinition` (until Task 3 is installed)

- [ ] **Step 3: Add extractPipeline to AvatarRecorder**

```java
public RuntimeValue<CompiledSpeechPipeline> extractPipeline(
        RuntimeValue<CompiledAvatar> compiled) {
    var pipeline = compiled.getValue().pipeline();
    return new RuntimeValue<>(pipeline != null ? pipeline : CompiledSpeechPipeline.EMPTY);
}
```

- [ ] **Step 4: Register CompiledSpeechPipeline bean in SpeechAvatarProcessor**

Add to `registerAvatarSpeechBeans()`:
```java
boolean needsPipeline = !hasAppBean(beanDiscovery, CompiledSpeechPipeline.class);

if (needsPipeline) {
    beans.add(SyntheticBeanBuildItem.configure(CompiledSpeechPipeline.class)
            .scope(Singleton.class)
            .setRuntimeInit()
            .runtimeValue(recorder.extractPipeline(compiled))
            .done());
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl speech-deployment`
Expected: all tests PASS

- [ ] **Step 6: Commit**

```bash
git add avatar-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AvatarRecorder.java speech-deployment/src/main/java/io/casehub/agency/speech/deployment/SpeechAvatarProcessor.java speech-deployment/src/test/resources/META-INF/avatar-with-pipeline.yaml speech-deployment/src/test/java/io/casehub/agency/speech/deployment/SpeechAvatarProcessorTest.java
git commit -m "feat(#536): register CompiledSpeechPipeline as synthetic CDI bean

AvatarRecorder.extractPipeline() extracts from CompiledAvatar.
SpeechAvatarProcessor registers it alongside existing beans.

Refs casehubio/agency#536"
```

### Task 5: Wire pipeline into SpeechSession + TurnExecutor

**Files:**
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/TurnExecutor.java` — add `CompiledSpeechPipeline` field, apply post-LLM and post-segmenter chains
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechSession.java` — replace `CleanupConfig` with `CompiledSpeechPipeline`, apply post-STT chain in `handleStop()` and `handleText()`
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechWebSocket.java` — inject `CompiledSpeechPipeline`, pass to `SpeechSession`
- Modify: `speech-ws/src/test/java/io/casehub/agency/speech/ws/SpeechSessionTest.java` — update constructors, add pipeline tests
- Modify: `speech-ws/src/test/java/io/casehub/agency/speech/ws/TurnExecutorTest.java` — update constructors, add pipeline tests

**Interfaces:**
- Consumes: `CompiledSpeechPipeline` (Task 3), `FilterChain` (Task 1)
- Produces: Runtime integration — no new public API

- [ ] **Step 1: Write failing test — SpeechSession post-STT chain applies in handleStop**

Add to `SpeechSessionTest`:
```java
@Test
void handleStopAppliesPostSttPipelineChain() {
    // Create a pipeline with an uppercasing post-STT filter
    TextFilter upper = new TextFilter() {
        public String apply(String t) { return t.toUpperCase(); }
        public String name() { return "upper"; }
        public int destructiveness() { return 0; }
    };
    var pipeline = new CompiledSpeechPipeline(
            FilterChain.of(upper), FilterChain.EMPTY, FilterChain.EMPTY);
    // Construct SpeechSession with pipeline instead of CleanupConfig
    // ... verify that after handleStop, the transcript message contains uppercased text
}
```

- [ ] **Step 2: Write failing test — TurnExecutor post-LLM chain applies per sentence**

Add to `TurnExecutorTest`:
```java
@Test
void streamingCallbackAppliesPostLlmChain() {
    // Create pipeline with post-LLM filter that adds "!" suffix
    // Verify each sentence callback result has the suffix applied before segmentation
}
```

- [ ] **Step 3: Modify TurnExecutor — add pipeline field and apply chains**

Add `CompiledSpeechPipeline pipeline` as a constructor parameter. In `processUserTurn()` streaming callback, apply `pipeline.postLlmChain().apply(sentence)` before `segmenter.segment()`. In `synthesiseAndSendSegment()`, apply `pipeline.postSegmenterChain().apply(seg.text())` before TTS synthesis.

- [ ] **Step 4: Modify SpeechSession — replace CleanupConfig with CompiledSpeechPipeline**

Replace the `cleanupConfig` field with `pipeline` of type `CompiledSpeechPipeline`. In `handleStop()`, replace `cleanupConfig.apply(raw)` with `pipeline.postSttChain().apply(raw)`. In `handleText()`, same replacement. Pass `pipeline` to `TurnExecutor` constructor.

Update all constructor overloads to accept `CompiledSpeechPipeline` instead of `CleanupConfig`.

- [ ] **Step 5: Modify SpeechWebSocket — inject and pass pipeline**

Replace `@Inject CleanupConfig cleanupConfig` with `@Inject CompiledSpeechPipeline pipeline`. Pass `pipeline` to `SpeechSession` constructor instead of `cleanupConfig`.

- [ ] **Step 6: Update all existing tests**

Update `SpeechSessionTest` and `TurnExecutorTest` constructors to pass `CompiledSpeechPipeline` wrapping the existing `CleanupConfig` via `FilterChain.of(cleanupConfig)`. All existing tests must pass with no behavioural change.

- [ ] **Step 7: Run all speech-ws tests**

Run: `mvn --batch-mode test -pl speech-ws`
Expected: all tests PASS (existing + new)

- [ ] **Step 8: Run full project build**

Run: `mvn --batch-mode install -DskipTests && mvn --batch-mode test`
Expected: full build + all tests pass

- [ ] **Step 9: Commit**

```bash
git add speech-ws/src/main/java/io/casehub/agency/speech/ws/TurnExecutor.java speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechSession.java speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechWebSocket.java speech-ws/src/test/java/io/casehub/agency/speech/ws/SpeechSessionTest.java speech-ws/src/test/java/io/casehub/agency/speech/ws/TurnExecutorTest.java
git commit -m "feat(#536): wire CompiledSpeechPipeline into speech-ws runtime

SpeechSession uses postSttChain in handleStop/handleText.
TurnExecutor applies postLlmChain per-sentence and postSegmenterChain
per-segment. All existing tests pass with backward-compat pipeline.

Refs casehubio/agency#536"
```

## Batch 3: Podman verification + documentation

### Task 6: Podman integration test + doc update

**Files:**
- Modify: `speech-demo/src/main/resources/META-INF/avatar.yaml` — add pipeline section for demo
- Modify: `docs/guides/consumer-guide.md` — document pipeline section

**Interfaces:**
- Consumes: All prior tasks
- Produces: Verified working system in Podman container

- [ ] **Step 1: Add pipeline section to speech-demo avatar.yaml**

Add a `pipeline:` section with a `post-llm` step to demonstrate the feature. Use a built-in filter that's already registered (e.g. `casing`):
```yaml
pipeline:
  post-llm:
    - name: casing
```

- [ ] **Step 2: Build and deploy to Podman**

```bash
podman stop speech-demo && podman rm speech-demo
mvn --batch-mode clean install -DskipTests
mvn --batch-mode clean package -pl speech-demo -DskipTests -Dquarkus.package.jar.type=uber-jar
podman build --no-cache --layers=false -t speech-demo -f speech-demo/Containerfile speech-demo
podman run -d --name speech-demo -p 8099:8080 \
  -e CLAUDE_CODE_USE_VERTEX=1 -e CLOUD_ML_REGION=us-east5 \
  -e ANTHROPIC_VERTEX_PROJECT_ID=itpc-gcp-cp-pe-eng-claude \
  -v ~/.config/gcloud:/home/avatar/.config/gcloud:ro speech-demo
```

- [ ] **Step 3: Verify in browser**

Navigate to `http://localhost:8099`. Speak a message. Verify:
1. STT transcription appears
2. LLM response generates
3. TTS audio plays
4. No errors in `podman logs speech-demo`

- [ ] **Step 4: Verify backward compatibility**

Remove `pipeline:` section from avatar.yaml, rebuild and redeploy. Verify same behaviour as before.

- [ ] **Step 5: Restore pipeline section and update consumer guide**

Add a "Pipeline Composition" section to `docs/guides/consumer-guide.md` documenting the `pipeline:` YAML surface, insertion points, and how to register custom `TextFilter` implementations.

- [ ] **Step 6: Commit**

```bash
git add speech-demo/src/main/resources/META-INF/avatar.yaml docs/guides/consumer-guide.md
git commit -m "feat(#536): add pipeline demo config and consumer guide docs

Demonstrates post-llm pipeline step in speech-demo avatar.yaml.
Documents pipeline YAML surface and custom filter registration.

Refs casehubio/agency#536"
```

## References

- [2026-11-26-avatar-pipeline-design.md] — design spec this plan implements
- `connectors-voice-spi: TextFilter` — existing text filter SPI
- `connectors-voice-spi: CleanupConfig` — existing destructiveness-sorted composition
- `avatar-yaml/compiler/AvatarCompiler.java` — existing avatar compilation
- `avatar-yaml/compiler/CompiledAvatar.java` — existing compiled avatar record
- `avatar-yaml/runtime/AvatarRecorder.java` — Quarkus recorder for avatar beans
- `speech-deployment/SpeechAvatarProcessor.java` — deployment-time CDI bean registration
- `speech-ws/SpeechSession.java` — runtime session with post-STT cleanup
- `speech-ws/TurnExecutor.java` — runtime turn execution with streaming TTS
- `speech-ws/SpeechWebSocket.java` — WebSocket endpoint wiring
- `summarisation-yaml/SummariserRegistry.java` — registry pattern precedent
- GitHub #536 — Avatar pipeline as composable playbook
- GitHub #526 — Zero-Java avatar self-wiring (predecessor)

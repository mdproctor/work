# Zero-Java Avatar Self-Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #526 — Zero-Java avatar: self-wiring Quarkus extension for speech pipeline
**Issue group:** #526

**Goal:** Enable zero-Java avatar deployment — consumer adds dependencies, drops config files, gets a fully working speech pipeline with no Java code.

**Architecture:** New `speech-deployment` Quarkus extension module discovers `META-INF/avatar.yaml` and registers TTS/STT/cleanup beans at build time, with agency-core excluded from the dependency tree. Infrastructure classes relocate from speech-demo to speech-ws. LLM integration via eidos descriptors + agent-config layered credentials. Frontend extracted into a reusable ES module.

**Tech Stack:** Java 22, Quarkus deployment extensions, Maven, sherpa-onnx FFM/Panama, eidos agent descriptors, ES modules

## Global Constraints

- JDK 22+ required (FFM/Panama for sherpa-onnx native bindings)
- `casehub-agency-core` must NOT appear in `speech-deployment`'s resolved dependency tree
- Existing `agentic-yaml-deployment` build steps remain unchanged — speech-deployment operates independently
- All package declarations use `io.casehub.agency.speech.deployment` for the new module
- Maven artifact: `casehub-agency-speech-deployment`
- Existing consumers of `agentic-yaml-deployment` are unaffected

---

## Batch 1: speech-deployment module compiles and registers beans

### Task 1: Create speech-deployment module with pom.xml and build items

**Files:**
- Create: `speech-deployment/pom.xml`
- Create: `speech-deployment/src/main/java/io/casehub/agency/speech/deployment/SpeechAvatarConfigBuildItem.java`
- Modify: `pom.xml:40-49` (add `speech-deployment` to jdk22+ profile modules)

**Interfaces:**
- Produces: `SpeechAvatarConfigBuildItem` — `SimpleBuildItem` carrying `@Nullable AvatarDefinition definition()`

- [ ] **Step 1: Create speech-deployment pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-agency-parent</artifactId>
    <version>0.4-SNAPSHOT</version>
  </parent>

  <artifactId>casehub-agency-speech-deployment</artifactId>
  <name>casehub-agency-speech-deployment</name>
  <description>Quarkus deployment extension for avatar speech pipeline — zero-Java self-wiring</description>

  <dependencies>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-agency-agentic-yaml</artifactId>
      <version>${project.version}</version>
      <exclusions>
        <exclusion>
          <groupId>io.casehub</groupId>
          <artifactId>casehub-agency-core</artifactId>
        </exclusion>
      </exclusions>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-agency-speech-api</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-connectors-voice-sherpa</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-platform-yaml-jackson</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-arc-deployment</artifactId>
    </dependency>
    <dependency>
      <groupId>io.smallrye</groupId>
      <artifactId>jandex</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jspecify</groupId>
      <artifactId>jspecify</artifactId>
    </dependency>

    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <annotationProcessorPaths>
            <path>
              <groupId>io.quarkus</groupId>
              <artifactId>quarkus-extension-processor</artifactId>
              <version>${version.quarkus.platform}</version>
            </path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Create SpeechAvatarConfigBuildItem**

```java
package io.casehub.agency.speech.deployment;

import io.casehub.agency.core.yaml.spec.AvatarDefinition;
import io.quarkus.builder.item.SimpleBuildItem;
import org.jspecify.annotations.Nullable;

public final class SpeechAvatarConfigBuildItem extends SimpleBuildItem {

    private final @Nullable AvatarDefinition definition;

    public SpeechAvatarConfigBuildItem(@Nullable AvatarDefinition definition) {
        this.definition = definition;
    }

    public @Nullable AvatarDefinition definition() {
        return definition;
    }
}
```

- [ ] **Step 3: Add speech-deployment to parent pom jdk22+ profile**

In `pom.xml`, inside the `<profile><id>jdk22+</id>` block, add `<module>speech-deployment</module>` before `<module>speech-demo</module>`.

- [ ] **Step 4: Verify module compiles**

Run: `mvn --batch-mode compile -pl speech-deployment -f pom.xml`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add speech-deployment/ pom.xml
git commit -m "feat(#526): create speech-deployment module with build item

New Quarkus deployment module for zero-Java avatar self-wiring.
Depends on agentic-yaml with agency-core excluded.

Refs #526"
```

---

### Task 2: Add SpeechAvatarProcessor with YAML discovery and bean registration

**Files:**
- Create: `speech-deployment/src/main/java/io/casehub/agency/speech/deployment/SpeechAvatarProcessor.java`
- Test: `speech-deployment/src/test/java/io/casehub/agency/speech/deployment/SpeechAvatarProcessorTest.java`

**Interfaces:**
- Consumes: `SpeechAvatarConfigBuildItem` from Task 1, `AvatarRecorder` from `agentic-yaml` runtime module
- Produces: `List<SyntheticBeanBuildItem>` for TtsModelRegistry, TextToSpeech, StreamingSpeechToText, CleanupConfig, CorrectionHooks; `SyntheticBeanBuildItem` for AvatarConfigBean; `AdditionalBeanBuildItem` for AvatarConfigResource

- [ ] **Step 1: Write failing test for avatar YAML discovery**

```java
package io.casehub.agency.speech.deployment;

import io.casehub.agency.core.yaml.spec.AvatarDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechAvatarProcessorTest {

    @Test
    void discoversAvatarYamlFromClasspath() throws Exception {
        var processor = new SpeechAvatarProcessor();
        var buildItem = processor.discoverAvatarConfig();
        assertThat(buildItem.definition()).isNotNull();
        assertThat(buildItem.definition().agent()).isEqualTo("test-avatar");
        assertThat(buildItem.definition().sampleRate()).isEqualTo(16000);
    }

    @Test
    void returnsNullDefinitionWhenNoYaml() throws Exception {
        // No avatar.yaml on test classpath for this test —
        // the first test uses a test-scoped avatar.yaml resource
        // This test verifies the null-safe path in the processor
        var buildItem = new SpeechAvatarConfigBuildItem(null);
        assertThat(buildItem.definition()).isNull();
    }
}
```

Create test resource: `speech-deployment/src/test/resources/META-INF/avatar.yaml`

```yaml
agent: test-avatar
sample-rate: 16000
speed: 0.9
min-playback-rate: 0.6
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl speech-deployment -Dtest=SpeechAvatarProcessorTest`
Expected: FAIL — `SpeechAvatarProcessor` class does not exist

- [ ] **Step 3: Implement SpeechAvatarProcessor**

```java
package io.casehub.agency.speech.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.core.yaml.compiler.CompiledAvatar;
import io.casehub.agency.core.yaml.runtime.AvatarConfigBean;
import io.casehub.agency.core.yaml.runtime.AvatarConfigResource;
import io.casehub.agency.core.yaml.runtime.AvatarRecorder;
import io.casehub.agency.core.yaml.spec.AvatarDefinition;
import io.casehub.agency.speech.CorrectionHooks;
import io.casehub.agency.speech.TtsModelRegistry;
import io.casehub.connectors.voice.spi.CleanupConfig;
import io.casehub.connectors.voice.spi.StreamingSpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.yaml.jackson.YamlMappers;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.runtime.RuntimeValue;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

public class SpeechAvatarProcessor {

    private static final ObjectMapper YAML = YamlMappers.create();
    private static final ObjectMapper JSON = new ObjectMapper();

    @BuildStep
    SpeechAvatarConfigBuildItem discoverAvatarConfig() throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL resource = cl.getResource("META-INF/avatar.yaml");
        if (resource == null) {
            resource = cl.getResource("META-INF/avatar.yml");
        }
        if (resource == null) {
            return new SpeechAvatarConfigBuildItem(null);
        }
        try (InputStream in = resource.openStream()) {
            var definition = YAML.readValue(in, AvatarDefinition.class);
            return new SpeechAvatarConfigBuildItem(definition);
        }
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    List<SyntheticBeanBuildItem> registerAvatarSpeechBeans(
            AvatarRecorder recorder,
            SpeechAvatarConfigBuildItem avatarConfig,
            BeanDiscoveryFinishedBuildItem beanDiscovery) {
        if (avatarConfig.definition() == null) return List.of();

        var def = avatarConfig.definition();
        var beans = new ArrayList<SyntheticBeanBuildItem>();

        boolean needsTts = def.tts() != null && !hasAppBean(beanDiscovery, TtsModelRegistry.class);
        boolean needsDefaultTts = def.tts() != null && !def.tts().engines().isEmpty()
                && !hasAppBean(beanDiscovery, TextToSpeech.class);
        boolean needsStt = def.stt() != null && !hasAppBean(beanDiscovery, StreamingSpeechToText.class);
        boolean needsCleanup = def.cleanup() != null && !hasAppBean(beanDiscovery, CleanupConfig.class);
        boolean needsHooks = !hasAppBean(beanDiscovery, CorrectionHooks.class);

        if (!needsTts && !needsDefaultTts && !needsStt && !needsCleanup && !needsHooks) {
            return List.of();
        }

        RuntimeValue<CompiledAvatar> compiled = recorder.compileAvatar(def);

        if (needsTts) {
            beans.add(SyntheticBeanBuildItem.configure(TtsModelRegistry.class)
                    .scope(Singleton.class)
                    .runtimeValue(recorder.extractTtsModelRegistry(compiled))
                    .done());
        }
        if (needsDefaultTts) {
            beans.add(SyntheticBeanBuildItem.configure(TextToSpeech.class)
                    .scope(Singleton.class)
                    .runtimeValue(recorder.extractDefaultTts(compiled))
                    .done());
        }
        if (needsStt) {
            beans.add(SyntheticBeanBuildItem.configure(StreamingSpeechToText.class)
                    .scope(Singleton.class)
                    .runtimeValue(recorder.extractStt(compiled))
                    .done());
        }
        if (needsCleanup) {
            beans.add(SyntheticBeanBuildItem.configure(CleanupConfig.class)
                    .scope(Singleton.class)
                    .runtimeValue(recorder.extractCleanup(compiled))
                    .done());
        }
        if (needsHooks) {
            beans.add(SyntheticBeanBuildItem.configure(CorrectionHooks.class)
                    .scope(Singleton.class)
                    .createWith(recorder.correctionHooksCreator())
                    .done());
        }

        return beans;
    }

    @BuildStep
    @Record(STATIC_INIT)
    SyntheticBeanBuildItem registerAvatarConfig(
            AvatarRecorder recorder,
            SpeechAvatarConfigBuildItem avatarConfig) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (avatarConfig.definition() == null) {
            return null;
        }
        String json = JSON.writeValueAsString(avatarConfig.definition());
        RuntimeValue<AvatarConfigBean> rv =
                recorder.createAvatarConfigBean(avatarConfig.definition(), json);
        return SyntheticBeanBuildItem.configure(AvatarConfigBean.class)
                .scope(Singleton.class)
                .runtimeValue(rv)
                .done();
    }

    @BuildStep
    AdditionalBeanBuildItem registerAvatarResource(SpeechAvatarConfigBuildItem avatarConfig) {
        if (avatarConfig.definition() == null) {
            return null;
        }
        return AdditionalBeanBuildItem.unremovableOf(AvatarConfigResource.class);
    }

    private static boolean hasAppBean(BeanDiscoveryFinishedBuildItem beanDiscovery, Class<?> beanType) {
        return !beanDiscovery.beanStream()
                .withBeanType(beanType)
                .filter(b -> !b.isDefaultBean() && !b.isSynthetic())
                .collect()
                .isEmpty();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl speech-deployment -Dtest=SpeechAvatarProcessorTest`
Expected: PASS

- [ ] **Step 5: Verify no agency-core in dependency tree**

Run: `mvn --batch-mode dependency:tree -pl speech-deployment -f pom.xml | grep agency-core`
Expected: No output (agency-core not in resolved dependencies)

- [ ] **Step 6: Commit**

```bash
git add speech-deployment/
git commit -m "feat(#526): add SpeechAvatarProcessor with YAML discovery and bean registration

Discovers META-INF/avatar.yaml at build time, registers TTS/STT/cleanup
beans via AvatarRecorder. No agency-core dependency.

Refs #526"
```

---

## Batch 2: Infrastructure relocated and LLM wired via eidos

### Task 3: Move infrastructure classes from speech-demo to speech-ws

**Files:**
- Move: `speech-demo/.../VoicesEndpoint.java` → `speech-ws/.../ws/VoicesEndpoint.java` (use `ide_move_file`)
- Move: `speech-demo/.../JsonPerformanceVocabulary.java` → `speech-ws/.../ws/JsonPerformanceVocabulary.java` (use `ide_move_file`)
- Move: `speech-demo/.../DiagnosticEndpoint.java` → `speech-ws/.../ws/DiagnosticEndpoint.java` (use `ide_move_file`)
- Move: `speech-demo/.../VoicesEndpointTest.java` → `speech-ws test dir` (use `ide_move_file`)
- Move: `speech-demo/.../JsonPerformanceVocabularyTest.java` → `speech-ws test dir` (use `ide_move_file`)
- Modify: `speech-ws/pom.xml` — add `quarkus-rest` as provided dependency (for JAX-RS `@Path`)

**Interfaces:**
- Consumes: `TtsModelRegistry` (speech-api), `AgentProvider` (platform-agent-api), `PerformanceVocabulary` (speech-api)
- Produces: `/api/avatar/voices` REST endpoint, `/diag` REST endpoint, `JsonPerformanceVocabulary` SPI impl

- [ ] **Step 1: Add quarkus-rest dependency to speech-ws pom.xml**

Add to `speech-ws/pom.xml` dependencies:

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-rest</artifactId>
  <scope>provided</scope>
</dependency>
```

- [ ] **Step 2: Move VoicesEndpoint to speech-ws**

Use `ide_move_file`:
- Source: `speech-demo/src/main/java/io/casehub/agency/speech/demo/VoicesEndpoint.java`
- Target: `speech-ws/src/main/java/io/casehub/agency/speech/ws/VoicesEndpoint.java`

The IDE will update the package declaration to `io.casehub.agency.speech.ws`.

- [ ] **Step 3: Move VoicesEndpointTest to speech-ws**

Use `ide_move_file`:
- Source: `speech-demo/src/test/java/io/casehub/agency/speech/demo/VoicesEndpointTest.java`
- Target: `speech-ws/src/test/java/io/casehub/agency/speech/ws/VoicesEndpointTest.java`

- [ ] **Step 4: Move JsonPerformanceVocabulary to speech-ws**

Use `ide_move_file`:
- Source: `speech-demo/src/main/java/io/casehub/agency/speech/demo/JsonPerformanceVocabulary.java`
- Target: `speech-ws/src/main/java/io/casehub/agency/speech/ws/JsonPerformanceVocabulary.java`

- [ ] **Step 5: Move JsonPerformanceVocabularyTest to speech-ws**

Use `ide_move_file`:
- Source: `speech-demo/src/test/java/io/casehub/agency/speech/demo/JsonPerformanceVocabularyTest.java`
- Target: `speech-ws/src/test/java/io/casehub/agency/speech/ws/JsonPerformanceVocabularyTest.java`

Note: This test loads `META-INF/resources/animations/avatar-performance.json` from the classpath. The JSON file stays in speech-demo (demo-specific config). Add a minimal test fixture JSON to `speech-ws/src/test/resources/META-INF/resources/animations/avatar-performance.json` with a small subset of gestures/moods for the test.

- [ ] **Step 6: Move DiagnosticEndpoint to speech-ws**

Use `ide_move_file`:
- Source: `speech-demo/src/main/java/io/casehub/agency/speech/demo/DiagnosticEndpoint.java`
- Target: `speech-ws/src/main/java/io/casehub/agency/speech/ws/DiagnosticEndpoint.java`

- [ ] **Step 7: Run speech-ws tests**

Run: `mvn --batch-mode test -pl speech-ws`
Expected: All tests pass

- [ ] **Step 8: Run speech-demo compile to verify it still works without moved classes**

Run: `mvn --batch-mode compile -pl speech-demo`
Expected: BUILD SUCCESS (speech-demo no longer references the moved classes)

- [ ] **Step 9: Commit**

```bash
git add speech-ws/ speech-demo/
git commit -m "refactor(#526): move VoicesEndpoint, JsonPerformanceVocabulary, DiagnosticEndpoint to speech-ws

Infrastructure classes are reusable across all avatar consumers,
not demo-specific. Tests moved with their classes.

Refs #526"
```

---

### Task 4: Wire LLM via eidos descriptors and agent-config

**Files:**
- Delete: `speech-demo/src/main/java/io/casehub/agency/speech/demo/SpeechProducers.java` (use `ide_refactor_safe_delete`)
- Delete: `speech-demo/src/main/java/io/casehub/agency/speech/demo/AvatarSpeechProducer.java` (use `ide_refactor_safe_delete`)
- Create: `speech-demo/src/main/resources/META-INF/eidos/descriptors.yaml`
- Create: `speech-demo/agent-config.yaml`
- Modify: `speech-demo/src/main/resources/application.properties` — add `casehub.avatar.agent-id`
- Modify: `speech-demo/pom.xml` — remove google-auth, add eidos-runtime, agent-claude, agent-config, speech-deployment

**Interfaces:**
- Consumes: `EidosAgentResolver` (eidos-runtime) → resolves agent descriptor to system prompt + model; `ClaudeAgentProvider` (agent-claude) → produces `AgentProvider` CDI bean; `AgentConfigLoader` (agent-config) → loads credentials from `agent-config.yaml`

- [ ] **Step 1: Create eidos descriptor for avatar agent**

Create `speech-demo/src/main/resources/META-INF/eidos/descriptors.yaml`:

```yaml
descriptors:
  - agentId: speech-demo-avatar
    name: Speech Demo Avatar
    slot: avatar
    provider: anthropic
    modelFamily: claude
    capabilities:
      - name: conversation
        model:
          tier: FAST
          capabilities: [text]
    briefing: >-
      You are a friendly avatar assistant for a speech pipeline demo.
      Keep responses conversational and concise — 1-2 sentences maximum.
      You can express emotions through gestures and mood changes.
      Be helpful and engaging.
```

- [ ] **Step 2: Create agent-config.yaml**

Create `speech-demo/agent-config.yaml`:

```yaml
providers:
  - vendor: anthropic
    credential: ${ANTHROPIC_API_KEY}
defaults:
  backend: claude
```

- [ ] **Step 3: Add agent-id to application.properties**

Add to `speech-demo/src/main/resources/application.properties` (create if it doesn't exist):

```properties
casehub.avatar.agent-id=speech-demo-avatar
```

- [ ] **Step 4: Update speech-demo pom.xml**

Remove `google-auth-library-oauth2-http` dependency. Remove `casehub-agency-agentic-yaml` dependency (now provided by speech-deployment). Add:

```xml
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-agency-speech-deployment</artifactId>
  <version>${project.version}</version>
</dependency>
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-eidos-runtime</artifactId>
  <version>${project.version}</version>
</dependency>
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-platform-agent-claude</artifactId>
  <version>${project.version}</version>
</dependency>
<dependency>
  <groupId>io.casehub</groupId>
  <artifactId>casehub-platform-agent-config</artifactId>
  <version>${project.version}</version>
</dependency>
```

- [ ] **Step 5: Delete SpeechProducers.java**

Use `ide_refactor_safe_delete` on `speech-demo/src/main/java/io/casehub/agency/speech/demo/SpeechProducers.java`.

- [ ] **Step 6: Delete AvatarSpeechProducer.java**

Use `ide_refactor_safe_delete` on `speech-demo/src/main/java/io/casehub/agency/speech/demo/AvatarSpeechProducer.java`.

- [ ] **Step 7: Verify speech-demo compiles**

Run: `mvn --batch-mode compile -pl speech-demo`
Expected: BUILD SUCCESS — no Java files reference deleted producers; eidos-runtime and agent-claude provide `AgentProvider` via CDI

- [ ] **Step 8: Commit**

```bash
git add speech-demo/
git commit -m "feat(#526): wire LLM via eidos descriptors and agent-config

Delete hardcoded Vertex AI SpeechProducers and manual AvatarSpeechProducer.
Add eidos descriptor for avatar agent, agent-config.yaml for credentials,
speech-deployment for build-time TTS/STT bean registration.

Refs #526"
```

---

## Batch 3: Frontend extracted into reusable ES module

### Task 5: Extract avatar-component.mjs from index.html

**Files:**
- Create: `speech-ws/src/main/resources/META-INF/resources/avatar-component.mjs`
- Move: `speech-demo/.../vendor/talkinghead.mjs` → `speech-ws/.../resources/vendor/talkinghead.mjs` (bash cp — non-code asset)
- Move: `speech-demo/.../animations/body-animations.mjs` → `speech-ws/.../resources/animations/body-animations.mjs` (bash cp — non-code asset)
- Modify: `speech-demo/.../index.html` — replace 750-line inline script with ES module import

**Interfaces:**
- Produces: `AvatarEngine` class (WebSocket, audio, visemes, body animations, TalkingHead), `AvatarControls` class (voice selector, LLM picker, speed slider, animation picker, model status polling)

- [ ] **Step 1: Copy shared assets to speech-ws**

```bash
mkdir -p speech-ws/src/main/resources/META-INF/resources/vendor
mkdir -p speech-ws/src/main/resources/META-INF/resources/animations
cp speech-demo/src/main/resources/META-INF/resources/vendor/talkinghead.mjs speech-ws/src/main/resources/META-INF/resources/vendor/
cp speech-demo/src/main/resources/META-INF/resources/animations/body-animations.mjs speech-ws/src/main/resources/META-INF/resources/animations/
```

- [ ] **Step 2: Create avatar-component.mjs**

Create `speech-ws/src/main/resources/META-INF/resources/avatar-component.mjs`.

This file extracts the avatar engine and controls from index.html into two classes:

```javascript
import { TalkingHead } from "talkinghead";
import { BodyAnimationSystem, FloorState } from "./animations/body-animations.mjs";

export { FloorState };

export class AvatarEngine {

  constructor(container, opts = {}) {
    this._container = container;
    this._avatarModel = opts.avatarModel || 'avatar.glb';
    this._cameraView = opts.cameraView || 'upper';
    this._ws = null;
    this._audioCtx = null;
    this._head = null;
    this._bodyAnim = null;
    this._activeTurnSeq = -1;
    this._pendingSegments = new Map();
    this._turnAudioPlayed = new Set();
    this._turnCuesAttached = new Set();
    this._turnComplete = false;
    this._turnSafetyTimer = null;
    this._safetyTimeoutMs = opts.safetyTimeoutMs || 10000;
    this._baselineMood = 'neutral';
    this._turnMood = null;
    this._turnMoodTimer = null;
    this._turnMoodDecayMs = opts.turnMoodDecayMs || 3000;
    this._playQueue = [];
    this._isPlaying = false;
    this._onMessage = opts.onMessage || null;
    this._onStatus = opts.onStatus || null;
    this._onConnected = opts.onConnected || null;
    this._onDisconnected = opts.onDisconnected || null;
  }

  connect() {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    this._ws = new WebSocket(proto + '//' + location.host + '/ws/avatar');
    this._ws.onopen = () => {
      this._emit('status', 'Connected');
      this._emit('connected');
    };
    this._ws.onmessage = (evt) => {
      if (typeof evt.data === 'string') {
        this._handleTextMessage(JSON.parse(evt.data));
      } else {
        this._handleAudioFrame(evt.data);
      }
    };
    this._ws.onclose = () => {
      this._emit('status', 'Disconnected — reconnecting...');
      this._emit('disconnected');
      setTimeout(() => this.connect(), 2000);
    };
    this._ws.onerror = () => this._emit('status', 'Connection error');
  }

  sendText(text, opts = {}) {
    if (!text || !this._ws || this._ws.readyState !== WebSocket.OPEN) return;
    this._ws.send(JSON.stringify({
      type: 'text', text,
      llmModel: opts.llmModel || '',
      ttsModel: opts.ttsModel || ''
    }));
    this._enterFloorState(FloorState.THINKING);
  }

  startRecording(sampleRate, opts = {}) {
    this._ws.send(JSON.stringify({
      type: 'start', sampleRate,
      llmModel: opts.llmModel || '',
      ttsModel: opts.ttsModel || ''
    }));
    this._enterFloorState(FloorState.LISTENING);
  }

  stopRecording() {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(JSON.stringify({ type: 'stop' }));
    }
    this._enterFloorState(FloorState.THINKING);
  }

  sendBinary(buffer) {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(buffer);
    }
  }

  sendJson(obj) {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(JSON.stringify(obj));
    }
  }

  async initAvatar() {
    if (this._head) return;
    this._head = new TalkingHead(this._container, {
      ttsEndpoint: null,
      lipsyncModules: ['en'],
      cameraView: this._cameraView,
      cameraRotateEnable: true, cameraZoomEnable: true, cameraPanEnable: true,
      avatarIdleEyeContact: 0.4, avatarIdleHeadMove: 0.6,
      avatarSpeakingEyeContact: 0.7, avatarSpeakingHeadMove: 0.7,
      modelMovementFactor: 0.8
    });
    await this._head.showAvatar({
      url: './' + this._avatarModel, body: 'F',
      avatarMood: 'neutral', lipsyncLang: 'en'
    });
    this._head.setMood('neutral');
    await this._initBodyAnimations();
  }

  async switchAvatar(model) {
    if (!this._head) return;
    await this._head.showAvatar({
      url: './' + model, body: 'F',
      avatarMood: 'neutral', lipsyncLang: 'en'
    });
  }

  triggerGesture(name) {
    if (this._bodyAnim) this._bodyAnim.triggerGesture(name);
  }

  get isConnected() {
    return this._ws && this._ws.readyState === WebSocket.OPEN;
  }

  // ── Private ──

  _emit(event, data) {
    const handler = this['_on' + event.charAt(0).toUpperCase() + event.slice(1)];
    if (handler) handler(data);
  }

  _applyMood() {
    const effective = this._turnMood || this._baselineMood;
    if (this._head) this._head.setMood(effective);
  }

  _setTurnMood(mood) {
    clearTimeout(this._turnMoodTimer);
    this._turnMood = mood;
    this._applyMood();
    this._turnMoodTimer = setTimeout(() => {
      this._turnMood = null; this._applyMood();
    }, this._turnMoodDecayMs);
  }

  _enterFloorState(state) {
    if (this._bodyAnim) this._bodyAnim.setFloorState(state);
  }

  _resetTurnState() {
    this._pendingSegments.clear();
    this._turnAudioPlayed.clear();
    this._turnCuesAttached.clear();
    this._turnComplete = false;
    clearTimeout(this._turnSafetyTimer);
    this._turnSafetyTimer = null;
  }

  _checkTurnFinished() {
    if (!this._turnComplete) return;
    for (const [, seg] of this._pendingSegments) {
      if (seg.hasAudio && !this._turnAudioPlayed.has(seg.index)) return;
    }
    this._enterFloorState(FloorState.IDLE);
    this.sendJson({ type: 'playbackComplete', turnSeq: this._activeTurnSeq });
    this._resetTurnState();
  }

  _handleTextMessage(msg) {
    switch (msg.type) {
      case 'transcript':
        this._emit('message', { text: msg.text, role: 'user' });
        break;
      case 'segment':
        this._handleSegment(msg);
        break;
      case 'turnComplete':
        this._handleTurnComplete(msg);
        break;
      case 'gesture':
        if (this._bodyAnim) this._bodyAnim.triggerGesture(msg.tag);
        break;
      case 'mood':
        this._baselineMood = msg.state;
        this._applyMood();
        break;
      case 'error':
        this._emit('message', { text: 'Error: ' + msg.message, role: 'system' });
        break;
      case 'partial':
        this._emit('status', 'Hearing: ' + msg.text);
        break;
    }
  }

  _handleSegment(msg) {
    const key = `${msg.turnSeq}:${msg.index}`;
    this._pendingSegments.set(key, {
      index: msg.index, text: msg.text,
      hasAudio: msg.hasAudio,
      visemes: msg.visemes || [], cues: msg.cues || []
    });
    if (msg.text) this._emit('message', { text: msg.text, role: 'avatar' });
    if (msg.turnSeq !== this._activeTurnSeq) {
      this._resetTurnState();
      this._activeTurnSeq = msg.turnSeq;
    }
    if (!this._head || !msg.hasAudio) {
      for (const cue of (msg.cues || [])) {
        if (cue.type === 'gesture' && this._bodyAnim) this._bodyAnim.triggerGesture(cue.tag);
        if (cue.type === 'mood') this._setTurnMood(cue.mood);
      }
    }
    if (!msg.hasAudio) {
      this._turnAudioPlayed.add(msg.index);
      this._checkTurnFinished();
    }
  }

  _handleTurnComplete(msg) {
    this._turnComplete = true;
    if (msg.segmentCount === 0) {
      this.sendJson({ type: 'playbackComplete', turnSeq: msg.turnSeq });
      this._enterFloorState(FloorState.IDLE);
      this._resetTurnState();
    } else {
      this._checkTurnFinished();
      clearTimeout(this._turnSafetyTimer);
      this._turnSafetyTimer = setTimeout(() => {
        this._enterFloorState(FloorState.IDLE);
        this.sendJson({ type: 'playbackComplete', turnSeq: this._activeTurnSeq });
        this._resetTurnState();
      }, this._safetyTimeoutMs);
    }
  }

  async _handleAudioFrame(blob) {
    try {
      const arrayBuf = await blob.arrayBuffer();
      const view = new DataView(arrayBuf);
      const header = {
        version: view.getUint8(0),
        isFinal: view.getUint8(1) !== 0,
        segmentIndex: view.getUint16(2, true),
        turnSeq: view.getUint32(4, true),
      };
      const audioBytes = arrayBuf.slice(8);
      if (!this._audioCtx) this._audioCtx = new AudioContext();
      const audioBuf = await this._audioCtx.decodeAudioData(audioBytes);

      if (this._head) {
        this._handleAudioWithHead(header, audioBuf);
      } else {
        this._handleAudioWithoutHead(header, audioBuf);
      }
    } catch (e) {
      console.error('[AvatarEngine] Audio frame error:', e);
    }
  }

  _handleAudioWithHead(header, audioBuf) {
    if (header.turnSeq === this._activeTurnSeq) this._enterFloorState(FloorState.SPEAKING);
    const key = `${header.turnSeq}:${header.segmentIndex}`;
    const seg = this._pendingSegments.get(key);
    const r = { audio: audioBuf };
    const firstChunk = !this._turnCuesAttached.has(key);
    if (firstChunk) this._turnCuesAttached.add(key);
    if (firstChunk && seg && seg.visemes && seg.visemes.length > 0) {
      r.visemes = []; r.vtimes = []; r.vdurations = [];
      for (const v of seg.visemes) {
        r.visemes.push(v.viseme); r.vtimes.push(v.startMs);
        r.vdurations.push(v.endMs - v.startMs);
      }
    }
    const markers = []; const mtimes = [];
    for (const cue of (firstChunk ? (seg?.cues || []) : [])) {
      if (cue.type === 'gesture' && this._bodyAnim) {
        const tag = cue.tag;
        const stroke = this._bodyAnim.strokeMs(tag);
        const triggerMs = Math.max(0, (cue.offsetMs || 0) - stroke - 150);
        markers.push(() => this._bodyAnim.triggerGesture(tag));
        mtimes.push(triggerMs);
      }
      if (cue.type === 'mood') {
        const mood = cue.mood;
        markers.push(() => this._setTurnMood(mood));
        mtimes.push(cue.offsetMs || 0);
      }
    }
    if (header.isFinal) {
      const segIdx = header.segmentIndex;
      markers.push(() => { this._turnAudioPlayed.add(segIdx); this._checkTurnFinished(); });
      mtimes.push(audioBuf.duration * 1000);
    }
    if (markers.length > 0) { r.markers = markers; r.mtimes = mtimes; }
    this._head.speakAudio(r, { isRaw: true });
  }

  _handleAudioWithoutHead(header, audioBuf) {
    if (header.turnSeq === this._activeTurnSeq && !this._isPlaying && this._playQueue.length === 0) {
      this._enterFloorState(FloorState.SPEAKING);
    }
    this._playQueue.push({ audio: audioBuf, turnSeq: header.turnSeq,
      segmentIndex: header.segmentIndex, isFinal: header.isFinal });
    if (!this._isPlaying) this._playNextQueued();
  }

  _playNextQueued() {
    if (this._playQueue.length === 0) { this._isPlaying = false; this._emit('status', 'Ready'); return; }
    this._isPlaying = true;
    const item = this._playQueue.shift();
    if (!this._audioCtx) this._audioCtx = new AudioContext();
    const source = this._audioCtx.createBufferSource();
    source.buffer = item.audio;
    source.connect(this._audioCtx.destination);
    source.start();
    this._emit('status', 'Speaking... (' + this._playQueue.length + ' queued)');
    source.onended = () => {
      if (item.isFinal) {
        this._turnAudioPlayed.add(item.segmentIndex);
        this._checkTurnFinished();
      }
      this._playNextQueued();
    };
  }

  async _initBodyAnimations() {
    try {
      if (this._bodyAnim) {
        if (this._head) this._head.unregisterMorphLayer('gestureExpressions');
        this._bodyAnim.dispose(); this._bodyAnim = null;
      }
      const avatarModel = this._head.armature;
      if (!avatarModel) return;
      const configResp = await fetch('animations/avatar-performance.json');
      const config = await configResp.json();
      this._bodyAnim = await BodyAnimationSystem.create(avatarModel, config);
      this._head.registerMixer(this._bodyAnim.mixer, {
        boneClaim: [
          'Spine', 'Spine1', 'Spine2', 'Hips',
          'LeftShoulder', 'LeftArm', 'LeftForeArm', 'LeftHand',
          'LeftHandThumb1', 'LeftHandThumb2', 'LeftHandThumb3',
          'LeftHandIndex1', 'LeftHandIndex2', 'LeftHandIndex3',
          'LeftHandMiddle1', 'LeftHandMiddle2', 'LeftHandMiddle3',
          'LeftHandRing1', 'LeftHandRing2', 'LeftHandRing3',
          'LeftHandPinky1', 'LeftHandPinky2', 'LeftHandPinky3',
          'RightShoulder', 'RightArm', 'RightForeArm', 'RightHand',
          'RightHandThumb1', 'RightHandThumb2', 'RightHandThumb3',
          'RightHandIndex1', 'RightHandIndex2', 'RightHandIndex3',
          'RightHandMiddle1', 'RightHandMiddle2', 'RightHandMiddle3',
          'RightHandRing1', 'RightHandRing2', 'RightHandRing3',
          'RightHandPinky1', 'RightHandPinky2', 'RightHandPinky3',
          'LeftUpLeg', 'LeftLeg', 'LeftFoot', 'LeftToeBase',
          'RightUpLeg', 'RightLeg', 'RightFoot', 'RightToeBase',
        ],
      });
      this._head.registerMorphLayer('gestureExpressions', {
        order: 10, blend: 'additive',
        update: (dt, get, set) => {
          const exprs = this._bodyAnim.sampleExpressions();
          if (!exprs) return;
          for (const [name, value] of Object.entries(exprs)) set(name, value);
        }
      });
    } catch (e) {
      console.warn('[AvatarEngine] Body animation load failed:', e);
    }
  }
}


export class AvatarControls {

  constructor(container, engine, opts = {}) {
    this._container = container;
    this._engine = engine;
    this._config = null;
    this._init(opts);
  }

  async _init(opts) {
    this._render(opts);
    await this._loadConfig();
    this._loadVoices();
    this._pollModelStatus();
  }

  _render(opts) {
    this._container.innerHTML = `
      <label>LLM: <select id="ac-llmModel">
        <option value="claude-haiku-4-5@20251001">Haiku 4.5 (fast)</option>
        <option value="claude-sonnet-4@20250514">Sonnet 4</option>
        <option value="claude-opus-4@20250514">Opus 4</option>
      </select></label>
      <label>Voice: <select id="ac-ttsModel">
        <option value="">Loading voices...</option>
      </select></label>
      <label>Speed: <input id="ac-speed" type="range" min="0.6" max="1.4" step="0.05" value="0.9"
        style="width:80px;vertical-align:middle"> <span id="ac-speedVal">0.9x</span></label>
      <label>Avatar: <select id="ac-avatarModel">
        <option value="avatar.glb">Current</option>
      </select></label>
      <label>Animation: <select id="ac-animPicker">
        <option value="">Auto (idle cycle)</option>
      </select></label>
      <button id="ac-playAnim" style="padding:0.3rem 0.6rem;border-radius:6px;border:1px solid #555;background:#2a2a3e;color:#e0e0e0;cursor:pointer;font-size:0.85rem" title="Play selected animation">&#9654;</button>
      <div id="ac-modelStatus" style="font-size:0.75rem;color:#999"></div>
    `;
    this._el('ac-speed').addEventListener('input', function() {
      document.getElementById('ac-speedVal').textContent = this.value + 'x';
    });
    this._el('ac-avatarModel').addEventListener('change', () => {
      this._engine.switchAvatar(this._el('ac-avatarModel').value);
    });
    this._el('ac-playAnim').addEventListener('click', () => {
      const clip = this._el('ac-animPicker').value;
      if (clip) this._engine.triggerGesture(clip);
    });
  }

  get llmModel() { return this._el('ac-llmModel').value; }
  get ttsModel() { return this._el('ac-ttsModel').value; }
  get speed() { return parseFloat(this._el('ac-speed').value); }

  async _loadConfig() {
    try {
      const r = await fetch('/api/avatar/config');
      this._config = await r.json();
      if (this._config.speed) {
        this._el('ac-speed').value = this._config.speed;
        this._el('ac-speedVal').textContent = this._config.speed + 'x';
      }
      if (this._config.minPlaybackRate) {
        this._el('ac-speed').min = this._config.minPlaybackRate;
      }
      if (this._config.model) {
        const sel = this._el('ac-llmModel');
        for (let i = 0; i < sel.options.length; i++) {
          if (sel.options[i].value === this._config.model) { sel.value = this._config.model; break; }
        }
      }
    } catch (err) {
      console.warn('Failed to load avatar config:', err);
    }
  }

  _loadVoices() {
    const sel = this._el('ac-ttsModel');
    fetch('/api/avatar/voices')
      .then(r => r.json())
      .then(voices => {
        sel.innerHTML = '';
        const groups = {};
        for (const v of voices) {
          let group, label;
          if (v.startsWith('qwen3:clone:')) { group = 'Qwen3 Cloned'; label = v.substring(12).replace(/_/g, ' '); }
          else if (v.startsWith('qwen3:')) { group = 'Qwen3-TTS'; label = v.substring(6); }
          else if (v.startsWith('kokoro:')) { group = 'Kokoro'; label = v.substring(7).replace(/_/g, ' '); }
          else if (v.startsWith('sherpa:')) { group = 'Sherpa VITS'; label = v.substring(7); }
          else if (v.startsWith('audio8') || v === 'cosyvoice3') { group = 'Experimental'; label = v; }
          else { group = 'VITS'; label = v; }
          if (!groups[group]) groups[group] = [];
          groups[group].push({ value: v, label });
        }
        for (const gname of Object.keys(groups)) {
          const optgroup = document.createElement('optgroup');
          optgroup.label = gname;
          for (const item of groups[gname]) {
            const opt = document.createElement('option');
            opt.value = item.value; opt.textContent = item.label;
            optgroup.appendChild(opt);
          }
          sel.appendChild(optgroup);
        }
        if (this._config && this._config.ttsVoice) sel.value = this._config.ttsVoice;
      })
      .catch(() => { sel.innerHTML = '<option value="lessac-medium">lessac-medium</option>'; });
  }

  _pollModelStatus() {
    const statusEl = this._el('ac-modelStatus');
    const sel = this._el('ac-ttsModel');
    const timer = setInterval(() => {
      fetch('/api/models/status')
        .then(r => r.json())
        .then(data => {
          const total = Object.keys(data).length;
          let ready = 0, downloading = 0, errors = 0;
          for (const key in data) {
            if (data[key] === 'READY') ready++;
            else if (data[key] === 'DOWNLOADING') downloading++;
            else if (data[key] === 'ERROR') errors++;
          }
          for (let i = 0; i < sel.options.length; i++) {
            const opt = sel.options[i];
            const st = data[opt.value];
            opt.disabled = st !== 'READY';
            opt.text = opt.text.replace(/ \(downloading\.\.\.\)$/, '');
            if (st === 'DOWNLOADING') opt.text += ' (downloading...)';
          }
          if (ready === total) {
            statusEl.innerHTML = '<span style="color:#4a9">All voice models ready</span>';
            clearInterval(timer);
            setTimeout(() => { statusEl.style.display = 'none'; }, 3000);
          } else if (downloading > 0) {
            statusEl.innerHTML = '<span style="color:#d9a547">Downloading: ' + ready + '/' + total + ' ready</span>';
          } else if (errors > 0) {
            statusEl.innerHTML = '<span style="color:#d64">Errors: ' + errors + '/' + total + '</span>';
            if (ready + errors === total) clearInterval(timer);
          }
        })
        .catch(() => {});
    }, 2000);
  }

  _el(id) { return document.getElementById(id); }
}
```

- [ ] **Step 3: Rewrite index.html as thin consumer**

Replace `speech-demo/src/main/resources/META-INF/resources/index.html` with a thin consumer that imports from `avatar-component.mjs`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Avatar Speech Demo</title>
<script type="importmap">
{ "imports": {
    "three": "https://cdn.jsdelivr.net/npm/three@0.180.0/build/three.module.js/+esm",
    "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.180.0/examples/jsm/",
    "talkinghead": "./vendor/talkinghead.mjs"
} }
</script>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: system-ui, sans-serif; background: #1a1a2e; color: #e0e0e0; height: 100vh; display: flex; flex-direction: column; }
  header { padding: 1rem; text-align: center; border-bottom: 1px solid #333; }
  header h1 { font-size: 1.2rem; font-weight: 500; }
  #avatar-container { width: 100%; height: 450px; background: #111; position: relative; border-bottom: 1px solid #333; display: none; }
  #avatar-container.active { display: block; }
  #toggle-avatar { font-size: 0.8rem; color: #999; cursor: pointer; text-decoration: underline; }
  #chat { flex: 1; overflow-y: auto; padding: 1rem; display: flex; flex-direction: column; gap: 0.75rem; }
  .msg { max-width: 70%; padding: 0.75rem 1rem; border-radius: 12px; font-size: 0.95rem; line-height: 1.4; }
  .msg.user { align-self: flex-end; background: #2d5aa0; }
  .msg.avatar { align-self: flex-start; background: #333; }
  .msg.system { align-self: center; background: transparent; color: #888; font-size: 0.8rem; }
  #controls { display: flex; flex-wrap: wrap; gap: 0.5rem 1rem; padding: 0.5rem 1rem; border-bottom: 1px solid #333; align-items: center; justify-content: center; }
  #controls label { font-size: 0.8rem; color: #999; }
  #controls select { padding: 0.3rem 0.5rem; border-radius: 6px; border: 1px solid #555; background: #2a2a3e; color: #e0e0e0; font-size: 0.85rem; }
  #input-bar { display: flex; gap: 0.5rem; padding: 1rem; border-top: 1px solid #333; }
  #input-bar input { flex: 1; padding: 0.75rem; border-radius: 8px; border: 1px solid #555; background: #2a2a3e; color: #e0e0e0; font-size: 1rem; }
  #input-bar input:focus { outline: none; border-color: #2d5aa0; }
  #input-bar button { padding: 0.75rem 1.5rem; border-radius: 8px; border: none; background: #2d5aa0; color: white; font-size: 1rem; cursor: pointer; }
  #input-bar button:disabled { opacity: 0.5; cursor: default; }
  #mic { padding: 0.75rem 1rem; border-radius: 8px; border: 2px solid #555; background: #2a2a3e; color: #e0e0e0; font-size: 1rem; cursor: pointer; transition: all 0.2s; }
  #mic:disabled { opacity: 0.5; cursor: default; }
  #mic.recording { background: #d64; border-color: #d64; color: white; animation: pulse 1s infinite; }
  @keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.7; } }
  #status { padding: 0.25rem 1rem; font-size: 0.75rem; color: #666; text-align: center; }
</style>
</head>
<body>
<header><h1>Avatar Speech Pipeline Demo</h1></header>
<div id="controls"></div>
<div style="text-align:center;padding:0.25rem"><span id="toggle-avatar">Show 3D Avatar</span></div>
<div id="avatar-container"></div>
<div id="status">Connecting...</div>
<div id="chat"></div>
<div id="input-bar">
  <button id="mic" disabled title="Click to talk">Mic</button>
  <input id="msg" type="text" placeholder="Type a message..." autocomplete="off" disabled>
  <button id="send" disabled>Send</button>
</div>

<script type="module">
import { AvatarEngine, AvatarControls } from './avatar-component.mjs';

const chat = document.getElementById('chat');
const msgInput = document.getElementById('msg');
const sendBtn = document.getElementById('send');
const statusEl = document.getElementById('status');
const micBtn = document.getElementById('mic');
const avatarContainer = document.getElementById('avatar-container');
const toggleAvatar = document.getElementById('toggle-avatar');

function addMessage(text, cls) {
  const div = document.createElement('div');
  div.className = 'msg ' + cls;
  div.textContent = text;
  chat.appendChild(div);
  chat.scrollTop = chat.scrollHeight;
}

const engine = new AvatarEngine(avatarContainer, {
  onMessage: ({ text, role }) => addMessage(text, role),
  onStatus: (msg) => { statusEl.textContent = msg; },
  onConnected: () => {
    statusEl.textContent = 'Connected';
    msgInput.disabled = false;
    sendBtn.disabled = false;
    micBtn.disabled = false;
    addMessage('Connected — type a message or click Mic to talk.', 'system');
  },
  onDisconnected: () => {
    msgInput.disabled = true;
    sendBtn.disabled = true;
    micBtn.disabled = true;
  }
});

const controls = new AvatarControls(document.getElementById('controls'), engine);

function sendText() {
  const text = msgInput.value.trim();
  if (!text) return;
  engine.sendText(text, { llmModel: controls.llmModel, ttsModel: controls.ttsModel });
  msgInput.value = '';
  statusEl.textContent = 'Processing...';
}

sendBtn.addEventListener('click', sendText);
msgInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') sendText(); });

let recording = false;
let audioCtx = null;
let micStream = null;
let micProcessor = null;
let micSource = null;

async function startRecording() {
  if (recording) return;
  try {
    if (!audioCtx) audioCtx = new AudioContext();
    micStream = await navigator.mediaDevices.getUserMedia({
      audio: { sampleRate: 16000, channelCount: 1, echoCancellation: true, noiseSuppression: true }
    });
    micSource = audioCtx.createMediaStreamSource(micStream);
    micProcessor = audioCtx.createScriptProcessor(4096, 1, 1);
    micProcessor.onaudioprocess = function(e) {
      if (!recording) return;
      const input = e.inputBuffer.getChannelData(0);
      const resampled = resampleTo16k(input, audioCtx.sampleRate);
      const buf = new ArrayBuffer(resampled.length * 4);
      new Float32Array(buf).set(resampled);
      engine.sendBinary(buf);
    };
    micSource.connect(micProcessor);
    micProcessor.connect(audioCtx.destination);
    engine.startRecording(16000, { llmModel: controls.llmModel, ttsModel: controls.ttsModel });
    recording = true;
    micBtn.classList.add('recording');
    micBtn.textContent = 'Recording...';
    statusEl.textContent = 'Listening...';
  } catch (e) {
    addMessage('Microphone error: ' + e.message, 'system');
  }
}

function stopRecording() {
  if (!recording) return;
  micBtn.classList.remove('recording');
  micBtn.textContent = 'Finishing...';
  micBtn.disabled = true;
  setTimeout(function() {
    recording = false;
    engine.stopRecording();
    statusEl.textContent = 'Processing speech...';
    micBtn.textContent = 'Mic';
    if (micProcessor) { micProcessor.disconnect(); micProcessor = null; }
    if (micSource) { micSource.disconnect(); micSource = null; }
    if (micStream) { micStream.getTracks().forEach(t => t.stop()); micStream = null; }
    micBtn.disabled = false;
  }, 500);
}

function resampleTo16k(input, fromRate) {
  if (fromRate === 16000) return input;
  const ratio = fromRate / 16000;
  const len = Math.round(input.length / ratio);
  const out = new Float32Array(len);
  for (let i = 0; i < len; i++) out[i] = input[Math.round(i * ratio)];
  return out;
}

micBtn.addEventListener('click', () => { if (recording) stopRecording(); else startRecording(); });

toggleAvatar.addEventListener('click', async () => {
  if (avatarContainer.classList.contains('active')) {
    avatarContainer.classList.remove('active');
    toggleAvatar.textContent = 'Show 3D Avatar';
    return;
  }
  avatarContainer.classList.add('active');
  toggleAvatar.textContent = 'Hide 3D Avatar';
  try {
    addMessage('Loading 3D avatar...', 'system');
    await engine.initAvatar();
    addMessage('3D avatar ready.', 'system');
  } catch (e) {
    addMessage('Avatar load error: ' + e.message, 'system');
  }
});

let lastTypingActivity = 0;
msgInput.addEventListener('input', function() {
  const now = Date.now();
  if (now - lastTypingActivity > 2000) {
    engine.sendJson({ type: 'userActivity', kind: 'TYPING' });
    lastTypingActivity = now;
  }
});

document.addEventListener('visibilitychange', function() {
  engine.sendJson({ type: 'userActivity', kind: 'VISIBILITY_CHANGE' });
});

engine.connect();
</script>
</body>
</html>
```

- [ ] **Step 4: Remove old vendor/animations from speech-demo (now served by speech-ws)**

```bash
rm speech-demo/src/main/resources/META-INF/resources/vendor/talkinghead.mjs
rm speech-demo/src/main/resources/META-INF/resources/animations/body-animations.mjs
```

The animation GLBs, PNGs, and `avatar-performance.json` stay in speech-demo (demo-specific assets).

- [ ] **Step 5: Verify speech-demo still serves the page**

Manual test: start the speech-demo app and verify:
- index.html loads and imports avatar-component.mjs from speech-ws resources
- Voice selector populates from `/api/avatar/voices`
- Controls apply defaults from `/api/avatar/config`

- [ ] **Step 6: Commit**

```bash
git add speech-ws/src/main/resources/ speech-demo/src/main/resources/
git commit -m "feat(#526): extract avatar-component.mjs ES module from index.html

AvatarEngine handles WebSocket, audio, visemes, body animations.
AvatarControls handles voice selector, LLM picker, speed slider.
index.html reduced from 750 to ~120 lines as a thin consumer.
Shared assets (talkinghead.mjs, body-animations.mjs) moved to speech-ws.

Refs #526"
```

---

## References

- [2026-11-20-zero-java-avatar-design.md] — design spec this plan implements
- `agentic-yaml-deployment/AvatarSpeechProcessor.java` — build step template
- `agentic-yaml-deployment/AvatarConfigBuildItem.java` — build item template
- `agentic-yaml-deployment/AgenticYamlProcessor.java:126-139` — avatar YAML discovery
- `agentic-yaml/AvatarRecorder.java` — runtime recorder (referenced by speech-deployment)
- `speech-ws/SpeechWebSocket.java:37-52` — CDI injection points
- `speech-ws/AvatarConfig.java:19-21` — agentId/tenantId wiring points
- `speech-demo/SpeechProducers.java` — hardcoded Vertex AI to delete
- `speech-demo/AvatarSpeechProducer.java` — manual compilation to delete
- `speech-demo/index.html` — 750-line frontend to extract
- `eidos/examples/agent-scenarios/descriptors.yaml` — descriptor format reference
- `platform/agent-config/agent-config.yaml` — agent-config format reference
- GitHub #526 — focal issue

# Avatar Reusable Mosaic Component Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #449 — Avatar reusable mosaic component with YAML configuration

**Goal:** Make the avatar pipeline configurable via YAML and decompose the monolithic mosaic avatar panel into composable sub-components.

**Architecture:** Two repos. Agency: `AvatarDefinition` YAML spec records in `agentic-yaml`, deployed via build step + recorder, served to frontend via REST endpoint. Mosaic: extract `AvatarSession` service from `AvatarWsController`, create `casehub-avatar-controls` component, add `.session` property to existing components, refactor `casehub-avatar-panel` to thin shell.

**Tech Stack:** Java 17 (agency — records, Jackson YAML), TypeScript/Lit 3 (mosaic — web components, vitest)

## Global Constraints

- Package naming convention: files under `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/` declare `package io.casehub.agency.core.yaml.*` — follow this pattern for all new files in agentic-yaml.
- Spec records use `@Nullable` from `org.jspecify.annotations` for optional fields.
- YAML deserialization uses `io.casehub.yaml.jackson.YamlMappers.create()` with `JavaTimeModule`.
- Mosaic components require ARIA attributes (`role`, `aria-label`) — tested in every component test.
- Mosaic tests use vitest with jsdom, `globals: true`, `experimentalDecorators: true`.
- Existing property-based APIs on mosaic components must be preserved for standalone use (backward compatible).

---

## Batch 1: Agency YAML Foundation

### Task 1: AvatarDefinition spec records + serialization tests

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/AvatarDefinition.java`
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TtsSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TtsEngineSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TtsVoiceSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/SttSpec.java`
- Test: `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/spec/AvatarDefinitionSerializationTest.java`

**Interfaces:**
- Consumes: existing `CleanupConfigSpec` from `io.casehub.agency.core.yaml.spec`
- Produces: `AvatarDefinition` record (used by Task 2 deployment, Task 3 adapter)

- [ ] **Step 1: Write failing serialization test — full YAML round-trip**

```java
package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvatarDefinitionSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void deserialises_full_avatar_definition() throws Exception {
        var yaml = """
                agent: mentor-coach
                model: avatar-brunette.glb
                ttsVoice: qwen3:jodi_krangle
                cameraView: head
                sampleRate: 16000
                speed: 0.9
                minPlaybackRate: 0.6
                tts:
                  engines:
                    - type: kokoro
                      voices:
                        - name: af_heart
                        - name: am_adam
                    - type: qwen3
                      voices:
                        - name: ryan
                      clonedVoices:
                        - jodi_krangle
                stt:
                  type: whisper
                cleanup:
                  maxDestructiveness: 2
                """;
        var def = mapper.readValue(yaml, AvatarDefinition.class);
        assertThat(def.agent()).isEqualTo("mentor-coach");
        assertThat(def.model()).isEqualTo("avatar-brunette.glb");
        assertThat(def.ttsVoice()).isEqualTo("qwen3:jodi_krangle");
        assertThat(def.cameraView()).isEqualTo("head");
        assertThat(def.sampleRate()).isEqualTo(16000);
        assertThat(def.speed()).isEqualTo(0.9);
        assertThat(def.minPlaybackRate()).isEqualTo(0.6);
        assertThat(def.tts()).isNotNull();
        assertThat(def.tts().engines()).hasSize(2);
        assertThat(def.tts().engines().get(0).type()).isEqualTo("kokoro");
        assertThat(def.tts().engines().get(0).voices()).hasSize(2);
        assertThat(def.tts().engines().get(0).voices().get(0).name()).isEqualTo("af_heart");
        assertThat(def.tts().engines().get(1).clonedVoices()).containsExactly("jodi_krangle");
        assertThat(def.stt()).isNotNull();
        assertThat(def.stt().type()).isEqualTo("whisper");
        assertThat(def.cleanup()).isNotNull();
        assertThat(def.cleanup().maxDestructiveness()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl agentic-yaml -Dtest=AvatarDefinitionSerializationTest#deserialises_full_avatar_definition -DfailIfNoTests=false`
Expected: FAIL — `AvatarDefinition` class does not exist

- [ ] **Step 3: Create TtsVoiceSpec record**

Create `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TtsVoiceSpec.java`:

```java
package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record TtsVoiceSpec(
        String name,
        @Nullable String model) {

    public TtsVoiceSpec {
        Objects.requireNonNull(name, "name");
    }

    @JsonCreator
    static TtsVoiceSpec fromJson(
            @JsonProperty("name") String name,
            @JsonProperty("model") @Nullable String model) {
        return new TtsVoiceSpec(name, model);
    }
}
```

- [ ] **Step 4: Create TtsEngineSpec record**

Create `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TtsEngineSpec.java`:

```java
package io.casehub.agency.core.yaml.spec;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record TtsEngineSpec(
        String type,
        List<TtsVoiceSpec> voices,
        @Nullable List<String> clonedVoices) {

    public TtsEngineSpec {
        Objects.requireNonNull(type, "type");
        voices = voices != null ? List.copyOf(voices) : List.of();
        clonedVoices = clonedVoices != null ? List.copyOf(clonedVoices) : null;
    }
}
```

- [ ] **Step 5: Create TtsSpec record**

Create `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TtsSpec.java`:

```java
package io.casehub.agency.core.yaml.spec;

import java.util.List;
import java.util.Objects;

public record TtsSpec(List<TtsEngineSpec> engines) {

    public TtsSpec {
        Objects.requireNonNull(engines, "engines");
        engines = List.copyOf(engines);
    }
}
```

- [ ] **Step 6: Create SttSpec record**

Create `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/SttSpec.java`:

```java
package io.casehub.agency.core.yaml.spec;

import java.util.Objects;

public record SttSpec(String type) {

    public SttSpec {
        Objects.requireNonNull(type, "type");
    }
}
```

- [ ] **Step 7: Create AvatarDefinition record**

Create `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/AvatarDefinition.java`:

```java
package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record AvatarDefinition(
        String agent,
        String model,
        @Nullable String ttsVoice,
        @Nullable String cameraView,
        int sampleRate,
        double speed,
        double minPlaybackRate,
        @Nullable TtsSpec tts,
        @Nullable SttSpec stt,
        @Nullable CleanupConfigSpec cleanup) {

    public AvatarDefinition {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(model, "model");
    }

    @JsonCreator
    static AvatarDefinition fromJson(
            @JsonProperty("agent") String agent,
            @JsonProperty("model") String model,
            @JsonProperty("ttsVoice") @Nullable String ttsVoice,
            @JsonProperty("cameraView") @Nullable String cameraView,
            @JsonProperty("sampleRate") @Nullable Integer sampleRate,
            @JsonProperty("speed") @Nullable Double speed,
            @JsonProperty("minPlaybackRate") @Nullable Double minPlaybackRate,
            @JsonProperty("tts") @Nullable TtsSpec tts,
            @JsonProperty("stt") @Nullable SttSpec stt,
            @JsonProperty("cleanup") @Nullable CleanupConfigSpec cleanup) {
        return new AvatarDefinition(
                agent, model, ttsVoice,
                cameraView,
                sampleRate != null ? sampleRate : 16000,
                speed != null ? speed : 0.9,
                minPlaybackRate != null ? minPlaybackRate : 0.6,
                tts, stt, cleanup);
    }
}
```

- [ ] **Step 8: Run the full test**

Run: `mvn test -pl agentic-yaml -Dtest=AvatarDefinitionSerializationTest#deserialises_full_avatar_definition -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 9: Add test for minimal YAML (only required fields)**

Add to `AvatarDefinitionSerializationTest`:

```java
@Test
void deserialises_minimal_avatar_definition() throws Exception {
    var yaml = """
            agent: coach
            model: avatar.glb
            """;
    var def = mapper.readValue(yaml, AvatarDefinition.class);
    assertThat(def.agent()).isEqualTo("coach");
    assertThat(def.model()).isEqualTo("avatar.glb");
    assertThat(def.ttsVoice()).isNull();
    assertThat(def.cameraView()).isNull();
    assertThat(def.sampleRate()).isEqualTo(16000);
    assertThat(def.speed()).isEqualTo(0.9);
    assertThat(def.minPlaybackRate()).isEqualTo(0.6);
    assertThat(def.tts()).isNull();
    assertThat(def.stt()).isNull();
    assertThat(def.cleanup()).isNull();
}
```

- [ ] **Step 10: Run the new test**

Run: `mvn test -pl agentic-yaml -Dtest=AvatarDefinitionSerializationTest -DfailIfNoTests=false`
Expected: PASS (both tests)

- [ ] **Step 11: Add test for null agent rejection**

```java
@Test
void rejects_null_agent() {
    assertThatThrownBy(() -> new AvatarDefinition(
            null, "avatar.glb", null, null, 16000, 0.9, 0.6, null, null, null))
            .isInstanceOf(NullPointerException.class);
}
```

- [ ] **Step 12: Run all tests**

Run: `mvn test -pl agentic-yaml -Dtest=AvatarDefinitionSerializationTest -DfailIfNoTests=false`
Expected: PASS (3 tests)

- [ ] **Step 13: Add TtsVoiceSpec shorthand deserialization test**

TtsVoiceSpec should deserialize from both a string (shorthand) and an object. Add a `@JsonCreator` that handles string input:

```java
@Test
void tts_voice_spec_deserialises_from_string() throws Exception {
    var yaml = """
            type: kokoro
            voices:
              - af_heart
              - name: am_adam
                model: custom-model
            """;
    var spec = mapper.readValue(yaml, TtsEngineSpec.class);
    assertThat(spec.voices()).hasSize(2);
    assertThat(spec.voices().get(0).name()).isEqualTo("af_heart");
    assertThat(spec.voices().get(0).model()).isNull();
    assertThat(spec.voices().get(1).name()).isEqualTo("am_adam");
    assertThat(spec.voices().get(1).model()).isEqualTo("custom-model");
}
```

- [ ] **Step 14: Add string deserialization to TtsVoiceSpec**

Update `TtsVoiceSpec` to handle both string and object deserialization:

```java
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
static TtsVoiceSpec fromString(String name) {
    return new TtsVoiceSpec(name, null);
}
```

Replace the existing `fromJson` method with:

```java
@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
static TtsVoiceSpec fromJson(
        @JsonProperty("name") String name,
        @JsonProperty("model") @Nullable String model) {
    return new TtsVoiceSpec(name, model);
}
```

Note: Jackson supports `DELEGATING` for single-string and `PROPERTIES` for object — both via `@JsonCreator`.

- [ ] **Step 15: Run all tests**

Run: `mvn test -pl agentic-yaml -Dtest=AvatarDefinitionSerializationTest -DfailIfNoTests=false`
Expected: PASS (4 tests)

- [ ] **Step 16: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/AvatarDefinition.java \
       agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TtsSpec.java \
       agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TtsEngineSpec.java \
       agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TtsVoiceSpec.java \
       agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/SttSpec.java \
       agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/spec/AvatarDefinitionSerializationTest.java
git commit -m "feat(#449): add AvatarDefinition YAML spec records

Refs #449"
```

### Task 2: Deployment build step + recorder + REST endpoint

**Files:**
- Create: `agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/AvatarConfigBuildItem.java`
- Modify: `agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/AgenticYamlProcessor.java`
- Modify: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgenticRecorder.java`
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AvatarConfigBean.java`
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AvatarConfigResource.java`

**Interfaces:**
- Consumes: `AvatarDefinition` from Task 1
- Produces: `AvatarConfigBean` (CDI singleton, holds definition + JSON), `AvatarConfigResource` (REST at `/api/avatar/config`)

- [ ] **Step 1: Create AvatarConfigBuildItem**

Create `agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/AvatarConfigBuildItem.java`:

```java
package io.casehub.agency.core.yaml.deployment;

import io.casehub.agency.core.yaml.spec.AvatarDefinition;
import io.quarkus.builder.item.SimpleBuildItem;
import org.jspecify.annotations.Nullable;

public final class AvatarConfigBuildItem extends SimpleBuildItem {

    private final @Nullable AvatarDefinition definition;

    public AvatarConfigBuildItem(@Nullable AvatarDefinition definition) {
        this.definition = definition;
    }

    public @Nullable AvatarDefinition definition() {
        return definition;
    }
}
```

- [ ] **Step 2: Add discovery build step to AgenticYamlProcessor**

Add to `AgenticYamlProcessor.java`:

```java
@BuildStep
AvatarConfigBuildItem discoverAvatarConfig() throws IOException {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    URL resource = cl.getResource("META-INF/avatar.yaml");
    if (resource == null) {
        resource = cl.getResource("META-INF/avatar.yml");
    }
    if (resource == null) {
        return new AvatarConfigBuildItem(null);
    }
    try (InputStream in = resource.openStream()) {
        var definition = YAML.readValue(in, io.casehub.agency.core.yaml.spec.AvatarDefinition.class);
        return new AvatarConfigBuildItem(definition);
    }
}
```

- [ ] **Step 3: Create AvatarConfigBean**

Create `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AvatarConfigBean.java`:

```java
package io.casehub.agency.core.yaml.runtime;

import io.casehub.agency.core.yaml.spec.AvatarDefinition;

public class AvatarConfigBean {

    private final AvatarDefinition definition;
    private final String json;

    public AvatarConfigBean(AvatarDefinition definition, String json) {
        this.definition = definition;
        this.json = json;
    }

    public AvatarDefinition definition() {
        return definition;
    }

    public String json() {
        return json;
    }
}
```

- [ ] **Step 4: Add recorder method**

Add to `AgenticRecorder.java`:

```java
@io.quarkus.runtime.annotations.Recorder
public class AgenticRecorder {
    // ... existing methods ...

    public RuntimeValue<AvatarConfigBean> createAvatarConfigBean(
            io.casehub.agency.core.yaml.spec.AvatarDefinition definition, String json) {
        return new RuntimeValue<>(new AvatarConfigBean(definition, json));
    }
}
```

- [ ] **Step 5: Add registration build step to AgenticYamlProcessor**

Add to `AgenticYamlProcessor.java`:

```java
@BuildStep
@Record(STATIC_INIT)
SyntheticBeanBuildItem registerAvatarConfig(
        AgenticRecorder recorder,
        AvatarConfigBuildItem avatarConfig) {
    if (avatarConfig.definition() == null) {
        return null;
    }
    try {
        String json = JSON.writeValueAsString(avatarConfig.definition());
        RuntimeValue<AvatarConfigBean> rv = recorder.createAvatarConfigBean(
                avatarConfig.definition(), json);
        return SyntheticBeanBuildItem.configure(AvatarConfigBean.class)
                .scope(Singleton.class)
                .runtimeValue(rv)
                .done();
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new RuntimeException("Failed to serialize AvatarDefinition to JSON", e);
    }
}
```

Note: this method returns `null` when no avatar YAML exists — Quarkus ignores null `SyntheticBeanBuildItem` returns.

- [ ] **Step 6: Create AvatarConfigResource**

Create `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AvatarConfigResource.java`:

```java
package io.casehub.agency.core.yaml.runtime;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/avatar")
public class AvatarConfigResource {

    @Inject
    Instance<AvatarConfigBean> avatarConfig;

    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfig() {
        if (!avatarConfig.isResolvable()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"No avatar configuration found\"}")
                    .build();
        }
        return Response.ok(avatarConfig.get().json()).build();
    }
}
```

- [ ] **Step 7: Add AdditionalBeanBuildItem for AvatarConfigResource**

Add to `AgenticYamlProcessor.java`:

```java
@BuildStep
AdditionalBeanBuildItem registerAvatarResource(AvatarConfigBuildItem avatarConfig) {
    if (avatarConfig.definition() == null) {
        return null;
    }
    return AdditionalBeanBuildItem.unremovableOf(AvatarConfigResource.class);
}
```

- [ ] **Step 8: Run build**

Run: `mvn --batch-mode install -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/AvatarConfigBuildItem.java \
       agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/AgenticYamlProcessor.java \
       agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AvatarConfigBean.java \
       agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AvatarConfigResource.java \
       agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgenticRecorder.java
git commit -m "feat(#449): add avatar YAML deployment and REST endpoint

Build step discovers META-INF/avatar.yaml, registers AvatarConfigBean
as CDI singleton. REST endpoint at /api/avatar/config serves the config
JSON to the frontend.

Refs #449"
```

## Batch 2: Mosaic AvatarSession Service

### Task 3: Extract AvatarSession from AvatarWsController

**Files:**
- Create: `casehub-mosaic/packages/avatar/src/avatar-session.ts`
- Create: `casehub-mosaic/packages/avatar/src/avatar-session.test.ts`
- Modify: `casehub-mosaic/packages/avatar/src/index.ts` (add exports)

**Interfaces:**
- Consumes: `ConversationTurn`, `PlaybackItem`, `VisemeFrame`, `AvatarConfig`, `AvatarMessage` from `types.ts`
- Produces: `AvatarSession` class (used by Tasks 4 and 5 — all mosaic components)

- [ ] **Step 1: Write failing test — config fetch and event dispatch**

Create `casehub-mosaic/packages/avatar/src/avatar-session.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

class MockWebSocket {
  readyState = WebSocket.CONNECTING;
  sent: (string | ArrayBuffer)[] = [];
  onopen: ((ev: Event) => void) | null = null;
  onmessage: ((ev: MessageEvent) => void) | null = null;
  onclose: ((ev: CloseEvent) => void) | null = null;
  onerror: ((ev: Event) => void) | null = null;
  send(data: string | ArrayBuffer) { this.sent.push(data); }
  close() { this.readyState = WebSocket.CLOSED; }
}

let mockWs: MockWebSocket;
const WsCtor = vi.fn(() => { mockWs = new MockWebSocket(); return mockWs; });

describe('AvatarSession', () => {
  beforeEach(() => {
    vi.stubGlobal('WebSocket', WsCtor);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({
        avatarUrl: 'test.glb',
        cameraView: 'head',
        defaultVoice: 'kokoro:af_heart',
        defaultSpeed: 0.9,
        voices: [{ id: 'kokoro:af_heart', engine: 'kokoro', label: 'Heart' }],
        llms: [{ id: 'claude-haiku-4-5', label: 'Haiku 4.5' }],
      }),
    }));
  });

  afterEach(() => { vi.unstubAllGlobals(); });

  it('fetches config on connect', async () => {
    const { AvatarSession } = await import('./avatar-session.js');
    const session = new AvatarSession({ wsUrl: '/ws/avatar' });
    await session.connect();
    expect(fetch).toHaveBeenCalledWith('/api/avatar/config');
    expect(session.config).not.toBeNull();
    expect(session.config!.avatarUrl).toBe('test.glb');
  });

  it('dispatches avatar:turn on transcript message', async () => {
    const { AvatarSession } = await import('./avatar-session.js');
    const session = new AvatarSession({ wsUrl: '/ws/avatar' });
    await session.connect();
    mockWs.onopen!({} as Event);

    const handler = vi.fn();
    session.addEventListener('avatar:turn', handler);

    mockWs.onmessage!({ data: JSON.stringify({ type: 'transcript', text: 'hello' }) } as MessageEvent);

    expect(handler).toHaveBeenCalled();
    expect(session.turns).toHaveLength(1);
    expect(session.turns[0]!.text).toBe('hello');
  });

  it('sends text with current voice and LLM', async () => {
    const { AvatarSession } = await import('./avatar-session.js');
    const session = new AvatarSession({ wsUrl: '/ws/avatar' });
    await session.connect();
    mockWs.readyState = WebSocket.OPEN;
    mockWs.onopen!({} as Event);

    session.sendText('hi');

    const sent = JSON.parse(mockWs.sent[0] as string);
    expect(sent.type).toBe('text');
    expect(sent.text).toBe('hi');
    expect(sent.ttsModel).toBe('kokoro:af_heart');
  });

  it('updates connectionState on open/close', async () => {
    const { AvatarSession } = await import('./avatar-session.js');
    const session = new AvatarSession({ wsUrl: '/ws/avatar' });
    await session.connect();

    const statusHandler = vi.fn();
    session.addEventListener('avatar:status', statusHandler);

    mockWs.onopen!({} as Event);
    expect(session.connectionState).toBe('connected');

    mockWs.onclose!({ code: 1000, reason: '', wasClean: true } as CloseEvent);
    expect(session.connectionState).toBe('disconnected');
    expect(statusHandler).toHaveBeenCalledTimes(2);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd casehub-mosaic && yarn workspace @casehubio/mosaic-avatar test -- --run avatar-session`
Expected: FAIL — `avatar-session.js` does not exist

- [ ] **Step 3: Implement AvatarSession**

Create `casehub-mosaic/packages/avatar/src/avatar-session.ts`:

```typescript
import type { ConversationTurn, VisemeFrame, PlaybackItem } from './types.js';

export interface AvatarSessionConfig {
  avatarUrl: string;
  cameraView: string;
  defaultVoice: string;
  defaultSpeed: number;
  minPlaybackRate?: number;
  voices: Array<{ id: string; engine: string; label: string }>;
  llms: Array<{ id: string; label: string }>;
}

export class AvatarSession extends EventTarget {
  config: AvatarSessionConfig | null = null;
  turns: ConversationTurn[] = [];
  connectionState: 'connecting' | 'connected' | 'disconnected' = 'disconnected';

  private _wsUrl: string;
  private _configUrl: string;
  private _ws: WebSocket | null = null;
  private _shouldReconnect = true;
  private _reconnectMs: number;
  private _currentVoice = '';
  private _currentLlm = '';
  private _speed = 0.9;
  private _pendingVisemes: VisemeFrame[] | null = null;
  private _audioCtx: AudioContext | null = null;

  constructor(opts: { wsUrl?: string; configUrl?: string; reconnectMs?: number }) {
    super();
    this._wsUrl = opts.wsUrl ?? '/ws/avatar';
    this._configUrl = opts.configUrl ?? '/api/avatar/config';
    this._reconnectMs = opts.reconnectMs ?? 2000;
  }

  get currentVoice() { return this._currentVoice; }
  get currentLlm() { return this._currentLlm; }
  get speed() { return this._speed; }

  async connect(): Promise<void> {
    await this._fetchConfig();
    this._connectWs();
  }

  disconnect(): void {
    this._shouldReconnect = false;
    this._ws?.close();
    this._ws = null;
  }

  sendText(text: string): void {
    this._send(JSON.stringify({
      type: 'text', text,
      llmModel: this._currentLlm,
      ttsModel: this._currentVoice,
    }));
  }

  sendStart(opts: { sampleRate: number }): void {
    this._send(JSON.stringify({
      type: 'start', ...opts,
      llmModel: this._currentLlm,
      ttsModel: this._currentVoice,
    }));
  }

  sendStop(): void {
    this._send(JSON.stringify({ type: 'stop' }));
  }

  sendAudio(buffer: ArrayBuffer): void {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(buffer);
    }
  }

  setVoice(voiceId: string): void {
    this._currentVoice = voiceId;
    this.dispatchEvent(new CustomEvent('avatar:config-changed', { detail: { voice: voiceId } }));
  }

  setLlm(llmId: string): void {
    this._currentLlm = llmId;
    this.dispatchEvent(new CustomEvent('avatar:config-changed', { detail: { llm: llmId } }));
  }

  setSpeed(speed: number): void {
    this._speed = speed;
    this.dispatchEvent(new CustomEvent('avatar:config-changed', { detail: { speed } }));
  }

  private async _fetchConfig(): Promise<void> {
    try {
      const resp = await fetch(this._configUrl);
      if (resp.ok) {
        this.config = await resp.json() as AvatarSessionConfig;
        this._currentVoice = this.config.defaultVoice ?? '';
        this._speed = this.config.defaultSpeed ?? 0.9;
        if (this.config.llms?.length > 0) {
          this._currentLlm = this.config.llms[0]!.id;
        }
        this.dispatchEvent(new CustomEvent('avatar:config-loaded', { detail: this.config }));
      }
    } catch { /* config fetch optional — component works with defaults */ }
  }

  private _connectWs(): void {
    const proto = globalThis.location?.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = globalThis.location?.host ?? 'localhost';
    const url = this._wsUrl.startsWith('ws') ? this._wsUrl : `${proto}//${host}${this._wsUrl}`;
    this._ws = new WebSocket(url);
    this.connectionState = 'connecting';
    this.dispatchEvent(new CustomEvent('avatar:status', { detail: { state: 'connecting' } }));

    this._ws.onopen = () => {
      this.connectionState = 'connected';
      this.dispatchEvent(new CustomEvent('avatar:status', { detail: { state: 'connected' } }));
    };

    this._ws.onmessage = (evt: MessageEvent) => {
      if (typeof evt.data === 'string') {
        this._handleTextMessage(JSON.parse(evt.data));
      } else {
        this._handleBinaryMessage(evt.data as Blob);
      }
    };

    this._ws.onclose = () => {
      this.connectionState = 'disconnected';
      this.dispatchEvent(new CustomEvent('avatar:status', { detail: { state: 'disconnected' } }));
      if (this._shouldReconnect) {
        setTimeout(() => this._connectWs(), this._reconnectMs);
      }
    };

    this._ws.onerror = () => {
      this.connectionState = 'disconnected';
      this.dispatchEvent(new CustomEvent('avatar:status', { detail: { state: 'disconnected' } }));
    };
  }

  private _handleTextMessage(msg: { type: string; [key: string]: unknown }): void {
    switch (msg.type) {
      case 'partial': {
        const last = this.turns[this.turns.length - 1];
        if (last && last.role === 'user' && last.status === 'partial') {
          this.turns = [...this.turns.slice(0, -1), { role: 'user', text: msg.text as string, status: 'partial' }];
        } else {
          this.turns = [...this.turns, { role: 'user', text: msg.text as string, status: 'partial' }];
        }
        this.dispatchEvent(new CustomEvent('avatar:turn', { detail: this.turns }));
        break;
      }
      case 'transcript': {
        const last = this.turns[this.turns.length - 1];
        if (last && last.role === 'user' && last.status === 'partial') {
          this.turns = [...this.turns.slice(0, -1), { role: 'user', text: msg.text as string, status: 'final' }];
        } else {
          this.turns = [...this.turns, { role: 'user', text: msg.text as string, status: 'final' }];
        }
        this.dispatchEvent(new CustomEvent('avatar:turn', { detail: this.turns }));
        break;
      }
      case 'response':
        this.turns = [...this.turns, { role: 'avatar', text: msg.text as string, status: 'final' }];
        this.dispatchEvent(new CustomEvent('avatar:turn', { detail: this.turns }));
        break;
      case 'phonemes':
        this._pendingVisemes = (msg.data as VisemeFrame[]) ?? null;
        break;
      case 'timing':
        this.dispatchEvent(new CustomEvent('avatar:timing', { detail: msg }));
        break;
      case 'error':
        this.dispatchEvent(new CustomEvent('avatar:error', { detail: { message: msg.message } }));
        break;
    }
  }

  private async _handleBinaryMessage(data: Blob | ArrayBuffer): Promise<void> {
    const myVisemes = this._pendingVisemes;
    this._pendingVisemes = null;

    if (!this._audioCtx) this._audioCtx = new AudioContext();
    try {
      const arrayBuf = data instanceof Blob ? await data.arrayBuffer() : data;
      const audioBuf = await this._audioCtx.decodeAudioData(arrayBuf.slice(0));
      const item: PlaybackItem = { audio: audioBuf, timeline: myVisemes && myVisemes.length > 0 ? myVisemes : null };
      this.dispatchEvent(new CustomEvent('avatar:audio', { detail: item }));
    } catch (e) {
      console.error('[AvatarSession] audio decode error:', e);
    }
  }

  private _send(data: string): void {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(data);
    }
  }
}
```

- [ ] **Step 4: Run tests**

Run: `cd casehub-mosaic && yarn workspace @casehubio/mosaic-avatar test -- --run avatar-session`
Expected: PASS (4 tests)

- [ ] **Step 5: Add export to index.ts**

Add to `casehub-mosaic/packages/avatar/src/index.ts`:

```typescript
export { AvatarSession } from './avatar-session.js';
export type { AvatarSessionConfig } from './avatar-session.js';
```

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/casehub-mosaic add packages/avatar/src/avatar-session.ts packages/avatar/src/avatar-session.test.ts packages/avatar/src/index.ts
git -C /Users/mdproctor/claude/casehub/casehub-mosaic commit -m "feat(#449): extract AvatarSession service from AvatarWsController

Shared service that owns WebSocket connection, fetches config from
/api/avatar/config, and publishes typed events. Components subscribe
via addEventListener instead of being coupled to a single Lit host.

Refs casehubio/agency#449"
```

## Batch 3: Mosaic Component Decomposition

### Task 4: casehub-avatar-controls component

**Files:**
- Create: `casehub-mosaic/packages/avatar/src/casehub-avatar-controls.ts`
- Create: `casehub-mosaic/packages/avatar/src/casehub-avatar-controls.test.ts`
- Modify: `casehub-mosaic/packages/avatar/src/index.ts` (add export)

**Interfaces:**
- Consumes: `AvatarSession` from Task 3, `AvatarSessionConfig` type
- Produces: `CasehubAvatarControls` web component (used by Task 5 panel refactor)

- [ ] **Step 1: Write failing test**

Create `casehub-mosaic/packages/avatar/src/casehub-avatar-controls.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

describe('casehub-avatar-controls', () => {
  let el: any;

  const mockSession = {
    config: {
      defaultVoice: 'kokoro:af_heart',
      defaultSpeed: 0.9,
      minPlaybackRate: 0.6,
      voices: [
        { id: 'kokoro:af_heart', engine: 'kokoro', label: 'Heart' },
        { id: 'kokoro:am_adam', engine: 'kokoro', label: 'Adam' },
        { id: 'qwen3:ryan', engine: 'qwen3', label: 'Ryan' },
      ],
      llms: [
        { id: 'claude-haiku-4-5', label: 'Haiku 4.5' },
        { id: 'claude-sonnet-4', label: 'Sonnet 4' },
      ],
    },
    currentVoice: 'kokoro:af_heart',
    currentLlm: 'claude-haiku-4-5',
    speed: 0.9,
    setVoice: vi.fn(),
    setLlm: vi.fn(),
    setSpeed: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  };

  beforeEach(async () => {
    await import('./casehub-avatar-controls.js');
    el = document.createElement('casehub-avatar-controls');
    el.session = mockSession;
    document.body.appendChild(el);
    await el.updateComplete;
  });

  afterEach(() => { document.body.innerHTML = ''; });

  it('has ARIA attributes', () => {
    expect(el.getAttribute('role')).toBe('group');
    expect(el.getAttribute('aria-label')).toBe('Avatar controls');
  });

  it('renders voice options from config', () => {
    const voiceSelect = el.shadowRoot!.querySelector('[data-control="voice"]') as HTMLSelectElement;
    expect(voiceSelect).not.toBeNull();
    const options = voiceSelect.querySelectorAll('option');
    expect(options.length).toBe(3);
  });

  it('renders LLM options from config', () => {
    const llmSelect = el.shadowRoot!.querySelector('[data-control="llm"]') as HTMLSelectElement;
    expect(llmSelect).not.toBeNull();
    const options = llmSelect.querySelectorAll('option');
    expect(options.length).toBe(2);
  });

  it('calls session.setVoice on voice change', () => {
    const voiceSelect = el.shadowRoot!.querySelector('[data-control="voice"]') as HTMLSelectElement;
    voiceSelect.value = 'qwen3:ryan';
    voiceSelect.dispatchEvent(new Event('change'));
    expect(mockSession.setVoice).toHaveBeenCalledWith('qwen3:ryan');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd casehub-mosaic && yarn workspace @casehubio/mosaic-avatar test -- --run casehub-avatar-controls`
Expected: FAIL — module does not exist

- [ ] **Step 3: Implement casehub-avatar-controls**

Create `casehub-mosaic/packages/avatar/src/casehub-avatar-controls.ts`:

```typescript
import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import type { AvatarSession, AvatarSessionConfig } from './avatar-session.js';

@customElement('casehub-avatar-controls')
export class CasehubAvatarControls extends LitElement {
  @property({ attribute: false }) session: AvatarSession | null = null;

  @state() private _config: AvatarSessionConfig | null = null;
  @state() private _voice = '';
  @state() private _llm = '';
  @state() private _speed = 0.9;

  private _configHandler = ((e: CustomEvent) => {
    this._config = e.detail;
    this._syncFromSession();
  }) as EventListener;

  private _changeHandler = (() => {
    this._syncFromSession();
  }) as EventListener;

  static override readonly styles = css`
    :host { display: flex; gap: 1rem; padding: 0.5rem 1rem; align-items: center; justify-content: center; flex-wrap: wrap; }
    label { font-size: 0.8rem; color: var(--pages-on-surface-variant, #999); }
    select { padding: 0.3rem 0.5rem; border-radius: 6px; border: 1px solid var(--pages-neutral-5, #555); background: var(--pages-surface, #2a2a3e); color: var(--pages-on-surface, #e0e0e0); font-size: 0.85rem; }
    input[type="range"] { width: 80px; vertical-align: middle; }
  `;

  override connectedCallback() {
    super.connectedCallback();
    this.setAttribute('role', 'group');
    this.setAttribute('aria-label', 'Avatar controls');
    this._bindSession();
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    this._unbindSession();
  }

  protected override updated(changed: Map<string, unknown>) {
    if (changed.has('session')) {
      this._unbindSession();
      this._bindSession();
    }
  }

  private _bindSession() {
    if (!this.session) return;
    this._config = this.session.config;
    this._syncFromSession();
    this.session.addEventListener('avatar:config-loaded', this._configHandler);
    this.session.addEventListener('avatar:config-changed', this._changeHandler);
  }

  private _unbindSession() {
    this.session?.removeEventListener('avatar:config-loaded', this._configHandler);
    this.session?.removeEventListener('avatar:config-changed', this._changeHandler);
  }

  private _syncFromSession() {
    if (!this.session) return;
    this._voice = this.session.currentVoice;
    this._llm = this.session.currentLlm;
    this._speed = this.session.speed;
  }

  private _onVoiceChange(e: Event) { this.session?.setVoice((e.target as HTMLSelectElement).value); }
  private _onLlmChange(e: Event) { this.session?.setLlm((e.target as HTMLSelectElement).value); }
  private _onSpeedChange(e: Event) { this.session?.setSpeed(parseFloat((e.target as HTMLInputElement).value)); }

  private _groupVoicesByEngine() {
    if (!this._config?.voices) return [];
    const groups = new Map<string, Array<{ id: string; label: string }>>();
    for (const v of this._config.voices) {
      if (!groups.has(v.engine)) groups.set(v.engine, []);
      groups.get(v.engine)!.push(v);
    }
    return [...groups.entries()].map(([engine, voices]) => ({ engine, voices }));
  }

  protected override render() {
    if (!this._config) return html``;
    const voiceGroups = this._groupVoicesByEngine();
    return html`
      ${this._config.llms?.length ? html`
        <label>LLM: <select data-control="llm" @change=${this._onLlmChange}>
          ${this._config.llms.map(l => html`<option value=${l.id} ?selected=${this._llm === l.id}>${l.label}</option>`)}
        </select></label>
      ` : ''}
      <label>Voice: <select data-control="voice" @change=${this._onVoiceChange}>
        ${voiceGroups.map(g => html`
          <optgroup label=${g.engine}>
            ${g.voices.map(v => html`<option value=${v.id} ?selected=${this._voice === v.id}>${v.label}</option>`)}
          </optgroup>
        `)}
      </select></label>
      <label>Speed: <input type="range"
        min=${this._config.minPlaybackRate ?? 0.6} max="1.4" step="0.05"
        .value=${String(this._speed)}
        @input=${this._onSpeedChange}>
        <span>${this._speed}x</span></label>
    `;
  }
}
```

- [ ] **Step 4: Run tests**

Run: `cd casehub-mosaic && yarn workspace @casehubio/mosaic-avatar test -- --run casehub-avatar-controls`
Expected: PASS (4 tests)

- [ ] **Step 5: Add export to index.ts**

Add to `casehub-mosaic/packages/avatar/src/index.ts`:

```typescript
export { CasehubAvatarControls } from './casehub-avatar-controls.js';
```

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/casehub-mosaic add packages/avatar/src/casehub-avatar-controls.ts packages/avatar/src/casehub-avatar-controls.test.ts packages/avatar/src/index.ts
git -C /Users/mdproctor/claude/casehub/casehub-mosaic commit -m "feat(#449): add casehub-avatar-controls data-driven component

Renders LLM picker, voice picker, and speed slider from AvatarSession
config. No hardcoded voice lists — all options come from the backend
YAML configuration.

Refs casehubio/agency#449"
```

### Task 5: Add session support to existing components + refactor panel

**Files:**
- Modify: `casehub-mosaic/packages/avatar/src/casehub-avatar.ts` (add `.session` property)
- Modify: `casehub-mosaic/packages/avatar/src/casehub-transcript.ts` (add `.session` property)
- Modify: `casehub-mosaic/packages/avatar/src/casehub-speech.ts` (add `.session` property)
- Modify: `casehub-mosaic/packages/avatar/src/casehub-avatar-panel.ts` (refactor to use AvatarSession)
- Modify: `casehub-mosaic/packages/avatar/src/casehub-avatar.test.ts` (add session tests)
- Modify: `casehub-mosaic/packages/avatar/src/casehub-transcript.test.ts` (add session tests)
- Modify: `casehub-mosaic/packages/avatar/src/casehub-speech.test.ts` (add session tests)
- Modify: `casehub-mosaic/packages/avatar/src/casehub-avatar-panel.test.ts` (update for refactored panel)

**Interfaces:**
- Consumes: `AvatarSession` from Task 3, `CasehubAvatarControls` from Task 4
- Produces: session-aware versions of all existing components

- [ ] **Step 1: Add session test for casehub-transcript**

Add to `casehub-mosaic/packages/avatar/src/casehub-transcript.test.ts`:

```typescript
it('subscribes to session turns', async () => {
  const mockSession = new EventTarget();
  const el = document.createElement('casehub-transcript') as any;
  el.session = mockSession;
  document.body.appendChild(el);
  await el.updateComplete;

  mockSession.dispatchEvent(new CustomEvent('avatar:turn', {
    detail: [{ role: 'user', text: 'hello', status: 'final' }],
  }));
  await el.updateComplete;

  const turns = el.shadowRoot!.querySelectorAll('.turn');
  expect(turns.length).toBe(1);
  expect(turns[0]!.textContent!.trim()).toBe('hello');
});
```

- [ ] **Step 2: Add `.session` property to casehub-transcript**

Add to `CasehubTranscript` class in `casehub-transcript.ts`:

```typescript
@property({ attribute: false }) session: EventTarget | null = null;

private _turnHandler = ((e: CustomEvent) => {
  this.turns = e.detail;
}) as EventListener;

protected override updated(changed: Map<string, unknown>) {
  super.updated(changed);
  if (changed.has('session')) {
    const prev = changed.get('session') as EventTarget | null;
    prev?.removeEventListener('avatar:turn', this._turnHandler);
    this.session?.addEventListener('avatar:turn', this._turnHandler);
  }
}

override disconnectedCallback() {
  super.disconnectedCallback();
  this.session?.removeEventListener('avatar:turn', this._turnHandler);
}
```

- [ ] **Step 3: Run transcript tests**

Run: `cd casehub-mosaic && yarn workspace @casehubio/mosaic-avatar test -- --run casehub-transcript`
Expected: PASS

- [ ] **Step 4: Add session test for casehub-speech**

Add to `casehub-mosaic/packages/avatar/src/casehub-speech.test.ts`:

```typescript
it('calls session.sendStart on recording start', async () => {
  const mockSession = {
    connectionState: 'connected',
    sendStart: vi.fn(),
    sendStop: vi.fn(),
    sendAudio: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  };
  const el = document.createElement('casehub-speech') as any;
  el.session = mockSession;
  document.body.appendChild(el);
  await el.updateComplete;

  // Session-connected speech should not be disabled
  expect(el.disabled).toBe(false);
});
```

- [ ] **Step 5: Add `.session` property to casehub-speech**

Add to `CasehubSpeech` class in `casehub-speech.ts`:

```typescript
@property({ attribute: false }) session: any | null = null;

private _statusHandler = ((e: CustomEvent) => {
  if (this.session) {
    this.disabled = this.session.connectionState !== 'connected';
  }
}) as EventListener;

protected override updated(changed: Map<string, unknown>) {
  super.updated(changed);
  if (changed.has('session')) {
    const prev = changed.get('session') as any;
    prev?.removeEventListener('avatar:status', this._statusHandler);
    if (this.session) {
      this.session.addEventListener('avatar:status', this._statusHandler);
      this.disabled = this.session.connectionState !== 'connected';
    }
  }
}
```

Then update `_startRecording` and `_stopRecording` to dispatch to session when available:

In `_startRecording`, after `this._recording = true`, add:

```typescript
if (this.session) {
  this.session.sendStart({ sampleRate: this.sampleRate });
}
```

And in `_micProcessor.onaudioprocess`, change the dispatch:

```typescript
if (this.session) {
  this.session.sendAudio(buf);
} else {
  this.dispatchEvent(new CustomEvent('speech:audio', { detail: { buffer: buf }, bubbles: true, composed: true }));
}
```

Similarly for start/stop events — dispatch to session when available, fall back to CustomEvent.

- [ ] **Step 6: Run speech tests**

Run: `cd casehub-mosaic && yarn workspace @casehubio/mosaic-avatar test -- --run casehub-speech`
Expected: PASS

- [ ] **Step 7: Add `.session` property to casehub-avatar**

Add to `CasehubAvatar` class in `casehub-avatar.ts`:

```typescript
@property({ attribute: false }) session: EventTarget | null = null;

private _audioHandler = ((e: CustomEvent) => {
  this._internalQueue.push(e.detail);
  if (!this._processingQueue) this._processQueue();
}) as EventListener;

private _configHandler = ((e: CustomEvent) => {
  const config = e.detail;
  if (config.avatarUrl) this.avatarUrl = config.avatarUrl;
  if (config.cameraView) this.cameraView = config.cameraView;
}) as EventListener;
```

Add session binding in `updated`:

```typescript
if (changed.has('session')) {
  const prev = changed.get('session') as EventTarget | null;
  prev?.removeEventListener('avatar:audio', this._audioHandler);
  prev?.removeEventListener('avatar:config-loaded', this._configHandler);
  this.session?.addEventListener('avatar:audio', this._audioHandler);
  this.session?.addEventListener('avatar:config-loaded', this._configHandler);
}
```

- [ ] **Step 8: Run avatar tests**

Run: `cd casehub-mosaic && yarn workspace @casehubio/mosaic-avatar test -- --run casehub-avatar`
Expected: PASS

- [ ] **Step 9: Refactor casehub-avatar-panel to use AvatarSession**

Rewrite `casehub-avatar-panel.ts` — replace `AvatarWsController` with `AvatarSession`, remove hardcoded `VOICE_GROUPS`, compose sub-components:

```typescript
import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { AvatarSession } from './avatar-session.js';
import type { ConversationTurn, PlaybackItem } from './types.js';
import './casehub-avatar.js';
import './casehub-transcript.js';
import './casehub-speech.js';
import './casehub-avatar-controls.js';

@customElement('casehub-avatar-panel')
export class CasehubAvatarPanel extends LitElement {
  @property({ type: String, attribute: 'ws-url' }) wsUrl = '/ws/avatar';
  @property({ type: String, attribute: 'avatar-url' }) avatarUrl = '';
  @property({ type: String }) body: 'M' | 'F' = 'F';
  @property({ type: String }) mood = 'neutral';
  @property({ attribute: false }) session: AvatarSession | null = null;

  @state() private _statusText = 'Connecting...';
  @state() private _timingText = '';
  @state() private _showAvatar = true;

  private _ownedSession: AvatarSession | null = null;

  static override readonly styles = css`
    :host { display: flex; flex-direction: column; height: 100%; background: var(--pages-background, #1a1a2e); color: var(--pages-on-background, #e0e0e0); font-family: system-ui, sans-serif; }
    casehub-avatar-controls { border-bottom: 1px solid var(--pages-neutral-3, #333); }
    casehub-avatar { flex: 0 0 300px; border-bottom: 1px solid var(--pages-neutral-3, #333); }
    casehub-avatar[hidden] { display: none; }
    .status { padding: 0.25rem 1rem; font-size: 0.75rem; color: var(--pages-on-surface-variant, #666); text-align: center; }
    .timing { margin: 0.5rem 1rem; padding: 0.5rem 0.75rem; background: var(--pages-surface-variant, #1e1e32); border-radius: 8px; font-size: 0.8rem; font-family: monospace; }
    .timing:empty { display: none; }
    casehub-transcript { flex: 1; overflow-y: auto; }
    .input-bar { display: flex; gap: 0.5rem; padding: 1rem; border-top: 1px solid var(--pages-neutral-3, #333); }
    .input-bar input { flex: 1; padding: 0.75rem; border-radius: 8px; border: 1px solid var(--pages-neutral-5, #555); background: var(--pages-surface, #2a2a3e); color: var(--pages-on-surface, #e0e0e0); font-size: 1rem; }
    .input-bar input:focus { outline: none; border-color: var(--pages-primary, #2d5aa0); }
    .input-bar button { padding: 0.75rem 1.5rem; border-radius: 8px; border: none; background: var(--pages-primary, #2d5aa0); color: var(--pages-on-primary, white); font-size: 1rem; cursor: pointer; }
    .input-bar button:disabled { opacity: 0.5; cursor: default; }
  `;

  private get _session(): AvatarSession {
    return this.session ?? this._ownedSession!;
  }

  override connectedCallback() {
    super.connectedCallback();
    this.setAttribute('role', 'region');
    this.setAttribute('aria-label', 'Avatar conversation');
    if (!this.session) {
      this._ownedSession = new AvatarSession({ wsUrl: this.wsUrl });
      this._ownedSession.connect();
    }
    this._session.addEventListener('avatar:status', ((e: CustomEvent) => {
      this._statusText = e.detail.state === 'connected' ? 'Connected'
        : e.detail.state === 'connecting' ? 'Connecting...'
        : 'Disconnected — reconnecting...';
    }) as EventListener);
    this._session.addEventListener('avatar:timing', ((e: CustomEvent) => {
      const t = e.detail;
      this._timingText = `Cleanup: ${t.cleanupMs}ms | LLM: ${t.llmMs}ms | TTS: ${t.ttsMs}ms | Total: ${t.totalMs}ms`;
    }) as EventListener);
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    if (this._ownedSession) {
      this._ownedSession.disconnect();
      this._ownedSession = null;
    }
  }

  private _onSendText() {
    const input = this.shadowRoot!.querySelector<HTMLInputElement>('#msg');
    if (!input) return;
    const text = input.value.trim();
    if (!text) return;
    this._session.sendText(text);
    input.value = '';
    this._statusText = 'Processing...';
  }

  private _onInputKeydown(e: KeyboardEvent) {
    if (e.key === 'Enter') this._onSendText();
  }

  protected override render() {
    const s = this._session;
    const connected = s?.connectionState === 'connected';
    return html`
      <casehub-avatar-controls .session=${s}></casehub-avatar-controls>
      <casehub-avatar
        ?hidden=${!this._showAvatar}
        avatar-url=${this.avatarUrl}
        body=${this.body}
        mood=${this.mood}
        .session=${s}>
      </casehub-avatar>
      <div class="status">${this._statusText}</div>
      <div class="timing">${this._timingText}</div>
      <casehub-transcript .session=${s}></casehub-transcript>
      <div class="input-bar">
        <casehub-speech .session=${s}></casehub-speech>
        <input id="msg" type="text" placeholder="Type a message..."
          autocomplete="off" ?disabled=${!connected}
          @keydown=${this._onInputKeydown}>
        <button ?disabled=${!connected} @click=${this._onSendText}>Send</button>
      </div>
    `;
  }
}
```

- [ ] **Step 10: Update panel test for refactored API**

Update `casehub-avatar-panel.test.ts` to verify the panel creates its own `AvatarSession` when none is provided, and passes session to children.

- [ ] **Step 11: Run all avatar package tests**

Run: `cd casehub-mosaic && yarn workspace @casehubio/mosaic-avatar test -- --run`
Expected: PASS

- [ ] **Step 12: Run typecheck**

Run: `cd casehub-mosaic && yarn workspace @casehubio/mosaic-avatar typecheck`
Expected: No errors

- [ ] **Step 13: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/casehub-mosaic add packages/avatar/src/
git -C /Users/mdproctor/claude/casehub/casehub-mosaic commit -m "feat(#449): decompose avatar panel into composable components

Add .session property to casehub-avatar, casehub-transcript, and
casehub-speech. Each component can now be used independently in any
layout by sharing an AvatarSession instance. casehub-avatar-panel
refactored to thin composition shell using AvatarSession internally.

Backward compatible — existing property-based APIs preserved for
standalone use.

Refs casehubio/agency#449"
```

## References

- [2026-11-19-avatar-mosaic-component-design.md] — design spec this plan implements
- [agentic-yaml/spec/CleanupConfigSpec.java] — existing spec record pattern
- [agentic-yaml/compiler/CompiledConversation.java] — existing compiled record pattern
- [agentic-yaml/registry/SherpaConfigRegistry.java] — existing registry resolve pattern
- [agentic-yaml-deployment/AgenticYamlProcessor.java] — existing build step + recorder pattern
- [agentic-yaml/runtime/AgencySpecsResource.java] — existing REST endpoint pattern
- [agentic-yaml-deployment/AgenticPatternsBuildItem.java] — existing build item pattern
- [speech-ws/TtsModelRegistry.java] — record wrapping Map<String, TextToSpeech>
- [casehub-mosaic/packages/avatar/] — existing mosaic avatar components
- [GitHub #449] — focal issue

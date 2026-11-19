# Speech-WS Gson-to-Jackson Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #521 — Add kebab-case @JsonAlias to speech-ws protocol DTOs
**Issue group:** #521

**Goal:** Migrate speech-ws protocol serialization from Gson to Jackson and add kebab-case aliases to all multi-word record components and type discriminators.

**Architecture:** Replace the hand-written Gson `MessageCodec` with Jackson `ObjectMapper` backed by `@JsonTypeInfo`/`@JsonSubTypes` on the `AvatarMessage` and `Cue` sealed hierarchies. Add `@JsonAlias` on multi-word record components. Preserve the existing camelCase wire format for serialization; kebab-case is accepted only on deserialization.

**Tech Stack:** Jackson 2.x (Quarkus BOM-managed), JUnit 5, AssertJ

## Global Constraints

- Jackson version managed by Quarkus BOM — no explicit version in pom.xml
- Serialization output MUST remain camelCase (backward-compatible)
- Kebab-case is additive (deserialization alias only)
- `charOffset` appearing in Cue wire output is an accepted additive change

---

## Batch 1: Jackson annotations and MessageCodec migration

### Task 1: Add Jackson annotations to protocol records and rewrite MessageCodec

**Files:**
- Modify: `speech-ws/pom.xml` — swap Gson for jackson-databind
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java` — add `@JsonTypeInfo`, `@JsonSubTypes`, `@JsonAlias` on record components
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/Cue.java` — add `@JsonTypeInfo`, `@JsonSubTypes`, `@JsonAlias` on record components
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/VisemeFrame.java` — add `@JsonAlias` on multi-word components
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/MessageCodec.java` — rewrite to use Jackson ObjectMapper
- Test: `speech-ws/src/test/java/io/casehub/agency/speech/ws/protocol/MessageCodecTest.java` — migrate from Gson to Jackson assertions

**Interfaces:**
- Produces: `MessageCodec.encode(AvatarMessage) → String` (unchanged API)
- Produces: `MessageCodec.decodeClient(String) → AvatarMessage` (unchanged API)

- [ ] **Step 1: Write failing test for Jackson round-trip encoding**

Add a new test that encodes a `Segment` message and deserializes it back via Jackson `ObjectMapper` to verify the full round-trip. This will fail because Jackson annotations don't exist yet.

```java
@Test
void roundTripsSegmentViaJackson() throws Exception {
    var visemes = List.of(new VisemeFrame("aa", 0, 200));
    var cues = List.of((Cue) new Cue.GestureCue("nod", 450L));
    var original = new AvatarMessage.Segment(1, 0, "Hello", true, visemes, cues);
    String json = MessageCodec.encode(original);
    AvatarMessage decoded = MessageCodec.decodeClient(json);
    // decodeClient currently rejects "segment" as unknown client type,
    // but once migrated to Jackson it will deserialize all types
    // For now this test just verifies the encode path works
    assertThat(json).contains("\"type\":\"segment\"");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl speech-ws -Dtest=MessageCodecTest#roundTripsSegmentViaJackson -Dsurefire.useFile=false --batch-mode`

- [ ] **Step 3: Swap Gson for Jackson in pom.xml**

Replace the Gson dependency with jackson-databind:

```xml
<!-- Remove -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.12.1</version>
</dependency>

<!-- Add -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

- [ ] **Step 4: Add Jackson annotations to AvatarMessage**

Add `@JsonTypeInfo` and `@JsonSubTypes` to the sealed interface. For multi-word type names, use `names = {...}` with camelCase first (serialization name) and kebab-case second (alias). Add `@JsonAlias` to multi-word record components. Handle the `Start` default `sampleRate` with a `@JsonCreator` static factory if `@JsonProperty(defaultValue)` doesn't work for primitive int.

```java
package io.casehub.agency.speech.ws.protocol;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AvatarMessage.Start.class, name = "start"),
    @JsonSubTypes.Type(value = AvatarMessage.Stop.class, name = "stop"),
    @JsonSubTypes.Type(value = AvatarMessage.Text.class, name = "text"),
    @JsonSubTypes.Type(value = AvatarMessage.Partial.class, name = "partial"),
    @JsonSubTypes.Type(value = AvatarMessage.Transcript.class, name = "transcript"),
    @JsonSubTypes.Type(value = AvatarMessage.Error.class, name = "error"),
    @JsonSubTypes.Type(value = AvatarMessage.SpeakerPrompt.class, names = {"speakerPrompt", "speaker-prompt"}),
    @JsonSubTypes.Type(value = AvatarMessage.SpeakerIdentify.class, names = {"speakerIdentify", "speaker-identify"}),
    @JsonSubTypes.Type(value = AvatarMessage.SpeakerIdentified.class, names = {"speakerIdentified", "speaker-identified"}),
    @JsonSubTypes.Type(value = AvatarMessage.Segment.class, name = "segment"),
    @JsonSubTypes.Type(value = AvatarMessage.TurnComplete.class, names = {"turnComplete", "turn-complete"}),
    @JsonSubTypes.Type(value = AvatarMessage.Gesture.class, name = "gesture"),
    @JsonSubTypes.Type(value = AvatarMessage.Mood.class, name = "mood"),
    @JsonSubTypes.Type(value = AvatarMessage.PlaybackComplete.class, names = {"playbackComplete", "playback-complete"}),
    @JsonSubTypes.Type(value = AvatarMessage.UserActivity.class, names = {"userActivity", "user-activity"})
})
public sealed interface AvatarMessage {
    record Start(@JsonAlias("sample-rate") int sampleRate,
                 @org.jspecify.annotations.Nullable @JsonAlias("llm-model") String llmModel,
                 @org.jspecify.annotations.Nullable @JsonAlias("tts-model") String ttsModel) implements AvatarMessage {
        public Start(int sampleRate) { this(sampleRate, null, null); }
    }

    record Stop() implements AvatarMessage {}

    record Text(String text,
                @org.jspecify.annotations.Nullable @JsonAlias("llm-model") String llmModel,
                @org.jspecify.annotations.Nullable @JsonAlias("tts-model") String ttsModel) implements AvatarMessage {
        public Text(String text) { this(text, null, null); }
    }

    record Partial(String text) implements AvatarMessage {}
    record Transcript(String text) implements AvatarMessage {}
    record Error(String message) implements AvatarMessage {}
    record SpeakerPrompt(String message) implements AvatarMessage {}
    record SpeakerIdentify(String name) implements AvatarMessage {}
    record SpeakerIdentified(String name, double confidence) implements AvatarMessage {}

    record Segment(@JsonAlias("turn-seq") int turnSeq,
                   int index, String text,
                   @JsonAlias("has-audio") boolean hasAudio,
                   List<VisemeFrame> visemes, List<Cue> cues) implements AvatarMessage {
        public Segment {
            visemes = List.copyOf(visemes);
            cues = List.copyOf(cues);
        }
    }

    record TurnComplete(@JsonAlias("turn-seq") int turnSeq,
                        @JsonAlias("segment-count") int segmentCount,
                        TurnOrigin origin) implements AvatarMessage {}

    record Gesture(String tag, GestureOrigin origin) implements AvatarMessage {}
    record Mood(String state) implements AvatarMessage {}

    record PlaybackComplete(@JsonAlias("turn-seq") int turnSeq) implements AvatarMessage {}

    record UserActivity(UserActivityKind kind) implements AvatarMessage {}
}
```

- [ ] **Step 5: Add Jackson annotations to Cue**

```java
package io.casehub.agency.speech.ws.protocol;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Cue.GestureCue.class, name = "gesture"),
    @JsonSubTypes.Type(value = Cue.MoodCue.class, name = "mood")
})
public sealed interface Cue {
    record GestureCue(String tag, @JsonAlias("offset-ms") long offsetMs,
                      @JsonAlias("char-offset") int charOffset) implements Cue {
        public GestureCue(String tag, long offsetMs) { this(tag, offsetMs, -1); }
    }
    record MoodCue(String mood, @JsonAlias("offset-ms") long offsetMs,
                   @JsonAlias("char-offset") int charOffset) implements Cue {
        public MoodCue(String mood, long offsetMs) { this(mood, offsetMs, -1); }
    }
}
```

- [ ] **Step 6: Add @JsonAlias to VisemeFrame**

```java
package io.casehub.agency.speech.ws.protocol;

import com.fasterxml.jackson.annotation.JsonAlias;

public record VisemeFrame(String viseme,
                          @JsonAlias("start-ms") long startMs,
                          @JsonAlias("end-ms") long endMs,
                          double weight) {
    public VisemeFrame {
        if (viseme == null || viseme.isBlank()) { throw new IllegalArgumentException("viseme required"); }
        if (endMs < startMs) { throw new IllegalArgumentException("endMs < startMs"); }
        if (weight < 0 || weight > 1.0) { throw new IllegalArgumentException("weight must be 0..1"); }
    }

    public VisemeFrame(String viseme, long startMs, long endMs) {
        this(viseme, startMs, endMs, 1.0);
    }

    public long durationMs() {
        return endMs - startMs;
    }
}
```

- [ ] **Step 7: Rewrite MessageCodec to use Jackson**

```java
package io.casehub.agency.speech.ws.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;

public final class MessageCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageCodec() {}

    public static String encode(AvatarMessage msg) {
        try {
            return MAPPER.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static AvatarMessage decodeClient(String json) {
        try {
            AvatarMessage msg = MAPPER.readValue(json, AvatarMessage.class);
            return switch (msg) {
                case AvatarMessage.Start _,
                     AvatarMessage.Stop _,
                     AvatarMessage.Text _,
                     AvatarMessage.SpeakerIdentify _,
                     AvatarMessage.PlaybackComplete _,
                     AvatarMessage.UserActivity _ -> msg;
                default -> throw new IllegalArgumentException(
                        "Unknown client message type: " + msg.getClass().getSimpleName());
            };
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid message JSON", e);
        }
    }
}
```

- [ ] **Step 8: Migrate MessageCodecTest to Jackson**

Replace all `com.google.gson.JsonParser` usage with `com.fasterxml.jackson.databind.ObjectMapper.readTree()`. Preserve all existing test assertions. The test structure stays the same — only the JSON parsing utility changes.

Replace imports:
```java
// Remove: import com.google.gson.JsonParser;
// Add:
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
```

Add a shared mapper field:
```java
private static final ObjectMapper MAPPER = new ObjectMapper();
```

Replace every `JsonParser.parseString(json).getAsJsonObject()` with `MAPPER.readTree(json)` and update assertion accessors:
- `obj.get("field").getAsString()` → `node.get("field").asText()`
- `obj.get("field").getAsInt()` → `node.get("field").asInt()`
- `obj.get("field").getAsDouble()` → `node.get("field").asDouble()`
- `obj.get("field").getAsBoolean()` → `node.get("field").asBoolean()`
- `obj.get("field").getAsLong()` → `node.get("field").asLong()`
- `obj.getAsJsonArray("field")` → `node.get("field")` (Jackson `ArrayNode` has `size()`)

Also update the `Start` decode test: Jackson may not automatically default `sampleRate` to 16000 when the field is missing. If the test `decodesStartMessageWithDefaultSampleRate` fails, we need to handle the default in the `Start` record (see Step 9).

- [ ] **Step 9: Run all tests and fix sampleRate default handling**

Run: `mvn test -pl speech-ws -Dtest=MessageCodecTest -Dsurefire.useFile=false --batch-mode`

If `decodesStartMessageWithDefaultSampleRate` fails (Jackson can't construct `Start` without `sampleRate`), add a `@JsonCreator` factory method to `Start`:

```java
record Start(@JsonAlias("sample-rate") int sampleRate,
             @org.jspecify.annotations.Nullable @JsonAlias("llm-model") String llmModel,
             @org.jspecify.annotations.Nullable @JsonAlias("tts-model") String ttsModel) implements AvatarMessage {
    public Start(int sampleRate) { this(sampleRate, null, null); }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static Start create(
            @com.fasterxml.jackson.annotation.JsonProperty("sampleRate") @JsonAlias("sample-rate") Integer sampleRate,
            @com.fasterxml.jackson.annotation.JsonProperty("llmModel") @JsonAlias("llm-model") String llmModel,
            @com.fasterxml.jackson.annotation.JsonProperty("ttsModel") @JsonAlias("tts-model") String ttsModel) {
        return new Start(sampleRate != null ? sampleRate : 16000, llmModel, ttsModel);
    }
}
```

- [ ] **Step 10: Run full speech-ws test suite**

Run: `mvn test -pl speech-ws -Dsurefire.useFile=false --batch-mode`

Verify all 88 tests pass. If any other test classes reference `com.google.gson`, fix their imports too.

- [ ] **Step 11: Commit**

```bash
git add speech-ws/pom.xml \
  speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java \
  speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/Cue.java \
  speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/VisemeFrame.java \
  speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/MessageCodec.java \
  speech-ws/src/test/java/io/casehub/agency/speech/ws/protocol/MessageCodecTest.java
git commit -m "feat(#521): migrate speech-ws protocol from Gson to Jackson

Replace hand-written Gson MessageCodec with Jackson ObjectMapper.
Add @JsonTypeInfo/@JsonSubTypes to AvatarMessage and Cue sealed
hierarchies. Add @JsonAlias kebab-case aliases to all multi-word
record components. Swap Gson dependency for jackson-databind.

Refs casehubio/agency#521

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## Batch 2: Kebab-case deserialization tests

### Task 2: Add kebab-case deserialization tests

**Files:**
- Modify: `speech-ws/src/test/java/io/casehub/agency/speech/ws/protocol/MessageCodecTest.java` — add kebab-case test methods

**Interfaces:**
- Consumes: `MessageCodec.decodeClient(String) → AvatarMessage` from Task 1

- [ ] **Step 1: Write kebab-case type discriminator tests**

Add tests verifying that kebab-case type discriminators are accepted:

```java
@Test
void decodesKebabCasePlaybackComplete() {
    var msg = MessageCodec.decodeClient("{\"type\":\"playback-complete\",\"turnSeq\":3}");
    assertThat(msg).isInstanceOf(AvatarMessage.PlaybackComplete.class);
    assertThat(((AvatarMessage.PlaybackComplete) msg).turnSeq()).isEqualTo(3);
}

@Test
void decodesKebabCaseUserActivity() {
    var msg = MessageCodec.decodeClient("{\"type\":\"user-activity\",\"kind\":\"TYPING\"}");
    assertThat(msg).isInstanceOf(AvatarMessage.UserActivity.class);
    assertThat(((AvatarMessage.UserActivity) msg).kind()).isEqualTo(UserActivityKind.TYPING);
}

@Test
void decodesKebabCaseSpeakerIdentify() {
    var msg = MessageCodec.decodeClient("{\"type\":\"speaker-identify\",\"name\":\"Mark\"}");
    assertThat(msg).isInstanceOf(AvatarMessage.SpeakerIdentify.class);
    assertThat(((AvatarMessage.SpeakerIdentify) msg).name()).isEqualTo("Mark");
}

@Test
void decodesKebabCaseTurnComplete() {
    // turnComplete is server→client, but we test Jackson can deserialize it
    var json = "{\"type\":\"turn-complete\",\"turnSeq\":2,\"segmentCount\":3,\"origin\":\"USER\"}";
    AvatarMessage msg;
    try {
        msg = new ObjectMapper().readValue(json, AvatarMessage.class);
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
    assertThat(msg).isInstanceOf(AvatarMessage.TurnComplete.class);
    var tc = (AvatarMessage.TurnComplete) msg;
    assertThat(tc.turnSeq()).isEqualTo(2);
    assertThat(tc.segmentCount()).isEqualTo(3);
}
```

- [ ] **Step 2: Write kebab-case field alias tests**

```java
@Test
void decodesKebabCaseFieldsInStart() {
    var msg = MessageCodec.decodeClient(
            "{\"type\":\"start\",\"sample-rate\":44100,\"llm-model\":\"gpt-4\",\"tts-model\":\"vits\"}");
    assertThat(msg).isInstanceOf(AvatarMessage.Start.class);
    var start = (AvatarMessage.Start) msg;
    assertThat(start.sampleRate()).isEqualTo(44100);
    assertThat(start.llmModel()).isEqualTo("gpt-4");
    assertThat(start.ttsModel()).isEqualTo("vits");
}

@Test
void decodesKebabCaseFieldsInText() {
    var msg = MessageCodec.decodeClient(
            "{\"type\":\"text\",\"text\":\"hello\",\"llm-model\":\"gpt-4\",\"tts-model\":\"vits\"}");
    assertThat(msg).isInstanceOf(AvatarMessage.Text.class);
    var text = (AvatarMessage.Text) msg;
    assertThat(text.llmModel()).isEqualTo("gpt-4");
    assertThat(text.ttsModel()).isEqualTo("vits");
}

@Test
void decodesKebabCasePlaybackCompleteFields() {
    var msg = MessageCodec.decodeClient(
            "{\"type\":\"playback-complete\",\"turn-seq\":5}");
    assertThat(msg).isInstanceOf(AvatarMessage.PlaybackComplete.class);
    assertThat(((AvatarMessage.PlaybackComplete) msg).turnSeq()).isEqualTo(5);
}
```

- [ ] **Step 3: Run kebab-case tests**

Run: `mvn test -pl speech-ws -Dtest=MessageCodecTest -Dsurefire.useFile=false --batch-mode`

All tests (existing + new) should pass.

- [ ] **Step 4: Run full module test suite**

Run: `mvn test -pl speech-ws -Dsurefire.useFile=false --batch-mode`

All 88+ tests should pass.

- [ ] **Step 5: Commit**

```bash
git add speech-ws/src/test/java/io/casehub/agency/speech/ws/protocol/MessageCodecTest.java
git commit -m "test(#521): add kebab-case deserialization tests for speech-ws protocol

Verify kebab-case type discriminators (playback-complete, user-activity,
speaker-identify, turn-complete) and kebab-case field aliases
(sample-rate, llm-model, tts-model, turn-seq) are accepted.

Refs casehubio/agency#521

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 6: Run full project build**

Run: `mvn install --batch-mode`

Verify the entire project builds clean with no Gson references remaining in speech-ws.

## References

- [2026-11-19-speech-ws-gson-to-jackson-design.md] — design spec this plan implements
- [speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java] — sealed message hierarchy
- [speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/MessageCodec.java] — Gson codec to replace
- [speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/Cue.java] — sealed cue hierarchy
- [speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/VisemeFrame.java] — viseme record
- [speech-ws/src/test/java/io/casehub/agency/speech/ws/protocol/MessageCodecTest.java] — existing test suite
- [speech-ws/pom.xml] — dependency configuration
- [GitHub #521] — focal issue
- [GitHub #520] — @JsonAlias pattern precedent for agentic-yaml

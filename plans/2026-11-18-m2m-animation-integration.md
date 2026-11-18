# M2M Animation Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #457 — Avatar demo: integrate M2M animation library with idle cycle and LLM-tagged gestures
**Issue group:** #457 (depends on landed #458 and TalkingHead fork issue)

**Goal:** Wire 68 M2M body animation clips into the avatar speech pipeline with audio-synced gesture triggering, turn-framed protocol, session concurrency safety, morph target compositing, and mood ownership.

**Architecture:** Server-side `UtteranceSegmenter` parses gesture/mood tags per-sentence, feeds clean text to TTS and tagged cues to the client via `Segment` messages. Client delivers audio and visemes through TalkingHead's `speakAudio` API with `markers` for gesture timing. `BodyAnimationSystem` runs two layers (base pool + gesture one-shot), registered with TalkingHead via the fork's `registerMixer` API (D22). Expression curves compose via the fork's morph layer registry (D21). Session concurrency is solved by capture/turn thread separation.

**Tech Stack:** Java 22 (speech-ws, speech-api), JavaScript ES modules (body-animations.mjs, index.html), three.js 0.180.0, TalkingHead 1.8 (casehubio fork, vendored), Jackson (JSON vocabulary), JUnit 5 + Mockito (tests)

## Global Constraints

- speech-api is framework-free ("no framework annotations — pure Java", #198 design)
- speech-ws uses Quarkus websockets-next annotations
- speech-demo targets browser ES modules via import maps (no bundler)
- All protocol changes are breaking — `Phonemes` and `Response` are removed
- `PerformanceVocabulary` SPI lives in speech-api; JSON implementation in speech-demo
- TalkingHead fork (casehubio/TalkingHead) must land before Batch 4 (client animation)
- Connectors mark timing SPI must land before Batch 3 (D4 integration)
- Vendored `talkinghead.mjs` from fork checked into `speech-demo/.../vendor/` (D19)
- IntelliJ MCP required for all structural code operations

## Prerequisites (not implementation tasks)

Before starting Batch 1:
1. **Land #458** via `work-end`. Recreate #457 branch from updated main.
2. **TalkingHead fork issue** (D24): fork casehubio/TalkingHead, implement four extension points (D20–D23), vendor built `talkinghead.mjs` into agency. Blocks Batch 4 (client animation). Replaces the verification spike (original Batch 0).
3. **File connectors issue:** `TextMark`/`MarkTiming` in `casehub-connectors-voice-spi`, `MarkTimingEnricher`, VITS guarded-exact. Blocks Batch 3.
4. **File neocortex issue:** Caller-supplied proactive prompt sections, non-verbal proactive outcome, delivery feedback. Non-blocking — follows #457.

---

## Batch 0: TalkingHead Fork Prerequisite (separate issue, D24)

**This batch is handled by the TalkingHead fork issue, not #457.** It produces the vendored `talkinghead.mjs` with four extension points. #457's Batches 1–3 (server-side) can proceed in parallel; Batch 4+ (client-side) blocks on this.

The fork issue implements in casehubio/TalkingHead:
1. **Bone ownership (D20)** — canonical bone vocabulary, rig mappings, `claimBones()`/`releaseBones()`, convenience groups
2. **Morph layer registry (D21)** — ordered layers with blend modes, `registerMorphLayer()`
3. **External mixer registration (D22)** — `registerMixer(mixer, { boneClaim })`, coordinated frame update
4. **Lifecycle events (D23)** — EventTarget: `statechange`, `moodchange`, `audiostart`/`audioend`

When complete: vendor the built `talkinghead.mjs` into `speech-demo/src/main/resources/META-INF/resources/vendor/talkinghead.mjs` and update the import map (D19).

**This replaces the original Batch 0 verification spike.** Source analysis confirmed `speakAudio` supports `markers`/`mtimes`, and the fork solves bone/morph ownership. The spike's three questions are answered by construction.

---

## Batch 1: Foundation — Vocabulary SPI and Protocol Types

After this batch: vocabulary is loadable on both server and client, all new protocol message types compile and encode/decode correctly.

### Task 2: Performance Vocabulary JSON and SPI

**Files:**
- Create: `speech-demo/src/main/resources/META-INF/resources/animations/avatar-performance.json`
- Create: `speech-api/src/main/java/io/casehub/agency/speech/PerformanceVocabulary.java`
- Create: `speech-api/src/main/java/io/casehub/agency/speech/GestureEntry.java`
- Create: `speech-api/src/main/java/io/casehub/agency/speech/MoodEntry.java`
- Create: `speech-demo/src/main/java/io/casehub/agency/speech/demo/JsonPerformanceVocabulary.java`
- Test: `speech-demo/src/test/java/io/casehub/agency/speech/demo/JsonPerformanceVocabularyTest.java`
- Test: `speech-demo/src/test/java/io/casehub/agency/speech/demo/VocabularyGlbConsistencyTest.java`

**Interfaces:**
- Produces: `PerformanceVocabulary` SPI — consumed by `UtteranceSegmenter` (Task 4), `GesturePromptSection` (Task 6), `MoodMapper` (Task 8), `body-animations.mjs` (Task 10)

- [ ] **Step 1: Write failing test for vocabulary loading**

```java
@Test
void loadsAllGesturesFromJson() {
    var vocab = new JsonPerformanceVocabulary(
        getClass().getClassLoader().getResourceAsStream("META-INF/resources/animations/avatar-performance.json"));
    assertThat(vocab.gestureTags()).contains("nod", "shrug", "greeting", "angry");
    assertThat(vocab.isValidGesture("nod")).isTrue();
    assertThat(vocab.isValidGesture("nonexistent")).isFalse();
}

@Test
void promptableEntriesHaveDescriptions() {
    var vocab = loadVocab();
    for (var entry : vocab.gesturePromptEntries()) {
        if (entry.promptable()) {
            assertThat(entry.description()).as("gesture %s", entry.tag()).isNotBlank();
        }
    }
}

@Test
void allMoodsLoadWithDescriptions() {
    var vocab = loadVocab();
    assertThat(vocab.moods()).extracting(MoodEntry::name)
        .contains("happy", "sad", "angry", "neutral", "fear", "love", "disgust");
    assertThat(vocab.isValidMood("happy")).isTrue();
    assertThat(vocab.isValidMood("sleepy")).isFalse();
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn --batch-mode test -pl speech-demo -Dtest=JsonPerformanceVocabularyTest
```
Expected: compilation failure — classes don't exist yet.

- [ ] **Step 3: Create the SPI in speech-api**

```java
// speech-api: PerformanceVocabulary.java
package io.casehub.agency.speech;

import java.util.List;
import java.util.Set;

public interface PerformanceVocabulary {
    Set<String> gestureTags();
    boolean isValidGesture(String tag);
    List<GestureEntry> gesturePromptEntries();
    List<MoodEntry> moods();
    boolean isValidMood(String mood);
}

// speech-api: GestureEntry.java
package io.casehub.agency.speech;

public record GestureEntry(String tag, String description, boolean promptable) {}

// speech-api: MoodEntry.java
package io.casehub.agency.speech;

public record MoodEntry(String name, String description, boolean promptable) {}
```

- [ ] **Step 4: Create the JSON vocabulary file**

Build `avatar-performance.json` from the existing `body-animations.mjs` TAXONOMY, GESTURE_MAP, pool refs, and mood set. Structure:

```json
{
  "clips": {
    "idle-subtle": { "strokeMs": 0 },
    "idle-talking": { "strokeMs": 200 },
    "head-nod": { "strokeMs": 150 },
    "shrug": { "strokeMs": 300 },
    ...
  },
  "taxonomy": {
    "conversational": {
      "stance": [{ "clip": "idle-subtle", "weight": 10 }, ...],
      "hand-gesture": [{ "clip": "idle-talking", "weight": 12 }, ...],
      ...
    },
    ...
  },
  "pools": {
    "idle": [{ "subcategory": "idle.relaxed", "weight": 14 }, ...],
    "listening": [{ "subcategory": "idle.relaxed", "weight": 14 }, ...],
    "thinking": [{ "subcategory": "conversational.stance", "weight": 10 }],
    "speaking": [{ "subcategory": "conversational.hand-gesture", "weight": 16 }, ...]
  },
  "gestures": [
    { "tag": "nod", "description": "head nod — agreement, acknowledgement", "target": { "clip": "head-nod" }, "promptable": true },
    { "tag": "shrug", "description": "shoulder shrug — uncertainty, 'I don't know'", "target": { "clip": "shrug" }, "promptable": true },
    { "tag": "greeting", "description": "friendly wave — hello, welcome", "target": { "clip": "greeting" }, "promptable": true },
    { "tag": "agreement", "description": "agreement (random: nod, clap, thumbs-up)", "target": { "subcategory": "conversational.agreement" }, "promptable": true },
    { "tag": "death", "description": "dramatic death fall — only for playful or role-play contexts", "target": { "clip": "death-a" }, "promptable": true },
    { "tag": "zombie", "description": "zombie stance — only for playful or role-play contexts", "target": { "clip": "zombie-idle" }, "promptable": true },
    ...
  ],
  "moods": [
    { "name": "neutral", "description": "calm baseline", "promptable": false },
    { "name": "happy", "description": "cheerful, smiling", "promptable": true },
    { "name": "sad", "description": "downcast, sympathetic", "promptable": true },
    { "name": "angry", "description": "frustrated, stern", "promptable": true },
    { "name": "fear", "description": "worried, anxious", "promptable": true },
    { "name": "love", "description": "warm, affectionate", "promptable": true },
    { "name": "disgust", "description": "repulsed, disapproving", "promptable": true },
    { "name": "sleep", "description": "drowsy, eyes closing — not for conversation", "promptable": false }
  ]
}
```

Populate ALL clips from the #458 `body-animations.mjs` TAXONOMY and GESTURE_MAP. Initial `strokeMs` values: 0 for idle/stance clips, 150–300 for gesture clips (refine with the offline analysis script later).

- [ ] **Step 5: Implement JsonPerformanceVocabulary**

```java
package io.casehub.agency.speech.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.speech.*;
import java.io.InputStream;
import java.util.*;

public class JsonPerformanceVocabulary implements PerformanceVocabulary {
    private final Set<String> gestureTags;
    private final List<GestureEntry> promptEntries;
    private final Set<String> moodNames;
    private final List<MoodEntry> moods;

    public JsonPerformanceVocabulary(InputStream json) {
        var mapper = new ObjectMapper();
        try {
            var root = mapper.readTree(json);
            // Parse gestures
            var gestureList = new ArrayList<GestureEntry>();
            var tags = new HashSet<String>();
            for (var node : root.get("gestures")) {
                var tag = node.get("tag").asText();
                tags.add(tag);
                gestureList.add(new GestureEntry(
                    tag,
                    node.has("description") ? node.get("description").asText() : "",
                    node.has("promptable") && node.get("promptable").asBoolean()));
            }
            this.gestureTags = Set.copyOf(tags);
            this.promptEntries = List.copyOf(gestureList);
            // Parse moods
            var moodList = new ArrayList<MoodEntry>();
            var mNames = new HashSet<String>();
            for (var node : root.get("moods")) {
                var name = node.get("name").asText();
                mNames.add(name);
                moodList.add(new MoodEntry(
                    name,
                    node.has("description") ? node.get("description").asText() : "",
                    node.has("promptable") && node.get("promptable").asBoolean()));
            }
            this.moodNames = Set.copyOf(mNames);
            this.moods = List.copyOf(moodList);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid vocabulary JSON", e);
        }
    }

    @Override public Set<String> gestureTags() { return gestureTags; }
    @Override public boolean isValidGesture(String tag) { return gestureTags.contains(tag); }
    @Override public List<GestureEntry> gesturePromptEntries() { return promptEntries; }
    @Override public List<MoodEntry> moods() { return moods; }
    @Override public boolean isValidMood(String mood) { return moodNames.contains(mood); }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
mvn --batch-mode test -pl speech-demo -Dtest=JsonPerformanceVocabularyTest
```
Expected: all PASS.

- [ ] **Step 7: Write GLB consistency test (D12 build-time test)**

```java
@Test
void everyVocabClipExistsInGlb() throws Exception {
    var vocab = loadVocab();
    // Load GLB clip names (use a simple binary scan or three.js test helper)
    // For now, parse clip names from the GLB's JSON chunk
    var glbClipNames = extractGlbClipNames(
        getClass().getClassLoader().getResourceAsStream("META-INF/resources/animations/m2m-animations.glb"));
    for (var clipName : vocab.allClipNames()) {
        assertThat(glbClipNames).as("vocab clip '%s' missing from GLB", clipName).contains(clipName);
    }
    for (var glbClip : glbClipNames) {
        assertThat(vocab.allClipNames()).as("GLB clip '%s' missing from vocab", glbClip).contains(glbClip);
    }
}
```

Add `allClipNames()` to `PerformanceVocabulary` and implement in `JsonPerformanceVocabulary`.

- [ ] **Step 8: Run all tests, commit**

```bash
mvn --batch-mode test -pl speech-api,speech-demo
git add speech-api/ speech-demo/
git commit -m "feat(#457): performance vocabulary SPI and JSON implementation (D12)"
```

### Task 3: Protocol Types — Segment, TurnComplete, Gesture, Mood, Cue

**Files:**
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java`
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/MessageCodec.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/Cue.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/TurnOrigin.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/GestureOrigin.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/UserActivityKind.java`
- Test: `speech-ws/src/test/java/io/casehub/agency/speech/ws/protocol/MessageCodecTest.java`

**Interfaces:**
- Consumes: existing `VisemeFrame` record
- Produces: `Segment`, `TurnComplete`, `Gesture`, `Mood`, `PlaybackComplete`, `UserActivity` message types — consumed by all subsequent server and client tasks

- [ ] **Step 1: Write failing codec round-trip tests**

```java
@Test
void encodesAndDecodesSegment() {
    var cues = List.of(new Cue.GestureCue("nod", 450L));
    var visemes = List.of(new VisemeFrame("aa", 0, 200, 0.8));
    var segment = new AvatarMessage.Segment(1, 0, "Hello world", true, visemes, cues);
    var json = MessageCodec.encode(segment);
    assertThat(json).contains("\"type\":\"segment\"");
    assertThat(json).contains("\"turnSeq\":1");
    assertThat(json).contains("\"gesture\"");
}

@Test
void encodesAndDecodesTurnComplete() {
    var msg = new AvatarMessage.TurnComplete(1, 2, TurnOrigin.USER);
    var json = MessageCodec.encode(msg);
    assertThat(json).contains("\"type\":\"turnComplete\"");
    assertThat(json).contains("\"segmentCount\":2");
}

@Test
void decodesClientPlaybackComplete() {
    var json = "{\"type\":\"playbackComplete\",\"turnSeq\":1}";
    var msg = MessageCodec.decodeClient(json);
    assertThat(msg).isInstanceOf(AvatarMessage.PlaybackComplete.class);
    assertThat(((AvatarMessage.PlaybackComplete) msg).turnSeq()).isEqualTo(1);
}

@Test
void decodesClientUserActivity() {
    var json = "{\"type\":\"userActivity\",\"kind\":\"typing\"}";
    var msg = MessageCodec.decodeClient(json);
    assertThat(msg).isInstanceOf(AvatarMessage.UserActivity.class);
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn --batch-mode test -pl speech-ws -Dtest=MessageCodecTest
```
Expected: compilation failure.

- [ ] **Step 3: Add protocol types to AvatarMessage**

```java
// New types added to the sealed interface:
record Segment(int turnSeq, int index, String text, boolean hasAudio,
               List<VisemeFrame> visemes, List<Cue> cues) implements AvatarMessage {}
record TurnComplete(int turnSeq, int segmentCount, TurnOrigin origin) implements AvatarMessage {}
record Gesture(String tag, GestureOrigin origin) implements AvatarMessage {}
record Mood(String state) implements AvatarMessage {}
record PlaybackComplete(int turnSeq) implements AvatarMessage {}
record UserActivity(UserActivityKind kind) implements AvatarMessage {}
```

```java
// Cue.java — sealed interface
package io.casehub.agency.speech.ws.protocol;
public sealed interface Cue {
    record GestureCue(String tag, long offsetMs) implements Cue {}
    record MoodCue(String mood, long offsetMs) implements Cue {}
}

// TurnOrigin.java
package io.casehub.agency.speech.ws.protocol;
public enum TurnOrigin { USER, PROACTIVE }

// GestureOrigin.java
package io.casehub.agency.speech.ws.protocol;
public enum GestureOrigin { ATTENTION, COGNITION }

// UserActivityKind.java
package io.casehub.agency.speech.ws.protocol;
public enum UserActivityKind { TYPING, VISIBILITY_CHANGE }
```

- [ ] **Step 4: Update MessageCodec — encode new types, decode new client messages**

Extend `encode()` with cases for `Segment`, `TurnComplete`, `Gesture`, `Mood`. Extend `decodeClient()` with cases for `PlaybackComplete`, `UserActivity`. Keep existing types working during migration (they're removed in Task 5 when the session refactor is complete).

- [ ] **Step 5: Run tests, commit**

```bash
mvn --batch-mode test -pl speech-ws -Dtest=MessageCodecTest
git add speech-ws/
git commit -m "feat(#457): turn-framed protocol types — Segment, TurnComplete, Gesture, Mood, Cue (D3)"
```

### Task 4: UtteranceSegmenter and Consumer Forms

**Files:**
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/UtteranceSegmenter.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/ParsedSegment.java`
- Test: `speech-ws/src/test/java/io/casehub/agency/speech/ws/UtteranceSegmenterTest.java`

**Interfaces:**
- Consumes: `PerformanceVocabulary` (Task 2) for tag validation
- Produces: `UtteranceSegmenter` — consumed by session pipeline (Task 5), returns `ParsedSegment` with clean text, cues, and canonical markup

- [ ] **Step 1: Write failing tests for segmenter rules (D2)**

```java
@Test
void stripsGestureTagsFromCleanText() {
    var seg = segmenter.segment("Hello [gesture:greeting] everyone!");
    assertThat(seg).hasSize(1);
    assertThat(seg.get(0).cleanText()).isEqualTo("Hello everyone!");
    assertThat(seg.get(0).cues()).hasSize(1);
    assertThat(seg.get(0).cues().get(0)).isInstanceOf(Cue.GestureCue.class);
}

@Test
void forwardBindsTagToNextWord() {
    var seg = segmenter.segment("[gesture:greeting] Hello!");
    assertThat(seg.get(0).cues().get(0))
        .extracting("tag", "wordIndex")
        .containsExactly("greeting", 0);
}

@Test
void trailingTagBindsToFirstWord() {
    var seg = segmenter.segment("Nice to meet you! [gesture:greeting]");
    assertThat(seg).hasSize(1);
    assertThat(seg.get(0).cues().get(0))
        .extracting("wordIndex").isEqualTo(0);
}

@Test
void dropsInvalidGestureTags() {
    var seg = segmenter.segment("Hello [gesture:nonexistent] world.");
    assertThat(seg.get(0).cues()).isEmpty();
    assertThat(seg.get(0).cleanText()).isEqualTo("Hello world.");
}

@Test
void stripsMoodTags() {
    var seg = segmenter.segment("Great news! [mood:happy]");
    assertThat(seg.get(0).cleanText()).isEqualTo("Great news!");
    assertThat(seg.get(0).cues()).hasSize(1);
    assertThat(seg.get(0).cues().get(0)).isInstanceOf(Cue.MoodCue.class);
}

@Test
void splitsSentences() {
    var segs = segmenter.segment("Hello! [gesture:greeting] How are you? [gesture:nod] Fine.");
    assertThat(segs).hasSize(3);
    assertThat(segs.get(0).cleanText()).isEqualTo("Hello!");
    assertThat(segs.get(1).cleanText()).isEqualTo("How are you?");
    assertThat(segs.get(2).cleanText()).isEqualTo("Fine.");
}

@Test
void producesCanonicalMarkupForHistory() {
    var seg = segmenter.segment("Hello [gesture:greeting] everyone! [gesture:invalid]");
    assertThat(seg.get(0).canonicalMarkup()).isEqualTo("[gesture:greeting] Hello everyone!");
}

@Test
void noEmptySegments() {
    var segs = segmenter.segment("[gesture:greeting]");
    assertThat(segs).isEmpty();
}

@Test
void handlesMalformedTags() {
    var seg = segmenter.segment("Hello [gesture:truncat");
    assertThat(seg.get(0).cleanText()).isEqualTo("Hello");
    assertThat(seg.get(0).cues()).isEmpty();
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
mvn --batch-mode test -pl speech-ws -Dtest=UtteranceSegmenterTest
```

- [ ] **Step 3: Implement ParsedSegment and UtteranceSegmenter**

```java
// ParsedSegment.java
package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.ws.protocol.Cue;
import java.util.List;

public record ParsedSegment(
    String cleanText,
    List<Cue> cues,
    String canonicalMarkup
) {
    public int wordCount() {
        return cleanText.isBlank() ? 0 : cleanText.trim().split("\\s+").length;
    }
}
```

```java
// UtteranceSegmenter.java
package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.PerformanceVocabulary;
import io.casehub.agency.speech.ws.protocol.Cue;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

public class UtteranceSegmenter {
    private static final Pattern TAG_RE = Pattern.compile("\\[(gesture|mood):([a-z0-9_-]+)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_RE = Pattern.compile("(?<=[.!?])\\s+");
    private final PerformanceVocabulary vocabulary;

    public UtteranceSegmenter(PerformanceVocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    public List<ParsedSegment> segment(String text) {
        // 1. Extract all tags with their positions
        // 2. Strip tags to get clean text
        // 3. Split clean text into sentences
        // 4. Map tags to their sentences by character offset
        // 5. For each sentence: build cues with word indices, validate, produce canonical markup
        // Implementation follows D2 rules: forward binding, trailing tag → first word,
        // drop invalid, no empty segments, strip malformed
        // ... (full implementation)
    }
}
```

The full implementation handles all D2 segmentation rules (forward binding, trailing tags, malformed markup stripping, empty segment elimination). Reference decisions.md D2 for the complete rule set.

- [ ] **Step 4: Run tests, commit**

```bash
mvn --batch-mode test -pl speech-ws -Dtest=UtteranceSegmenterTest
git add speech-ws/
git commit -m "feat(#457): UtteranceSegmenter with consumer forms (D2, D9)"
```

---

## Batch 2: Server Session Refactor

After this batch: SpeechSession has capture/turn separation, turn serialisation, proactive suppression, and PromptSection composition. The server speaks the new protocol.

### Task 5: Capture/Turn Split and TurnExecutor

**Files:**
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechSession.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/TurnExecutor.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/TurnRequest.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/FloorState.java`
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechWebSocket.java`
- Test: `speech-ws/src/test/java/io/casehub/agency/speech/ws/TurnExecutorTest.java`
- Test: `speech-ws/src/test/java/io/casehub/agency/speech/ws/SpeechSessionTest.java` (update existing)

**Interfaces:**
- Consumes: `UtteranceSegmenter` (Task 4), protocol types (Task 3)
- Produces: `TurnExecutor` — serialises turns, `FloorState` — activity tracking. Used by Task 6 (prompt), Task 7 (timing), Task 8 (mood), Task 9 (attention)

This is the largest task. It restructures `SpeechSession` per D10:
- Capture side owns: STT stream, ring buffer, speaker ID, partial polling
- Turn side owns: history, prompt assembly, LLM, TTS, outbound messages
- `FloorState` is an atomic lock-free object tracking last activity and unacknowledged turns

Key implementation notes from D10:
- `Stop` is split at the boundary: capture side finalises STT + speaker ID + sends Transcript, then enqueues `UserTurn` to executor
- Proactive turns carry `evaluatedAt` and are dropped at dequeue if stale
- `TurnComplete` is sent in a `finally` block (error paths close the turn)
- TTS failure for one sentence sends `Segment(hasAudio=false)` and continues

- [ ] **Step 1: Write failing tests for turn serialisation**

```java
@Test
void turnsExecuteSerially() throws Exception {
    var executor = new TurnExecutor(/* mocks */);
    var order = new CopyOnWriteArrayList<String>();
    executor.submit(new UserTurn("first", ...));
    executor.submit(new UserTurn("second", ...));
    // Verify "first" completes before "second" starts
}

@Test
void proactiveTurnDroppedWhenStale() throws Exception {
    var executor = new TurnExecutor(/* mocks */);
    var floor = new FloorState();
    floor.recordUserActivity(); // activity after evaluation
    var proactive = new ProactiveTurn("text", Instant.now().minusSeconds(1));
    executor.submit(proactive);
    // Verify turn was dropped (logged, not executed)
}

@Test
void turnCompleteSentOnError() throws Exception {
    // Mock LLM to throw, verify TurnComplete is still sent
}
```

- [ ] **Step 2–4: Implement TurnExecutor, FloorState, refactor SpeechSession**

Follow D10's field ownership table. The refactor is mechanical but large — move fields to their owning side, extract `TurnExecutor` as a single-thread virtual-thread executor, wire `FloorState` for activity tracking.

- [ ] **Step 5: Wire new protocol — send Segments instead of Phonemes/Response**

In the turn executor's speech loop, for each sentence:
1. Run `UtteranceSegmenter` on full LLM text
2. For each `ParsedSegment`: TTS clean text, build `Segment` message, send it
3. Send binary audio with 8-byte header (`version=1, segmentIndex, turnSeq`)
4. After all segments: send `TurnComplete`

- [ ] **Step 6: Handle new client messages in SpeechWebSocket**

Add `PlaybackComplete` and `UserActivity` to `@OnTextMessage` handler. Route to `FloorState`.

- [ ] **Step 7: Run all speech-ws tests, commit**

```bash
mvn --batch-mode test -pl speech-ws
git add speech-ws/
git commit -m "feat(#457): capture/turn split, TurnExecutor, Segment protocol (D10, D3)"
```

### Task 6: PromptSection Composition and GesturePromptSection

**Files:**
- Modify: `speech-api/src/main/java/io/casehub/agency/speech/SpeechPromptAssembler.java`
- Modify: `speech-api/src/main/java/io/casehub/agency/speech/PromptSection.java`
- Create: `speech-api/src/main/java/io/casehub/agency/speech/PromptContext.java`
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/DefaultPromptAssembler.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/GesturePromptSection.java`
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechWebSocket.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/social/cognition/CognitionAvatarAdapter.java`
- Test: `speech-ws/src/test/java/io/casehub/agency/speech/ws/GesturePromptSectionTest.java`
- Test: `speech-ws/src/test/java/io/casehub/agency/speech/ws/DefaultPromptAssemblerTest.java`

**Interfaces:**
- Consumes: `PerformanceVocabulary` (Task 2), `PromptContext` (new)
- Produces: gesture-instructed system prompts. Consumed by TurnExecutor (Task 5)

- [ ] **Step 1: Write failing tests**

```java
@Test
void gesturePromptSectionRendersDescriptions() {
    var vocab = loadTestVocab();
    var section = new GesturePromptSection(vocab);
    var text = section.render(new PromptContext("avatar", "default", null));
    assertThat(text).contains("[gesture:nod]");
    assertThat(text).contains("head nod");
    assertThat(text).contains("at most one tag per sentence");
}

@Test
void assemblerComposesPromptSections() {
    var section = new GesturePromptSection(loadTestVocab());
    var assembler = new DefaultPromptAssembler(Optional.of("Base prompt"), List.of(section));
    var prompt = assembler.assemble(new PromptContext("avatar", "default", null), "Hello", List.of());
    assertThat(prompt.systemPrompt()).contains("Base prompt");
    assertThat(prompt.systemPrompt()).contains("[gesture:");
}
```

- [ ] **Step 2–4: Implement PromptContext, update SpeechPromptAssembler signature, implement GesturePromptSection, update DefaultPromptAssembler**

`PromptContext` record: `(String agentId, String tenantId, @Nullable String subjectId)`.

`PromptSection` gains `default int order() { return 0; }`.

`DefaultPromptAssembler` takes `List<PromptSection>` at construction, renders them in order, appends to system prompt.

`GesturePromptSection` renders the vocabulary's promptable entries with descriptions and rules (D7).

Update `CognitionAvatarAdapter.wrapAssembler` to accept `PromptContext` parameter — loses its closure parameters.

- [ ] **Step 5: Run tests, commit**

```bash
mvn --batch-mode test -pl speech-api,speech-ws
git add speech-api/ speech-ws/ agency-core/
git commit -m "feat(#457): PromptSection composition, GesturePromptSection, PromptContext (D7)"
```

---

## Batch 3: Server Features — Timing, Mood, Attention

After this batch: server delivers gesture cues with mark timing, owns mood, and can bid for attention. Connectors mark timing SPI must have landed.

### Task 7: Mark Timing Integration

**Files:**
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechSession.java` (turn executor path)
- Test: `speech-ws/src/test/java/io/casehub/agency/speech/ws/MarkTimingIntegrationTest.java`

**Interfaces:**
- Consumes: connectors `TextMark`/`MarkTiming`/`MarkTimingEnricher` (from landed connectors issue), `UtteranceSegmenter` (Task 4)
- Produces: `Cue.GestureCue` with accurate `offsetMs` values in `Segment` messages

- [ ] **Step 1: Write failing test for mark timing**

```java
@Test
void gestureCueCarriesOffsetFromMarkTiming() {
    // Mock TTS to return MarkTiming for "nod" at 450ms
    var tts = mock(TextToSpeech.class);
    when(tts.synthesise(any())).thenReturn(new SynthesisResult(
        wavBytes, AudioFormat.WAV_16K,
        List.of(phonemes),
        List.of(new MarkTiming("gesture:nod", 450L))));
    // Run the sentence through the turn executor
    // Verify the Segment's GestureCue has offsetMs=450
}
```

- [ ] **Step 2–3: Wire mark timing into the turn executor's TTS call**

For each `ParsedSegment` with gesture cues:
1. Build `TextMark` entries from cue positions
2. Pass marks in `SynthesisOptions`
3. Read `MarkTiming` from `SynthesisResult`
4. Set `offsetMs` on `GestureCue` from the matched `MarkTiming`
5. Fall back to `MarkTimingEnricher` if TTS returns no marks

- [ ] **Step 4: Run tests, commit**

```bash
mvn --batch-mode test -pl speech-ws
git add speech-ws/
git commit -m "feat(#457): mark timing integration for gesture cue offsets (D4)"
```

### Task 8: MoodMapper and Affect SPI

**Files:**
- Create: `speech-api/src/main/java/io/casehub/agency/speech/Affect.java`
- Modify: `speech-api/src/main/java/io/casehub/agency/speech/AvatarCognition.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/MoodMapper.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/social/cognition/CognitionAvatarAdapter.java`
- Test: `speech-ws/src/test/java/io/casehub/agency/speech/ws/MoodMapperTest.java`

**Interfaces:**
- Consumes: `PerformanceVocabulary` moods (Task 2), `AvatarCognition.currentAffect()` (new)
- Produces: `Mood` messages to client. Mood ownership decision per session.

- [ ] **Step 1: Write failing tests for PAD→mood mapping (D16)**

```java
@Test
void highPleasureHighArousalMapsToHappy() {
    var mapper = new MoodMapper(loadTestVocab());
    assertThat(mapper.map(new Affect(0.6, 0.6, 0.0))).isEqualTo("happy");
}

@Test
void lowPleasureHighArousalHighDominanceMapsToAngry() {
    var mapper = new MoodMapper(loadTestVocab());
    assertThat(mapper.map(new Affect(-0.6, 0.6, 0.6))).isEqualTo("angry");
}

@Test
void deadZoneMapsToNeutral() {
    var mapper = new MoodMapper(loadTestVocab());
    assertThat(mapper.map(new Affect(0.1, 0.1, 0.0))).isEqualTo("neutral");
}

@Test
void hysteresisPreventsBoundaryFlapping() {
    var mapper = new MoodMapper(loadTestVocab());
    mapper.map(new Affect(0.6, 0.6, 0.0)); // → happy
    // Slight shift shouldn't change yet (within hysteresis margin)
    assertThat(mapper.mapWithHysteresis(new Affect(0.5, 0.5, 0.0))).isEqualTo("happy");
}
```

- [ ] **Step 2–4: Implement Affect record, AvatarCognition.currentAffect, MoodMapper, CognitionAvatarAdapter bridge**

```java
// speech-api: Affect.java
package io.casehub.agency.speech;
public record Affect(double pleasure, double arousal, double dominance) {}
```

Add to `AvatarCognition`: `boolean providesAffect()` and `Optional<Affect> currentAffect(String agentId, String tenantId)`.

`CognitionAvatarAdapter` implements from `MoodOrchestrator.currentMood()` → `MoodState` → `Affect`.

`MoodMapper` implements octant mapping with dead-zone (0.25), hysteresis margins (0.1), 5s dwell.

- [ ] **Step 5: Run tests, commit**

```bash
mvn --batch-mode test -pl speech-api,speech-ws,agency-core
git add speech-api/ speech-ws/ agency-core/
git commit -m "feat(#457): MoodMapper, Affect SPI, cognition mood bridge (D16)"
```

### Task 9: SilenceAttentionPolicy and Adherence Logging

**Files:**
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/SilenceAttentionPolicy.java`
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/AvatarConfig.java`
- Test: `speech-ws/src/test/java/io/casehub/agency/speech/ws/SilenceAttentionPolicyTest.java`

**Interfaces:**
- Consumes: `FloorState` (Task 5), `PerformanceVocabulary` (Task 2)
- Produces: `Gesture(tag, ATTENTION)` messages. Adherence log lines per turn.

- [ ] **Step 1: Write failing tests**

```java
@Test
void firesFirstBidAfter30Seconds() {
    var policy = new SilenceAttentionPolicy(vocab, Duration.ofSeconds(30), Duration.ofSeconds(75));
    var clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    policy.recordActivity(clock.instant());
    // Advance clock 31 seconds
    var bid = policy.evaluate(clock.instant().plusSeconds(31));
    assertThat(bid).isPresent();
    assertThat(bid.get()).isIn(vocab.gestureTags()); // from idle.attention
}

@Test
void firesSecondBidAfter75Seconds() {
    // ... after first bid at 30s, second at 75s
}

@Test
void noMoreBidsAfterSecond() {
    // ... after both bids, no further bids
}

@Test
void resetsOnActivity() {
    var policy = new SilenceAttentionPolicy(vocab, Duration.ofSeconds(30), Duration.ofSeconds(75));
    policy.recordActivity(Instant.now());
    // No bids even after 30s if activity happened
}
```

- [ ] **Step 2–3: Implement SilenceAttentionPolicy and adherence logging**

`SilenceAttentionPolicy` is opt-in via `AvatarConfig` (`attention.enabled`, default false). Picks from `idle.attention` subcategory in vocabulary. Two escalation levels, then stops.

Adherence logging: after each turn, log gesture tag count (present, valid, dropped) at INFO level.

- [ ] **Step 4: Run tests, commit**

```bash
mvn --batch-mode test -pl speech-ws
git add speech-ws/
git commit -m "feat(#457): SilenceAttentionPolicy (D8), adherence logging (D15)"
```

---

## Batch 4: Client Animation Engine

**Requires:** TalkingHead fork (Batch 0 / D24) must have landed — the vendored `talkinghead.mjs` with extension points must be in `vendor/`.

After this batch: `body-animations.mjs` loads from vocabulary, registers its mixer and morph layers with TalkingHead via fork APIs, runs layered animation with expression curves, and has a floor state machine. No protocol wiring yet.

### Task 10: body-animations.mjs Vocabulary-Driven Rewrite + Layered Animation

**Files:**
- Modify: `speech-demo/src/main/resources/META-INF/resources/animations/body-animations.mjs`
- Modify: `speech-demo/src/main/resources/META-INF/resources/index.html` (import map, remove rAF loop)
- Test: manual browser test via animation picker

**Interfaces:**
- Consumes: `avatar-performance.json` (Task 2), TalkingHead fork APIs (`registerMixer`, `claimBones`)
- Produces: `BodyAnimationSystem` with vocabulary-driven pools, base+gesture layers (D11), floor states (D13), registered with TalkingHead (D22). Consumed by Task 12 (protocol integration).

- [ ] **Step 0: Update import map to vendored TalkingHead fork**

Change `index.html` import map from:
```
"talkinghead": "https://cdn.jsdelivr.net/gh/met4citizen/TalkingHead@1.8/modules/talkinghead.mjs"
```
to:
```
"talkinghead": "./vendor/talkinghead.mjs"
```

- [ ] **Step 1: Rewrite BodyAnimationSystem to load from vocabulary and register with TalkingHead**

Delete all data constants (`TAXONOMY`, `GESTURE_MAP`, `KEYWORD_GESTURES`, `SPEAKING_POOL_REFS`, `IDLE_POOL_REFS`). Replace with a `fetch("animations/avatar-performance.json")` in `load()`. Build all runtime structures from the fetched data.

In `load()`, register the mixer with TalkingHead via the fork API:
```javascript
head.registerMixer(this._mixer, {
  boneClaim: ['spine', 'spine1', 'spine2', 'hips',
              'leftUpperArm', 'rightUpperArm', 'leftForeArm', 'rightForeArm',
              'leftHand', 'rightHand', 'leftUpLeg', 'rightUpLeg',
              'leftLeg', 'rightLeg', 'leftFoot', 'rightFoot']
});
```

Delete the separate `tickBody` rAF loop from `index.html` — TalkingHead now calls `mixer.update(dt)` in its own frame loop.

- [ ] **Step 2: Implement two-layer animation (D11)**

```javascript
class BodyAnimationSystem {
  constructor() {
    this._baseAction = null;    // looping pool clip
    this._gestureAction = null; // one-shot gesture
    this._baseWeight = 1.0;
    this._gestureWeight = 0.0;
    // ...
  }

  _playBase(name) {
    // Swap base layer clip. If gesture is playing, duck base weight to 0.
    const clip = this._clips.get(name);
    if (!clip) return;
    const action = this._mixer.clipAction(clip);
    action.setLoop(THREE.LoopRepeat);
    if (this._baseAction && this._baseAction !== action) {
      action.reset().play();
      this._baseAction.crossFadeTo(action, CROSSFADE_DURATION, true);
    } else {
      action.reset().play();
    }
    this._baseAction = action;
    // If gesture is playing, keep base ducked
    if (this._gestureAction && this._gestureAction.isRunning()) {
      action.setEffectiveWeight(0);
    }
  }

  playGesture(name) {
    const clipName = this._resolveGesture(name);
    const clip = this._clips.get(clipName);
    if (!clip) return false;

    const action = this._mixer.clipAction(clip);
    action.setLoop(THREE.LoopOnce, 1);
    action.clampWhenFinished = true;
    action.reset().play();

    // Duck base
    if (this._baseAction) {
      this._baseAction.setEffectiveWeight(0);
    }
    action.setEffectiveWeight(1);
    this._gestureAction = action;

    // On finish, restore base
    const onFinished = (e) => {
      if (e.action === action) {
        this._mixer.removeEventListener("finished", onFinished);
        this._gestureAction = null;
        if (this._baseAction) {
          this._baseAction.setEffectiveWeight(1);
        }
      }
    };
    this._mixer.addEventListener("finished", onFinished);
    return true;
  }
}
```

- [ ] **Step 3: Implement floor state machine (D13)**

```javascript
const FLOOR_STATES = { IDLE: "idle", LISTENING: "listening", THINKING: "thinking", SPEAKING: "speaking" };

setFloorState(state) {
  if (this._floorState === state) return;
  this._floorState = state;
  this._playFromPool(state);
  this._startCycle(state);
}

_playFromPool(state) {
  const poolRefs = this._pools[state] || this._pools["idle"];
  const name = this._pickFromPool(poolRefs);
  if (name) this._playBase(name);
}
```

- [ ] **Step 4: Delete parseGestureTags and classifyResponse exports (D2, D15)**

Remove `parseGestureTags()`, `classifyResponse()`, `KEYWORD_GESTURES`, and `GESTURE_TAG_RE` from `body-animations.mjs`. The server is the single parser (D2). Keyword fallback is dropped (D15).

- [ ] **Step 5: Test via animation picker in browser, commit**

Start dev server, open demo page, verify animation picker still works with vocabulary-loaded clips. Verify idle cycling works across all floor states.

```bash
git add speech-demo/
git commit -m "feat(#457): vocabulary-driven BodyAnimationSystem with layered animation (D11, D12, D13)"
```

### Task 11: Expression Curves and Morph Layer Registration

**Files:**
- Modify: `speech-demo/src/main/resources/META-INF/resources/animations/body-animations.mjs`
- Modify: `speech-demo/src/main/resources/META-INF/resources/index.html`
- Test: manual browser test

**Interfaces:**
- Consumes: `BodyAnimationSystem` (Task 10), TalkingHead fork morph layer API (`registerMorphLayer`)
- Produces: registered morph layers for visemes and expression curves (D21). Compositing handled natively by TalkingHead's layer pipeline.

- [ ] **Step 1: Extract expression curves at load (D6)**

```javascript
const EXPRESSION_ALLOW = new Set([
  "browDownLeft", "browDownRight", "browInnerUp", "browOuterUpLeft", "browOuterUpRight",
  "mouthFrownLeft", "mouthFrownRight", "mouthSmileLeft", "mouthSmileRight",
  "mouthPressLeft", "mouthPressRight", "cheekPuff", "cheekSquintLeft", "cheekSquintRight",
  "noseSneerLeft", "noseSneerRight", "mouthPucker"
]);

_extractExpressionCurves(clip, bundleMeshes) {
  const curves = new Map(); // name → { times, values }
  clip.tracks = clip.tracks.filter(track => {
    if (!track.name.includes(".morphTargetInfluences")) return true;
    const meshName = track.name.split(".")[0];
    const mesh = bundleMeshes.get(meshName);
    if (!mesh || !mesh.morphTargetDictionary) return false;
    // Extract name-keyed curves for allowed channels
    for (const [name, idx] of Object.entries(mesh.morphTargetDictionary)) {
      if (!EXPRESSION_ALLOW.has(name)) continue;
      // Extract the idx-th value from each keyframe
      const count = mesh.morphTargetDictionary ? Object.keys(mesh.morphTargetDictionary).length : 0;
      const times = track.times;
      const values = new Float32Array(times.length);
      for (let i = 0; i < times.length; i++) {
        values[i] = track.values[i * count + idx] || 0;
      }
      if (values.some(v => v > 0.001)) {
        curves.set(name, { times: Array.from(times), values: Array.from(values) });
      }
    }
    return false; // strip from clip
  });
  if (curves.size > 0) this._expressionCurves.set(clip.name, curves);
}
```

- [ ] **Step 2: Register morph layers with TalkingHead fork (D21)**

Register two morph layers via the fork API. TalkingHead's blink/mood/eyes are layer 0 (internal). Our layers compose on top:

```javascript
// Register viseme layer (replace mode — overwrites viseme_* channels)
head.registerMorphLayer('visemes', {
  order: 10,
  blend: 'replace',
  channels: ['viseme_aa', 'viseme_E', 'viseme_I', 'viseme_O', 'viseme_U',
             'viseme_PP', 'viseme_SS', 'viseme_TH', 'viseme_DD', 'viseme_FF',
             'viseme_kk', 'viseme_nn', 'viseme_RR', 'viseme_CH', 'viseme_sil'],
  update: (dt, influences) => {
    // Write current viseme weights (driven by speakAudio playback)
  }
});

// Register gesture expression layer (additive — adds to expression channels)
head.registerMorphLayer('gestureExpressions', {
  order: 20,
  blend: 'additive',
  channels: Array.from(EXPRESSION_ALLOW),
  update: (dt, influences) => {
    if (!bodyAnim.currentExpressionCurves) return;
    const weight = bodyAnim.gestureWeight;
    for (const [name, value] of bodyAnim.currentExpressionCurves) {
      if (influences[name] !== undefined) {
        influences[name] = Math.min(1, influences[name] + value * weight);
      }
    }
  }
});
```

This replaces the `scene.onBeforeRender` workaround from the original plan. TalkingHead's native layer pipeline handles ordering and blending.
```

- [ ] **Step 3: Test shrug frown visibility, commit**

Play the shrug gesture. Verify mouthFrown shows on the avatar's face. Verify during speech, visemes still work correctly (expression curves add, don't replace).

```bash
git add speech-demo/
git commit -m "feat(#457): expression curve extraction, morph layer registration (D6, D21)"
```

---

## Batch 5: Client Protocol Integration

After this batch: client speaks the new protocol, gestures fire audio-synced, floor states transition correctly, mood displays.

### Task 12: New Protocol Handler and speakAudio Integration

**Files:**
- Modify: `speech-demo/src/main/resources/META-INF/resources/index.html`
- Test: manual browser test

**Interfaces:**
- Consumes: `Segment`, `TurnComplete`, `Gesture`, `Mood` messages from server (Task 5), TalkingHead `speakAudio` with markers
- Produces: working chat display from `Segment.text`, audio playback via `speakAudio` with gesture markers

- [ ] **Step 1: Replace ws.onmessage handlers for new protocol**

```javascript
ws.onmessage = (evt) => {
  if (typeof evt.data === "string") {
    const msg = JSON.parse(evt.data);
    switch (msg.type) {
      case "segment": {
        // Display text in chat
        addMessage(msg.text, "avatar");
        // Store visemes and cues for audio pairing
        pendingSegments.set(segmentKey(msg.turnSeq, msg.index), {
          visemes: msg.visemes, cues: msg.cues, hasAudio: msg.hasAudio
        });
        if (!msg.hasAudio) {
          // No audio coming — process cues immediately if any
        }
        break;
      }
      case "turnComplete": {
        currentTurnComplete = msg;
        checkTurnFinished();
        break;
      }
      case "gesture": {
        // Non-speech gesture (attention, cognition)
        if (!document.hidden && bodyAnim) {
          bodyAnim.playGesture(msg.tag);
        }
        break;
      }
      case "mood": {
        // Persistent mood baseline
        if (head) head.setMood(msg.state);
        currentMoodBaseline = msg.state;
        break;
      }
      // Keep: transcript, timing, error, partial, speakerPrompt, speakerIdentified
    }
  } else {
    // Binary audio — parse 8-byte header, deliver via speakAudio
    handleAudioFrame(evt.data);
  }
};
```

- [ ] **Step 2: Parse audio frames and deliver via TalkingHead speakAudio**

Replace the custom `playQueue` with TalkingHead's `speakAudio` API. Each audio frame is paired with its segment's visemes and gesture cues, then delivered to TalkingHead:

```javascript
async function handleAudioFrame(data) {
  const header = new DataView(data.buffer || data, 0, 8);
  const segIndex = header.getUint16(2, true);
  const turnSeq = header.getUint32(4, true);
  const audioBytes = new Uint8Array(data.slice(8));

  const key = segmentKey(turnSeq, segIndex);
  const seg = pendingSegments.get(key);
  if (!seg) return;

  // Decode audio
  if (!audioCtx) audioCtx = new AudioContext();
  const audioBuf = await audioCtx.decodeAudioData(audioBytes.buffer);

  // Build speakAudio input with visemes and gesture markers
  const r = { audio: audioBuf };
  if (seg.visemes && seg.visemes.length > 0) {
    r.visemes = seg.visemes.map(v => v.viseme);
    r.vtimes = seg.visemes.map(v => v.startMs);
    r.vdurations = seg.visemes.map(v => v.endMs - v.startMs);
  }

  // Gesture cues become markers (timed callbacks)
  if (seg.cues && seg.cues.length > 0) {
    const rate = parseFloat(document.getElementById("speed").value);
    r.markers = [];
    r.mtimes = [];
    for (const cue of seg.cues) {
      if (cue.type === "gesture") {
        const strokeMs = bodyAnim.strokeMs(cue.tag) || 0;
        const triggerMs = Math.max(0, cue.offsetMs / rate - strokeMs);
        r.markers.push(() => bodyAnim.playGesture(cue.tag));
        r.mtimes.push(triggerMs);
      }
    }
  }

  // Deliver to TalkingHead — it handles queuing, viseme rendering, and marker firing
  head.speakAudio(r);
}
```

Delete the custom `playQueue`, `playNextQueued`, `isPlaying`, and the viseme rAF loop. TalkingHead's `speakAudio` owns audio playback, viseme rendering (through the registered morph layer), and gesture marker timing.

- [ ] **Step 3: Send PlaybackComplete and UserActivity**

```javascript
function sendPlaybackComplete(turnSeq) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: "playbackComplete", turnSeq }));
  }
}

function sendUserActivity(kind) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ type: "userActivity", kind }));
  }
}

// Wire: sendUserActivity("typing") on input keydown (throttled to 5s)
// Wire: sendPlaybackComplete in checkTurnFinished when all audio played
```

- [ ] **Step 4: Test with running server, commit**

```bash
git add speech-demo/
git commit -m "feat(#457): client protocol — Segment, audio headers, PlaybackComplete (D3)"
```

### Task 13: Gesture Timing, Speaking State, Mood Display

**Files:**
- Modify: `speech-demo/src/main/resources/META-INF/resources/index.html`
- Test: manual browser test

**Interfaces:**
- Consumes: `BodyAnimationSystem` floor states (Task 10), `Segment.cues` (Task 12)
- Produces: audio-synced gesture triggering, floor state transitions, turn-scoped mood

- [ ] **Step 1: Wire gesture timing from cues in playback queue**

```javascript
function playNextQueued() {
  if (playQueue.length === 0) {
    isPlaying = false;
    checkTurnFinished();
    return;
  }
  isPlaying = true;
  var item = playQueue.shift();

  // Floor state: speaking on first audio
  if (!isSpeaking) {
    isSpeaking = true;
    bodyAnim.setFloorState("speaking");
  }

  // Schedule gesture cues with timing
  if (item.cues) {
    for (const cue of item.cues) {
      if (cue.type === "gesture") {
        const rate = parseFloat(document.getElementById("speed").value);
        const strokeMs = bodyAnim.strokeMs(cue.tag) || 0;
        const delay = Math.max(0, cue.offsetMs / rate - strokeMs - 200);
        setTimeout(() => bodyAnim.playGesture(cue.tag), delay);
      }
      if (cue.type === "mood") {
        const rate = parseFloat(document.getElementById("speed").value);
        setTimeout(() => {
          if (head) head.setMood(cue.mood);
          turnMood = cue.mood;
        }, cue.offsetMs / rate);
      }
    }
  }

  // ... audio playback + visemes (existing logic adapted)
}
```

- [ ] **Step 2: Wire floor state transitions**

```javascript
// On sendText or stopRecording:
bodyAnim.setFloorState("thinking");

// On mic start:
bodyAnim.setFloorState("listening");

// On first audio play (in playNextQueued):
bodyAnim.setFloorState("speaking");

// On turn finished (checkTurnFinished):
bodyAnim.setFloorState("idle");

// Turn-scoped mood decay:
function checkTurnFinished() {
  if (currentTurnComplete && !isPlaying) {
    sendPlaybackComplete(currentTurnComplete.turnSeq);
    bodyAnim.setFloorState("idle");
    // Mood decay after hold
    if (turnMood) {
      setTimeout(() => {
        if (head) head.setMood(currentMoodBaseline || "neutral");
        turnMood = null;
      }, 3000);
    }
    currentTurnComplete = null;
  }
}
```

- [ ] **Step 3: Remove old gesture-from-Response logic, old mood detection**

Delete the `case 'response'` gesture parsing, `detectMood`, `MOOD_TAG_RE`, `KEYWORD_MOODS`, `classifyResponse` call, and `parseGestureTags` import. The server owns all of this now (D2, D15, D16).

- [ ] **Step 4: End-to-end test, commit**

Start the full pipeline. Send a message. Verify:
- Gesture fires at approximately the right word
- Floor states transition: idle → thinking → speaking → idle
- Mood tag sets face expression, decays after 3s
- Attention gestures fire after silence (if enabled in config)

```bash
git add speech-demo/
git commit -m "feat(#457): gesture timing, floor states, mood display, drop keyword fallback (D4, D5, D13, D15, D16)"
```

---

## Batch 6: Integration and Cleanup

After this batch: everything works end-to-end. Stale docs updated.

### Task 14: Attention Wiring, Docs Update, Final Verification

**Files:**
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechWebSocket.java` (wire attention)
- Modify: `docs/guides/consumer-guide.md` (fix stale SocialPromptAssembler reference)
- Modify: `speech-demo/src/main/resources/application.properties` (enable attention for demo)
- Test: end-to-end manual verification

**Interfaces:**
- Consumes: `SilenceAttentionPolicy` (Task 9), `FloorState` (Task 5)
- Produces: working attention bids in the demo, clean docs

- [ ] **Step 1: Wire SilenceAttentionPolicy into the proactive tick**

In `SpeechWebSocket.startProactiveTick`, after cognition evaluation, check `SilenceAttentionPolicy` when attention is enabled and cognition does not provide affect:

```java
if (avatarConfig.attentionEnabled() && !cognitionProvidesAffect) {
    silencePolicy.evaluate(floorState.lastActivity()).ifPresent(tag -> {
        session.sendGesture(tag, GestureOrigin.ATTENTION);
    });
}
```

- [ ] **Step 2: Enable attention in demo config**

```properties
# application.properties
casehub.avatar.attention.enabled=true
casehub.avatar.attention.first-bid-seconds=30
casehub.avatar.attention.second-bid-seconds=75
```

- [ ] **Step 3: Fix consumer-guide.md stale reference**

Update `docs/guides/consumer-guide.md` L668 — replace stale `SocialPromptAssembler` reference with `DefaultPromptAssembler` + `PromptSection` composition pattern.

- [ ] **Step 4: Full end-to-end verification**

Run the demo and verify ALL of:
- [ ] Gestures fire at approximately the right word during speech
- [ ] TTS does NOT vocalize `[gesture:X]` or `[mood:X]` markers
- [ ] Floor states: idle → listening (mic) → thinking (send) → speaking (audio) → idle
- [ ] Speaking pool animations cycle during speech, idle pool during silence
- [ ] Shrug frown expression shows on face, composes with visemes
- [ ] Mood tags set face expression, decay after playback + 3s
- [ ] Attention bids fire at 30s and 75s of silence, reset on activity
- [ ] PlaybackComplete sent after each turn
- [ ] Multiple sentences play sequentially with correct segment pairing
- [ ] Animation picker still works
- [ ] Avatar model switching still works

- [ ] **Step 5: Run full test suite, commit**

```bash
mvn --batch-mode test
git add .
git commit -m "feat(#457): attention wiring, docs fix, final integration (D8, D14)"
```

---

## References

- [2026-11-18-m2m-animation-integration-design.md] — design spec
- [decisions.md D1–D24] — reviewed decisions with full rationale (D18–D24: TalkingHead fork)
- [SpeechSession.java:239-245] — handleStop streaming path
- [SpeechSession.java:175-176] — handleProactiveSpeech
- [AvatarMessage.java] — current protocol (replaced by D3)
- [body-animations.mjs (#458 branch)] — existing animation system
- [index.html (#458 branch)] — TalkingHead 1.8 integration
- [CognitionAvatarAdapter.java:51-75] — wrapAssembler
- [CognitionAvatarAdapter.java:94-103] — evaluateProactive
- [consumer-guide.md:668] — stale SocialPromptAssembler reference
- [casehub-connectors-voice-spi] — SynthesisOptions, SynthesisResult
- [GitHub #457] — focal issue
- [GitHub #458] — animation library (prerequisite)

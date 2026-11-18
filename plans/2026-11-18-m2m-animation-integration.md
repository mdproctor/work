# M2M Animation Integration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #457 — Avatar demo: integrate M2M animation library with idle cycle and LLM-tagged gestures
**Issue group:** #457 (depends on landed #458)

**Goal:** Wire 68 M2M body animation clips into the avatar speech pipeline with audio-synced gesture triggering, turn-framed protocol, session concurrency safety, morph target compositing, and mood ownership.

**Architecture:** Server-side `UtteranceSegmenter` parses gesture/mood tags per-sentence, feeds clean text to TTS and tagged cues to the client via `Segment` messages. Client plays gestures from an audio-synced playback queue using mark timing from the TTS SPI. `BodyAnimationSystem` runs two layers (base pool + gesture one-shot) with expression curve compositing via a single `scene.onBeforeRender` hook. Session concurrency is solved by capture/turn thread separation.

**Tech Stack:** Java 22 (speech-ws, speech-api), JavaScript ES modules (body-animations.mjs, index.html), three.js 0.180.0, TalkingHead 1.8, Jackson (JSON vocabulary), JUnit 5 + Mockito (tests)

## Global Constraints

- speech-api is framework-free ("no framework annotations — pure Java", #198 design)
- speech-ws uses Quarkus websockets-next annotations
- speech-demo targets browser ES modules via import maps (no bundler)
- All protocol changes are breaking — `Phonemes` and `Response` are removed
- `PerformanceVocabulary` SPI lives in speech-api; JSON implementation in speech-demo
- Connectors mark timing SPI must land before Batch 3 (D4 integration)
- IntelliJ MCP required for all structural code operations

## Prerequisites (not implementation tasks)

Before starting Batch 1:
1. **Land #458** via `work-end`. Recreate #457 branch from updated main.
2. **File connectors issue:** `TextMark`/`MarkTiming` in `casehub-connectors-voice-spi`, `MarkTimingEnricher`, VITS guarded-exact. Blocks Batch 3.
3. **File neocortex issue:** Caller-supplied proactive prompt sections, non-verbal proactive outcome, delivery feedback. Non-blocking — follows #457.

---

## Batch 0: TalkingHead Verification Spike

### Task 1: Verify TalkingHead 1.8 speakAudio API

**Files:**
- Create: `speech-demo/src/main/resources/META-INF/resources/spike-speakaudio.html`
- Test: manual browser test

**Interfaces:**
- Consumes: TalkingHead 1.8 from CDN (already in import map)
- Produces: spike result recorded in decisions.md D14 — determines whether Batches 4–5 use TalkingHead's audio host or the custom queue

This spike determines client audio architecture. All three checks must pass to adopt `speakAudio`:

- [ ] **Step 1: Create spike test page**

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>speakAudio Spike</title>
<script type="importmap">
{ "imports": {
    "three": "https://cdn.jsdelivr.net/npm/three@0.180.0/build/three.module.js/+esm",
    "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.180.0/examples/jsm/",
    "talkinghead": "https://cdn.jsdelivr.net/gh/met4citizen/TalkingHead@1.8/modules/talkinghead.mjs"
} }
</script>
</head>
<body>
<div id="container" style="width:600px;height:400px"></div>
<button id="test">Run Spike</button>
<pre id="log"></pre>
<script type="module">
import { TalkingHead } from "talkinghead";

const log = document.getElementById("log");
function print(msg) { log.textContent += msg + "\n"; }

const head = new TalkingHead(document.getElementById("container"), {
  ttsEndpoint: null, lipsyncModules: ["en"],
  cameraView: "upper", cameraRotateEnable: true
});

await head.showAvatar({ url: "./avatar.glb", body: "F", avatarMood: "neutral", lipsyncLang: "en" });
print("Avatar loaded.");

document.getElementById("test").onclick = async () => {
  // Check (a): does speakAudio exist and accept our format?
  print("Check (a): speakAudio API existence...");
  if (typeof head.speakAudio !== "function") {
    print("FAIL: speakAudio not found on TalkingHead instance");
    return;
  }
  print("  speakAudio exists. Checking parameter signature...");

  // Create a simple test audio (1s silence at 16kHz)
  const ctx = new AudioContext();
  const buf = ctx.createBuffer(1, 16000, 16000);
  const wavBytes = encodeWav(buf);

  // Try calling with visemes and markers
  try {
    // Check if it accepts markers/words alongside visemes
    const visemes = [{ viseme: "sil", startMs: 0, endMs: 500, weight: 1.0 }];
    const markers = [{ name: "gesture:nod", time: 0.3 }];

    // Try the call — we just want to know if it throws on the shape
    await head.speakAudio(wavBytes, { visemes, markers });
    print("  PASS (a): speakAudio accepted visemes + markers");
  } catch (e) {
    print("  Result (a): " + e.message);
    // Try without markers
    try {
      await head.speakAudio(wavBytes, {});
      print("  speakAudio works without markers — markers not supported");
    } catch (e2) {
      print("  FAIL (a): speakAudio rejects even basic call: " + e2.message);
    }
  }

  // Check (b): do markers fire callbacks?
  print("Check (b): marker callback timing...");
  // This depends on what (a) revealed about the API shape
  // Record marker event times if the API supports them
  print("  (manual observation required — see console for events)");

  // Check (c): can we prevent body pose writes?
  print("Check (c): body bone ownership...");
  const armature = head.armature;
  if (!armature) {
    print("  FAIL (c): no armature exposed");
  } else {
    const spine = armature.getObjectByName("Spine");
    if (spine) {
      const before = spine.quaternion.clone();
      // Let TalkingHead run a frame
      await new Promise(r => requestAnimationFrame(r));
      const after = spine.quaternion.clone();
      const changed = !before.equals(after);
      print("  Spine changed after frame: " + changed);
      if (changed) {
        print("  TalkingHead writes body bones — need to find disable mechanism");
      } else {
        print("  PASS (c): TalkingHead does not touch Spine");
      }
    } else {
      print("  Spine bone not found — check bone names");
    }
  }

  print("\nSpike complete. Record results in D14.");
};

function encodeWav(audioBuf) {
  const samples = audioBuf.getChannelData(0);
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  const sr = audioBuf.sampleRate;
  // WAV header
  writeString(view, 0, "RIFF");
  view.setUint32(4, 36 + samples.length * 2, true);
  writeString(view, 8, "WAVE");
  writeString(view, 12, "fmt ");
  view.setUint32(16, 16, true); view.setUint16(20, 1, true);
  view.setUint16(22, 1, true); view.setUint32(24, sr, true);
  view.setUint32(28, sr * 2, true); view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeString(view, 36, "data");
  view.setUint32(40, samples.length * 2, true);
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(44 + i * 2, s < 0 ? s * 0x8000 : s * 0x7FFF, true);
  }
  return new Uint8Array(buffer);
}

function writeString(view, offset, str) {
  for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i));
}
</script>
</body>
</html>
```

- [ ] **Step 2: Run the spike in a browser**

Start the speech-demo dev server:
```bash
mvn --batch-mode quarkus:dev -pl speech-demo
```

Open `http://localhost:8080/spike-speakaudio.html`. Click "Run Spike". Record the three check results.

- [ ] **Step 3: Record spike outcome**

Update decisions.md D14 with the spike result:
- If all three pass → "Adopt speakAudio as playback host" — Batches 4–5 use TalkingHead audio
- If any fails → "Keep custom audio queue" — Batches 4–5 use the custom `playQueue` with D3 header-based pairing

- [ ] **Step 4: Commit**

```bash
git add speech-demo/src/main/resources/META-INF/resources/spike-speakaudio.html
git commit -m "spike(#457): verify TalkingHead 1.8 speakAudio API (D14)"
```

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

After this batch: `body-animations.mjs` loads from vocabulary, runs layered animation with expression curves, and has a floor state machine. No protocol wiring yet.

### Task 10: body-animations.mjs Vocabulary-Driven Rewrite + Layered Animation

**Files:**
- Modify: `speech-demo/src/main/resources/META-INF/resources/animations/body-animations.mjs`
- Test: manual browser test via animation picker

**Interfaces:**
- Consumes: `avatar-performance.json` (Task 2)
- Produces: `BodyAnimationSystem` with vocabulary-driven pools, base+gesture layers (D11), floor states (D13). Consumed by Task 12 (protocol integration).

- [ ] **Step 1: Rewrite BodyAnimationSystem to load from vocabulary**

Delete all data constants (`TAXONOMY`, `GESTURE_MAP`, `KEYWORD_GESTURES`, `SPEAKING_POOL_REFS`, `IDLE_POOL_REFS`). Replace with a `fetch("animations/avatar-performance.json")` in `load()`. Build all runtime structures from the fetched data.

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

### Task 11: Expression Curves and Face Compositor

**Files:**
- Modify: `speech-demo/src/main/resources/META-INF/resources/animations/body-animations.mjs`
- Modify: `speech-demo/src/main/resources/META-INF/resources/index.html`
- Test: manual browser test

**Interfaces:**
- Consumes: `BodyAnimationSystem` (Task 10), TalkingHead scene
- Produces: face compositor via `scene.onBeforeRender` hook (D14). Expression curves from body clips compose with visemes.

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

- [ ] **Step 2: Implement face compositor in scene.onBeforeRender (D14)**

Replace the separate `tickBody` rAF loop in `index.html`. Use a single `scene.onBeforeRender` hook:

```javascript
// In index.html, after avatar loads:
const scene = head.scene || head._scene;
const bodyClock = new THREE.Clock();
let lastFrame = -1;

scene.onBeforeRender = (renderer) => {
  const frame = renderer.info.render.frame;
  if (frame === lastFrame) return;
  lastFrame = frame;

  // 1. body mixer update
  bodyAnim.update(bodyClock.getDelta());
  bodyAnim.model.updateMatrixWorld(true);

  // 2. face compositor — TalkingHead already wrote base (blink, mood, eyes)
  // 3. visemes already written by our playback loop (layer 2)
  // 4. gesture expression curves (layer 3) — additive, clamped
  if (bodyAnim.currentExpressionCurves) {
    const weight = bodyAnim.gestureWeight;
    for (const [name, value] of bodyAnim.currentExpressionCurves) {
      const idx = headMesh.morphTargetDictionary[name];
      if (idx !== undefined) {
        headMesh.morphTargetInfluences[idx] = Math.min(1,
          headMesh.morphTargetInfluences[idx] + value * weight);
      }
    }
  }
};
```

- [ ] **Step 3: Test shrug frown visibility, commit**

Play the shrug gesture. Verify mouthFrown shows on the avatar's face. Verify during speech, visemes still work correctly (expression curves add, don't replace).

```bash
git add speech-demo/
git commit -m "feat(#457): expression curve extraction, face compositor via onBeforeRender (D6, D14)"
```

---

## Batch 5: Client Protocol Integration

After this batch: client speaks the new protocol, gestures fire audio-synced, floor states transition correctly, mood displays.

### Task 12: New Protocol Handler and Audio Header Parsing

**Files:**
- Modify: `speech-demo/src/main/resources/META-INF/resources/index.html`
- Test: manual browser test

**Interfaces:**
- Consumes: `Segment`, `TurnComplete`, `Gesture`, `Mood` messages from server (Task 5)
- Produces: working chat display from `Segment.text`, audio playback with header-based pairing

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
    // Binary audio — parse 8-byte header
    handleAudioFrame(evt.data);
  }
};
```

- [ ] **Step 2: Parse audio frame headers**

```javascript
function handleAudioFrame(data) {
  const header = new DataView(data.buffer || data, 0, 8);
  const version = header.getUint8(0);
  const segIndex = header.getUint16(2, true);
  const turnSeq = header.getUint32(4, true);

  const audioBytes = data.slice(8); // actual WAV data
  const key = segmentKey(turnSeq, segIndex);
  const seg = pendingSegments.get(key);

  // Pair with segment, queue for playback
  playQueue.push({
    audio: null, // decode async
    visemes: seg ? seg.visemes : null,
    cues: seg ? seg.cues : null,
    turnSeq: turnSeq,
    segIndex: segIndex,
    rawAudio: audioBytes
  });
  if (!isPlaying) playNextQueued();
}
```

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
- Delete: `speech-demo/src/main/resources/META-INF/resources/spike-speakaudio.html`
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

- [ ] **Step 4: Clean up spike file**

```bash
git rm speech-demo/src/main/resources/META-INF/resources/spike-speakaudio.html
```

- [ ] **Step 5: Full end-to-end verification**

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

- [ ] **Step 6: Run full test suite, commit**

```bash
mvn --batch-mode test
git add .
git commit -m "feat(#457): attention wiring, docs fix, final integration (D8, D14)"
```

---

## References

- [2026-11-18-m2m-animation-integration-design.md] — design spec
- [decisions.md D1–D17] — reviewed decisions with full rationale
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

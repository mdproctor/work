# Server Session Refactor (Batch 2) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #457 — Avatar demo: integrate M2M animation library with idle cycle and LLM-tagged gestures
**Issue group:** #457, #491, #492

**Goal:** Split SpeechSession into capture/turn sides with TurnExecutor
serialisation, add PromptSection composition with GesturePromptSection,
and emit turn-framed protocol messages (Segment, TurnComplete).

**Architecture:** SpeechSession becomes a thin capture-side coordinator
(STT stream, ring buffer, speaker ID, partial polling). A single-thread
TurnExecutor owns all turn processing (history, prompt assembly, LLM,
TTS, outbound messages) and emits turn-framed Segment/TurnComplete
protocol alongside legacy Response/Phonemes for client backward
compatibility. FloorState provides lock-free coordination between both
sides for proactive suppression. DefaultPromptAssembler gains composable
PromptSection support, with GesturePromptSection rendering gesture/mood
tags from the performance vocabulary.

**Tech Stack:** Java 17, JUnit 5, Mockito, AssertJ, virtual threads
(single-thread executor)

## Global Constraints

- speech-ws has no Quarkus runtime in tests — plain JUnit 5 with Mockito
- PerformanceVocabulary is an SPI in speech-api — speech-ws takes it as a
  dependency, speech-demo supplies the JSON impl
- WebSocket send is assumed thread-safe (websockets-next contract, D10)
- No Jandex index — CDI beans must be opted in by consumers
- Existing SpeechSession tests must pass with minimal changes — new
  protocol messages (Segment, TurnComplete) are emitted ALONGSIDE legacy
  messages (Response, Phonemes) during the transition period

---

## Batch 1: PromptSection Composition

### Task 1: DefaultPromptAssembler PromptSection composition + GesturePromptSection

**Files:**
- Modify: `speech-api/src/main/java/io/casehub/agency/speech/SpeechPromptAssembler.java`
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/DefaultPromptAssembler.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/GesturePromptSection.java`
- Modify: `speech-ws/src/test/java/io/casehub/agency/speech/ws/DefaultPromptAssemblerTest.java`
- Create: `speech-ws/src/test/java/io/casehub/agency/speech/ws/GesturePromptSectionTest.java`

**Interfaces:**
- Consumes: `PromptSection` (speech-api), `PromptContext` (speech-api),
  `PerformanceVocabulary` (speech-api), `GestureEntry`, `MoodEntry`
- Produces: `SpeechPromptAssembler.assemble(String, List<ConversationTurn>, PromptContext)`
  default method; `DefaultPromptAssembler(String, List<PromptSection>)` constructor;
  `GesturePromptSection implements PromptSection`

- [ ] **Step 1: Write failing test — DefaultPromptAssembler with sections**

```java
// In DefaultPromptAssemblerTest.java — add new test
@Test
void assembleWithSectionsAppendsToSystemPrompt() {
    PromptSection section = ctx -> "## Gestures\n[gesture:nod] — agreement";
    var assembler = new DefaultPromptAssembler("You are helpful.", List.of(section));
    var context = new PromptContext("agent-1", "tenant-1", "user-1");

    var result = assembler.assemble("hello", List.of(), context);

    assertThat(result.systemPrompt()).contains("You are helpful.");
    assertThat(result.systemPrompt()).contains("## Gestures");
    assertThat(result.systemPrompt()).contains("[gesture:nod]");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl speech-ws -Dtest=DefaultPromptAssemblerTest#assembleWithSectionsAppendsToSystemPrompt`
Expected: FAIL — constructor `DefaultPromptAssembler(String, List)` does not exist

- [ ] **Step 3: Add default method to SpeechPromptAssembler**

Add to `SpeechPromptAssembler.java`:

```java
default AssembledPrompt assemble(String userMessage, List<ConversationTurn> history,
                                 @org.jspecify.annotations.Nullable PromptContext context) {
    return assemble(userMessage, history);
}
```

The interface stays `@FunctionalInterface` — one abstract method, one default.

- [ ] **Step 4: Implement section composition in DefaultPromptAssembler**

Replace constructor and assemble method:

```java
private final List<PromptSection> sections;

public DefaultPromptAssembler(@Nullable String systemPrompt) {
    this(systemPrompt, List.of());
}

public DefaultPromptAssembler(@Nullable String systemPrompt, List<PromptSection> sections) {
    this.systemPrompt = systemPrompt != null ? systemPrompt : FALLBACK_SYSTEM_PROMPT;
    this.sections = List.copyOf(sections);
}

@Override
public AssembledPrompt assemble(String userMessage, List<ConversationTurn> history) {
    return assemble(userMessage, history, null);
}

@Override
public AssembledPrompt assemble(String userMessage, List<ConversationTurn> history,
                                @Nullable PromptContext context) {
    var sb = new StringBuilder();
    var speakers = new java.util.LinkedHashSet<String>();
    for (ConversationTurn turn : history) {
        String label;
        if (turn.speaker() != null) {
            label = turn.speaker() + " (User)";
            speakers.add(turn.speaker());
        } else {
            label = turn.role().equals("user") ? "User" : "Assistant";
        }
        sb.append(label).append(": ").append(turn.text()).append("\n");
    }
    sb.append("User: ").append(userMessage);

    String effectiveSystemPrompt = systemPrompt;
    if (!speakers.isEmpty()) {
        effectiveSystemPrompt = systemPrompt + "\nSpeaking with: "
                + String.join(", ", speakers) + ".";
    }

    if (context != null && !sections.isEmpty()) {
        var sectionBuilder = new StringBuilder(effectiveSystemPrompt);
        for (var section : sections) {
            String text = section.contribute(context);
            if (text != null) {
                sectionBuilder.append("\n\n").append(text);
            }
        }
        effectiveSystemPrompt = sectionBuilder.toString();
    }

    return new AssembledPrompt(effectiveSystemPrompt, sb.toString());
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn --batch-mode test -pl speech-ws -Dtest=DefaultPromptAssemblerTest`
Expected: ALL PASS — including existing tests (backward compatible)

- [ ] **Step 6: Write additional section composition tests**

```java
@Test
void assembleWithoutContextSkipsSections() {
    PromptSection section = ctx -> "should not appear";
    var assembler = new DefaultPromptAssembler("Base.", List.of(section));

    var result = assembler.assemble("hello", List.of());

    assertThat(result.systemPrompt()).isEqualTo("Base.");
}

@Test
void assembleSkipsNullSectionContributions() {
    PromptSection returns_null = ctx -> null;
    PromptSection returns_text = ctx -> "visible section";
    var assembler = new DefaultPromptAssembler("Base.", List.of(returns_null, returns_text));
    var context = new PromptContext("a", "t", null);

    var result = assembler.assemble("hello", List.of(), context);

    assertThat(result.systemPrompt()).contains("visible section");
    assertThat(result.systemPrompt()).doesNotContain("null");
}

@Test
void assembleWithEmptySectionsListUnchanged() {
    var assembler = new DefaultPromptAssembler("Base.", List.of());
    var context = new PromptContext("a", "t", null);

    var result = assembler.assemble("hello", List.of(), context);

    assertThat(result.systemPrompt()).isEqualTo("Base.");
}
```

- [ ] **Step 7: Run all section tests**

Run: `mvn --batch-mode test -pl speech-ws -Dtest=DefaultPromptAssemblerTest`
Expected: ALL PASS

- [ ] **Step 8: Write failing test — GesturePromptSection**

```java
// GesturePromptSectionTest.java
class GesturePromptSectionTest {

    @Test
    void contributesPromptableGesturesAndMoods() {
        var vocab = stubVocabulary(
            List.of(
                new GestureEntry("nod", "agreement", true),
                new GestureEntry("shrug", "uncertainty", true),
                new GestureEntry("internal-only", "not for LLM", false)),
            List.of(
                new MoodEntry("happy", "warm positive tone", true),
                new MoodEntry("sleep", "drowsy", false)));

        var section = new GesturePromptSection(vocab);
        var text = section.contribute(new PromptContext("a", "t", null));

        assertThat(text).contains("[gesture:nod]").contains("agreement");
        assertThat(text).contains("[gesture:shrug]").contains("uncertainty");
        assertThat(text).doesNotContain("internal-only");
        assertThat(text).contains("[mood:happy]").contains("warm positive tone");
        assertThat(text).doesNotContain("sleep");
        assertThat(text).contains("at most one gesture per sentence");
    }

    @Test
    void returnsNullWhenNoPromptableEntries() {
        var vocab = stubVocabulary(
            List.of(new GestureEntry("hidden", "x", false)),
            List.of(new MoodEntry("hidden", "x", false)));

        var section = new GesturePromptSection(vocab);

        assertThat(section.contribute(new PromptContext("a", "t", null))).isNull();
    }

    @Test
    void worksWithGesturesOnlyNoMoods() {
        var vocab = stubVocabulary(
            List.of(new GestureEntry("wave", "greeting", true)),
            List.of());

        var section = new GesturePromptSection(vocab);
        var text = section.contribute(new PromptContext("a", "t", null));

        assertThat(text).contains("[gesture:wave]");
        assertThat(text).doesNotContain("[mood:");
    }

    private static PerformanceVocabulary stubVocabulary(List<GestureEntry> gestures,
                                                        List<MoodEntry> moods) {
        return new PerformanceVocabulary() {
            public java.util.Set<String> gestureTags() {
                return gestures.stream().map(GestureEntry::tag)
                        .collect(java.util.stream.Collectors.toSet());
            }
            public boolean isValidGesture(String tag) {
                return gestures.stream().anyMatch(g -> g.tag().equals(tag));
            }
            public List<GestureEntry> gesturePromptEntries() { return gestures; }
            public java.util.Set<String> allClipNames() { return java.util.Set.of(); }
            public List<MoodEntry> moods() { return moods; }
            public boolean isValidMood(String mood) {
                return moods.stream().anyMatch(m -> m.name().equals(mood));
            }
        };
    }
}
```

- [ ] **Step 9: Implement GesturePromptSection**

```java
package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.GestureEntry;
import io.casehub.agency.speech.MoodEntry;
import io.casehub.agency.speech.PerformanceVocabulary;
import io.casehub.agency.speech.PromptContext;
import io.casehub.agency.speech.PromptSection;
import org.jspecify.annotations.Nullable;

public class GesturePromptSection implements PromptSection {

    private final PerformanceVocabulary vocabulary;

    public GesturePromptSection(PerformanceVocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    @Override
    public @Nullable String contribute(PromptContext context) {
        var gestures = vocabulary.gesturePromptEntries().stream()
                .filter(GestureEntry::promptable).toList();
        var moods = vocabulary.moods().stream()
                .filter(MoodEntry::promptable).toList();

        if (gestures.isEmpty() && moods.isEmpty()) return null;

        var sb = new StringBuilder("You may annotate your response with tags.\n");

        if (!gestures.isEmpty()) {
            sb.append("\nGestures (place before the associated word):\n");
            for (var g : gestures) {
                sb.append("[gesture:").append(g.tag()).append("] — ")
                  .append(g.description()).append("\n");
            }
        }

        if (!moods.isEmpty()) {
            sb.append("\nMoods (place at sentence start):\n");
            for (var m : moods) {
                sb.append("[mood:").append(m.name()).append("] — ")
                  .append(m.description()).append("\n");
            }
        }

        sb.append("\nRules: at most one gesture per sentence. Only use when it fits naturally.");
        return sb.toString();
    }
}
```

- [ ] **Step 10: Run all tests**

Run: `mvn --batch-mode test -pl speech-api,speech-ws`
Expected: ALL PASS

- [ ] **Step 11: Commit**

```bash
git add speech-api/src/main/java/io/casehub/agency/speech/SpeechPromptAssembler.java \
       speech-ws/src/main/java/io/casehub/agency/speech/ws/DefaultPromptAssembler.java \
       speech-ws/src/main/java/io/casehub/agency/speech/ws/GesturePromptSection.java \
       speech-ws/src/test/java/io/casehub/agency/speech/ws/DefaultPromptAssemblerTest.java \
       speech-ws/src/test/java/io/casehub/agency/speech/ws/GesturePromptSectionTest.java
git commit -m "feat(#457): PromptSection composition in DefaultPromptAssembler + GesturePromptSection (D7)"
```

---

## Batch 2: Capture/Turn Split

### Task 2: UserTurn, FloorState, TurnExecutor

**Files:**
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/UserTurn.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/FloorState.java`
- Create: `speech-ws/src/main/java/io/casehub/agency/speech/ws/TurnExecutor.java`
- Create: `speech-ws/src/test/java/io/casehub/agency/speech/ws/FloorStateTest.java`
- Create: `speech-ws/src/test/java/io/casehub/agency/speech/ws/TurnExecutorTest.java`

**Interfaces:**
- Consumes: `SpeechPromptAssembler`, `SpeechSession.StreamingResponseGenerator`,
  `TextToSpeech`, `UtteranceSegmenter`, `ParsedSegment`, `VisemeMapping`,
  `AvatarMessage.Segment`, `AvatarMessage.TurnComplete`, `AvatarMessage.Phonemes`,
  `AvatarMessage.Response`, `AvatarMessage.Timing`, `AvatarMessage.Error`,
  `MessageCodec`, `ConversationTurn`, `PromptContext`, `TurnOrigin`
- Produces: `UserTurn(String cleanText, @Nullable String speakerLabel, @Nullable String llmModel, @Nullable String ttsModel)`;
  `FloorState` with volatile state tracking and proactive suppression queries;
  `TurnExecutor` with `executeUserTurn(UserTurn)` (blocking) and
  `enqueueProactiveTurn(String content, long evaluatedAt)` (non-blocking)

- [ ] **Step 1: Create UserTurn record**

```java
package io.casehub.agency.speech.ws;

import org.jspecify.annotations.Nullable;

public record UserTurn(
        String cleanText,
        @Nullable String speakerLabel,
        @Nullable String llmModel,
        @Nullable String ttsModel
) {}
```

- [ ] **Step 2: Write failing FloorState tests**

```java
package io.casehub.agency.speech.ws;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FloorStateTest {

    @Test
    void initialStateIsNotListeningAndNoUnacknowledged() {
        var state = new FloorState();
        assertThat(state.isListening()).isFalse();
        assertThat(state.hasUnacknowledgedTurn()).isFalse();
        assertThat(state.shouldSuppressProactive()).isFalse();
    }

    @Test
    void onStartSetsListening() {
        var state = new FloorState();
        state.onStart();
        assertThat(state.isListening()).isTrue();
        assertThat(state.shouldSuppressProactive()).isTrue();
    }

    @Test
    void onStopClearsListening() {
        var state = new FloorState();
        state.onStart();
        state.onStopOrText();
        assertThat(state.isListening()).isFalse();
    }

    @Test
    void turnCompleteSetsUnacknowledged() {
        var state = new FloorState();
        long future = System.currentTimeMillis() + 60_000;
        state.onTurnComplete(1, future);

        assertThat(state.hasUnacknowledgedTurn()).isTrue();
        assertThat(state.shouldSuppressProactive()).isTrue();
    }

    @Test
    void playbackCompleteClearsMatchingTurn() {
        var state = new FloorState();
        state.onTurnComplete(1, System.currentTimeMillis() + 60_000);
        state.onPlaybackComplete(1);

        assertThat(state.hasUnacknowledgedTurn()).isFalse();
        assertThat(state.shouldSuppressProactive()).isFalse();
    }

    @Test
    void playbackCompleteIgnoresMismatchedTurnSeq() {
        var state = new FloorState();
        state.onTurnComplete(1, System.currentTimeMillis() + 60_000);
        state.onPlaybackComplete(99);

        assertThat(state.hasUnacknowledgedTurn()).isTrue();
    }

    @Test
    void expiredUnacknowledgedTurnDetected() {
        var state = new FloorState();
        long past = System.currentTimeMillis() - 1000;
        state.onTurnComplete(1, past);

        assertThat(state.isUnacknowledgedExpired(System.currentTimeMillis())).isTrue();
    }

    @Test
    void expireUnacknowledgedClearsState() {
        var state = new FloorState();
        state.onTurnComplete(1, System.currentTimeMillis() - 1000);
        state.expireUnacknowledged();

        assertThat(state.hasUnacknowledgedTurn()).isFalse();
        assertThat(state.shouldSuppressProactive()).isFalse();
    }

    @Test
    void onUserActivityUpdatesTimestamp() {
        var state = new FloorState();
        long before = state.lastUserActivity();
        state.onUserActivity();
        assertThat(state.lastUserActivity()).isGreaterThanOrEqualTo(before);
    }

    @Test
    void turnInFlightSuppressesProactive() {
        var state = new FloorState();
        state.onTurnStart();
        assertThat(state.shouldSuppressProactive()).isTrue();
        state.onTurnEnd();
        assertThat(state.shouldSuppressProactive()).isFalse();
    }
}
```

- [ ] **Step 3: Implement FloorState**

```java
package io.casehub.agency.speech.ws;

public class FloorState {

    private volatile boolean listening;
    private volatile boolean turnInFlight;
    private volatile long lastUserActivityMs;
    private volatile int unacknowledgedTurnSeq = -1;
    private volatile long unacknowledgedExpiresAt;

    public void onStart() {
        listening = true;
        lastUserActivityMs = System.currentTimeMillis();
    }

    public void onStopOrText() {
        listening = false;
        lastUserActivityMs = System.currentTimeMillis();
    }

    public void onUserActivity() {
        lastUserActivityMs = System.currentTimeMillis();
    }

    public void onTurnStart() {
        turnInFlight = true;
    }

    public void onTurnEnd() {
        turnInFlight = false;
    }

    public void onTurnComplete(int turnSeq, long expiresAt) {
        unacknowledgedTurnSeq = turnSeq;
        unacknowledgedExpiresAt = expiresAt;
    }

    public void onPlaybackComplete(int turnSeq) {
        if (turnSeq == unacknowledgedTurnSeq) {
            unacknowledgedTurnSeq = -1;
        }
    }

    public boolean isListening() {
        return listening;
    }

    public boolean hasUnacknowledgedTurn() {
        return unacknowledgedTurnSeq >= 0;
    }

    public boolean isUnacknowledgedExpired(long now) {
        return hasUnacknowledgedTurn() && now >= unacknowledgedExpiresAt;
    }

    public void expireUnacknowledged() {
        unacknowledgedTurnSeq = -1;
        lastUserActivityMs = System.currentTimeMillis();
    }

    public boolean shouldSuppressProactive() {
        return listening || turnInFlight || hasUnacknowledgedTurn();
    }

    public long lastUserActivity() {
        return lastUserActivityMs;
    }
}
```

- [ ] **Step 4: Run FloorState tests**

Run: `mvn --batch-mode test -pl speech-ws -Dtest=FloorStateTest`
Expected: ALL PASS

- [ ] **Step 5: Write failing TurnExecutor tests**

```java
package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.AssembledPrompt;
import io.casehub.agency.speech.ConversationTurn;
import io.casehub.agency.speech.PerformanceVocabulary;
import io.casehub.agency.speech.GestureEntry;
import io.casehub.agency.speech.MoodEntry;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.agency.speech.ws.protocol.AvatarMessage;
import io.casehub.agency.speech.ws.protocol.MessageCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TurnExecutorTest {

    private TextToSpeech ttsService;
    private List<String> sentText;
    private List<byte[]> sentBinary;
    private FloorState floorState;
    private TurnExecutor executor;

    private static final PerformanceVocabulary EMPTY_VOCAB = new PerformanceVocabulary() {
        public Set<String> gestureTags() { return Set.of(); }
        public boolean isValidGesture(String tag) { return false; }
        public List<GestureEntry> gesturePromptEntries() { return List.of(); }
        public Set<String> allClipNames() { return Set.of(); }
        public List<MoodEntry> moods() { return List.of(); }
        public boolean isValidMood(String mood) { return false; }
    };

    @BeforeEach
    void setUp() {
        ttsService = mock(TextToSpeech.class);
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1, 2, 3}, "wav", List.of()));
        sentText = new ArrayList<>();
        sentBinary = new ArrayList<>();
        floorState = new FloorState();

        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                prompt -> "I hear you.",
                null,
                ttsService,
                Map.of(),
                sentText::add,
                sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB),
                floorState,
                null,
                null);
    }

    @Test
    void executeUserTurnSendsSegmentAndTurnComplete() {
        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"segment\""));
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"turnComplete\""));
        assertThat(sentBinary).isNotEmpty();
    }

    @Test
    void turnSeqIncrementsPerTurn() {
        executor.executeUserTurn(new UserTurn("first", null, null, null));
        executor.executeUserTurn(new UserTurn("second", null, null, null));

        var turnCompletes = sentText.stream()
                .filter(json -> json.contains("\"type\":\"turnComplete\""))
                .toList();
        assertThat(turnCompletes).hasSize(2);
        assertThat(turnCompletes.get(0)).contains("\"turnSeq\":1");
        assertThat(turnCompletes.get(1)).contains("\"turnSeq\":2");
    }

    @Test
    void executeUserTurnAddsToHistory() {
        executor.executeUserTurn(new UserTurn("hello", null, null, null));
        assertThat(executor.historySize()).isEqualTo(2); // user + assistant
    }

    @Test
    void executeUserTurnSendsLegacyResponseAndPhonemes() {
        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"response\""));
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"phonemes\""));
    }

    @Test
    void streamingGeneratorProcessesPerSentence() {
        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            onSentence.accept("First sentence.");
            onSentence.accept("Second sentence.");
            return "First sentence. Second sentence.";
        };

        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                null,
                streamGen,
                ttsService,
                Map.of(),
                sentText::add,
                sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB),
                floorState,
                null,
                null);

        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        var segments = sentText.stream()
                .filter(json -> json.contains("\"type\":\"segment\""))
                .toList();
        assertThat(segments).hasSize(2);
        assertThat(sentBinary).hasSize(2);
    }

    @Test
    void turnCompleteAlwaysSentEvenOnLlmError() {
        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                prompt -> { throw new RuntimeException("LLM down"); },
                null,
                ttsService,
                Map.of(),
                sentText::add,
                sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB),
                floorState,
                null,
                null);

        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"turnComplete\""));
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"error\""));
    }

    @Test
    void ttsFailureSendsSegmentWithoutAudioAndContinues() {
        SpeechSession.StreamingResponseGenerator streamGen = (prompt, onSentence) -> {
            onSentence.accept("Good sentence.");
            onSentence.accept("Bad sentence.");
            return "Good sentence. Bad sentence.";
        };

        var failTts = mock(TextToSpeech.class);
        var callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(failTts.synthesise(any(), any())).thenAnswer(inv -> {
            if (callCount.getAndIncrement() == 1) throw new RuntimeException("TTS failed");
            return new SynthesisResult(new byte[]{1}, "wav", List.of());
        });

        executor = new TurnExecutor(
                new DefaultPromptAssembler(null),
                null, streamGen, failTts, Map.of(),
                sentText::add, sentBinary::add,
                new UtteranceSegmenter(EMPTY_VOCAB), floorState, null, null);

        executor.executeUserTurn(new UserTurn("hello", null, null, null));

        var segments = sentText.stream()
                .filter(json -> json.contains("\"type\":\"segment\""))
                .toList();
        assertThat(segments).hasSize(2);
        // First segment has audio, second does not
        assertThat(segments.get(0)).contains("\"hasAudio\":true");
        assertThat(segments.get(1)).contains("\"hasAudio\":false");
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"error\""));
        assertThat(sentText).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"turnComplete\""));
    }

    @Test
    void proactiveTurnSuppressedWhenFloorStateSays() {
        floorState.onStart(); // listening → suppress
        executor.enqueueProactiveTurn("hello proactive", System.currentTimeMillis());

        // Give the executor time to process (it's async for proactive)
        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        assertThat(sentText).noneSatisfy(json ->
                assertThat(json).contains("hello proactive"));
    }

    @Test
    void proactiveTurnDroppedWhenStaleAtDequeue() {
        long past = System.currentTimeMillis() - 10_000;
        executor.enqueueProactiveTurn("stale content", past);

        // Activity after evaluatedAt makes it stale
        floorState.onUserActivity();

        try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        assertThat(sentText).noneSatisfy(json ->
                assertThat(json).contains("stale content"));
    }
}
```

- [ ] **Step 6: Implement TurnExecutor**

```java
package io.casehub.agency.speech.ws;

import io.casehub.agency.speech.AssembledPrompt;
import io.casehub.agency.speech.ConversationTurn;
import io.casehub.agency.speech.PromptContext;
import io.casehub.agency.speech.SpeechPromptAssembler;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.agency.speech.ws.protocol.AvatarMessage;
import io.casehub.agency.speech.ws.protocol.MessageCodec;
import io.casehub.agency.speech.ws.protocol.TurnOrigin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

public class TurnExecutor implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger("turn-executor");

    private final SpeechPromptAssembler assembler;
    private final @Nullable Function<AssembledPrompt, String> responseGenerator;
    private final @Nullable SpeechSession.StreamingResponseGenerator streamingGenerator;
    private final TextToSpeech defaultTts;
    private final Map<String, TextToSpeech> ttsModels;
    private final Consumer<String> textSink;
    private final Consumer<byte[]> binarySink;
    private final UtteranceSegmenter segmenter;
    private final FloorState floorState;
    private final @Nullable Consumer<String> onResponse;
    private final @Nullable PromptContext promptContext;
    private final List<ConversationTurn> history = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("turn-executor").factory());

    private int turnSeq;

    public TurnExecutor(SpeechPromptAssembler assembler,
                        @Nullable Function<AssembledPrompt, String> responseGenerator,
                        @Nullable SpeechSession.StreamingResponseGenerator streamingGenerator,
                        TextToSpeech defaultTts,
                        Map<String, TextToSpeech> ttsModels,
                        Consumer<String> textSink,
                        Consumer<byte[]> binarySink,
                        UtteranceSegmenter segmenter,
                        FloorState floorState,
                        @Nullable Consumer<String> onResponse,
                        @Nullable PromptContext promptContext) {
        this.assembler = assembler;
        this.responseGenerator = responseGenerator;
        this.streamingGenerator = streamingGenerator;
        this.defaultTts = defaultTts;
        this.ttsModels = ttsModels;
        this.textSink = textSink;
        this.binarySink = binarySink;
        this.segmenter = segmenter;
        this.floorState = floorState;
        this.onResponse = onResponse;
        this.promptContext = promptContext;
    }

    /** Blocking — capture side calls this after finalising its work. */
    public void executeUserTurn(UserTurn turn) {
        try {
            executor.submit(() -> processUserTurn(turn)).get();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Turn execution failed", e);
        }
    }

    /** Non-blocking — proactive tick thread calls this. */
    public void enqueueProactiveTurn(String content, long evaluatedAt) {
        executor.submit(() -> processProactiveTurn(content, evaluatedAt));
    }

    int historySize() { return history.size(); }

    int turnSeq() { return turnSeq; }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void processUserTurn(UserTurn turn) {
        floorState.onTurnStart();
        int currentTurnSeq = ++turnSeq;
        int[] segmentIndex = {0};

        try {
            history.add(new ConversationTurn("user", turn.cleanText(), turn.speakerLabel()));
            TextToSpeech tts = resolveTts(turn.ttsModel());
            AssembledPrompt prompt = assemblePrompt(turn);

            String responseText;
            if (streamingGenerator != null) {
                responseText = streamingGenerator.generate(prompt, sentence -> {
                    var segments = segmenter.segment(sentence);
                    for (var seg : segments) {
                        synthesiseAndSendSegment(tts, seg, currentTurnSeq, segmentIndex[0]++);
                    }
                });
            } else if (responseGenerator != null) {
                responseText = responseGenerator.apply(prompt);
                if (responseText != null) {
                    var segments = segmenter.segment(responseText);
                    for (var seg : segments) {
                        synthesiseAndSendSegment(tts, seg, currentTurnSeq, segmentIndex[0]++);
                    }
                }
            } else {
                send(new AvatarMessage.Error("No LLM provider configured"));
                responseText = null;
            }

            if (responseText != null) {
                // Legacy backward compat
                send(new AvatarMessage.Response(responseText));
                history.add(new ConversationTurn("assistant", responseText));
                if (onResponse != null) onResponse.accept(responseText);
            }
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        } finally {
            floorState.onTurnEnd();
            long expiresAt = System.currentTimeMillis() + 5000;
            floorState.onTurnComplete(currentTurnSeq, expiresAt);
            send(new AvatarMessage.TurnComplete(currentTurnSeq, segmentIndex[0],
                    TurnOrigin.USER));
        }
    }

    private void processProactiveTurn(String content, long evaluatedAt) {
        if (floorState.shouldSuppressProactive()) {
            LOG.log(System.Logger.Level.DEBUG, "Proactive turn suppressed — floor busy");
            return;
        }
        if (floorState.lastUserActivity() > evaluatedAt) {
            LOG.log(System.Logger.Level.DEBUG,
                    "Proactive turn dropped — user activity after evaluation");
            return;
        }

        floorState.onTurnStart();
        int currentTurnSeq = ++turnSeq;
        int[] segmentIndex = {0};

        try {
            TextToSpeech tts = resolveTts(null);
            var segments = segmenter.segment(content);
            for (var seg : segments) {
                synthesiseAndSendSegment(tts, seg, currentTurnSeq, segmentIndex[0]++);
            }
            // Legacy backward compat
            send(new AvatarMessage.Response(content));
            history.add(new ConversationTurn("assistant", content));
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        } finally {
            floorState.onTurnEnd();
            long expiresAt = System.currentTimeMillis() + 5000;
            floorState.onTurnComplete(currentTurnSeq, segmentIndex[0],
                    TurnOrigin.PROACTIVE);
            send(new AvatarMessage.TurnComplete(currentTurnSeq, segmentIndex[0],
                    TurnOrigin.PROACTIVE));
        }
    }

    private void synthesiseAndSendSegment(TextToSpeech tts, ParsedSegment seg,
                                           int currentTurnSeq, int index) {
        try {
            SynthesisResult synthesis = tts.synthesise(seg.cleanText(),
                    new SynthesisOptions(null, null, "wav", true));
            var visemes = VisemeMapping.convert(synthesis.phonemes());

            send(new AvatarMessage.Segment(currentTurnSeq, index, seg.cleanText(),
                    true, visemes, seg.cues()));
            // Legacy backward compat
            send(new AvatarMessage.Phonemes(visemes));
            binarySink.accept(synthesis.audioData());
        } catch (Exception e) {
            send(new AvatarMessage.Segment(currentTurnSeq, index, seg.cleanText(),
                    false, List.of(), List.of()));
            send(new AvatarMessage.Error("TTS failed for segment " + index
                    + ": " + e.getMessage()));
        }
    }

    private AssembledPrompt assemblePrompt(UserTurn turn) {
        AssembledPrompt prompt = promptContext != null
                ? assembler.assemble(turn.cleanText(), List.copyOf(history), promptContext)
                : assembler.assemble(turn.cleanText(), List.copyOf(history));
        if (turn.llmModel() != null) {
            prompt = new AssembledPrompt(prompt.systemPrompt(), prompt.userPrompt(),
                    turn.llmModel());
        }
        return prompt;
    }

    private TextToSpeech resolveTts(@Nullable String modelKey) {
        if (modelKey != null && ttsModels.containsKey(modelKey)) {
            return ttsModels.get(modelKey);
        }
        return defaultTts;
    }

    private void send(AvatarMessage msg) {
        textSink.accept(MessageCodec.encode(msg));
    }
}
```

**Note:** The `processProactiveTurn` calls `floorState.onTurnComplete(turnSeq, segmentIndex, origin)` —
but `FloorState.onTurnComplete` takes `(int, long)`. The turn origin goes
only on the outbound `TurnComplete` message, not FloorState. Adjust the
call to `floorState.onTurnComplete(currentTurnSeq, expiresAt)`.

- [ ] **Step 7: Run TurnExecutor tests**

Run: `mvn --batch-mode test -pl speech-ws -Dtest=TurnExecutorTest`
Expected: ALL PASS

- [ ] **Step 8: Run all speech-ws tests**

Run: `mvn --batch-mode test -pl speech-ws`
Expected: ALL PASS (existing tests untouched, new types are independent)

- [ ] **Step 9: Commit**

```bash
git add speech-ws/src/main/java/io/casehub/agency/speech/ws/UserTurn.java \
       speech-ws/src/main/java/io/casehub/agency/speech/ws/FloorState.java \
       speech-ws/src/main/java/io/casehub/agency/speech/ws/TurnExecutor.java \
       speech-ws/src/test/java/io/casehub/agency/speech/ws/FloorStateTest.java \
       speech-ws/src/test/java/io/casehub/agency/speech/ws/TurnExecutorTest.java
git commit -m "feat(#457): UserTurn, FloorState, TurnExecutor — capture/turn split types (D10)"
```

---

### Task 3: Refactor SpeechSession, wire FloorState/TurnExecutor, adapt dispatch

**Files:**
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechSession.java`
- Modify: `speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechWebSocket.java`
- Modify: `speech-ws/src/test/java/io/casehub/agency/speech/ws/SpeechSessionTest.java`
- Modify: `speech-ws/src/test/java/io/casehub/agency/speech/ws/SpeechSessionSpeakerTest.java`

**Interfaces:**
- Consumes: `TurnExecutor`, `FloorState`, `UserTurn`, `UtteranceSegmenter`
- Modifies: `SpeechSession` — capture-side coordinator only. Turn processing
  delegated to `TurnExecutor`. New methods: `handlePlaybackComplete(int)`,
  `handleUserActivity()`.
- Modifies: `SpeechWebSocket.onText` — dispatch `PlaybackComplete` and
  `UserActivity` messages. Proactive tick checks `floorState.shouldSuppressProactive()`.

- [ ] **Step 1: Refactor SpeechSession — add TurnExecutor and FloorState fields**

SpeechSession keeps its existing constructor signature but internally
creates TurnExecutor and FloorState. The existing mutable fields that
belong to the turn side (history, turn processing) move into TurnExecutor.
Capture-side fields (stream, polling, ring buffer, speaker ID) stay.

Add an optional `UtteranceSegmenter` parameter to the most complete
constructor. The old constructors pass `null`, which falls back to a
no-op segmenter internally.

Key changes to SpeechSession:
- Add fields: `TurnExecutor turnExecutor`, `FloorState floorState`
- Remove fields: `history` (moved to TurnExecutor)
- Constructor: create FloorState, create TurnExecutor with turn-side deps
- `handleStop()`: capture-side only → enqueue UserTurn
- `handleText()`: normalise → enqueue UserTurn
- `handleProactiveSpeech()`: delegate to turnExecutor.enqueueProactiveTurn()
- `handleStart()`: add `floorState.onStart()`
- New: `handlePlaybackComplete(int turnSeq)` → `floorState.onPlaybackComplete()`
- New: `handleUserActivity()` → `floorState.onUserActivity()`
- `close()`: also close turnExecutor
- `historySize()`: delegate to turnExecutor

- [ ] **Step 2: Rewrite handleStop — capture side only, enqueue turn**

The refactored `handleStop()`:

```java
public void handleStop() {
    stopPolling();
    if (stream == null) {
        send(new AvatarMessage.Error("No active recording session"));
        return;
    }
    try {
        TranscriptionResult result = stream.finalResult();
        stream.close();
        stream = null;

        String raw = result.text();
        String cleanText = ensureTrailingPunctuation(cleanupConfig.apply(raw));
        send(new AvatarMessage.Transcript(cleanText));
        floorState.onStopOrText();

        String speakerLabel = identifySpeaker();

        turnExecutor.executeUserTurn(
                new UserTurn(cleanText, speakerLabel, activeLlmModel, activeTtsModel));
    } catch (Exception e) {
        send(new AvatarMessage.Error(e.getMessage()));
    }
}
```

- [ ] **Step 3: Rewrite handleText — normalise, enqueue**

```java
public void handleText(String text, @Nullable String llmModel, @Nullable String ttsModel) {
    try {
        String cleanText = cleanupConfig.apply(text);
        send(new AvatarMessage.Transcript(cleanText));
        floorState.onStopOrText();

        turnExecutor.executeUserTurn(
                new UserTurn(cleanText, null, llmModel, ttsModel));

        recordCognitionInteraction(cleanText);
    } catch (Exception e) {
        send(new AvatarMessage.Error(e.getMessage()));
    }
}
```

- [ ] **Step 4: Rewrite handleProactiveSpeech**

```java
public void handleProactiveSpeech(String content) {
    turnExecutor.enqueueProactiveTurn(content, System.currentTimeMillis());
}
```

- [ ] **Step 5: Add handlePlaybackComplete and handleUserActivity**

```java
public void handlePlaybackComplete(int turnSeq) {
    floorState.onPlaybackComplete(turnSeq);
}

public void handleUserActivity() {
    floorState.onUserActivity();
}
```

- [ ] **Step 6: Wire handleStart with floorState**

Add `floorState.onStart()` to `handleStart()`:

```java
public void handleStart(AvatarMessage.Start msg) {
    stopPolling();
    if (stream != null) {
        stream.close();
    }
    floorState.onStart();
    // ... rest unchanged
}
```

- [ ] **Step 7: Update close() to shut down TurnExecutor**

```java
public void close() {
    isClosed = true;
    stopPolling();
    turnExecutor.close();
    if (stream != null) {
        stream.close();
        stream = null;
    }
}
```

- [ ] **Step 8: Update SpeechWebSocket dispatch**

In `onText()`, add cases for the new client→server messages:

```java
case AvatarMessage.PlaybackComplete pc -> session.handlePlaybackComplete(pc.turnSeq());
case AvatarMessage.UserActivity ua -> session.handleUserActivity();
```

In `startProactiveTick()`, add suppression check:

```java
// Before evaluateProactive, check floor state
if (session.shouldSuppressProactive()) return; // skip this tick
```

Add to SpeechSession:
```java
public boolean shouldSuppressProactive() {
    return floorState.shouldSuppressProactive();
}
```

- [ ] **Step 9: Run existing tests — verify backward compat**

Run: `mvn --batch-mode test -pl speech-ws`

Existing tests should pass because:
1. TurnExecutor emits legacy `Response`/`Phonemes` alongside new `Segment`/`TurnComplete`
2. `anySatisfy` checks in existing tests still find their expected messages
3. Additional messages (Segment, TurnComplete) don't break any existing assertions
4. `handleStop`/`handleText` still produce the same observable behavior

Fix any failures — most likely:
- Constructor changes need updating in test setUp()
- `historySize()` delegation
- Timing message differences (TurnExecutor timing vs inline timing)

- [ ] **Step 10: Adapt SpeechSessionTest setUp for new constructor**

If the constructor signature changed (added `@Nullable UtteranceSegmenter`),
update the test setUp to pass `null`:

```java
session = new SpeechSession(
        sttService, ttsService, cleanupConfig,
        null,
        new DefaultPromptAssembler(null),
        sentTextMessages::add,
        sentBinaryMessages::add);
// null segmenter → TurnExecutor uses fallback sentence splitting
```

- [ ] **Step 11: Run all tests — full green**

Run: `mvn --batch-mode test -pl speech-api,speech-ws`
Expected: ALL PASS

- [ ] **Step 12: Commit**

```bash
git add speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechSession.java \
       speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechWebSocket.java \
       speech-ws/src/test/java/io/casehub/agency/speech/ws/SpeechSessionTest.java \
       speech-ws/src/test/java/io/casehub/agency/speech/ws/SpeechSessionSpeakerTest.java
git commit -m "feat(#457): capture/turn split — SpeechSession delegates to TurnExecutor (D10)"
```

---

## Deferred (later batches)

These items are designed but not in this plan:
- **Unacknowledged-turn expiry timer** — FloorState has the state, but
  no timer thread polls `isUnacknowledgedExpired()` yet
- **SilenceAttentionPolicy** (D8) — idle attention bids after silence
- **MoodMapper** (D16) — PAD→discrete mood with hysteresis
- **Mark timing integration** (D4) — blocked by connectors peer issue
- **Binary audio frame headers** (D3) — 8-byte header for segment pairing
- **Streaming TTS path in TurnExecutor** — currently only non-streaming
  TTS is used in `synthesiseAndSendSegment`; wire `StreamingTextToSpeech`
  when needed
- **Client-side Segment/TurnComplete handling** — speech-demo update
- **Remove legacy Response/Phonemes emission** — after client update

## References

- [2026-11-18-m2m-animation-integration-design.md] — design spec
- [decisions.md D10:332] — session concurrency: capture/turn split
- [decisions.md D13:450] — conversational floor states
- [decisions.md D7] — LLM prompt via GesturePromptSection
- [decisions.md D3] — turn-framed protocol
- [decisions.md D12] — performance vocabulary SPI
- [SpeechSession.java] — current monolithic session (535 lines)
- [SpeechWebSocket.java] — WebSocket endpoint and proactive tick
- [DefaultPromptAssembler.java] — current prompt assembly
- [UtteranceSegmenter.java] — Batch 1 tag parser
- [PerformanceVocabulary.java] — speech-api SPI
- [PromptSection.java / PromptContext.java] — speech-api composition SPIs
- [AvatarMessage.java] — protocol types including Batch 1 additions
- GitHub #457, #491, #492

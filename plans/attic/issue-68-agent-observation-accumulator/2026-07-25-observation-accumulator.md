# Observation Accumulator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #68 — Agent observation accumulator
**Issue group:** #68

**Goal:** Build the observation sub-package under `io.casehub.blocks.summarisation` — tiered, demand-driven event rendering for LLM agent prompts with RAG-able chunk production.

**Architecture:** Terminal consumer of the summarisation pipeline. `ObservationAccumulator<E>` wraps a synchronized buffer and delegates to an `ObservationRenderer<E>` SPI. `TieredObservationRenderer<E>` is the standard implementation that routes to verbatim/grouped/summarised tiers based on batch size. All types are pure Java, zero CDI.

**Tech Stack:** Java 21, JUnit 5, AssertJ. No new dependencies.

## Global Constraints

- Package: `io.casehub.blocks.summarisation.observation`
- Pure Java — no CDI, no Quarkus, no framework dependencies
- Thread safety: synchronized buffer, consistent with `EventAccumulator`
- `ObservationTier` is a record (mirrors `EventLevel`), not an enum
- `ObservationResult.empty()` factory — no null returns from `CompletionStage`
- Constructor validation follows `WindowPolicy` compact constructor precedent
- Test style: plain JUnit 5 + AssertJ, no mocking framework needed

---

### Task 1: Foundation Types

**Files:**
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/ObservationTier.java`
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/ObservationContext.java`
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/ObservationChunk.java`
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/ObservationResult.java`
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/ObservationRenderer.java`
- Test: `src/test/java/io/casehub/blocks/summarisation/observation/ObservationResultTest.java`

**Interfaces:**
- Consumes: `LevelEvent<E>` from `io.casehub.blocks.summarisation`
- Produces: `ObservationTier`, `ObservationContext`, `ObservationChunk`, `ObservationResult`, `ObservationRenderer<E>` — used by Tasks 2, 3, 4

- [ ] **Step 1: Write ObservationResult tests**

```java
package io.casehub.blocks.summarisation.observation;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ObservationResultTest {

    @Test
    void empty_returnsZeroCountNullTier() {
        var result = ObservationResult.empty(5000);
        assertThat(result.renderedText()).isEmpty();
        assertThat(result.chunks()).isEmpty();
        assertThat(result.eventCount()).isZero();
        assertThat(result.timeSinceLastDrain()).isEqualTo(5000);
        assertThat(result.tier()).isNull();
    }

    @Test
    void chunks_areDefensivelyCopied() {
        var chunk = new ObservationChunk("text", 100, ObservationTier.VERBATIM, 1, Map.of());
        var mutableList = new java.util.ArrayList<>(List.of(chunk));
        var result = new ObservationResult("text", mutableList, 1, 500, ObservationTier.VERBATIM);
        mutableList.add(new ObservationChunk("extra", 200, ObservationTier.VERBATIM, 1, Map.of()));
        assertThat(result.chunks()).hasSize(1);
    }

    @Test
    void chunk_metadata_isDefensivelyCopied() {
        var mutableMap = new java.util.HashMap<>(Map.of("key", "value"));
        var chunk = new ObservationChunk("text", 100, ObservationTier.VERBATIM, 1, mutableMap);
        mutableMap.put("extra", "data");
        assertThat(chunk.metadata()).hasSize(1);
    }

    @Test
    void tier_predefinedConstants() {
        assertThat(ObservationTier.VERBATIM.name()).isEqualTo("verbatim");
        assertThat(ObservationTier.VERBATIM.ordinal()).isZero();
        assertThat(ObservationTier.GROUPED.name()).isEqualTo("grouped");
        assertThat(ObservationTier.GROUPED.ordinal()).isEqualTo(1);
        assertThat(ObservationTier.SUMMARISED.name()).isEqualTo("summarised");
        assertThat(ObservationTier.SUMMARISED.ordinal()).isEqualTo(2);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=ObservationResultTest`
Expected: compilation failure — types don't exist yet

- [ ] **Step 3: Create all five foundation types**

`ObservationTier.java`:
```java
package io.casehub.blocks.summarisation.observation;

public record ObservationTier(String name, int ordinal) {
    public static final ObservationTier VERBATIM = new ObservationTier("verbatim", 0);
    public static final ObservationTier GROUPED = new ObservationTier("grouped", 1);
    public static final ObservationTier SUMMARISED = new ObservationTier("summarised", 2);
}
```

`ObservationContext.java`:
```java
package io.casehub.blocks.summarisation.observation;

public record ObservationContext(long currentTime, long timeSinceLastDrain) {}
```

`ObservationChunk.java`:
```java
package io.casehub.blocks.summarisation.observation;

import java.util.Map;

public record ObservationChunk(
        String content,
        long timestamp,
        ObservationTier tier,
        int eventCount,
        Map<String, String> metadata) {
    public ObservationChunk {
        metadata = Map.copyOf(metadata);
    }
}
```

`ObservationResult.java`:
```java
package io.casehub.blocks.summarisation.observation;

import java.util.List;

public record ObservationResult(
        String renderedText,
        List<ObservationChunk> chunks,
        int eventCount,
        long timeSinceLastDrain,
        ObservationTier tier) {
    public ObservationResult {
        chunks = List.copyOf(chunks);
    }

    public static ObservationResult empty(long timeSinceLastDrain) {
        return new ObservationResult("", List.of(), 0, timeSinceLastDrain, null);
    }
}
```

`ObservationRenderer.java`:
```java
package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.LevelEvent;
import java.util.List;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ObservationRenderer<E> {
    CompletionStage<ObservationResult> render(
            List<LevelEvent<E>> events, ObservationContext context);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=ObservationResultTest`
Expected: all 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/casehub/blocks/summarisation/observation/ src/test/java/io/casehub/blocks/summarisation/observation/
git commit -m "feat(#68): foundation types — ObservationTier, ObservationContext, ObservationChunk, ObservationResult, ObservationRenderer"
```

---

### Task 2: TieredObservationRenderer

**Files:**
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/TieredObservationRenderer.java`
- Test: `src/test/java/io/casehub/blocks/summarisation/observation/TieredObservationRendererTest.java`

**Interfaces:**
- Consumes: `ObservationTier`, `ObservationContext`, `ObservationChunk`, `ObservationResult`, `ObservationRenderer<E>` from Task 1; `LevelEvent<E>`, `Summariser<E, String>`, `EventLevel` from `io.casehub.blocks.summarisation`
- Produces: `TieredObservationRenderer<E>` — used by Tasks 3, 4

- [ ] **Step 1: Write constructor validation tests**

```java
package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;

class TieredObservationRendererTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    Function<String, String>   eventRenderer     = s -> s;
    Function<String, String>   groupKeyExtractor = s -> s.split(":")[0];
    Summariser<String, String> summariser        = batch ->
                                                           CompletableFuture.completedFuture(List.of("Summary of " + batch.size() + " events"));

    @Test
    void rejects_negativeVerbatimThreshold() {
        assertThatThrownBy(() -> new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verbatimThreshold");
    }

    @Test
    void rejects_groupedThresholdNotGreaterThanVerbatim() {
        assertThatThrownBy(() -> new TieredObservationRenderer<>(
                eventRenderer, groupKeyExtractor, 5, 5, summariser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupedThreshold");
    }

    @Test
    void rejects_groupedThresholdLessThanVerbatim() {
        assertThatThrownBy(() -> new TieredObservationRenderer<>(
                eventRenderer, groupKeyExtractor, 5, 3, summariser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupedThreshold");
    }

    @Test
    void accepts_zeroVerbatimThreshold() {
        assertThatCode(() -> new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 0))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=TieredObservationRendererTest`
Expected: compilation failure — `TieredObservationRenderer` doesn't exist

- [ ] **Step 3: Implement TieredObservationRenderer skeleton with validation**

```java
package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TieredObservationRenderer<E> implements ObservationRenderer<E> {

    private final Function<E, String> eventRenderer;
    private final Function<E, String> groupKeyExtractor;
    private final int verbatimThreshold;
    private final int groupedThreshold;
    private final Summariser<E, String> summariser;
    private final Function<ObservationContext, String> headerFormatter;

    public TieredObservationRenderer(
            Function<E, String> eventRenderer,
            Function<E, String> groupKeyExtractor,
            int verbatimThreshold) {
        this(eventRenderer, groupKeyExtractor, verbatimThreshold,
                Integer.MAX_VALUE, null, null);
    }

    public TieredObservationRenderer(
            Function<E, String> eventRenderer,
            Function<E, String> groupKeyExtractor,
            int verbatimThreshold,
            int groupedThreshold,
            Summariser<E, String> summariser) {
        this(eventRenderer, groupKeyExtractor, verbatimThreshold,
                groupedThreshold, summariser, null);
    }

    private TieredObservationRenderer(
            Function<E, String> eventRenderer,
            Function<E, String> groupKeyExtractor,
            int verbatimThreshold,
            int groupedThreshold,
            Summariser<E, String> summariser,
            Function<ObservationContext, String> headerFormatter) {
        if (verbatimThreshold < 0) {
            throw new IllegalArgumentException(
                    "verbatimThreshold must be >= 0, was: " + verbatimThreshold);
        }
        if (summariser != null && groupedThreshold <= verbatimThreshold) {
            throw new IllegalArgumentException(
                    "groupedThreshold must be > verbatimThreshold, was: "
                    + groupedThreshold + " <= " + verbatimThreshold);
        }
        this.eventRenderer = eventRenderer;
        this.groupKeyExtractor = groupKeyExtractor;
        this.verbatimThreshold = verbatimThreshold;
        this.groupedThreshold = summariser != null ? groupedThreshold : Integer.MAX_VALUE;
        this.summariser = summariser;
        this.headerFormatter = headerFormatter != null
                ? headerFormatter : TieredObservationRenderer::defaultHeader;
    }

    public TieredObservationRenderer<E> withHeaderFormatter(
            Function<ObservationContext, String> headerFormatter) {
        return new TieredObservationRenderer<>(
                eventRenderer, groupKeyExtractor, verbatimThreshold,
                summariser != null ? groupedThreshold : Integer.MAX_VALUE,
                summariser, headerFormatter);
    }

    @Override
    public CompletionStage<ObservationResult> render(
            List<LevelEvent<E>> events, ObservationContext context) {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ObservationResult.empty(context.timeSinceLastDrain()));
        }
        String header = headerFormatter.apply(context);
        if (events.size() <= verbatimThreshold) {
            return CompletableFuture.completedFuture(
                    renderVerbatim(events, context, header));
        } else if (events.size() <= groupedThreshold) {
            return CompletableFuture.completedFuture(
                    renderGrouped(events, context, header));
        } else {
            return renderSummarised(events, context, header);
        }
    }

    private ObservationResult renderVerbatim(
            List<LevelEvent<E>> events, ObservationContext context, String header) {
        var sb = new StringBuilder(header).append("\n");
        var chunks = new ArrayList<ObservationChunk>();
        for (var event : events) {
            long ago = context.currentTime() - event.timestamp();
            String text = eventRenderer.apply(event.payload());
            sb.append("- [").append(formatAgo(ago)).append(" ago] ")
              .append(text).append("\n");
            chunks.add(new ObservationChunk(
                    text, event.timestamp(), ObservationTier.VERBATIM, 1, Map.of()));
        }
        return new ObservationResult(sb.toString(), chunks, events.size(),
                context.timeSinceLastDrain(), ObservationTier.VERBATIM);
    }

    private ObservationResult renderGrouped(
            List<LevelEvent<E>> events, ObservationContext context, String header) {
        var groups = new LinkedHashMap<String, List<LevelEvent<E>>>();
        for (var event : events) {
            groups.computeIfAbsent(groupKeyExtractor.apply(event.payload()),
                    k -> new ArrayList<>()).add(event);
        }
        var sb = new StringBuilder(header).append("\n");
        var chunks = new ArrayList<ObservationChunk>();
        for (var entry : groups.entrySet()) {
            String groupText = entry.getValue().stream()
                    .map(e -> eventRenderer.apply(e.payload()))
                    .collect(Collectors.joining(". "));
            String line = entry.getKey() + ": " + groupText;
            sb.append(line).append("\n");
            chunks.add(new ObservationChunk(
                    line, context.currentTime(), ObservationTier.GROUPED,
                    entry.getValue().size(),
                    Map.of("groupKey", entry.getKey())));
        }
        return new ObservationResult(sb.toString(), chunks, events.size(),
                context.timeSinceLastDrain(), ObservationTier.GROUPED);
    }

    private CompletionStage<ObservationResult> renderSummarised(
            List<LevelEvent<E>> events, ObservationContext context, String header) {
        return summariser.summarise(events).thenApply(parts -> {
            String summary = String.join("\n", parts);
            var sb = new StringBuilder(header).append("\n")
                    .append(summary).append("\n");
            var chunk = new ObservationChunk(
                    summary, context.currentTime(), ObservationTier.SUMMARISED,
                    events.size(), Map.of());
            return new ObservationResult(sb.toString(), List.of(chunk),
                    events.size(), context.timeSinceLastDrain(),
                    ObservationTier.SUMMARISED);
        });
    }

    private static String defaultHeader(ObservationContext context) {
        return "== What Just Happened ("
                + formatDuration(context.timeSinceLastDrain())
                + " since your last action) ==";
    }

    static String formatDuration(long millis) {
        if (millis < 1000) return millis + "ms";
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + " second" + (seconds == 1 ? "" : "s");
        long minutes = seconds / 60;
        return minutes + " minute" + (minutes == 1 ? "" : "s");
    }

    static String formatAgo(long millis) {
        if (millis < 1000) return millis + "ms";
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        return minutes + "m";
    }
}
```

- [ ] **Step 4: Run validation tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=TieredObservationRendererTest`
Expected: all 4 validation tests PASS

- [ ] **Step 5: Add tier selection and rendering tests**

Append to `TieredObservationRendererTest`:

```java
    private LevelEvent<String> event(String payload, long timestamp) {
        return new LevelEvent<>(payload, timestamp, LEVEL, null);
    }

    private ObservationContext ctx(long now, long sinceLast) {
        return new ObservationContext(now, sinceLast);
    }

    // --- Verbatim tier ---

    @Test
    void verbatim_belowThreshold_rendersEachEvent() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 5);
        var events = List.of(event("hello", 1000), event("world", 2000));
        var result = renderer.render(events, ctx(3000, 5000))
                .toCompletableFuture().join();

        assertThat(result.tier()).isEqualTo(ObservationTier.VERBATIM);
        assertThat(result.eventCount()).isEqualTo(2);
        assertThat(result.renderedText()).contains("[2s ago]", "hello");
        assertThat(result.renderedText()).contains("[1s ago]", "world");
        assertThat(result.chunks()).hasSize(2);
        assertThat(result.chunks().get(0).content()).isEqualTo("hello");
        assertThat(result.chunks().get(0).eventCount()).isEqualTo(1);
        assertThat(result.chunks().get(0).tier()).isEqualTo(ObservationTier.VERBATIM);
        assertThat(result.chunks().get(0).timestamp()).isEqualTo(1000);
    }

    @Test
    void verbatim_exactlyAtThreshold_usesVerbatim() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 3);
        var events = List.of(event("a", 1), event("b", 2), event("c", 3));
        var result = renderer.render(events, ctx(10, 5000))
                .toCompletableFuture().join();
        assertThat(result.tier()).isEqualTo(ObservationTier.VERBATIM);
        assertThat(result.chunks()).hasSize(3);
    }

    // --- Grouped tier ---

    @Test
    void grouped_aboveVerbatimThreshold_groupsByKey() {
        var renderer = new TieredObservationRenderer<>(
                s -> s.split(":")[1], s -> s.split(":")[0], 2);
        var events = List.of(
                event("move:entered room", 1),
                event("talk:said hello", 2),
                event("move:left room", 3));
        var result = renderer.render(events, ctx(10, 5000))
                .toCompletableFuture().join();

        assertThat(result.tier()).isEqualTo(ObservationTier.GROUPED);
        assertThat(result.eventCount()).isEqualTo(3);
        assertThat(result.chunks()).hasSize(2);
        assertThat(result.chunks().get(0).metadata()).containsEntry("groupKey", "move");
        assertThat(result.chunks().get(0).eventCount()).isEqualTo(2);
        assertThat(result.chunks().get(1).metadata()).containsEntry("groupKey", "talk");
        assertThat(result.chunks().get(1).eventCount()).isEqualTo(1);
        assertThat(result.renderedText()).contains("move: entered room. left room");
        assertThat(result.renderedText()).contains("talk: said hello");
    }

    // --- Summarised tier ---

    @Test
    void summarised_aboveGroupedThreshold_delegatesToSummariser() {
        var renderer = new TieredObservationRenderer<>(
                eventRenderer, groupKeyExtractor, 2, 4, summariser);
        var events = List.of(
                event("a", 1), event("b", 2), event("c", 3),
                event("d", 4), event("e", 5));
        var result = renderer.render(events, ctx(10, 5000))
                .toCompletableFuture().join();

        assertThat(result.tier()).isEqualTo(ObservationTier.SUMMARISED);
        assertThat(result.eventCount()).isEqualTo(5);
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().get(0).content()).isEqualTo("Summary of 5 events");
        assertThat(result.chunks().get(0).eventCount()).isEqualTo(5);
        assertThat(result.chunks().get(0).tier()).isEqualTo(ObservationTier.SUMMARISED);
    }

    @Test
    void summarised_multiElementList_joinedWithNewlines() {
        Summariser<String, String> multiPartSummariser = batch ->
                CompletableFuture.completedFuture(List.of("Part one.", "Part two."));
        var renderer = new TieredObservationRenderer<>(
                eventRenderer, groupKeyExtractor, 1, 2, multiPartSummariser);
        var events = List.of(event("a", 1), event("b", 2), event("c", 3));
        var result = renderer.render(events, ctx(10, 5000))
                .toCompletableFuture().join();

        assertThat(result.chunks().get(0).content()).isEqualTo("Part one.\nPart two.");
    }

    // --- Two-tier (no summariser) ---

    @Test
    void twoTier_aboveVerbatim_usesGrouped() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 2);
        var events = List.of(event("a:1", 1), event("b:2", 2), event("a:3", 3));
        var result = renderer.render(events, ctx(10, 5000))
                .toCompletableFuture().join();
        assertThat(result.tier()).isEqualTo(ObservationTier.GROUPED);
    }

    @Test
    void twoTier_largeBuffer_stillUsesGrouped() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 2);
        var events = new java.util.ArrayList<LevelEvent<String>>();
        for (int i = 0; i < 100; i++) {
            events.add(event("g" + (i % 3) + ":event" + i, i));
        }
        var result = renderer.render(events, ctx(200, 5000))
                .toCompletableFuture().join();
        assertThat(result.tier()).isEqualTo(ObservationTier.GROUPED);
    }

    // --- Empty events ---

    @Test
    void emptyEvents_returnsEmpty() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 5);
        var result = renderer.render(List.of(), ctx(10, 5000))
                .toCompletableFuture().join();
        assertThat(result.eventCount()).isZero();
        assertThat(result.tier()).isNull();
        assertThat(result.renderedText()).isEmpty();
    }

    // --- Header ---

    @Test
    void defaultHeader_includesElapsedTime() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 5);
        var result = renderer.render(List.of(event("x", 1)), ctx(10, 8000))
                .toCompletableFuture().join();
        assertThat(result.renderedText()).contains("8 seconds since your last action");
    }

    @Test
    void customHeader_replacesDefault() {
        var renderer = new TieredObservationRenderer<>(eventRenderer, groupKeyExtractor, 5)
                .withHeaderFormatter(ctx -> "== Custom (" + ctx.timeSinceLastDrain() + "ms) ==");
        var result = renderer.render(List.of(event("x", 1)), ctx(10, 3000))
                .toCompletableFuture().join();
        assertThat(result.renderedText()).contains("== Custom (3000ms) ==");
        assertThat(result.renderedText()).doesNotContain("What Just Happened");
    }

    // --- Format helpers ---

    @Test
    void formatDuration_variousRanges() {
        assertThat(TieredObservationRenderer.formatDuration(500)).isEqualTo("500ms");
        assertThat(TieredObservationRenderer.formatDuration(1000)).isEqualTo("1 second");
        assertThat(TieredObservationRenderer.formatDuration(8000)).isEqualTo("8 seconds");
        assertThat(TieredObservationRenderer.formatDuration(60000)).isEqualTo("1 minute");
        assertThat(TieredObservationRenderer.formatDuration(120000)).isEqualTo("2 minutes");
    }

    @Test
    void formatAgo_variousRanges() {
        assertThat(TieredObservationRenderer.formatAgo(500)).isEqualTo("500ms");
        assertThat(TieredObservationRenderer.formatAgo(2000)).isEqualTo("2s");
        assertThat(TieredObservationRenderer.formatAgo(65000)).isEqualTo("1m");
    }
```

- [ ] **Step 6: Run all renderer tests**

Run: `mvn --batch-mode test -pl . -Dtest=TieredObservationRendererTest`
Expected: all tests PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/casehub/blocks/summarisation/observation/TieredObservationRenderer.java src/test/java/io/casehub/blocks/summarisation/observation/TieredObservationRendererTest.java
git commit -m "feat(#68): TieredObservationRenderer — verbatim, grouped, summarised tiers with configurable header"
```

---

### Task 3: ObservationAccumulator

**Files:**
- Create: `src/main/java/io/casehub/blocks/summarisation/observation/ObservationAccumulator.java`
- Test: `src/test/java/io/casehub/blocks/summarisation/observation/ObservationAccumulatorTest.java`

**Interfaces:**
- Consumes: `ObservationRenderer<E>`, `ObservationResult`, `ObservationContext` from Task 1; `LevelEvent<E>`, `EventLevel` from `io.casehub.blocks.summarisation`
- Produces: `ObservationAccumulator<E>` — used by Task 4

- [ ] **Step 1: Write accumulator tests**

```java
package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationAccumulatorTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    ObservationRenderer<String> verbatimRenderer = (events, ctx) ->
                                                           CompletableFuture.completedFuture(new ObservationResult(
                                                                   "rendered:" + events.size(),
                                                                   events.stream().map(e -> new ObservationChunk(
                                                                           e.payload(), e.timestamp(),
                                                                           ObservationTier.VERBATIM, 1, Map.of())).toList(),
                                                                   events.size(),
                                                                   ctx.timeSinceLastDrain(),
                                                                   ObservationTier.VERBATIM));

    @Test
    void collectAndDrain_lifecycle() {
        var acc = new ObservationAccumulator<>(verbatimRenderer, 1000);
        acc.collect(new LevelEvent<>("a", 1100, LEVEL, null));
        acc.collect(new LevelEvent<>("b", 1200, LEVEL, null));
        assertThat(acc.eventCount()).isEqualTo(2);

        var result = acc.drainObservation(1500).toCompletableFuture().join();
        assertThat(result.eventCount()).isEqualTo(2);
        assertThat(result.renderedText()).isEqualTo("rendered:2");
        assertThat(acc.eventCount()).isZero();
    }

    @Test
    void emptyDrain_returnsEmptyResult() {
        var acc    = new ObservationAccumulator<>(verbatimRenderer, 1000);
        var result = acc.drainObservation(2000).toCompletableFuture().join();
        assertThat(result.eventCount()).isZero();
        assertThat(result.tier()).isNull();
        assertThat(result.renderedText()).isEmpty();
        assertThat(result.timeSinceLastDrain()).isEqualTo(1000);
    }

    @Test
    void emptyDrain_doesNotUpdateLastDrainTimestamp() {
        var acc = new ObservationAccumulator<>(verbatimRenderer, 1000);
        acc.drainObservation(2000).toCompletableFuture().join();
        acc.drainObservation(3000).toCompletableFuture().join();

        acc.collect(new LevelEvent<>("a", 3100, LEVEL, null));
        var result = acc.drainObservation(4000).toCompletableFuture().join();
        assertThat(result.timeSinceLastDrain())
                .as("time since creation, not since empty drains")
                .isEqualTo(3000);
    }

    @Test
    void firstDrain_timeSinceCreation() {
        var acc = new ObservationAccumulator<>(verbatimRenderer, 1000);
        acc.collect(new LevelEvent<>("a", 1100, LEVEL, null));
        var result = acc.drainObservation(1500).toCompletableFuture().join();
        assertThat(result.timeSinceLastDrain()).isEqualTo(500);
    }

    @Test
    void subsequentDrain_timeSinceLastNonEmptyDrain() {
        var acc = new ObservationAccumulator<>(verbatimRenderer, 1000);
        acc.collect(new LevelEvent<>("a", 1100, LEVEL, null));
        acc.drainObservation(2000).toCompletableFuture().join();

        acc.collect(new LevelEvent<>("b", 2500, LEVEL, null));
        var result = acc.drainObservation(3000).toCompletableFuture().join();
        assertThat(result.timeSinceLastDrain()).isEqualTo(1000);
    }

    @Test
    void clear_emptiesBuffer() {
        var acc = new ObservationAccumulator<>(verbatimRenderer, 1000);
        acc.collect(new LevelEvent<>("a", 1100, LEVEL, null));
        acc.collect(new LevelEvent<>("b", 1200, LEVEL, null));
        acc.clear();
        assertThat(acc.eventCount()).isZero();

        var result = acc.drainObservation(2000).toCompletableFuture().join();
        assertThat(result.eventCount()).isZero();
    }

    @Test
    void rendererFailure_propagatesViaCompletionStage() {
        ObservationRenderer<String> failingRenderer = (events, ctx) ->
                                                              CompletableFuture.failedFuture(new RuntimeException("LLM timeout"));
        var acc = new ObservationAccumulator<>(failingRenderer, 1000);
        acc.collect(new LevelEvent<>("a", 1100, LEVEL, null));

        var stage = acc.drainObservation(2000);
        assertThatThrownBy(() -> stage.toCompletableFuture().join())
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM timeout");

        assertThat(acc.eventCount()).as("buffer cleared before render").isZero();
    }

    @Test
    void concurrentCollectDuringDrain_noDataLoss() throws Exception {
        final int preCollect        = 50;
        final int concurrentCollect = 200;
        var       acc               = new ObservationAccumulator<>(verbatimRenderer, 0);

        for (int i = 0; i < preCollect; i++) {
            acc.collect(new LevelEvent<>("pre-" + i, i, LEVEL, null));
        }

        var             latch    = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < concurrentCollect; i++) {
                final int val = i;
                executor.submit(() -> {
                    try {latch.await();} catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    acc.collect(new LevelEvent<>("concurrent-" + val, 1000 + val, LEVEL, null));
                });
            }

            latch.countDown();
            var result = acc.drainObservation(5000).toCompletableFuture().join();

            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            var result2 = acc.drainObservation(6000).toCompletableFuture().join();
            int total   = result.eventCount() + result2.eventCount() + acc.eventCount();
            assertThat(total).isEqualTo(preCollect + concurrentCollect);
        } finally {
            executor.shutdownNow();
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=ObservationAccumulatorTest`
Expected: compilation failure — `ObservationAccumulator` doesn't exist

- [ ] **Step 3: Implement ObservationAccumulator**

```java
package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.LevelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ObservationAccumulator<E> {

    private final ObservationRenderer<E> renderer;
    private final List<LevelEvent<E>> buffer = new ArrayList<>();
    private long lastDrainTimestamp;

    public ObservationAccumulator(ObservationRenderer<E> renderer) {
        this(renderer, System.currentTimeMillis());
    }

    ObservationAccumulator(ObservationRenderer<E> renderer, long createdAt) {
        this.renderer = renderer;
        this.lastDrainTimestamp = createdAt;
    }

    public synchronized void collect(LevelEvent<E> event) {
        buffer.add(event);
    }

    public CompletionStage<ObservationResult> drainObservation(long now) {
        List<LevelEvent<E>> snapshot;
        long timeSinceLastDrain;

        synchronized (this) {
            timeSinceLastDrain = now - lastDrainTimestamp;
            if (buffer.isEmpty()) {
                return CompletableFuture.completedFuture(
                        ObservationResult.empty(timeSinceLastDrain));
            }
            snapshot = List.copyOf(buffer);
            buffer.clear();
            lastDrainTimestamp = now;
        }

        return renderer.render(snapshot,
                new ObservationContext(now, timeSinceLastDrain));
    }

    public synchronized int eventCount() {
        return buffer.size();
    }

    public synchronized void clear() {
        buffer.clear();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=ObservationAccumulatorTest`
Expected: all 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/casehub/blocks/summarisation/observation/ObservationAccumulator.java src/test/java/io/casehub/blocks/summarisation/observation/ObservationAccumulatorTest.java
git commit -m "feat(#68): ObservationAccumulator — demand-driven drain with time tracking and thread safety"
```

---

### Task 4: Integration Example Test

**Files:**
- Test: `src/test/java/io/casehub/blocks/summarisation/observation/ObservationExampleTest.java`

**Interfaces:**
- Consumes: All types from Tasks 1-3; `EventStreamBus<E>`, `EventLevel`, `LevelEvent<E>` from `io.casehub.blocks.summarisation`

- [ ] **Step 1: Write end-to-end example test**

```java
package io.casehub.blocks.summarisation.observation;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationExampleTest {

    record GameEvent(String actor, String action, String category) {}

    static final EventLevel L1_EVENTS = new EventLevel("game-events", 1);

    EventStreamBus<GameEvent> eventBus;
    ObservationAccumulator<GameEvent> playerAccumulator;

    @BeforeEach
    void setUp() {
        eventBus = new EventStreamBus<>();

        Summariser<GameEvent, String> gameSummariser = batch -> {
            long actors = batch.stream()
                    .map(e -> e.payload().actor()).distinct().count();
            return CompletableFuture.completedFuture(List.of(
                    "While you were away, " + actors + " characters were active "
                    + "across " + batch.size() + " events."));
        };

        var renderer = new TieredObservationRenderer<>(
                e -> e.actor() + " " + e.action(),
                GameEvent::category,
                3, 8, gameSummariser);

        playerAccumulator = new ObservationAccumulator<>(renderer, 0);
        eventBus.subscribe(e -> true, playerAccumulator::collect);
    }

    @Test
    void verbatimTier_fewEvents_fullDetail() {
        publish("Dastardly", "entered the Kitchen", "movement", 1000);
        publish("Penelope", "asked about a brass key", "dialogue", 2000);

        var obs = playerAccumulator.drainObservation(3000)
                .toCompletableFuture().join();

        assertThat(obs.tier()).isEqualTo(ObservationTier.VERBATIM);
        assertThat(obs.eventCount()).isEqualTo(2);
        assertThat(obs.renderedText()).contains("Dastardly entered the Kitchen");
        assertThat(obs.renderedText()).contains("Penelope asked about a brass key");
        assertThat(obs.chunks()).hasSize(2);
        assertThat(obs.chunks().get(0).content())
                .isEqualTo("Dastardly entered the Kitchen");
    }

    @Test
    void groupedTier_moderateEvents_groupedByCategory() {
        publish("Dastardly", "entered the Kitchen", "movement", 1000);
        publish("AntHillMob", "passed through", "movement", 2000);
        publish("Penelope", "said hello", "dialogue", 3000);
        publish("Peter", "offered to help", "dialogue", 4000);
        publish("Dastardly", "tried the cabinet", "interaction", 5000);

        var obs = playerAccumulator.drainObservation(6000)
                .toCompletableFuture().join();

        assertThat(obs.tier()).isEqualTo(ObservationTier.GROUPED);
        assertThat(obs.eventCount()).isEqualTo(5);
        assertThat(obs.chunks()).extracting(c -> c.metadata().get("groupKey"))
                .containsExactly("movement", "dialogue", "interaction");
        assertThat(obs.chunks().get(0).eventCount()).isEqualTo(2);
        assertThat(obs.chunks().get(1).eventCount()).isEqualTo(2);
        assertThat(obs.chunks().get(2).eventCount()).isEqualTo(1);
    }

    @Test
    void summarisedTier_manyEvents_singleSummary() {
        for (int i = 0; i < 10; i++) {
            publish("Actor" + (i % 3), "did action " + i, "cat" + (i % 2), 1000 + i * 100);
        }

        var obs = playerAccumulator.drainObservation(3000)
                .toCompletableFuture().join();

        assertThat(obs.tier()).isEqualTo(ObservationTier.SUMMARISED);
        assertThat(obs.eventCount()).isEqualTo(10);
        assertThat(obs.chunks()).hasSize(1);
        assertThat(obs.chunks().get(0).content())
                .contains("3 characters were active")
                .contains("10 events");
    }

    @Test
    void multipleDrains_timeTracking() {
        publish("Dastardly", "entered", "movement", 100);
        var obs1 = playerAccumulator.drainObservation(500)
                .toCompletableFuture().join();
        assertThat(obs1.timeSinceLastDrain()).isEqualTo(500);

        publish("Penelope", "spoke", "dialogue", 700);
        var obs2 = playerAccumulator.drainObservation(1000)
                .toCompletableFuture().join();
        assertThat(obs2.timeSinceLastDrain()).isEqualTo(500);
    }

    @Test
    void visibilityFilter_agentSeesOnlyRelevantEvents() {
        var kitchenAccumulator = new ObservationAccumulator<>(
                new TieredObservationRenderer<>(
                        e -> e.actor() + " " + e.action(),
                        GameEvent::category, 5),
                0);
        eventBus.subscribe(
                e -> e.action().contains("Kitchen"),
                kitchenAccumulator::collect);

        publish("Dastardly", "entered the Kitchen", "movement", 1000);
        publish("AntHillMob", "went to the Ballroom", "movement", 2000);
        publish("Penelope", "left the Kitchen", "movement", 3000);

        var obs = kitchenAccumulator.drainObservation(4000)
                .toCompletableFuture().join();
        assertThat(obs.eventCount()).isEqualTo(2);
        assertThat(obs.renderedText()).contains("Kitchen");
        assertThat(obs.renderedText()).doesNotContain("Ballroom");
    }

    private void publish(String actor, String action, String category, long timestamp) {
        eventBus.publish(new LevelEvent<>(new GameEvent(actor, action, category), timestamp, L1_EVENTS, null));
    }
}
```

- [ ] **Step 2: Run example test**

Run: `mvn --batch-mode test -pl . -Dtest=ObservationExampleTest`
Expected: all 5 tests PASS

- [ ] **Step 3: Run full test suite**

Run: `mvn --batch-mode test`
Expected: all tests PASS (existing + new)

- [ ] **Step 4: Commit**

```bash
git add src/test/java/io/casehub/blocks/summarisation/observation/ObservationExampleTest.java
git commit -m "feat(#68): ObservationExampleTest — end-to-end integration with EventStreamBus"
```

---

### Task 5: Documentation

**Files:**
- Modify: `ARC42STORIES.MD` — add observation sub-package to §5
- Modify: `CLAUDE.md` — add observation package documentation

**Interfaces:**
- Consumes: none
- Produces: updated docs

- [ ] **Step 1: Update ARC42STORIES.MD §5**

Add under the existing `io.casehub.blocks.summarisation` section in §5:

```markdown
#### Sub-package: `io.casehub.blocks.summarisation.observation`

Terminal consumer of the summarisation pipeline — tiered, demand-driven rendering for LLM agent prompts with RAG-able chunk production.

| Type | Kind | Role |
|------|------|------|
| `ObservationTier` | record | Named tier in rendering hierarchy (mirrors EventLevel) |
| `ObservationContext` | record | Render-time context: currentTime, timeSinceLastDrain |
| `ObservationChunk` | record | RAG-able content unit with extensible metadata |
| `ObservationResult` | record | Drain output: renderedText + chunks. Factory: `empty(timeSinceLastDrain)` |
| `ObservationRenderer<E>` | @FunctionalInterface | SPI: render batch → ObservationResult (CompletionStage) |
| `TieredObservationRenderer<E>` | class | Standard tiered impl: verbatim/grouped/summarised by batch size |
| `ObservationAccumulator<E>` | class | Thread-safe buffer with demand-driven drain lifecycle |
```

- [ ] **Step 2: Update CLAUDE.md**

Add a new entry in the Key Directories table and a new package documentation section.

- [ ] **Step 3: Commit**

```bash
git add ARC42STORIES.MD CLAUDE.md
git commit -m "docs(#68): add observation sub-package to ARC42STORIES §5 and CLAUDE.md"
```

# Qhorus Channel Summarisation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #40 — feat: qhorus channel integration for summarisation — bidirectional bridge
**Issue group:** #40

**Goal:** Add keyed (group-and-emit-on-completion) accumulation to the
summarisation framework and two thin channel adapters bridging qhorus
MessageObserver/MessageDispatcher to EventStreamBus.

**Architecture:** Four new types split across two packages.
`KeyedAccumulator` and `KeyedSummarisationRunner` extend the
summarisation framework with grouped windowing (pure Java, no qhorus
dependency). `ChannelEventAdapter` and `ChannelEventPublisher` bridge
qhorus channel events to/from the summarisation pipeline (depend on
qhorus-api, already a compile dependency).

**Tech Stack:** Java 22, JUnit 5, AssertJ, Mockito, qhorus-api

## Global Constraints

- No new compile dependencies — all qhorus types already in scope
- Pure Java for summarisation package — no CDI, no Quarkus
- Thread-safe via synchronized (same pattern as EventAccumulator)
- All tests plain JUnit 5 + AssertJ (+ Mockito for channel tests)
- `keyExtractor` must return non-null — fail-fast NPE
- Staleness uses clock-from-last-event semantics
- Per-group at-most-once error semantics for KeyedSummarisationRunner

---

### Task 1: KeyedAccumulator

**Files:**
- Create: `src/main/java/io/casehub/blocks/summarisation/KeyedAccumulator.java`
- Test: `src/test/java/io/casehub/blocks/summarisation/KeyedAccumulatorTest.java`

**Interfaces:**
- Consumes: `LevelEvent<E>`, `EventLevel` (existing summarisation types)
- Produces: `KeyedAccumulator<K, E>` — used by `KeyedSummarisationRunner` (Task 2) via `collect()`, `drain(long)`, `clear()`, `groupCount()`, `eventCount()`

- [ ] **Step 1: Write failing tests for KeyedAccumulator**

Create test file with all test cases:

```java
package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyedAccumulatorTest {

    private static final EventLevel LEVEL = new EventLevel("test", 0);

    // --- Basic grouping ---

    @Test
    void collect_groupsEventsByKey() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s.substring(0, 1), group -> false, 0);
        acc.collect(new LevelEvent<>("a1", 1, LEVEL));
        acc.collect(new LevelEvent<>("a2", 2, LEVEL));
        acc.collect(new LevelEvent<>("b1", 3, LEVEL));
        assertThat(acc.groupCount()).isEqualTo(2);
        assertThat(acc.eventCount()).isEqualTo(3);
    }

    @Test
    void drain_completedGroup_returnsSingleGroup() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> group.size() >= 2,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        acc.collect(new LevelEvent<>("k1", 2, LEVEL));
        var groups = acc.drain(10);
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).hasSize(2);
        assertThat(groups.get(0).get(0).payload()).isEqualTo("k1");
    }

    @Test
    void drain_completedGroup_removesFromAccumulator() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> group.size() >= 2,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        acc.collect(new LevelEvent<>("k1", 2, LEVEL));
        acc.drain(10);
        assertThat(acc.groupCount()).isZero();
        assertThat(acc.eventCount()).isZero();
    }

    @Test
    void drain_incompleteGroup_retained() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> group.size() >= 3,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        acc.collect(new LevelEvent<>("k1", 2, LEVEL));
        var groups = acc.drain(10);
        assertThat(groups).isEmpty();
        assertThat(acc.groupCount()).isEqualTo(1);
    }

    // --- Staleness ---

    @Test
    void drain_staleGroup_emittedOnTimeout() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> false,
            100);
        acc.collect(new LevelEvent<>("k1", 10, LEVEL));
        assertThat(acc.drain(50)).as("not stale yet").isEmpty();
        var groups = acc.drain(111);
        assertThat(groups).as("stale after 100ms of inactivity").hasSize(1);
        assertThat(groups.get(0)).hasSize(1);
    }

    @Test
    void drain_staleTimeout_resetsOnNewEvent() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> false,
            100);
        acc.collect(new LevelEvent<>("k1", 10, LEVEL));
        assertThat(acc.drain(80)).as("not stale at t=80").isEmpty();
        acc.collect(new LevelEvent<>("k1", 90, LEVEL));
        assertThat(acc.drain(150)).as("timer reset at t=90, not stale at t=150").isEmpty();
        var groups = acc.drain(191);
        assertThat(groups).as("stale at t=191 (90+100+1)").hasSize(1);
        assertThat(groups.get(0)).hasSize(2);
    }

    @Test
    void drain_zeroStaleTimeout_neverForcesEmission() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> false,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        assertThat(acc.drain(999_999)).isEmpty();
        assertThat(acc.groupCount()).isEqualTo(1);
    }

    // --- Mixed completed + stale ---

    @Test
    void drain_completedAndStale_bothReturned() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s.substring(0, 2),
            group -> group.stream().anyMatch(e -> e.payload().endsWith("!")),
            100);
        acc.collect(new LevelEvent<>("k1-start", 10, LEVEL));
        acc.collect(new LevelEvent<>("k1!", 20, LEVEL));
        acc.collect(new LevelEvent<>("k2-start", 10, LEVEL));
        var groups = acc.drain(200);
        assertThat(groups).hasSize(2);
    }

    // --- Multi-key interleaving ---

    @Test
    void drain_multipleKeys_independentCompletion() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s.substring(0, 1),
            group -> group.size() >= 2,
            0);
        acc.collect(new LevelEvent<>("a1", 1, LEVEL));
        acc.collect(new LevelEvent<>("b1", 2, LEVEL));
        acc.collect(new LevelEvent<>("a2", 3, LEVEL));
        var groups = acc.drain(10);
        assertThat(groups).as("only group 'a' completed").hasSize(1);
        assertThat(groups.get(0)).extracting(e -> e.payload()).containsExactly("a1", "a2");
        assertThat(acc.groupCount()).as("group 'b' still active").isEqualTo(1);
    }

    // --- Edge cases ---

    @Test
    void drain_emptyAccumulator_returnsEmptyList() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s, group -> true, 0);
        assertThat(acc.drain(100)).isEmpty();
    }

    @Test
    void drain_singleEventGroup_completesImmediately() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> true,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        var groups = acc.drain(10);
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0)).hasSize(1);
    }

    @Test
    void clear_removesAllGroups() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s, group -> false, 0);
        acc.collect(new LevelEvent<>("a", 1, LEVEL));
        acc.collect(new LevelEvent<>("b", 2, LEVEL));
        acc.clear();
        assertThat(acc.groupCount()).isZero();
        assertThat(acc.eventCount()).isZero();
        assertThat(acc.drain(999)).isEmpty();
    }

    @Test
    void collect_nullKeyFromExtractor_throwsNPE() {
        var acc = new KeyedAccumulator<String, String>(
            s -> null, group -> false, 0);
        assertThatThrownBy(() -> acc.collect(new LevelEvent<>("a", 1, LEVEL)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void drain_completionCheckedPerDrain_notOnCollect() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s,
            group -> group.size() >= 2,
            0);
        acc.collect(new LevelEvent<>("k1", 1, LEVEL));
        acc.collect(new LevelEvent<>("k1", 2, LEVEL));
        assertThat(acc.groupCount()).as("group still present before drain").isEqualTo(1);
        acc.drain(10);
        assertThat(acc.groupCount()).as("removed after drain").isZero();
    }

    // --- Boundary ---

    @Test
    void drain_staleBoundary_exactlyAtTimeout() {
        var acc = new KeyedAccumulator<String, String>(
            s -> s, group -> false, 100);
        acc.collect(new LevelEvent<>("k1", 10, LEVEL));
        assertThat(acc.drain(109)).as("1ms below timeout").isEmpty();
        var groups = acc.drain(110);
        assertThat(groups).as("exactly at timeout").hasSize(1);
    }

    // --- Thread-safety ---

    @Test
    void concurrentCollectAndDrain_noDataLoss() throws Exception {
        final int eventCount = 500;
        var acc = new KeyedAccumulator<Integer, Integer>(
            i -> i % 10,
            group -> false,
            0);
        var latch = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            for (int i = 0; i < eventCount; i++) {
                final int val = i;
                executor.submit(() -> {
                    try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    acc.collect(new LevelEvent<>(val, val, LEVEL));
                });
            }
            latch.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(acc.eventCount()).isEqualTo(eventCount);
        } finally {
            executor.shutdownNow();
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=KeyedAccumulatorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation failure — `KeyedAccumulator` class does not exist

- [ ] **Step 3: Implement KeyedAccumulator**

Create `src/main/java/io/casehub/blocks/summarisation/KeyedAccumulator.java`:

```java
package io.casehub.blocks.summarisation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class KeyedAccumulator<K, E> {

    private final Function<E, K> keyExtractor;
    private final Predicate<List<LevelEvent<E>>> completionTest;
    private final long staleTimeout;
    private final Map<K, List<LevelEvent<E>>> groups = new LinkedHashMap<>();
    private final Map<K, Long> lastEventTime = new LinkedHashMap<>();

    public KeyedAccumulator(Function<E, K> keyExtractor,
                            Predicate<List<LevelEvent<E>>> completionTest,
                            long staleTimeout) {
        this.keyExtractor = keyExtractor;
        this.completionTest = completionTest;
        this.staleTimeout = staleTimeout;
    }

    public synchronized void collect(LevelEvent<E> event) {
        K key = Objects.requireNonNull(keyExtractor.apply(event.payload()),
            "keyExtractor returned null");
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        lastEventTime.put(key, event.timestamp());
    }

    public synchronized List<List<LevelEvent<E>>> drain(long now) {
        if (groups.isEmpty()) return List.of();
        List<List<LevelEvent<E>>> result = new ArrayList<>();
        var it = groups.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            K key = entry.getKey();
            List<LevelEvent<E>> group = entry.getValue();
            boolean completed = completionTest.test(group);
            boolean stale = staleTimeout > 0
                && (now - lastEventTime.get(key)) >= staleTimeout;
            if (completed || stale) {
                result.add(List.copyOf(group));
                it.remove();
                lastEventTime.remove(key);
            }
        }
        return result;
    }

    public synchronized void clear() {
        groups.clear();
        lastEventTime.clear();
    }

    public synchronized int groupCount() {
        return groups.size();
    }

    public synchronized int eventCount() {
        return groups.values().stream().mapToInt(List::size).sum();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=KeyedAccumulatorTest`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add src/main/java/io/casehub/blocks/summarisation/KeyedAccumulator.java src/test/java/io/casehub/blocks/summarisation/KeyedAccumulatorTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#40): KeyedAccumulator — grouped event accumulation with completion predicate and stale timeout"
```

---

### Task 2: KeyedSummarisationRunner

**Files:**
- Create: `src/main/java/io/casehub/blocks/summarisation/KeyedSummarisationRunner.java`
- Test: `src/test/java/io/casehub/blocks/summarisation/KeyedSummarisationRunnerTest.java`

**Interfaces:**
- Consumes: `KeyedAccumulator<K, E>` (Task 1), `Summariser<IN, OUT>`, `EventStreamBus<OUT>`, `EventLevel`, `LevelEvent<IN>` (existing)
- Produces: `KeyedSummarisationRunner<K, IN, OUT>` — used by integration test (Task 5) via `collect()`, `tick(long)`, `clear()`, `groupCount()`, `eventCount()`

- [ ] **Step 1: Write failing tests for KeyedSummarisationRunner**

Create test file:

```java
package io.casehub.blocks.summarisation;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyedSummarisationRunnerTest {

    private static final EventLevel INPUT_LEVEL = new EventLevel("input", 0);
    private static final EventLevel OUTPUT_LEVEL = new EventLevel("output", 1);

    @Test
    void tick_completedGroup_summariserCalledAndPublished() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s.substring(0, 1), group -> group.size() >= 2, 0,
            summariser, outputBus, OUTPUT_LEVEL);

        List<LevelEvent<Integer>> received = new ArrayList<>();
        outputBus.subscribe(i -> true, received::add);

        runner.collect(new LevelEvent<>("a1", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("a2", 2, INPUT_LEVEL));
        runner.tick(10);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).payload()).isEqualTo(2);
        assertThat(received.get(0).level()).isEqualTo(OUTPUT_LEVEL);
        assertThat(received.get(0).timestamp()).isEqualTo(10);
    }

    @Test
    void tick_noCompletedGroups_nothingPublished() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s, group -> group.size() >= 5, 0,
            summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("k1", 1, INPUT_LEVEL));
        runner.tick(10);

        assertThat(received).isEmpty();
    }

    @Test
    void tick_staleGroup_emittedAndSummarised() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s, group -> false, 100,
            summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("k1", 10, INPUT_LEVEL));
        runner.tick(50);
        assertThat(received).as("not stale yet").isEmpty();

        runner.tick(111);
        assertThat(received).as("stale group emitted").containsExactly(1);
    }

    @Test
    void tick_multipleCompletedGroups_eachSummarisedIndependently() {
        Summariser<String, String> summariser = Summariser.ofSync(
            batch -> List.of("group-" + batch.get(0).payload().substring(0, 1)
                + "-size-" + batch.size()));
        var outputBus = new EventStreamBus<String>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s.substring(0, 1), group -> group.size() >= 2, 0,
            summariser, outputBus, OUTPUT_LEVEL);

        List<String> received = new ArrayList<>();
        outputBus.subscribe(s -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("a1", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("b1", 2, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("a2", 3, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("b2", 4, INPUT_LEVEL));
        runner.tick(10);

        assertThat(received).containsExactlyInAnyOrder("group-a-size-2", "group-b-size-2");
    }

    @Test
    void tick_asyncFailure_propagatesThroughReturnedStage() {
        AtomicInteger callCount = new AtomicInteger();
        Summariser<String, Integer> failingSummariser = batch -> {
            callCount.incrementAndGet();
            return CompletableFuture.failedFuture(new RuntimeException("LLM timeout"));
        };
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s, group -> group.size() >= 1, 0,
            failingSummariser, outputBus, OUTPUT_LEVEL);

        runner.collect(new LevelEvent<>("k1", 1, INPUT_LEVEL));
        CompletionStage<Void> result = runner.tick(10);

        assertThatThrownBy(() -> result.toCompletableFuture().join())
            .hasCauseInstanceOf(RuntimeException.class)
            .hasMessageContaining("LLM timeout");
    }

    @Test
    void tick_oneGroupFails_otherGroupStillPublished() {
        AtomicInteger callCount = new AtomicInteger();
        Summariser<String, Integer> summariser = batch -> {
            int call = callCount.incrementAndGet();
            if (batch.get(0).payload().startsWith("fail")) {
                return CompletableFuture.failedFuture(new RuntimeException("boom"));
            }
            return CompletableFuture.completedFuture(List.of(batch.size()));
        };
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s.substring(0, 4), group -> group.size() >= 1, 0,
            summariser, outputBus, OUTPUT_LEVEL);

        List<Integer> received = new ArrayList<>();
        outputBus.subscribe(i -> true, e -> received.add(e.payload()));

        runner.collect(new LevelEvent<>("good", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("fail", 2, INPUT_LEVEL));
        runner.tick(10);

        assertThat(received).as("successful group published despite sibling failure").containsExactly(1);
    }

    @Test
    void tick_noGroups_returnsCompletedStage() {
        Summariser<String, Integer> summariser = Summariser.ofSync(batch -> List.of(batch.size()));
        var outputBus = new EventStreamBus<Integer>();
        var runner = new KeyedSummarisationRunner<>(
            s -> s, group -> true, 0,
            summariser, outputBus, OUTPUT_LEVEL);

        CompletionStage<Void> result = runner.tick(100);
        assertThat(result.toCompletableFuture().isDone()).isTrue();
        assertThat(result.toCompletableFuture().isCompletedExceptionally()).isFalse();
    }

    @Test
    void clear_delegatesToAccumulator() {
        var runner = new KeyedSummarisationRunner<>(
            s -> s, group -> false, 0,
            Summariser.ofSync(batch -> List.of()), new EventStreamBus<>(),
            OUTPUT_LEVEL);
        runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("b", 2, INPUT_LEVEL));
        runner.clear();
        assertThat(runner.groupCount()).isZero();
        assertThat(runner.eventCount()).isZero();
    }

    @Test
    void groupCountAndEventCount_delegateToAccumulator() {
        var runner = new KeyedSummarisationRunner<>(
            s -> s.substring(0, 1), group -> false, 0,
            Summariser.ofSync(batch -> List.of()), new EventStreamBus<>(),
            OUTPUT_LEVEL);
        runner.collect(new LevelEvent<>("a1", 1, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("a2", 2, INPUT_LEVEL));
        runner.collect(new LevelEvent<>("b1", 3, INPUT_LEVEL));
        assertThat(runner.groupCount()).isEqualTo(2);
        assertThat(runner.eventCount()).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=KeyedSummarisationRunnerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation failure — `KeyedSummarisationRunner` class does not exist

- [ ] **Step 3: Implement KeyedSummarisationRunner**

Create `src/main/java/io/casehub/blocks/summarisation/KeyedSummarisationRunner.java`:

```java
package io.casehub.blocks.summarisation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

public class KeyedSummarisationRunner<K, IN, OUT> {

    private final KeyedAccumulator<K, IN> accumulator;
    private final Summariser<IN, OUT> summariser;
    private final EventStreamBus<OUT> outputBus;
    private final EventLevel outputLevel;

    public KeyedSummarisationRunner(Function<IN, K> keyExtractor,
                                    Predicate<List<LevelEvent<IN>>> completionTest,
                                    long staleTimeout,
                                    Summariser<IN, OUT> summariser,
                                    EventStreamBus<OUT> outputBus,
                                    EventLevel outputLevel) {
        this.accumulator = new KeyedAccumulator<>(keyExtractor, completionTest, staleTimeout);
        this.summariser = summariser;
        this.outputBus = outputBus;
        this.outputLevel = outputLevel;
    }

    public void collect(LevelEvent<IN> event) {
        accumulator.collect(event);
    }

    public CompletionStage<Void> tick(long now) {
        var groups = accumulator.drain(now);
        if (groups.isEmpty())
            return CompletableFuture.completedFuture(null);
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = groups.stream()
            .map(group -> summariser.summarise(group).thenAccept(results -> {
                for (var payload : results) {
                    outputBus.publish(new LevelEvent<>(payload, now, outputLevel));
                }
            }).toCompletableFuture())
            .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public void clear() {
        accumulator.clear();
    }

    public int groupCount() {
        return accumulator.groupCount();
    }

    public int eventCount() {
        return accumulator.eventCount();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=KeyedSummarisationRunnerTest`
Expected: All tests PASS

- [ ] **Step 5: Run all summarisation tests to check for regressions**

Run: `mvn --batch-mode test -pl . -Dtest="io.casehub.blocks.summarisation.*Test"`
Expected: All PASS (existing tests unaffected)

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add src/main/java/io/casehub/blocks/summarisation/KeyedSummarisationRunner.java src/test/java/io/casehub/blocks/summarisation/KeyedSummarisationRunnerTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#40): KeyedSummarisationRunner — wires KeyedAccumulator to Summariser and output bus"
```

---

### Task 3: ChannelEventAdapter

**Files:**
- Create: `src/main/java/io/casehub/blocks/channel/ChannelEventAdapter.java`
- Test: `src/test/java/io/casehub/blocks/channel/ChannelEventAdapterTest.java`

**Interfaces:**
- Consumes: `MessageObserver`, `MessageReceivedEvent` (qhorus-api), `EventStreamBus<E>`, `EventLevel`, `LevelEvent<E>` (summarisation)
- Produces: `ChannelEventAdapter<E>` — implements `MessageObserver`, used by integration test (Task 5). Consumers register it as a CDI bean or pass to observer registration.

- [ ] **Step 1: Write failing tests for ChannelEventAdapter**

Create test file:

```java
package io.casehub.blocks.channel;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelEventAdapterTest {

    private static final EventLevel LEVEL = new EventLevel("channel", 1);
    private static final UUID CHANNEL_ID = UUID.randomUUID();

    private MessageReceivedEvent event(MessageType type, String content,
                                       String correlationId, String sender) {
        return new MessageReceivedEvent("test-channel", CHANNEL_ID, "tenant-1",
            type, sender, correlationId, Instant.ofEpochMilli(42_000), content, "general");
    }

    @Test
    void onMessage_extractorCalled_eventPublished() {
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(e -> e.content(), LEVEL, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.STATUS, "hello", null, "agent-1"));

        assertThat(received).hasSize(1);
        assertThat(received.get(0).payload()).isEqualTo("hello");
    }

    @Test
    void onMessage_timestampFromOccurredAt() {
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(e -> e.content(), LEVEL, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.STATUS, "hello", null, "agent-1"));

        assertThat(received.get(0).timestamp()).isEqualTo(42_000L);
    }

    @Test
    void onMessage_levelSetCorrectly() {
        var level = new EventLevel("classified", 2);
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(e -> e.content(), level, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.STATUS, "hello", null, "agent-1"));

        assertThat(received.get(0).level()).isEqualTo(level);
    }

    @Test
    void onMessage_extractorReturnsNull_noEventPublished() {
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(e -> null, LEVEL, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.STATUS, "hello", null, "agent-1"));

        assertThat(received).isEmpty();
    }

    @Test
    void onMessage_eventTypeWithNullContent_handledByExtractor() {
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(
            e -> e.messageType() == MessageType.EVENT ? null : e.content(),
            LEVEL, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.EVENT, null, null, "system"));

        assertThat(received).as("EVENT with null content filtered").isEmpty();
    }

    @Test
    void onMessage_extractorThrows_eventDroppedSilently() {
        var bus = new EventStreamBus<String>();
        var adapter = new ChannelEventAdapter<>(
            e -> { throw new RuntimeException("parse error"); },
            LEVEL, bus);

        List<LevelEvent<String>> received = new ArrayList<>();
        bus.subscribe(s -> true, received::add);

        adapter.onMessage(event(MessageType.STATUS, "hello", null, "agent-1"));

        assertThat(received).as("exception caught, event dropped").isEmpty();
    }

    @Test
    void channels_threeArgConstructor_returnsEmptySet() {
        var adapter = new ChannelEventAdapter<>(e -> e.content(), LEVEL,
            new EventStreamBus<>());
        assertThat(adapter.channels()).isEmpty();
    }

    @Test
    void channels_fourArgConstructor_returnsSpecifiedChannels() {
        var adapter = new ChannelEventAdapter<>(e -> e.content(), LEVEL,
            new EventStreamBus<>(), Set.of("channel-a", "channel-b"));
        assertThat(adapter.channels()).containsExactlyInAnyOrder("channel-a", "channel-b");
    }

    @Test
    void scope_returnsLocal() {
        var adapter = new ChannelEventAdapter<>(e -> e.content(), LEVEL,
            new EventStreamBus<>());
        assertThat(adapter.scope()).isEqualTo(MessageObserver.Scope.LOCAL);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=ChannelEventAdapterTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation failure — `ChannelEventAdapter` class does not exist

- [ ] **Step 3: Implement ChannelEventAdapter**

Create `src/main/java/io/casehub/blocks/channel/ChannelEventAdapter.java`:

```java
package io.casehub.blocks.channel;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;

import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

public class ChannelEventAdapter<E> implements MessageObserver {

    private static final Logger LOG = Logger.getLogger(ChannelEventAdapter.class.getName());

    private final Function<MessageReceivedEvent, E> extractor;
    private final EventLevel level;
    private final EventStreamBus<E> outputBus;
    private final Set<String> channelNames;

    public ChannelEventAdapter(Function<MessageReceivedEvent, E> extractor,
                               EventLevel level,
                               EventStreamBus<E> outputBus) {
        this(extractor, level, outputBus, Set.of());
    }

    public ChannelEventAdapter(Function<MessageReceivedEvent, E> extractor,
                               EventLevel level,
                               EventStreamBus<E> outputBus,
                               Set<String> channelNames) {
        this.extractor = extractor;
        this.level = level;
        this.outputBus = outputBus;
        this.channelNames = Set.copyOf(channelNames);
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {
        E extracted;
        try {
            extracted = extractor.apply(event);
        } catch (Exception e) {
            LOG.warning("ChannelEventAdapter: extractor failed on channel "
                + event.channelName() + " [" + event.messageType() + "]: "
                + e.getClass().getSimpleName() + " — " + e.getMessage());
            return;
        }
        if (extracted == null) return;
        outputBus.publish(new LevelEvent<>(extracted,
            event.occurredAt().toEpochMilli(), level));
    }

    @Override
    public Set<String> channels() {
        return channelNames;
    }

    @Override
    public Scope scope() {
        return Scope.LOCAL;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=ChannelEventAdapterTest`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add src/main/java/io/casehub/blocks/channel/ChannelEventAdapter.java src/test/java/io/casehub/blocks/channel/ChannelEventAdapterTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#40): ChannelEventAdapter — MessageObserver to EventStreamBus bridge (Direction 1)"
```

---

### Task 4: ChannelEventPublisher

**Files:**
- Create: `src/main/java/io/casehub/blocks/channel/ChannelEventPublisher.java`
- Test: `src/test/java/io/casehub/blocks/channel/ChannelEventPublisherTest.java`

**Interfaces:**
- Consumes: `EventStreamBus<E>`, `LevelEvent<E>` (summarisation), `MessageDispatcher`, `MessageDispatch` (qhorus-api)
- Produces: `ChannelEventPublisher<E>` — used by integration test (Task 5). Subscribes to bus on construction, dispatches via `MessageDispatcher`.

- [ ] **Step 1: Write failing tests for ChannelEventPublisher**

Create test file:

```java
package io.casehub.blocks.channel;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelEventPublisherTest {

    private static final EventLevel LEVEL = new EventLevel("test", 1);
    private static final UUID CHANNEL_ID = UUID.randomUUID();

    private MessageDispatch buildDispatch(String content) {
        return MessageDispatch.builder()
            .channelId(CHANNEL_ID)
            .sender("test-publisher")
            .type(MessageType.STATUS)
            .content(content)
            .actorType(ActorType.AGENT)
            .build();
    }

    @Test
    void subscribesOnConstruction_dispatchesOnPublish() {
        var bus = new EventStreamBus<String>();
        List<MessageDispatch> dispatched = new ArrayList<>();
        MessageDispatcher dispatcher = d -> {
            dispatched.add(d);
            return new DispatchResult(1L, d.channelId(), d.sender(), d.type(),
                d.correlationId(), d.inReplyTo(), d.artefactRefs(), d.target(),
                null, null, null, 0, List.of());
        };

        new ChannelEventPublisher<>(bus, dispatcher,
            event -> buildDispatch("content:" + event.payload()));

        bus.publish(new LevelEvent<>("hello", 1, LEVEL));

        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).content()).isEqualTo("content:hello");
    }

    @Test
    void messageBuilderCalledPerEvent() {
        var bus = new EventStreamBus<String>();
        List<MessageDispatch> dispatched = new ArrayList<>();
        MessageDispatcher dispatcher = d -> {
            dispatched.add(d);
            return new DispatchResult(1L, d.channelId(), d.sender(), d.type(),
                d.correlationId(), d.inReplyTo(), d.artefactRefs(), d.target(),
                null, null, null, 0, List.of());
        };

        new ChannelEventPublisher<>(bus, dispatcher,
            event -> buildDispatch(event.payload()));

        bus.publish(new LevelEvent<>("first", 1, LEVEL));
        bus.publish(new LevelEvent<>("second", 2, LEVEL));

        assertThat(dispatched).hasSize(2);
        assertThat(dispatched.get(0).content()).isEqualTo("first");
        assertThat(dispatched.get(1).content()).isEqualTo("second");
    }

    @Test
    void dispatchException_caughtAndLogged_neverPropagated() {
        var bus = new EventStreamBus<String>();
        MessageDispatcher dispatcher = d -> {
            throw new RuntimeException("connection refused");
        };

        new ChannelEventPublisher<>(bus, dispatcher,
            event -> buildDispatch(event.payload()));

        bus.publish(new LevelEvent<>("hello", 1, LEVEL));
        bus.publish(new LevelEvent<>("world", 2, LEVEL));
        // no exception propagated — bus continues to work
    }

    @Test
    void messageBuilderReturnsNull_dispatchSkipped() {
        var bus = new EventStreamBus<String>();
        List<MessageDispatch> dispatched = new ArrayList<>();
        MessageDispatcher dispatcher = d -> {
            dispatched.add(d);
            return new DispatchResult(1L, d.channelId(), d.sender(), d.type(),
                d.correlationId(), d.inReplyTo(), d.artefactRefs(), d.target(),
                null, null, null, 0, List.of());
        };

        new ChannelEventPublisher<>(bus, dispatcher,
            event -> event.payload().equals("skip") ? null : buildDispatch(event.payload()));

        bus.publish(new LevelEvent<>("keep", 1, LEVEL));
        bus.publish(new LevelEvent<>("skip", 2, LEVEL));
        bus.publish(new LevelEvent<>("keep2", 3, LEVEL));

        assertThat(dispatched).hasSize(2);
        assertThat(dispatched.get(0).content()).isEqualTo("keep");
        assertThat(dispatched.get(1).content()).isEqualTo("keep2");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=ChannelEventPublisherTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: Compilation failure — `ChannelEventPublisher` class does not exist

- [ ] **Step 3: Implement ChannelEventPublisher**

Create `src/main/java/io/casehub/blocks/channel/ChannelEventPublisher.java`:

```java
package io.casehub.blocks.channel;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;

import java.util.function.Function;
import java.util.logging.Logger;

public class ChannelEventPublisher<E> {

    private static final Logger LOG = Logger.getLogger(ChannelEventPublisher.class.getName());

    public ChannelEventPublisher(EventStreamBus<E> inputBus,
                                 MessageDispatcher dispatcher,
                                 Function<LevelEvent<E>, MessageDispatch> messageBuilder) {
        inputBus.subscribe(e -> true, event -> {
            try {
                MessageDispatch dispatch = messageBuilder.apply(event);
                if (dispatch == null) return;
                dispatcher.dispatch(dispatch);
            } catch (Exception e1) {
                LOG.warning("ChannelEventPublisher: dispatch failed: "
                    + e1.getClass().getSimpleName() + " — " + e1.getMessage());
            }
        });
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=ChannelEventPublisherTest`
Expected: All tests PASS

- [ ] **Step 5: Run all channel tests to check for regressions**

Run: `mvn --batch-mode test -pl . -Dtest="io.casehub.blocks.channel.*Test"`
Expected: All PASS

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add src/main/java/io/casehub/blocks/channel/ChannelEventPublisher.java src/test/java/io/casehub/blocks/channel/ChannelEventPublisherTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#40): ChannelEventPublisher — EventStreamBus to MessageDispatcher bridge (Direction 2)"
```

---

### Task 5: Integration example test

**Files:**
- Create: `src/test/java/io/casehub/blocks/channel/examples/summarisation/ChannelSummarisationPipelineTest.java`

**Interfaces:**
- Consumes: `ChannelEventAdapter<E>` (Task 3), `ChannelEventPublisher<E>` (Task 4), `KeyedSummarisationRunner<K,IN,OUT>` (Task 2), `SummarisationRunner<IN,OUT>`, `EventStreamBus<E>`, `WindowPolicy`, `Summariser`, `EventLevel`, `LevelEvent` (existing)
- Produces: end-to-end validation — no downstream consumers

- [ ] **Step 1: Write the integration example test**

Create `src/test/java/io/casehub/blocks/channel/examples/summarisation/ChannelSummarisationPipelineTest.java`:

```java
package io.casehub.blocks.channel.examples.summarisation;

import io.casehub.blocks.channel.ChannelEventAdapter;
import io.casehub.blocks.channel.ChannelEventPublisher;
import io.casehub.blocks.summarisation.*;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelSummarisationPipelineTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();
    private static final EventLevel L1 = new EventLevel("channel-events", 1);
    private static final EventLevel L2 = new EventLevel("episodes", 2);
    private static final EventLevel L3 = new EventLevel("phases", 3);

    record ChannelEvent(String correlationId, MessageType messageType,
                        String content, String sender) {}

    record Episode(String correlationId, int messageCount, String summary) {}

    record Phase(String name, int episodeCount) {}

    private MessageReceivedEvent channelMessage(MessageType type, String content,
                                                 String correlationId, String sender,
                                                 long epochMillis) {
        return new MessageReceivedEvent("test-channel", CHANNEL_ID, "tenant-1",
            type, sender, correlationId, Instant.ofEpochMilli(epochMillis),
            content, "general");
    }

    @Test
    void fullPipeline_correlationChainCompletionTriggersEpisode() {
        // --- Buses ---
        var eventBus = new EventStreamBus<ChannelEvent>();
        var episodeBus = new EventStreamBus<Episode>();

        // --- Direction 1: channel → L1 events ---
        var adapter = new ChannelEventAdapter<>(
            event -> {
                if (event.correlationId() == null) return null;
                if (event.messageType() == MessageType.EVENT) return null;
                return new ChannelEvent(event.correlationId(), event.messageType(),
                    event.content(), event.senderId());
            }, L1, eventBus);

        // --- L1 → L2: keyed runner groups by correlationId ---
        Summariser<ChannelEvent, Episode> episodeSummariser = Summariser.ofSync(batch -> {
            var first = batch.get(0).payload();
            return List.of(new Episode(first.correlationId(), batch.size(),
                "chain of " + batch.size() + " messages"));
        });
        var keyedRunner = new KeyedSummarisationRunner<>(
            (ChannelEvent e) -> e.correlationId(),
            group -> group.stream().anyMatch(e -> e.payload().messageType().isTerminal()),
            0, episodeSummariser, episodeBus, L2);

        eventBus.subscribe(e -> true, e -> keyedRunner.collect(e));

        // --- Capture episodes ---
        List<LevelEvent<Episode>> episodes = new ArrayList<>();
        episodeBus.subscribe(e -> true, episodes::add);

        // --- Simulate a COMMAND → STATUS → DONE chain ---
        adapter.onMessage(channelMessage(MessageType.COMMAND, "do task",
            "corr-1", "user-1", 1000));
        adapter.onMessage(channelMessage(MessageType.STATUS, "working",
            "corr-1", "agent-1", 2000));
        adapter.onMessage(channelMessage(MessageType.DONE, "finished",
            "corr-1", "agent-1", 3000));

        keyedRunner.tick(3000);

        assertThat(episodes).hasSize(1);
        assertThat(episodes.get(0).payload().correlationId()).isEqualTo("corr-1");
        assertThat(episodes.get(0).payload().messageCount()).isEqualTo(3);
        assertThat(episodes.get(0).level()).isEqualTo(L2);
    }

    @Test
    void staleChain_forceEmittedOnTimeout() {
        var eventBus = new EventStreamBus<ChannelEvent>();
        var episodeBus = new EventStreamBus<Episode>();

        var adapter = new ChannelEventAdapter<>(
            event -> {
                if (event.correlationId() == null) return null;
                return new ChannelEvent(event.correlationId(), event.messageType(),
                    event.content(), event.senderId());
            }, L1, eventBus);

        Summariser<ChannelEvent, Episode> episodeSummariser = Summariser.ofSync(batch -> {
            var first = batch.get(0).payload();
            return List.of(new Episode(first.correlationId(), batch.size(), "stale"));
        });
        var keyedRunner = new KeyedSummarisationRunner<>(
            (ChannelEvent e) -> e.correlationId(),
            group -> group.stream().anyMatch(e -> e.payload().messageType().isTerminal()),
            5000, episodeSummariser, episodeBus, L2);

        eventBus.subscribe(e -> true, e -> keyedRunner.collect(e));

        List<LevelEvent<Episode>> episodes = new ArrayList<>();
        episodeBus.subscribe(e -> true, episodes::add);

        adapter.onMessage(channelMessage(MessageType.COMMAND, "do task",
            "corr-stale", "user-1", 1000));
        adapter.onMessage(channelMessage(MessageType.STATUS, "working",
            "corr-stale", "agent-1", 2000));

        keyedRunner.tick(5000);
        assertThat(episodes).as("not stale yet at t=5000").isEmpty();

        keyedRunner.tick(7001);
        assertThat(episodes).as("stale after 5s of inactivity").hasSize(1);
        assertThat(episodes.get(0).payload().messageCount()).isEqualTo(2);
    }

    @Test
    void phaseWindowing_composesWithKeyedRunner() {
        var eventBus = new EventStreamBus<ChannelEvent>();
        var episodeBus = new EventStreamBus<Episode>();
        var phaseBus = new EventStreamBus<Phase>();

        var adapter = new ChannelEventAdapter<>(
            event -> {
                if (event.correlationId() == null) return null;
                if (event.messageType() == MessageType.EVENT) return null;
                return new ChannelEvent(event.correlationId(), event.messageType(),
                    event.content(), event.senderId());
            }, L1, eventBus);

        Summariser<ChannelEvent, Episode> episodeSummariser = Summariser.ofSync(batch ->
            List.of(new Episode(batch.get(0).payload().correlationId(),
                batch.size(), "episode")));

        var keyedRunner = new KeyedSummarisationRunner<>(
            (ChannelEvent e) -> e.correlationId(),
            group -> group.stream().anyMatch(e -> e.payload().messageType().isTerminal()),
            0, episodeSummariser, episodeBus, L2);

        Summariser<Episode, Phase> phaseSummariser = Summariser.ofSync(batch ->
            List.of(new Phase("active", batch.size())));

        var phaseRunner = new SummarisationRunner<>(
            new WindowPolicy(0, 2), phaseSummariser, phaseBus, L3);

        eventBus.subscribe(e -> true, e -> keyedRunner.collect(e));
        episodeBus.subscribe(e -> true, e -> phaseRunner.collect(e));

        List<LevelEvent<Phase>> phases = new ArrayList<>();
        phaseBus.subscribe(p -> true, phases::add);

        // Two complete chains → 2 episodes → triggers phase window
        adapter.onMessage(channelMessage(MessageType.COMMAND, "task1", "c1", "u", 1000));
        adapter.onMessage(channelMessage(MessageType.DONE, "done1", "c1", "a", 2000));
        keyedRunner.tick(2000);

        adapter.onMessage(channelMessage(MessageType.COMMAND, "task2", "c2", "u", 3000));
        adapter.onMessage(channelMessage(MessageType.DONE, "done2", "c2", "a", 4000));
        keyedRunner.tick(4000);

        phaseRunner.tick(4000);

        assertThat(phases).hasSize(1);
        assertThat(phases.get(0).payload().episodeCount()).isEqualTo(2);
        assertThat(phases.get(0).level()).isEqualTo(L3);
    }

    @Test
    void publisher_capturesOutputDispatches() {
        var episodeBus = new EventStreamBus<Episode>();
        List<MessageDispatch> dispatched = new ArrayList<>();
        MessageDispatcher dispatcher = d -> {
            dispatched.add(d);
            return new DispatchResult(1L, d.channelId(), d.sender(), d.type(),
                d.correlationId(), d.inReplyTo(), d.artefactRefs(), d.target(),
                null, null, null, 0, List.of());
        };

        new ChannelEventPublisher<>(episodeBus, dispatcher,
            event -> MessageDispatch.builder()
                .channelId(CHANNEL_ID)
                .sender("summarisation.episodes")
                .actorType(ActorType.AGENT)
                .type(MessageType.STATUS)
                .content("Episode: " + event.payload().summary())
                .build());

        episodeBus.publish(new LevelEvent<>(
            new Episode("c1", 3, "chain of 3"), 1000, L2));

        assertThat(dispatched).hasSize(1);
        assertThat(dispatched.get(0).content()).isEqualTo("Episode: chain of 3");
        assertThat(dispatched.get(0).sender()).isEqualTo("summarisation.episodes");
    }

    @Test
    void senderFiltering_preventsFeedbackLoop() {
        var eventBus = new EventStreamBus<ChannelEvent>();

        var adapter = new ChannelEventAdapter<>(
            event -> {
                if (event.senderId().startsWith("summarisation.")) return null;
                if (event.correlationId() == null) return null;
                return new ChannelEvent(event.correlationId(), event.messageType(),
                    event.content(), event.senderId());
            }, L1, eventBus);

        List<LevelEvent<ChannelEvent>> received = new ArrayList<>();
        eventBus.subscribe(e -> true, received::add);

        adapter.onMessage(channelMessage(MessageType.STATUS, "real message",
            "c1", "user-1", 1000));
        adapter.onMessage(channelMessage(MessageType.STATUS, "published summary",
            "c1", "summarisation.episodes", 2000));

        assertThat(received).hasSize(1);
        assertThat(received.get(0).payload().sender()).isEqualTo("user-1");
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `mvn --batch-mode test -pl . -Dtest=ChannelSummarisationPipelineTest`
Expected: All tests PASS (all dependencies from Tasks 1-4 are in place)

- [ ] **Step 3: Run the full test suite**

Run: `mvn --batch-mode test`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add src/test/java/io/casehub/blocks/channel/examples/summarisation/ChannelSummarisationPipelineTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "test(#40): integration example — full channel-to-summarisation pipeline with correlation grouping"
```

---

### Task 6: Documentation

**Files:**
- Modify: `CLAUDE.md` — add ChannelEventAdapter, ChannelEventPublisher to channel package table; add KeyedAccumulator, KeyedSummarisationRunner to summarisation package table
- Modify: `ARC42STORIES.MD` — update §5 summarisation and channel entries

**Interfaces:**
- Consumes: nothing
- Produces: nothing — docs only

- [ ] **Step 1: Update CLAUDE.md channel package table**

Add `ChannelEventAdapter` and `ChannelEventPublisher` to the `## Package: io.casehub.blocks.channel` table.

- [ ] **Step 2: Update CLAUDE.md summarisation package table**

Add `KeyedAccumulator` and `KeyedSummarisationRunner` to the `## Package: io.casehub.blocks.summarisation` table.

- [ ] **Step 3: Update ARC42STORIES.MD §5**

Add entries for the new types in the building block view.

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add CLAUDE.md ARC42STORIES.MD
git -C /Users/mdproctor/claude/casehub/blocks commit -m "docs(#40): update CLAUDE.md and ARC42STORIES.MD with channel-summarisation bridge types"
```

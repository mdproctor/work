# Layered Event Summarisation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #27 — feat: layered event summarisation — temporal abstraction framework from quarkmind
**Issue group:** #27, #38

**Goal:** Extract the temporal abstraction framework from quarkmind to blocks with two API improvements, then build clinical and logistics domain examples proving portability.

**Architecture:** The framework is 7 pure-Java source files (records, classes, one interface) under `io.casehub.blocks.summarisation`. Two API improvements are made in quarkmind before extraction: remove dead `EventConsumer` interface, change `tick()` to return `CompletionStage<Void>`. After extraction, quarkmind depends on blocks and deletes its local copies. Two domain examples (clinical, logistics) validate the framework in blocks.

**Tech Stack:** Java 21, JUnit 5, AssertJ, CompletionStage (JDK)

## Global Constraints

- No new compile dependencies in blocks — framework is pure Java
- All quarkmind changes go on quarkmind main (approved for consolidation work per CLAUDE.md)
- All blocks changes go on branch `issue-27-layered-event-summarisation`
- Every commit references an issue
- TDD: failing test before implementation
- `mvn --batch-mode test` to validate after each change
- **IntelliJ MCP for all structural operations.** Use `ide_refactor_safe_delete` for deletions, `ide_move_file` for cross-repo moves, `ide_refactor_rename` for renames. Never use bash `rm`, `cp`, `mv`, or `mkdir` for source files. Both quarkmind (`project_path=/Users/mdproctor/claude/casehub/quarkmind`) and blocks (`project_path=/Users/mdproctor/claude/casehub/blocks`) are open in IntelliJ.
- **Edit tool for content changes.** Use the Edit tool (not bash sed/awk) for modifying file contents — adding `synchronized`, changing return types, updating Javadoc.

---

## File Map

### Phase 1 — Quarkmind improvements (on quarkmind main)

| Action | File | What changes |
|--------|------|-------------|
| Delete | `src/main/java/io/casehub/blocks/summarisation/EventConsumer.java` | Dead interface — no framework references |
| Modify | `src/main/java/io/quarkmind/plugin/summarisation/MomentConsumer.java:8` | Remove `extends EventConsumer<GameMoment>`, remove import |
| Modify | `src/main/java/io/quarkmind/plugin/summarisation/MomentBroker.java:54` | Remove `@Inject @Any Instance<MomentConsumer> consumers` field |
| Modify | `src/main/java/io/casehub/blocks/summarisation/EventAccumulator.java` | Add `synchronized` to all public methods |
| Modify | `src/main/java/io/casehub/blocks/summarisation/WindowPolicy.java` | Add compact constructor with validation |
| Modify | `src/main/java/io/casehub/blocks/summarisation/EventStreamBus.java:8-12` | Update class Javadoc (concurrent publish+subscribe IS safe) |
| Modify | `src/main/java/io/casehub/blocks/summarisation/SummarisationRunner.java:24-33` | `tick()` returns `CompletionStage<Void>` |
| Modify | `src/test/java/io/casehub/blocks/summarisation/SummarisationRunnerTest.java` | Add async error observability test |

### Phase 2 — Extract to blocks (on issue-27 branch)

| Action | File |
|--------|------|
| Create | `src/main/java/io/casehub/blocks/summarisation/EventLevel.java` |
| Create | `src/main/java/io/casehub/blocks/summarisation/LevelEvent.java` |
| Create | `src/main/java/io/casehub/blocks/summarisation/WindowPolicy.java` |
| Create | `src/main/java/io/casehub/blocks/summarisation/EventAccumulator.java` |
| Create | `src/main/java/io/casehub/blocks/summarisation/EventStreamBus.java` |
| Create | `src/main/java/io/casehub/blocks/summarisation/Summariser.java` |
| Create | `src/main/java/io/casehub/blocks/summarisation/SummarisationRunner.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/EventAccumulatorTest.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/EventStreamBusTest.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/SummarisationRunnerTest.java` |

### Phase 3 — Wire quarkmind (on quarkmind main)

| Action | File |
|--------|------|
| Modify | `pom.xml` | Add `casehub-blocks` compile dependency |
| Delete | `src/main/java/io/casehub/blocks/summarisation/EventLevel.java` |
| Delete | `src/main/java/io/casehub/blocks/summarisation/LevelEvent.java` |
| Delete | `src/main/java/io/casehub/blocks/summarisation/WindowPolicy.java` |
| Delete | `src/main/java/io/casehub/blocks/summarisation/EventAccumulator.java` |
| Delete | `src/main/java/io/casehub/blocks/summarisation/EventStreamBus.java` |
| Delete | `src/main/java/io/casehub/blocks/summarisation/Summariser.java` |
| Delete | `src/main/java/io/casehub/blocks/summarisation/SummarisationRunner.java` |
| Delete | `src/test/java/io/casehub/blocks/summarisation/EventAccumulatorTest.java` |
| Delete | `src/test/java/io/casehub/blocks/summarisation/EventStreamBusTest.java` |
| Delete | `src/test/java/io/casehub/blocks/summarisation/SummarisationRunnerTest.java` |

### Phase 4 — Domain examples (on blocks issue-27 branch)

| Action | File |
|--------|------|
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/clinical/VitalReading.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/clinical/VitalType.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/clinical/ClinicalEvent.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/clinical/ClinicalCategory.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/clinical/Severity.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/clinical/CarePhase.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/clinical/ClinicalNarrative.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/clinical/ClinicalEventSummariser.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/clinical/CarePhaseSummariser.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/clinical/ClinicalPipelineTest.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/logistics/PackageScan.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/logistics/ScanType.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/logistics/PackageAnomaly.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/logistics/AnomalyType.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/logistics/HubPhase.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/logistics/HubNarrative.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/logistics/AnomalyDetectorSummariser.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/logistics/HubPhaseSummariser.java` |
| Create | `src/test/java/io/casehub/blocks/summarisation/examples/logistics/LogisticsPipelineTest.java` |

---

### Task 1: Remove EventConsumer and clean up MomentBroker dead code (quarkmind)

**Repo:** `/Users/mdproctor/claude/casehub/quarkmind` (on main)

**Files:**
- Delete: `src/main/java/io/casehub/blocks/summarisation/EventConsumer.java`
- Modify: `src/main/java/io/quarkmind/plugin/summarisation/MomentConsumer.java`
- Modify: `src/main/java/io/quarkmind/plugin/summarisation/MomentBroker.java`

**Interfaces:**
- Consumes: nothing
- Produces: `MomentConsumer` without `extends EventConsumer<GameMoment>` (still has `eventFilter()` default method). `MomentBroker` without dead `consumers` field.

- [ ] **Step 1: Verify current test suite passes**

Run: `mvn --batch-mode test` in quarkmind
Expected: BUILD SUCCESS

- [ ] **Step 2: Safe-delete EventConsumer.java via IntelliJ**

Use `ide_refactor_safe_delete` on `EventConsumer` (file: `src/main/java/io/casehub/blocks/summarisation/EventConsumer.java`, line 5, column 18, project_path: quarkmind). If usages are reported, review them — `MomentConsumer` extends it (handled in step 3) and that should be the only reference. Use `force: true` after confirming the usage list matches expectations.

- [ ] **Step 3: Update MomentConsumer — remove extends clause and import**

```java
package io.quarkmind.plugin.summarisation;

import java.util.Set;
import java.util.function.Predicate;

public interface MomentConsumer {

    Set<GameMomentType> subscribedMomentTypes();

    default Predicate<GameMoment> eventFilter() {
        return m -> subscribedMomentTypes().contains(m.type());
    }
}
```

- [ ] **Step 4: Remove dead consumers field from MomentBroker**

Remove line 54: `@Inject @Any Instance<MomentConsumer> consumers;`
Remove the `jakarta.enterprise.inject.Instance` import if now unused.
Remove the `jakarta.enterprise.inject.Any` import if now unused.

- [ ] **Step 5: Run tests**

Run: `mvn --batch-mode test` in quarkmind
Expected: BUILD SUCCESS — `DroolsStrategyTask` calls `eventFilter()` on `this` (implements `MomentConsumer`), method signature unchanged.

- [ ] **Step 6: Commit**

```
feat(#27): remove dead EventConsumer interface and MomentBroker.consumers

EventConsumer had no framework references — EventStreamBus.subscribe()
takes Predicate<E> directly. MomentBroker.consumers was injected but
never iterated. MomentConsumer retains its eventFilter() default method.

Refs casehubio/blocks#27
```

---

### Task 2: Harden framework types — thread-safety, validation, Javadoc (quarkmind)

**Repo:** `/Users/mdproctor/claude/casehub/quarkmind` (on main)

**Files:**
- Modify: `src/main/java/io/casehub/blocks/summarisation/WindowPolicy.java`
- Modify: `src/main/java/io/casehub/blocks/summarisation/EventAccumulator.java`
- Modify: `src/main/java/io/casehub/blocks/summarisation/EventStreamBus.java`

**Interfaces:**
- Consumes: nothing
- Produces: `WindowPolicy` with compact constructor validation. `EventAccumulator` with synchronized public methods. `EventStreamBus` with corrected Javadoc.

- [ ] **Step 1: Add WindowPolicy compact constructor validation**

```java
package io.casehub.blocks.summarisation;

public record WindowPolicy(long maxAge, int maxCount) {
    public WindowPolicy {
        if (maxAge < 0) throw new IllegalArgumentException("maxAge must be >= 0, was: " + maxAge);
        if (maxCount < 0) throw new IllegalArgumentException("maxCount must be >= 0, was: " + maxCount);
        if (maxAge == 0 && maxCount == 0) throw new IllegalArgumentException("At least one of maxAge or maxCount must be positive");
    }
}
```

- [ ] **Step 2: Make EventAccumulator thread-safe**

Add `synchronized` to all 5 public methods: `collect`, `shouldEmit`, `drain`, `clear`, `size`. Update the class Javadoc:

```java
package io.casehub.blocks.summarisation;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe event buffer. All public methods are synchronized — safe for
 * concurrent collect() (from async bus callbacks) and tick()-driven
 * shouldEmit()/drain() on the main thread.
 */
public class EventAccumulator<E> {

    private final WindowPolicy policy;
    private final List<LevelEvent<E>> buffer = new ArrayList<>();

    public EventAccumulator(WindowPolicy policy) {
        this.policy = policy;
    }

    public synchronized void collect(LevelEvent<E> event) {
        buffer.add(event);
    }

    public synchronized boolean shouldEmit(long now) {
        if (buffer.isEmpty()) return false;
        if (policy.maxCount() > 0 && buffer.size() >= policy.maxCount()) return true;
        if (policy.maxAge() > 0) {
            long oldest = buffer.get(0).timestamp();
            return (now - oldest) >= policy.maxAge();
        }
        return false;
    }

    public synchronized List<LevelEvent<E>> drain() {
        var result = List.copyOf(buffer);
        buffer.clear();
        return result;
    }

    public synchronized void clear() {
        buffer.clear();
    }

    public synchronized int size() {
        return buffer.size();
    }
}
```

- [ ] **Step 3: Update EventStreamBus Javadoc**

Replace the class Javadoc. The current text incorrectly claims concurrent publish+subscribe is unsafe — `CopyOnWriteArrayList` makes both safe:

```java
/**
 * Predicate-based pub/sub for typed events. Backed by {@link CopyOnWriteArrayList} —
 * concurrent {@code publish()} and {@code subscribe()} calls are safe. Thread-safety
 * of the overall pipeline depends on subscriber callbacks: when using
 * {@link SummarisationRunner}, the downstream {@link EventAccumulator}'s synchronized
 * methods ensure safe cross-thread access.
 */
```

- [ ] **Step 4: Run tests**

Run: `mvn --batch-mode test` in quarkmind
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```
feat(#27): harden framework types — thread-safety, validation, Javadoc

WindowPolicy compact constructor rejects negative values and both-zero.
EventAccumulator synchronized on all public methods for cross-thread
safety when async bus callbacks call collect() on background threads.
EventStreamBus Javadoc corrected — CopyOnWriteArrayList makes concurrent
publish+subscribe safe.

Refs casehubio/blocks#27
```

---

### Task 3: Change tick() return type and add async error test (quarkmind)

**Repo:** `/Users/mdproctor/claude/casehub/quarkmind` (on main)

**Files:**
- Modify: `src/main/java/io/casehub/blocks/summarisation/SummarisationRunner.java`
- Modify: `src/test/java/io/casehub/blocks/summarisation/SummarisationRunnerTest.java`

**Interfaces:**
- Consumes: `EventAccumulator` (synchronized, from Task 2), `Summariser`, `EventStreamBus`
- Produces: `SummarisationRunner.tick()` returning `CompletionStage<Void>`

- [ ] **Step 1: Write failing async error test**

Add to `SummarisationRunnerTest.java`:

```java
@Test
void tick_asyncFailure_propagatesThroughReturnedStage() {
    Summariser<String, Integer> failingSummariser = batch ->
        CompletableFuture.failedFuture(new RuntimeException("LLM timeout"));
    var outputBus = new EventStreamBus<Integer>();
    var runner = new SummarisationRunner<>(new WindowPolicy(0, 1), failingSummariser, outputBus, OUTPUT_LEVEL);

    List<Integer> received = new ArrayList<>();
    outputBus.subscribe(i -> true, e -> received.add(e.payload()));

    runner.collect(new LevelEvent<>("a", 1, INPUT_LEVEL, null));
    CompletionStage<Void> result = runner.tick(5);

    assertThat(result).isNotNull();
    assertThatThrownBy(() -> result.toCompletableFuture().join())
        .hasCauseInstanceOf(RuntimeException.class)
        .hasMessageContaining("LLM timeout");
    assertThat(received).as("nothing published on failure").isEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=SummarisationRunnerTest#tick_asyncFailure_propagatesThroughReturnedStage` in quarkmind
Expected: FAIL — `tick()` currently returns void, `CompletionStage<Void> result = runner.tick(5)` won't compile.

- [ ] **Step 3: Implement tick() return type change**

Replace `SummarisationRunner.java`:

```java
package io.casehub.blocks.summarisation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class SummarisationRunner<IN, OUT> {

    private final EventAccumulator<IN> accumulator;
    private final Summariser<IN, OUT> summariser;
    private final EventStreamBus<OUT> outputBus;
    private final EventLevel outputLevel;

    public SummarisationRunner(WindowPolicy policy,
                               Summariser<IN, OUT> summariser,
                               EventStreamBus<OUT> outputBus,
                               EventLevel outputLevel) {
        this.accumulator = new EventAccumulator<>(policy);
        this.summariser = summariser;
        this.outputBus = outputBus;
        this.outputLevel = outputLevel;
    }

    public void collect(LevelEvent<IN> event) {
        accumulator.collect(event);
    }

    public CompletionStage<Void> tick(long now) {
        if (!accumulator.shouldEmit(now))
            return CompletableFuture.completedFuture(null);
        var batch = accumulator.drain();
        return summariser.summarise(batch).thenAccept(results -> {
            for (var payload : results) {
                outputBus.publish(new LevelEvent<>(payload, now, outputLevel, null));
            }
        });
    }

    public void clear() {
        accumulator.clear();
    }

    public int size() {
        return accumulator.size();
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `mvn --batch-mode test` in quarkmind
Expected: BUILD SUCCESS — `SummarisationLifecycle.tick()` ignores the return value (sync summarisers, already complete). `SummarisationTickable.tick()` returns void — no signature conflict since `SummarisationLifecycle.tick()` calls `phaseRunner.tick()` and `arcRunner.tick()` as statements, discarding the returned `CompletionStage`.

- [ ] **Step 5: Commit**

```
feat(#27): tick() returns CompletionStage<Void> for async error observability

Enables callers to observe async summariser failures instead of silently
swallowing them. Sync callers unchanged — returned stage is already
complete. Adds async error propagation test.

Refs casehubio/blocks#27
```

---

### Task 4: Extract framework to blocks

**Repo:** `/Users/mdproctor/claude/casehub/blocks` (on `issue-27-layered-event-summarisation`)

**Files:**
- Create: 7 source files + 3 test files under `io.casehub.blocks.summarisation`

**Interfaces:**
- Consumes: improved quarkmind source (Tasks 1-3)
- Produces: complete `io.casehub.blocks.summarisation` package in blocks

- [ ] **Step 1: Install blocks to local Maven repo**

Run: `mvn --batch-mode install -DskipTests` in blocks
Expected: BUILD SUCCESS — ensures blocks artifact is available for quarkmind in Task 5.

- [ ] **Step 2: Move 7 source files from quarkmind to blocks via IntelliJ**

Use `ide_move_file` for each source file. The package name is already `io.casehub.blocks.summarisation` — no import changes needed. IntelliJ handles directory creation automatically.

For each of the 7 source files, call `ide_move_file` with:
- `project_path`: `/Users/mdproctor/claude/casehub/quarkmind`
- `file`: `src/main/java/io/casehub/blocks/summarisation/<filename>.java`
- `destination`: `/Users/mdproctor/claude/casehub/blocks/src/main/java/io/casehub/blocks/summarisation`

Files to move: `EventLevel.java`, `LevelEvent.java`, `WindowPolicy.java`, `EventAccumulator.java`, `EventStreamBus.java`, `Summariser.java`, `SummarisationRunner.java`

After all moves, sync both projects: `ide_sync_files` on both quarkmind and blocks.

- [ ] **Step 3: Move 3 test files from quarkmind to blocks via IntelliJ**

Same approach with `ide_move_file` for each test file:
- `project_path`: `/Users/mdproctor/claude/casehub/quarkmind`
- `file`: `src/test/java/io/casehub/blocks/summarisation/<filename>.java`
- `destination`: `/Users/mdproctor/claude/casehub/blocks/src/test/java/io/casehub/blocks/summarisation`

Files to move: `EventAccumulatorTest.java`, `EventStreamBusTest.java`, `SummarisationRunnerTest.java`

After all moves, sync both projects.

- [ ] **Step 5: Run tests**

Run: `mvn --batch-mode test` in blocks
Expected: BUILD SUCCESS — all 15 tests pass (6 + 4 + 5)

- [ ] **Step 6: Install blocks with new code**

Run: `mvn --batch-mode install -DskipTests` in blocks
Expected: BUILD SUCCESS — makes updated artifact available for quarkmind.

- [ ] **Step 7: Commit**

```
feat(#27): extract summarisation framework from quarkmind

7 source files + 3 test files (15 tests). Pure Java, zero new
dependencies. EventLevel, LevelEvent, WindowPolicy, EventAccumulator,
EventStreamBus, Summariser, SummarisationRunner.

Refs casehubio/blocks#27
```

---

### Task 5: Wire quarkmind to blocks dependency (quarkmind)

**Repo:** `/Users/mdproctor/claude/casehub/quarkmind` (on main)

**Files:**
- Modify: `pom.xml` — add casehub-blocks dependency

**Note:** Task 4 used `ide_move_file` which already removed the source and test files from quarkmind. This task only adds the dependency and validates.

**Interfaces:**
- Consumes: blocks artifact with `io.casehub.blocks.summarisation` (from Task 4)
- Produces: quarkmind depending on blocks, no local framework copies

- [ ] **Step 1: Add casehub-blocks dependency to quarkmind pom.xml**

Add in the `<dependencies>` section (after the casehub-* compile deps):

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks</artifactId>
</dependency>
```

Version is managed by `casehub-parent`.

- [ ] **Step 2: Verify files were removed by Task 4's ide_move_file**

Confirm `src/main/java/io/casehub/blocks/summarisation/` and `src/test/java/io/casehub/blocks/summarisation/` no longer exist in quarkmind. If they do (move failed partially), use `ide_refactor_safe_delete` on the remaining files.

- [ ] **Step 4: Run full test suite**

Run: `mvn --batch-mode test` in quarkmind
Expected: BUILD SUCCESS — all quarkmind code imports from `io.casehub.blocks.summarisation` which is now resolved from the blocks JAR. No source changes needed — package names are identical.

- [ ] **Step 5: Commit**

```
feat(#27): depend on casehub-blocks for summarisation framework

Replace local io.casehub.blocks.summarisation sources with casehub-blocks
dependency. Package names unchanged — all imports resolve from blocks JAR.

Refs casehubio/blocks#27
```

---

### Task 6: Clinical domain example (blocks)

**Repo:** `/Users/mdproctor/claude/casehub/blocks` (on `issue-27-layered-event-summarisation`)

**Files:**
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/clinical/VitalType.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/clinical/VitalReading.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/clinical/ClinicalCategory.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/clinical/Severity.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/clinical/ClinicalEvent.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/clinical/CarePhase.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/clinical/ClinicalNarrative.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/clinical/ClinicalEventSummariser.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/clinical/CarePhaseSummariser.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/clinical/ClinicalPipelineTest.java`

**Interfaces:**
- Consumes: `io.casehub.blocks.summarisation.*` (from Task 4)
- Produces: complete clinical temporal abstraction example with L1->L4 pipeline, fan-out topology, Pattern B, async summariser

- [ ] **Step 1: Create domain records**

`VitalType.java`:
```java
package io.casehub.blocks.summarisation.examples.clinical;

public enum VitalType { HR, BP_SYSTOLIC, BP_DIASTOLIC, SPO2, RR, TEMP }
```

`VitalReading.java`:
```java
package io.casehub.blocks.summarisation.examples.clinical;

public record VitalReading(VitalType type, double value, String unit) {}
```

`Severity.java`:
```java
package io.casehub.blocks.summarisation.examples.clinical;

public enum Severity { MILD, MODERATE, CRITICAL }
```

`ClinicalCategory.java`:
```java
package io.casehub.blocks.summarisation.examples.clinical;

public enum ClinicalCategory { TACHYCARDIA, BRADYCARDIA, HYPERTENSION, HYPOTENSION, HYPOXEMIA, TACHYPNEA, FEVER, HYPOTHERMIA }
```

`ClinicalEvent.java`:
```java
package io.casehub.blocks.summarisation.examples.clinical;

public record ClinicalEvent(ClinicalCategory category, Severity severity, String description, String rationale) {}
```

`CarePhase.java`:
```java
package io.casehub.blocks.summarisation.examples.clinical;

public record CarePhase(String phase, long durationMillis, String rationale) {}
```

`ClinicalNarrative.java`:
```java
package io.casehub.blocks.summarisation.examples.clinical;

public record ClinicalNarrative(String summary, long timestamp) {}
```

- [ ] **Step 2: Create ClinicalEventSummariser (sync, rule-based L1->L2)**

```java
package io.casehub.blocks.summarisation.examples.clinical;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.ArrayList;
import java.util.List;

public class ClinicalEventSummariser implements Summariser.SyncSummariser<VitalReading, ClinicalEvent> {

    @Override
    public List<ClinicalEvent> summarise(List<LevelEvent<VitalReading>> batch) {
        var events = new ArrayList<ClinicalEvent>();
        for (var e : batch) {
            var reading = e.payload();
            classify(reading).ifPresent(events::add);
        }
        return events;
    }

    private java.util.Optional<ClinicalEvent> classify(VitalReading reading) {
        return switch (reading.type()) {
            case HR -> {
                if (reading.value() > 130) yield java.util.Optional.of(new ClinicalEvent(
                    ClinicalCategory.TACHYCARDIA, Severity.CRITICAL,
                    "HR " + reading.value() + " " + reading.unit(),
                    "HR > 130 bpm"));
                if (reading.value() > 100) yield java.util.Optional.of(new ClinicalEvent(
                    ClinicalCategory.TACHYCARDIA, Severity.MODERATE,
                    "HR " + reading.value() + " " + reading.unit(),
                    "HR > 100 bpm"));
                if (reading.value() < 50) yield java.util.Optional.of(new ClinicalEvent(
                    ClinicalCategory.BRADYCARDIA, Severity.MODERATE,
                    "HR " + reading.value() + " " + reading.unit(),
                    "HR < 50 bpm"));
                yield java.util.Optional.empty();
            }
            case SPO2 -> {
                if (reading.value() < 90) yield java.util.Optional.of(new ClinicalEvent(
                    ClinicalCategory.HYPOXEMIA, Severity.CRITICAL,
                    "SpO2 " + reading.value() + "%",
                    "SpO2 < 90%"));
                if (reading.value() < 94) yield java.util.Optional.of(new ClinicalEvent(
                    ClinicalCategory.HYPOXEMIA, Severity.MODERATE,
                    "SpO2 " + reading.value() + "%",
                    "SpO2 < 94%"));
                yield java.util.Optional.empty();
            }
            case BP_SYSTOLIC -> {
                if (reading.value() > 180) yield java.util.Optional.of(new ClinicalEvent(
                    ClinicalCategory.HYPERTENSION, Severity.CRITICAL,
                    "BP " + reading.value() + " " + reading.unit(),
                    "Systolic > 180 mmHg"));
                yield java.util.Optional.empty();
            }
            default -> java.util.Optional.empty();
        };
    }
}
```

- [ ] **Step 3: Create CarePhaseSummariser (sync, rule-based L2->L3)**

```java
package io.casehub.blocks.summarisation.examples.clinical;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.List;

public class CarePhaseSummariser implements Summariser.SyncSummariser<ClinicalEvent, CarePhase> {

    @Override
    public List<CarePhase> summarise(List<LevelEvent<ClinicalEvent>> batch) {
        if (batch.isEmpty()) return List.of();

        long earliest = batch.get(0).timestamp();
        long latest = batch.get(batch.size() - 1).timestamp();
        long duration = latest - earliest;

        long criticalCount = batch.stream()
            .filter(e -> e.payload().severity() == Severity.CRITICAL)
            .count();

        String phase;
        String rationale;

        if (criticalCount >= 2) {
            phase = "ACUTE_DETERIORATION";
            rationale = criticalCount + " critical events in window";
        } else if (criticalCount == 1) {
            phase = "EARLY_WARNING";
            rationale = "Single critical event — monitoring closely";
        } else {
            phase = "STABLE_MONITORING";
            rationale = "No critical events — routine monitoring";
        }

        return List.of(new CarePhase(phase, duration, rationale));
    }
}
```

- [ ] **Step 4: Write ClinicalPipelineTest**

```java
package io.casehub.blocks.summarisation.examples.clinical;

import io.casehub.blocks.summarisation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.*;

class ClinicalPipelineTest {

    static final EventLevel L1_VITALS = new EventLevel("vitals", 1);
    static final EventLevel L2_EVENTS = new EventLevel("clinical-events", 2);
    static final EventLevel L3_PHASES = new EventLevel("care-phases", 3);
    static final EventLevel L4_NARRATIVE = new EventLevel("narrative", 4);

    EventStreamBus<VitalReading> vitalsBus;
    EventStreamBus<ClinicalEvent> eventBus;
    EventStreamBus<CarePhase> phaseBus;
    EventStreamBus<ClinicalNarrative> narrativeBus;

    SummarisationRunner<VitalReading, ClinicalEvent> eventRunner;
    SummarisationRunner<ClinicalEvent, CarePhase> phaseRunner;
    SummarisationRunner<CarePhase, ClinicalNarrative> narrativeRunner;

    List<ClinicalEvent> capturedEvents;
    List<CarePhase> capturedPhases;
    List<ClinicalNarrative> capturedNarratives;

    @BeforeEach
    void setUp() {
        vitalsBus = new EventStreamBus<>();
        eventBus = new EventStreamBus<>();
        phaseBus = new EventStreamBus<>();
        narrativeBus = new EventStreamBus<>();

        capturedEvents = new ArrayList<>();
        capturedPhases = new ArrayList<>();
        capturedNarratives = new ArrayList<>();

        eventBus.subscribe(e -> true, e -> capturedEvents.add(e.payload()));
        phaseBus.subscribe(p -> true, e -> capturedPhases.add(e.payload()));
        narrativeBus.subscribe(n -> true, e -> capturedNarratives.add(e.payload()));

        eventRunner = new SummarisationRunner<>(
            new WindowPolicy(0, 5),
            Summariser.ofSync(new ClinicalEventSummariser()),
            eventBus, L2_EVENTS);

        phaseRunner = new SummarisationRunner<>(
            new WindowPolicy(900_000, 0),
            Summariser.ofSync(new CarePhaseSummariser()),
            phaseBus, L3_PHASES);

        Summariser<CarePhase, ClinicalNarrative> narrativeSummariser = batch -> {
            String phases = batch.stream()
                .map(e -> e.payload().phase())
                .distinct()
                .reduce((a, b) -> a + " → " + b)
                .orElse("none");
            long ts = batch.get(batch.size() - 1).timestamp();
            return CompletableFuture.completedFuture(List.of(
                new ClinicalNarrative("Patient progressed through: " + phases, ts)));
        };

        narrativeRunner = new SummarisationRunner<>(
            new WindowPolicy(0, 3),
            narrativeSummariser,
            narrativeBus, L4_NARRATIVE);

        // Wire pipeline
        vitalsBus.subscribe(v -> true, eventRunner::collect);
        eventBus.subscribe(e -> true, phaseRunner::collect);
        phaseBus.subscribe(p -> true, narrativeRunner::collect);
    }

    @Test
    void fullPipeline_vitalsToNarrative() {
        // Feed 5 vitals with 2 critical readings → triggers L1->L2
        feedVital(VitalType.HR, 72, "bpm", 1000);
        feedVital(VitalType.SPO2, 98, "%", 2000);
        feedVital(VitalType.HR, 135, "bpm", 3000);   // CRITICAL tachycardia
        feedVital(VitalType.SPO2, 88, "%", 4000);     // CRITICAL hypoxemia
        feedVital(VitalType.HR, 110, "bpm", 5000);    // MODERATE tachycardia

        eventRunner.tick(6000);

        assertThat(capturedEvents)
            .extracting(ClinicalEvent::category)
            .containsExactly(
                ClinicalCategory.TACHYCARDIA,
                ClinicalCategory.HYPOXEMIA,
                ClinicalCategory.TACHYCARDIA);
        assertThat(capturedEvents)
            .extracting(ClinicalEvent::rationale)
            .allMatch(r -> r != null && !r.isEmpty());
    }

    @Test
    void phaseTransition_stableToDeterioration() {
        // First window — stable (no critical events)
        feedVital(VitalType.HR, 75, "bpm", 1000);
        feedVital(VitalType.HR, 78, "bpm", 2000);
        feedVital(VitalType.SPO2, 97, "%", 3000);
        feedVital(VitalType.HR, 80, "bpm", 4000);
        feedVital(VitalType.SPO2, 96, "%", 5000);
        eventRunner.tick(6000);

        // Trigger L2->L3 with time window
        phaseRunner.tick(1_000_000);  // past 900s window

        assertThat(capturedPhases).hasSize(1);
        assertThat(capturedPhases.get(0).phase()).isEqualTo("STABLE_MONITORING");

        // Second window — deterioration (critical events)
        feedVital(VitalType.HR, 140, "bpm", 1_000_001);
        feedVital(VitalType.SPO2, 85, "%", 1_000_002);
        feedVital(VitalType.HR, 145, "bpm", 1_000_003);
        feedVital(VitalType.SPO2, 82, "%", 1_000_004);
        feedVital(VitalType.HR, 150, "bpm", 1_000_005);
        eventRunner.tick(1_000_006);

        phaseRunner.tick(2_000_000);

        assertThat(capturedPhases).hasSize(2);
        assertThat(capturedPhases.get(1).phase()).isEqualTo("ACUTE_DETERIORATION");
    }

    @Test
    void asyncNarrative_producesNarrativeFromPhases() {
        // Feed enough to produce 3 phases → triggers L3->L4
        for (int i = 0; i < 3; i++) {
            feedVital(VitalType.HR, 72, "bpm", i * 1_000_000L + 1);
            feedVital(VitalType.HR, 73, "bpm", i * 1_000_000L + 2);
            feedVital(VitalType.HR, 74, "bpm", i * 1_000_000L + 3);
            feedVital(VitalType.HR, 75, "bpm", i * 1_000_000L + 4);
            feedVital(VitalType.HR, 76, "bpm", i * 1_000_000L + 5);
            eventRunner.tick(i * 1_000_000L + 6);
            phaseRunner.tick((i + 1) * 1_000_000L);
        }

        narrativeRunner.tick(3_000_001);

        assertThat(capturedNarratives).hasSize(1);
        assertThat(capturedNarratives.get(0).summary()).contains("STABLE_MONITORING");
    }

    @Test
    void asyncError_observableViaTick() {
        Summariser<CarePhase, ClinicalNarrative> failingSummariser = batch ->
            CompletableFuture.failedFuture(new RuntimeException("LLM unavailable"));

        var failingRunner = new SummarisationRunner<>(
            new WindowPolicy(0, 1), failingSummariser, narrativeBus, L4_NARRATIVE);

        failingRunner.collect(new LevelEvent<>(new CarePhase("STABLE", 1000, "test"), 1, L3_PHASES, null));

        CompletionStage<Void> result = failingRunner.tick(2);

        assertThatThrownBy(() -> result.toCompletableFuture().join())
            .hasCauseInstanceOf(RuntimeException.class)
            .hasMessageContaining("LLM unavailable");
    }

    @Test
    void fanOut_patternBAccumulatorAlongsidePipeline() {
        // Pattern B: direct EventAccumulator on L2 bus (medication alert check)
        var alertAccumulator = new EventAccumulator<ClinicalEvent>(new WindowPolicy(0, 3));
        eventBus.subscribe(
            e -> e.severity() == Severity.CRITICAL,
            e -> alertAccumulator.collect(e));

        // Feed 5 vitals with 3 critical → triggers both pipeline and accumulator
        feedVital(VitalType.HR, 140, "bpm", 1000);
        feedVital(VitalType.SPO2, 85, "%", 2000);
        feedVital(VitalType.HR, 145, "bpm", 3000);
        feedVital(VitalType.SPO2, 82, "%", 4000);
        feedVital(VitalType.HR, 150, "bpm", 5000);
        eventRunner.tick(6000);

        // Pipeline produced L2 events
        assertThat(capturedEvents).hasSizeGreaterThanOrEqualTo(3);

        // Pattern B accumulator also collected critical events
        assertThat(alertAccumulator.shouldEmit(7000)).isTrue();
        var alertBatch = alertAccumulator.drain();
        assertThat(alertBatch)
            .extracting(e -> e.payload().severity())
            .containsOnly(Severity.CRITICAL);
    }

    private void feedVital(VitalType type, double value, String unit, long timestamp) {
        vitalsBus.publish(new LevelEvent<>(new VitalReading(type, value, unit), timestamp, L1_VITALS, null));
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn --batch-mode test` in blocks
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```
feat(#38): clinical temporal abstraction example — L1-L4 pipeline

Demonstrates full pipeline (vitals→episodes→phases→narrative),
fan-out topology (Pattern A + Pattern B from L2 bus), phase transitions,
severity escalation, async error observability, and rationale traces.

Refs casehubio/blocks#38
```

---

### Task 7: Logistics domain example (blocks)

**Repo:** `/Users/mdproctor/claude/casehub/blocks` (on `issue-27-layered-event-summarisation`)

**Files:**
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/logistics/ScanType.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/logistics/PackageScan.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/logistics/AnomalyType.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/logistics/PackageAnomaly.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/logistics/HubPhase.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/logistics/HubNarrative.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/logistics/AnomalyDetectorSummariser.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/logistics/HubPhaseSummariser.java`
- Create: `src/test/java/io/casehub/blocks/summarisation/examples/logistics/LogisticsPipelineTest.java`

**Interfaces:**
- Consumes: `io.casehub.blocks.summarisation.*` (from Task 4)
- Produces: complete logistics hub monitoring example with L1->L4 pipeline, Pattern B branch, multi-level subscription

- [ ] **Step 1: Create domain records**

`ScanType.java`:
```java
package io.casehub.blocks.summarisation.examples.logistics;

public enum ScanType { INBOUND, OUTBOUND, TRANSFER }
```

`PackageScan.java`:
```java
package io.casehub.blocks.summarisation.examples.logistics;

public record PackageScan(String scanId, String warehouseId, double weight, String destination, ScanType scanType) {}
```

`AnomalyType.java`:
```java
package io.casehub.blocks.summarisation.examples.logistics;

public enum AnomalyType { MISROUTE, WEIGHT_MISMATCH, DELAY, DAMAGE }
```

`PackageAnomaly.java`:
```java
package io.casehub.blocks.summarisation.examples.logistics;

public record PackageAnomaly(AnomalyType type, String severity, String details, String rationale) {}
```

`HubPhase.java`:
```java
package io.casehub.blocks.summarisation.examples.logistics;

public record HubPhase(String phase, long durationMillis, String rationale) {}
```

`HubNarrative.java`:
```java
package io.casehub.blocks.summarisation.examples.logistics;

public record HubNarrative(String summary, long timestamp) {}
```

- [ ] **Step 2: Create AnomalyDetectorSummariser (sync, L1->L2)**

```java
package io.casehub.blocks.summarisation.examples.logistics;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnomalyDetectorSummariser implements Summariser.SyncSummariser<PackageScan, PackageAnomaly> {

    @Override
    public List<PackageAnomaly> summarise(List<LevelEvent<PackageScan>> batch) {
        var anomalies = new ArrayList<PackageAnomaly>();

        // Detect misroutes: same destination appearing from multiple warehouses
        Map<String, List<PackageScan>> byDestination = batch.stream()
            .collect(Collectors.groupingBy(
                e -> e.payload().destination(),
                Collectors.mapping(LevelEvent::payload, Collectors.toList())));

        for (var entry : byDestination.entrySet()) {
            long distinctWarehouses = entry.getValue().stream()
                .map(PackageScan::warehouseId)
                .distinct().count();
            if (distinctWarehouses > 1) {
                anomalies.add(new PackageAnomaly(
                    AnomalyType.MISROUTE, "HIGH",
                    entry.getKey() + " routed from " + distinctWarehouses + " warehouses",
                    "Same destination from multiple origins suggests misroute"));
            }
        }

        // Detect weight anomalies: any package over 50kg flagged
        for (var e : batch) {
            if (e.payload().weight() > 50.0) {
                anomalies.add(new PackageAnomaly(
                    AnomalyType.WEIGHT_MISMATCH, "MEDIUM",
                    e.payload().scanId() + " weighs " + e.payload().weight() + "kg",
                    "Weight exceeds 50kg threshold"));
            }
        }

        return anomalies;
    }
}
```

- [ ] **Step 3: Create HubPhaseSummariser (sync, L2->L3)**

```java
package io.casehub.blocks.summarisation.examples.logistics;

import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;

import java.util.List;

public class HubPhaseSummariser implements Summariser.SyncSummariser<PackageAnomaly, HubPhase> {

    @Override
    public List<HubPhase> summarise(List<LevelEvent<PackageAnomaly>> batch) {
        if (batch.isEmpty()) return List.of();

        long earliest = batch.get(0).timestamp();
        long latest = batch.get(batch.size() - 1).timestamp();
        long duration = latest - earliest;

        long highCount = batch.stream()
            .filter(e -> "HIGH".equals(e.payload().severity()))
            .count();

        String phase;
        String rationale;

        if (highCount >= 3) {
            phase = "CONGESTION";
            rationale = highCount + " high-severity anomalies — hub overwhelmed";
        } else if (highCount >= 1) {
            phase = "DEGRADED";
            rationale = highCount + " high-severity anomaly — monitoring";
        } else {
            phase = "NORMAL_FLOW";
            rationale = "No high-severity anomalies";
        }

        return List.of(new HubPhase(phase, duration, rationale));
    }
}
```

- [ ] **Step 4: Write LogisticsPipelineTest**

```java
package io.casehub.blocks.summarisation.examples.logistics;

import io.casehub.blocks.summarisation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

class LogisticsPipelineTest {

    static final EventLevel L1_SCANS = new EventLevel("scans", 1);
    static final EventLevel L2_ANOMALIES = new EventLevel("anomalies", 2);
    static final EventLevel L3_PHASES = new EventLevel("hub-phases", 3);
    static final EventLevel L4_NARRATIVE = new EventLevel("narrative", 4);

    EventStreamBus<PackageScan> scanBus;
    EventStreamBus<PackageAnomaly> anomalyBus;
    EventStreamBus<HubPhase> phaseBus;
    EventStreamBus<HubNarrative> narrativeBus;

    SummarisationRunner<PackageScan, PackageAnomaly> anomalyRunner;
    SummarisationRunner<PackageAnomaly, HubPhase> phaseRunner;
    SummarisationRunner<HubPhase, HubNarrative> narrativeRunner;

    List<PackageAnomaly> capturedAnomalies;
    List<HubPhase> capturedPhases;
    List<HubNarrative> capturedNarratives;

    @BeforeEach
    void setUp() {
        scanBus = new EventStreamBus<>();
        anomalyBus = new EventStreamBus<>();
        phaseBus = new EventStreamBus<>();
        narrativeBus = new EventStreamBus<>();

        capturedAnomalies = new ArrayList<>();
        capturedPhases = new ArrayList<>();
        capturedNarratives = new ArrayList<>();

        anomalyBus.subscribe(a -> true, e -> capturedAnomalies.add(e.payload()));
        phaseBus.subscribe(p -> true, e -> capturedPhases.add(e.payload()));
        narrativeBus.subscribe(n -> true, e -> capturedNarratives.add(e.payload()));

        anomalyRunner = new SummarisationRunner<>(
            new WindowPolicy(0, 10),
            Summariser.ofSync(new AnomalyDetectorSummariser()),
            anomalyBus, L2_ANOMALIES);

        phaseRunner = new SummarisationRunner<>(
            new WindowPolicy(300_000, 0),
            Summariser.ofSync(new HubPhaseSummariser()),
            phaseBus, L3_PHASES);

        Summariser<HubPhase, HubNarrative> narrativeSummariser = batch -> {
            String phases = batch.stream()
                .map(e -> e.payload().phase())
                .distinct()
                .reduce((a, b) -> a + " → " + b)
                .orElse("none");
            long ts = batch.get(batch.size() - 1).timestamp();
            return CompletableFuture.completedFuture(List.of(
                new HubNarrative("Hub progression: " + phases, ts)));
        };

        narrativeRunner = new SummarisationRunner<>(
            new WindowPolicy(0, 2),
            narrativeSummariser,
            narrativeBus, L4_NARRATIVE);

        // Wire pipeline
        scanBus.subscribe(s -> true, anomalyRunner::collect);
        anomalyBus.subscribe(a -> true, phaseRunner::collect);
        phaseBus.subscribe(p -> true, narrativeRunner::collect);
    }

    @Test
    void fullPipeline_scansToNarrative() {
        // Feed 10 scans with misroute pattern → triggers L1->L2
        for (int i = 0; i < 8; i++) {
            feedScan("PKG-" + i, "WH-A", 10.0, "DEST-1", ScanType.INBOUND, i * 100L);
        }
        feedScan("PKG-8", "WH-B", 10.0, "DEST-1", ScanType.INBOUND, 800);
        feedScan("PKG-9", "WH-C", 10.0, "DEST-1", ScanType.INBOUND, 900);

        anomalyRunner.tick(1000);

        assertThat(capturedAnomalies)
            .extracting(PackageAnomaly::type)
            .contains(AnomalyType.MISROUTE);
        assertThat(capturedAnomalies)
            .extracting(PackageAnomaly::rationale)
            .allMatch(r -> r != null && !r.isEmpty());
    }

    @Test
    void weightAnomaly_detected() {
        for (int i = 0; i < 9; i++) {
            feedScan("PKG-" + i, "WH-A", 10.0, "DEST-" + i, ScanType.OUTBOUND, i * 100L);
        }
        feedScan("PKG-HEAVY", "WH-A", 75.0, "DEST-X", ScanType.OUTBOUND, 900);

        anomalyRunner.tick(1000);

        assertThat(capturedAnomalies)
            .extracting(PackageAnomaly::type)
            .contains(AnomalyType.WEIGHT_MISMATCH);
    }

    @Test
    void phaseClassification_normalVsCongestion() {
        // First window — no anomalies → normal flow
        for (int i = 0; i < 10; i++) {
            feedScan("PKG-" + i, "WH-A", 10.0, "DEST-" + i, ScanType.INBOUND, i * 100L);
        }
        anomalyRunner.tick(1000);
        phaseRunner.tick(400_000);

        assertThat(capturedPhases).hasSize(1);
        assertThat(capturedPhases.get(0).phase()).isEqualTo("NORMAL_FLOW");
    }

    @Test
    void patternB_complianceAccumulatorAlongsidePipeline() {
        // Pattern B: direct accumulator for compliance audit log
        var complianceAccumulator = new EventAccumulator<PackageAnomaly>(new WindowPolicy(600_000, 0));
        anomalyBus.subscribe(a -> true, complianceAccumulator::collect);

        // Feed 10 scans with a heavy package
        for (int i = 0; i < 9; i++) {
            feedScan("PKG-" + i, "WH-A", 10.0, "DEST-" + i, ScanType.TRANSFER, i * 100L);
        }
        feedScan("PKG-HEAVY", "WH-A", 60.0, "DEST-X", ScanType.TRANSFER, 900);
        anomalyRunner.tick(1000);

        // Pipeline produced anomalies
        assertThat(capturedAnomalies).isNotEmpty();

        // Pattern B accumulator also collected (time-based window, not yet triggered)
        assertThat(complianceAccumulator.size()).isGreaterThan(0);
        assertThat(complianceAccumulator.shouldEmit(500_000)).isFalse();
        assertThat(complianceAccumulator.shouldEmit(700_000)).isTrue();
    }

    @Test
    void multiLevelSubscription_singleConsumerOnTwoBuses() {
        List<String> allEvents = new ArrayList<>();
        anomalyBus.subscribe(a -> true, e -> allEvents.add("L2:" + e.payload().type()));
        phaseBus.subscribe(p -> true, e -> allEvents.add("L3:" + e.payload().phase()));

        // Feed enough to produce L2 and L3
        for (int i = 0; i < 10; i++) {
            feedScan("PKG-" + i, "WH-A", (i == 9 ? 60.0 : 10.0), "DEST-" + i, ScanType.INBOUND, i * 100L);
        }
        anomalyRunner.tick(1000);
        phaseRunner.tick(400_000);

        assertThat(allEvents).anyMatch(s -> s.startsWith("L2:"));
        assertThat(allEvents).anyMatch(s -> s.startsWith("L3:"));
    }

    private void feedScan(String scanId, String warehouseId, double weight,
                          String destination, ScanType scanType, long timestamp) {
        scanBus.publish(new LevelEvent<>(new PackageScan(scanId, warehouseId, weight, destination, scanType), timestamp, L1_SCANS, null));
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn --batch-mode test` in blocks
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```
feat(#38): logistics hub monitoring example — L1-L4 pipeline

Demonstrates full pipeline (scans→anomalies→phases→narrative),
Pattern B compliance accumulator, multi-level subscription,
count vs time windowing, and misroute/weight anomaly detection.

Refs casehubio/blocks#38
```

---

### Task 8: Documentation updates (blocks + quarkmind)

**Repos:** blocks (on `issue-27-layered-event-summarisation`) + quarkmind (on main)

**Files:**
- Modify: `/Users/mdproctor/claude/casehub/blocks/CLAUDE.md`
- Modify: `/Users/mdproctor/claude/casehub/blocks/ARC42STORIES.MD`
- Modify: `/Users/mdproctor/claude/casehub/quarkmind/ARC42STORIES.MD`

**Interfaces:**
- Consumes: completed extraction and examples (Tasks 1-7)
- Produces: updated documentation

- [ ] **Step 1: Update blocks CLAUDE.md**

Add new package documentation section after the `routing.agent` package section:

```markdown
## Package: `io.casehub.blocks.summarisation`

Temporal abstraction framework for summarising high-frequency event streams into progressively higher-level abstractions. Pure Java, zero CDI/Quarkus dependencies. Extracted from quarkmind via #27.

| Class | What it does |
|-------|-------------|
| `EventLevel` | Record: `(String name, int ordinal)` — identifies a level in the hierarchy |
| `LevelEvent<E>` | Record: `(E payload, long timestamp, EventLevel level)` — typed event at a specific level |
| `WindowPolicy` | Record: `(long maxAge, int maxCount)` — dual-trigger windowing. Validates: both >= 0, at least one positive. |
| `EventAccumulator<E>` | Thread-safe event buffer. `collect()`, `shouldEmit(now)`, `drain()`, `clear()`, `size()`. Synchronized on all public methods. |
| `EventStreamBus<E>` | Predicate-based pub/sub. `subscribe(Predicate, Consumer)`, `publish(LevelEvent)`, `clear()`. CopyOnWriteArrayList-backed — concurrent publish+subscribe safe. |
| `Summariser<IN, OUT>` | `@FunctionalInterface`. `CompletionStage<List<OUT>> summarise(List<LevelEvent<IN>>)`. `ofSync()` factory for deterministic implementations. |
| `SummarisationRunner<IN, OUT>` | Wires accumulator → summariser → output bus. `collect()`, `tick()` (returns `CompletionStage<Void>`), `clear()`, `size()`. |

Two integration patterns: **Pattern A** (SummarisationRunner pipeline — sync heuristics, microsecond latency) and **Pattern B** (direct EventAccumulator — async LLM dispatch, caller manages). See spec for details.
```

Also update the Key Directories table to add:

```markdown
| `src/main/java/io/casehub/blocks/summarisation/` | Temporal abstraction framework — event levels, windowed accumulation, pluggable summarisation |
| `src/test/java/io/casehub/blocks/summarisation/` | Tests for summarisation framework |
| `src/test/java/io/casehub/blocks/summarisation/examples/clinical/` | Clinical temporal abstraction example (L1-L4 pipeline) |
| `src/test/java/io/casehub/blocks/summarisation/examples/logistics/` | Logistics hub monitoring example (L1-L4 pipeline) |
```

Also update the Consumers table to add quarkmind:

```markdown
| casehub-quarkmind | Summarisation: `SummarisationRunner`, `EventStreamBus`, `EventAccumulator`, `WindowPolicy`, `Summariser`, `EventLevel`, `LevelEvent` — SC2 game event hierarchy (L2→L3→L4 pipeline via `SummarisationLifecycle`, `GamePhaseSummariser`, `GameArcSummariser`) |
```

- [ ] **Step 2: Update blocks ARC42STORIES.MD §5**

Add a `summarisation` entry to the Building Block View section, following the existing pattern for `channel` and `agentic`.

- [ ] **Step 3: Update quarkmind ARC42STORIES.MD**

Add note that quarkmind depends on `casehub-blocks` for the generic summarisation framework types (`io.casehub.blocks.summarisation`).

- [ ] **Step 4: Update issue #27 body**

Update the issue body to reflect EventConsumer removal (8→7 files).

- [ ] **Step 5: Commit blocks changes**

```
docs(#27): update CLAUDE.md and ARC42STORIES for summarisation package

Refs casehubio/blocks#27
```

- [ ] **Step 6: Commit quarkmind changes**

```
docs: note casehub-blocks dependency for summarisation framework

Refs casehubio/blocks#27
```

---

## Self-Review Checklist

| Check | Status |
|-------|--------|
| Spec coverage: EventConsumer removal | ✅ Task 1 |
| Spec coverage: MomentBroker dead code | ✅ Task 1 step 4 |
| Spec coverage: tick() return type | ✅ Task 3 |
| Spec coverage: WindowPolicy validation | ✅ Task 2 step 1 |
| Spec coverage: EventAccumulator thread-safety | ✅ Task 2 step 2 |
| Spec coverage: EventStreamBus Javadoc | ✅ Task 2 step 3 |
| Spec coverage: 7 source + 3 test extraction | ✅ Task 4 |
| Spec coverage: quarkmind dependency switch | ✅ Task 5 |
| Spec coverage: Clinical example with fan-out | ✅ Task 6 |
| Spec coverage: Logistics example with Pattern B | ✅ Task 7 |
| Spec coverage: ARC42STORIES updates | ✅ Task 8 steps 2-3 |
| Spec coverage: Issue #27 body update | ✅ Task 8 step 4 |
| Spec coverage: CLAUDE.md update | ✅ Task 8 step 1 |
| Placeholder scan | ✅ No TBD/TODO found |
| Type consistency: `WindowPolicy` | ✅ Same name/fields across Tasks 2, 4, 6, 7 |
| Type consistency: `CompletionStage<Void>` | ✅ Same return type in Tasks 3, 4, 6 |
| Type consistency: `Summariser.ofSync()` | ✅ Same factory method in Tasks 3, 6, 7 |
| Type consistency: example records | ✅ Field names match between record defs and test usage |

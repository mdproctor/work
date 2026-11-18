# Memory Hygiene Parameter Adaptation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #1119 — Memory hygiene parameter adaptation via consolidation phase
**Issue group:** #1119

**Goal:** Add a consolidation phase that observes memory hygiene quality signals and adapts the retention threshold to maintain healthy eviction/retention balance.

**Architecture:** A `HygieneAdaptationPhase` (ConsolidationPhase at @Priority(48)) reads `KnowledgeGapSummary` from the hygiene orchestrator, computes eviction pressure, and adjusts `retentionThreshold` via an `AdaptiveRetentionConfig` holder. A `RetentionConfigSupplier` SPI in cognition-api decouples the hygiene orchestrator from the adaptation mechanism.

**Tech Stack:** Java 21, Quarkus 3.32.2 CDI, JUnit 5, AssertJ, Mockito

## Global Constraints

- Java 21 source, Java 26 JVM
- Build: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install`
- Module test: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean test -pl <module>`
- Use `mvn` not `./mvnw`
- All commits reference issue: `Refs #1119` or `Closes #1119`
- Use ide-tooling for all code navigation and structural editing

---

## Batch 1: SPI + Holder Foundation

### Task 1: RetentionConfigSupplier SPI and AdaptiveRetentionConfig holder

**Files:**
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/RetentionConfigSupplier.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/AdaptiveRetentionConfig.java`
- Create: `cognition/src/test/java/io/casehub/neocortex/cognition/memory/AdaptiveRetentionConfigTest.java`
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/CognitionDefaultBeans.java:346-351`

**Interfaces:**
- Produces: `RetentionConfigSupplier` — `@FunctionalInterface`, single method `RetentionConfig current()`
- Produces: `AdaptiveRetentionConfig` — `@ApplicationScoped`, implements `RetentionConfigSupplier`, exposes `void update(RetentionConfig)`

- [ ] **Step 1: Write failing test for AdaptiveRetentionConfig**

```java
package io.casehub.neocortex.cognition.memory;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveRetentionConfigTest {

    @Test
    void startsWithDefault() {
        var holder = new AdaptiveRetentionConfig();
        assertThat(holder.current()).isEqualTo(RetentionConfig.DEFAULT);
    }

    @Test
    void updateSwapsConfig() {
        var holder = new AdaptiveRetentionConfig();
        var custom = new RetentionConfig(0.2, 1.0, 1.0, 0.5, 0.5);
        holder.update(custom);
        assertThat(holder.current()).isEqualTo(custom);
    }

    @Test
    void updateIsVisibleAcrossReads() {
        var holder = new AdaptiveRetentionConfig();
        var c1 = new RetentionConfig(0.15, 1.0, 1.0, 0.5, 0.5);
        var c2 = new RetentionConfig(0.25, 1.0, 1.0, 0.5, 0.5);
        holder.update(c1);
        assertThat(holder.current().retentionThreshold()).isEqualTo(0.15);
        holder.update(c2);
        assertThat(holder.current().retentionThreshold()).isEqualTo(0.25);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=AdaptiveRetentionConfigTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation error — `AdaptiveRetentionConfig` and `RetentionConfigSupplier` don't exist yet

- [ ] **Step 3: Create RetentionConfigSupplier SPI**

```java
package io.casehub.neocortex.cognition.memory;

@FunctionalInterface
public interface RetentionConfigSupplier {
    RetentionConfig current();
}
```

- [ ] **Step 4: Create AdaptiveRetentionConfig implementation**

```java
package io.casehub.neocortex.cognition.memory;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class AdaptiveRetentionConfig implements RetentionConfigSupplier {
    private final AtomicReference<RetentionConfig> config =
            new AtomicReference<>(RetentionConfig.DEFAULT);

    @Override
    public RetentionConfig current() {
        return config.get();
    }

    public void update(RetentionConfig newConfig) {
        config.set(newConfig);
    }
}
```

- [ ] **Step 5: Add DefaultBean producer for RetentionConfigSupplier in CognitionDefaultBeans**

In `CognitionDefaultBeans.java`, add a new producer method after the existing `retentionConfig()` producer (line ~351):

```java
@Produces @DefaultBean @Singleton
RetentionConfigSupplier retentionConfigSupplier() {
    return RetentionConfig.DEFAULT::current;
}
```

Wait — `RetentionConfig` is a record and doesn't have a `current()` method. The default supplier returns a fixed value:

```java
@Produces @DefaultBean @Singleton
RetentionConfigSupplier retentionConfigSupplier() {
    return () -> RetentionConfig.DEFAULT;
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=AdaptiveRetentionConfigTest`
Expected: 3 tests PASS

- [ ] **Step 7: Commit**

```bash
git add cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/RetentionConfigSupplier.java \
       cognition/src/main/java/io/casehub/neocortex/cognition/memory/AdaptiveRetentionConfig.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/memory/AdaptiveRetentionConfigTest.java \
       cognition/src/main/java/io/casehub/neocortex/cognition/CognitionDefaultBeans.java
git commit -m "feat(#1119): add RetentionConfigSupplier SPI and AdaptiveRetentionConfig holder

Refs #1119"
```

### Task 2: Wire MemoryHygieneOrchestrator to use RetentionConfigSupplier + add tenantGapSummary

**Files:**
- Modify: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/MemoryHygieneOrchestrator.java:5-9`
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/MemoryHygieneOrchestratorImpl.java:40,53-76,121-136`
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/memory/MemoryHygieneOrchestratorImplTest.java:31-44`

**Interfaces:**
- Consumes: `RetentionConfigSupplier` from Task 1
- Produces: `MemoryHygieneOrchestrator.tenantGapSummary(String tenantId)` — aggregates `KnowledgeGapSummary` across all agents for a tenant

- [ ] **Step 1: Write failing test for tenantGapSummary**

Add to `MemoryHygieneOrchestratorImplTest.java`:

```java
@Test
void tenantGapSummaryAggregatesAcrossAgents() {
    var now = Instant.now();
    var m1 = makeMemory("c1", "agent-1", now.minus(Duration.ofDays(1)));
    var m2 = makeMemory("c2", "agent-2", now.minus(Duration.ofDays(1)));
    when(store.retrieveSimilar(any(), any())).thenReturn(List.of(m1, m2));
    when(scorer.score(any(), any())).thenReturn(0.1); // below LOW_RETENTION_THRESHOLD

    orchestrator.tick("agent-1", "tenant-1");
    orchestrator.tick("agent-2", "tenant-1");

    var summary = orchestrator.tenantGapSummary("tenant-1");
    assertThat(summary.totalScored()).isEqualTo(
            orchestrator.knowledgeGaps("agent-1", "tenant-1").totalScored()
            + orchestrator.knowledgeGaps("agent-2", "tenant-1").totalScored());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=MemoryHygieneOrchestratorImplTest#tenantGapSummaryAggregatesAcrossAgents`
Expected: compilation error — `tenantGapSummary` doesn't exist

- [ ] **Step 3: Add tenantGapSummary to MemoryHygieneOrchestrator SPI**

Add to `MemoryHygieneOrchestrator.java` (after line 8):

```java
KnowledgeGapSummary tenantGapSummary(String tenantId);
```

- [ ] **Step 4: Implement tenantGapSummary in MemoryHygieneOrchestratorImpl**

Add to `MemoryHygieneOrchestratorImpl.java` (after `knowledgeGaps` method, ~line 87):

```java
@Override
public KnowledgeGapSummary tenantGapSummary(String tenantId) {
    String suffix = ":" + tenantId;
    int totalLow = 0, totalGroups = 0, totalScored = 0;
    for (var entry : gapStates.entrySet()) {
        if (entry.getKey().endsWith(suffix)) {
            var s = entry.getValue();
            totalLow += s.lowRetentionCount();
            totalGroups += s.consolidationGroups();
            totalScored += s.totalScored();
        }
    }
    if (totalScored == 0) return KnowledgeGapSummary.empty();
    return new KnowledgeGapSummary(totalLow, totalGroups, totalScored);
}
```

- [ ] **Step 5: Modify constructor to accept RetentionConfigSupplier**

Replace the `retentionConfig` field (line 40) with a `RetentionConfigSupplier`:

```java
private final RetentionConfigSupplier configSupplier;
```

Update constructor parameter (line 61): `RetentionConfig retentionConfig` → `RetentionConfigSupplier configSupplier`

Update constructor body (line 72): `this.retentionConfig = retentionConfig` → `this.configSupplier = configSupplier`

- [ ] **Step 6: Update tick logic to read config per tick**

In `doTickInternal` method (line 103), add at the top:

```java
var retentionConfig = configSupplier.current();
```

The rest of the method already uses `retentionConfig` as a local variable name, so it shadows the removed field seamlessly. No other changes needed in the tick logic.

- [ ] **Step 7: Update test setUp to pass a supplier**

In `MemoryHygieneOrchestratorImplTest.java`, change the constructor call in `setUp()` (line 38-43):

Replace `RETENTION,` with `() -> RETENTION,` — wrapping the config in a lambda that implements `RetentionConfigSupplier`.

- [ ] **Step 8: Run all hygiene tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=MemoryHygieneOrchestratorImplTest`
Expected: all tests PASS (existing + new tenantGapSummary test)

- [ ] **Step 9: Compile full module to catch any other references**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl cognition`
Expected: BUILD SUCCESS. If other classes construct `MemoryHygieneOrchestratorImpl` directly (e.g. `MemoryHygieneScheduler`), update them to pass a `RetentionConfigSupplier` — either injected or `() -> RetentionConfig.DEFAULT`.

- [ ] **Step 10: Commit**

```bash
git add cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/MemoryHygieneOrchestrator.java \
       cognition/src/main/java/io/casehub/neocortex/cognition/memory/MemoryHygieneOrchestratorImpl.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/memory/MemoryHygieneOrchestratorImplTest.java
git commit -m "feat(#1119): wire MemoryHygieneOrchestrator to RetentionConfigSupplier + add tenantGapSummary

Refs #1119"
```

## Batch 2: Adaptation Phase + Config + Event

### Task 3: HygieneAdaptationPhase with config gate and observability event

**Files:**
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/HygieneAdaptationConfig.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/HygieneAdaptationEvent.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/HygieneAdaptationPhase.java`
- Create: `cognition/src/test/java/io/casehub/neocortex/cognition/memory/HygieneAdaptationPhaseTest.java`

**Interfaces:**
- Consumes: `RetentionConfigSupplier` / `AdaptiveRetentionConfig` from Task 1
- Consumes: `MemoryHygieneOrchestrator.tenantGapSummary(String)` from Task 2
- Produces: `HygieneAdaptationPhase` — `ConsolidationPhase` at `@Priority(48)`, gated by `casehub.cognition.hygiene-adaptation.enabled`
- Produces: `HygieneAdaptationEvent` — CDI event record with `AdaptationDirection` enum

- [ ] **Step 1: Write failing tests for adaptation logic**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.KnowledgeGapSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HygieneAdaptationPhaseTest {

    private AdaptiveRetentionConfig adaptiveConfig;
    private StubHygieneOrchestrator orchestrator;
    private List<HygieneAdaptationEvent> firedEvents;
    private HygieneAdaptationPhase phase;

    @BeforeEach
    void setUp() {
        adaptiveConfig = new AdaptiveRetentionConfig();
        orchestrator = new StubHygieneOrchestrator();
        firedEvents = new ArrayList<>();
        phase = new HygieneAdaptationPhase(
                orchestrator, adaptiveConfig,
                new TestHygieneAdaptationConfig(),
                firedEvents::add);
    }

    @Test
    void firstTickStoresBaselineWithoutAdapting() {
        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(30, 2, 100)); // 30% eviction pressure
        phase.run("t1", List.of());
        assertThat(adaptiveConfig.current()).isEqualTo(RetentionConfig.DEFAULT);
        assertThat(firedEvents).isEmpty();
    }

    @Test
    void highEvictionPressureLowersThreshold() {
        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(40, 2, 100)); // 40% — above 0.3 upper
        phase.run("t1", List.of()); // first tick — baseline

        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(40, 2, 100)); // still high
        phase.run("t1", List.of()); // second tick — should adapt

        assertThat(adaptiveConfig.current().retentionThreshold())
                .isLessThan(RetentionConfig.DEFAULT.retentionThreshold());
        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.getFirst().direction())
                .isEqualTo(HygieneAdaptationEvent.AdaptationDirection.LOWERED);
    }

    @Test
    void lowEvictionPressureRaisesThreshold() {
        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(2, 1, 100)); // 2% — below 0.05 lower
        phase.run("t1", List.of());

        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(2, 1, 100));
        phase.run("t1", List.of());

        assertThat(adaptiveConfig.current().retentionThreshold())
                .isGreaterThan(RetentionConfig.DEFAULT.retentionThreshold());
        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.getFirst().direction())
                .isEqualTo(HygieneAdaptationEvent.AdaptationDirection.RAISED);
    }

    @Test
    void pressureInTargetRangeHolds() {
        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(15, 1, 100)); // 15% — in [0.05, 0.3]
        phase.run("t1", List.of());

        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(15, 1, 100));
        phase.run("t1", List.of());

        assertThat(adaptiveConfig.current()).isEqualTo(RetentionConfig.DEFAULT);
        assertThat(firedEvents).isEmpty();
    }

    @Test
    void thresholdClampsAtMinimum() {
        adaptiveConfig.update(new RetentionConfig(0.02, 1.0, 1.0, 0.5, 0.5));
        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(50, 2, 100)); // high pressure
        phase.run("t1", List.of());

        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(50, 2, 100));
        phase.run("t1", List.of());

        assertThat(adaptiveConfig.current().retentionThreshold())
                .isGreaterThanOrEqualTo(0.01); // min from config
    }

    @Test
    void improvingTrendHoldsRatherThanOscillating() {
        // Tick 1: high pressure baseline
        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(40, 2, 100));
        phase.run("t1", List.of());

        // Tick 2: still high — lowers threshold
        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(40, 2, 100));
        phase.run("t1", List.of());
        double afterFirstAdaptation = adaptiveConfig.current().retentionThreshold();

        // Tick 3: pressure improved (dropped from 40% to 20%) — should HOLD
        orchestrator.setTenantSummary("t1",
                new KnowledgeGapSummary(20, 2, 100));
        phase.run("t1", List.of());

        assertThat(adaptiveConfig.current().retentionThreshold())
                .isEqualTo(afterFirstAdaptation);
    }

    @Test
    void zeroTotalScoredIsHandledSafely() {
        orchestrator.setTenantSummary("t1", KnowledgeGapSummary.empty());
        phase.run("t1", List.of());
        phase.run("t1", List.of());
        assertThat(adaptiveConfig.current()).isEqualTo(RetentionConfig.DEFAULT);
    }

    // --- Test doubles ---

    static class StubHygieneOrchestrator implements MemoryHygieneOrchestrator {
        private final java.util.Map<String, KnowledgeGapSummary> tenantSummaries =
                new java.util.HashMap<>();

        void setTenantSummary(String tenantId, KnowledgeGapSummary summary) {
            tenantSummaries.put(tenantId, summary);
        }

        @Override
        public void tick(String agentId, String tenantId) {}

        @Override
        public KnowledgeGapSummary knowledgeGaps(String agentId, String tenantId) {
            return KnowledgeGapSummary.empty();
        }

        @Override
        public KnowledgeGapSummary tenantGapSummary(String tenantId) {
            return tenantSummaries.getOrDefault(tenantId, KnowledgeGapSummary.empty());
        }
    }

    static class TestHygieneAdaptationConfig implements HygieneAdaptationConfig {
        @Override public boolean enabled() { return true; }
        @Override public double evictionPressureUpperThreshold() { return 0.3; }
        @Override public double evictionPressureLowerThreshold() { return 0.05; }
        @Override public double stepSize() { return 0.02; }
        @Override public double minRetentionThreshold() { return 0.01; }
        @Override public double maxRetentionThreshold() { return 0.5; }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=HygieneAdaptationPhaseTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation errors — HygieneAdaptationPhase, HygieneAdaptationConfig, HygieneAdaptationEvent don't exist

- [ ] **Step 3: Create HygieneAdaptationEvent record**

```java
package io.casehub.neocortex.cognition.memory;

import java.time.Instant;

public record HygieneAdaptationEvent(
        String tenantId,
        RetentionConfig previousConfig,
        RetentionConfig newConfig,
        double evictionPressure,
        AdaptationDirection direction,
        Instant timestamp) {

    public enum AdaptationDirection { LOWERED, RAISED, HELD }
}
```

- [ ] **Step 4: Create HygieneAdaptationConfig interface**

```java
package io.casehub.neocortex.cognition.memory;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.cognition.hygiene-adaptation")
public interface HygieneAdaptationConfig {
    @WithDefault("false")
    boolean enabled();

    @WithDefault("0.3")
    double evictionPressureUpperThreshold();

    @WithDefault("0.05")
    double evictionPressureLowerThreshold();

    @WithDefault("0.02")
    double stepSize();

    @WithDefault("0.01")
    double minRetentionThreshold();

    @WithDefault("0.5")
    double maxRetentionThreshold();
}
```

- [ ] **Step 5: Create HygieneAdaptationPhase**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.KnowledgeGapSummary;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@ApplicationScoped
@Priority(48)
@IfBuildProperty(name = "casehub.cognition.hygiene-adaptation.enabled",
                 stringValue = "true", enableIfMissing = false)
public class HygieneAdaptationPhase implements ConsolidationPhase {

    private final MemoryHygieneOrchestrator hygieneOrchestrator;
    private final AdaptiveRetentionConfig adaptiveConfig;
    private final HygieneAdaptationConfig config;
    private final Consumer<HygieneAdaptationEvent> eventSink;

    private final ConcurrentHashMap<String, KnowledgeGapSummary> previousSummaries =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RetentionConfig> previousConfigs =
            new ConcurrentHashMap<>();

    // CDI constructor
    public HygieneAdaptationPhase(
            MemoryHygieneOrchestrator hygieneOrchestrator,
            AdaptiveRetentionConfig adaptiveConfig,
            HygieneAdaptationConfig config,
            Event<HygieneAdaptationEvent> adaptationEvent) {
        this(hygieneOrchestrator, adaptiveConfig, config,
             (Consumer<HygieneAdaptationEvent>) adaptationEvent::fire);
    }

    // Test constructor
    HygieneAdaptationPhase(
            MemoryHygieneOrchestrator hygieneOrchestrator,
            AdaptiveRetentionConfig adaptiveConfig,
            HygieneAdaptationConfig config,
            Consumer<HygieneAdaptationEvent> eventSink) {
        this.hygieneOrchestrator = hygieneOrchestrator;
        this.adaptiveConfig = adaptiveConfig;
        this.config = config;
        this.eventSink = eventSink;
    }

    @Override
    public String name() {
        return "hygiene-adaptation";
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        var currentSummary = hygieneOrchestrator.tenantGapSummary(tenantId);
        if (currentSummary.totalScored() == 0) return;

        var previous = previousSummaries.get(tenantId);
        var currentConfig = adaptiveConfig.current();

        if (previous == null) {
            previousSummaries.put(tenantId, currentSummary);
            previousConfigs.put(tenantId, currentConfig);
            return;
        }

        double pressure = (double) currentSummary.lowRetentionCount() / currentSummary.totalScored();
        double previousPressure = previous.totalScored() > 0
                ? (double) previous.lowRetentionCount() / previous.totalScored()
                : 0.0;

        var prevConfig = previousConfigs.getOrDefault(tenantId, currentConfig);
        boolean previouslyAdjusted = prevConfig.retentionThreshold() != currentConfig.retentionThreshold();

        // If we adjusted last tick and pressure moved toward target range, hold
        if (previouslyAdjusted) {
            boolean improving = Math.abs(pressure - targetMidpoint()) < Math.abs(previousPressure - targetMidpoint());
            if (improving) {
                previousSummaries.put(tenantId, currentSummary);
                return;
            }
        }

        double newThreshold = currentConfig.retentionThreshold();
        HygieneAdaptationEvent.AdaptationDirection direction;

        if (pressure > config.evictionPressureUpperThreshold()) {
            newThreshold -= config.stepSize();
            direction = HygieneAdaptationEvent.AdaptationDirection.LOWERED;
        } else if (pressure < config.evictionPressureLowerThreshold()) {
            newThreshold += config.stepSize();
            direction = HygieneAdaptationEvent.AdaptationDirection.RAISED;
        } else {
            previousSummaries.put(tenantId, currentSummary);
            previousConfigs.put(tenantId, currentConfig);
            return;
        }

        newThreshold = Math.max(config.minRetentionThreshold(),
                       Math.min(config.maxRetentionThreshold(), newThreshold));

        if (newThreshold == currentConfig.retentionThreshold()) {
            previousSummaries.put(tenantId, currentSummary);
            previousConfigs.put(tenantId, currentConfig);
            return;
        }

        var newConfig = new RetentionConfig(newThreshold,
                currentConfig.confidenceWeight(), currentConfig.recencyWeight(),
                currentConfig.scopeWeight(), currentConfig.trustWeight());

        adaptiveConfig.update(newConfig);

        eventSink.accept(new HygieneAdaptationEvent(
                tenantId, currentConfig, newConfig, pressure, direction, Instant.now()));

        previousSummaries.put(tenantId, currentSummary);
        previousConfigs.put(tenantId, newConfig);
    }

    private double targetMidpoint() {
        return (config.evictionPressureUpperThreshold() + config.evictionPressureLowerThreshold()) / 2.0;
    }
}
```

- [ ] **Step 6: Run tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=HygieneAdaptationPhaseTest`
Expected: 7 tests PASS

- [ ] **Step 7: Run full module test suite**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition`
Expected: all tests PASS (no regressions)

- [ ] **Step 8: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/memory/HygieneAdaptationPhase.java \
       cognition/src/main/java/io/casehub/neocortex/cognition/memory/HygieneAdaptationConfig.java \
       cognition/src/main/java/io/casehub/neocortex/cognition/memory/HygieneAdaptationEvent.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/memory/HygieneAdaptationPhaseTest.java
git commit -m "feat(#1119): add HygieneAdaptationPhase consolidation phase

Single-parameter adaptation: adjusts retentionThreshold based on
eviction pressure ratio from KnowledgeGapSummary. Direction tracking
prevents oscillation. Config-gated via
casehub.cognition.hygiene-adaptation.enabled.

Refs #1119"
```

## Batch 3: Integration Verification + Documentation

### Task 4: Full build verification and pipeline defaults documentation

**Files:**
- Modify: `cognition/src/main/resources/cognitive-pipeline-defaults.properties`

**Interfaces:**
- Consumes: All types from Tasks 1-3

- [ ] **Step 1: Run full project build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -DskipTests`
Expected: BUILD SUCCESS across all modules. If any module fails to compile due to the `MemoryHygieneOrchestrator` SPI change or the `MemoryHygieneOrchestratorImpl` constructor change, fix by updating call sites to pass a `RetentionConfigSupplier`.

- [ ] **Step 2: Run full project tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test`
Expected: all tests PASS. Watch for failures in modules that construct `MemoryHygieneOrchestratorImpl` directly or mock `MemoryHygieneOrchestrator`.

- [ ] **Step 3: Document adaptation parameters in pipeline defaults**

Add to `cognition/src/main/resources/cognitive-pipeline-defaults.properties`:

```properties
# --- Hygiene Adaptation Phase ---
# Automatic retention threshold adaptation based on eviction pressure.
# Disabled by default; enable when memory hygiene is active and stable.
# casehub.cognition.hygiene-adaptation.enabled=false

# Eviction pressure ratio thresholds (lowRetentionCount / totalScored).
# Above upper: threshold too aggressive, lower it. Below lower: too lenient, raise it.
# casehub.cognition.hygiene-adaptation.eviction-pressure-upper-threshold=0.3
# casehub.cognition.hygiene-adaptation.eviction-pressure-lower-threshold=0.05

# Step size for threshold adjustment per consolidation tick.
# casehub.cognition.hygiene-adaptation.step-size=0.02

# Hard bounds for retention threshold.
# casehub.cognition.hygiene-adaptation.min-retention-threshold=0.01
# casehub.cognition.hygiene-adaptation.max-retention-threshold=0.5
```

- [ ] **Step 4: Commit**

```bash
git add cognition/src/main/resources/cognitive-pipeline-defaults.properties
git commit -m "docs(#1119): document hygiene adaptation parameters in pipeline defaults

Closes #1119"
```

## References

- [2026-11-18-hygiene-adaptation-phase-design.md] — design spec this plan implements
- [cognition-api/.../RetentionConfig.java:3] — immutable 5-field config record
- [cognition-api/.../MemoryHygieneOrchestrator.java:5] — SPI with tick() and knowledgeGaps()
- [cognition/.../MemoryHygieneOrchestratorImpl.java:28] — implementation with gapStates ConcurrentHashMap
- [cognition/.../CognitionDefaultBeans.java:349] — existing RetentionConfig producer
- [memory-api/.../KnowledgeGapSummary.java:3] — quality signal record
- [mindmap-intelligence/.../ConsolidationPhase.java:10] — phase SPI contract
- [cognition/.../MemoryHygieneOrchestratorImplTest.java:22] — existing test pattern
- [GitHub #1119] — parent issue
- [GitHub #1115] — migration that established the boundary

# Receive Memory Hygiene Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #1115 — Receive memory hygiene implementation from agency#447
**Issue group:** #1115

**Goal:** Bring the memory hygiene core (retention scoring, eviction, consolidation, integrity checking) from agency into neocortex's cognition module, implementing the existing MemoryHygieneOrchestrator SPI.

**Architecture:** Value types and SPIs go in cognition-api's existing `io.casehub.neocortex.cognition.memory` package alongside the existing MemoryHygieneOrchestrator interface. Implementations go in cognition's matching package. The orchestrator adapts agency's concrete class to use neocortex-native infrastructure: ConsolidationSummariser SPI replaces agency's ContentSummariser, inline ReentrantLock replaces agency's KeyedLock, and existing ReflectionOrchestrator replaces agency's ReflectionStore.

**Tech Stack:** Java 21, Quarkus CDI (DefaultBean, ApplicationScoped), JUnit 5 + AssertJ + Mockito

## Global Constraints

- Java 21 source (runs on Java 26 JVM)
- Build: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install`
- Use `mvn` not `./mvnw`
- All new types in package `io.casehub.neocortex.cognition.memory`
- Every commit references issue: `Refs casehubio/neocortex#1115`

---

## Batch 1: Value types and SPIs in cognition-api

### Task 1: Value types — RetentionConfig, RetentionScore, ViolationType, IntegrityViolation, HygieneEvent, HygieneTick, MaintenanceTick

**Files:**
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/RetentionConfig.java`
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/RetentionScore.java`
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/ViolationType.java`
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/IntegrityViolation.java`
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/HygieneEvent.java`
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/HygieneTick.java`
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/MaintenanceTick.java`
- Test: `cognition-api/src/test/java/io/casehub/neocortex/cognition/memory/FoundationTypesTest.java`

**Interfaces:**
- Consumes: nothing (pure value types)
- Produces: `RetentionConfig` record with `DEFAULT` constant and `retentionThreshold()`, `confidenceWeight()`, `recencyWeight()`, `scopeWeight()`, `trustWeight()` accessors; `RetentionScore` record with `compute(String caseId, String entityId, double confidence, double recencyFactor, double scopeFactor, double trustFactor, RetentionConfig config)` static factory; `ViolationType` enum with 5 values; `IntegrityViolation(String caseId, ViolationType type, String detail, boolean escalateToSemantic)` record; `HygieneEvent` sealed interface with `MemoryEvicted`, `MemoryConsolidated`, `ReflectionGenerated`, `IntegrityViolationDetected` variants; `HygieneTick` sealed interface with `Idle`, `Completed`, `Failed` variants; `MaintenanceTick` sealed interface with `Completed`, `Failed` variants.

- [ ] **Step 1: Write FoundationTypesTest**

Create the test file. This covers all 7 value types — RetentionConfig validation, RetentionScore weighted-mean computation, sealed variant construction, IntegrityViolation null rejection, ViolationType enum coverage.

```java
package io.casehub.neocortex.cognition.memory;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FoundationTypesTest {

    @Test
    void retentionScoreComputation() {
        var config = new RetentionConfig(0.1, 1.0, 1.0, 0.5, 0.5);
        var score = RetentionScore.compute("c1", "e1", 0.8, 0.6, 1.0, 0.9, config);
        assertThat(score.composite()).isBetween(0.0, 1.0);
        assertThat(score.caseId()).isEqualTo("c1");
        assertThat(score.entityId()).isEqualTo("e1");
        assertThat(score.confidence()).isEqualTo(0.8);
        assertThat(score.recencyFactor()).isEqualTo(0.6);
    }

    @Test
    void retentionScoreWeightedMean() {
        var config = new RetentionConfig(0.1, 2.0, 1.0, 0.0, 0.0);
        var score = RetentionScore.compute("c1", "e1", 0.6, 0.3, 0.5, 0.5, config);
        assertThat(score.composite()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void retentionConfigValidatesAllWeightsZero() {
        assertThatThrownBy(() -> new RetentionConfig(0.1, 0.0, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retentionConfigRejectsNegativeThreshold() {
        assertThatThrownBy(() -> new RetentionConfig(-0.1, 1.0, 1.0, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retentionConfigRejectsThresholdAboveOne() {
        assertThatThrownBy(() -> new RetentionConfig(1.1, 1.0, 1.0, 0.5, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retentionConfigDefaultHasExpectedValues() {
        assertThat(RetentionConfig.DEFAULT.retentionThreshold()).isEqualTo(0.1);
        assertThat(RetentionConfig.DEFAULT.confidenceWeight()).isEqualTo(1.0);
    }

    @Test
    void hygieneTickSealedVariants() {
        HygieneTick idle = new HygieneTick.Idle("no memories");
        HygieneTick completed = new HygieneTick.Completed(2, 3, 10, List.of());
        HygieneTick failed = new HygieneTick.Failed("store error");
        assertThat(idle).isInstanceOf(HygieneTick.Idle.class);
        assertThat(completed).isInstanceOf(HygieneTick.Completed.class);
        assertThat(failed).isInstanceOf(HygieneTick.Failed.class);
        assertThat(((HygieneTick.Completed) completed).consolidated()).isEqualTo(2);
        assertThat(((HygieneTick.Completed) completed).evicted()).isEqualTo(3);
        assertThat(((HygieneTick.Completed) completed).totalScored()).isEqualTo(10);
    }

    @Test
    void maintenanceTickSealedVariants() {
        var hygiene = new HygieneTick.Completed(0, 0, 5, List.of());
        var completed = new MaintenanceTick.Completed(hygiene, 3, 1, List.of());
        var failed = new MaintenanceTick.Failed("reflection", "timeout");
        assertThat(completed.hygiene()).isEqualTo(hygiene);
        assertThat(completed.reflectionsGenerated()).isEqualTo(3);
        assertThat(completed).isInstanceOf(MaintenanceTick.class);
        assertThat(failed.stage()).isEqualTo("reflection");
        assertThat(failed.reason()).isEqualTo("timeout");
    }

    @Test
    void hygieneEventSealedVariants() {
        var score = RetentionScore.compute("c1", "e1", 0.1, 0.1, 0.1, 0.1,
                new RetentionConfig(0.5, 1.0, 1.0, 0.5, 0.5));
        HygieneEvent evicted = new HygieneEvent.MemoryEvicted("c1", score);
        HygieneEvent consolidated = new HygieneEvent.MemoryConsolidated("m1", List.of("s1", "s2"));
        HygieneEvent reflected = new HygieneEvent.ReflectionGenerated("a1", "insight");
        var violation = new IntegrityViolation("c1", ViolationType.ORPHANED_SUPERSESSION, "detail", false);
        HygieneEvent detected = new HygieneEvent.IntegrityViolationDetected(violation);
        assertThat(evicted).isInstanceOf(HygieneEvent.MemoryEvicted.class);
        assertThat(consolidated).isInstanceOf(HygieneEvent.MemoryConsolidated.class);
        assertThat(reflected).isInstanceOf(HygieneEvent.ReflectionGenerated.class);
        assertThat(detected).isInstanceOf(HygieneEvent.IntegrityViolationDetected.class);
    }

    @Test
    void violationTypeCoversAllExpected() {
        assertThat(ViolationType.values()).containsExactlyInAnyOrder(
                ViolationType.ORPHANED_SUPERSESSION,
                ViolationType.DUPLICATE_CASE,
                ViolationType.MISSING_FEATURES,
                ViolationType.UNPROCESSED_STALE,
                ViolationType.SEMANTIC_CONFLICT);
    }

    @Test
    void integrityViolationRejectsNulls() {
        assertThatThrownBy(() -> new IntegrityViolation(null, ViolationType.DUPLICATE_CASE, "d", false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IntegrityViolation("c1", null, "d", false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IntegrityViolation("c1", ViolationType.DUPLICATE_CASE, null, false))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition-api -Dtest=FoundationTypesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure — types do not exist yet.

- [ ] **Step 3: Create all 7 value types**

Create `ViolationType.java`:
```java
package io.casehub.neocortex.cognition.memory;

public enum ViolationType {
    ORPHANED_SUPERSESSION,
    DUPLICATE_CASE,
    MISSING_FEATURES,
    UNPROCESSED_STALE,
    SEMANTIC_CONFLICT
}
```

Create `IntegrityViolation.java`:
```java
package io.casehub.neocortex.cognition.memory;

import java.util.Objects;

public record IntegrityViolation(String caseId, ViolationType type,
                                  String detail, boolean escalateToSemantic) {
    public IntegrityViolation {
        Objects.requireNonNull(caseId, "caseId required");
        Objects.requireNonNull(type, "type required");
        Objects.requireNonNull(detail, "detail required");
    }
}
```

Create `RetentionConfig.java`:
```java
package io.casehub.neocortex.cognition.memory;

public record RetentionConfig(double retentionThreshold,
                              double confidenceWeight, double recencyWeight,
                              double scopeWeight, double trustWeight) {
    public RetentionConfig {
        if (retentionThreshold < 0.0 || retentionThreshold > 1.0)
            throw new IllegalArgumentException("retentionThreshold must be in [0,1], got " + retentionThreshold);
        if (confidenceWeight < 0 || recencyWeight < 0 || scopeWeight < 0 || trustWeight < 0)
            throw new IllegalArgumentException("weights must be >= 0");
        if (confidenceWeight + recencyWeight + scopeWeight + trustWeight <= 0)
            throw new IllegalArgumentException("at least one weight must be > 0");
    }

    public static final RetentionConfig DEFAULT =
            new RetentionConfig(0.1, 1.0, 1.0, 0.5, 0.5);
}
```

Create `RetentionScore.java`:
```java
package io.casehub.neocortex.cognition.memory;

import java.util.Objects;

public record RetentionScore(String caseId, String entityId,
                             double confidence, double recencyFactor,
                             double scopeFactor, double trustFactor,
                             double composite) {
    public RetentionScore {
        Objects.requireNonNull(caseId, "caseId required");
        Objects.requireNonNull(entityId, "entityId required");
    }

    public static RetentionScore compute(String caseId, String entityId,
                                         double confidence, double recencyFactor,
                                         double scopeFactor, double trustFactor,
                                         RetentionConfig config) {
        double num = confidence * config.confidenceWeight()
                   + recencyFactor * config.recencyWeight()
                   + scopeFactor * config.scopeWeight()
                   + trustFactor * config.trustWeight();
        double den = config.confidenceWeight() + config.recencyWeight()
                   + config.scopeWeight() + config.trustWeight();
        double composite = Math.clamp(num / den, 0.0, 1.0);
        return new RetentionScore(caseId, entityId, confidence,
                                  recencyFactor, scopeFactor, trustFactor, composite);
    }
}
```

Create `HygieneEvent.java`:
```java
package io.casehub.neocortex.cognition.memory;

import java.util.List;

public sealed interface HygieneEvent {
    record MemoryEvicted(String caseId, RetentionScore score) implements HygieneEvent {}
    record MemoryConsolidated(String mergedCaseId,
                               List<String> sourceCaseIds) implements HygieneEvent {
        public MemoryConsolidated { sourceCaseIds = List.copyOf(sourceCaseIds); }
    }
    record ReflectionGenerated(String agentId, String insight) implements HygieneEvent {}
    record IntegrityViolationDetected(IntegrityViolation violation) implements HygieneEvent {}
}
```

Create `HygieneTick.java`:
```java
package io.casehub.neocortex.cognition.memory;

import java.util.List;

public sealed interface HygieneTick {
    record Idle(String reason) implements HygieneTick {}
    record Completed(int consolidated, int evicted, int totalScored,
                     List<RetentionScore> scores) implements HygieneTick {
        public Completed { scores = List.copyOf(scores); }
    }
    record Failed(String reason) implements HygieneTick {}
}
```

Create `MaintenanceTick.java`:
```java
package io.casehub.neocortex.cognition.memory;

import java.util.List;

public sealed interface MaintenanceTick {
    record Completed(HygieneTick hygiene, int reflectionsGenerated,
                     int crossLinksCreated,
                     List<IntegrityViolation> violations) implements MaintenanceTick {
        public Completed { violations = List.copyOf(violations); }
    }
    record Failed(String stage, String reason) implements MaintenanceTick {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition-api -Dtest=FoundationTypesTest`
Expected: all 11 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/RetentionConfig.java \
       cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/RetentionScore.java \
       cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/ViolationType.java \
       cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/IntegrityViolation.java \
       cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/HygieneEvent.java \
       cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/HygieneTick.java \
       cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/MaintenanceTick.java \
       cognition-api/src/test/java/io/casehub/neocortex/cognition/memory/FoundationTypesTest.java
git commit -m "feat: add memory hygiene value types to cognition-api

RetentionConfig, RetentionScore, ViolationType, IntegrityViolation,
HygieneEvent, HygieneTick, MaintenanceTick — all sealed/record types
migrated from agency social/memory.

Refs casehubio/neocortex#1115"
```

### Task 2: SPIs — ConfidenceScorer, ConsolidationSummariser, IntegrityChecker, SemanticIntegrityChecker

**Files:**
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/ConfidenceScorer.java`
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/ConsolidationSummariser.java`
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/IntegrityChecker.java`
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/SemanticIntegrityChecker.java`

**Interfaces:**
- Consumes: `IntegrityViolation`, `ViolationType` (from Task 1)
- Produces: `ConfidenceScorer` `@FunctionalInterface` with `double score(CbrMatch<? extends CbrRecord> memory, Instant now)`; `ConsolidationSummariser` `@FunctionalInterface` with `String summarise(List<String> texts)`; `IntegrityChecker` `@FunctionalInterface` with `List<IntegrityViolation> check(String agentId, String tenantId, MemoryDomain domain)`; `SemanticIntegrityChecker` `@FunctionalInterface` with `List<IntegrityViolation> checkSemantic(List<IntegrityViolation> flagged, String agentId, String tenantId)`

- [ ] **Step 1: Create ConfidenceScorer**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import java.time.Instant;

@FunctionalInterface
public interface ConfidenceScorer {
    double score(CbrMatch<? extends CbrRecord> memory, Instant now);
}
```

- [ ] **Step 2: Create ConsolidationSummariser**

```java
package io.casehub.neocortex.cognition.memory;

import java.util.List;

@FunctionalInterface
public interface ConsolidationSummariser {
    String summarise(List<String> texts);
}
```

- [ ] **Step 3: Create IntegrityChecker**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import java.util.List;

@FunctionalInterface
public interface IntegrityChecker {
    List<IntegrityViolation> check(String agentId, String tenantId, MemoryDomain domain);
}
```

- [ ] **Step 4: Create SemanticIntegrityChecker**

```java
package io.casehub.neocortex.cognition.memory;

import java.util.List;

@FunctionalInterface
public interface SemanticIntegrityChecker {
    List<IntegrityViolation> checkSemantic(List<IntegrityViolation> flagged,
                                            String agentId, String tenantId);
}
```

- [ ] **Step 5: Verify cognition-api compiles**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl cognition-api`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/ConfidenceScorer.java \
       cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/ConsolidationSummariser.java \
       cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/IntegrityChecker.java \
       cognition-api/src/main/java/io/casehub/neocortex/cognition/memory/SemanticIntegrityChecker.java
git commit -m "feat: add memory hygiene SPIs to cognition-api

ConfidenceScorer, ConsolidationSummariser (new — replaces agency
ContentSummariser), IntegrityChecker, SemanticIntegrityChecker.

Refs casehubio/neocortex#1115"
```

---

## Batch 2: Scorer and integrity implementations in cognition

### Task 3: Confidence scorers — ArousalScorer, SurpriseScorer, CompositeConfidenceScorer, WeightedScorer

**Files:**
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/WeightedScorer.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/ArousalScorer.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/SurpriseScorer.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/CompositeConfidenceScorer.java`
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/memory/ConfidenceScorerTest.java`

**Interfaces:**
- Consumes: `ConfidenceScorer` (from Task 2); `ContentScorer`, `ScoreableContent` (from memory-api — existing)
- Produces: `WeightedScorer(ConfidenceScorer scorer, double weight)` record; `ArousalScorer` implementing `ContentScorer` + `ConfidenceScorer`; `SurpriseScorer` implementing `ContentScorer` + `ConfidenceScorer` with `static ScoreableContent toScoreableContent(CbrMatch, Instant)`; `CompositeConfidenceScorer(List<WeightedScorer>)` implementing `ConfidenceScorer`

- [ ] **Step 1: Write ConfidenceScorerTest**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.experience.ScoreableContent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfidenceScorerTest {

    private static CbrMatch<CbrRecord> scored(String problem, String solution,
                                                  Map<String, FeatureValue> features) {
        var c = new CbrFeatureRecord(problem, solution, null, null, features, null, null);
        return new CbrMatch<>(c, "case-1", 0.5);
    }

    @Test
    void arousalScorerReturnsLowForNeutralText() {
        var scorer = new ArousalScorer();
        var memory = scored("routine update completed", "nothing notable happened", Map.of());
        assertThat(scorer.score(memory, Instant.now())).isBetween(0.0, 0.3);
    }

    @Test
    void arousalScorerReturnsHighForEmotionalText() {
        var scorer = new ArousalScorer();
        var memory = scored("critical emergency failure detected", "urgent crisis escalation required", Map.of());
        assertThat(scorer.score(memory, Instant.now())).isGreaterThan(0.3);
    }

    @Test
    void surpriseScorerReturnsBoundedValue() {
        var scorer = new SurpriseScorer();
        var memory = scored("task", "solution", Map.of("type", FeatureValue.string("routine")));
        assertThat(scorer.score(memory, Instant.now())).isBetween(0.0, 1.0);
    }

    @Test
    void surpriseScorerReturnsDefaultForEmptyFeatures() {
        var scorer = new SurpriseScorer();
        var memory = scored("task", "solution", Map.of());
        assertThat(scorer.score(memory, Instant.now())).isEqualTo(0.5);
    }

    @Test
    void compositeWeightedMean() {
        ConfidenceScorer fixed80 = (m, now) -> 0.8;
        ConfidenceScorer fixed20 = (m, now) -> 0.2;
        var composite = new CompositeConfidenceScorer(List.of(
                new WeightedScorer(fixed80, 3.0),
                new WeightedScorer(fixed20, 1.0)));
        var memory = scored("p", "s", Map.of("k", FeatureValue.string("v")));
        assertThat(composite.score(memory, Instant.now()))
                .isCloseTo(0.65, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void compositeRejectsEmptyList() {
        assertThatThrownBy(() -> new CompositeConfidenceScorer(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weightedScorerRejectsZeroWeight() {
        assertThatThrownBy(() -> new WeightedScorer((m, now) -> 0.5, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arousalScorer_contentScorer_noHighArousalWords() {
        var scorer = new ArousalScorer();
        var content = new ScoreableContent("a peaceful calm day", Map.of(), Instant.EPOCH);
        assertThat(scorer.score(content)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void arousalScorer_contentScorer_withHighArousalWords() {
        var scorer = new ArousalScorer();
        var content = new ScoreableContent("critical emergency alert failure", Map.of(), Instant.EPOCH);
        assertThat(scorer.score(content)).isGreaterThan(0.0);
    }

    @Test
    void dualInterface_arousalScorer_consistency() {
        var scorer = new ArousalScorer();
        var cbrCase = new CbrFeatureRecord("critical error", "urgent fix", null, null, Map.of(), null, null);
        var cbrScored = new CbrMatch<CbrRecord>(cbrCase, "case-1", 0.5);
        double cbrScore = scorer.score(cbrScored, Instant.now());
        var content = new ScoreableContent("critical error urgent fix", Map.of(), Instant.EPOCH);
        double contentScore = scorer.score(content);
        assertThat(cbrScore).isCloseTo(contentScore, org.assertj.core.data.Offset.offset(0.001));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=io.casehub.neocortex.cognition.memory.ConfidenceScorerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure.

- [ ] **Step 3: Create WeightedScorer**

```java
package io.casehub.neocortex.cognition.memory;

import java.util.Objects;

public record WeightedScorer(ConfidenceScorer scorer, double weight) {
    public WeightedScorer {
        Objects.requireNonNull(scorer, "scorer required");
        if (weight <= 0.0)
            throw new IllegalArgumentException("weight must be positive, got " + weight);
    }
}
```

- [ ] **Step 4: Create SurpriseScorer**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.experience.ContentScorer;
import io.casehub.neocortex.memory.experience.ScoreableContent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class SurpriseScorer implements ContentScorer, ConfidenceScorer {

    @Override
    public double score(ScoreableContent content) {
        Map<String, String> metadata = content.metadata();
        if (metadata == null || metadata.isEmpty()) return 0.5;
        int distinctValues = 0;
        for (var value : metadata.values()) {
            distinctValues += value.length();
        }
        return Math.clamp(Math.log1p(distinctValues) / 10.0, 0.0, 1.0);
    }

    @Override
    public double score(CbrMatch<? extends CbrRecord> memory, Instant now) {
        return score(toScoreableContent(memory, now));
    }

    static ScoreableContent toScoreableContent(CbrMatch<? extends CbrRecord> memory, Instant now) {
        CbrRecord c = memory.cbrRecord();
        String text = c.problem();
        if (c.solution() != null) text = text + " " + c.solution();
        Map<String, String> metadata = new HashMap<>();
        if (c.features() != null) {
            for (var entry : c.features().entrySet()) {
                metadata.put(entry.getKey(), featureValueToString(entry.getValue()));
            }
        }
        Instant timestamp = memory.storedAt() != null ? memory.storedAt() : now;
        return new ScoreableContent(text != null ? text : "", metadata, timestamp);
    }

    private static String featureValueToString(FeatureValue fv) {
        if (fv instanceof FeatureValue.StringVal sv) return sv.value();
        if (fv instanceof FeatureValue.NumberVal nv) return String.valueOf(nv.value());
        if (fv instanceof FeatureValue.StringListVal sl) return String.join(",", sl.values());
        return fv.toString();
    }
}
```

- [ ] **Step 5: Create ArousalScorer**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.experience.ContentScorer;
import io.casehub.neocortex.memory.experience.ScoreableContent;
import java.time.Instant;
import java.util.Set;

public final class ArousalScorer implements ContentScorer, ConfidenceScorer {

    private static final Set<String> HIGH_AROUSAL = Set.of(
            "critical", "emergency", "urgent", "failure", "crisis", "error",
            "escalation", "breach", "violation", "fatal", "severe", "alarm",
            "panic", "catastrophe", "danger", "threat", "attack", "outage",
            "incident", "alert", "warning", "shutdown", "corrupt", "exploit");

    @Override
    public double score(ScoreableContent content) {
        var text = content.text().toLowerCase();
        var words = text.split("\\W+");
        if (words.length == 0) return 0.0;
        int hits = 0;
        for (var word : words) {
            if (HIGH_AROUSAL.contains(word)) hits++;
        }
        return Math.clamp((double) hits / words.length * 5.0, 0.0, 1.0);
    }

    @Override
    public double score(CbrMatch<? extends CbrRecord> memory, Instant now) {
        return score(SurpriseScorer.toScoreableContent(memory, now));
    }
}
```

- [ ] **Step 6: Create CompositeConfidenceScorer**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class CompositeConfidenceScorer implements ConfidenceScorer {

    private final List<WeightedScorer> scorers;

    public CompositeConfidenceScorer(List<WeightedScorer> scorers) {
        Objects.requireNonNull(scorers, "scorers required");
        if (scorers.isEmpty())
            throw new IllegalArgumentException("at least one scorer required");
        this.scorers = List.copyOf(scorers);
    }

    @Override
    public double score(CbrMatch<? extends CbrRecord> memory, Instant now) {
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        for (var ws : scorers) {
            weightedSum += ws.scorer().score(memory, now) * ws.weight();
            totalWeight += ws.weight();
        }
        return Math.clamp(weightedSum / totalWeight, 0.0, 1.0);
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=io.casehub.neocortex.cognition.memory.ConfidenceScorerTest`
Expected: all 10 tests PASS.

- [ ] **Step 8: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/memory/WeightedScorer.java \
       cognition/src/main/java/io/casehub/neocortex/cognition/memory/ArousalScorer.java \
       cognition/src/main/java/io/casehub/neocortex/cognition/memory/SurpriseScorer.java \
       cognition/src/main/java/io/casehub/neocortex/cognition/memory/CompositeConfidenceScorer.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/memory/ConfidenceScorerTest.java
git commit -m "feat: add confidence scorers for memory hygiene

ArousalScorer, SurpriseScorer (dual ContentScorer + ConfidenceScorer),
CompositeConfidenceScorer, WeightedScorer. Migrated from agency
social/memory.

Refs casehubio/neocortex#1115"
```

### Task 4: Integrity checkers — DefaultIntegrityChecker, NoOpSemanticIntegrityChecker, NoOpConsolidationSummariser

**Files:**
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/DefaultIntegrityChecker.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/NoOpSemanticIntegrityChecker.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/NoOpConsolidationSummariser.java`
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/memory/DefaultIntegrityCheckerTest.java`

**Interfaces:**
- Consumes: `IntegrityChecker`, `SemanticIntegrityChecker`, `ConsolidationSummariser` (from Task 2); `IntegrityViolation`, `ViolationType` (from Task 1); `CbrRecordStore`, `SupersessionStatus` (from memory-api — existing)
- Produces: `DefaultIntegrityChecker(CbrRecordStore, SemanticIntegrityChecker, List<String> caseTypes)` implementing `IntegrityChecker`; `NoOpSemanticIntegrityChecker` `@DefaultBean`; `NoOpConsolidationSummariser` `@DefaultBean`

- [ ] **Step 1: Write DefaultIntegrityCheckerTest**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.SupersessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultIntegrityCheckerTest {

    private CbrRecordStore store;
    private SemanticIntegrityChecker semanticChecker;
    private DefaultIntegrityChecker checker;
    private static final MemoryDomain DOMAIN = new MemoryDomain("agent");

    @BeforeEach
    void setUp() {
        store = mock(CbrRecordStore.class);
        semanticChecker = mock(SemanticIntegrityChecker.class);
        when(semanticChecker.checkSemantic(any(), any(), any())).thenReturn(List.of());
        checker = new DefaultIntegrityChecker(store, semanticChecker, List.of("test-case"));
    }

    @Test
    void detectsOrphanedSupersession() {
        var status = new SupersessionStatus("case-1", true, Instant.now(),
                "missing-case", "consolidation", null);
        when(store.findSupersededCases(DOMAIN, "tenant-1")).thenReturn(List.of(status));
        when(store.getSupersessionStatus("missing-case", "tenant-1"))
                .thenReturn(SupersessionStatus.NOT_SUPERSEDED);

        var violations = checker.check("agent-1", "tenant-1", DOMAIN);
        assertThat(violations).anyMatch(v -> v.type() == ViolationType.ORPHANED_SUPERSESSION);
    }

    @Test
    void noViolationsWhenSupersessionChainIsValid() {
        var status = new SupersessionStatus("case-1", true, Instant.now(),
                "case-2", "consolidation", null);
        when(store.findSupersededCases(DOMAIN, "tenant-1")).thenReturn(List.of(status));
        when(store.getSupersessionStatus("case-2", "tenant-1"))
                .thenReturn(new SupersessionStatus("case-2", false, null, null, null, null));

        var violations = checker.check("agent-1", "tenant-1", DOMAIN);
        assertThat(violations).noneMatch(v -> v.type() == ViolationType.ORPHANED_SUPERSESSION);
    }

    @Test
    void returnsEmptyWhenNoIssues() {
        when(store.findSupersededCases(DOMAIN, "tenant-1")).thenReturn(List.of());
        var violations = checker.check("agent-1", "tenant-1", DOMAIN);
        assertThat(violations).isEmpty();
    }

    @Test
    void delegatesToSemanticCheckerForFlaggedItems() {
        var status = new SupersessionStatus("case-1", true, Instant.now(),
                "missing-case", "consolidation", null);
        when(store.findSupersededCases(DOMAIN, "tenant-1")).thenReturn(List.of(status));
        when(store.getSupersessionStatus("missing-case", "tenant-1"))
                .thenReturn(SupersessionStatus.NOT_SUPERSEDED);
        var semanticViolation = new IntegrityViolation("case-1",
                ViolationType.SEMANTIC_CONFLICT, "contradictory", false);
        when(semanticChecker.checkSemantic(any(), any(), any()))
                .thenReturn(List.of(semanticViolation));

        var violations = checker.check("agent-1", "tenant-1", DOMAIN);
        assertThat(violations).anyMatch(v -> v.type() == ViolationType.SEMANTIC_CONFLICT);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=io.casehub.neocortex.cognition.memory.DefaultIntegrityCheckerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure.

- [ ] **Step 3: Create DefaultIntegrityChecker**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.SupersessionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultIntegrityChecker implements IntegrityChecker {

    private static final Logger LOG = Logger.getLogger(DefaultIntegrityChecker.class.getName());

    private final CbrRecordStore store;
    private final SemanticIntegrityChecker semanticChecker;
    private final List<String> caseTypes;

    public DefaultIntegrityChecker(CbrRecordStore store,
                                    SemanticIntegrityChecker semanticChecker,
                                    List<String> caseTypes) {
        this.store = store;
        this.semanticChecker = semanticChecker;
        this.caseTypes = List.copyOf(caseTypes);
    }

    @Override
    public List<IntegrityViolation> check(String agentId, String tenantId, MemoryDomain domain) {
        var violations = new ArrayList<IntegrityViolation>();
        checkOrphanedSupersessions(tenantId, domain, violations);
        var flagged = violations.stream()
                .filter(IntegrityViolation::escalateToSemantic)
                .toList();
        if (!flagged.isEmpty()) {
            var semanticResults = semanticChecker.checkSemantic(flagged, agentId, tenantId);
            violations.addAll(semanticResults);
        }
        return List.copyOf(violations);
    }

    private void checkOrphanedSupersessions(String tenantId, MemoryDomain domain,
                                             List<IntegrityViolation> violations) {
        try {
            var superseded = store.findSupersededCases(domain, tenantId);
            for (var status : superseded) {
                if (status.supersedingCaseId() == null) continue;
                try {
                    var target = store.getSupersessionStatus(
                            status.supersedingCaseId(), tenantId);
                    if (target == SupersessionStatus.NOT_SUPERSEDED
                            && status.supersedingCaseId() != null) {
                        violations.add(new IntegrityViolation(
                                status.caseId(),
                                ViolationType.ORPHANED_SUPERSESSION,
                                "superseding case " + status.supersedingCaseId() + " not found",
                                true));
                    }
                } catch (Exception e) {
                    violations.add(new IntegrityViolation(
                            status.caseId(),
                            ViolationType.ORPHANED_SUPERSESSION,
                            "failed to verify superseding case: " + e.getMessage(),
                            true));
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to check supersessions", e);
        }
    }
}
```

- [ ] **Step 4: Create NoOpSemanticIntegrityChecker and NoOpConsolidationSummariser**

```java
// NoOpSemanticIntegrityChecker.java
package io.casehub.neocortex.cognition.memory;

import java.util.List;

public class NoOpSemanticIntegrityChecker implements SemanticIntegrityChecker {
    @Override
    public List<IntegrityViolation> checkSemantic(List<IntegrityViolation> flagged,
                                                    String agentId, String tenantId) {
        return List.of();
    }
}

// NoOpConsolidationSummariser.java
package io.casehub.neocortex.cognition.memory;

import java.util.List;

public class NoOpConsolidationSummariser implements ConsolidationSummariser {
    @Override
    public String summarise(List<String> texts) {
        return "";
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=io.casehub.neocortex.cognition.memory.DefaultIntegrityCheckerTest`
Expected: all 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/memory/DefaultIntegrityChecker.java \
       cognition/src/main/java/io/casehub/neocortex/cognition/memory/NoOpSemanticIntegrityChecker.java \
       cognition/src/main/java/io/casehub/neocortex/cognition/memory/NoOpConsolidationSummariser.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/memory/DefaultIntegrityCheckerTest.java
git commit -m "feat: add integrity checkers and no-op default beans

DefaultIntegrityChecker (orphaned supersession detection),
NoOpSemanticIntegrityChecker, NoOpConsolidationSummariser.

Refs casehubio/neocortex#1115"
```

---

## Batch 3: Orchestrator, scheduler, and CDI wiring

### Task 5: MemoryHygieneOrchestratorImpl and MemoryHygieneScheduler

**Files:**
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/MemoryHygieneOrchestratorImpl.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/memory/MemoryHygieneScheduler.java`
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/memory/MemoryHygieneOrchestratorImplTest.java`
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/memory/MemoryHygieneSchedulerTest.java`

**Interfaces:**
- Consumes: `MemoryHygieneOrchestrator` SPI (from cognition-api — existing); `ConfidenceScorer`, `ConsolidationSummariser`, `IntegrityChecker`, `RetentionConfig`, `RetentionScore`, `HygieneEvent`, `HygieneTick`, `MaintenanceTick` (from Tasks 1-2); `CbrRecordStore`, `CbrMatch`, `CbrQuery`, `CbrFeatureRecord`, `FeatureValue`, `TemporalDecay`, `ScopeDecay`, `EraseRequest`, `MemoryDomain`, `KnowledgeGapSummary` (from memory-api — existing); `ReflectionOrchestrator` (from memory-api — existing)
- Produces: `MemoryHygieneOrchestratorImpl` implementing `MemoryHygieneOrchestrator` with `HygieneTick tick(String agentId, String tenantId)` (internal), `void tick(String, String)` (SPI), `KnowledgeGapSummary knowledgeGaps(String, String)` (SPI); `MemoryHygieneScheduler` with `MaintenanceTick maintain(String agentId, String tenantId)`

- [ ] **Step 1: Write MemoryHygieneOrchestratorImplTest**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.TemporalDecay;
import io.casehub.neocortex.memory.cbr.ScopeDecay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryHygieneOrchestratorImplTest {

    private CbrRecordStore store;
    private ConfidenceScorer scorer;
    @SuppressWarnings("unchecked")
    private Consumer<HygieneEvent> eventSink = mock(Consumer.class);
    private MemoryHygieneOrchestratorImpl orchestrator;

    private static final MemoryDomain DOMAIN = new MemoryDomain("agent");
    private static final RetentionConfig RETENTION = new RetentionConfig(0.5, 1.0, 1.0, 0.0, 0.0);

    @BeforeEach
    void setUp() {
        store = mock(CbrRecordStore.class);
        scorer = mock(ConfidenceScorer.class);
        var summariser = new NoOpConsolidationSummariser();
        orchestrator = new MemoryHygieneOrchestratorImpl(
                store, scorer,
                new TemporalDecay.HalfLife(Duration.ofDays(30)),
                new ScopeDecay.Step(0.5),
                summariser, DOMAIN, List.of("test-case"),
                RETENTION, 100, 0.7, eventSink);
    }

    private CbrMatch<CbrRecord> makeMemory(String caseId, String agentId, Instant storedAt) {
        var cbrCase = new CbrFeatureRecord("problem text", "solution text", null, null,
                Map.of("k", FeatureValue.string("v")), null, agentId);
        return new CbrMatch<>(cbrCase, caseId, "memory", 0.5, false,
                Map.of(), storedAt, io.casehub.platform.api.path.Path.root(), null);
    }

    @Test
    void tickReturnsIdleWhenNoMemories() {
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of());
        orchestrator.tick("agent-1", "tenant-1");
        assertThat(orchestrator.knowledgeGaps("agent-1", "tenant-1").totalMemories()).isEqualTo(0);
    }

    @Test
    void tickEvictsLowScoringMemories() {
        var memory = makeMemory("case-1", "agent-1", Instant.now().minus(Duration.ofDays(365)));
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of(memory));
        when(scorer.score(any(), any())).thenReturn(0.1);

        orchestrator.tick("agent-1", "tenant-1");
        verify(store).erase(any());
        verify(eventSink).accept(any(HygieneEvent.MemoryEvicted.class));
    }

    @Test
    void tickRetainsHighScoringMemories() {
        var memory = makeMemory("case-1", "agent-1", Instant.now());
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of(memory));
        when(scorer.score(any(), any())).thenReturn(0.9);

        orchestrator.tick("agent-1", "tenant-1");
        verify(store, never()).erase(any());
    }

    @Test
    void tickFiltersMemoriesByAgentId() {
        var ownMemory = makeMemory("case-1", "agent-1", Instant.now());
        var otherMemory = makeMemory("case-2", "other-agent", Instant.now());
        when(store.retrieveSimilar(any(), any())).thenReturn(List.of(ownMemory, otherMemory));
        when(scorer.score(any(), any())).thenReturn(0.9);

        orchestrator.tick("agent-1", "tenant-1");
        var gaps = orchestrator.knowledgeGaps("agent-1", "tenant-1");
        assertThat(gaps.totalMemories()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Write MemoryHygieneSchedulerTest**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MemoryHygieneSchedulerTest {

    private MemoryHygieneOrchestratorImpl orchestrator;
    private ReflectionOrchestrator reflectionOrchestrator;
    private IntegrityChecker integrityChecker;
    @SuppressWarnings("unchecked")
    private Consumer<HygieneEvent> eventSink = mock(Consumer.class);
    private MemoryHygieneScheduler scheduler;

    private static final MemoryDomain DOMAIN = new MemoryDomain("agent");

    @BeforeEach
    void setUp() {
        orchestrator = mock(MemoryHygieneOrchestratorImpl.class);
        reflectionOrchestrator = mock(ReflectionOrchestrator.class);
        integrityChecker = mock(IntegrityChecker.class);
        scheduler = new MemoryHygieneScheduler(
                orchestrator, reflectionOrchestrator,
                integrityChecker, DOMAIN, 50, eventSink);
    }

    @Test
    void maintainComposesAllStages() {
        var tick = new HygieneTick.Completed(0, 1, 5, List.of());
        when(orchestrator.doTick("agent-1", "tenant-1")).thenReturn(tick);
        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenReturn(List.of("insight one"));
        when(integrityChecker.check(anyString(), anyString(), any(MemoryDomain.class)))
                .thenReturn(List.of());

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Completed.class);
        var completed = (MaintenanceTick.Completed) result;
        assertThat(completed.reflectionsGenerated()).isEqualTo(1);
        assertThat(completed.hygiene()).isEqualTo(tick);
    }

    @Test
    void maintainReturnsFailedOnReflectionError() {
        when(orchestrator.doTick(anyString(), anyString()))
                .thenReturn(new HygieneTick.Completed(0, 0, 0, List.of()));
        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("reflection failed"));

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Failed.class);
        assertThat(((MaintenanceTick.Failed) result).stage()).isEqualTo("reflection");
    }

    @Test
    void maintainPropagatesTickFailure() {
        when(orchestrator.doTick(anyString(), anyString()))
                .thenReturn(new HygieneTick.Failed("store down"));

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Failed.class);
        assertThat(((MaintenanceTick.Failed) result).stage()).isEqualTo("tick");
    }

    @Test
    void maintainReportsIntegrityViolations() {
        when(orchestrator.doTick(anyString(), anyString()))
                .thenReturn(new HygieneTick.Completed(0, 0, 0, List.of()));
        when(reflectionOrchestrator.reflect(anyString(), anyString(), any(Instant.class), anyInt()))
                .thenReturn(List.of());
        var violation = new IntegrityViolation("c1", ViolationType.ORPHANED_SUPERSESSION, "orphaned", false);
        when(integrityChecker.check(anyString(), anyString(), any(MemoryDomain.class)))
                .thenReturn(List.of(violation));

        var result = scheduler.maintain("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MaintenanceTick.Completed.class);
        assertThat(((MaintenanceTick.Completed) result).violations()).hasSize(1);
        verify(eventSink).accept(any(HygieneEvent.IntegrityViolationDetected.class));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest="io.casehub.neocortex.cognition.memory.MemoryHygieneOrchestratorImplTest,io.casehub.neocortex.cognition.memory.MemoryHygieneSchedulerTest" -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure.

- [ ] **Step 4: Create MemoryHygieneOrchestratorImpl**

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.KnowledgeGapSummary;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrRecord;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrFeatureRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.TemporalDecay;
import io.casehub.neocortex.memory.cbr.ScopeDecay;
import io.casehub.platform.api.path.Path;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MemoryHygieneOrchestratorImpl implements MemoryHygieneOrchestrator {

    private static final Logger LOG = Logger.getLogger(MemoryHygieneOrchestratorImpl.class.getName());
    static final double LOW_RETENTION_THRESHOLD = 0.3;

    private final CbrRecordStore store;
    private final ConfidenceScorer confidenceScorer;
    private final TemporalDecay temporalDecay;
    private final ScopeDecay scopeDecay;
    private final ConsolidationSummariser summariser;
    private final MemoryDomain domain;
    private final List<String> caseTypes;
    private final RetentionConfig retentionConfig;
    private final int consolidationBatchSize;
    private final double crossLinkSimilarityThreshold;
    private final Consumer<HygieneEvent> eventSink;

    private final ConcurrentHashMap<String, ReentrantLock> tickLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KnowledgeGapSummary> gapStates = new ConcurrentHashMap<>();

    public MemoryHygieneOrchestratorImpl(
            CbrRecordStore store,
            ConfidenceScorer confidenceScorer,
            TemporalDecay temporalDecay,
            ScopeDecay scopeDecay,
            ConsolidationSummariser summariser,
            MemoryDomain domain,
            List<String> caseTypes,
            RetentionConfig retentionConfig,
            int consolidationBatchSize,
            double crossLinkSimilarityThreshold,
            Consumer<HygieneEvent> eventSink) {
        this.store = store;
        this.confidenceScorer = confidenceScorer;
        this.temporalDecay = temporalDecay;
        this.scopeDecay = scopeDecay;
        this.summariser = summariser;
        this.domain = domain;
        this.caseTypes = List.copyOf(caseTypes);
        this.retentionConfig = retentionConfig;
        this.consolidationBatchSize = consolidationBatchSize;
        this.crossLinkSimilarityThreshold = crossLinkSimilarityThreshold;
        this.eventSink = eventSink;
    }

    @Override
    public void tick(String agentId, String tenantId) {
        doTick(agentId, tenantId);
    }

    @Override
    public KnowledgeGapSummary knowledgeGaps(String agentId, String tenantId) {
        var summary = gapStates.get(agentId + ":" + tenantId);
        return summary != null ? summary : KnowledgeGapSummary.empty();
    }

    HygieneTick doTick(String agentId, String tenantId) {
        var lock = tickLocks.computeIfAbsent(agentId + ":" + tenantId, k -> new ReentrantLock());
        lock.lock();
        try {
            return doTickInternal(agentId, tenantId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Memory hygiene tick failed for " + agentId, e);
            return new HygieneTick.Failed(e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private HygieneTick doTickInternal(String agentId, String tenantId) {
        var now = Instant.now();
        var allScores = new ArrayList<RetentionScore>();
        int totalEvicted = 0;
        int totalConsolidated = 0;

        for (var caseType : caseTypes) {
            var query = CbrQuery.of(tenantId, domain, Path.root(), caseType,
                            Map.of(), consolidationBatchSize)
                    .withMinSimilarity(0.0);
            List<CbrMatch<CbrRecord>> memories = store.retrieveSimilar(query, CbrRecord.class);

            var agentMemories = memories.stream()
                    .filter(m -> agentId.equals(m.cbrRecord().producerAgentId()))
                    .toList();

            if (agentMemories.isEmpty()) continue;

            var scored = agentMemories.stream()
                    .map(m -> RetentionScore.compute(
                            m.caseId(),
                            entityId(m),
                            confidenceScorer.score(m, now),
                            temporalDecay.factor(m.storedAt(), now),
                            scopeDecay.factor(0),
                            m.cbrRecord().trustScore() != null ? m.cbrRecord().trustScore() : 1.0,
                            retentionConfig))
                    .toList();

            allScores.addAll(scored);

            var toEvict = scored.stream()
                    .filter(s -> s.composite() < retentionConfig.retentionThreshold())
                    .toList();

            for (var eviction : toEvict) {
                store.erase(new EraseRequest(eviction.entityId(), domain, tenantId, eviction.caseId()));
                eventSink.accept(new HygieneEvent.MemoryEvicted(eviction.caseId(), eviction));
            }
            totalEvicted += toEvict.size();

            var survivors = agentMemories.stream()
                    .filter(m -> scored.stream()
                            .anyMatch(s -> s.caseId().equals(m.caseId())
                                    && s.composite() >= retentionConfig.retentionThreshold()))
                    .toList();

            totalConsolidated += consolidate(survivors, tenantId, caseType);
        }

        if (allScores.isEmpty()) {
            return new HygieneTick.Idle("no memories for agent");
        }

        int lowRetention = (int) allScores.stream()
                .filter(s -> s.composite() < LOW_RETENTION_THRESHOLD).count();
        gapStates.put(agentId + ":" + tenantId,
                new KnowledgeGapSummary(lowRetention, totalConsolidated, allScores.size()));

        return new HygieneTick.Completed(totalConsolidated, totalEvicted, allScores.size(), allScores);
    }

    private int consolidate(List<CbrMatch<CbrRecord>> survivors, String tenantId, String caseType) {
        if (survivors.size() < 2) return 0;
        var groups = findSimilarGroups(survivors);
        int consolidated = 0;

        for (var group : groups) {
            if (group.size() < 2) continue;
            try {
                var texts = group.stream()
                        .map(m -> m.cbrRecord().problem() != null ? m.cbrRecord().problem() : "")
                        .toList();
                var summaryText = summariser.summarise(texts);
                if (summaryText == null || summaryText.isBlank()) continue;

                var mergedFeatures = new HashMap<String, FeatureValue>();
                var sourceCaseIds = new ArrayList<String>();
                for (var m : group) {
                    mergedFeatures.putAll(m.cbrRecord().features());
                    sourceCaseIds.add(m.caseId());
                }
                mergedFeatures.put("source_cases", FeatureValue.stringList(sourceCaseIds));

                var mergedCase = new CbrFeatureRecord(
                        summaryText, "consolidated from " + sourceCaseIds.size() + " memories",
                        null, null, mergedFeatures, null,
                        group.getFirst().cbrRecord().producerAgentId());

                var mergedId = store.store(mergedCase, caseType,
                        group.getFirst().cbrRecord().producerAgentId(),
                        domain, tenantId, null, Path.root());

                for (var sourceId : sourceCaseIds) {
                    store.supersede(sourceId, tenantId, mergedId, "hygiene-consolidation");
                }

                eventSink.accept(new HygieneEvent.MemoryConsolidated(mergedId, sourceCaseIds));
                consolidated++;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Consolidation failed for group", e);
            }
        }
        return consolidated;
    }

    private List<List<CbrMatch<CbrRecord>>> findSimilarGroups(List<CbrMatch<CbrRecord>> memories) {
        var groups = new ArrayList<List<CbrMatch<CbrRecord>>>();
        var used = new boolean[memories.size()];
        for (int i = 0; i < memories.size(); i++) {
            if (used[i]) continue;
            var group = new ArrayList<CbrMatch<CbrRecord>>();
            group.add(memories.get(i));
            used[i] = true;
            for (int j = i + 1; j < memories.size(); j++) {
                if (used[j]) continue;
                if (memories.get(j).score() >= crossLinkSimilarityThreshold) {
                    group.add(memories.get(j));
                    used[j] = true;
                }
            }
            groups.add(group);
        }
        return groups;
    }

    private static String entityId(CbrMatch<CbrRecord> m) {
        var features = m.cbrRecord().features();
        if (features != null && features.containsKey("entity_id")) {
            var fv = features.get("entity_id");
            if (fv instanceof FeatureValue.StringVal sv) return sv.value();
        }
        return m.caseId();
    }
}
```

- [ ] **Step 5: Create MemoryHygieneScheduler**

The scheduler uses `ReflectionOrchestrator` (neocortex's existing infrastructure) instead of agency's `ReflectionStore`. Reflections are stored through the orchestrator which fires CDI events internally.

```java
package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.reflection.ReflectionOrchestrator;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MemoryHygieneScheduler {

    private static final Logger LOG = Logger.getLogger(MemoryHygieneScheduler.class.getName());

    private final MemoryHygieneOrchestratorImpl orchestrator;
    private final ReflectionOrchestrator reflectionOrchestrator;
    private final IntegrityChecker integrityChecker;
    private final MemoryDomain domain;
    private final int maxReflectionSources;
    private final Consumer<HygieneEvent> eventSink;

    public MemoryHygieneScheduler(
            MemoryHygieneOrchestratorImpl orchestrator,
            ReflectionOrchestrator reflectionOrchestrator,
            IntegrityChecker integrityChecker,
            MemoryDomain domain,
            int maxReflectionSources,
            Consumer<HygieneEvent> eventSink) {
        this.orchestrator = orchestrator;
        this.reflectionOrchestrator = reflectionOrchestrator;
        this.integrityChecker = integrityChecker;
        this.domain = domain;
        this.maxReflectionSources = maxReflectionSources;
        this.eventSink = eventSink;
    }

    public MaintenanceTick maintain(String agentId, String tenantId) {
        var hygieneTick = orchestrator.doTick(agentId, tenantId);

        if (hygieneTick instanceof HygieneTick.Failed f) {
            return new MaintenanceTick.Failed("tick", f.reason());
        }

        int reflectionsGenerated;
        try {
            var since = Instant.now().minusSeconds(86400);
            var reflections = reflectionOrchestrator.reflect(
                    agentId, tenantId, since, maxReflectionSources);
            reflectionsGenerated = reflections.size();
            for (var insight : reflections) {
                eventSink.accept(new HygieneEvent.ReflectionGenerated(agentId, insight));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Reflection failed for " + agentId, e);
            return new MaintenanceTick.Failed("reflection", e.getMessage());
        }

        List<IntegrityViolation> violations;
        try {
            violations = integrityChecker.check(agentId, tenantId, domain);
            for (var violation : violations) {
                eventSink.accept(new HygieneEvent.IntegrityViolationDetected(violation));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Integrity check failed for " + agentId, e);
            return new MaintenanceTick.Failed("integrity", e.getMessage());
        }

        return new MaintenanceTick.Completed(hygieneTick, reflectionsGenerated, 0, violations);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest="io.casehub.neocortex.cognition.memory.MemoryHygieneOrchestratorImplTest,io.casehub.neocortex.cognition.memory.MemoryHygieneSchedulerTest"`
Expected: all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/memory/MemoryHygieneOrchestratorImpl.java \
       cognition/src/main/java/io/casehub/neocortex/cognition/memory/MemoryHygieneScheduler.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/memory/MemoryHygieneOrchestratorImplTest.java \
       cognition/src/test/java/io/casehub/neocortex/cognition/memory/MemoryHygieneSchedulerTest.java
git commit -m "feat: add MemoryHygieneOrchestratorImpl and MemoryHygieneScheduler

Implements the existing MemoryHygieneOrchestrator SPI from cognition-api.
Retention scoring + eviction + consolidation (via ConsolidationSummariser
SPI) + integrity checking. MemoryHygieneScheduler composes orchestrator
with ReflectionOrchestrator and IntegrityChecker.

Refs casehubio/neocortex#1115"
```

### Task 6: CDI wiring — default bean producers

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/CognitionDefaultBeans.java`

**Interfaces:**
- Consumes: All types from Tasks 1-5; existing `Instance<CbrRecordStore>` pattern from CognitionDefaultBeans
- Produces: CDI `@DefaultBean` producers for `RetentionConfig`, `ConfidenceScorer`, `IntegrityChecker`, `SemanticIntegrityChecker`, `ConsolidationSummariser`

- [ ] **Step 1: Add default bean producers to CognitionDefaultBeans**

Add the following producers at the end of `CognitionDefaultBeans`, before the closing brace. Use `ide_insert_member` or append with Edit tool.

```java
@Produces @DefaultBean @Singleton
RetentionConfig retentionConfig() {
    return RetentionConfig.DEFAULT;
}

@Produces @DefaultBean @Singleton
ConsolidationSummariser consolidationSummariser() {
    return new io.casehub.neocortex.cognition.memory.NoOpConsolidationSummariser();
}

@Produces @DefaultBean @Singleton
SemanticIntegrityChecker semanticIntegrityChecker() {
    return new io.casehub.neocortex.cognition.memory.NoOpSemanticIntegrityChecker();
}

@Produces @DefaultBean @Singleton
ConfidenceScorer confidenceScorer() {
    return new io.casehub.neocortex.cognition.memory.CompositeConfidenceScorer(java.util.List.of(
            new io.casehub.neocortex.cognition.memory.WeightedScorer(
                    new io.casehub.neocortex.cognition.memory.ArousalScorer(), 1.0),
            new io.casehub.neocortex.cognition.memory.WeightedScorer(
                    new io.casehub.neocortex.cognition.memory.SurpriseScorer(), 0.5)));
}

@Produces
@DefaultBean
@Singleton
IntegrityChecker integrityChecker(
        Instance<CbrRecordStore> cbrStore,
        SemanticIntegrityChecker semanticChecker) {
    if (!cbrStore.isResolvable()) return (agentId, tenantId, domain) -> java.util.List.of();
    return new io.casehub.neocortex.cognition.memory.DefaultIntegrityChecker(
            cbrStore.get(), semanticChecker, java.util.List.of());
}
```

Add imports at the top of CognitionDefaultBeans:
```java
import io.casehub.neocortex.cognition.memory.RetentionConfig;
import io.casehub.neocortex.cognition.memory.ConsolidationSummariser;
import io.casehub.neocortex.cognition.memory.SemanticIntegrityChecker;
import io.casehub.neocortex.cognition.memory.ConfidenceScorer;
import io.casehub.neocortex.cognition.memory.IntegrityChecker;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
```

- [ ] **Step 2: Verify full build compiles**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn compile -pl cognition-api,cognition`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run all memory hygiene tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition-api,cognition -Dtest="io.casehub.neocortex.cognition.memory.*"`
Expected: all tests PASS.

- [ ] **Step 4: Run full cognition module tests to check for regressions**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition`
Expected: all existing tests still PASS.

- [ ] **Step 5: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/CognitionDefaultBeans.java
git commit -m "feat: wire memory hygiene default beans in CognitionDefaultBeans

RetentionConfig.DEFAULT, NoOpConsolidationSummariser,
NoOpSemanticIntegrityChecker, CompositeConfidenceScorer (arousal+surprise),
DefaultIntegrityChecker (when CbrRecordStore available).

Refs casehubio/neocortex#1115"
```

---

## Batch 4: Verification

### Task 7: Full build verification and CLAUDE.md update

**Files:**
- Modify: `CLAUDE.md` (update cognition-api and cognition module descriptions)

- [ ] **Step 1: Run full project build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -DskipTests`
Expected: BUILD SUCCESS across all modules.

- [ ] **Step 2: Run all tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition-api,cognition`
Expected: all tests PASS, including pre-existing tests.

- [ ] **Step 3: Update CLAUDE.md**

Update the cognition-api description to mention the new memory hygiene types. Update the cognition description to mention the new implementations.

In the cognition-api bullet, add after the existing orchestrator SPI mentions:
`Memory hygiene types: ConfidenceScorer, ConsolidationSummariser, IntegrityChecker, SemanticIntegrityChecker SPIs, RetentionConfig, RetentionScore, HygieneEvent/HygieneTick/MaintenanceTick sealed hierarchies, IntegrityViolation, ViolationType.`

In the cognition bullet, add:
`Memory hygiene: MemoryHygieneOrchestratorImpl (retention scoring + eviction + consolidation via ConsolidationSummariser SPI), MemoryHygieneScheduler (composes orchestrator + ReflectionOrchestrator + IntegrityChecker), ArousalScorer/SurpriseScorer (dual ContentScorer + ConfidenceScorer), CompositeConfidenceScorer/WeightedScorer, DefaultIntegrityChecker, NoOpSemanticIntegrityChecker, NoOpConsolidationSummariser.`

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md with memory hygiene types

Refs casehubio/neocortex#1115"
```

## References

- [2026-11-18-receive-memory-hygiene-design.md] — design spec this plan implements
- [cognition-api/.../memory/MemoryHygieneOrchestrator.java] — existing SPI
- [cognition/.../core/CognitionCore.java:311] — existing tick wiring
- [cognition/.../CognitionDefaultBeans.java] — CDI default bean producers
- [agency social/memory/] — source implementation
- [memory-api/.../reflection/ReflectionOrchestrator.java] — reused for reflection
- [casehubio/neocortex#1115] — focal issue
- [casehubio/agency#447] — parent migration issue

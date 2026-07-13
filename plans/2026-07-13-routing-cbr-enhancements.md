# Routing CBR Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #37 — feat: feature weight support path from RoutingFeatureExtractor to CbrQuery
**Issue group:** #37, #34, #32

**Goal:** Make CBR routing analysis configurable (outcome weights, similarity weighting), add a structured signal enrichment SPI for non-LLM strategies, and implement plan composition matching as the first signal provider.

**Architecture:** Three layered changes. #37 extracts hardcoded outcome weights into an injectable SPI and adds similarity-weighted scoring. #34 creates `RoutingSignalProvider`/`RoutingSignalAssembler` in engine-api (parallel to `RoutingPromptSection`/`RoutingPromptAssembler`), then integrates them into `CbrAgentRoutingStrategy`. #32 implements `PlanCompositionAnalyser` as a `RoutingSignalProvider` that scores candidates based on case-level outcomes in multi-step plans.

**Tech Stack:** Java 21, JUnit 5, Mockito, AssertJ, Mutiny (reactive). No CDI container in tests.

## Global Constraints

- Pre-release — breaking changes cost nothing
- `RoutingOutcome` enum: SUCCESS, FAILURE, GATE_REJECTED, GATE_EXPIRED (engine-api)
- Case-level outcomes: String-typed from `CaseOutcomeEvent.outcomeLabel()` — COMPLETED, FAULTED, CANCELLED
- Signal scores normalised to [0.0, 1.0]
- `@DefaultBean` pattern for single-bean replacement SPIs (Pattern C)
- `Instance<T>` for optional CDI dependencies; `@DefaultBean` ensures always satisfied
- Cross-repo: SPI types go in engine-api (`io.casehub.api.spi.routing`), implementations in blocks (`io.casehub.blocks.routing.agent`)

## Cross-repo note

Task 2 creates 3 new files in **engine-api** (`/Users/mdproctor/claude/casehub/engine/api/`). This follows the established pattern — `RoutingPromptSection` and `RoutingPromptAssembler` are also in engine-api. After committing to engine-api, run `mvn -C /Users/mdproctor/claude/casehub/engine install -DskipTests` to make the new types available to blocks.

---

### Task 1: Configurable Outcome Weights + Similarity Weighting (#37)

**Files:**
- Create: `src/main/java/io/casehub/blocks/routing/agent/CbrOutcomeWeights.java`
- Create: `src/main/java/io/casehub/blocks/routing/agent/DefaultCbrOutcomeWeights.java`
- Modify: `src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java`
- Test: `src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java`

**Interfaces:**
- Produces: `CbrOutcomeWeights.weights()` → `Map<RoutingOutcome, Double>` — consumed by Task 3 (`PlanCompositionAnalyser`)
- Produces: `CbrAgentRoutingStrategy.analyseExperiences()` → `Map<String, Double>` — consumed by Task 2 (signal combination in `doSelect`)

- [ ] **Step 1: Write failing tests for CbrOutcomeWeights injection**

Add new test class `ConfigurableWeights` nested in `CbrAgentRoutingStrategyTest`:

```java
@Nested
class ConfigurableWeights {

  @Test
  void usesInjectedOutcomeWeights() {
    // Custom weights: GATE_EXPIRED=1.0 (same as SUCCESS)
    var customWeights = new CbrOutcomeWeights() {
      @Override
      public Map<RoutingOutcome, Double> weights() {
        return Map.of(
            RoutingOutcome.SUCCESS, 1.0,
            RoutingOutcome.GATE_EXPIRED, 1.0,
            RoutingOutcome.GATE_REJECTED, 0.0,
            RoutingOutcome.FAILURE, 0.0);
      }
    };

    // agent-a has GATE_EXPIRED (=1.0 with custom), agent-b has SUCCESS (=1.0)
    // With default weights, agent-b wins (1.0 vs 0.5)
    // With custom weights, both score 1.0 — tie broken by evidence mass
    var experiences = List.of(
        experience("analysis", "agent-a", "GATE_EXPIRED"),
        experience("analysis", "agent-b", "SUCCESS"));

    var strategy = new CbrAgentRoutingStrategy(
        (AgentGraphQuery) null, null, null, null, customWeights, null);
    var result = strategy.select(
        context("analysis", experiences),
        List.of(candidate("agent-a"), candidate("agent-b")))
        .await().indefinitely();

    assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    // Both score 1.0 with custom weights — tie, but agent-b has same evidence mass
    // so first-encountered wins; the important assertion is that GATE_EXPIRED
    // scored 1.0, not 0.5 (the default). We'll verify via a clearer test below.
  }

  @Test
  void customWeightsMakeGateExpiredBeatSuccess() {
    var customWeights = new CbrOutcomeWeights() {
      @Override
      public Map<RoutingOutcome, Double> weights() {
        return Map.of(
            RoutingOutcome.SUCCESS, 0.5,
            RoutingOutcome.GATE_EXPIRED, 1.0,
            RoutingOutcome.GATE_REJECTED, 0.0,
            RoutingOutcome.FAILURE, 0.0);
      }
    };

    var experiences = List.of(
        experience("analysis", "agent-a", "GATE_EXPIRED"),
        experience("analysis", "agent-b", "SUCCESS"));

    var strategy = new CbrAgentRoutingStrategy(
        (AgentGraphQuery) null, null, null, null, customWeights, null);
    var result = strategy.select(
        context("analysis", experiences),
        List.of(candidate("agent-a"), candidate("agent-b")))
        .await().indefinitely();

    assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
  }

  @Test
  void unrecognisedOutcomeDefaultsToZero() {
    var experiences = List.of(
        experience("analysis", "agent-a", "DECLINED"),
        experience("analysis", "agent-b", "SUCCESS"));

    var strategy = new CbrAgentRoutingStrategy(
        (AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
    var result = strategy.select(
        context("analysis", experiences),
        List.of(candidate("agent-a"), candidate("agent-b")))
        .await().indefinitely();

    assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
  }
}
```

- [ ] **Step 2: Write failing tests for similarity weighting**

Add new test class `SimilarityWeighting` nested in `CbrAgentRoutingStrategyTest`:

```java
@Nested
class SimilarityWeighting {

  private RetrievedExperience experienceWithSimilarity(
      String capName, String workerName, String outcome, double similarity) {
    return new RetrievedExperience(
        "problem", "solution", "COMPLETED", 0.9, similarity,
        Map.of(),
        List.of(new ExperiencePlanStep("binding-a", capName, workerName, outcome, 0, Map.of())),
        Map.of());
  }

  @Test
  void highSimilarityExperienceOutweighsLowSimilarity() {
    // agent-a: SUCCESS at 0.95 similarity
    // agent-b: SUCCESS at 0.3 similarity
    // Both have SUCCESS, but agent-a's evidence is stronger
    var experiences = List.of(
        experienceWithSimilarity("analysis", "agent-a", "SUCCESS", 0.95),
        experienceWithSimilarity("analysis", "agent-b", "SUCCESS", 0.3));

    var strategy = new CbrAgentRoutingStrategy(
        (AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
    var result = strategy.select(
        context("analysis", experiences),
        List.of(candidate("agent-a"), candidate("agent-b")))
        .await().indefinitely();

    // Both have rate=1.0 (SUCCESS/1), tie broken by evidence mass
    // agent-a: evidenceMass=0.95, agent-b: evidenceMass=0.3
    assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
  }

  @Test
  void negativeSimilarityClampedToZero() {
    // agent-a has only negative-similarity experience — should be excluded
    // agent-b has positive-similarity SUCCESS
    var experiences = List.of(
        experienceWithSimilarity("analysis", "agent-a", "SUCCESS", -0.5),
        experienceWithSimilarity("analysis", "agent-b", "SUCCESS", 0.7));

    var strategy = new CbrAgentRoutingStrategy(
        (AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
    var result = strategy.select(
        context("analysis", experiences),
        List.of(candidate("agent-a"), candidate("agent-b")))
        .await().indefinitely();

    assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
  }

  @Test
  void zeroSimilarityExperienceContributesNothing() {
    var experiences = List.of(
        experienceWithSimilarity("analysis", "agent-a", "SUCCESS", 0.0),
        experienceWithSimilarity("analysis", "agent-b", "SUCCESS", 0.8));

    var strategy = new CbrAgentRoutingStrategy(
        (AgentGraphQuery) null, null, null, null, new DefaultCbrOutcomeWeights(), null);
    var result = strategy.select(
        context("analysis", experiences),
        List.of(candidate("agent-a"), candidate("agent-b")))
        .await().indefinitely();

    assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
  }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=CbrAgentRoutingStrategyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failures — `CbrOutcomeWeights` not found, constructor signature mismatch.

- [ ] **Step 4: Create CbrOutcomeWeights interface**

Create `src/main/java/io/casehub/blocks/routing/agent/CbrOutcomeWeights.java`:

```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.RoutingOutcome;
import java.util.Map;

public interface CbrOutcomeWeights {
    Map<RoutingOutcome, Double> weights();
}
```

- [ ] **Step 5: Create DefaultCbrOutcomeWeights**

Create `src/main/java/io/casehub/blocks/routing/agent/DefaultCbrOutcomeWeights.java`:

```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.RoutingOutcome;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@DefaultBean
@ApplicationScoped
public class DefaultCbrOutcomeWeights implements CbrOutcomeWeights {

  private static final Map<RoutingOutcome, Double> DEFAULTS =
      Map.of(
          RoutingOutcome.SUCCESS, 1.0,
          RoutingOutcome.GATE_EXPIRED, 0.5,
          RoutingOutcome.GATE_REJECTED, 0.25,
          RoutingOutcome.FAILURE, 0.0);

  @Override
  public Map<RoutingOutcome, Double> weights() {
    return DEFAULTS;
  }
}
```

- [ ] **Step 6: Refactor CbrAgentRoutingStrategy**

Three changes:
1. Add `CbrOutcomeWeights` field + inject via constructor (both CDI and test constructors)
2. Change `analyseExperiences` to return `Map<String, Double>` instead of `@Nullable String`
3. Add similarity weighting + negative clamping + evidence mass tie-breaking
4. Update `doSelect` to use the score map

The `OUTCOME_WEIGHTS` static field is removed — replaced by the injected `CbrOutcomeWeights`.

Add `CbrOutcomeWeights` and `RoutingSignalAssembler` (null for now) parameters to both constructors. The CDI constructor resolves `Instance<CbrOutcomeWeights>` eagerly (always satisfied due to `@DefaultBean`). The test constructor accepts the weights directly. A second `null` parameter slot for `RoutingSignalAssembler` is added now but unused until Task 2.

Updated `analyseExperiences` method body:

```java
private Map<String, Double> analyseExperiences(
    final List<RetrievedExperience> experiences,
    final Set<String> eligibleIds,
    final String capabilityName) {
  final Map<String, double[]> workerStats = new HashMap<>();
  final var weights = outcomeWeights.weights();

  for (final var exp : experiences) {
    final double relevance = Math.max(0.0, exp.similarityScore());
    if (relevance == 0.0) continue;

    for (final var step : exp.planTrace()) {
      if (capabilityName.equals(step.capabilityName())
          && step.workerName() != null
          && eligibleIds.contains(step.workerName())) {
        final var stats =
            workerStats.computeIfAbsent(step.workerName(), k -> new double[] {0.0, 0.0});
        RoutingOutcome outcome;
        try {
          outcome = RoutingOutcome.valueOf(step.stepOutcome());
        } catch (IllegalArgumentException e) {
          outcome = null;
        }
        final double outcomeWeight = outcome != null ? weights.getOrDefault(outcome, 0.0) : 0.0;
        stats[0] += outcomeWeight * relevance;
        stats[1] += relevance;
      }
    }
  }

  final Map<String, Double> scores = new HashMap<>();
  for (final var entry : workerStats.entrySet()) {
    final double evidenceMass = entry.getValue()[1];
    if (evidenceMass > 0.0) {
      scores.put(entry.getKey(), entry.getValue()[0] / evidenceMass);
    }
  }
  return scores;
}
```

Updated `doSelect` — select best candidate from score map:

```java
// In doSelect, after analyseExperiences:
final Map<String, Double> experienceScores =
    analyseExperiences(context.experiences(), eligibleIds, context.capabilityName());

// Select best candidate (will incorporate signals in Task 2)
String bestCandidate = null;
double bestScore = 0.0;
for (final var entry : experienceScores.entrySet()) {
  if (entry.getValue() > bestScore) {
    bestScore = entry.getValue();
    bestCandidate = entry.getKey();
  }
}

if (bestCandidate != null) {
  return RoutingResult.assigned(bestCandidate, "CBR selected based on historical success rate");
}
```

- [ ] **Step 7: Update existing tests for new constructor signature**

All existing tests use `new CbrAgentRoutingStrategy((AgentGraphQuery) null, null, null, null)`. Update to add `new DefaultCbrOutcomeWeights()` and `null` (for future assembler) as 5th and 6th parameters.

- [ ] **Step 8: Run all tests**

Run: `mvn --batch-mode test -pl . -Dtest=CbrAgentRoutingStrategyTest`
Expected: ALL pass (both new and existing tests).

- [ ] **Step 9: Verify with diagnostics**

Run `ide_diagnostics` on `CbrAgentRoutingStrategy.java` and both new files.

- [ ] **Step 10: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/routing/agent/CbrOutcomeWeights.java \
  src/main/java/io/casehub/blocks/routing/agent/DefaultCbrOutcomeWeights.java \
  src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java \
  src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#37): configurable outcome weights + similarity-weighted CBR scoring

Extract OUTCOME_WEIGHTS into injectable CbrOutcomeWeights SPI with
DefaultCbrOutcomeWeights @DefaultBean. Weight experience contributions
by similarityScore (clamped >= 0). Refactor analyseExperiences to return
per-candidate score map for signal integration.

Refs #37"
```

---

### Task 2: RoutingSignalProvider SPI + Strategy Integration (#34)

**Files:**
- Create: `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/spi/routing/RoutingSignal.java` (engine-api)
- Create: `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/spi/routing/RoutingSignalProvider.java` (engine-api)
- Create: `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/spi/routing/RoutingSignalAssembler.java` (engine-api)
- Create: `src/test/java/io/casehub/api/spi/routing/RoutingSignalAssemblerTest.java` (blocks — contract test)
- Modify: `src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java` (blocks)
- Modify: `src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java` (blocks)

**Interfaces:**
- Produces: `RoutingSignalProvider.signal()` → `@Nullable RoutingSignal` — consumed by Task 3 (`PlanCompositionAnalyser implements RoutingSignalProvider`)
- Produces: `RoutingSignalAssembler.assemble()` → `Map<String, RoutingSignal>` — consumed by `CbrAgentRoutingStrategy.doSelect()`

- [ ] **Step 1: Write RoutingSignalAssembler contract tests**

Create `src/test/java/io/casehub/api/spi/routing/RoutingSignalAssemblerTest.java`:

```java
package io.casehub.api.spi.routing;

import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingSignalAssemblerTest {

  private static final AgentRoutingContext CONTEXT =
      new AgentRoutingContext(UUID.randomUUID(), "cap", NullNode.instance, "t", List.of());
  private static final List<AgentCandidate> EMPTY_ELIGIBLE = List.of();

  @Nested
  class Assembly {

    @Test
    void emptyProvidersReturnsEmptyMap() {
      var assembler = new RoutingSignalAssembler(List.of());
      assertThat(assembler.assemble(CONTEXT, EMPTY_ELIGIBLE)).isEmpty();
    }

    @Test
    void collectsNonNullSignalsKeyedById() {
      var p1 = provider("sig-a", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal(0.8, "good"))));
      var p2 = provider("sig-b", new RoutingSignal(Map.of("w2",
          new RoutingSignal.CandidateSignal(0.6, "ok"))));

      var assembler = new RoutingSignalAssembler(List.of(p1, p2));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result).containsOnlyKeys("sig-a", "sig-b");
      assertThat(result.get("sig-a").candidates().get("w1").score()).isEqualTo(0.8);
      assertThat(result.get("sig-b").candidates().get("w2").score()).isEqualTo(0.6);
    }

    @Test
    void skipsNullSignals() {
      var p1 = provider("sig-a", null);
      var p2 = provider("sig-b", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal(0.5, null))));

      var assembler = new RoutingSignalAssembler(List.of(p1, p2));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result).containsOnlyKeys("sig-b");
    }
  }

  @Nested
  class Resilience {

    @Test
    void throwingProviderIsSkipped() {
      var good = provider("good", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal(0.9, null))));
      var bad = new RoutingSignalProvider() {
        @Override public String id() { return "bad"; }
        @Override public RoutingSignal signal(AgentRoutingContext ctx, List<AgentCandidate> e) {
          throw new RuntimeException("boom");
        }
      };

      var assembler = new RoutingSignalAssembler(List.of(bad, good));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result).containsOnlyKeys("good");
    }
  }

  @Nested
  class ScoreClamping {

    @Test
    void scoresAboveOneAreClampedToOne() {
      var p = provider("over", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal(1.5, "over"))));

      var assembler = new RoutingSignalAssembler(List.of(p));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result.get("over").candidates().get("w1").score()).isEqualTo(1.0);
    }

    @Test
    void scoresBelowZeroAreClampedToZero() {
      var p = provider("under", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal(-0.3, "under"))));

      var assembler = new RoutingSignalAssembler(List.of(p));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result.get("under").candidates().get("w1").score()).isEqualTo(0.0);
    }

    @Test
    void scoresInRangeAreUnchanged() {
      var p = provider("ok", new RoutingSignal(Map.of("w1",
          new RoutingSignal.CandidateSignal(0.75, null))));

      var assembler = new RoutingSignalAssembler(List.of(p));
      var result = assembler.assemble(CONTEXT, EMPTY_ELIGIBLE);

      assertThat(result.get("ok").candidates().get("w1").score()).isEqualTo(0.75);
    }
  }

  private static RoutingSignalProvider provider(String id, RoutingSignal signal) {
    return new RoutingSignalProvider() {
      @Override public String id() { return id; }
      @Override public RoutingSignal signal(AgentRoutingContext ctx, List<AgentCandidate> e) {
        return signal;
      }
    };
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=RoutingSignalAssemblerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure — `RoutingSignal`, `RoutingSignalProvider`, `RoutingSignalAssembler` not found.

- [ ] **Step 3: Create RoutingSignal record in engine-api**

Create `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/spi/routing/RoutingSignal.java`:

```java
package io.casehub.api.spi.routing;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record RoutingSignal(Map<String, CandidateSignal> candidates) {

  public RoutingSignal {
    Objects.requireNonNull(candidates, "candidates must not be null");
    candidates = Map.copyOf(candidates);
  }

  public record CandidateSignal(double score, @Nullable String reason) {}
}
```

- [ ] **Step 4: Create RoutingSignalProvider interface in engine-api**

Create `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/spi/routing/RoutingSignalProvider.java`:

```java
package io.casehub.api.spi.routing;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface RoutingSignalProvider {

  String id();

  @Nullable RoutingSignal signal(AgentRoutingContext context, List<AgentCandidate> eligible);
}
```

- [ ] **Step 5: Create RoutingSignalAssembler in engine-api**

Create `/Users/mdproctor/claude/casehub/engine/api/src/main/java/io/casehub/api/spi/routing/RoutingSignalAssembler.java`:

```java
package io.casehub.api.spi.routing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RoutingSignalAssembler {

  private static final Logger LOG = Logger.getLogger(RoutingSignalAssembler.class);

  private final List<RoutingSignalProvider> providers;

  @Inject
  public RoutingSignalAssembler(Instance<RoutingSignalProvider> providers) {
    this.providers =
        providers.stream()
            .sorted(Comparator.comparingInt(RoutingSignalAssembler::priority))
            .toList();
  }

  public RoutingSignalAssembler(List<RoutingSignalProvider> providers) {
    this.providers =
        providers.stream()
            .sorted(Comparator.comparingInt(RoutingSignalAssembler::priority))
            .toList();
  }

  public Map<String, RoutingSignal> assemble(
      AgentRoutingContext context, List<AgentCandidate> eligible) {
    var result = new LinkedHashMap<String, RoutingSignal>();
    for (var provider : providers) {
      try {
        RoutingSignal signal = provider.signal(context, eligible);
        if (signal != null) {
          result.put(provider.id(), clampScores(signal, provider.id()));
        }
      } catch (Exception e) {
        LOG.warnf(
            e, "RoutingSignalProvider threw — skipping: %s", provider.getClass().getName());
      }
    }
    return result;
  }

  private static RoutingSignal clampScores(RoutingSignal signal, String providerId) {
    var clamped = new LinkedHashMap<String, RoutingSignal.CandidateSignal>();
    boolean anyClamped = false;
    for (var entry : signal.candidates().entrySet()) {
      var cs = entry.getValue();
      double score = cs.score();
      if (score < 0.0 || score > 1.0) {
        anyClamped = true;
        score = Math.max(0.0, Math.min(1.0, score));
      }
      clamped.put(entry.getKey(), new RoutingSignal.CandidateSignal(score, cs.reason()));
    }
    if (anyClamped) {
      LOG.warnf("RoutingSignalProvider '%s' returned out-of-range scores — clamped to [0,1]",
          providerId);
      return new RoutingSignal(clamped);
    }
    return signal;
  }

  private static int priority(RoutingSignalProvider provider) {
    var annotation = provider.getClass().getAnnotation(jakarta.annotation.Priority.class);
    return annotation != null ? annotation.value() : Integer.MAX_VALUE;
  }
}
```

- [ ] **Step 6: Install engine-api to local Maven**

Run: `mvn --batch-mode -f /Users/mdproctor/claude/casehub/engine/pom.xml install -DskipTests -pl api`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit engine-api changes**

```bash
git -C /Users/mdproctor/claude/casehub/engine add \
  api/src/main/java/io/casehub/api/spi/routing/RoutingSignal.java \
  api/src/main/java/io/casehub/api/spi/routing/RoutingSignalProvider.java \
  api/src/main/java/io/casehub/api/spi/routing/RoutingSignalAssembler.java
git -C /Users/mdproctor/claude/casehub/engine commit -m "feat(#34): RoutingSignalProvider SPI — structured enrichment for non-LLM routing strategies

New SPI parallel to RoutingPromptSection. RoutingSignalProvider returns
per-candidate scores [0.0, 1.0]. RoutingSignalAssembler discovers,
orders by @Priority, collects signals keyed by provider id. Score
clamping enforced by assembler.

Refs casehubio/blocks#34"
```

- [ ] **Step 8: Run assembler contract tests in blocks**

Run: `mvn --batch-mode test -pl . -Dtest=RoutingSignalAssemblerTest`
Expected: ALL pass.

- [ ] **Step 9: Write signal integration tests for CbrAgentRoutingStrategy**

Add `SignalIntegration` nested class in `CbrAgentRoutingStrategyTest`:

```java
@Nested
class SignalIntegration {

  @Test
  void signalBoostsCandidateAboveExperienceOnlyWinner() {
    // agent-a: SUCCESS (score=1.0), agent-b: FAILURE (score=0.0)
    // Signal boosts agent-b by 0.9 → agent-b wins (0.0 + 0.9 > 1.0 + 0.0)
    // Wait — that's wrong. 0.9 < 1.0. Let me make it clearer:
    // agent-a: GATE_EXPIRED (score=0.5), agent-b: FAILURE (score=0.0)
    // Signal boosts agent-b by 0.9 → agent-b wins (0.0 + 0.9 > 0.5 + 0.0)
    var experiences = List.of(
        experience("analysis", "agent-a", "GATE_EXPIRED"),
        experience("analysis", "agent-b", "FAILURE"));

    var signal = new RoutingSignal(Map.of(
        "agent-b", new RoutingSignal.CandidateSignal(0.9, "plan-fit")));
    var assembler = new RoutingSignalAssembler(
        List.of(provider("test", signal)));

    var strategy = new CbrAgentRoutingStrategy(
        (AgentGraphQuery) null, null, null, null,
        new DefaultCbrOutcomeWeights(), assembler);
    var result = strategy.select(
        context("analysis", experiences),
        List.of(candidate("agent-a"), candidate("agent-b")))
        .await().indefinitely();

    assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-b");
  }

  @Test
  void signalOnlyNoExperiences() {
    var signal = new RoutingSignal(Map.of(
        "agent-a", new RoutingSignal.CandidateSignal(0.7, "signal-only")));
    var assembler = new RoutingSignalAssembler(
        List.of(provider("test", signal)));

    var strategy = new CbrAgentRoutingStrategy(
        (AgentGraphQuery) null, null, null, null,
        new DefaultCbrOutcomeWeights(), assembler);
    var result = strategy.select(
        context("analysis", List.of()),
        List.of(candidate("agent-a"), candidate("agent-b")))
        .await().indefinitely();

    assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
  }

  @Test
  void noSignalsNoExperiencesFallsThrough() {
    var assembler = new RoutingSignalAssembler(List.of());

    var strategy = new CbrAgentRoutingStrategy(
        (AgentGraphQuery) null, null, null, null,
        new DefaultCbrOutcomeWeights(), assembler);
    var result = strategy.select(
        context("analysis", List.of()),
        List.of(candidate("agent-a")))
        .await().indefinitely();

    assertThat(result).isInstanceOf(RoutingResult.Unresolvable.class);
  }

  @Test
  void sparseCandidateMapHandledGracefully() {
    var experiences = List.of(
        experience("analysis", "agent-a", "SUCCESS"),
        experience("analysis", "agent-b", "SUCCESS"));

    // Signal only has data for agent-a
    var signal = new RoutingSignal(Map.of(
        "agent-a", new RoutingSignal.CandidateSignal(0.5, "boosted")));
    var assembler = new RoutingSignalAssembler(
        List.of(provider("test", signal)));

    var strategy = new CbrAgentRoutingStrategy(
        (AgentGraphQuery) null, null, null, null,
        new DefaultCbrOutcomeWeights(), assembler);
    var result = strategy.select(
        context("analysis", experiences),
        List.of(candidate("agent-a"), candidate("agent-b")))
        .await().indefinitely();

    // agent-a: 1.0 (experience) + 0.5 (signal) = 1.5
    // agent-b: 1.0 (experience) + 0.0 (no signal) = 1.0
    assertThat(result).isInstanceOf(RoutingResult.Selected.class);
    assertThat(((RoutingResult.Selected) result).single().executorId()).isEqualTo("agent-a");
  }

  private RoutingSignalProvider provider(String id, RoutingSignal signal) {
    return new RoutingSignalProvider() {
      @Override public String id() { return id; }
      @Override public RoutingSignal signal(AgentRoutingContext ctx, List<AgentCandidate> e) {
        return signal;
      }
    };
  }
}
```

- [ ] **Step 10: Integrate signals into CbrAgentRoutingStrategy.doSelect**

Update `doSelect` to combine experience scores with signal scores:

```java
final Map<String, Double> experienceScores =
    analyseExperiences(context.experiences(), eligibleIds, context.capabilityName());

// Combine experience scores with signal scores
final Map<String, Double> finalScores = new HashMap<>(experienceScores);
if (signalAssembler != null) {
  final Map<String, RoutingSignal> signals = signalAssembler.assemble(context, eligible);
  for (final var signalEntry : signals.values()) {
    for (final var candidateEntry : signalEntry.candidates().entrySet()) {
      if (eligibleIds.contains(candidateEntry.getKey())) {
        finalScores.merge(
            candidateEntry.getKey(), candidateEntry.getValue().score(), Double::sum);
      }
    }
  }
}

String bestCandidate = null;
double bestScore = 0.0;
for (final var entry : finalScores.entrySet()) {
  if (entry.getValue() > bestScore) {
    bestScore = entry.getValue();
    bestCandidate = entry.getKey();
  }
}

if (bestCandidate != null) {
  return RoutingResult.assigned(bestCandidate, "CBR selected based on historical success rate");
}
```

- [ ] **Step 11: Run all tests**

Run: `mvn --batch-mode test -pl . -Dtest="CbrAgentRoutingStrategyTest,RoutingSignalAssemblerTest"`
Expected: ALL pass.

- [ ] **Step 12: Commit blocks changes**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/test/java/io/casehub/api/spi/routing/RoutingSignalAssemblerTest.java \
  src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java \
  src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#34): integrate RoutingSignalProvider into CbrAgentRoutingStrategy

CbrAgentRoutingStrategy now injects RoutingSignalAssembler and combines
experience scores with signal scores. Sparse candidate maps handled
gracefully. Falls through to graph query when no positive score.

Refs #34"
```

---

### Task 3: Plan Composition Analyser + Case Outcome Weights (#32)

**Files:**
- Create: `src/main/java/io/casehub/blocks/routing/agent/CbrCaseOutcomeWeights.java`
- Create: `src/main/java/io/casehub/blocks/routing/agent/DefaultCbrCaseOutcomeWeights.java`
- Create: `src/main/java/io/casehub/blocks/routing/agent/PlanCompositionAnalyser.java`
- Create: `src/test/java/io/casehub/blocks/routing/agent/PlanCompositionAnalyserTest.java`

**Interfaces:**
- Consumes: `CbrCaseOutcomeWeights.weights()` → `Map<String, Double>` (case-level outcomes)
- Consumes: `RoutingSignalProvider` (from Task 2)
- Consumes: `AgentRoutingContext.experiences()` → `List<RetrievedExperience>`

- [ ] **Step 1: Write failing tests for PlanCompositionAnalyser**

Create `src/test/java/io/casehub/blocks/routing/agent/PlanCompositionAnalyserTest.java`:

```java
package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.eidos.api.MatchDegree;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlanCompositionAnalyserTest {

  private final PlanCompositionAnalyser analyser =
      new PlanCompositionAnalyser(new DefaultCbrCaseOutcomeWeights());

  private AgentRoutingContext context(String capability, List<RetrievedExperience> experiences) {
    return new AgentRoutingContext(
        UUID.randomUUID(), capability, NullNode.instance, "test-tenant", experiences);
  }

  private AgentCandidate candidate(String id) {
    return new AgentCandidate(
        id, Set.of("analysis"), 0, AgentHealth.READY, null, new MatchDegree.None());
  }

  private RetrievedExperience multiStepExperience(
      String outcome, double similarity, List<ExperiencePlanStep> planTrace) {
    return new RetrievedExperience(
        "problem", "solution", outcome, 0.9, similarity,
        Map.of(), planTrace, Map.of());
  }

  @Test
  void idIsPlanComposition() {
    assertThat(analyser.id()).isEqualTo("plan-composition");
  }

  @Nested
  class ReturnsNull {

    @Test
    void whenNoExperiences() {
      var result = analyser.signal(
          context("analysis", List.of()),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }

    @Test
    void whenAllExperiencesSingleStep() {
      var exp = multiStepExperience("COMPLETED", 0.8, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }

    @Test
    void whenNoEligibleCandidatesInTraces() {
      var exp = multiStepExperience("COMPLETED", 0.8, List.of(
          new ExperiencePlanStep("b1", "analysis", "not-eligible", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "other", "SUCCESS", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }

    @Test
    void whenNegativeSimilarityOnly() {
      var exp = multiStepExperience("COMPLETED", -0.5, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));
      assertThat(result).isNull();
    }
  }

  @Nested
  class Scoring {

    @Test
    void workerInCompletedMultiStepPlanScoresHigh() {
      var exp = multiStepExperience("COMPLETED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      assertThat(result.candidates()).containsKey("agent-a");
      assertThat(result.candidates().get("agent-a").score()).isCloseTo(1.0, within(0.01));
    }

    @Test
    void workerInFaultedMultiStepPlanScoresLow() {
      var exp = multiStepExperience("FAULTED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "FAILURE", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      assertThat(result.candidates().get("agent-a").score()).isCloseTo(0.2, within(0.01));
    }

    @Test
    void cancelledCaseProducesZeroScore() {
      var exp = multiStepExperience("CANCELLED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));

      // CANCELLED weight = 0.0, so no positive contribution → null
      assertThat(result).isNull();
    }

    @Test
    void multipleExperiencesAveraged() {
      var exp1 = multiStepExperience("COMPLETED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));
      var exp2 = multiStepExperience("FAULTED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-c", "FAILURE", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp1, exp2)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      // agent-a: (1.0*0.9 + 0.2*0.9) / (0.9+0.9) = 1.08/1.8 = 0.6
      assertThat(result.candidates().get("agent-a").score()).isCloseTo(0.6, within(0.01));
    }

    @Test
    void higherSimilarityExperienceWeighsMore() {
      var expHigh = multiStepExperience("COMPLETED", 0.95, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));
      var expLow = multiStepExperience("FAULTED", 0.1, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-c", "FAILURE", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(expHigh, expLow)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      // agent-a: (1.0*0.95 + 0.2*0.1) / (0.95+0.1) = 0.97/1.05 ≈ 0.924
      assertThat(result.candidates().get("agent-a").score()).isGreaterThan(0.9);
    }

    @Test
    void onlyTargetCapabilityStepsAreScored() {
      var exp = multiStepExperience("COMPLETED", 0.8, List.of(
          new ExperiencePlanStep("b1", "other-cap", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "analysis", "agent-b", "SUCCESS", 1, Map.of())));

      var result = analyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a"), candidate("agent-b")));

      // Only agent-b matches the target capability "analysis"
      assertThat(result).isNotNull();
      assertThat(result.candidates()).containsKey("agent-b");
      assertThat(result.candidates()).doesNotContainKey("agent-a");
    }
  }

  @Nested
  class CustomCaseOutcomeWeights {

    @Test
    void domainWeightsAreRespected() {
      var customWeights = new CbrCaseOutcomeWeights() {
        @Override
        public Map<String, Double> weights() {
          return Map.of("COMPLETED", 1.0, "FAULTED", 0.8, "CANCELLED", 0.5);
        }
      };
      var customAnalyser = new PlanCompositionAnalyser(customWeights);

      var exp = multiStepExperience("FAULTED", 0.9, List.of(
          new ExperiencePlanStep("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
          new ExperiencePlanStep("b2", "review", "agent-b", "SUCCESS", 1, Map.of())));

      var result = customAnalyser.signal(
          context("analysis", List.of(exp)),
          List.of(candidate("agent-a")));

      assertThat(result).isNotNull();
      // FAULTED weight = 0.8 (custom, not 0.2 default)
      assertThat(result.candidates().get("agent-a").score()).isCloseTo(0.8, within(0.01));
    }
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=PlanCompositionAnalyserTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure — `CbrCaseOutcomeWeights`, `PlanCompositionAnalyser` not found.

- [ ] **Step 3: Create CbrCaseOutcomeWeights interface**

Create `src/main/java/io/casehub/blocks/routing/agent/CbrCaseOutcomeWeights.java`:

```java
package io.casehub.blocks.routing.agent;

import java.util.Map;

public interface CbrCaseOutcomeWeights {
    Map<String, Double> weights();
}
```

- [ ] **Step 4: Create DefaultCbrCaseOutcomeWeights**

Create `src/main/java/io/casehub/blocks/routing/agent/DefaultCbrCaseOutcomeWeights.java`:

```java
package io.casehub.blocks.routing.agent;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@DefaultBean
@ApplicationScoped
public class DefaultCbrCaseOutcomeWeights implements CbrCaseOutcomeWeights {

  private static final Map<String, Double> DEFAULTS =
      Map.of("COMPLETED", 1.0, "FAULTED", 0.2, "CANCELLED", 0.0);

  @Override
  public Map<String, Double> weights() {
    return DEFAULTS;
  }
}
```

- [ ] **Step 5: Create PlanCompositionAnalyser**

Create `src/main/java/io/casehub/blocks/routing/agent/PlanCompositionAnalyser.java`:

```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.ExperiencePlanStep;
import io.casehub.api.spi.routing.RetrievedExperience;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.api.spi.routing.RoutingSignalProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
public class PlanCompositionAnalyser implements RoutingSignalProvider {

  private final CbrCaseOutcomeWeights caseOutcomeWeights;

  @Inject
  public PlanCompositionAnalyser(Instance<CbrCaseOutcomeWeights> caseOutcomeWeights) {
    this.caseOutcomeWeights = caseOutcomeWeights.get();
  }

  PlanCompositionAnalyser(CbrCaseOutcomeWeights caseOutcomeWeights) {
    this.caseOutcomeWeights = caseOutcomeWeights;
  }

  @Override
  public String id() {
    return "plan-composition";
  }

  @Override
  public @Nullable RoutingSignal signal(
      AgentRoutingContext context, List<AgentCandidate> eligible) {
    List<RetrievedExperience> experiences = context.experiences();
    if (experiences == null || experiences.isEmpty()) {
      return null;
    }

    Set<String> eligibleIds =
        eligible.stream().map(AgentCandidate::workerId).collect(Collectors.toSet());
    Map<String, Double> weights = caseOutcomeWeights.weights();
    Map<String, double[]> workerStats = new HashMap<>();

    for (var exp : experiences) {
      if (exp.planTrace().size() < 2) {
        continue;
      }
      double relevance = Math.max(0.0, exp.similarityScore());
      if (relevance == 0.0) {
        continue;
      }
      double caseWeight = weights.getOrDefault(exp.outcome(), 0.0);
      if (caseWeight == 0.0) {
        continue;
      }

      for (var step : exp.planTrace()) {
        if (context.capabilityName().equals(step.capabilityName())
            && step.workerName() != null
            && eligibleIds.contains(step.workerName())) {
          var stats =
              workerStats.computeIfAbsent(step.workerName(), k -> new double[] {0.0, 0.0});
          stats[0] += caseWeight * relevance;
          stats[1] += relevance;
        }
      }
    }

    if (workerStats.isEmpty()) {
      return null;
    }

    Map<String, RoutingSignal.CandidateSignal> candidates = new HashMap<>();
    for (var entry : workerStats.entrySet()) {
      double evidenceMass = entry.getValue()[1];
      if (evidenceMass > 0.0) {
        double score = entry.getValue()[0] / evidenceMass;
        candidates.put(entry.getKey(),
            new RoutingSignal.CandidateSignal(score, "plan-composition analysis"));
      }
    }

    return candidates.isEmpty() ? null : new RoutingSignal(candidates);
  }
}
```

- [ ] **Step 6: Run tests**

Run: `mvn --batch-mode test -pl . -Dtest=PlanCompositionAnalyserTest`
Expected: ALL pass.

- [ ] **Step 7: Run full test suite**

Run: `mvn --batch-mode test`
Expected: ALL pass — no regressions.

- [ ] **Step 8: Verify with diagnostics**

Run `ide_diagnostics` on all new files and modified files.

- [ ] **Step 9: Update CLAUDE.md**

Remove stale entries: `RoutingFeatureExtractor`, `TextOnlyFeatureExtractor`, `CbrRoutingOutcomeRecorder` from the package table. Add new types: `CbrOutcomeWeights`, `DefaultCbrOutcomeWeights`, `CbrCaseOutcomeWeights`, `DefaultCbrCaseOutcomeWeights`, `PlanCompositionAnalyser`. Update `RoutingSignal`, `RoutingSignalProvider`, `RoutingSignalAssembler` references. Remove `AmlRoutingFeatureExtractor`/`ClinicalRoutingFeatureExtractor` from consumers table.

- [ ] **Step 10: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/routing/agent/CbrCaseOutcomeWeights.java \
  src/main/java/io/casehub/blocks/routing/agent/DefaultCbrCaseOutcomeWeights.java \
  src/main/java/io/casehub/blocks/routing/agent/PlanCompositionAnalyser.java \
  src/test/java/io/casehub/blocks/routing/agent/PlanCompositionAnalyserTest.java \
  CLAUDE.md
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#32): PlanCompositionAnalyser — multi-step plan scoring via RoutingSignalProvider

Implements RoutingSignalProvider that scores candidates based on
case-level outcomes in multi-step plans. CbrCaseOutcomeWeights SPI
provides configurable case-level outcome weights (COMPLETED/FAULTED/
CANCELLED). Returns null when no multi-step plan data exists.

Closes #32
Refs #34, #37"
```

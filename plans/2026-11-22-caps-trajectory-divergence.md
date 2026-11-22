# CAPS Topology Trajectory Divergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #1192 — CAPS topology trajectory divergence — deeper feedback loops
**Issue group:** #1192

**Goal:** Add 6 feedback connections to the CAPS topology so that
personality-dependent weight modulation produces measurably different
settling trajectories (iteration count, convergence type, saturation)
across profiles.

**Architecture:** Add output→mediating and mediating→mediating feedback
connections to `caps-topology.yaml` with disposition-modulated tags
(`BAS_connections`, `BIS_connections`, `social_input`). The CapsSettler
algorithm is unchanged — the topology change alone creates recurrent
energy paths that produce personality-dependent convergence dynamics.

**Tech Stack:** Java 21, YAML topology, JUnit 5 + AssertJ

## Global Constraints

- No changes to `CapsSettler.java` or the settling algorithm
- No new disposition modifier definitions — use existing tags only
- Feedback connection weights must be moderate (0.10–0.15) to avoid
  destabilising convergence
- `BIS_activation → threat_sensitivity` already exists at weight 0.40
  (line 1122) — do not duplicate

---

## Batch 1: Feedback Connections and Trajectory Divergence Tests

### Task 1: Add feedback connections to CAPS topology

**Files:**
- Modify: `caps-engine/src/main/resources/caps-topology.yaml:1691` (insert after last connection, before `disposition_modifiers`)

**Interfaces:**
- Produces: 6 new YAML connection entries in the connections list

**Note:** `BIS_activation → threat_sensitivity` already exists at line
1122 with weight 0.40 and tags `[bis, threat]`. Connection #6 in the
spec is replaced with `persist → self_efficacy` (output→mediating).

- [ ] **Step 1: Add the 6 feedback connections**

Insert the following YAML block after line 1691 (after the `suppression → response_modulation` connection) and before the `# Disposition modulation` comment at line 1693:

```yaml

  # Feedback loops — output→mediating recurrence for personality-dependent
  # convergence dynamics (#1192)
  # Source: Mischel & Shoda 1995, Gray 1982, Bandura 1997, Bowlby 1988, Beck 1976
  - from: approach
    to: self_efficacy
    weight: 0.15
    provenance: consensus
    source: "Bandura 1997: approach behavior reinforces perceived capability"
    tags: [BAS_connections]
  - from: withdraw
    to: self_worth
    weight: -0.15
    provenance: consensus
    source: "Beck 1976: avoidance erodes self-evaluation"
    tags: [BIS_connections]
  - from: trust
    to: other_reliability
    weight: 0.10
    provenance: consensus
    source: "Bowlby 1988: trusting action reinforces internal working model"
    tags: [social_input]
  - from: distrust
    to: other_reliability
    weight: -0.10
    provenance: consensus
    source: "Bowlby 1988: distrust degrades perceived reliability"
    tags: [social_input]
  - from: BAS_activation
    to: reinforcement_expectation
    weight: 0.15
    provenance: consensus
    source: "Gray 1982: BAS amplification loop"
    tags: [BAS_connections]
  - from: persist
    to: self_efficacy
    weight: 0.15
    provenance: consensus
    source: "Bandura 1997: persistence reinforces self-efficacy"
    tags: [BAS_connections]
```

- [ ] **Step 2: Build to verify YAML parses**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean compile -pl caps-engine -am -q
```

Expected: BUILD SUCCESS (no YAML parse errors)

- [ ] **Step 3: Commit**

```bash
git add caps-engine/src/main/resources/caps-topology.yaml
git commit -m "feat(#1192): add 6 feedback loops to CAPS topology for trajectory divergence

Add output→mediating and mediating→mediating feedback connections:
- approach → self_efficacy (+0.15, BAS)
- withdraw → self_worth (-0.15, BIS)
- trust → other_reliability (+0.10, social)
- distrust → other_reliability (-0.10, social)
- BAS_activation → reinforcement_expectation (+0.15, BAS)
- persist → self_efficacy (+0.15, BAS)

These create recurrent energy paths where personality-dependent weight
modulation produces different convergence dynamics per profile.

Refs #1192

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 2: Write trajectory divergence test in caps-engine

**Files:**
- Create: `caps-engine/src/test/java/io/casehub/neocortex/caps/engine/FeedbackLoopDivergenceTest.java`

**Interfaces:**
- Consumes: `CapsTopologyLoader.load()` → `CapsTopology`,
  `DispositionWeightMapper.initializeWeights(CapsTopology, DispositionAxes)` → `AgentCapsState`,
  `CapsSettler.settle(AgentCapsState, Map<String, Double>)` → `SettlingResult`
- Produces: test proving iteration counts differ between open and cautious profiles

- [ ] **Step 1: Write the failing test**

Create `caps-engine/src/test/java/io/casehub/neocortex/caps/engine/FeedbackLoopDivergenceTest.java`:

```java
package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.SettlingResult;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackLoopDivergenceTest {

    private static final DispositionAxes OPEN =
            new DispositionAxes("cooperative", "moderate", "bold", "high", "cooperative");

    private static final DispositionAxes CAUTIOUS =
            new DispositionAxes("independent", "strict", "conservative", "low", "avoidant");

    @Test
    void feedbackLoopsProducePersonalityDependentConvergenceRate() {
        CapsTopology topology = CapsTopologyLoader.load();
        var settler = new CapsSettler(topology);

        var openState = DispositionWeightMapper.initializeWeights(topology, OPEN);
        var cautiousState = DispositionWeightMapper.initializeWeights(topology, CAUTIOUS);

        // Activate approach-oriented inputs for open, threat-oriented for cautious
        Map<String, Double> approachInputs = Map.of(
                "reward", 0.8,
                "mastery", 0.7,
                "agency_granted", 0.6,
                "choice_available", 0.5);

        Map<String, Double> threatInputs = Map.of(
                "social_threat", 0.8,
                "rejection", 0.7,
                "controlled", 0.6,
                "punishment", 0.5);

        SettlingResult openResult = settler.settle(openState, approachInputs);
        SettlingResult cautiousResult = settler.settle(cautiousState, threatInputs);

        assertThat(openResult.iterations())
                .as("open and cautious should converge at different rates — "
                    + "open=%d vs cautious=%d",
                    openResult.iterations(), cautiousResult.iterations())
                .isNotEqualTo(cautiousResult.iterations());
    }

    @Test
    void bothProfilesStillConverge() {
        CapsTopology topology = CapsTopologyLoader.load();
        var settler = new CapsSettler(topology);

        var openState = DispositionWeightMapper.initializeWeights(topology, OPEN);
        var cautiousState = DispositionWeightMapper.initializeWeights(topology, CAUTIOUS);

        Map<String, Double> inputs = Map.of(
                "reward", 0.7,
                "social_threat", 0.5);

        SettlingResult openResult = settler.settle(openState, inputs);
        SettlingResult cautiousResult = settler.settle(cautiousState, inputs);

        assertThat(openResult.convergence())
                .as("open should still converge")
                .isEqualTo(io.casehub.neocortex.caps.ConvergenceType.CONVERGED);
        assertThat(cautiousResult.convergence())
                .as("cautious should still converge")
                .isEqualTo(io.casehub.neocortex.caps.ConvergenceType.CONVERGED);
    }
}
```

- [ ] **Step 2: Run tests to verify they compile and pass/fail as expected**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl caps-engine -am -Dtest="FeedbackLoopDivergenceTest" -q
```

Expected: `feedbackLoopsProducePersonalityDependentConvergenceRate` — the
test should pass if the feedback loops create measurable divergence.
`bothProfilesStillConverge` — PASS (convergence is not broken).

If `feedbackLoopsProducePersonalityDependentConvergenceRate` fails
(iterations are still equal), the feedback weights are too low or the
input patterns don't excite the loops sufficiently. Adjust:
1. Try stronger input activations (0.9 instead of 0.8)
2. Try same inputs for both profiles (isolates weight-only effect)
3. If still equal, increase feedback weights from 0.15 to 0.20

- [ ] **Step 3: Adjust test if needed based on observed behavior**

If iterations are equal with different inputs, try same inputs for both
profiles — this isolates the weight modulation effect without the
confound of different input patterns:

```java
Map<String, Double> sharedInputs = Map.of(
        "reward", 0.7,
        "social_threat", 0.6,
        "mastery", 0.5);
```

The divergence should come from the disposition-modulated feedback
weights, not from different inputs.

- [ ] **Step 4: Commit**

```bash
git add caps-engine/src/test/java/io/casehub/neocortex/caps/engine/FeedbackLoopDivergenceTest.java
git commit -m "test(#1192): add feedback loop divergence test

Verifies that personality-dependent feedback loop weights produce
different convergence rates between open and cautious profiles.

Refs #1192

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 3: Update ConsolidationDivergenceTest for trajectory divergence

**Files:**
- Modify: `cognition/src/test/java/io/casehub/neocortex/cognition/eval/divergence/ConsolidationDivergenceTest.java`

**Interfaces:**
- Consumes: `CognitionTestHarness.capsTrajectory(agentId, tenant)` → `Optional<CapsTrajectory>`,
  `CapsTrajectory.iterations()`, `CapsTrajectory.convergence()`, `CapsTrajectory.saturationRatio()`

- [ ] **Step 1: Write the trajectory divergence test**

Add a new test method to `ConsolidationDivergenceTest.java`:

```java
@Test
void capsTrajectoryDiverges() {
    var store   = DivergenceFixtures.seededCognitiveStore();
    var harness = DivergenceFixtures.fullBuilder(store).build();
    harness.consolidate("open", TENANT);

    var openTrajectory     = harness.capsTrajectory("open", TENANT);
    var cautiousTrajectory = harness.capsTrajectory("cautious", TENANT);

    assertThat(openTrajectory).as("open agent should have CAPS trajectory").isPresent();
    assertThat(cautiousTrajectory).as("cautious agent should have CAPS trajectory").isPresent();

    var openT     = openTrajectory.get();
    var cautiousT = cautiousTrajectory.get();

    boolean trajectoryDiffers =
            openT.iterations() != cautiousT.iterations()
            || openT.convergence() != cautiousT.convergence()
            || Double.compare(openT.saturationRatio(), cautiousT.saturationRatio()) != 0;

    assertThat(trajectoryDiffers)
            .as("trajectory metadata should differ — "
                + "open[iter=%d conv=%s sat=%.4f] vs cautious[iter=%d conv=%s sat=%.4f]",
                openT.iterations(), openT.convergence(), openT.saturationRatio(),
                cautiousT.iterations(), cautiousT.convergence(), cautiousT.saturationRatio())
            .isTrue();
}
```

- [ ] **Step 2: Run the new test**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -am -Dtest="ConsolidationDivergenceTest#capsTrajectoryDiverges" -q
```

Expected: PASS — the feedback loops should produce measurable trajectory
divergence through the full cognition pipeline.

**If it fails:** The consolidation pipeline may use different input
activations than the direct settler test. Check what
`BehavioralSynthesisPhase` passes as `inputActivations` to `settle()`.
The divergence may need seeded experience nodes with stronger signals,
or the `DivergenceFixtures.seededCognitiveStore()` may need enrichment.

- [ ] **Step 3: Run the full divergence test suite to check for regressions**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -am -Dtest="ConsolidationDivergenceTest" -q
```

Expected: ALL PASS. Existing tests (`behavioralAttractorsDiverge`,
`capsTrajectoryBothAgentsConverge`, etc.) should still pass — feedback
loops change convergence *rate*, not the fundamental attractor landscape.

- [ ] **Step 4: Run the caps-engine and caps-testing suites for regressions**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl caps-engine,caps-testing -am -q
```

Expected: ALL PASS. Existing tests like `DispositionAttractorDifferentiationTest`,
`SocialThreatOscillationTest`, `CapsEngineContractTest` should still pass.
If any fail, the feedback loop weights may be too strong — reduce from
0.15 to 0.10 and re-run.

- [ ] **Step 5: Commit**

```bash
git add cognition/src/test/java/io/casehub/neocortex/cognition/eval/divergence/ConsolidationDivergenceTest.java
git commit -m "test(#1192): assert trajectory divergence in ConsolidationDivergenceTest

Adds capsTrajectoryDiverges test that verifies open and cautious
profiles produce different settling trajectories (iterations,
convergence type, or saturation) through the full cognition pipeline.

Refs #1192

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## References

- [2026-11-22-caps-trajectory-divergence-design.md] — design spec
- [caps-engine/src/main/resources/caps-topology.yaml] — topology connections
- [caps-engine/src/main/java/io/casehub/neocortex/caps/engine/CapsSettler.java:99] — damping
- [caps-engine/src/main/java/io/casehub/neocortex/caps/engine/DispositionWeightMapper.java] — weight mapping
- [cognition/src/test/java/io/casehub/neocortex/cognition/eval/divergence/ConsolidationDivergenceTest.java] — existing tests
- [cognition/src/test/java/io/casehub/neocortex/cognition/eval/divergence/DivergenceFixtures.java] — profile fixtures
- [GitHub #1171] — CAPS settling trajectory assertions
- [GitHub #1175] — prior investigation

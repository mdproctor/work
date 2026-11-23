# Direct CAPS Settling Pathway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #1212 — Evaluate direct CAPS settling pathway for dissonance behavioural effects
**Issue group:** #1212 (child of #593)

**Goal:** Inject dissonance-derived CAPS activations directly into BehavioralSynthesisPhase's settling pass, giving the CAPS settler the actual competing belief activations per Shultz & Lepper (1996) constraint satisfaction.

**Architecture:** Define a `DissonanceActivationSource` SPI in `cognition-api` to break the circular module dependency. `DissonanceDetectionPhase` (cognition) implements the SPI and exposes CAPS activation maps from its cache. `BehavioralSynthesisPhase` (mindmap-intelligence) injects the SPI via `Instance<T>` and merges tension-strength-scaled activations into the combined CAPS activation map using `Math::max`. DissonanceDetectionPhase moves to `@Priority(17)` to run before BehavioralSynthesisPhase (19).

**Tech Stack:** Java 21 (on Java 26 JVM), Quarkus CDI, CAPS engine

## Global Constraints

- No new configuration properties — existing `casehub.cognition.dissonance.enabled` gates everything
- No changes to caps-api, caps-engine, mindmap-api, DissonancePromptSection, DissonanceTickParticipant
- Both direct and indirect CAPS pathways coexist
- `Instance<T>` graceful degradation — mergeDissonanceActivations is a no-op when dissonance is disabled

---

## Batch 1: SPI + Cache Extension + Merge

### Task 1: DissonanceActivationSource SPI and TensionActivation record in cognition-api

**Files:**
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/dissonance/DissonanceActivationSource.java`
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/dissonance/TensionActivation.java`

**Interfaces:**
- Consumes: nothing (new SPI)
- Produces: `DissonanceActivationSource` interface with `List<TensionActivation> activeTensions(String cacheKey)`. `TensionActivation` record with `Map<String, Double> capsActivationsA()`, `Map<String, Double> capsActivationsB()`, `double strength()`.

- [ ] **Step 1: Create TensionActivation record**

```java
package io.casehub.neocortex.cognition.dissonance;

import java.util.Map;

public record TensionActivation(
    Map<String, Double> capsActivationsA,
    Map<String, Double> capsActivationsB,
    double strength
) {
    public TensionActivation {
        capsActivationsA = capsActivationsA != null ? Map.copyOf(capsActivationsA) : Map.of();
        capsActivationsB = capsActivationsB != null ? Map.copyOf(capsActivationsB) : Map.of();
    }
}
```

- [ ] **Step 2: Create DissonanceActivationSource SPI**

```java
package io.casehub.neocortex.cognition.dissonance;

import java.util.List;

@FunctionalInterface
public interface DissonanceActivationSource {
    List<TensionActivation> activeTensions(String cacheKey);
}
```

- [ ] **Step 3: Compile cognition-api**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean compile -pl cognition-api`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add cognition-api/src/main/java/io/casehub/neocortex/cognition/dissonance/TensionActivation.java
git add cognition-api/src/main/java/io/casehub/neocortex/cognition/dissonance/DissonanceActivationSource.java
git commit -m "feat(cognition-api): add DissonanceActivationSource SPI and TensionActivation

Refs #1212"
```

---

### Task 2: DissonanceDetectionPhase — reorder + implement SPI + extend cache

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/DissonanceDetectionPhase.java:28` (priority), `:74` (activeTensions), `:271` (updateCache)
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/TensionSnapshot.java` (add CAPS activation fields)
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/dissonance/DissonanceDetectionPhaseTest.java`

**Interfaces:**
- Consumes: `DissonanceActivationSource` SPI from Task 1
- Produces: `DissonanceDetectionPhase implements DissonanceActivationSource`. Cache now exposes CAPS activation maps via `activeTensions(String cacheKey) → List<TensionActivation>`.

- [ ] **Step 1: Write failing test — SPI returns CAPS activations from cache**

Add to `DissonanceDetectionPhaseTest.java`:

```java
@Test
void activeTensionsSPI_returnsCapsActivations() {
    // Setup: create a tension node with caps-activations-a/b properties
    var props = new HashMap<String, String>();
    props.put("tension-type", "belief-belief");
    props.put("element-a-id", "nodeA");
    props.put("element-b-id", "nodeB");
    props.put("element-a-text", "I am loyal");
    props.put("element-b-text", "I betray for power");
    props.put("strength", "0.85");
    props.put("caps-activations-a", "{\"acceptance\":0.7,\"sharing\":0.5}");
    props.put("caps-activations-b", "{\"dominance\":0.8,\"competition\":0.7}");
    props.put("reasoning", "contradiction");
    props.put("agent-id", AGENT_ID);
    props.put("detected-at", Instant.now().toString());
    props.put("last-confirmed", Instant.now().toString());
    props.put("decay-count", "0");

    store.addNode(
        NodeInput.of("test tension", cognitiveSubgraphId)
            .withTraits(Set.of("DissonanceTension"))
            .withProperties(props),
        TENANT_ID);

    // Act: trigger cache update by running the phase
    // (or call updateCache directly if accessible)
    DissonanceActivationSource source = phase;
    var activations = source.activeTensions(TENANT_ID + ":" + AGENT_ID);

    // Assert
    assertThat(activations).hasSize(1);
    var ta = activations.get(0);
    assertThat(ta.strength()).isEqualTo(0.85);
    assertThat(ta.capsActivationsA()).containsEntry("acceptance", 0.7);
    assertThat(ta.capsActivationsA()).containsEntry("sharing", 0.5);
    assertThat(ta.capsActivationsB()).containsEntry("dominance", 0.8);
    assertThat(ta.capsActivationsB()).containsEntry("competition", 0.7);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=DissonanceDetectionPhaseTest#activeTensionsSPI_returnsCapsActivations`
Expected: FAIL — DissonanceDetectionPhase does not implement DissonanceActivationSource

- [ ] **Step 3: Add CAPS activation fields to TensionSnapshot**

Modify `TensionSnapshot.java` to add `capsActivationsA` and `capsActivationsB`:

```java
package io.casehub.neocortex.cognition.dissonance;

import java.util.Map;

public record TensionSnapshot(
    String tensionNodeId,
    String tensionType,
    String elementAId,
    String elementBId,
    String elementAText,
    String elementBText,
    double strength,
    String reasoning,
    Map<String, Double> capsActivationsA,
    Map<String, Double> capsActivationsB
) {}
```

- [ ] **Step 4: Implement SPI on DissonanceDetectionPhase**

Change line 28 from `@Priority(25)` to `@Priority(17)`.

Add `implements DissonanceActivationSource` to the class declaration at line 29.

Add the SPI method implementation — a bridge from TensionSnapshot to TensionActivation:

```java
@Override
public List<TensionActivation> activeTensions(String cacheKey) {
    var snapshots = cache.getOrDefault(cacheKey, List.of());
    return snapshots.stream()
        .map(s -> new TensionActivation(s.capsActivationsA(), s.capsActivationsB(), s.strength()))
        .toList();
}
```

- [ ] **Step 5: Update updateCache() to include CAPS activation maps**

Modify `updateCache()` at line 271 to parse `caps-activations-a` and `caps-activations-b` from the MindMap node properties:

```java
private void updateCache(String agentId, String tenantId, String cognitiveSubgraphId) {
    var snapshots = new ArrayList<TensionSnapshot>();
    for (var node : store.nodesIn(cognitiveSubgraphId, tenantId)) {
        if (node.traits().contains(TENSION_TRAIT)
                && agentId.equals(node.properties().get("agent-id"))) {
            snapshots.add(new TensionSnapshot(
                node.id(),
                node.property("tension-type").orElse(""),
                node.property("element-a-id").orElse(""),
                node.property("element-b-id").orElse(""),
                node.property("element-a-text").orElse(""),
                node.property("element-b-text").orElse(""),
                Double.parseDouble(node.property("strength").orElse("0")),
                node.property("reasoning").orElse(""),
                parseCapsActivations(node.property("caps-activations-a").orElse("")),
                parseCapsActivations(node.property("caps-activations-b").orElse(""))));
        }
    }
    cache.put(tenantId + ":" + agentId, List.copyOf(snapshots));
}

private static Map<String, Double> parseCapsActivations(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var type = mapper.getTypeFactory()
            .constructMapType(java.util.HashMap.class, String.class, Double.class);
        return Map.copyOf(mapper.readValue(json, type));
    } catch (Exception e) {
        return Map.of();
    }
}
```

- [ ] **Step 6: Fix existing tests that construct TensionSnapshot**

Update all existing test sites that construct `TensionSnapshot` to include the two new fields (pass `Map.of()` for the CAPS activation maps where activations aren't relevant to the test).

- [ ] **Step 7: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=DissonanceDetectionPhaseTest`
Expected: ALL PASS

- [ ] **Step 8: Run full module tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition`
Expected: BUILD SUCCESS (all tests pass)

- [ ] **Step 9: Commit**

```bash
git add cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/DissonanceDetectionPhase.java
git add cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/TensionSnapshot.java
git add cognition/src/test/java/io/casehub/neocortex/cognition/dissonance/DissonanceDetectionPhaseTest.java
git commit -m "feat(cognition): DissonanceDetectionPhase implements DissonanceActivationSource

Move to @Priority(17), extend cache with CAPS activation maps,
add SPI bridge method activeTensions(cacheKey).

Refs #1212"
```

---

### Task 3: BehavioralSynthesisPhase — merge dissonance activations

**Files:**
- Modify: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/BehavioralSynthesisPhase.java:66-74` (add injection), `:197` (add merge call), `:497` (add method after mergeDriveIntensities)
- Test: `mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/BehavioralSynthesisPhaseTest.java`

**Interfaces:**
- Consumes: `DissonanceActivationSource` SPI from Task 1, implemented by Task 2
- Produces: `mergeDissonanceActivations()` private method. CAPS settling now includes dissonance-derived activations.

- [ ] **Step 1: Write failing test — dissonance activations merged into settling**

Add to `BehavioralSynthesisPhaseTest.java`:

```java
@Test
void mergeDissonanceActivations_scalesByStrengthAndMergesMax() {
    // Setup: create a DissonanceActivationSource that returns one tension
    var tensionActivation = new TensionActivation(
        Map.of("acceptance", 0.7, "sharing", 0.5),
        Map.of("dominance", 0.8, "competition", 0.7),
        0.85  // strength
    );
    DissonanceActivationSource source = cacheKey -> List.of(tensionActivation);

    // Create a combined activation map with existing experience activations
    var combined = new HashMap<String, Double>();
    combined.put("acceptance", 0.3);  // lower than scaled dissonance (0.7*0.85=0.595)
    combined.put("dominance", 0.9);   // higher than scaled dissonance (0.8*0.85=0.68)

    // Act: invoke mergeDissonanceActivations via reflection or test-visible method
    // (the method is private — test through the full run() path with mocks,
    //  or use a package-private accessor)

    // Assert: acceptance should be 0.595 (dissonance wins), dominance stays 0.9 (experience wins)
    // sharing should be 0.425 (new from dissonance), competition should be 0.595 (new from dissonance)
}
```

The exact test structure depends on how BehavioralSynthesisPhaseTest is currently structured. The test should:
1. Provide a mock/stub `DissonanceActivationSource` with known activations
2. Set up graduated nodes that produce known experience activations
3. Verify the resulting combined activations include dissonance contributions
4. Verify `Math::max` merge (dissonance only wins when scaled value exceeds experience)

- [ ] **Step 2: Write test for graceful degradation — no dissonance source**

```java
@Test
void mergeDissonanceActivations_noOpWhenSourceNotResolvable() {
    // Setup: Instance<DissonanceActivationSource> not resolvable
    // Act: run() completes normally
    // Assert: settling uses only experience + drive activations (no NPE, no change)
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=BehavioralSynthesisPhaseTest`
Expected: FAIL — mergeDissonanceActivations does not exist

- [ ] **Step 4: Add CDI injection to BehavioralSynthesisPhase**

Add after the existing `@Inject` block (around line 74):

```java
@Inject
Instance<DissonanceActivationSource> dissonanceActivationSource;
```

Add import: `import io.casehub.neocortex.cognition.dissonance.DissonanceActivationSource;`
Add import: `import io.casehub.neocortex.cognition.dissonance.TensionActivation;`

- [ ] **Step 5: Add mergeDissonanceActivations() method**

Add after `mergeDriveIntensities()` (after line ~530):

```java
private void mergeDissonanceActivations(Map<String, Double> combinedActivations,
                                        String agentId, String tenantId) {
    if (!dissonanceActivationSource.isResolvable()) {
        return;
    }

    String cacheKey = tenantId + ":" + agentId;
    List<TensionActivation> tensions = dissonanceActivationSource.get()
        .activeTensions(cacheKey);
    if (tensions == null || tensions.isEmpty()) {
        return;
    }

    for (TensionActivation tension : tensions) {
        double strength = tension.strength();
        mergeActivationMap(combinedActivations, tension.capsActivationsA(), strength);
        mergeActivationMap(combinedActivations, tension.capsActivationsB(), strength);
    }
}

private void mergeActivationMap(Map<String, Double> target,
                                Map<String, Double> source, double scale) {
    if (source == null) return;
    for (var entry : source.entrySet()) {
        double scaled = entry.getValue() * scale;
        target.merge(entry.getKey(), scaled, Math::max);
    }
}
```

- [ ] **Step 6: Wire the call in run()**

Add call after `mergeDriveIntensities()` at line ~197:

```java
mergeDriveIntensities(combinedActivations, agentId, tenantId);
mergeDissonanceActivations(combinedActivations, agentId, tenantId); // ← ADD
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=BehavioralSynthesisPhaseTest`
Expected: ALL PASS

- [ ] **Step 8: Run full build to verify no regressions**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean test -pl cognition-api,cognition,mindmap-intelligence`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/BehavioralSynthesisPhase.java
git add mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/BehavioralSynthesisPhaseTest.java
git commit -m "feat(mindmap-intelligence): merge dissonance activations into CAPS settling

Add mergeDissonanceActivations() — reads DissonanceActivationSource
SPI, scales by tension strength, merges via Math::max. Called after
mergeDriveIntensities() in the settling pass.

Closes #1212"
```

---

## References

- `specs/issue-1212-direct-caps-evaluation/2026-11-23-direct-caps-evaluation-design.md` — design spec
- `specs/issue-1212-direct-caps-evaluation/decisions.md` — D1-D6 decisions
- `cognition/src/main/java/.../DissonanceDetectionPhase.java:28,74,271` — priority, activeTensions, updateCache
- `cognition/src/main/java/.../TensionSnapshot.java` — record missing CAPS fields
- `mindmap-intelligence/src/main/java/.../BehavioralSynthesisPhase.java:43,66-74,197,497` — priority, injections, merge call, merge method
- `cognition-api/src/main/java/.../dissonance/DissonanceDetectionConfig.java` — existing dissonance package in cognition-api
- casehubio/neocortex#1212 — focal issue
- casehubio/neocortex#593 — parent issue, D4 effect path constraint

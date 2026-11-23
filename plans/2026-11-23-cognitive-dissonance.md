# Cognitive Dissonance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #593 — Cognitive dissonance: constraint satisfaction resolution (Shultz & Lepper 1996)

**Goal:** Detect contradictions between an agent's beliefs and between beliefs and behavioral patterns, produce persistent tension state, adjust PAD, and render felt conflict in the cognitive prompt.

**Architecture:** New consolidation phase (`DissonanceDetectionPhase`, `@Priority(25)`) in the `cognition` module uses a single LLM call per agent per consolidation tick to detect contradictions among Belieflike nodes and CapsGenerated behavioral attractors. Detected contradictions are persisted as `DissonanceTension`-trait nodes in MindMap and cached in-memory for per-tick PAD adjustment (`DissonanceTickParticipant`) and prompt rendering (`DissonancePromptSection`). Mechanical decay provides interim resolution; designed for future coping integration (#595).

**Tech Stack:** Java 21, Quarkus 3.32.2, CDI, MindMapStore SPI, SessionPipeline (LLM calls), Jackson (JSON parsing)

## Global Constraints

- Java 21 source on Java 26 JVM. Build: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install`
- Use `mvn` not `./mvnw`
- All commits reference issue: `Refs #593` or `Closes #593`
- Use IntelliJ MCP (`ide_insert_member`, `ide_replace_member`) for code editing, not bash
- Follow existing patterns: BeliefRevisionPhase for consolidation phases, BeliefRevisionConfig for config records
- Test with InMemoryMindMapStore (`@Alternative @Priority(2)`)
- Module: `cognition-api` for types, `cognition` for implementation

---

## Batch 1: Detection Foundation

### Task 1: DissonanceDetectionConfig, TensionSnapshot, and DissonanceDetectionPhase

**Files:**
- Create: `cognition-api/src/main/java/io/casehub/neocortex/cognition/dissonance/DissonanceDetectionConfig.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/TensionSnapshot.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/DissonanceDetectionPhase.java`
- Create: `cognition/src/test/java/io/casehub/neocortex/cognition/dissonance/DissonanceDetectionPhaseTest.java`
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/CognitionDefaultBeans.java` — add `@DefaultBean` producer for `DissonanceDetectionConfig`

**Interfaces:**
- Consumes: `MindMapStore` SPI, `SessionPipeline` (set via `setSessionPipeline()`), `ConsolidationPhase` SPI
- Produces: `TensionSnapshot` records accessible via `activeTensions(String agentId, String tenantId)` — consumed by Task 2's DissonanceTickParticipant and DissonancePromptSection

- [ ] **Step 1: Write failing tests for the phase lifecycle**

Create the test class with tests for skip-when-disabled, tension creation, confirmation, decay, supersession, and deduplication. Use InMemoryMindMapStore and a mock SessionPipeline that returns canned JSON.

```java
package io.casehub.neocortex.cognition.dissonance;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.agent.session.SessionPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DissonanceDetectionPhaseTest {

    private InMemoryMindMapStore store;
    private DissonanceDetectionPhase phase;
    private StubSessionPipeline sessionPipeline;
    private static final String TENANT = "test-tenant";
    private static final String AGENT = "agent-1";

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        phase = new DissonanceDetectionPhase(store, DissonanceDetectionConfig.defaults());
        sessionPipeline = new StubSessionPipeline();
        phase.setSessionPipeline(sessionPipeline);
    }

    @Test
    void skipsWhenDisabled() {
        var disabled = new DissonanceDetectionPhase(store,
            new DissonanceDetectionConfig(false, 0.1, 0.15, 0.3, 3,
                0.15, 0.3, 0.10, 0.2, 0.05, 0.1, 0.5));
        disabled.setSessionPipeline(sessionPipeline);
        seedBeliefs("I am kind", "I enjoy cruelty");
        disabled.run(TENANT, List.of());
        assertTrue(tensionNodes().isEmpty());
    }

    @Test
    void skipsWhenNoSessionPipeline() {
        var noLlm = new DissonanceDetectionPhase(store, DissonanceDetectionConfig.defaults());
        seedBeliefs("I am kind", "I enjoy cruelty");
        noLlm.run(TENANT, List.of());
        assertTrue(tensionNodes().isEmpty());
    }

    @Test
    void createsTensionNodeWhenContradictionDetected() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        sessionPipeline.setResponse(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.85));
        phase.run(TENANT, List.of());

        var tensions = tensionNodes();
        assertEquals(1, tensions.size());
        var t = tensions.getFirst();
        assertTrue(t.traits().contains("DissonanceTension"));
        assertEquals("0.85", t.property("strength").orElse(""));
        assertEquals("belief-belief", t.property("tension-type").orElse(""));
        assertEquals(AGENT, t.property("agent-id").orElse(""));
    }

    @Test
    void confirmsTensionWhenRedetected() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        sessionPipeline.setResponse(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.85));
        phase.run(TENANT, List.of());
        String firstConfirmed = tensionNodes().getFirst()
            .property("last-confirmed").orElse("");

        sessionPipeline.setResponse(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.90));
        phase.run(TENANT, List.of());

        var tensions = tensionNodes();
        assertEquals(1, tensions.size());
        String secondConfirmed = tensions.getFirst()
            .property("last-confirmed").orElse("");
        assertNotEquals(firstConfirmed, secondConfirmed);
        assertEquals("0", tensions.getFirst()
            .property("decay-count").orElse(""));
    }

    @Test
    void decaysAdditivelyWhenNotRedetected() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        sessionPipeline.setResponse(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.50));
        phase.run(TENANT, List.of());

        sessionPipeline.setResponse("{\"contradictions\": []}");
        phase.run(TENANT, List.of());

        var tensions = tensionNodes();
        assertEquals(1, tensions.size());
        double strength = Double.parseDouble(
            tensions.getFirst().property("strength").orElse("0"));
        assertEquals(0.40, strength, 0.001);
        assertEquals("1", tensions.getFirst()
            .property("decay-count").orElse(""));
    }

    @Test
    void supersedesWhenBelowThreshold() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        sessionPipeline.setResponse(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.20));
        phase.run(TENANT, List.of());
        assertEquals(1, tensionNodes().size());

        sessionPipeline.setResponse("{\"contradictions\": []}");
        phase.run(TENANT, List.of());
        // 0.20 - 0.10 = 0.10, below threshold 0.15 → superseded
        var active = tensionNodes().stream()
            .filter(n -> n.supersessionStatus() == null
                || !n.supersessionStatus().superseded())
            .toList();
        assertTrue(active.isEmpty());
    }

    @Test
    void deduplicatesOrderIndependentPairs() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        sessionPipeline.setResponse(contradictionJson(
            "belief-2", "I enjoy cruelty", "belief-1", "I am kind", 0.85));
        phase.run(TENANT, List.of());
        // Same pair, reversed order — should match existing
        sessionPipeline.setResponse(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.90));
        phase.run(TENANT, List.of());

        assertEquals(1, tensionNodes().size());
    }

    @Test
    void populatesInMemoryCache() {
        seedBeliefs("I am kind", "I enjoy cruelty");
        sessionPipeline.setResponse(contradictionJson(
            "belief-1", "I am kind", "belief-2", "I enjoy cruelty", 0.85));
        phase.run(TENANT, List.of());

        var cached = phase.activeTensions(AGENT, TENANT);
        assertEquals(1, cached.size());
        assertEquals(0.85, cached.getFirst().strength(), 0.001);
    }

    // --- helpers ---

    private String beliefSgId;

    private void seedBeliefs(String... beliefs) {
        beliefSgId = store.createSubgraph(
            new SubgraphInput("Cognitive", SubgraphTypes.COGNITIVE, null), TENANT);
        store.createSubgraph(
            new SubgraphInput("Behavioral", SubgraphTypes.BEHAVIORAL, null), TENANT);
        for (int i = 0; i < beliefs.length; i++) {
            store.addNode(
                NodeInput.of(beliefs[i], beliefSgId)
                    .withTraits(Set.of("Belieflike"))
                    .withProperties(java.util.Map.of("agent-id", AGENT))
                    .withPrincipalId(
                        io.casehub.platform.api.identity.PrincipalId.agent(AGENT)),
                TENANT);
        }
    }

    private List<MindMapNode> tensionNodes() {
        return store.search(MindMapQuery.of(TENANT, 100)
                .withType(SubgraphTypes.COGNITIVE)).stream()
            .filter(n -> n.traits().contains("DissonanceTension"))
            .toList();
    }

    private String contradictionJson(String idA, String textA,
                                      String idB, String textB,
                                      double strength) {
        return """
            {"contradictions": [{
              "elementA": {"nodeId": "%s", "type": "belief", "text": "%s"},
              "elementB": {"nodeId": "%s", "type": "belief", "text": "%s"},
              "strength": %s,
              "capsActivationsA": {"acceptance": 0.7},
              "capsActivationsB": {"dominance": 0.8},
              "reasoning": "test contradiction"
            }]}""".formatted(idA, textA, idB, textB, strength);
    }
}
```

`StubSessionPipeline` is a test-only implementation of `SessionPipeline` that returns canned responses. If one already exists in the test fixtures, reuse it; otherwise create a minimal stub in the test package.

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=DissonanceDetectionPhaseTest -DfailIfNoTests=false`

Expected: compilation failure — `DissonanceDetectionConfig`, `TensionSnapshot`, and `DissonanceDetectionPhase` do not exist yet.

- [ ] **Step 3: Write DissonanceDetectionConfig**

```java
package io.casehub.neocortex.cognition.dissonance;

public record DissonanceDetectionConfig(
    boolean enabled,
    double decayRate,
    double resolutionThreshold,
    double renderingThreshold,
    int maxRendered,
    double arousalWeight,
    double arousalCap,
    double pleasureWeight,
    double pleasureCap,
    double dominanceWeight,
    double dominanceCap,
    double copingTriggerThreshold
) {
    public static DissonanceDetectionConfig defaults() {
        return new DissonanceDetectionConfig(
            false, 0.1, 0.15, 0.3, 3,
            0.15, 0.3, 0.10, 0.2, 0.05, 0.1, 0.5);
    }
}
```

- [ ] **Step 4: Write TensionSnapshot**

Internal value type for the in-memory cache, consumed by the tick participant and prompt section.

```java
package io.casehub.neocortex.cognition.dissonance;

public record TensionSnapshot(
    String tensionNodeId,
    String tensionType,
    String elementAId,
    String elementBId,
    String elementAText,
    String elementBText,
    double strength,
    String reasoning
) {}
```

- [ ] **Step 5: Write DissonanceDetectionPhase**

Follow the `BeliefRevisionPhase` pattern exactly: `@ApplicationScoped`, `@Priority(25)`, implements `ConsolidationPhase`, receives `MindMapStore` and `Instance<DissonanceDetectionConfig>` via `@Inject`, receives `SessionPipeline` via `setSessionPipeline()`.

```java
package io.casehub.neocortex.cognition.dissonance;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.NodeUpdate;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationPhase;
import io.casehub.platform.agent.session.SessionPipeline;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@Priority(25)
public class DissonanceDetectionPhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(
        DissonanceDetectionPhase.class.getName());
    private static final String TENSION_TRAIT = "DissonanceTension";
    private static final String BELIEF_TRAIT = "Belieflike";
    private static final String CAPS_GENERATED_TRAIT = "CapsGenerated";

    private static final String SYSTEM_PROMPT = """
        You are analyzing a character's beliefs and behavioral patterns for \
        cognitive dissonance — contradictions between what they believe and \
        what they do, or between beliefs that cannot both be true.
        For each contradiction found, provide the CAPS input node activations \
        that each element would trigger (using node IDs from the CAPS topology: \
        secure_attachment, rejection, mastery, failure, acceptance, exclusion, \
        dominance, submission, reward, punishment, etc.).
        Respond with JSON only. If no contradictions found, return \
        {"contradictions": []}.
        """;

    private final MindMapStore store;
    private final DissonanceDetectionConfig config;
    private volatile SessionPipeline sessionPipeline;
    private final ConcurrentHashMap<String, List<TensionSnapshot>> cache =
        new ConcurrentHashMap<>();

    @Inject
    public DissonanceDetectionPhase(MindMapStore store,
                                     Instance<DissonanceDetectionConfig> config) {
        this(store,
             config.isResolvable() ? config.get() : DissonanceDetectionConfig.defaults());
    }

    public DissonanceDetectionPhase(MindMapStore store,
                                     DissonanceDetectionConfig config) {
        this.store = store;
        this.config = config;
    }

    @Override
    public String name() { return "dissonance-detection"; }

    @Override
    public void setSessionPipeline(SessionPipeline pipeline) {
        this.sessionPipeline = pipeline;
    }

    public List<TensionSnapshot> activeTensions(String agentId, String tenantId) {
        return cache.getOrDefault(tenantId + ":" + agentId, List.of());
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (!config.enabled() || sessionPipeline == null) return;

        // 1. Collect Belieflike nodes grouped by agent
        // 2. Collect CapsGenerated attractor nodes from BEHAVIORAL subgraph
        // 3. Collect existing DissonanceTension nodes
        // 4. For each agent: build LLM prompt, call, parse JSON response
        // 5. For each detected contradiction:
        //    - If existing tension matches (order-independent element IDs): confirm
        //    - Else: create new tension node
        // 6. For existing tensions NOT re-detected: decay additively
        // 7. Supersede tensions below resolution threshold
        // 8. Update in-memory cache

        var beliefs = collectBeliefs(tenantId);
        var attractors = collectAttractors(tenantId);
        var existingTensions = collectTensions(tenantId);

        for (var agentId : beliefs.keySet()) {
            try {
                processAgent(agentId, beliefs.get(agentId),
                    attractors.getOrDefault(agentId, List.of()),
                    existingTensions.getOrDefault(agentId, List.of()),
                    tenantId);
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                    agentId + ": dissonance detection failed (non-fatal)", e);
            }
        }
    }

    // ... implement collectBeliefs, collectAttractors, collectTensions,
    //     processAgent, buildPrompt, parseResponse, createTension,
    //     confirmTension, decayTension, supersedeTension, updateCache
}
```

The key implementation methods:

**`collectBeliefs(tenantId)`**: Search COGNITIVE subgraph for nodes with `Belieflike` trait, group by `agent-id` property (or `principalId().id()`).

**`collectAttractors(tenantId)`**: Search BEHAVIORAL subgraph for nodes with `CapsGenerated` trait, group by `agent-id` property. Build text representation: `category + " (driven by " + dominant-input + ")"`.

**`collectTensions(tenantId)`**: Search COGNITIVE subgraph for nodes with `DissonanceTension` trait, group by `agent-id` property.

**`processAgent(...)`**: Build user prompt listing beliefs and attractors. Single LLM call via `sessionPipeline`. Parse JSON response. For each detected contradiction: match against existing tensions by element IDs (order-independent — sort the two IDs and compare). Create new or confirm existing. Decay unmatched existing tensions. Supersede below-threshold tensions. Rebuild cache for this agent.

**`buildPrompt(agentId, beliefs, attractors)`**: Format beliefs as numbered list with IDs, format attractors as numbered list with IDs and descriptions. Include existing tension pairs so the LLM can re-assess them.

**`parseResponse(json)`**: Jackson ObjectMapper parse. Extract `contradictions` array. Each entry has `elementA.nodeId`, `elementA.type`, `elementA.text`, `elementB.nodeId`, `elementB.type`, `elementB.text`, `strength`, `capsActivationsA`, `capsActivationsB`, `reasoning`.

**Tension node creation**: `store.addNode(NodeInput.of(name, cognitiveSubgraphId).withTraits(Set.of("DissonanceTension")).withProperties(props), tenantId)` — properties per §3 of the spec.

**Deduplication key**: `min(elementAId, elementBId) + ":" + max(elementAId, elementBId)`.

**Decay**: `newStrength = strength - config.decayRate()`. If `newStrength < config.resolutionThreshold()`: `store.supersede(tensionNodeId, null, "dissonance resolved: strength decayed below threshold", tenantId)`.

**Cache update**: After processing, rebuild `cache.put(tenantId + ":" + agentId, snapshots)` where `snapshots` is a `List<TensionSnapshot>` of all active (non-superseded) tensions.

- [ ] **Step 6: Add DefaultBean producer for DissonanceDetectionConfig**

In `CognitionDefaultBeans.java`, add a producer method following the `BeliefRevisionConfig` pattern:

```java
@Produces @DefaultBean @Singleton
DissonanceDetectionConfig dissonanceDetectionConfig() {
    return DissonanceDetectionConfig.defaults();
}
```

Add the import: `import io.casehub.neocortex.cognition.dissonance.DissonanceDetectionConfig;`

- [ ] **Step 7: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest=DissonanceDetectionPhaseTest`

Expected: all 7 tests PASS.

- [ ] **Step 8: Commit**

```bash
git add cognition-api/src/main/java/io/casehub/neocortex/cognition/dissonance/
git add cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/
git add cognition/src/test/java/io/casehub/neocortex/cognition/dissonance/
git add cognition/src/main/java/io/casehub/neocortex/cognition/CognitionDefaultBeans.java
git commit -m "feat(cognition): DissonanceDetectionPhase — LLM-based contradiction detection with tension lifecycle

Implements the core consolidation phase for cognitive dissonance (#593).
Detects belief-belief and belief-behavior contradictions via LLM,
persists DissonanceTension nodes in MindMap with additive decay,
and maintains an in-memory cache for per-tick consumers.

Refs #593

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 2: Effects and Wiring

### Task 2: DissonanceTickParticipant, DissonancePromptSection, and BlockTag.DISSONANCE

**Files:**
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/DissonanceTickParticipant.java`
- Create: `cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/DissonancePromptSection.java`
- Create: `cognition/src/test/java/io/casehub/neocortex/cognition/dissonance/DissonanceTickParticipantTest.java`
- Create: `cognition/src/test/java/io/casehub/neocortex/cognition/dissonance/DissonancePromptSectionTest.java`
- Modify: `cognition-api/src/main/java/io/casehub/neocortex/cognition/prompt/BlockTag.java:14` — add `DISSONANCE(212)` between DRIVES(210) and CURIOSITY(215)

**Interfaces:**
- Consumes: `DissonanceDetectionPhase.activeTensions(agentId, tenantId)` from Task 1; `DissonanceDetectionConfig` from Task 1
- Produces: PAD deltas (via `CognitionTickContext`), rendered prompt text (via `CognitionPromptRenderer`)

- [ ] **Step 1: Add BlockTag.DISSONANCE**

In `BlockTag.java`, add `DISSONANCE(212),` between `DRIVES(210),` and `CURIOSITY(215),`:

```java
// Tier 2 — State (how you feel right now)
MOOD(200),
DRIVES(210),
DISSONANCE(212),
CURIOSITY(215),
```

- [ ] **Step 2: Write failing tests for DissonanceTickParticipant**

```java
package io.casehub.neocortex.cognition.dissonance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DissonanceTickParticipantTest {

    private static final DissonanceDetectionConfig CONFIG =
        DissonanceDetectionConfig.defaults();

    @Test
    void computesArousalIncrease() {
        var tensions = List.of(
            snapshot(0.8), snapshot(0.6));
        var deltas = DissonanceTickParticipant.computePadDeltas(tensions, CONFIG);
        double expected = Math.min(CONFIG.arousalCap(),
            (0.8 + 0.6) * CONFIG.arousalWeight());
        assertEquals(expected, deltas.arousal(), 0.001);
    }

    @Test
    void computesPleasureDecrease() {
        var tensions = List.of(snapshot(0.8));
        var deltas = DissonanceTickParticipant.computePadDeltas(tensions, CONFIG);
        double expected = -Math.min(CONFIG.pleasureCap(),
            0.8 * CONFIG.pleasureWeight());
        assertEquals(expected, deltas.pleasure(), 0.001);
    }

    @Test
    void computesDominanceFromMaxTension() {
        var tensions = List.of(snapshot(0.5), snapshot(0.9));
        var deltas = DissonanceTickParticipant.computePadDeltas(tensions, CONFIG);
        double expected = -Math.min(CONFIG.dominanceCap(),
            0.9 * CONFIG.dominanceWeight());
        assertEquals(expected, deltas.dominance(), 0.001);
    }

    @Test
    void capsArousalAtConfiguredCap() {
        var tensions = List.of(
            snapshot(1.0), snapshot(1.0), snapshot(1.0));
        var deltas = DissonanceTickParticipant.computePadDeltas(tensions, CONFIG);
        assertEquals(CONFIG.arousalCap(), deltas.arousal(), 0.001);
    }

    @Test
    void returnsZeroDeltasWhenNoTensions() {
        var deltas = DissonanceTickParticipant.computePadDeltas(List.of(), CONFIG);
        assertEquals(0.0, deltas.arousal(), 0.001);
        assertEquals(0.0, deltas.pleasure(), 0.001);
        assertEquals(0.0, deltas.dominance(), 0.001);
    }

    private TensionSnapshot snapshot(double strength) {
        return new TensionSnapshot("n1", "belief-belief",
            "a", "b", "text-a", "text-b", strength, "reason");
    }
}
```

`PadDelta` is a simple record `(double arousal, double pleasure, double dominance)` defined in `DissonanceTickParticipant` or as a package-private record.

- [ ] **Step 3: Write failing tests for DissonancePromptSection**

```java
package io.casehub.neocortex.cognition.dissonance;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DissonancePromptSectionTest {

    private static final DissonanceDetectionConfig CONFIG =
        DissonanceDetectionConfig.defaults();

    @Test
    void rendersBeliefBeliefTension() {
        var tensions = List.of(new TensionSnapshot(
            "n1", "belief-belief", "a", "b",
            "I am a loyal friend",
            "I would betray anyone for power",
            0.85, "reason"));
        var section = new DissonancePromptSection(tensions, CONFIG);
        var text = section.render(null);
        assertTrue(text.contains("loyal friend"));
        assertTrue(text.contains("betray"));
    }

    @Test
    void rendersBeliefBehaviorWithActionGapWording() {
        var tensions = List.of(new TensionSnapshot(
            "n1", "belief-behavior", "a", "b",
            "honesty matters",
            "deliberate_provocation (driven by dominance)",
            0.70, "reason"));
        var section = new DissonancePromptSection(tensions, CONFIG);
        var text = section.render(null);
        assertTrue(text.contains("pattern of"));
    }

    @Test
    void filtersBelowRenderingThreshold() {
        var tensions = List.of(
            new TensionSnapshot("n1", "belief-belief", "a", "b",
                "belief-a", "belief-b", 0.20, "reason"));
        var section = new DissonancePromptSection(tensions, CONFIG);
        var text = section.render(null);
        assertTrue(text == null || text.isBlank());
    }

    @Test
    void sortsStrongestFirst() {
        var tensions = List.of(
            new TensionSnapshot("n1", "belief-belief", "a", "b",
                "weak-a", "weak-b", 0.4, "r"),
            new TensionSnapshot("n2", "belief-belief", "c", "d",
                "strong-a", "strong-b", 0.9, "r"));
        var section = new DissonancePromptSection(tensions, CONFIG);
        var text = section.render(null);
        assertTrue(text.indexOf("strong") < text.indexOf("weak"));
    }

    @Test
    void capsAtMaxRendered() {
        var tensions = List.of(
            snapshot(0.9, "a1", "b1"),
            snapshot(0.8, "a2", "b2"),
            snapshot(0.7, "a3", "b3"),
            snapshot(0.6, "a4", "b4"));
        var section = new DissonancePromptSection(tensions, CONFIG);
        var text = section.render(null);
        assertFalse(text.contains("a4"));
    }

    @Test
    void blockTagIsDissonance() {
        var section = new DissonancePromptSection(List.of(), CONFIG);
        assertEquals(BlockTag.DISSONANCE, section.blockTag());
    }

    private TensionSnapshot snapshot(double strength, String a, String b) {
        return new TensionSnapshot("n", "belief-belief",
            "id-" + a, "id-" + b, a, b, strength, "r");
    }
}
```

- [ ] **Step 4: Implement DissonanceTickParticipant**

```java
package io.casehub.neocortex.cognition.dissonance;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;

import java.util.List;

public class DissonanceTickParticipant implements CognitionTickParticipant {

    private final DissonanceDetectionPhase phase;
    private final DissonanceDetectionConfig config;

    public DissonanceTickParticipant(DissonanceDetectionPhase phase,
                                     DissonanceDetectionConfig config) {
        this.phase = phase;
        this.config = config;
    }

    @Override
    public void tick(CognitionTickContext context) {
        var tensions = phase.activeTensions(
            context.agentId(), context.tenantId());
        if (tensions.isEmpty()) return;

        var deltas = computePadDeltas(tensions, config);
        context.adjustMood(deltas.arousal(), deltas.pleasure(), deltas.dominance());
    }

    record PadDelta(double arousal, double pleasure, double dominance) {}

    static PadDelta computePadDeltas(List<TensionSnapshot> tensions,
                                      DissonanceDetectionConfig config) {
        if (tensions.isEmpty()) return new PadDelta(0, 0, 0);

        double arousalSum = 0;
        double pleasureSum = 0;
        double maxStrength = 0;
        for (var t : tensions) {
            arousalSum += t.strength() * config.arousalWeight();
            pleasureSum += t.strength() * config.pleasureWeight();
            maxStrength = Math.max(maxStrength, t.strength());
        }

        double arousal = Math.min(config.arousalCap(), arousalSum);
        double pleasure = -Math.min(config.pleasureCap(), pleasureSum);
        double dominance = -Math.min(config.dominanceCap(),
            maxStrength * config.dominanceWeight());

        return new PadDelta(arousal, pleasure, dominance);
    }
}
```

Note: `context.adjustMood(arousal, pleasure, dominance)` — verify the actual method signature on `CognitionTickContext`. If it uses a different PAD adjustment API, adapt accordingly. The pattern is in `AppraisalTickParticipant`.

- [ ] **Step 5: Implement DissonancePromptSection**

```java
package io.casehub.neocortex.cognition.dissonance;

import io.casehub.neocortex.cognition.prompt.BlockTag;
import io.casehub.neocortex.cognition.prompt.CognitionPromptRenderer;
import io.casehub.neocortex.cognition.prompt.CognitionRenderContext;

import java.util.Comparator;
import java.util.List;

public class DissonancePromptSection implements CognitionPromptRenderer {

    private final List<TensionSnapshot> tensions;
    private final DissonanceDetectionConfig config;

    public DissonancePromptSection(List<TensionSnapshot> tensions,
                                    DissonanceDetectionConfig config) {
        this.tensions = tensions;
        this.config = config;
    }

    @Override
    public BlockTag blockTag() { return BlockTag.DISSONANCE; }

    @Override
    public String render(CognitionRenderContext context) {
        var renderable = tensions.stream()
            .filter(t -> t.strength() >= config.renderingThreshold())
            .sorted(Comparator.comparingDouble(TensionSnapshot::strength).reversed())
            .limit(config.maxRendered())
            .toList();
        if (renderable.isEmpty()) return null;

        var sb = new StringBuilder();
        sb.append("You feel an uncomfortable tension between parts of yourself ")
          .append("that don't fit together:\n");
        for (var t : renderable) {
            sb.append("- ");
            if ("belief-behavior".equals(t.tensionType())) {
                sb.append("Your belief that ").append(t.elementAText())
                  .append(" sits uneasily alongside your established pattern of ")
                  .append(t.elementBText()).append(".");
            } else {
                sb.append("Your belief that ").append(t.elementAText())
                  .append(" pulls against your belief that ")
                  .append(t.elementBText()).append(".");
            }
            if (t.strength() >= 0.7) {
                sb.append(" This contradiction is sharp and unresolved.");
            }
            sb.append("\n");
        }
        return sb.toString().strip();
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition -Dtest="DissonanceTickParticipantTest,DissonancePromptSectionTest"`

Expected: all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add cognition-api/src/main/java/io/casehub/neocortex/cognition/prompt/BlockTag.java
git add cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/DissonanceTickParticipant.java
git add cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/DissonancePromptSection.java
git add cognition/src/test/java/io/casehub/neocortex/cognition/dissonance/
git commit -m "feat(cognition): DissonanceTickParticipant + DissonancePromptSection + BlockTag.DISSONANCE

Per-tick PAD adjustment from dissonance tension (arousal up, pleasure
down per Cooper & Fazio 1984). Prompt section renders felt tension
between contradictory beliefs/behaviors. BlockTag.DISSONANCE(212)
slots between DRIVES and CURIOSITY in Tier 2 State.

Refs #593

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 3: CognitionCore, CognitivePipelineBuilder, CognitionCoreFactory wiring

**Files:**
- Modify: `cognition-api/src/main/java/io/casehub/neocortex/cognition/core/CognitionConfig.java` — add `dissonanceEnabled` field
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java` — add `configureDissonance()`, update `promptSections()`
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitivePipelineBuilder.java` — add `dissonance()` builder method
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCoreFactory.java` — inject `DissonanceDetectionPhase`, wire through builder
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/prompt/DirectiveSection.java` — add DIRECTIVES + TAGS entries
- Test: Compile and run full module test suite

**Interfaces:**
- Consumes: `DissonanceDetectionPhase` (Task 1), `DissonanceTickParticipant` (Task 2), `DissonancePromptSection` (Task 2), `DissonanceDetectionConfig` (Task 1)
- Produces: Fully wired cognitive pipeline with dissonance subsystem

- [ ] **Step 1: Add `dissonanceEnabled` to CognitionConfig**

Add `boolean dissonanceEnabled` as the 28th record component. Update all constructor call sites:

- `all()`: add `false` (off by default, like `behavioralEnabled`)
- `withAllSubsystems()`: add `true`
- `none()`: add `false`
- `withDirectives()`: pass through `dissonanceEnabled`
- Add `case "dissonance"` branch in `with()` switch that returns a new `CognitionConfig` with `enabled` in the `dissonanceEnabled` position and all other fields passed through by name

- [ ] **Step 2: Add `configureDissonance()` to CognitionCore**

Add field:
```java
private DissonanceDetectionPhase dissonancePhase;
private DissonanceTickParticipant dissonanceParticipant;
```

Add method:
```java
public void configureDissonance(DissonanceDetectionPhase phase,
                                 DissonanceDetectionConfig config) {
    this.dissonancePhase = phase;
    this.dissonanceParticipant = new DissonanceTickParticipant(phase, config);
    addParticipant(CognitionPhase.DERIVED, this.dissonanceParticipant);
}
```

- [ ] **Step 3: Add DissonancePromptSection to `promptSections()`**

In `CognitionCore.promptSections()`, add a guarded block following the Pattern B convention (simple config flag + null check):

```java
if (config.dissonanceEnabled() && dissonancePhase != null) {
    var tensions = dissonancePhase.activeTensions(agentId, tenantId);
    if (!tensions.isEmpty()) {
        sections.add(new DissonancePromptSection(tensions,
            dissonancePhase.config()));
    }
}
```

This requires exposing `config()` on `DissonanceDetectionPhase` — add a public accessor: `public DissonanceDetectionConfig config() { return config; }`.

- [ ] **Step 4: Add `dissonance()` builder method to CognitivePipelineBuilder**

Add field:
```java
private DissonanceDetectionPhase dissonancePhase;
private DissonanceDetectionConfig dissonanceConfig;
```

Add builder method:
```java
public CognitivePipelineBuilder dissonance(DissonanceDetectionPhase phase,
                                            DissonanceDetectionConfig config) {
    this.dissonancePhase = phase;
    this.dissonanceConfig = config;
    return this;
}
```

In `build()`, after existing configure calls:
```java
if (dissonancePhase != null && dissonanceConfig != null) {
    core.configureDissonance(dissonancePhase, dissonanceConfig);
}
```

- [ ] **Step 5: Wire in CognitionCoreFactory**

Add injected field:
```java
@Inject Instance<DissonanceDetectionPhase> dissonancePhase;
@Inject Instance<DissonanceDetectionConfig> dissonanceConfig;
```

In `create()`, add the wiring block following the existing pattern:
```java
if (dissonancePhase.isResolvable() && dissonanceConfig.isResolvable()) {
    builder.dissonance(dissonancePhase.get(), dissonanceConfig.get());
}
```

- [ ] **Step 6: Add DirectiveSection entries**

In `DirectiveSection.java`, add entries to the `DIRECTIVES` and `TAGS` static maps:

In `DIRECTIVES` map:
```java
"DissonancePromptSection", "Internal tension from contradictory beliefs or values — this shapes your emotional state and may surface in conversation:"
```

In `TAGS` map:
```java
"DissonancePromptSection", BlockTag.DISSONANCE
```

- [ ] **Step 7: Run full module test suite**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition-api,cognition`

Expected: all tests PASS. If any existing tests break from the CognitionConfig field addition, fix the positional constructor calls.

- [ ] **Step 8: Run full project build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -DskipTests`

Expected: BUILD SUCCESS. This catches any compilation errors from the CognitionConfig change propagating across modules.

- [ ] **Step 9: Commit**

```bash
git add cognition-api/src/main/java/io/casehub/neocortex/cognition/core/CognitionConfig.java
git add cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java
git add cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitivePipelineBuilder.java
git add cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCoreFactory.java
git add cognition/src/main/java/io/casehub/neocortex/cognition/prompt/DirectiveSection.java
git add cognition/src/main/java/io/casehub/neocortex/cognition/dissonance/DissonanceDetectionPhase.java
git commit -m "feat(cognition): wire DissonanceDetectionPhase into cognitive pipeline

CognitionConfig.dissonanceEnabled flag, CognitionCore.configureDissonance(),
CognitivePipelineBuilder.dissonance(), CognitionCoreFactory CDI wiring,
DirectiveSection entries for prompt wrapping.

Refs #593

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## References

- [2026-11-23-cognitive-dissonance-design.md] — design spec this plan implements
- [decisions.md] — 9 design decisions with trade-offs
- `cognition/src/main/java/.../belief/BeliefRevisionPhase.java` — consolidation phase pattern
- `cognition-api/src/main/java/.../belief/BeliefRevisionConfig.java` — config record pattern
- `cognition/src/main/java/.../core/CognitionCore.java` — tick participant registration, promptSections()
- `cognition/src/main/java/.../core/CognitivePipelineBuilder.java` — builder wiring pattern
- `cognition/src/main/java/.../core/CognitionCoreFactory.java` — CDI Instance<T> injection pattern
- `cognition-api/src/main/java/.../prompt/BlockTag.java` — prompt ordering enum
- `cognition/src/main/java/.../prompt/DirectiveSection.java` — DIRECTIVES/TAGS maps
- `cognition-api/src/main/java/.../core/CognitionConfig.java` — 27-field record, with() switch
- `cognition/src/main/java/.../CognitionDefaultBeans.java` — @DefaultBean producer pattern
- `mindmap-intelligence/src/main/java/.../consolidation/BehavioralSynthesisPhase.java` — in-memory cache pattern
- GitHub #593 — focal issue
- GitHub #595 — future coping integration

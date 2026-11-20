# Edge Extraction Scaling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #547 — Edge extraction not scaling with entity growth
**Issue group:** #547

**Goal:** Restore edge/node ratio above 1.0 by adding cross-passage/cross-turn edge inference to both the Python biographical pipeline and the Java consolidation pipeline.

**Architecture:** Hybrid approach — programmatic co-occurrence analysis identifies candidate entity pairs (entities that share memories/passages but lack edges), then a focused LLM call infers edge types for top candidates. Python pipeline gets a new post-extraction function; Java pipeline gets a new `EdgeInferencePhase` consolidation phase.

**Tech Stack:** Python (Anthropic SDK), Java 21 (Quarkus CDI, SmallRye Config), IntelliJ MCP for structural editing

## Global Constraints

- Java 21 source, Java 26 JVM
- Quarkus 3.32.2
- `@IfBuildProperty` for opt-in activation of Java phase
- `Instance<T>` for graceful degradation when CDI beans unavailable
- `SessionPipeline` + `ParallelLlmExecutor` for LLM calls in consolidation (not `AgentProvider`)
- Confidence origin `INFERRED` for all inferred edges
- TDD: failing test first, then minimal implementation

---

## Batch 1: Java — EdgeInferencePhase

### Task 1: EdgeInferenceConfig and EdgeInferencePhase skeleton with no-op test

**Files:**
- Create: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferenceConfig.java`
- Create: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferencePhase.java`
- Create: `mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferencePhaseTest.java`

**Interfaces:**
- Consumes: `ConsolidationPhase` SPI (`mindmap-intelligence/.../consolidation/ConsolidationPhase.java`), `MindMapStore` (`mindmap-api`), `CaseMemoryStore` (`memory-api`), `SessionPipeline` (`casehub-platform-agent-api`), `ParallelLlmExecutor` (`casehub-platform-agent-api`)
- Produces: `EdgeInferencePhase implements ConsolidationPhase` — `name()` returns `"edge-inference"`, `run(tenantId, subgraphPriority)` performs edge inference, `setSessionPipeline(pipeline)` receives LLM pipeline

- [ ] **Step 1: Write the failing test — phase skips when no SessionPipeline**

```java
package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.inmem.InMemoryMemoryStore;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EdgeInferencePhaseTest {

    private static final String TENANT = "test-tenant";

    private InMemoryMemoryStore memoryStore;
    private InMemoryMindMapStore mindMapStore;
    private EdgeInferencePhase phase;

    private final CurrentPrincipal principal = new CurrentPrincipal() {
        @Override public String actorId() { return "actor"; }
        @Override public Set<String> groups() { return Set.of(); }
        @Override public String tenancyId() { return TENANT; }
        @Override public boolean isCrossTenantAdmin() { return true; }
    };

    @BeforeEach
    void setUp() {
        memoryStore = new InMemoryMemoryStore(principal);
        mindMapStore = new InMemoryMindMapStore();
        phase = new EdgeInferencePhase(mindMapStore, memoryStore, 1, 30);
    }

    @Test
    void noOpWhenNoSessionPipeline() {
        String sgId = mindMapStore.createSubgraph(
            new SubgraphInput(SubgraphTypes.PERSON, SubgraphTypes.PERSON, null), TENANT);
        mindMapStore.addNode(NodeInput.of("Alice", sgId), TENANT);
        mindMapStore.addNode(NodeInput.of("Bob", sgId), TENANT);

        // No setSessionPipeline called — should be a no-op
        phase.run(TENANT, List.of());

        // No edges created
        var alice = mindMapStore.resolveNode("Alice", null, TENANT);
        assertTrue(mindMapStore.neighbors(alice.id(), TENANT).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=EdgeInferencePhaseTest#noOpWhenNoSessionPipeline -DfailIfNoTests=false`
Expected: FAIL — class `EdgeInferencePhase` does not exist

- [ ] **Step 3: Write EdgeInferenceConfig**

```java
package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.mindmap.consolidation.edge-inference")
public interface EdgeInferenceConfig {
    @WithDefault("1")
    int degreeThreshold();

    @WithDefault("30")
    int maxCandidates();
}
```

- [ ] **Step 4: Write minimal EdgeInferencePhase — skeleton that is a no-op without SessionPipeline**

```java
package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.platform.agent.session.SessionPipeline;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.logging.Logger;

@Priority(22)
public class EdgeInferencePhase implements ConsolidationPhase {

    private static final Logger LOG = Logger.getLogger(EdgeInferencePhase.class.getName());

    private final MindMapStore mindMapStore;
    private final CaseMemoryStore memoryStore;
    private final int degreeThreshold;
    private final int maxCandidates;
    private volatile SessionPipeline sessionPipeline;

    @Inject
    public EdgeInferencePhase(MindMapStore mindMapStore,
                              Instance<CaseMemoryStore> memoryStoreInstance,
                              Instance<EdgeInferenceConfig> configInstance) {
        this.mindMapStore = mindMapStore;
        this.memoryStore = memoryStoreInstance.isResolvable() ? memoryStoreInstance.get() : null;
        EdgeInferenceConfig c = configInstance.isResolvable() ? configInstance.get() : null;
        this.degreeThreshold = c != null ? c.degreeThreshold() : 1;
        this.maxCandidates = c != null ? c.maxCandidates() : 30;
    }

    EdgeInferencePhase(MindMapStore mindMapStore, CaseMemoryStore memoryStore,
                       int degreeThreshold, int maxCandidates) {
        this.mindMapStore = mindMapStore;
        this.memoryStore = memoryStore;
        this.degreeThreshold = degreeThreshold;
        this.maxCandidates = maxCandidates;
    }

    @Override
    public String name() { return "edge-inference"; }

    @Override
    public void setSessionPipeline(SessionPipeline pipeline) {
        this.sessionPipeline = pipeline;
    }

    @Override
    public void run(String tenantId, List<String> subgraphPriority) {
        if (sessionPipeline == null || memoryStore == null) {
            LOG.fine("Edge inference skipped: no SessionPipeline or CaseMemoryStore");
            return;
        }
        // Full implementation in Task 2
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=EdgeInferencePhaseTest#noOpWhenNoSessionPipeline -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferenceConfig.java mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferencePhase.java mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferencePhaseTest.java
git commit -m "feat(#547): add EdgeInferencePhase skeleton and config

Refs #547"
```

### Task 2: Co-occurrence candidate identification and LLM edge inference

**Files:**
- Modify: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferencePhase.java`
- Modify: `mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferencePhaseTest.java`

**Interfaces:**
- Consumes: `EdgeInferencePhase` skeleton from Task 1, `MindMapStore.nodesIn()`, `MindMapStore.neighbors()`, `CaseMemoryStore.scan()`, `SessionPipeline`, `ParallelLlmExecutor`, `ExtractionJsonParser.parse()`
- Produces: Fully implemented `run()` method that finds under-connected nodes, builds co-occurrence from memories, and invokes LLM for edge inference

- [ ] **Step 1: Write the failing test — orphan nodes with shared memory get edge inferred**

Add to `EdgeInferencePhaseTest.java`:

```java
@Test
void infersEdgeBetweenOrphanNodesWithSharedMemory() {
    String sgId = mindMapStore.createSubgraph(
        new SubgraphInput(SubgraphTypes.PERSON, SubgraphTypes.PERSON, null), TENANT);
    mindMapStore.addNode(NodeInput.of("Alice", sgId), TENANT);
    mindMapStore.addNode(NodeInput.of("Bob", sgId), TENANT);

    // Store a memory mentioning both entities
    var attrs = new java.util.HashMap<String, String>();
    attrs.put("subject", "Alice");
    attrs.put("related_entities", "Bob");
    attrs.put(io.casehub.neocortex.memory.experience.ExperienceAttributeKeys.EVENT_TYPE, "observation");
    memoryStore.store(new io.casehub.neocortex.memory.MemoryInput(
        io.casehub.neocortex.memory.Subject.of("agent", "test-agent"),
        "experience", TENANT, null,
        "Alice and Bob collaborated on a research project",
        attrs, null, null, null, null, null, null));

    // Mock SessionPipeline that returns a valid edge JSON
    var mockPipeline = EdgeInferenceTestHelper.mockPipeline(
        """
        {"relationships": [{"source": "Alice", "target": "Bob", \
        "type": "collaborated-with", "confidence": "INFERRED"}]}
        """);
    phase.setSessionPipeline(mockPipeline);

    phase.run(TENANT, List.of());

    var alice = mindMapStore.resolveNode("Alice", null, TENANT);
    var edges = mindMapStore.neighbors(alice.id(), TENANT);
    assertFalse(edges.isEmpty(), "Should have inferred at least one edge");
    assertEquals("collaborated-with", edges.getFirst().edgeType());
}
```

- [ ] **Step 2: Write EdgeInferenceTestHelper — mock SessionPipeline**

Add a package-private helper class in the test directory. Reference the pattern from `SearchBudgetDescriptionGenerationPhaseTest` or similar tests that mock `SessionPipeline`. The mock returns a fixed LLM response string when `session.query()` is called.

```java
package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.session.AgentSession;
import io.casehub.platform.agent.session.SessionPipeline;
import io.smallrye.mutiny.Multi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class EdgeInferenceTestHelper {
    static SessionPipeline mockPipeline(String response) {
        var pipeline = mock(SessionPipeline.class);
        var session = mock(AgentSession.class);
        lenient().when(pipeline.openSession(any(), any())).thenReturn(session);
        lenient().when(session.query(any())).thenReturn(
            Multi.createFrom().item(new AgentEvent.TextDelta(response)));
        return pipeline;
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=EdgeInferencePhaseTest#infersEdgeBetweenOrphanNodesWithSharedMemory -DfailIfNoTests=false`
Expected: FAIL — `run()` is a no-op stub

- [ ] **Step 4: Implement the full `run()` method in EdgeInferencePhase**

The implementation has four parts:

**4a. Find under-connected nodes:**
```java
private List<MindMapNode> findUnderConnectedNodes(String tenantId, List<String> subgraphPriority) {
    List<String> sgIds = orderedSubgraphs(tenantId, subgraphPriority);
    List<MindMapNode> underConnected = new ArrayList<>();
    for (String sgId : sgIds) {
        for (MindMapNode node : mindMapStore.nodesIn(sgId, tenantId)) {
            if (node.traits().contains("Summary") || node.traits().contains("CapsGenerated")) continue;
            int degree = mindMapStore.neighbors(node.id(), tenantId).size();
            if (degree <= degreeThreshold) {
                underConnected.add(node);
            }
        }
    }
    return underConnected;
}
```

**4b. Build co-occurrence matrix from memories:**
```java
private record CandidatePair(String nodeIdA, String nameA, String nodeIdB, String nameB,
                              int coOccurrenceCount, List<String> sharedMemoryTexts) {}

private List<CandidatePair> buildCandidates(List<MindMapNode> underConnected, String tenantId) {
    Set<String> underConnectedIds = underConnected.stream()
        .map(MindMapNode::id).collect(Collectors.toSet());
    Map<String, Map<String, List<String>>> coOccurrence = new HashMap<>();

    for (MindMapNode node : underConnected) {
        var scanRequest = MemoryScanRequest.forTenant(tenantId, "experience", null, 200);
        for (var memory : memoryStore.scan(scanRequest)) {
            String subject = memory.attributes().getOrDefault("subject", "");
            String related = memory.attributes().getOrDefault("related_entities", "");
            Set<String> mentioned = new HashSet<>();
            if (subject.equalsIgnoreCase(node.name())) mentioned.add(node.id());
            if (related.toLowerCase().contains(node.name().toLowerCase())) mentioned.add(node.id());
            for (MindMapNode other : underConnected) {
                if (other.id().equals(node.id())) continue;
                if (subject.equalsIgnoreCase(other.name()) ||
                    related.toLowerCase().contains(other.name().toLowerCase())) {
                    mentioned.add(other.id());
                }
            }
            if (mentioned.size() >= 2) {
                for (String a : mentioned) {
                    for (String b : mentioned) {
                        if (a.compareTo(b) >= 0) continue;
                        coOccurrence.computeIfAbsent(a + "|" + b, k -> new HashMap<>())
                            .computeIfAbsent("texts", k -> new ArrayList<>())
                            .add(memory.text().substring(0, Math.min(200, memory.text().length())));
                    }
                }
            }
        }
    }

    Map<String, MindMapNode> idToNode = underConnected.stream()
        .collect(Collectors.toMap(MindMapNode::id, n -> n));
    List<CandidatePair> candidates = new ArrayList<>();
    for (var entry : coOccurrence.entrySet()) {
        String[] ids = entry.getKey().split("\\|");
        if (ids.length != 2) continue;
        MindMapNode a = idToNode.get(ids[0]);
        MindMapNode b = idToNode.get(ids[1]);
        if (a == null || b == null) continue;
        boolean hasEdge = mindMapStore.neighbors(a.id(), tenantId).stream()
            .anyMatch(e -> e.targetNodeId().equals(b.id()) || e.sourceNodeId().equals(b.id()));
        if (hasEdge) continue;
        List<String> texts = entry.getValue().getOrDefault("texts", List.of());
        candidates.add(new CandidatePair(a.id(), a.name(), b.id(), b.name(),
            texts.size(), texts.subList(0, Math.min(3, texts.size()))));
    }
    candidates.sort(Comparator.comparingInt(CandidatePair::coOccurrenceCount).reversed());
    return candidates.subList(0, Math.min(maxCandidates, candidates.size()));
}
```

**4c. LLM edge inference with ParallelLlmExecutor:**
```java
private static final String SYSTEM_PROMPT = """
    You are a knowledge graph edge inference agent. Given pairs of entities \
    and the context where they co-occur, determine if a relationship exists.
    
    For each pair, respond with either:
    - An edge: {"source": "...", "target": "...", "type": "relationship-type", \
      "confidence": "INFERRED"}
    - No edge: skip the pair
    
    Relationship vocabulary: child-of, sibling-of, parent-of, married-to, \
    treated-by, taught-by, championed, collaborated-with, pursues, enables, \
    blocks, requires, contributes-to, lives-at, located-in, at, inspired-by, \
    created-by, depicted-in, embodies, exemplifies, contradicts, influenced-by, \
    participated, preceded-by, concurrent-with, caused-by, resulted-in, motivated-by
    
    Respond with JSON only: {"relationships": [...]}""";
```

**4d. Parse and persist edges:**
Use `ExtractionJsonParser.parse()` to extract `ParsedRelationship` records. Resolve node IDs via `mindMapStore.resolveNode()`. Create `EdgeInput` records with confidence origin `INFERRED` and provenance `"edge-inference-consolidation"`. Persist via `mindMapStore.addEdges()`.

Full `run()` method:
```java
@Override
public void run(String tenantId, List<String> subgraphPriority) {
    if (sessionPipeline == null || memoryStore == null) {
        LOG.fine("Edge inference skipped: no SessionPipeline or CaseMemoryStore");
        return;
    }

    List<MindMapNode> underConnected = findUnderConnectedNodes(tenantId, subgraphPriority);
    if (underConnected.isEmpty()) return;

    List<CandidatePair> candidates = buildCandidates(underConnected, tenantId);
    if (candidates.isEmpty()) return;

    String userPrompt = buildUserPrompt(candidates);
    var tasks = new ArrayList<Consumer<AgentSession>>();
    var inferred = new ArrayList<ParsedRelationship>();

    tasks.add(session -> {
        try {
            var responseText = new StringBuilder();
            session.query(userPrompt).subscribe().asStream()
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .forEach(responseText::append);
            var parsed = ExtractionJsonParser.parse(responseText.toString());
            if (parsed != null) {
                synchronized (inferred) {
                    inferred.addAll(parsed.relationships());
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Edge inference LLM call failed", e);
        }
    });

    ParallelLlmExecutor.execute(sessionPipeline, "consolidation", 1,
        SYSTEM_PROMPT, "edge-inference", tasks);

    persistEdges(inferred, tenantId);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=EdgeInferencePhaseTest#infersEdgeBetweenOrphanNodesWithSharedMemory -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 6: Add remaining tests**

Add these tests to `EdgeInferencePhaseTest.java`:

```java
@Test
void skipsNodesAboveDegreeThreshold() {
    // degreeThreshold=1, so nodes with >1 edge are skipped
    String sgId = mindMapStore.createSubgraph(
        new SubgraphInput(SubgraphTypes.PERSON, SubgraphTypes.PERSON, null), TENANT);
    mindMapStore.addNode(NodeInput.of("Alice", sgId), TENANT);
    mindMapStore.addNode(NodeInput.of("Bob", sgId), TENANT);
    mindMapStore.addNode(NodeInput.of("Carol", sgId), TENANT);
    var alice = mindMapStore.resolveNode("Alice", null, TENANT);
    var bob = mindMapStore.resolveNode("Bob", null, TENANT);
    var carol = mindMapStore.resolveNode("Carol", null, TENANT);
    // Give Alice 2 edges — above threshold
    mindMapStore.addEdge(EdgeInput.of(alice.id(), bob.id(), "knows"), TENANT);
    mindMapStore.addEdge(EdgeInput.of(alice.id(), carol.id(), "knows"), TENANT);

    var mockPipeline = EdgeInferenceTestHelper.mockPipeline(
        """{"relationships": []}""");
    phase.setSessionPipeline(mockPipeline);
    phase.run(TENANT, List.of());

    // Alice should not have gained any new edges
    assertEquals(2, mindMapStore.neighbors(alice.id(), TENANT).size());
}

@Test
void noEdgeCreatedWhenNoSharedMemories() {
    String sgId = mindMapStore.createSubgraph(
        new SubgraphInput(SubgraphTypes.PERSON, SubgraphTypes.PERSON, null), TENANT);
    mindMapStore.addNode(NodeInput.of("Alice", sgId), TENANT);
    mindMapStore.addNode(NodeInput.of("Bob", sgId), TENANT);
    // Separate memories — no co-occurrence
    var attrs1 = new java.util.HashMap<String, String>();
    attrs1.put("subject", "Alice");
    attrs1.put(io.casehub.neocortex.memory.experience.ExperienceAttributeKeys.EVENT_TYPE, "observation");
    memoryStore.store(new io.casehub.neocortex.memory.MemoryInput(
        io.casehub.neocortex.memory.Subject.of("agent", "a"), "experience",
        TENANT, null, "Alice went to school", attrs1, null, null, null, null, null, null));
    var attrs2 = new java.util.HashMap<String, String>();
    attrs2.put("subject", "Bob");
    attrs2.put(io.casehub.neocortex.memory.experience.ExperienceAttributeKeys.EVENT_TYPE, "observation");
    memoryStore.store(new io.casehub.neocortex.memory.MemoryInput(
        io.casehub.neocortex.memory.Subject.of("agent", "a"), "experience",
        TENANT, null, "Bob went to work", attrs2, null, null, null, null, null, null));

    var mockPipeline = EdgeInferenceTestHelper.mockPipeline(
        """{"relationships": []}""");
    phase.setSessionPipeline(mockPipeline);
    phase.run(TENANT, List.of());

    var alice = mindMapStore.resolveNode("Alice", null, TENANT);
    assertTrue(mindMapStore.neighbors(alice.id(), TENANT).isEmpty());
}

@Test
void skipsAlreadyConnectedPairs() {
    String sgId = mindMapStore.createSubgraph(
        new SubgraphInput(SubgraphTypes.PERSON, SubgraphTypes.PERSON, null), TENANT);
    mindMapStore.addNode(NodeInput.of("Alice", sgId), TENANT);
    mindMapStore.addNode(NodeInput.of("Bob", sgId), TENANT);
    var alice = mindMapStore.resolveNode("Alice", null, TENANT);
    var bob = mindMapStore.resolveNode("Bob", null, TENANT);
    // Add existing edge — pair should be excluded
    mindMapStore.addEdge(EdgeInput.of(alice.id(), bob.id(), "knows"), TENANT);

    // Add co-occurring memory
    var attrs = new java.util.HashMap<String, String>();
    attrs.put("subject", "Alice");
    attrs.put("related_entities", "Bob");
    attrs.put(io.casehub.neocortex.memory.experience.ExperienceAttributeKeys.EVENT_TYPE, "observation");
    memoryStore.store(new io.casehub.neocortex.memory.MemoryInput(
        io.casehub.neocortex.memory.Subject.of("agent", "a"), "experience",
        TENANT, null, "Alice and Bob met", attrs, null, null, null, null, null, null));

    var mockPipeline = EdgeInferenceTestHelper.mockPipeline(
        """{"relationships": [{"source": "Alice", "target": "Bob", "type": "collaborated-with", "confidence": "INFERRED"}]}""");
    phase.setSessionPipeline(mockPipeline);
    phase.run(TENANT, List.of());

    // Should still only have the original edge, not a duplicate
    assertEquals(1, mindMapStore.neighbors(alice.id(), TENANT).size());
    assertEquals("knows", mindMapStore.neighbors(alice.id(), TENANT).getFirst().edgeType());
}
```

- [ ] **Step 7: Run all tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -Dtest=EdgeInferencePhaseTest -DfailIfNoTests=false`
Expected: ALL PASS

- [ ] **Step 8: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferencePhase.java mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferencePhaseTest.java mindmap-intelligence/src/test/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferenceTestHelper.java
git commit -m "feat(#547): implement co-occurrence edge inference in consolidation

Finds under-connected nodes, builds co-occurrence from shared memories,
infers edge types via LLM, persists with INFERRED confidence.

Refs #547"
```

### Task 3: CDI wiring with @IfBuildProperty and pipeline defaults

**Files:**
- Modify: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferencePhase.java` — add `@ApplicationScoped` and `@IfBuildProperty`
- Modify: `cognition/src/main/resources/cognitive-pipeline-defaults.properties` — add edge-inference config defaults

**Interfaces:**
- Consumes: `EdgeInferencePhase` from Task 2, `EdgeInferenceConfig` from Task 1
- Produces: CDI-wired phase that is auto-discovered by `ConsolidationScheduler` when enabled

- [ ] **Step 1: Add CDI annotations to EdgeInferencePhase**

Add to class declaration:
```java
@ApplicationScoped
@IfBuildProperty(name = "casehub.mindmap.consolidation.edge-inference.enabled",
                 stringValue = "true", enableIfMissing = false)
@Priority(22)
public class EdgeInferencePhase implements ConsolidationPhase {
```

- [ ] **Step 2: Add config defaults to cognitive-pipeline-defaults.properties**

Append to `cognition/src/main/resources/cognitive-pipeline-defaults.properties`:
```properties
# --- Edge Inference Phase ---
# When enabled, scans for under-connected nodes and infers edges from
# memory co-occurrence via LLM. One-off per entity pair — inferred edges
# are stored permanently.
# Default: false (opt-in)
casehub.mindmap.consolidation.edge-inference.enabled=false
# Nodes with this many edges or fewer are candidates for inference
casehub.mindmap.consolidation.edge-inference.degree-threshold=1
# Maximum candidate pairs to send to LLM per consolidation run
casehub.mindmap.consolidation.edge-inference.max-candidates=30
```

- [ ] **Step 3: Verify full module build**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl mindmap-intelligence -DfailIfNoTests=false`
Expected: ALL PASS (existing tests unaffected, edge-inference disabled by default)

- [ ] **Step 4: Commit**

```bash
git add mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferencePhase.java cognition/src/main/resources/cognitive-pipeline-defaults.properties
git commit -m "feat(#547): wire EdgeInferencePhase with CDI and config defaults

@IfBuildProperty opt-in, disabled by default. Config: degree-threshold=1,
max-candidates=30.

Refs #547"
```

## Batch 2: Python — Cross-Passage Edge Inference

### Task 4: Add `infer_cross_passage_edges()` to extract_structure.py

**Files:**
- Modify: `biographies/scripts/extract_structure.py`

**Interfaces:**
- Consumes: `call_llm()`, `build_entity_context()`, existing `edge_dedup` dict, `all_memories` list, `all_entities` dict
- Produces: `infer_cross_passage_edges(client, all_entities, all_memories, edge_dedup, top_n)` — returns updated `edge_dedup` with inferred edges merged in

- [ ] **Step 1: Define CROSS_PASSAGE_EDGE_SYSTEM prompt constant**

Add after the existing `MEMORY_SYSTEM` constant (after line 188):

```python
CROSS_PASSAGE_EDGE_SYSTEM = """You are a knowledge graph edge inference agent. Given pairs of entities and the context where they co-occur in memories, determine if a relationship exists.

For each pair, respond with either:
- An edge: {"source": "...", "target": "...", "type": "relationship-type", "confidence_origin": "INFERRED"}
- No relationship: omit the pair

Relationship vocabulary:
  Kinship: child-of, sibling-of, parent-of
  Romantic: married-to, affair-with, divorced-from
  Professional: treated-by, taught-by, championed, collaborated-with
  Goal: pursues, enables, blocks, requires, contributes-to, decomposes-into
  Spatial: lives-at, located-in, at
  Creative: inspired-by, created-by, depicted-in
  Conceptual: embodies, exemplifies, contradicts, influenced-by
  Activity: participated, occasion
  Temporal: preceded-by, concurrent-with, succeeded-by
  Causal: caused-by, resulted-in, motivated-by

Respond with JSON only: {"edges": [...]}"""
```

- [ ] **Step 2: Implement `infer_cross_passage_edges()`**

Add after `build_entity_context()` function:

```python
def infer_cross_passage_edges(client, all_entities, all_memories, edge_dedup, top_n=50):
    """Infer edges between entities that co-occur in memories but have no direct edge."""
    # Build co-occurrence matrix from memory related_entities
    cooccurrence = {}
    for mem in all_memories:
        subject = mem.get('subject', '')
        related = mem.get('related_entities', [])
        if isinstance(related, str):
            related = [r.strip() for r in related.split(',') if r.strip()]
        entities_in_mem = set()
        if subject:
            entities_in_mem.add(subject.lower())
        for r in related:
            entities_in_mem.add(r.lower())

        for a in entities_in_mem:
            for b in entities_in_mem:
                if a >= b:
                    continue
                pair = (a, b)
                if pair not in cooccurrence:
                    cooccurrence[pair] = {'count': 0, 'contexts': []}
                cooccurrence[pair]['count'] += 1
                if len(cooccurrence[pair]['contexts']) < 3:
                    cooccurrence[pair]['contexts'].append(mem.get('text', '')[:200])

    # Filter out pairs with existing edges
    candidates = []
    for (a, b), info in cooccurrence.items():
        has_edge = False
        for ekey in edge_dedup:
            parts = ekey.split('|')
            if len(parts) >= 2 and parts[0] == a and parts[1] == b:
                has_edge = True
                break
            if len(parts) >= 2 and parts[0] == b and parts[1] == a:
                has_edge = True
                break
        if not has_edge:
            candidates.append((a, b, info))

    # Sort by co-occurrence count descending, take top-N
    candidates.sort(key=lambda x: x[2]['count'], reverse=True)
    candidates = candidates[:top_n]

    if not candidates:
        print("  No cross-passage edge candidates found")
        return edge_dedup

    # Resolve canonical names
    entity_names = {e['name'].lower(): e['name'] for e in all_entities.values()}

    # Batch into groups of ~20 pairs per LLM call
    batch_size = 20
    total_inferred = 0
    for i in range(0, len(candidates), batch_size):
        batch = candidates[i:i + batch_size]
        pairs_text = []
        for a, b, info in batch:
            name_a = entity_names.get(a, a)
            name_b = entity_names.get(b, b)
            ctx = ' | '.join(info['contexts'])
            pairs_text.append(
                f"  - {name_a} ↔ {name_b} (co-occurs {info['count']}x): {ctx}")

        user_prompt = f"Entity pairs to evaluate:\n" + '\n'.join(pairs_text)
        result = call_llm(client, CROSS_PASSAGE_EDGE_SYSTEM, user_prompt, None,
                          'cross-passage edges')
        new_edges = result.get('edges', [])
        for edge in new_edges:
            if not edge.get('source') or not edge.get('target') or not edge.get('type'):
                continue
            ekey = f"{edge['source'].lower()}|{edge['target'].lower()}|{edge['type']}"
            if ekey not in edge_dedup:
                edge['extraction_pass'] = 'edge-inference-cross-passage'
                edge['confidence_origin'] = 'INFERRED'
                edge['validation_tier'] = 'UNVALIDATED'
                edge_dedup[ekey] = edge
                total_inferred += 1

    print(f"  Cross-passage edge inference: {total_inferred} new edges from {len(candidates)} candidates")
    return edge_dedup
```

- [ ] **Step 3: Wire into main extraction flow**

In the `main()` function, add the cross-passage edge inference call after the per-passage loop and before `derive_temporal_from_lifecycle()`. Find the line `derive_temporal_from_lifecycle(all_entities)` (line 670) and insert before it:

```python
    # Cross-passage edge inference
    print("\nCross-passage edge inference...")
    top_n = getattr(args, 'edge_inference_top_n', 50)
    edge_dedup = infer_cross_passage_edges(client, all_entities, all_memories, edge_dedup, top_n)
```

- [ ] **Step 4: Add `--edge-inference-top-n` CLI argument**

In the argument parser (around line 512), add:
```python
    parser.add_argument('--edge-inference-top-n', type=int, default=50,
                        help='Max candidate pairs for cross-passage edge inference (0=skip)')
```

- [ ] **Step 5: Handle skip when top_n is 0**

At the top of `infer_cross_passage_edges()`, add:
```python
    if top_n <= 0:
        print("  Cross-passage edge inference: skipped (top_n=0)")
        return edge_dedup
```

- [ ] **Step 6: Commit**

```bash
git add biographies/scripts/extract_structure.py
git commit -m "feat(#547): add cross-passage edge inference to biographical pipeline

Builds co-occurrence matrix from memory related_entities, filters pairs
with existing edges, sends top-N candidates to LLM for edge type inference.

Refs #547"
```

### Task 5: Update CLAUDE.md and pipeline defaults documentation

**Files:**
- Modify: `CLAUDE.md` — update MindMap Intelligence module description to mention EdgeInferencePhase
- Modify: `cognition/src/main/resources/cognitive-pipeline-defaults.properties` (already done in Task 3, verify)

**Interfaces:**
- Consumes: Completed Tasks 1-4
- Produces: Updated documentation

- [ ] **Step 1: Update CLAUDE.md**

In the `mindmap-intelligence/` section, add `EdgeInferencePhase` to the consolidation phases listing. Find the existing consolidation phase descriptions and add:

```
EdgeInferencePhase (@Priority(22) — co-occurrence edge inference: finds under-connected nodes, 
builds co-occurrence from CaseMemoryStore shared subjects/related_entities, infers edge types 
via LLM SessionPipeline, persists with INFERRED confidence and edge-inference-consolidation 
provenance; @IfBuildProperty casehub.mindmap.consolidation.edge-inference.enabled, disabled by default)
```

Also update the Pipeline Configuration section to mention `EdgeInferenceConfig`.

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(#547): document EdgeInferencePhase in CLAUDE.md

Refs #547"
```

## References

- [2026-11-20-edge-extraction-scaling-design.md] — design spec this plan implements
- [mindmap-intelligence/.../consolidation/ConsolidationPhase.java:10] — SPI interface
- [mindmap-intelligence/.../consolidation/ExperienceConsolidationPhase.java:43] — cursor pattern reference
- [mindmap-intelligence/.../consolidation/CuriosityDescriptionGenerationPhase.java:42] — LLM-in-consolidation pattern (SessionPipeline + ParallelLlmExecutor)
- [mindmap-intelligence/.../consolidation/ExperienceConsolidationConfig.java:1] — config interface pattern
- [mindmap-intelligence/.../consolidation/CommunitySummaryPhase.java:29] — edge persistence pattern (addEdges)
- [biographies/scripts/extract_structure.py:239] — Python per-passage extraction flow
- [biographies/scripts/extract_structure.py:191] — build_entity_context() helper
- [GitHub #547] — edge extraction not scaling with entity growth
- [GitHub #538] — parent epic: goal & motivation taxonomy extension

# SE5 cognitive-index Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #1225 — SE5: cognitive-index — agent-scoped state, modulation, privacy
**Issue group:** #1225

**Goal:** Fix three agent-state isolation and consistency bugs in cognitive-index.

**Architecture:** Three independent fixes, each in a separate batch. D14 is a one-line fix in `CognitiveDerivationEngine`. D19 replaces the shared mutable `tickContext` field in `CognitiveMemoryRetriever` with a per-agent `ConcurrentHashMap`. D11 adds agent ownership to affect memories so `CognitiveProfile` can query agent-scoped trajectories for social comparison.

**Tech Stack:** Java 21, JUnit 5, AssertJ

## Global Constraints

- All files in `cognitive-index` module unless noted otherwise
- D11 touches `memory-api` (AffectEvents) and `mindmap-core` (AffectTrajectoryDecorator) in addition to `cognitive-index`
- Run `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean test -pl cognitive-index` to validate after each batch
- For D11, also run `-pl mindmap-core,memory-api` after the write-side changes

---

## Batch 1: D14 — PID-5 isLow default consistency

### Task 1: Fix isLow() absent-facet default

**Files:**
- Modify: `cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java:1226-1229`
- Test: `cognitive-index/src/test/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEnginePid5FacetTest.java`

**Interfaces:**
- Consumes: `PersonalityFacet.isLow()` returns `score <= 0.3`
- Produces: `isLow(List, String)` returns `false` for absent facets (consistent with numeric default of 0.5)

The bug: `isLow()` returns `.orElse(true)` for absent facets. This means absent PID-5 facets (which default to 0.5 numerically) are treated as LOW for expression style derivation, while 0.5 is actually moderate (not ≤ 0.3). `isHigh()` correctly returns `.orElse(false)`.

This affects 14 call sites in `deriveExpressionStyles()` — all in the boolean expression-style-selection logic. With the fix, absent facets stop matching either `isHigh` or `isLow`, causing the else branches to fire (which produce neutral/default expression styles). This is correct: absent data should produce neutral defaults, not trigger specific personality-driven styles.

- [ ] **Step 1: Write failing test**

Add to `CognitiveDerivationEnginePid5FacetTest.java`:

```java
@Test
void expressionStyles_absentFacetsProduceNeutralDefaults() {
    // No facets at all — absent facets should not trigger isLow() branches
    var descriptor = descriptorWithFacets(List.of());
    var defaults = CognitiveDerivationEngine.derive(descriptor);

    // With the bug: absent facets are treated as LOW, triggering specific styles
    // After fix: absent facets are neither high nor low, producing neutral defaults
    // The key assertion: expression styles with no facets should equal expression styles
    // with all facets at 0.5 (the numeric neutral point)
    var neutralFacets = List.of(
            new PersonalityFacet(PersonalityFacet.GRANDIOSITY, 0.5, "test"),
            new PersonalityFacet(PersonalityFacet.CALLOUSNESS, 0.5, "test"),
            new PersonalityFacet(PersonalityFacet.MANIPULATIVENESS, 0.5, "test"),
            new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.5, "test"),
            new PersonalityFacet(PersonalityFacet.HOSTILITY, 0.5, "test"),
            new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.5, "test"));
    var neutralDescriptor = descriptorWithFacets(neutralFacets);
    var neutralDefaults = CognitiveDerivationEngine.derive(neutralDescriptor);

    assertThat(defaults.expressionStyles()).isEqualTo(neutralDefaults.expressionStyles());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-index -Dtest=CognitiveDerivationEnginePid5FacetTest#expressionStyles_absentFacetsProduceNeutralDefaults`
Expected: FAIL — absent facets produce different expression styles than 0.5-scored facets because `isLow()` returns `true` for absent but `false` for 0.5.

- [ ] **Step 3: Fix isLow() default**

In `CognitiveDerivationEngine.java` line 1228, change `.orElse(true)` to `.orElse(false)`:

```java
private static boolean isLow(List<PersonalityFacet> facets, String name) {
    return facets.stream().filter(f -> name.equals(f.facet())).findFirst()
            .map(PersonalityFacet::isLow).orElse(false);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-index -Dtest=CognitiveDerivationEnginePid5FacetTest`
Expected: ALL PASS (including the new test and all existing tests)

- [ ] **Step 5: Commit**

```bash
git add cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java
git add cognitive-index/src/test/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEnginePid5FacetTest.java
git commit -m "fix(cognitive-index): isLow() defaults to false for absent PID-5 facets

Absent facets default to 0.5 numerically (neutral), but isLow()
returned true via .orElse(true). This caused deriveExpressionStyles()
to treat absent facets as LOW, triggering personality-specific styles
instead of neutral defaults. Now consistent: absent = neutral = neither
high nor low.

Refs #1225"
```

---

## Batch 2: D19 — Per-agent tickContext in CognitiveMemoryRetriever

### Task 2: Replace shared tickContext with per-agent map

**Files:**
- Modify: `cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveMemoryRetriever.java:24,56-78`
- Test: `cognitive-index/src/test/java/io/casehub/neocortex/cognitive/index/CognitiveMemoryRetrieverTest.java`

**Interfaces:**
- Consumes: `ModulationContext.agentId()` as map key
- Produces: `setTickContext(ModulationContext)` stores by agentId; `clearTickContext(String agentId)` removes; `query(MemoryQuery)` no-arg falls back to default context (no agent-specific modulation). New `query(MemoryQuery, String agentId)` looks up the agent's context from the map.

The bug: `private volatile ModulationContext tickContext` is a single field. When CognitionCore ticks agent A then agent B concurrently (virtual threads), agent B's `setTickContext()` overwrites agent A's context. Agent A's subsequent `query(MemoryQuery)` calls then use agent B's mood/personality for modulation.

The callers in `cognition` module that use the no-arg `query()` are: `MentalModelOrchestrator`, `GutFeelingParticipant`, `UserModelOrchestrator`, `StrategyLearningOrchestrator`, `NarrativeContentSummariser`. These all run within a tick for a specific agent but don't pass the agentId to the retriever. CognitionCore sets the tickContext at line 354 with the agentId.

Fix: replace the single field with `ConcurrentHashMap<String, ModulationContext>`. Add `query(MemoryQuery, String agentId)` that looks up the per-agent context. The no-arg `query(MemoryQuery)` becomes a fallback with no modulation context (backward compatible — callers that don't know the agentId get default behavior).

- [ ] **Step 1: Write failing test for multi-agent isolation**

Add to `CognitiveMemoryRetrieverTest.java`:

```java
@Test
void perAgentTickContextIsolation() {
    var happy = memory("m1", NOW, Confidence.unknown(1.0), 0.8, 0.3, 0.5);
    var sad   = memory("m2", NOW, Confidence.unknown(1.0), -0.7, -0.2, -0.3);

    ContextualModulationFactory<Memory> moodFactory = ctx -> {
        if (ctx.currentMood() == null) {return null;}
        return ModulationFactors.moodCongruence(ctx.currentMood(), 1.0);
    };

    var retriever = new CognitiveMemoryRetriever(
            stubStore(List.of(sad, happy)),
            List.of(),
            List.of(moodFactory));

    // Agent A has positive mood
    var positiveMood = new io.casehub.neocortex.memory.mood.MoodState(
            "agentA", "t1", Instant.now(), 0.8, 0.3, 0.5, "test", null, Set.of(), Map.of());
    retriever.setTickContext(new ModulationContext("agentA", "t1", positiveMood, null, null));

    // Agent B has negative mood
    var negativeMood = new io.casehub.neocortex.memory.mood.MoodState(
            "agentB", "t1", Instant.now(), -0.8, 0.7, -0.5, "test", null, Set.of(), Map.of());
    retriever.setTickContext(new ModulationContext("agentB", "t1", negativeMood, null, null));

    // Agent A's query should still use positive mood (happy first)
    var agentAResults = retriever.query(query(), "agentA");
    assertThat(agentAResults).extracting(Memory::memoryId)
                              .containsExactly("m1", "m2");

    // Agent B's query should use negative mood (sad first)
    var agentBResults = retriever.query(query(), "agentB");
    assertThat(agentBResults).extracting(Memory::memoryId)
                              .containsExactly("m2", "m1");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-index -Dtest=CognitiveMemoryRetrieverTest#perAgentTickContextIsolation`
Expected: FAIL — `query(MemoryQuery, String)` does not exist yet.

- [ ] **Step 3: Implement per-agent tickContext**

In `CognitiveMemoryRetriever.java`:

Replace the field (line 24):
```java
// Before:
private volatile ModulationContext tickContext;

// After:
private final java.util.concurrent.ConcurrentHashMap<String, ModulationContext> tickContexts = new java.util.concurrent.ConcurrentHashMap<>();
```

Replace `setTickContext` (line 56-58):
```java
public void setTickContext(ModulationContext context) {
    if (context != null && context.agentId() != null) {
        tickContexts.put(context.agentId(), context);
    }
}

public void clearTickContext(String agentId) {
    if (agentId != null) {
        tickContexts.remove(agentId);
    }
}
```

Add new agent-scoped query method and update the no-arg version (lines 60-78):
```java
public List<Memory> query(MemoryQuery query, ModulationContext context) {
    var factors = buildFactors(context);
    List<Memory> results = store.query(query);
    if (!factors.isEmpty()) {
        results = RetrievalModulator.modulate(results, ModulationProfiles.MEMORY, factors);
    }
    for (var observer : observers) {
        observer.onRecall(context.agentId(), query.tenantId(), results.size());
    }
    return results;
}

public List<Memory> query(MemoryQuery query, String agentId) {
    var ctx = agentId != null ? tickContexts.get(agentId) : null;
    if (ctx == null) {
        ctx = new ModulationContext(agentId, query.tenantId(), null, null, null);
    }
    return query(query, ctx);
}

public List<Memory> query(MemoryQuery query) {
    return query(query, (String) null);
}
```

- [ ] **Step 4: Update existing test**

The existing `convenienceQueryUsesTickContext` test should still pass: it sets context for agent "a1" and calls the no-arg `query()`. After the refactor, the no-arg `query()` doesn't look up by agent (it has no agentId), so it gets default context. The test asserts that setting tickContext changes the no-arg result.

This test needs updating: change the second `query()` call to use the agent-aware overload:

```java
// In convenienceQueryUsesTickContext, change:
var withContext = retriever.query(query());
// To:
var withContext = retriever.query(query(), "a1");
```

- [ ] **Step 5: Run all tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognitive-index -Dtest=CognitiveMemoryRetrieverTest`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveMemoryRetriever.java
git add cognitive-index/src/test/java/io/casehub/neocortex/cognitive/index/CognitiveMemoryRetrieverTest.java
git commit -m "fix(cognitive-index): per-agent tickContext in CognitiveMemoryRetriever

Replace shared volatile ModulationContext field with
ConcurrentHashMap<String, ModulationContext> keyed by agentId.
Add query(MemoryQuery, String agentId) for agent-scoped lookup.
Prevents agent B's tick from overwriting agent A's modulation
context in concurrent multi-agent scenarios.

Refs #1225"
```

---

## Batch 3: D11 — Agent-scoped affect trajectories

### Task 3: Store agent ownership on affect memories

**Files:**
- Modify: `memory-api/src/main/java/io/casehub/neocortex/memory/mood/AffectEvents.java:24-33`
- Modify: `mindmap-core/src/main/java/io/casehub/neocortex/mindmap/runtime/AffectTrajectoryDecorator.java:27-57`
- Modify: `cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveProfile.java:234-261`
- Test: `cognitive-index/src/test/java/io/casehub/neocortex/cognitive/index/CognitiveProfileTest.java` (create or extend)

**Interfaces:**
- Consumes: `MemoryInput.withPrincipalId(PrincipalId)`, `OverlayRef.AGENT_ID` property on overlay nodes, `MemoryQuery.withCallerPrincipalId(PrincipalId)` for visibility filtering
- Produces: Agent-scoped affect memories — overlay node PAD updates carry the agent's PrincipalId; shared node PAD updates remain unowned (visible to all)

The bug: `AffectTrajectoryDecorator` stores affect memories with `principalId = null` (line 51 of AffectEvents — the MemoryInput 11th arg is null). `CognitiveProfile.computeTrajectory()` queries affect memories without agent filtering. All agents see the same unowned affect history. In `SocialComparison.computeAlignment()`, every agent gets the same `AffectTrajectory` → cosine similarity is always 1.0 → trajectory alignment is always ALIGNED.

Fix has two parts:
1. **Write side:** `AffectTrajectoryDecorator` checks if the updated node is an overlay (has `OverlayRef.AGENT_ID` property). If so, extracts the agentId and passes it as PrincipalId to `AffectEvents.toMemoryInput()`.
2. **Read side:** `CognitiveProfile.computeTrajectory()` adds `withCallerPrincipalId(query.asSeenBy())` when querying affect memories. The store visibility filter (`principal_id IS NULL OR principal_id = ?`) ensures each agent sees its own + shared memories.

- [ ] **Step 1: Add PrincipalId overload to AffectEvents**

In `AffectEvents.java`, add an overload that accepts PrincipalId:

```java
public static MemoryInput toMemoryInput(String nodeId, String tenantId,
                                        double pleasure, double arousal, double dominance,
                                        AffectType affectType, PrincipalId principalId) {
    var input = new MemoryInput(Subject.of("node", nodeId), DOMAIN, tenantId, null, "PAD update",
                           Map.of("affect-type", affectType.name().toLowerCase()),
                           null, pleasure, arousal, dominance, null, null);
    return principalId != null ? input.withPrincipalId(principalId) : input;
}
```

Add `import io.casehub.platform.api.identity.PrincipalId;` to the imports.

- [ ] **Step 2: Update AffectTrajectoryDecorator to extract agent from overlay nodes**

In `AffectTrajectoryDecorator.updateNode()`, after getting the `before` node and computing PAD changes, extract the agentId from overlay node properties:

```java
@Override
public void updateNode(String nodeId, NodeUpdate update, String tenantId) {
    if (!hasPadUpdate(update) || memoryStore == null) {
        delegate().updateNode(nodeId, update, tenantId);
        return;
    }

    MindMapNode before = delegate().getNode(nodeId, tenantId);
    Double      oldP   = before.pleasure();
    Double      oldA   = before.arousal();
    Double      oldD   = before.dominance();

    delegate().updateNode(nodeId, update, tenantId);

    Double newP = update.pleasure() != null ? update.pleasure() : oldP;
    Double newA = update.arousal() != null ? update.arousal() : oldA;
    Double newD = update.dominance() != null ? update.dominance() : oldD;

    if (Objects.equals(oldP, newP) && Objects.equals(oldA, newA) && Objects.equals(oldD, newD)) {return;}

    try {
        double p = newP != null ? newP : 0.0;
        double a = newA != null ? newA : 0.0;
        double d = newD != null ? newD : 0.0;

        io.casehub.platform.api.identity.PrincipalId principalId = null;
        String agentId = before.properties().get(io.casehub.neocortex.mindmap.OverlayRef.AGENT_ID);
        if (agentId != null) {
            principalId = io.casehub.platform.api.identity.PrincipalId.of(agentId);
        }

        var    input    = AffectEvents.toMemoryInput(nodeId, tenantId, p, a, d,
                              io.casehub.neocortex.cognitive.AffectType.INHERENT, principalId);
        String memoryId = memoryStore.store(input);
        eventSink.accept(new AffectRecorded(nodeId, tenantId, memoryId));
    } catch (Exception e) {
        java.util.logging.Logger.getLogger(AffectTrajectoryDecorator.class.getName())
                                .log(java.util.logging.Level.WARNING, "Affect trajectory recording failed for node " + nodeId, e);
    }
}
```

- [ ] **Step 3: Filter affect queries by agent in CognitiveProfile**

In `CognitiveProfile.computeTrajectory()`, add `withCallerPrincipalId` when `asSeenBy` is non-null. This applies to both the pre-computed path (line 242) and the fallback query path (line 244-255).

The fallback query block becomes:

```java
var memQuery = MemoryQuery.forSubjects(
                                  entityIds.stream().map(id -> io.casehub.neocortex.memory.Subject.of("unknown", id)).toList(),
                                  AffectEvents.DOMAIN, query.tenantId())
                          .withLimit(query.memoryLimit())
                          .withOrder(MemoryOrder.CHRONOLOGICAL);

if (query.asSeenBy() != null) {
    memQuery = memQuery.withCallerPrincipalId(query.asSeenBy());
}

affectMemories = memoryStore.query(memQuery);
```

This already exists for the fallback path. But the pre-computed path at line 242 (`memories.get(AffectEvents.DOMAIN)`) uses the memories from `queryMemories()` which already applies `callerPrincipalId`. So the pre-computed path is already correctly filtered.

Verify that `queryMemories()` at line 222-224 applies `callerPrincipalId`:
```java
if (query.asSeenBy() != null) {
    memQuery = memQuery.withCallerPrincipalId(query.asSeenBy());
}
```
Yes, it does (line 222-224). The fix is already in place for the read side — the missing piece is the write side (steps 1-2 above).

- [ ] **Step 4: Write integration test**

Create or find the test file. Test that two agents with different overlay PAD values get different trajectories:

```java
@Test
void agentScopedAffectTrajectories() {
    // Setup: in-memory store with principal visibility
    var memoryStore = new InMemoryMemoryStore();
    var mindMapStore = new InMemoryMindMapStore();

    // Store affect memories with different principals
    var agentA = PrincipalId.of("agentA");
    var agentB = PrincipalId.of("agentB");

    // Agent A: positive affect trajectory
    memoryStore.store(AffectEvents.toMemoryInput("node1", "t1", 0.5, 0.3, 0.4,
            AffectType.INHERENT, agentA));
    memoryStore.store(AffectEvents.toMemoryInput("node1", "t1", 0.8, 0.4, 0.6,
            AffectType.INHERENT, agentA));

    // Agent B: negative affect trajectory
    memoryStore.store(AffectEvents.toMemoryInput("node1", "t1", -0.3, 0.6, -0.2,
            AffectType.INHERENT, agentB));
    memoryStore.store(AffectEvents.toMemoryInput("node1", "t1", -0.7, 0.8, -0.5,
            AffectType.INHERENT, agentB));

    // Query as agent A — should see only agent A's memories + unowned
    var queryA = MemoryQuery.forSubject(Subject.of("node", "node1"),
                     AffectEvents.DOMAIN, "t1")
                     .withCallerPrincipalId(agentA);
    var memoriesA = memoryStore.query(queryA);

    // Query as agent B
    var queryB = MemoryQuery.forSubject(Subject.of("node", "node1"),
                     AffectEvents.DOMAIN, "t1")
                     .withCallerPrincipalId(agentB);
    var memoriesB = memoryStore.query(queryB);

    // Agents should see different memories
    var trajectoryA = AffectTrajectoryAnalyzer.analyze(memoriesA);
    var trajectoryB = AffectTrajectoryAnalyzer.analyze(memoriesB);

    assertThat(trajectoryA.trend()).isEqualTo(TrendDirection.IMPROVING);
    assertThat(trajectoryB.trend()).isEqualTo(TrendDirection.WORSENING);
}
```

- [ ] **Step 5: Run tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl memory-api,mindmap-core,cognitive-index`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add memory-api/src/main/java/io/casehub/neocortex/memory/mood/AffectEvents.java
git add mindmap-core/src/main/java/io/casehub/neocortex/mindmap/runtime/AffectTrajectoryDecorator.java
git add cognitive-index/src/test/java/io/casehub/neocortex/cognitive/index/AgentScopedAffectTrajectoryTest.java
git commit -m "fix(cognitive-index): agent-scoped affect trajectories for social comparison

AffectTrajectoryDecorator now extracts agentId from overlay node
properties and stores it as PrincipalId on affect memories.
AffectEvents gains a PrincipalId-aware overload. CognitiveProfile
already applies callerPrincipalId on affect queries via queryMemories().
This breaks the always-1.0 trajectory alignment in SocialComparison.

Refs #1225"
```

---

## References

- [#1225] — SE5: cognitive-index epic issue
- [#1220] — parent: Cognitive Pipeline Coherence
- [docs/specs/cognitive-review-analysis/2026-11-24-cognitive-review-triage.md] — finding categorisation
- [docs/specs/cognitive-review-analysis/2026-11-24-epic-plan.md] — epic plan
- [CognitiveDerivationEngine.java:1226-1229] — isLow/isHigh defaults
- [CognitiveMemoryRetriever.java:24,56-78] — shared tickContext field
- [AffectTrajectoryDecorator.java:27-57] — affect memory creation
- [CognitiveProfile.java:234-261] — trajectory computation
- [AffectEvents.java:24-33] — affect memory input factory
- [PersonalityFacet.java:27-28] — isHigh/isLow thresholds

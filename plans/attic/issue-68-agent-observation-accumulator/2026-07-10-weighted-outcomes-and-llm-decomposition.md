# Weighted Outcomes + LlmDecomposition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #43 — weighted routing outcomes
**Issue group:** #43, #13

**Goal:** Add weighted outcome scoring to CBR routing and implement LLM-backed
task decomposition with a properly typed TaskNode hierarchy.

**Architecture:** Two independent features sharing a branch. #43 modifies one
private method in `CbrAgentRoutingStrategy`. #13 restructures the `TaskNode`
sealed hierarchy (adding `LeafTask` intermediate interface and `PlannedTask`
variant), extracts card-building utilities from `LlmSelectedRouting`, and
implements `LlmDecomposition<T>` following the same `AgentProvider` pattern.

**Tech Stack:** Java 21, Mutiny (reactive), Jackson (JSON parsing), Mockito
(test mocking), AssertJ (assertions), JUnit 5

## Global Constraints

- Pre-release — breaking changes cost nothing
- No CDI annotations on new decomposition classes — plain instantiation
- `AgentProvider` for LLM invocation (not LangChain4j directly)
- `@Nullable` from `org.jspecify.annotations`
- All tests are plain JUnit 5 + Mockito — no Quarkus test runtime
- IntelliJ MCP mandatory for all .java edits — use `ide_insert_member`,
  `ide_replace_member`, `ide_edit_member`, `ide_refactor_rename`

---

### Task 1: Weighted Outcome Scoring (#43)

**Files:**
- Modify: `src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java:235-279`
- Test: `src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java`

**Interfaces:**
- Consumes: `PlanTrace.stepOutcome()` (String), `RoutingOutcome` enum values
- Produces: Same return type as before (`@Nullable String` — best worker ID). Ranking behaviour changes.

- [ ] **Step 1: Write failing tests for weighted scoring**

Add a new `@Nested class WeightedOutcomes` inside `CbrAgentRoutingStrategyTest`.
Use the same `PlanBasedCbr` setup pattern — create a strategy with `cbrStore` present
and `graphQuery` present (for fallback). Tests use `PlanTrace` with outcome strings
matching `RoutingOutcome` enum names.

```java
@Nested
class WeightedOutcomes {
    private CbrAgentRoutingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new CbrAgentRoutingStrategy(
                10, 0.5,
                presentInstance(cbrStore), presentInstance(graphQuery),
                absentInstance(), absentInstance(), absentInstance(),
                new TextOnlyFeatureExtractor());
    }

    @Test
    void gateExpiredCountsAsHalfCredit() {
        var expiredTrace = new PlanTrace("b", "analysis", "agent-a", "GATE_EXPIRED", 1, Map.of());
        var failTrace = new PlanTrace("b", "analysis", "agent-b", "FAILURE", 1, Map.of());
        var cbrCase = new PlanCbrCase("p", "s", "RESOLVED", 0.9, Map.of(),
                List.of(expiredTrace, failTrace));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
    }

    @Test
    void gateRejectedCountsAsQuarterCredit() {
        var rejectedTrace = new PlanTrace("b", "analysis", "agent-a", "GATE_REJECTED", 1, Map.of());
        var failTrace = new PlanTrace("b", "analysis", "agent-b", "FAILURE", 1, Map.of());
        var cbrCase = new PlanCbrCase("p", "s", "RESOLVED", 0.9, Map.of(),
                List.of(rejectedTrace, failTrace));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
    }

    @Test
    void boldWorkerBeatsFailingWorker() {
        // agent-a: 1 SUCCESS + 1 GATE_REJECTED = (1.0+0.25)/2 = 0.625
        // agent-b: 1 SUCCESS + 1 FAILURE       = (1.0+0.0)/2  = 0.5
        var aSuccess = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var aRejected = new PlanTrace("b", "analysis", "agent-a", "GATE_REJECTED", 1, Map.of());
        var bSuccess = new PlanTrace("b", "analysis", "agent-b", "SUCCESS", 1, Map.of());
        var bFail = new PlanTrace("b", "analysis", "agent-b", "FAILURE", 1, Map.of());
        var case1 = new PlanCbrCase("p1", "s1", "R", 0.9, Map.of(), List.of(aSuccess, bSuccess));
        var case2 = new PlanCbrCase("p2", "s2", "R", 0.8, Map.of(), List.of(aRejected, bFail));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(case1, 0.9), new ScoredCbrCase<>(case2, 0.8)));

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
    }

    @Test
    void consistentSuccessBeatsGateRejected() {
        var successTrace = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var rejectedTrace = new PlanTrace("b", "analysis", "agent-b", "GATE_REJECTED", 1, Map.of());
        var cbrCase = new PlanCbrCase("p", "s", "R", 0.9, Map.of(),
                List.of(successTrace, rejectedTrace));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
    }

    @Test
    void mixedOutcomesWeightedCorrectly() {
        // 2 SUCCESS + 1 GATE_REJECTED + 1 FAILURE = (1.0+1.0+0.25+0.0)/4 = 0.5625
        // vs 1 SUCCESS + 1 GATE_EXPIRED = (1.0+0.5)/2 = 0.75
        var aS1 = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var aS2 = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var aR = new PlanTrace("b", "analysis", "agent-a", "GATE_REJECTED", 1, Map.of());
        var aF = new PlanTrace("b", "analysis", "agent-a", "FAILURE", 1, Map.of());
        var bS = new PlanTrace("b", "analysis", "agent-b", "SUCCESS", 1, Map.of());
        var bE = new PlanTrace("b", "analysis", "agent-b", "GATE_EXPIRED", 1, Map.of());
        var c1 = new PlanCbrCase("p", "s", "R", 0.9, Map.of(), List.of(aS1, aS2, aR, aF, bS, bE));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(c1, 0.9)));

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        // agent-b (0.75) beats agent-a (0.5625)
        assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-b");
    }

    @Test
    void crossCaseAccumulationWeightsCorrectly() {
        var aSuccess = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var aRejected = new PlanTrace("b", "analysis", "agent-a", "GATE_REJECTED", 1, Map.of());
        var case1 = new PlanCbrCase("p1", "s1", "R", 0.9, Map.of(), List.of(aSuccess));
        var case2 = new PlanCbrCase("p2", "s2", "R", 0.8, Map.of(), List.of(aRejected));
        // Only one worker across cases: (1.0 + 0.25) / 2 = 0.625
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(case1, 0.9), new ScoredCbrCase<>(case2, 0.8)));

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
    }

    @Test
    void tiesBrokenByTotalCount() {
        // agent-a: 1 SUCCESS = 1.0 (count 1)
        // agent-b: 2 SUCCESS = 1.0 (count 2) — wins on count
        var aTrace = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var bTrace1 = new PlanTrace("b", "analysis", "agent-b", "SUCCESS", 1, Map.of());
        var bTrace2 = new PlanTrace("b", "analysis", "agent-b", "SUCCESS", 1, Map.of());
        var c1 = new PlanCbrCase("p", "s", "R", 0.9, Map.of(), List.of(aTrace, bTrace1, bTrace2));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(c1, 0.9)));

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-b");
    }

    @Test
    void unknownOutcomeTreatedAsFailure() {
        var unknownTrace = new PlanTrace("b", "analysis", "agent-a", "UNKNOWN_OUTCOME", 1, Map.of());
        var successTrace = new PlanTrace("b", "analysis", "agent-b", "SUCCESS", 1, Map.of());
        var cbrCase = new PlanCbrCase("p", "s", "R", 0.9, Map.of(),
                List.of(unknownTrace, successTrace));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-b");
    }

    @Test
    void successOnlyWorkerScoresOne() {
        var s1 = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var s2 = new PlanTrace("b", "analysis", "agent-a", "SUCCESS", 1, Map.of());
        var cbrCase = new PlanCbrCase("p", "s", "R", 0.9, Map.of(), List.of(s1, s2));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"))).await().indefinitely();

        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
        assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
    }

    @Test
    void failureOnlyWorkerScoresZero() {
        var f1 = new PlanTrace("b", "analysis", "agent-a", "FAILURE", 1, Map.of());
        var cbrCase = new PlanCbrCase("p", "s", "R", 0.9, Map.of(), List.of(f1));
        when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));
        when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                .thenReturn(List.of("agent-a"));

        var result = strategy.select(context("analysis"),
                List.of(candidate("agent-a"))).await().indefinitely();

        // 0.0 score — analyseByType returns null, falls through to graphQuery
        assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest="CbrAgentRoutingStrategyTest$WeightedOutcomes" -DfailIfNoTests=false`
Expected: Tests that rely on gate outcomes being weighted differently will fail
(current binary scoring treats all non-SUCCESS as 0).

- [ ] **Step 3: Implement weighted scoring in `analyseByType()`**

Use `ide_replace_member` to replace the `analyseByType` method body on
`CbrAgentRoutingStrategy`. The new implementation:

```java
private static final Map<String, Double> OUTCOME_WEIGHTS = Map.of(
        "SUCCESS", 1.0,
        "GATE_EXPIRED", 0.5,
        "GATE_REJECTED", 0.25,
        "FAILURE", 0.0
);

private @Nullable String analyseByType(
        final List<ScoredCbrCase<CbrCase>> results,
        final Set<String> eligibleIds,
        final String capabilityName) {
    final Map<String, double[]> workerStats = new HashMap<>(); // [weightedScore, total]

    for (final var scored : results) {
        final var cbrCase = scored.cbrCase();
        if (cbrCase instanceof PlanCbrCase planCase) {
            for (final PlanTrace trace : planCase.planTrace()) {
                if (capabilityName.equals(trace.capabilityName())
                        && trace.workerName() != null
                        && eligibleIds.contains(trace.workerName())) {
                    final var stats = workerStats.computeIfAbsent(
                            trace.workerName(), k -> new double[] {0.0, 0});
                    stats[1]++;
                    stats[0] += OUTCOME_WEIGHTS.getOrDefault(trace.stepOutcome(), 0.0);
                }
            }
        }
    }

    if (workerStats.isEmpty()) {
        return null;
    }

    String bestWorker = null;
    double bestRate = -1;
    int bestCount = 0;
    for (final var entry : workerStats.entrySet()) {
        final double weightedScore = entry.getValue()[0];
        final int total = (int) entry.getValue()[1];
        final double rate = weightedScore / total;
        if (rate > bestRate || (rate == bestRate && total > bestCount)) {
            bestRate = rate;
            bestCount = total;
            bestWorker = entry.getKey();
        }
    }
    return bestWorker;
}
```

- [ ] **Step 4: Run all CbrAgentRoutingStrategy tests**

Run: `mvn --batch-mode test -pl . -Dtest="CbrAgentRoutingStrategyTest"`
Expected: ALL tests pass — both new weighted tests and existing tests.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java \
  src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#43): weighted outcome scoring in CBR routing

Replace binary SUCCESS/not-SUCCESS counting with weighted scoring:
SUCCESS=1.0, GATE_EXPIRED=0.5, GATE_REJECTED=0.25, FAILURE=0.0.
Workers that trigger gate rejections are penalised less than workers
that outright fail.

Refs casehubio/blocks#43"
```

---

### Task 2: TaskNode Hierarchy — LeafTask + PlannedTask (#13)

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/decomposition/TaskNode.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/decomposition/Decomposition.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/HtnBuilder.java:84-122`
- Modify: `src/test/java/io/casehub/blocks/agentic/decomposition/TaskNodeTest.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/decomposition/StaticDecompositionTest.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/decomposition/IdentityDecompositionTest.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/pattern/HtnBuilderTest.java`

**Interfaces:**
- Consumes: `AgentRef` (from `io.casehub.blocks.agentic`)
- Produces: `TaskNode.LeafTask<T>` (sealed interface with `agent()`, `description()`),
  `TaskNode.PlannedTask<T>` (new record), updated `TaskNode.PrimitiveTask<T>` (new
  `description` field, `@Nullable` precondition/effect)

- [ ] **Step 1: Write failing tests for the new hierarchy**

Add new tests and update existing assertions in `TaskNodeTest`:

```java
// New tests to add:

@Nested
class LeafTaskContract {
    @Test
    void leafTaskAgentAccessorWorksForPrimitive() {
        var agent = AgentRef.external(s -> CompletableFuture.completedFuture(new AgentResult("ok")));
        TaskNode.LeafTask<String> leaf = new TaskNode.PrimitiveTask<>(null, agent, null, null);
        assertThat(leaf.agent()).isSameAs(agent);
    }

    @Test
    void leafTaskAgentAccessorWorksForPlanned() {
        var agent = AgentRef.external(s -> CompletableFuture.completedFuture(new AgentResult("ok")));
        TaskNode.LeafTask<String> leaf = new TaskNode.PlannedTask<>("do analysis", agent, null);
        assertThat(leaf.agent()).isSameAs(agent);
    }

    @Test
    void leafTaskDescriptionAccessorWorksForBothVariants() {
        var agent = AgentRef.external(s -> CompletableFuture.completedFuture(new AgentResult("ok")));
        TaskNode.LeafTask<String> withDesc = new TaskNode.PlannedTask<>("do analysis", agent, null);
        TaskNode.LeafTask<String> withoutDesc = new TaskNode.PrimitiveTask<>(null, agent, null, null);
        assertThat(withDesc.description()).isEqualTo("do analysis");
        assertThat(withoutDesc.description()).isNull();
    }
}

@Nested
class PlannedTaskVariant {
    @Test
    void carriesDescriptionAgentAndRationale() {
        var agent = AgentRef.external(s -> CompletableFuture.completedFuture(new AgentResult("ok")));
        var task = new TaskNode.PlannedTask<String>("analyse data", agent, "best fit for domain");
        assertThat(task.description()).isEqualTo("analyse data");
        assertThat(task.agent()).isSameAs(agent);
        assertThat(task.rationale()).isEqualTo("best fit for domain");
    }

    @Test
    void nullRationaleAllowed() {
        var agent = AgentRef.external(s -> CompletableFuture.completedFuture(new AgentResult("ok")));
        var task = new TaskNode.PlannedTask<String>("analyse data", agent, null);
        assertThat(task.rationale()).isNull();
    }

    @Test
    void rejectsNullDescription() {
        var agent = AgentRef.external(s -> CompletableFuture.completedFuture(new AgentResult("ok")));
        assertThatThrownBy(() -> new TaskNode.PlannedTask<String>(null, agent, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullAgent() {
        assertThatThrownBy(() -> new TaskNode.PlannedTask<String>("desc", null, null))
                .isInstanceOf(NullPointerException.class);
    }
}

// Update existing test:
@Test
void sealedInterfacePermitsLeafAndCompound() {
    assertThat(TaskNode.class.getPermittedSubclasses()).hasSize(2);
    assertThat(TaskNode.LeafTask.class.getPermittedSubclasses()).hasSize(2);
}
```

Also update all existing `PrimitiveTask` constructor calls to include `description`
as first arg (`null`):
- `TaskNodeTest`: `new TaskNode.PrimitiveTask<String>(null, agent, s -> true, s -> {})`
- `StaticDecompositionTest`: `new TaskNode.PrimitiveTask<String>(null, agent1, null, null)`
- `IdentityDecompositionTest`: `new TaskNode.PrimitiveTask<String>(null, agent, null, null)`
- `HtnBuilderTest`: all `new TaskNode.PrimitiveTask<>` calls add `null` first arg

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest="TaskNodeTest"`
Expected: Compile failure — `LeafTask`, `PlannedTask` don't exist yet.

- [ ] **Step 3: Implement the new TaskNode hierarchy**

Use `ide_edit_member` to replace the `TaskNode` class declaration with the full
new hierarchy:

```java
public sealed interface TaskNode<T>
        permits TaskNode.LeafTask, TaskNode.CompoundTask {

    sealed interface LeafTask<T> extends TaskNode<T>
            permits TaskNode.PrimitiveTask, TaskNode.PlannedTask {
        AgentRef agent();
        @Nullable String description();
    }

    record PrimitiveTask<T>(@Nullable String description, AgentRef agent,
                            @Nullable Predicate<T> precondition,
                            @Nullable Consumer<T> effect) implements LeafTask<T> {}

    record CompoundTask<T>(String name, List<DecompositionMethod<T>> methods)
            implements TaskNode<T> {
        public CompoundTask { methods = List.copyOf(methods); }
    }

    record PlannedTask<T>(String description, AgentRef agent,
                          @Nullable String rationale) implements LeafTask<T> {
        public PlannedTask {
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(agent, "agent");
        }
    }
}
```

Add necessary imports: `java.util.Objects`, `org.jspecify.annotations.Nullable`.

- [ ] **Step 4: Update `Decomposition` factory**

Use `ide_replace_member` on `Decomposition.primitive(AgentRef)` to update the
constructor call, and `ide_insert_member` to add new factories:

```java
public static <T> TaskNode.PrimitiveTask<T> primitive(AgentRef agent) {
    return new TaskNode.PrimitiveTask<>(null, agent, null, null);
}

public static <T> TaskNode.PrimitiveTask<T> primitive(String description, AgentRef agent) {
    return new TaskNode.PrimitiveTask<>(description, agent, null, null);
}

public static <T> TaskNode.PlannedTask<T> planned(String description, AgentRef agent) {
    return new TaskNode.PlannedTask<>(description, agent, null);
}

public static <T> TaskNode.PlannedTask<T> planned(String description, AgentRef agent,
                                                   String rationale) {
    return new TaskNode.PlannedTask<>(description, agent, rationale);
}
```

- [ ] **Step 5: Update `HtnBuilder.flatten()`**

Use `ide_replace_member` on `HtnBuilder.flatten()`. Two changes:
1. Return type: `List<TaskNode.PrimitiveTask<T>>` → `List<TaskNode.LeafTask<T>>`
2. Switch: match `LeafTask` instead of just `PrimitiveTask`

```java
private Uni<List<TaskNode.LeafTask<T>>> flatten(TaskNode<T> node, T state) {
    return switch (node) {
        case TaskNode.LeafTask<T> leaf -> Uni.createFrom().item(List.of(leaf));

        case TaskNode.CompoundTask<T> compound -> {
            var matchingMethod = compound.methods().stream()
                    .filter(m -> m.guard().test(state))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No decomposition method guard matched for task: " + compound.name()));

            var ctx = new DecompositionContext<>(state, List.of(), 0);
            yield matchingMethod.strategy()
                    .decompose(compound, ctx)
                    .flatMap(children -> {
                        var childFlattens = children.stream()
                                .map(child -> flatten(child, state))
                                .toList();

                        return Uni.combine().all().unis(childFlattens)
                                .combinedWith(results -> {
                                    var flattened = new ArrayList<TaskNode.LeafTask<T>>();
                                    for (var result : results) {
                                        @SuppressWarnings("unchecked")
                                        var taskList = (List<TaskNode.LeafTask<T>>) result;
                                        flattened.addAll(taskList);
                                    }
                                    return flattened;
                                });
                    });
        }
    };
}
```

Also update the `execute()` method's agent extraction (line ~56) to use
`LeafTask.agent()`:
```java
var agents = tasks.stream()
        .map(t -> new RoutingCandidate(t.agent(), null))
        .toList();
```

- [ ] **Step 6: Run all decomposition and pattern tests**

Run: `mvn --batch-mode test -pl . -Dtest="TaskNodeTest,StaticDecompositionTest,IdentityDecompositionTest,HtnBuilderTest"`
Expected: ALL pass.

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/agentic/decomposition/TaskNode.java \
  src/main/java/io/casehub/blocks/agentic/decomposition/Decomposition.java \
  src/main/java/io/casehub/blocks/agentic/pattern/HtnBuilder.java \
  src/test/java/io/casehub/blocks/agentic/decomposition/TaskNodeTest.java \
  src/test/java/io/casehub/blocks/agentic/decomposition/StaticDecompositionTest.java \
  src/test/java/io/casehub/blocks/agentic/decomposition/IdentityDecompositionTest.java \
  src/test/java/io/casehub/blocks/agentic/pattern/HtnBuilderTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#13): LeafTask sealed interface + PlannedTask variant

Restructure TaskNode hierarchy: TaskNode permits LeafTask | CompoundTask,
LeafTask permits PrimitiveTask | PlannedTask. LeafTask defines shared
agent() + description() accessors. PlannedTask carries description +
agent + rationale for LLM-decomposed tasks. PrimitiveTask gains nullable
description field and @Nullable annotations on precondition/effect.

Refs casehubio/blocks#13"
```

---

### Task 3: AgentCardSupport Extraction (#13)

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/AgentCardSupport.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/routing/LlmSelectedRouting.java:98-142`
- Test: `src/test/java/io/casehub/blocks/agentic/routing/LlmSelectedRoutingTest.java` (must still pass)

**Interfaces:**
- Consumes: `RoutingCandidate`, `AgentDescriptor`, `AgentRef.WorkerAgent`
- Produces: `AgentCardSupport.buildCard(RoutingCandidate, int)` → `String`,
  `AgentCardSupport.candidateName(RoutingCandidate, int)` → `String`

- [ ] **Step 1: Create `AgentCardSupport` with extracted methods**

Use the Write tool (new file). Extract `buildAgentCard()` and `candidateName()`
from `LlmSelectedRouting`:

```java
package io.casehub.blocks.agentic;

import io.casehub.eidos.api.AgentDescriptor;

import java.util.stream.Collectors;

public final class AgentCardSupport {
    private AgentCardSupport() {}

    public static String buildCard(RoutingCandidate candidate, int index) {
        var sb = new StringBuilder();
        var name = candidateName(candidate, index);
        sb.append("- Agent: \"").append(name).append("\"");

        if (candidate.ref() instanceof AgentRef.WorkerAgent w) {
            var desc = w.worker().description();
            if (desc != null && !desc.isBlank()) {
                sb.append("\n  Description: ").append(desc);
            }
        }

        var descriptor = candidate.descriptor();
        if (descriptor != null) {
            if (descriptor.briefing() != null && !descriptor.briefing().isBlank()) {
                sb.append("\n  Briefing: ").append(descriptor.briefing());
            }
            if (descriptor.capabilities() != null && !descriptor.capabilities().isEmpty()) {
                var caps = descriptor.capabilities().stream()
                        .map(c -> {
                            var capStr = c.name();
                            if (c.epistemicDomains() != null && !c.epistemicDomains().isEmpty()) {
                                capStr += " (domains: " + String.join(", ", c.epistemicDomains().keySet()) + ")";
                            }
                            return capStr;
                        })
                        .collect(Collectors.joining(", "));
                sb.append("\n  Capabilities: ").append(caps);
            }
            if (descriptor.slot() != null && !descriptor.slot().isBlank()) {
                sb.append("\n  Slot: ").append(descriptor.slot());
            }
        }
        return sb.toString();
    }

    public static String candidateName(RoutingCandidate candidate, int index) {
        if (candidate.descriptor() != null && candidate.descriptor().name() != null) {
            return candidate.descriptor().name();
        }
        if (candidate.ref() instanceof AgentRef.WorkerAgent w) {
            return w.worker().name();
        }
        return candidate.ref().getClass().getSimpleName() + "-" + index;
    }
}
```

- [ ] **Step 2: Refactor `LlmSelectedRouting` to delegate to `AgentCardSupport`**

Use `ide_replace_member` on `buildAgentCard` and `candidateName` in
`LlmSelectedRouting`:

```java
private String buildAgentCard(RoutingCandidate candidate, int index) {
    return AgentCardSupport.buildCard(candidate, index);
}

static String candidateName(RoutingCandidate candidate, int index) {
    return AgentCardSupport.candidateName(candidate, index);
}
```

- [ ] **Step 3: Run LlmSelectedRouting tests to verify no regression**

Run: `mvn --batch-mode test -pl . -Dtest="LlmSelectedRoutingTest"`
Expected: ALL pass — behaviour unchanged.

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/agentic/AgentCardSupport.java \
  src/main/java/io/casehub/blocks/agentic/routing/LlmSelectedRouting.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "refactor(#13): extract AgentCardSupport from LlmSelectedRouting

Move buildCard() and candidateName() to shared AgentCardSupport in the
agentic package. LlmSelectedRouting delegates to it. Prepares for reuse
by LlmDecomposition without duplicating card-building logic.

Refs casehubio/blocks#13"
```

---

### Task 4: LlmDecomposition Strategy (#13)

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/decomposition/LlmDecomposition.java`
- Create: `src/test/java/io/casehub/blocks/agentic/decomposition/LlmDecompositionTest.java`

**Interfaces:**
- Consumes: `AgentProvider` (platform-agent-api), `AgentCardSupport` (from Task 3),
  `DecompositionStrategy<T>`, `DecompositionContext<T>`, `TaskNode.CompoundTask`,
  `RoutingCandidate`, `AgentDescriptor`
- Produces: `LlmDecomposition<T>` implementing `DecompositionStrategy<T>`, returns
  `List<TaskNode<T>>` containing `PlannedTask` instances

- [ ] **Step 1: Write failing tests for LlmDecomposition**

Create new test file:

```java
package io.casehub.blocks.agentic.decomposition;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.blocks.agentic.AgentCardSupport;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LlmDecompositionTest {

    private static AgentRef dummyAgent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(new AgentResult("ok")));
    }

    private static RoutingCandidate candidate(String name, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(name).name(name).slot("default").tenancyId("test")
                .briefing(briefing)
                .capabilities(List.of(AgentCapability.builder().name("analysis").build()))
                .build();
        return new RoutingCandidate(dummyAgent(), descriptor);
    }

    private static AgentProvider providerReturning(String text) {
        return config -> Multi.createFrom().item(new AgentEvent.TextDelta(text));
    }

    private static AgentProvider failingProvider() {
        return config -> Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
    }

    @Nested
    class HappyPath {
        @Test
        void decomposesGoalIntoPlannedTasks() {
            var json = """
                    [{"agent": "analyst", "task": "review the data", "rationale": "domain expert"},
                     {"agent": "reporter", "task": "write the report", "rationale": "writing skills"}]
                    """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "data analysis"), candidate("reporter", "reporting"));
            var ctx = new DecompositionContext<>("initial state", agents, 0);
            var compound = new TaskNode.CompoundTask<String>("investigate", List.of());

            var result = decomp.decompose(compound, ctx).await().indefinitely();

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isInstanceOf(TaskNode.PlannedTask.class);
            var t0 = (TaskNode.PlannedTask<String>) result.get(0);
            assertThat(t0.description()).isEqualTo("review the data");
            assertThat(t0.rationale()).isEqualTo("domain expert");
            var t1 = (TaskNode.PlannedTask<String>) result.get(1);
            assertThat(t1.description()).isEqualTo("write the report");
        }

        @Test
        void preservesTaskOrdering() {
            var json = """
                    [{"agent": "reporter", "task": "step-1"},
                     {"agent": "analyst", "task": "step-2"}]
                    """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "a"), candidate("reporter", "r"));
            var ctx = new DecompositionContext<>("s", agents, 0);
            var compound = new TaskNode.CompoundTask<String>("goal", List.of());

            var result = decomp.decompose(compound, ctx).await().indefinitely();

            assertThat(result).hasSize(2);
            assertThat(((TaskNode.PlannedTask<String>) result.get(0)).description()).isEqualTo("step-1");
            assertThat(((TaskNode.PlannedTask<String>) result.get(1)).description()).isEqualTo("step-2");
        }

        @Test
        void parsesCodeFenceWrappedJson() {
            var json = """
                    ```json
                    [{"agent": "analyst", "task": "do work"}]
                    ```
                    """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new DecompositionContext<>("s", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    class AgentMatching {
        @Test
        void mapsAgentNamesByDescriptorName() {
            var json = """
                    [{"agent": "analyst", "task": "work"}]
                    """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new DecompositionContext<>("s", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(result).hasSize(1);
            assertThat(((TaskNode.PlannedTask<String>) result.get(0)).agent())
                    .isSameAs(agents.get(0).ref());
        }

        @Test
        void skipsUnknownAgentNames() {
            var json = """
                    [{"agent": "unknown", "task": "skip me"},
                     {"agent": "analyst", "task": "keep me"}]
                    """;
            var decomp = new LlmDecomposition<String>(providerReturning(json));
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new DecompositionContext<>("s", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(result).hasSize(1);
            assertThat(((TaskNode.PlannedTask<String>) result.get(0)).description()).isEqualTo("keep me");
        }
    }

    @Nested
    class ErrorHandling {
        @Test
        void returnsEmptyOnUnparseableResponse() {
            var decomp = new LlmDecomposition<String>(providerReturning("not json at all"));
            var agents = List.of(candidate("a", "a"));
            var ctx = new DecompositionContext<>("s", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyWhenAgentProviderFails() {
            var decomp = new LlmDecomposition<String>(failingProvider());
            var agents = List.of(candidate("a", "a"));
            var ctx = new DecompositionContext<>("s", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyOnEmptyLlmPlan() {
            var decomp = new LlmDecomposition<String>(providerReturning("[]"));
            var agents = List.of(candidate("a", "a"));
            var ctx = new DecompositionContext<>("s", agents, 0);

            var result = decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(result).isEmpty();
        }

        @Test
        void returnsInputUnchangedForNonCompoundTask() {
            var decomp = new LlmDecomposition<String>(failingProvider());
            var leaf = new TaskNode.PlannedTask<String>("task", dummyAgent(), null);
            var ctx = new DecompositionContext<>("s", List.of(), 0);

            var result = decomp.decompose(leaf, ctx).await().indefinitely();

            assertThat(result).containsExactly(leaf);
        }
    }

    @Nested
    class PromptConstruction {
        @Test
        void includesStateInPromptWhenPresent() {
            var promptCapture = new AtomicReference<String>();
            var provider = capturingProvider(promptCapture);
            var decomp = new LlmDecomposition<String>(provider, s -> "STATE:" + s);
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new DecompositionContext<>("my-state", agents, 0);

            decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(promptCapture.get()).contains("STATE:my-state");
        }

        @Test
        void omitsStateFromPromptWhenNull() {
            var promptCapture = new AtomicReference<String>();
            var provider = capturingProvider(promptCapture);
            var decomp = new LlmDecomposition<String>(provider);
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new DecompositionContext<>(null, agents, 0);

            decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(promptCapture.get()).doesNotContain("Current state");
        }

        @Test
        void passesCompoundTaskNameAsGoal() {
            var promptCapture = new AtomicReference<String>();
            var provider = capturingProvider(promptCapture);
            var decomp = new LlmDecomposition<String>(provider);
            var agents = List.of(candidate("analyst", "a"));
            var ctx = new DecompositionContext<>("s", agents, 0);

            decomp.decompose(new TaskNode.CompoundTask<>("investigate-fraud", List.of()), ctx)
                    .await().indefinitely();

            assertThat(promptCapture.get()).contains("investigate-fraud");
        }

        @Test
        void includesAgentCardsInPrompt() {
            var promptCapture = new AtomicReference<String>();
            var provider = capturingProvider(promptCapture);
            var decomp = new LlmDecomposition<String>(provider);
            var agents = List.of(candidate("analyst", "expert in data analysis"));
            var ctx = new DecompositionContext<>("s", agents, 0);

            decomp.decompose(new TaskNode.CompoundTask<>("g", List.of()), ctx)
                    .await().indefinitely();

            assertThat(promptCapture.get()).contains("analyst");
            assertThat(promptCapture.get()).contains("expert in data analysis");
        }
    }

    private static AgentProvider capturingProvider(AtomicReference<String> capture) {
        return config -> {
            capture.set(config.userMessage());
            return Multi.createFrom().item(new AgentEvent.TextDelta("[]"));
        };
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest="LlmDecompositionTest" -DfailIfNoTests=false`
Expected: Compile failure — `LlmDecomposition` doesn't exist.

- [ ] **Step 3: Implement `LlmDecomposition`**

Create new file using Write tool:

```java
package io.casehub.blocks.agentic.decomposition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.blocks.agentic.AgentCardSupport;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LlmDecomposition<T> implements DecompositionStrategy<T> {

    private static final System.Logger LOG = System.getLogger(LlmDecomposition.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a task planner. Given a goal, current state, and available agents, \
            decompose the goal into a sequence of agent tasks.

            Respond with JSON only: [{"agent": "<name>", "task": "<description>", "rationale": "<why>"}]

            Each step should be a concrete action assigned to the agent best suited for it. \
            Order matters — steps execute sequentially.""";

    private final AgentProvider agentProvider;
    private final Function<T, String> stateRenderer;

    public LlmDecomposition(AgentProvider agentProvider, Function<T, String> stateRenderer) {
        this.agentProvider = agentProvider;
        this.stateRenderer = stateRenderer;
    }

    public LlmDecomposition(AgentProvider agentProvider) {
        this(agentProvider, Object::toString);
    }

    @Override
    public Uni<List<TaskNode<T>>> decompose(TaskNode<T> compound,
                                            DecompositionContext<T> context) {
        if (!(compound instanceof TaskNode.CompoundTask<T> goal)) {
            return Uni.createFrom().item(List.of(compound));
        }

        return Uni.createFrom().item(() -> {
            try {
                var userPrompt = buildUserPrompt(goal, context);
                var config = AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt);

                var text = agentProvider.invoke(config)
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().indefinitely();

                return parseResponse(text, context.agents());
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "LLM decomposition failed", e);
                return List.<TaskNode<T>>of();
            }
        });
    }

    private String buildUserPrompt(TaskNode.CompoundTask<T> goal,
                                   DecompositionContext<T> context) {
        var sb = new StringBuilder();
        sb.append("Goal: ").append(goal.name()).append("\n\n");

        if (context.state() != null) {
            var stateStr = stateRenderer.apply(context.state());
            if (stateStr != null && !stateStr.isBlank()) {
                sb.append("Current state:\n").append(stateStr).append("\n\n");
            }
        }

        sb.append("Available agents:\n");
        for (int i = 0; i < context.agents().size(); i++) {
            sb.append(AgentCardSupport.buildCard(context.agents().get(i), i)).append("\n");
        }
        return sb.toString();
    }

    private List<TaskNode<T>> parseResponse(@Nullable String text,
                                            List<RoutingCandidate> agents) {
        if (text == null || text.isBlank()) return List.of();

        var trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }

        try {
            var root = MAPPER.readTree(trimmed);
            if (!root.isArray()) return List.of();

            var result = new ArrayList<TaskNode<T>>();
            for (JsonNode node : root) {
                var agentName = node.has("agent") ? node.get("agent").asText() : null;
                var task = node.has("task") ? node.get("task").asText() : null;
                var rationale = node.has("rationale") ? node.get("rationale").asText() : null;

                if (agentName == null || task == null) continue;

                var agentRef = resolveAgent(agentName, agents);
                if (agentRef == null) {
                    LOG.log(System.Logger.Level.WARNING,
                            "LLM named unknown agent ''{0}'' — skipping step", agentName);
                    continue;
                }

                result.add(new TaskNode.PlannedTask<>(task, agentRef, rationale));
            }
            return result;
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to parse LLM decomposition response", e);
            return List.of();
        }
    }

    private static @Nullable AgentRef resolveAgent(String name,
                                                   List<RoutingCandidate> agents) {
        for (int i = 0; i < agents.size(); i++) {
            if (name.equals(AgentCardSupport.candidateName(agents.get(i), i))) {
                return agents.get(i).ref();
            }
        }
        for (int i = 0; i < agents.size(); i++) {
            var candidate = agents.get(i);
            if (candidate.ref() instanceof AgentRef.WorkerAgent w
                    && name.equals(w.worker().name())) {
                return candidate.ref();
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: Run LlmDecomposition tests**

Run: `mvn --batch-mode test -pl . -Dtest="LlmDecompositionTest"`
Expected: ALL pass.

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test`
Expected: ALL tests pass — no regressions.

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/agentic/decomposition/LlmDecomposition.java \
  src/test/java/io/casehub/blocks/agentic/decomposition/LlmDecompositionTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#13): LlmDecomposition — LLM-backed goal-to-plan decomposition

Implement LlmDecomposition<T> — asks an LLM to decompose a CompoundTask
into a sequence of PlannedTasks. Uses AgentProvider for invocation and
AgentCardSupport for candidate card building. Parses JSON response with
code-fence stripping. Non-CompoundTask input passes through unchanged.

Refs casehubio/blocks#13"
```

---

### Task 5: Decomposition Factory Tests (#13)

**Files:**
- Test: `src/test/java/io/casehub/blocks/agentic/decomposition/DecompositionTest.java` (new file or
  add to existing test class)

**Interfaces:**
- Consumes: `Decomposition.planned()`, `Decomposition.primitive()` factories
- Produces: Test coverage for factory methods

- [ ] **Step 1: Write factory tests**

```java
package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class DecompositionFactoryTest {

    private static AgentRef dummyAgent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(new AgentResult("ok")));
    }

    @Test
    void primitiveFactoryBackwardCompatible() {
        var agent = dummyAgent();
        var task = Decomposition.primitive(agent);
        assertThat(task.agent()).isSameAs(agent);
        assertThat(task.description()).isNull();
        assertThat(task.precondition()).isNull();
        assertThat(task.effect()).isNull();
    }

    @Test
    void primitiveFactoryWithDescription() {
        var agent = dummyAgent();
        var task = Decomposition.primitive("build artifact", agent);
        assertThat(task.description()).isEqualTo("build artifact");
        assertThat(task.agent()).isSameAs(agent);
    }

    @Test
    void plannedFactoryCreatesPlannedTask() {
        var agent = dummyAgent();
        var task = Decomposition.planned("analyse data", agent);
        assertThat(task.description()).isEqualTo("analyse data");
        assertThat(task.agent()).isSameAs(agent);
        assertThat(task.rationale()).isNull();
    }

    @Test
    void plannedFactoryWithRationale() {
        var agent = dummyAgent();
        var task = Decomposition.planned("analyse data", agent, "domain expert");
        assertThat(task.description()).isEqualTo("analyse data");
        assertThat(task.agent()).isSameAs(agent);
        assertThat(task.rationale()).isEqualTo("domain expert");
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn --batch-mode test -pl . -Dtest="DecompositionFactoryTest"`
Expected: ALL pass (factories were implemented in Task 2).

- [ ] **Step 3: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/test/java/io/casehub/blocks/agentic/decomposition/DecompositionFactoryTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "test(#13): Decomposition factory method coverage

Tests for primitive() and planned() factory methods including backward
compatibility and new overloads.

Refs casehubio/blocks#13"
```

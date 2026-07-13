# Hybrid Decomposition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #45 — feat: hybrid decomposition strategy — static + LLM fallback (ChatHTN-style)
**Issue group:** #45

**Goal:** Implement `HybridDecomposition<T>`, a `DecompositionStrategy<T>` that
tries static decomposition first and falls back to LLM when no method guard matches.

**Architecture:** `HybridDecomposition` wraps two `DecompositionStrategy<T>` instances
(primary + fallback). On `decompose()`, delegates to primary; catches
`NoMethodMatchedException`; falls back to the second strategy. `StaticDecomposition`
is changed to throw `NoMethodMatchedException` (extends `IllegalStateException`).
`HtnBuilder.flatten()` is refactored to delegate to `this.decomposition` instead
of reimplementing guard matching inline.

**Tech Stack:** Java 21, Mutiny (`Uni`), JUnit 5, Mockito, AssertJ

## Global Constraints

- Pre-release: breaking changes are free
- All logging via `System.Logger`
- All new records must validate required fields with `Objects.requireNonNull`
- Tests are plain JUnit 5 + Mockito (no CDI container)
- `project_path` for IntelliJ MCP: `/Users/mdproctor/claude/casehub/blocks`

---

### Task 1: `NoMethodMatchedException` + `StaticDecomposition` change

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/decomposition/NoMethodMatchedException.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/decomposition/StaticDecomposition.java:16-17`
- Modify: `src/main/java/io/casehub/blocks/agentic/decomposition/TaskNode.java` (CompoundTask null check)
- Test: `src/test/java/io/casehub/blocks/agentic/decomposition/NoMethodMatchedExceptionTest.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/decomposition/StaticDecompositionTest.java:36-45`

**Interfaces:**
- Produces: `NoMethodMatchedException` — `extends IllegalStateException`, `taskName()` accessor.
  Used by Task 2 (`HybridDecomposition`) to catch fallback condition.
- Produces: `CompoundTask` null-safe `name` — `Objects.requireNonNull(name, "name")` in compact constructor.

- [ ] **Step 1: Write `NoMethodMatchedExceptionTest`**

```java
package io.casehub.blocks.agentic.decomposition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoMethodMatchedExceptionTest {

    @Test
    void carriesTaskName() {
        var ex = new NoMethodMatchedException("deploy-app");
        assertThat(ex.taskName()).isEqualTo("deploy-app");
    }

    @Test
    void extendsIllegalStateException() {
        var ex = new NoMethodMatchedException("task");
        assertThat(ex).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void messageIncludesTaskName() {
        var ex = new NoMethodMatchedException("my-task");
        assertThat(ex.getMessage()).contains("my-task");
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

Run: `mvn --batch-mode test -pl . -Dtest=NoMethodMatchedExceptionTest -DfailIfNoTests=false`
Expected: compilation error — `NoMethodMatchedException` does not exist

- [ ] **Step 3: Create `NoMethodMatchedException`**

Create file `src/main/java/io/casehub/blocks/agentic/decomposition/NoMethodMatchedException.java`:

```java
package io.casehub.blocks.agentic.decomposition;

public class NoMethodMatchedException extends IllegalStateException {

    private final String taskName;

    public NoMethodMatchedException(String taskName) {
        super("No decomposition method guard matched for task '" + taskName + "'");
        this.taskName = taskName;
    }

    public String taskName() {
        return taskName;
    }
}
```

- [ ] **Step 4: Run `NoMethodMatchedExceptionTest` — expect PASS**

Run: `mvn --batch-mode test -pl . -Dtest=NoMethodMatchedExceptionTest`
Expected: 3 tests PASS

- [ ] **Step 5: Update `StaticDecomposition` to throw `NoMethodMatchedException`**

Use `ide_replace_member` to replace the `decompose` method body in `StaticDecomposition.java`.
The only change: replace `new IllegalStateException("No decomposition method guard matched")`
with `new NoMethodMatchedException(ct.name())`.

New body:
```java
if (compound instanceof TaskNode.CompoundTask<T> ct) {
    for (var method : ct.methods()) {
        if (method.guard().test(context.state())) {
            return method.strategy().decompose(compound, context);
        }
    }
    return Uni.createFrom().failure(
        new NoMethodMatchedException(ct.name()));
}
return Uni.createFrom().item(ExecutionPlan.singleton((TaskNode.LeafTask<T>) compound));
```

- [ ] **Step 6: Add null check to `CompoundTask`**

Use `ide_edit_member` on `TaskNode.java` to replace the `CompoundTask` record declaration.
Add `Objects.requireNonNull(name, "name")` to the compact constructor:

```java
record CompoundTask<T>(String name, List<DecompositionMethod<T>> methods)
        implements TaskNode<T> {
    public CompoundTask {
        Objects.requireNonNull(name, "name");
        methods = List.copyOf(methods);
    }
}
```

- [ ] **Step 7: Update `StaticDecompositionTest.throwsWhenNoMethodMatches`**

Use `ide_replace_member` on `StaticDecompositionTest.java` to update the
`throwsWhenNoMethodMatches` test to assert `NoMethodMatchedException`:

```java
assertThatThrownBy(() -> decomp.decompose(compound, ctx).await().indefinitely())
    .isInstanceOf(NoMethodMatchedException.class)
    .hasMessageContaining("No decomposition method guard matched")
    .extracting(e -> ((NoMethodMatchedException) e).taskName())
    .isEqualTo("root");
```

- [ ] **Step 8: Run all decomposition tests**

Run: `mvn --batch-mode test -pl . -Dtest="StaticDecompositionTest,NoMethodMatchedExceptionTest"`
Expected: all PASS

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/agentic/decomposition/NoMethodMatchedException.java \
  src/main/java/io/casehub/blocks/agentic/decomposition/StaticDecomposition.java \
  src/main/java/io/casehub/blocks/agentic/decomposition/TaskNode.java \
  src/test/java/io/casehub/blocks/agentic/decomposition/NoMethodMatchedExceptionTest.java \
  src/test/java/io/casehub/blocks/agentic/decomposition/StaticDecompositionTest.java
```

Message: `feat(#45): NoMethodMatchedException + StaticDecomposition throws typed exception`

---

### Task 2: `HybridDecomposition<T>`

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/decomposition/HybridDecomposition.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/decomposition/Decomposition.java` (add `hybrid()` factory)
- Test: `src/test/java/io/casehub/blocks/agentic/decomposition/HybridDecompositionTest.java`

**Interfaces:**
- Consumes: `NoMethodMatchedException` from Task 1
- Consumes: `DecompositionStrategy<T>.decompose(TaskNode<T>, DecompositionContext<T>)` → `Uni<ExecutionPlan<T>>`
- Consumes: `AgentProvider` from platform-agent-api
- Consumes: `LlmDecomposition<T>(AgentProvider)` and `LlmDecomposition<T>(AgentProvider, Function<T, String>)`
- Produces: `HybridDecomposition<T>` with three constructors:
  - `HybridDecomposition(AgentProvider agentProvider)`
  - `HybridDecomposition(AgentProvider agentProvider, Function<T, String> stateRenderer)`
  - `HybridDecomposition(DecompositionStrategy<T> primaryStrategy, DecompositionStrategy<T> fallbackStrategy)`
- Produces: `Decomposition.hybrid(AgentProvider)` and `Decomposition.hybrid(AgentProvider, Function<T, String>)`

- [ ] **Step 1: Write `HybridDecompositionTest` — core tests**

```java
package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.plan.ExecutionPlan;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HybridDecompositionTest {

    private static AgentRef dummyAgent() {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    }

    private static RoutingCandidate candidate(String name, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(name).name(name).slot("default").tenancyId("test")
                .briefing(briefing)
                .capabilities(List.of(AgentCapability.builder().name("work").build()))
                .build();
        return new RoutingCandidate(dummyAgent(), descriptor);
    }

    private static AgentProvider providerReturning(String text) {
        var provider = mock(AgentProvider.class);
        when(provider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(text)));
        return provider;
    }

    private static DecompositionStrategy<String> staticThatMatches(TaskNode.LeafTask<String> task) {
        return (compound, ctx) -> Uni.createFrom().item(ExecutionPlan.singleton(task));
    }

    private static DecompositionStrategy<String> staticThatFails() {
        return (compound, ctx) -> Uni.createFrom().failure(new NoMethodMatchedException("test-task"));
    }

    private static DecompositionStrategy<String> fallbackReturning(TaskNode.LeafTask<String> task) {
        return (compound, ctx) -> Uni.createFrom().item(ExecutionPlan.singleton(task));
    }

    @Nested
    class StaticSucceeds {
        @Test
        void staticSucceeds_llmNotInvoked() {
            var staticTask = new TaskNode.PrimitiveTask<String>("s1", Instant.now(), "static-task", dummyAgent(), null, null);
            var fallbackTask = new TaskNode.PrimitiveTask<String>("f1", Instant.now(), "fallback-task", dummyAgent(), null, null);

            var hybrid = new HybridDecomposition<>(staticThatMatches(staticTask), fallbackReturning(fallbackTask));
            var compound = new TaskNode.CompoundTask<>("goal", List.of());
            var ctx = new DecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
            assertThat(result.topologicalSort().get(0).task()).isSameAs(staticTask);
        }

        @Test
        void multipleGuards_firstMatchWins() {
            var agent = dummyAgent();
            var prim = new TaskNode.PrimitiveTask<String>("p1", Instant.now(), null, agent, null, null);
            var static_ = new StaticDecomposition<String>();
            var fallback = mock(DecompositionStrategy.class);

            var compound = new TaskNode.CompoundTask<>("root", List.of(
                    new DecompositionMethod<>(s -> false, (c, x) -> Uni.createFrom().failure(new AssertionError("should not be called"))),
                    new DecompositionMethod<>(s -> true, (c, x) -> Uni.createFrom().item(ExecutionPlan.singleton(prim)))));

            var hybrid = new HybridDecomposition<>(static_, (DecompositionStrategy<String>) fallback);
            var ctx = new DecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.topologicalSort().get(0).task()).isSameAs(prim);
            verifyNoInteractions(fallback);
        }
    }

    @Nested
    class Fallback {
        @Test
        void staticFails_llmProducesPlan() {
            var fallbackTask = new TaskNode.PrimitiveTask<String>("f1", Instant.now(), "llm-planned", dummyAgent(), null, null);
            var hybrid = new HybridDecomposition<>(staticThatFails(), fallbackReturning(fallbackTask));
            var compound = new TaskNode.CompoundTask<>("goal", List.of());
            var ctx = new DecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
            assertThat(result.topologicalSort().get(0).task()).isSameAs(fallbackTask);
        }

        @Test
        void staticFails_llmAlsoFails_errorPropagates() {
            DecompositionStrategy<String> failingFallback = (compound, ctx) ->
                    Uni.createFrom().failure(new IllegalStateException("LLM also failed"));

            var hybrid = new HybridDecomposition<>(staticThatFails(), failingFallback);
            var compound = new TaskNode.CompoundTask<>("goal", List.of());
            var ctx = new DecompositionContext<>("state", List.of(), 0);

            assertThatThrownBy(() -> hybrid.decompose(compound, ctx).await().indefinitely())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("LLM also failed");
        }
    }

    @Nested
    class EdgeCases {
        @Test
        void leafTask_passedThrough() {
            var leaf = new TaskNode.PrimitiveTask<String>("l1", Instant.now(), "leaf", dummyAgent(), null, null);
            var static_ = new StaticDecomposition<String>();
            var fallback = mock(DecompositionStrategy.class);
            var hybrid = new HybridDecomposition<>(static_, (DecompositionStrategy<String>) fallback);
            var ctx = new DecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(leaf, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
            assertThat(result.topologicalSort().get(0).task()).isSameAs(leaf);
            verifyNoInteractions(fallback);
        }

        @Test
        void nonGuardException_propagatesWithoutFallback() {
            DecompositionStrategy<String> throwing = (compound, ctx) ->
                    Uni.createFrom().failure(new NullPointerException("bug"));
            var fallback = mock(DecompositionStrategy.class);

            var hybrid = new HybridDecomposition<>(throwing, (DecompositionStrategy<String>) fallback);
            var compound = new TaskNode.CompoundTask<>("goal", List.of());
            var ctx = new DecompositionContext<>("state", List.of(), 0);

            assertThatThrownBy(() -> hybrid.decompose(compound, ctx).await().indefinitely())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("bug");
            verifyNoInteractions(fallback);
        }

        @Test
        void fallbackWithEmptyAgents_logsWarning() {
            var fallbackTask = new TaskNode.PrimitiveTask<String>("f1", Instant.now(), "planned", dummyAgent(), null, null);
            var hybrid = new HybridDecomposition<>(staticThatFails(), fallbackReturning(fallbackTask));
            var compound = new TaskNode.CompoundTask<>("goal", List.of());
            var ctx = new DecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
        }
    }

    @Nested
    class Constructors {
        @Test
        void convenienceConstructor_createsWorkingInstance() {
            var json = "[{\"agent\": \"analyst\", \"task\": \"analyse\"}]";
            var provider = providerReturning(json);
            var hybrid = new HybridDecomposition<String>(provider);

            var agents = List.of(candidate("analyst", "data analysis"));
            var compound = new TaskNode.CompoundTask<>("goal", List.of());
            var ctx = new DecompositionContext<>("state", agents, 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
        }

        @Test
        void stateRendererConstructor_passesRendererToLlm() {
            var json = "[{\"agent\": \"analyst\", \"task\": \"analyse\"}]";
            var provider = providerReturning(json);
            var hybrid = new HybridDecomposition<String>(provider, s -> "RENDERED:" + s);

            var agents = List.of(candidate("analyst", "data analysis"));
            var compound = new TaskNode.CompoundTask<>("goal", List.of());
            var ctx = new DecompositionContext<>("state", agents, 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.nodes()).hasSize(1);
        }

        @Test
        void fullControlConstructor_usesBothStrategies() {
            var staticTask = new TaskNode.PrimitiveTask<String>("s1", Instant.now(), "static", dummyAgent(), null, null);
            var fallbackTask = new TaskNode.PrimitiveTask<String>("f1", Instant.now(), "fallback", dummyAgent(), null, null);

            var primary = staticThatMatches(staticTask);
            var fallback = fallbackReturning(fallbackTask);
            var hybrid = new HybridDecomposition<>(primary, fallback);

            var compound = new TaskNode.CompoundTask<>("goal", List.of());
            var ctx = new DecompositionContext<>("state", List.of(), 0);

            var result = hybrid.decompose(compound, ctx).await().indefinitely();
            assertThat(result.topologicalSort().get(0).task()).isSameAs(staticTask);
        }
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

Run: `mvn --batch-mode test -pl . -Dtest=HybridDecompositionTest -DfailIfNoTests=false`
Expected: compilation error — `HybridDecomposition` does not exist

- [ ] **Step 3: Create `HybridDecomposition`**

Create file `src/main/java/io/casehub/blocks/agentic/decomposition/HybridDecomposition.java`:

```java
package io.casehub.blocks.agentic.decomposition;

import io.casehub.blocks.agentic.plan.ExecutionPlan;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Uni;

import java.util.Objects;
import java.util.function.Function;

public class HybridDecomposition<T> implements DecompositionStrategy<T> {

    private static final System.Logger LOG = System.getLogger(HybridDecomposition.class.getName());

    private final DecompositionStrategy<T> primaryStrategy;
    private final DecompositionStrategy<T> fallbackStrategy;

    public HybridDecomposition(AgentProvider agentProvider) {
        this(new StaticDecomposition<>(), new LlmDecomposition<>(agentProvider));
    }

    public HybridDecomposition(AgentProvider agentProvider, Function<T, String> stateRenderer) {
        this(new StaticDecomposition<>(), new LlmDecomposition<>(agentProvider, stateRenderer));
    }

    public HybridDecomposition(DecompositionStrategy<T> primaryStrategy,
                                DecompositionStrategy<T> fallbackStrategy) {
        this.primaryStrategy = Objects.requireNonNull(primaryStrategy, "primaryStrategy");
        this.fallbackStrategy = Objects.requireNonNull(fallbackStrategy, "fallbackStrategy");
    }

    @Override
    public Uni<ExecutionPlan<T>> decompose(TaskNode<T> compound,
                                            DecompositionContext<T> context) {
        return primaryStrategy.decompose(compound, context)
                .invoke(plan -> LOG.log(System.Logger.Level.DEBUG,
                        "Primary strategy succeeded for ''{0}''", taskName(compound)))
                .onFailure(NoMethodMatchedException.class)
                .recoverWithUni(failure -> {
                    var taskName = ((NoMethodMatchedException) failure).taskName();
                    LOG.log(System.Logger.Level.INFO,
                            "No static method matched for ''{0}'' — falling back to LLM", taskName);
                    if (context.agents().isEmpty()) {
                        LOG.log(System.Logger.Level.WARNING,
                                "Fallback for task ''{0}'' has no agents — call .agents() on the builder",
                                taskName);
                    }
                    return fallbackStrategy.decompose(compound, context)
                            .invoke(plan -> LOG.log(System.Logger.Level.DEBUG,
                                    "Fallback produced plan with {0} task(s) for ''{1}''",
                                    plan.nodes().size(), taskName));
                });
    }

    private static <T> String taskName(TaskNode<T> node) {
        return node instanceof TaskNode.CompoundTask<T> ct ? ct.name() : "(leaf)";
    }
}
```

- [ ] **Step 4: Run `HybridDecompositionTest` — expect PASS**

Run: `mvn --batch-mode test -pl . -Dtest=HybridDecompositionTest`
Expected: 11 tests PASS

- [ ] **Step 5: Add `hybrid()` factory methods to `Decomposition`**

Use `ide_insert_member` on `Decomposition.java` to add two `hybrid()` methods
after the `staticTree()` method:

```java
public static <T> HybridDecomposition<T> hybrid(AgentProvider agentProvider) {
    return new HybridDecomposition<>(agentProvider);
}

public static <T> HybridDecomposition<T> hybrid(AgentProvider agentProvider,
                                                  Function<T, String> stateRenderer) {
    return new HybridDecomposition<>(agentProvider, stateRenderer);
}
```

Add import for `AgentProvider` and `Function` to `Decomposition.java`.

- [ ] **Step 6: Run all tests**

Run: `mvn --batch-mode test -pl . -Dtest="HybridDecompositionTest,NoMethodMatchedExceptionTest,StaticDecompositionTest"`
Expected: all PASS

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/agentic/decomposition/HybridDecomposition.java \
  src/main/java/io/casehub/blocks/agentic/decomposition/Decomposition.java \
  src/test/java/io/casehub/blocks/agentic/decomposition/HybridDecompositionTest.java
```

Message: `feat(#45): HybridDecomposition — static-first, LLM fallback strategy`

---

### Task 3: `HtnBuilder` refactoring — delegate `flatten()` to decomposition strategy

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/HtnBuilder.java:76-92` (flatten method)
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/HtnBuilder.java:36-39` (agents visibility)
- Test: `src/test/java/io/casehub/blocks/agentic/pattern/HtnBuilderTest.java` (add new tests)

**Interfaces:**
- Consumes: `DecompositionStrategy<T>.decompose(TaskNode<T>, DecompositionContext<T>)` → `Uni<ExecutionPlan<T>>`
- Consumes: `DecompositionContext<T>(T state, List<RoutingCandidate> agents, int depth)`
- Consumes: `AbstractPatternBuilder.candidateSupplier` field (protected)

- [ ] **Step 1: Write failing test — `flatten` delegates to decomposition strategy**

Add to `HtnBuilderTest.java` using `ide_insert_member`:

```java
@Test
void flattenDelegatesToDecompositionStrategy() {
    var log = new ArrayList<String>();
    var agent = AgentRef.external((Object in) -> {
        log.add("executed");
        return CompletableFuture.completedFuture(AgentResult.success(null, "done"));
    });

    var customPlan = new TaskNode.PrimitiveTask<String>("c1", java.time.Instant.now(), null, agent, null, null);

    DecompositionStrategy<String> customStrategy = (compound, ctx) ->
            Uni.createFrom().item(ExecutionPlan.singleton(customPlan));

    var rootTask = new TaskNode.CompoundTask<String>("root", List.of(
            new DecompositionMethod<>(s -> true,
                    (c, x) -> Uni.createFrom().failure(new AssertionError("should not be called — strategy should override")))));

    var result = Patterns.<String>htn()
            .decompose(customStrategy)
            .agents(agent)
            .rootTask(rootTask)
            .task("delegate-test")
            .execute("state")
            .await().indefinitely();

    assertThat(result).isInstanceOf(ExecutionResult.Completed.class);
    assertThat(log).containsExactly("executed");
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `mvn --batch-mode test -pl . -Dtest="HtnBuilderTest#flattenDelegatesToDecompositionStrategy"`
Expected: FAIL — `HtnBuilder.flatten()` uses inline guard matching, ignores the custom strategy.
The test will hit "should not be called" because `flatten()` evaluates the method guard directly.

- [ ] **Step 3: Refactor `HtnBuilder.flatten()` to delegate**

Use `ide_replace_member` on `HtnBuilder.java` to replace the `flatten` method body:

```java
return switch (node) {
    case TaskNode.LeafTask<T> leaf -> Uni.createFrom().item(ExecutionPlan.singleton(leaf));
    case TaskNode.CompoundTask<T> compound -> {
        var agents = candidateSupplier != null
                ? candidateSupplier.get() : java.util.List.<io.casehub.blocks.agentic.RoutingCandidate>of();
        var ctx = new DecompositionContext<>(state, agents, 0);
        yield decomposition.decompose(compound, ctx);
    }
};
```

- [ ] **Step 4: Override `agents()` as public in `HtnBuilder`**

Use `ide_insert_member` on `HtnBuilder.java` to add public `agents` overrides
after the `rootTask` method:

```java
@Override
public HtnBuilder<T> agents(io.casehub.blocks.agentic.AgentRef... agents) {
    return (HtnBuilder<T>) super.agents(agents);
}

@Override
public HtnBuilder<T> agents(io.casehub.blocks.agentic.RoutingCandidate... candidates) {
    return (HtnBuilder<T>) super.agents(candidates);
}
```

- [ ] **Step 5: Run the new test — expect PASS**

Run: `mvn --batch-mode test -pl . -Dtest="HtnBuilderTest#flattenDelegatesToDecompositionStrategy"`
Expected: PASS — custom strategy is invoked instead of inline guard matching

- [ ] **Step 6: Write test — agents passed to `DecompositionContext`**

Add to `HtnBuilderTest.java`:

```java
@Test
void agentsPassedToDecompositionContext() {
    var agent = AgentRef.external((Object in) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "done")));
    var captured = new java.util.concurrent.atomic.AtomicReference<DecompositionContext<String>>();

    DecompositionStrategy<String> capturing = (compound, ctx) -> {
        captured.set(ctx);
        var leaf = new TaskNode.PrimitiveTask<String>("c1", java.time.Instant.now(), null, agent, null, null);
        return Uni.createFrom().item(ExecutionPlan.singleton(leaf));
    };

    var rootTask = new TaskNode.CompoundTask<String>("root", List.of());

    Patterns.<String>htn()
            .decompose(capturing)
            .agents(agent)
            .rootTask(rootTask)
            .task("ctx-test")
            .execute("my-state")
            .await().indefinitely();

    assertThat(captured.get()).isNotNull();
    assertThat(captured.get().agents()).hasSize(1);
    assertThat(captured.get().state()).isEqualTo("my-state");
}
```

- [ ] **Step 7: Run new test — expect PASS**

Run: `mvn --batch-mode test -pl . -Dtest="HtnBuilderTest#agentsPassedToDecompositionContext"`
Expected: PASS

- [ ] **Step 8: Run all existing `HtnBuilderTest` tests — regression check**

Run: `mvn --batch-mode test -pl . -Dtest=HtnBuilderTest`
Expected: all PASS (existing tests still work with delegation)

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/agentic/pattern/HtnBuilder.java \
  src/test/java/io/casehub/blocks/agentic/pattern/HtnBuilderTest.java
```

Message: `feat(#45): HtnBuilder delegates flatten() to decomposition strategy`

---

### Task 4: Example — incident response hybrid test

**Files:**
- Create: `src/test/java/io/casehub/blocks/agentic/decomposition/examples/incident/IncidentResponseHybridTest.java`

**Interfaces:**
- Consumes: `HybridDecomposition<T>` from Task 2
- Consumes: `StaticDecomposition<T>`, `DecompositionMethod<T>`, `TaskNode.CompoundTask<T>`
- Consumes: `LlmDecomposition<T>` (via `HybridDecomposition` convenience constructor)
- Consumes: `AgentProvider` (mocked), `AgentEvent.TextDelta`

- [ ] **Step 1: Write the full example test**

```java
package io.casehub.blocks.agentic.decomposition.examples.incident;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.decomposition.*;
import io.casehub.blocks.agentic.plan.ExecutionPlan;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentResponseHybridTest {

    record IncidentState(String type, String severity, String description) {}

    private static AgentRef namedAgent(String name) {
        return AgentRef.external(s -> CompletableFuture.completedFuture(AgentResult.success(null, name + "-done")));
    }

    private static RoutingCandidate candidate(String name, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(name).name(name).slot("default").tenancyId("test")
                .briefing(briefing)
                .capabilities(List.of(AgentCapability.builder().name("incident-response").build()))
                .build();
        return new RoutingCandidate(namedAgent(name), descriptor);
    }

    private final AgentRef failoverAgent = namedAgent("failover-db");
    private final AgentRef verifyAgent = namedAgent("verify-connectivity");
    private final AgentRef notifyAgent = namedAgent("notify-stakeholders");
    private final AgentRef isolateAgent = namedAgent("isolate-systems");
    private final AgentRef forensicsAgent = namedAgent("forensic-analysis");
    private final AgentRef diagnoseAgent = namedAgent("diagnose-network");
    private final AgentRef rerouteAgent = namedAgent("reroute-traffic");

    private TaskNode.CompoundTask<IncidentState> incidentTree() {
        return new TaskNode.CompoundTask<>("respond-to-incident", List.of(
                new DecompositionMethod<>(
                        state -> "DATABASE_OUTAGE".equals(state.type()),
                        (compound, ctx) -> Uni.createFrom().item(ExecutionPlan.sequence(List.of(
                                new TaskNode.PrimitiveTask<>("t1", Instant.now(), "failover-db", failoverAgent, null, null),
                                new TaskNode.PrimitiveTask<>("t2", Instant.now(), "verify-connectivity", verifyAgent, null, null),
                                new TaskNode.PrimitiveTask<>("t3", Instant.now(), "notify-stakeholders", notifyAgent, null, null))))),
                new DecompositionMethod<>(
                        state -> "SECURITY_BREACH".equals(state.type()),
                        (compound, ctx) -> Uni.createFrom().item(ExecutionPlan.sequence(List.of(
                                new TaskNode.PrimitiveTask<>("t4", Instant.now(), "isolate-systems", isolateAgent, null, null),
                                new TaskNode.PrimitiveTask<>("t5", Instant.now(), "forensic-analysis", forensicsAgent, null, null),
                                new TaskNode.PrimitiveTask<>("t6", Instant.now(), "notify-stakeholders", notifyAgent, null, null))))),
                new DecompositionMethod<>(
                        state -> "NETWORK_FAILURE".equals(state.type()),
                        (compound, ctx) -> Uni.createFrom().item(ExecutionPlan.sequence(List.of(
                                new TaskNode.PrimitiveTask<>("t7", Instant.now(), "diagnose-network", diagnoseAgent, null, null),
                                new TaskNode.PrimitiveTask<>("t8", Instant.now(), "reroute-traffic", rerouteAgent, null, null),
                                new TaskNode.PrimitiveTask<>("t9", Instant.now(), "notify-stakeholders", notifyAgent, null, null)))))
        ));
    }

    @Test
    void databaseOutage_staticPlaybook() {
        var hybrid = new HybridDecomposition<>(new StaticDecomposition<>(),
                (compound, ctx) -> Uni.createFrom().failure(new AssertionError("LLM should not be called")));
        var state = new IncidentState("DATABASE_OUTAGE", "HIGH", "DB cluster down");
        var ctx = new DecompositionContext<>(state, List.of(), 0);

        var plan = hybrid.decompose(incidentTree(), ctx).await().indefinitely();

        assertThat(plan.nodes()).hasSize(3);
        var tasks = plan.topologicalSort();
        assertThat(tasks.get(0).task().description()).isEqualTo("failover-db");
        assertThat(tasks.get(1).task().description()).isEqualTo("verify-connectivity");
        assertThat(tasks.get(2).task().description()).isEqualTo("notify-stakeholders");
    }

    @Test
    void securityBreach_staticPlaybook() {
        var hybrid = new HybridDecomposition<>(new StaticDecomposition<>(),
                (compound, ctx) -> Uni.createFrom().failure(new AssertionError("LLM should not be called")));
        var state = new IncidentState("SECURITY_BREACH", "CRITICAL", "Unauthorised access detected");
        var ctx = new DecompositionContext<>(state, List.of(), 0);

        var plan = hybrid.decompose(incidentTree(), ctx).await().indefinitely();

        assertThat(plan.nodes()).hasSize(3);
        var tasks = plan.topologicalSort();
        assertThat(tasks.get(0).task().description()).isEqualTo("isolate-systems");
        assertThat(tasks.get(1).task().description()).isEqualTo("forensic-analysis");
        assertThat(tasks.get(2).task().description()).isEqualTo("notify-stakeholders");
    }

    @Test
    void novelIncident_llmFallback() {
        var llmJson = """
                [{"agent": "diagnose-network", "task": "check latency and throughput metrics", "rationale": "performance issues often start at network layer"},
                 {"agent": "notify-stakeholders", "task": "alert ops team", "rationale": "escalation needed"}]
                """;
        var provider = mock(AgentProvider.class);
        when(provider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(llmJson)));

        var agents = List.of(
                candidate("diagnose-network", "network diagnostics"),
                candidate("notify-stakeholders", "stakeholder notification"),
                candidate("failover-db", "database failover"),
                candidate("verify-connectivity", "connectivity verification"));

        var hybrid = new HybridDecomposition<IncidentState>(provider, state -> state.description());
        var state = new IncidentState("PERFORMANCE_DEGRADATION", "MEDIUM", "API response times elevated");
        var ctx = new DecompositionContext<>(state, agents, 0);

        var plan = hybrid.decompose(incidentTree(), ctx).await().indefinitely();

        assertThat(plan.nodes()).hasSize(2);
        var tasks = plan.topologicalSort();
        assertThat(tasks.get(0).task()).isInstanceOf(TaskNode.PlannedTask.class);
        assertThat(tasks.get(0).task().description()).isEqualTo("check latency and throughput metrics");
        assertThat(tasks.get(1).task().description()).isEqualTo("alert ops team");
    }
}
```

- [ ] **Step 2: Run example tests**

Run: `mvn --batch-mode test -pl . -Dtest=IncidentResponseHybridTest`
Expected: 3 tests PASS

- [ ] **Step 3: Run full test suite — regression check**

Run: `mvn --batch-mode test`
Expected: all PASS

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/test/java/io/casehub/blocks/agentic/decomposition/examples/incident/IncidentResponseHybridTest.java
```

Message: `test(#45): incident response example — static playbooks + LLM fallback`

---

### Task 5: CLAUDE.md update

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:**
- None — documentation only

- [ ] **Step 1: Add `HybridDecomposition` to the package table**

In the `## Package: \`io.casehub.blocks.agentic\`` section under the `agentic.decomposition`
sub-package entry, add to the description:

`HybridDecomposition` and `NoMethodMatchedException`

Update the decomposition sub-package row to include:
```
`agentic.decomposition` | Decomposition SPI: `DecompositionStrategy<T>`, `TaskNode` (sealed: LeafTask | CompoundTask; LeafTask sealed: PrimitiveTask, PlannedTask; LeafTask extends `TaskDescriptor`), `DecompositionMethod`, `DecompositionContext`, `IdentityDecomposition`, `StaticDecomposition`, `LlmDecomposition`, `HybridDecomposition`, `NoMethodMatchedException`
```

- [ ] **Step 2: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add CLAUDE.md
```

Message: `docs(#45): add HybridDecomposition to CLAUDE.md package table`

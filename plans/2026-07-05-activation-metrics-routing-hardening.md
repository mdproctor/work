# Activation Context, Metrics Listener, Routing Hardening — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use hortora:subagent-driven-development (recommended) or hortora:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close #7, #21, #31 — type-tighten ActivationContext, add OTel MetricsListener, harden AI routing strategies.

**Architecture:** Foundation fixes first (ActivationContext type, driver timing, callback enrichment), then MetricsListener built on those foundations, then routing hardening (trust extraction, config, parser, tests).

**Tech Stack:** Java 21, JUnit 5, Mockito, AssertJ, OpenTelemetry API 1.57.0, Jackson, MicroProfile Config

## Global Constraints

- blocks is a library JAR — no Quarkus runtime, no CDI container in tests
- All new `provided` dependencies must be declared in `pom.xml` (no reliance on transitive availability)
- Tests are plain JUnit 5 + Mockito — no `@QuarkusTest`
- Spec: `docs/specs/2026-07-05-activation-metrics-routing-hardening-design.md`
- Project: `/Users/mdproctor/claude/casehub/blocks`

---

### Task 1: ActivationContext type-tightening + missing tests (#7)

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/activation/ActivationContext.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/model/AbstractExecutionDriver.java:43` (field type)
- Modify: `src/test/java/io/casehub/blocks/agentic/model/AbstractExecutionDriverTest.java`

**Interfaces:**
- Consumes: `AggregationResult` (sealed interface from `io.casehub.blocks.agentic.aggregation`)
- Produces: `ActivationContext<T>` with `Optional<AggregationResult> lastAggregationResult()` — used by all `ActivationRule` implementations and Task 3 (MetricsListener)

- [ ] **Step 1: Write failing test — lastAggregationResult carries forward**

Add to `AbstractExecutionDriverTest.ActivationContextPopulation`:

```java
@Test
void lastAggregationResultCarriesForwardFromPreviousIteration() {
    var capturedContexts = new ArrayList<ActivationContext<String>>();
    ActivationRule<String> capturingActivation = ctx -> {
        capturedContexts.add(ctx);
        return Uni.createFrom().item(true);
    };

    var agent = AgentRef.external((Object input) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    var candidate = new RoutingCandidate(agent, null);

    var model = new ExecutionModel<String>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            capturingActivation,
            new PassThrough<>(),
            new MaxIterationsTermination<>(3),
            () -> List.of(candidate),
            FailurePolicy.defaults(),
            List.of(), "test");

    var driver = new OrchestratedDriver<String>();
    driver.execute(model, "ctx").await().indefinitely();

    assertThat(capturedContexts).hasSize(3);
    assertThat(capturedContexts.get(0).lastAggregationResult()).isEmpty();
    assertThat(capturedContexts.get(1).lastAggregationResult()).isPresent();
    assertThat(capturedContexts.get(1).lastAggregationResult().get())
            .isInstanceOf(AggregationResult.class);
    assertThat(capturedContexts.get(2).lastAggregationResult()).isPresent();
}
```

Add imports: `io.casehub.blocks.agentic.aggregation.AggregationResult`.

- [ ] **Step 2: Write failing test — consecutiveIdleActivations increments and resets**

```java
@Test
void consecutiveIdleActivationsIncrementsOnSkipAndResetsOnActivation() {
    var capturedContexts = new ArrayList<ActivationContext<String>>();
    var callCount = new int[]{0};
    ActivationRule<String> alternatingActivation = ctx -> {
        capturedContexts.add(ctx);
        boolean activate = callCount[0] != 1;
        callCount[0]++;
        return Uni.createFrom().item(activate);
    };

    var agent = AgentRef.external((Object input) ->
            CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
    var candidate = new RoutingCandidate(agent, null);

    var model = new ExecutionModel<String>(
            new FirstMatchRouting<>(c -> true),
            new IdentityDecomposition<>(),
            alternatingActivation,
            new PassThrough<>(),
            new MaxIterationsTermination<>(3),
            () -> List.of(candidate),
            FailurePolicy.defaults(),
            List.of(), "test");

    var driver = new OrchestratedDriver<String>();
    driver.execute(model, "ctx").await().indefinitely();

    assertThat(capturedContexts.get(0).consecutiveIdleActivations()).isEqualTo(0);
    assertThat(capturedContexts.get(1).consecutiveIdleActivations()).isEqualTo(1);
    assertThat(capturedContexts.get(2).consecutiveIdleActivations()).isEqualTo(0);
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest="AbstractExecutionDriverTest" -DfailIfNoTests=false`

Expected: compilation failure — `ActivationContext.lastAggregationResult()` returns `Optional<Object>`, test expects `Optional<AggregationResult>`.

- [ ] **Step 4: Type-tighten ActivationContext**

In `ActivationContext.java`, change:

```java
public record ActivationContext<T>(
        Object event,
        T state,
        AgentRef agent,
        int activationCount,
        Optional<AggregationResult> lastAggregationResult,
        int consecutiveIdleActivations
) {}
```

Add import: `io.casehub.blocks.agentic.aggregation.AggregationResult`.

In `AbstractExecutionDriver.java`, change field at line 43:

```java
protected AggregationResult lastAggregationResult = null;
```

Add import: `io.casehub.blocks.agentic.aggregation.AggregationResult`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest="AbstractExecutionDriverTest" -DfailIfNoTests=false`

Expected: all tests PASS.

- [ ] **Step 6: Run full test suite**

Run: `mvn --batch-mode test`

Expected: BUILD SUCCESS — no other code reads `lastAggregationResult()` as `Optional<Object>`.

- [ ] **Step 7: Commit**

```
feat(#7): type-tighten ActivationContext.lastAggregationResult + missing tests

Optional<Object> → Optional<AggregationResult>. Add tests for
lastAggregationResult carry-forward and consecutiveIdleActivations
increment/reset cycle.
```

---

### Task 2: AgentResult.duration fix + ExecutionEventListener callback enrichment

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/model/AbstractExecutionDriver.java:54-77,179-188,203-207`
- Modify: `src/main/java/io/casehub/blocks/agentic/model/ExecutionEventListener.java:23`
- Modify: `src/main/java/io/casehub/blocks/agentic/listener/EventLogListener.java:109-112`
- Modify: `src/main/java/io/casehub/blocks/agentic/listener/LedgerExecutionListener.java:120-127`
- Modify: `src/test/java/io/casehub/blocks/agentic/model/AbstractExecutionDriverTest.java`
- Modify: `src/test/java/io/casehub/blocks/agentic/model/ExecutionEventListenerContractTest.java`

**Interfaces:**
- Consumes: `AgentResult` record, `ExecutionResult` sealed interface
- Produces: `ExecutionEventListener.onExecutionComplete(ExecutionResult result, Duration executionDuration, int iterationCount)` — consumed by Task 3 (MetricsListener) and existing listeners

- [ ] **Step 1: Write failing test — AgentResult.duration is non-zero**

Add to `AbstractExecutionDriverTest`:

```java
@Nested
class AgentResultDuration {

    @Test
    void invokeAgentRecordsNonZeroDuration() {
        var capturedResults = new ArrayList<AgentResult>();
        var listener = new ExecutionEventListener() {
            @Override
            public void onAgentResult(AgentResult result) {
                capturedResults.add(result);
            }
        };

        var agent = AgentRef.external((Object input) -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            return CompletableFuture.completedFuture(AgentResult.success(null, "ok"));
        });
        var candidate = new RoutingCandidate(agent, null);

        var model = new ExecutionModel<String>(
                new FirstMatchRouting<>(c -> true),
                new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(),
                new PassThrough<>(),
                new MaxIterationsTermination<>(1),
                () -> List.of(candidate),
                FailurePolicy.defaults(),
                List.of(listener), "test");

        new OrchestratedDriver<String>().execute(model, "ctx").await().indefinitely();

        assertThat(capturedResults).hasSize(1);
        assertThat(capturedResults.get(0).duration()).isGreaterThan(Duration.ZERO);
    }

    @Test
    void failedInvocationStillRecordsDuration() {
        var capturedResults = new ArrayList<AgentResult>();
        var listener = new ExecutionEventListener() {
            @Override
            public void onAgentResult(AgentResult result) {
                capturedResults.add(result);
            }
        };

        AgentInvoker<String> failingInvoker = (agent, state) -> {
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            throw new RuntimeException("agent exploded");
        };

        var agent = AgentRef.external((Object input) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "unused")));
        var candidate = new RoutingCandidate(agent, null);

        var model = new ExecutionModel<String>(
                new FirstMatchRouting<>(c -> true),
                new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(),
                new PassThrough<>(),
                new MaxIterationsTermination<>(1),
                () -> List.of(candidate),
                FailurePolicy.defaults(),
                List.of(listener), "test");

        new OrchestratedDriver<>(failingInvoker).execute(model, "ctx").await().indefinitely();

        assertThat(capturedResults).hasSize(1);
        assertThat(capturedResults.get(0).duration()).isGreaterThan(Duration.ZERO);
        assertThat(capturedResults.get(0).status())
                .isEqualTo(AgentResult.AgentResultStatus.FAILURE);
    }
}
```

Add imports: `java.time.Duration`, `io.casehub.blocks.agentic.activation.OnExplicitDispatch`.

- [ ] **Step 2: Write failing test — onExecutionComplete receives duration and iteration count**

```java
@Nested
class EnrichedExecutionComplete {

    @Test
    void onExecutionCompleteReceivesDurationAndIterationCount() {
        var capturedDurations = new ArrayList<Duration>();
        var capturedIterations = new ArrayList<Integer>();
        var listener = new ExecutionEventListener() {
            @Override
            public void onExecutionComplete(ExecutionResult result,
                                            Duration executionDuration,
                                            int iterationCount) {
                capturedDurations.add(executionDuration);
                capturedIterations.add(iterationCount);
            }
        };

        var agent = AgentRef.external((Object input) ->
                CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
        var candidate = new RoutingCandidate(agent, null);

        var model = new ExecutionModel<String>(
                new FirstMatchRouting<>(c -> true),
                new IdentityDecomposition<>(),
                new OnExplicitDispatch<>(),
                new PassThrough<>(),
                new MaxIterationsTermination<>(3),
                () -> List.of(candidate),
                FailurePolicy.defaults(),
                List.of(listener), "test");

        new OrchestratedDriver<String>().execute(model, "ctx").await().indefinitely();

        assertThat(capturedDurations).hasSize(1);
        assertThat(capturedDurations.get(0)).isGreaterThan(Duration.ZERO);
        assertThat(capturedIterations).containsExactly(3);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest="AbstractExecutionDriverTest" -DfailIfNoTests=false`

Expected: compilation failure — `onExecutionComplete` signature doesn't match.

- [ ] **Step 4: Enrich ExecutionEventListener.onExecutionComplete**

In `ExecutionEventListener.java`, replace line 23:

```java
default void onExecutionComplete(ExecutionResult result) {}
```

with:

```java
default void onExecutionComplete(ExecutionResult result, Duration executionDuration,
                                 int iterationCount) {}
```

Add import: `java.time.Duration`.

- [ ] **Step 5: Update AbstractExecutionDriver — timing, iteration tracking, invokeAgent**

In `AbstractExecutionDriver.java`:

Add fields after line 43:

```java
protected Instant executionStart;
protected int iterationCount;
```

Add import: `java.time.Instant` (already present).

In `execute()` method (line 54-77), after `lastAggregationResult = null;` add:

```java
executionStart = Instant.now();
iterationCount = 0;
```

In `executeIteration()` method, add at the start of the method body (after line 92):

```java
iterationCount++;
```

Replace `notifyExecutionComplete` method (lines 203-207):

```java
protected void notifyExecutionComplete(ExecutionModel<T> model, ExecutionResult result) {
    var elapsed = Duration.between(executionStart, Instant.now());
    for (var listener : model.listeners()) {
        listener.onExecutionComplete(result, elapsed, iterationCount);
    }
}
```

Replace `invokeAgent` method (lines 179-188):

```java
protected AgentResult invokeAgent(ExecutionModel<T> model, AgentRef agent, T context) {
    transition(model, new ExecutionState.WaitingForAgent(agent));
    var start = Instant.now();
    try {
        var result = invoker.invoke(agent, context).await().indefinitely();
        var elapsed = Duration.between(start, Instant.now());
        return new AgentResult(result.agent(), result.output(), elapsed, result.status());
    } catch (Exception e) {
        var elapsed = Duration.between(start, Instant.now());
        LOG.log(System.Logger.Level.WARNING, "Agent invocation failed", e);
        notifyFailure(model, agent, e);
        return new AgentResult(agent, e.getMessage(), elapsed, AgentResult.AgentResultStatus.FAILURE);
    }
}
```

- [ ] **Step 6: Update EventLogListener.onExecutionComplete**

In `EventLogListener.java`, replace `onExecutionComplete` (lines 109-112):

```java
@Override
public void onExecutionComplete(ExecutionResult result, Duration executionDuration,
                                int iterationCount) {
    sink.record(OrchestrationEventType.EXECUTION_COMPLETED, Map.of(
            "result", result.getClass().getSimpleName(),
            "durationMs", executionDuration.toMillis(),
            "iterations", iterationCount));
}
```

Add import: `java.time.Duration`.

- [ ] **Step 7: Update LedgerExecutionListener.onExecutionComplete**

In `LedgerExecutionListener.java`, replace `onExecutionComplete` (lines 120-127):

```java
@Override
public void onExecutionComplete(ExecutionResult result, Duration executionDuration,
                                int iterationCount) {
    var data = new LinkedHashMap<String, Object>();
    data.put("result", result.getClass().getSimpleName());
    data.put("durationMs", executionDuration.toMillis());
    data.put("iterations", iterationCount);
    if (result instanceof ExecutionResult.Escalated e) data.put("reason", e.reason());
    if (result instanceof ExecutionResult.Failed f) data.put("reason", f.reason());
    sink.record(OrchestrationEventType.EXECUTION_COMPLETED,
            supervisorActorId, "orchestration-supervisor", data);
}
```

Add import: `java.time.Duration`.

- [ ] **Step 8: Update ExecutionEventListenerContractTest**

In `ExecutionEventListenerContractTest.java`, update the test:

```java
@Test
void anonymousImplementationCompiles() {
    ExecutionEventListener listener = new ExecutionEventListener() {};
    assertThatCode(() -> listener.onExecutionStart(null)).doesNotThrowAnyException();
    assertThatCode(() -> listener.onExecutionComplete(null, Duration.ZERO, 0))
            .doesNotThrowAnyException();
}
```

Add import: `java.time.Duration`.

- [ ] **Step 9: Run full test suite**

Run: `mvn --batch-mode test`

Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```
feat(#7,#21): driver timing + enriched onExecutionComplete callback

invokeAgent() now times invocations — AgentResult.duration is authoritative.
onExecutionComplete receives executionDuration and iterationCount from the
driver, eliminating listener-side state tracking. EventLogListener and
LedgerExecutionListener updated to log the new fields.
```

---

### Task 3: MetricsListener (#21)

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/io/casehub/blocks/agentic/listener/MetricsListener.java`
- Create: `src/test/java/io/casehub/blocks/agentic/listener/MetricsListenerTest.java`

**Interfaces:**
- Consumes: `ExecutionEventListener` (enriched from Task 2), `AgentResult.duration()` (fixed in Task 2), OTel `Meter` API
- Produces: OTel metrics — `casehub.agentic.agent.duration`, `casehub.agentic.routing.decisions`, `casehub.agentic.activation.evaluations`, `casehub.agentic.agent.failures`, `casehub.agentic.execution.duration`, `casehub.agentic.execution.iterations`

- [ ] **Step 1: Add OTel dependencies to pom.xml**

Add after the `casehub-neocortex-memory-api` dependency block:

```xml
<!-- Provided — OTel metrics instrumentation (consumer brings the SDK) -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <scope>provided</scope>
</dependency>
```

Add in the test dependencies section, after `awaitility`:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-testing</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Write failing test — MetricsListener records agent duration**

Create `src/test/java/io/casehub/blocks/agentic/listener/MetricsListenerTest.java`:

```java
package io.casehub.blocks.agentic.listener;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.aggregation.AggregationResult;
import io.casehub.blocks.agentic.model.ExecutionEventListener;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.ExecutionState;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsListenerTest {

    private InMemoryMetricReader reader;
    private MetricsListener listener;

    @BeforeEach
    void setUp() {
        reader = InMemoryMetricReader.create();
        var meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(reader)
                .build();
        listener = new MetricsListener(meterProvider.get("test"));
    }

    @Nested
    class AgentDuration {

        @Test
        void recordsAgentDurationFromResult() {
            var agent = AgentRef.external((Object input) ->
                    CompletableFuture.completedFuture(AgentResult.success(null, "ok")));
            var result = new AgentResult(agent, "ok", Duration.ofMillis(150),
                    AgentResult.AgentResultStatus.SUCCESS);

            listener.onAgentResult(result);

            var metrics = reader.collectAllMetrics();
            var histogram = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.agent.duration"))
                    .findFirst();
            assertThat(histogram).isPresent();
        }
    }

    @Nested
    class RoutingDecisions {

        @Test
        void countsRoutingDecisions() {
            var decision = new RoutingDecision.Selected(List.of(), "test");
            listener.onRoutingDecision(decision, List.of());

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.routing.decisions"))
                    .findFirst();
            assertThat(counter).isPresent();
        }
    }

    @Nested
    class ActivationEvaluations {

        @Test
        void countsActivationEvaluations() {
            var agent = AgentRef.human(null);
            listener.onActivation(agent, true);
            listener.onActivation(agent, false);

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.activation.evaluations"))
                    .findFirst();
            assertThat(counter).isPresent();
        }
    }

    @Nested
    class AgentFailures {

        @Test
        void countsAgentFailures() {
            var agent = AgentRef.human(null);
            listener.onFailure(agent, new RuntimeException("oops"));

            var metrics = reader.collectAllMetrics();
            var counter = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.agent.failures"))
                    .findFirst();
            assertThat(counter).isPresent();
        }
    }

    @Nested
    class ExecutionLifecycle {

        @Test
        void recordsExecutionDurationAndIterations() {
            var result = new ExecutionResult.Completed("done");
            listener.onExecutionComplete(result, Duration.ofSeconds(5), 7);

            var metrics = reader.collectAllMetrics();
            var duration = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.execution.duration"))
                    .findFirst();
            var iterations = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.execution.iterations"))
                    .findFirst();
            assertThat(duration).isPresent();
            assertThat(iterations).isPresent();
        }

        @Test
        void cancelledExecutionRecordsMetrics() {
            var result = new ExecutionResult.Cancelled();
            listener.onExecutionComplete(result, Duration.ofMillis(200), 1);

            var metrics = reader.collectAllMetrics();
            var duration = metrics.stream()
                    .filter(m -> m.getName().equals("casehub.agentic.execution.duration"))
                    .findFirst();
            assertThat(duration).isPresent();
        }
    }

    @Nested
    class NoOpSafety {

        @Test
        void worksWithNoOpMeter() {
            var noOpListener = new MetricsListener(
                    io.opentelemetry.api.OpenTelemetry.noop().getMeter("test"));
            var agent = AgentRef.human(null);
            noOpListener.onActivation(agent, true);
            noOpListener.onAgentResult(AgentResult.success(agent, "ok"));
            noOpListener.onFailure(agent, new RuntimeException("oops"));
            noOpListener.onExecutionComplete(new ExecutionResult.Completed("ok"),
                    Duration.ZERO, 1);
            // no exception = pass
        }
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest="MetricsListenerTest" -DfailIfNoTests=false`

Expected: compilation failure — `MetricsListener` class doesn't exist.

- [ ] **Step 4: Implement MetricsListener**

Create `src/main/java/io/casehub/blocks/agentic/listener/MetricsListener.java`:

```java
package io.casehub.blocks.agentic.listener;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.aggregation.AggregationResult;
import io.casehub.blocks.agentic.model.ExecutionEventListener;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.agentic.termination.TerminationDecision;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class MetricsListener implements ExecutionEventListener {

    private static final AttributeKey<String> AGENT = AttributeKey.stringKey("agent");
    private static final AttributeKey<String> STATUS = AttributeKey.stringKey("status");
    private static final AttributeKey<String> DECISION_TYPE = AttributeKey.stringKey("decision_type");
    private static final AttributeKey<String> ACTIVATED = AttributeKey.stringKey("activated");
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");

    private final DoubleHistogram agentDuration;
    private final LongCounter routingDecisions;
    private final LongCounter activationEvaluations;
    private final LongCounter agentFailures;
    private final DoubleHistogram executionDuration;
    private final DoubleHistogram executionIterations;

    public MetricsListener(Meter meter) {
        Objects.requireNonNull(meter, "meter");

        this.agentDuration = meter.histogramBuilder("casehub.agentic.agent.duration")
                .setUnit("s")
                .setDescription("Agent invocation latency")
                .build();

        this.routingDecisions = meter.counterBuilder("casehub.agentic.routing.decisions")
                .setDescription("Routing decision count by type")
                .build();

        this.activationEvaluations = meter.counterBuilder("casehub.agentic.activation.evaluations")
                .setDescription("Activation evaluation count by agent and outcome")
                .build();

        this.agentFailures = meter.counterBuilder("casehub.agentic.agent.failures")
                .setDescription("Agent invocation exception count")
                .build();

        this.executionDuration = meter.histogramBuilder("casehub.agentic.execution.duration")
                .setUnit("s")
                .setDescription("Total execution wall-clock time")
                .build();

        this.executionIterations = meter.histogramBuilder("casehub.agentic.execution.iterations")
                .setUnit("{iteration}")
                .setDescription("Iteration count per execution")
                .build();
    }

    @Override
    public void onRoutingDecision(RoutingDecision decision, List<RoutingCandidate> candidates) {
        routingDecisions.add(1, Attributes.of(
                DECISION_TYPE, decision.getClass().getSimpleName()));
    }

    @Override
    public void onActivation(AgentRef agent, boolean activated) {
        activationEvaluations.add(1, Attributes.of(
                AGENT, ExecutionEventListener.agentName(agent),
                ACTIVATED, String.valueOf(activated)));
    }

    @Override
    public void onAgentResult(AgentResult result) {
        agentDuration.record(result.duration().toMillis() / 1000.0, Attributes.of(
                AGENT, ExecutionEventListener.agentName(result.agent()),
                STATUS, result.status().name()));
    }

    @Override
    public void onFailure(AgentRef agent, Throwable cause) {
        agentFailures.add(1, Attributes.of(
                AGENT, ExecutionEventListener.agentName(agent)));
    }

    @Override
    public void onExecutionComplete(ExecutionResult result, Duration executionDuration,
                                    int iterationCount) {
        var outcome = result.getClass().getSimpleName();
        this.executionDuration.record(executionDuration.toMillis() / 1000.0,
                Attributes.of(OUTCOME, outcome));
        this.executionIterations.record(iterationCount,
                Attributes.of(OUTCOME, outcome));
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn --batch-mode test -pl . -Dtest="MetricsListenerTest" -DfailIfNoTests=false`

Expected: all PASS.

- [ ] **Step 6: Run full test suite**

Run: `mvn --batch-mode test`

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```
feat(#21): MetricsListener — OTel metrics for orchestration events

Six instruments: agent.duration, routing.decisions, activation.evaluations,
agent.failures, execution.duration, execution.iterations. Stateless design
using enriched onExecutionComplete callback. OTel API as provided dependency.
```

---

### Task 4: Rename LlmRoutingSupport → RoutingSupport + extract trust classification (#31)

**Files:**
- Rename: `src/main/java/io/casehub/blocks/routing/agent/LlmRoutingSupport.java` → `RoutingSupport.java`
- Rename: `src/test/java/io/casehub/blocks/routing/agent/LlmRoutingSupportTest.java` → `RoutingSupportTest.java`
- Modify: `src/main/java/io/casehub/blocks/routing/agent/LlmAgentRoutingStrategy.java`
- Modify: `src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java`

**Interfaces:**
- Consumes: `TrustCandidateClassifier`, `TrustScoreSource`, `TrustRoutingPolicyProvider`, `AgentRoutingContext`, `AgentCandidate`
- Produces: `RoutingSupport.TrustFilterOutcome` (sealed interface: `Proceed`, `Decided`) — consumed by both routing strategies. `RoutingSupport.applyTrustFilter(...)` static method. Also: `RoutingSupport.extractAgentName(String)`, `RoutingSupport.buildUserPrompt(...)`, `RoutingSupport.parseSelection(...)`, `RoutingSupport.invokeAndCollect(...)`

- [ ] **Step 1: Write failing test — TrustFilterOutcome.Proceed when no trust services**

Add to a new `@Nested class TrustFiltering` in `RoutingSupportTest.java` (after rename):

```java
@Nested
class TrustFiltering {

    @Test
    void proceedsWithAllCandidatesWhenNoTrustServices() {
        var candidates = List.of(
                candidateNoDescriptor("agent-a"),
                candidateNoDescriptor("agent-b"));
        var context = new AgentRoutingContext(
                UUID.randomUUID(), "analysis", NullNode.instance, "test");

        var outcome = RoutingSupport.applyTrustFilter(
                null, null, null, context, candidates);

        assertThat(outcome).isInstanceOf(RoutingSupport.TrustFilterOutcome.Proceed.class);
        var proceed = (RoutingSupport.TrustFilterOutcome.Proceed) outcome;
        assertThat(proceed.eligible()).hasSize(2);
        assertThat(proceed.classified()).isNull();
    }
}
```

Add imports: `java.util.UUID`, `com.fasterxml.jackson.databind.node.NullNode`, `io.casehub.api.spi.routing.AgentRoutingContext`.

- [ ] **Step 2: Write failing test — TrustFilterOutcome.Decided on bootstrap escalation**

```java
@Test
void decidesOnBootstrapEscalation() {
    var classifier = mock(TrustCandidateClassifier.class);
    var scoreSource = mock(TrustScoreSource.class);
    var policyProvider = mock(TrustRoutingPolicyProvider.class);
    var policy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), true, null);
    when(policyProvider.forCapability("analysis")).thenReturn(policy);

    var candidate = candidateNoDescriptor("bootstrap-agent");
    var classified = List.of(
            new TrustCandidateClassifier.ClassifiedCandidate(
                    candidate, TrustCandidateClassifier.Phase.BOOTSTRAP,
                    OptionalDouble.empty(), 1.0));
    when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
            .thenReturn(classified);

    var context = new AgentRoutingContext(
            UUID.randomUUID(), "analysis", NullNode.instance, "test");

    var outcome = RoutingSupport.applyTrustFilter(
            classifier, scoreSource, policyProvider, context, List.of(candidate));

    assertThat(outcome).isInstanceOf(RoutingSupport.TrustFilterOutcome.Decided.class);
    var decided = (RoutingSupport.TrustFilterOutcome.Decided) outcome;
    assertThat(decided.assignment()).isInstanceOf(AgentAssignment.EscalateToOversight.class);
}
```

Add imports for mock and trust types (already present in `LlmRoutingSupportTest`'s existing imports — extend them).

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest="RoutingSupportTest" -DfailIfNoTests=false`

Expected: compilation failure — `RoutingSupport.applyTrustFilter` doesn't exist.

- [ ] **Step 4: Rename LlmRoutingSupport → RoutingSupport using IntelliJ**

Use IntelliJ MCP refactor rename:
- `ide_refactor_rename` on `LlmRoutingSupport.java` → `RoutingSupport`
- `ide_refactor_rename` on `LlmRoutingSupportTest.java` → `RoutingSupportTest`

This updates all references in `LlmAgentRoutingStrategy` automatically.

- [ ] **Step 5: Add TrustFilterOutcome sealed interface and applyTrustFilter method**

In `RoutingSupport.java`, add:

```java
sealed interface TrustFilterOutcome {
    record Proceed(List<AgentCandidate> eligible,
                   @Nullable List<ClassifiedCandidate> classified)
            implements TrustFilterOutcome {}
    record Decided(AgentAssignment assignment)
            implements TrustFilterOutcome {}
}

static TrustFilterOutcome applyTrustFilter(
        @Nullable TrustCandidateClassifier classifier,
        @Nullable TrustScoreSource scoreSource,
        @Nullable TrustRoutingPolicyProvider policyProvider,
        AgentRoutingContext context,
        List<AgentCandidate> candidates) {

    if (classifier == null || scoreSource == null || policyProvider == null) {
        return new TrustFilterOutcome.Proceed(candidates, null);
    }

    TrustRoutingPolicy policy = policyProvider.forCapability(context.capabilityName());
    List<ClassifiedCandidate> classified =
            classifier.classify(candidates, context.capabilityName(), policy, scoreSource);

    if (policy.bootstrapEscalationRequired()) {
        boolean hasQualified =
                classified.stream().anyMatch(c -> c.phase() == Phase.QUALIFIED);
        boolean hasBootstrap =
                classified.stream().anyMatch(c -> c.phase() == Phase.BOOTSTRAP);
        if (!hasQualified && hasBootstrap) {
            return new TrustFilterOutcome.Decided(AgentAssignment.escalate(
                    context.capabilityName(),
                    EscalationReason.NO_QUALIFIED_AGENT,
                    "bootstrap only — no qualified agents for capability '%s'"
                            .formatted(context.capabilityName())));
        }
    }

    List<AgentCandidate> eligible = classified.stream()
            .filter(c -> !c.isExcluded())
            .filter(c -> !policy.bootstrapEscalationRequired() || c.phase() != Phase.BOOTSTRAP)
            .map(ClassifiedCandidate::candidate)
            .toList();

    if (eligible.isEmpty()) {
        List<ScoredCandidate> scored = classified.stream()
                .map(c -> new ScoredCandidate(c, 0.0, "excluded by trust filter"))
                .toList();
        return new TrustFilterOutcome.Decided(
                classifier.decide(classified, scored, context.capabilityName()));
    }

    return new TrustFilterOutcome.Proceed(eligible, classified);
}
```

Add imports: `AgentAssignment`, `AgentCandidate`, `AgentRoutingContext`, `EscalationReason`, `TrustRoutingPolicy`, `TrustRoutingPolicyProvider`, `TrustCandidateClassifier`, `TrustCandidateClassifier.ClassifiedCandidate`, `TrustCandidateClassifier.Phase`, `TrustCandidateClassifier.ScoredCandidate`, `TrustScoreSource`.

- [ ] **Step 6: Refactor LlmAgentRoutingStrategy to use applyTrustFilter**

Replace the trust classification block in `doSelect()` (lines 124-161) with:

```java
private AgentAssignment doSelect(
        AgentRoutingContext context, List<AgentCandidate> candidates) {
    var trustOutcome = RoutingSupport.applyTrustFilter(
            classifier, scoreSource, policyProvider, context, candidates);

    if (trustOutcome instanceof RoutingSupport.TrustFilterOutcome.Decided decided) {
        return decided.assignment();
    }

    var proceed = (RoutingSupport.TrustFilterOutcome.Proceed) trustOutcome;
    var eligible = proceed.eligible();

    String caseContextSummary = context.caseContext() != null
            && !context.caseContext().isNull()
            ? context.caseContext().toString()
            : null;
    String prompt = RoutingSupport.buildUserPrompt(
            context.capabilityName(), caseContextSummary, eligible);
    String response = RoutingSupport.invokeAndCollect(
            agentProvider, RoutingSupport.SYSTEM_PROMPT, prompt);

    if (response == null) {
        if (proceed.classified() != null) {
            var scored = proceed.classified().stream()
                    .map(c -> new ScoredCandidate(c, 0.0, "LLM invocation failed"))
                    .toList();
            return classifier.decide(proceed.classified(), scored, context.capabilityName());
        }
        return AgentAssignment.unresolvable("LLM invocation failed or returned no response");
    }

    String workerId = RoutingSupport.parseSelection(response, eligible);
    if (workerId == null) {
        if (proceed.classified() != null) {
            var scored = proceed.classified().stream()
                    .map(c -> new ScoredCandidate(c, 0.0, "LLM response unparseable"))
                    .toList();
            return classifier.decide(proceed.classified(), scored, context.capabilityName());
        }
        return AgentAssignment.unresolvable(
                "LLM response unparseable or selected unknown agent: " + response);
    }

    return AgentAssignment.assign(workerId,
            "LLM selected from %d candidates".formatted(eligible.size()));
}
```

- [ ] **Step 7: Refactor CbrAgentRoutingStrategy to use applyTrustFilter**

Replace the trust classification block in `doSelect()` (lines 159-198) with:

```java
private AgentAssignment doSelect(
        AgentRoutingContext context, List<AgentCandidate> candidates) {
    var trustOutcome = RoutingSupport.applyTrustFilter(
            classifier, scoreSource, policyProvider, context, candidates);

    if (trustOutcome instanceof RoutingSupport.TrustFilterOutcome.Decided decided) {
        return decided.assignment();
    }

    var proceed = (RoutingSupport.TrustFilterOutcome.Proceed) trustOutcome;
    var eligible = proceed.eligible();
    Set<String> eligibleIds = eligible.stream()
            .map(AgentCandidate::workerId)
            .collect(Collectors.toSet());

    if (cbrStore != null) {
        String cbrResult = tryCbrStore(context, eligibleIds);
        if (cbrResult != null) {
            return AgentAssignment.assign(cbrResult,
                    "CBR selected based on historical success rate");
        }
    }

    if (graphQuery != null) {
        String graphResult = tryGraphQuery(context, eligibleIds);
        if (graphResult != null) {
            return AgentAssignment.assign(graphResult,
                    "graph query fallback — CBR produced no match");
        }
    }

    if (proceed.classified() != null) {
        var scored = proceed.classified().stream()
                .map(c -> new ScoredCandidate(c, 0.0, "no CBR or graph match"))
                .toList();
        return classifier.decide(proceed.classified(), scored, context.capabilityName());
    }

    return AgentAssignment.unresolvable("no CBR or graph match found");
}
```

- [ ] **Step 8: Run full test suite**

Run: `mvn --batch-mode test`

Expected: BUILD SUCCESS — refactor preserves behavior.

- [ ] **Step 9: Commit**

```
refactor(#31): extract shared trust classification to RoutingSupport

Rename LlmRoutingSupport → RoutingSupport. Extract TrustFilterOutcome
sealed interface and applyTrustFilter() — eliminates ~35 lines of
duplicated trust classification between LlmAgentRoutingStrategy and
CbrAgentRoutingStrategy. Both strategies now fall back to
classifier.decide() on selection failure when trust context is present.
```

---

### Task 5: CBR config injection + parser hardening + NullNode fix + tests (#31)

**Files:**
- Modify: `src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java`
- Modify: `src/main/java/io/casehub/blocks/routing/agent/RoutingSupport.java`
- Modify: `src/test/java/io/casehub/blocks/routing/agent/LlmAgentRoutingStrategyTest.java`
- Modify: `src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java`
- Modify: `src/test/java/io/casehub/blocks/routing/agent/RoutingSupportTest.java`

**Interfaces:**
- Consumes: `RoutingSupport` (from Task 4), `CbrQuery.withMinSimilarity()`, `@ConfigProperty`
- Produces: configurable `topK` and `minSimilarity` on `CbrAgentRoutingStrategy`, Jackson-based parser, NullNode filtering

- [ ] **Step 1: Write failing test — Jackson parser handles markdown-wrapped JSON**

Add to `RoutingSupportTest.ResponseParsing`:

```java
@Test
void extractsAgentNameFromMarkdownWrappedJson() {
    assertThat(RoutingSupport.extractAgentName(
            "```json\n{\"agent\": \"reviewer\", \"reason\": \"best fit\"}\n```"))
            .isEqualTo("reviewer");
}

@Test
void extractsAgentNameWithEscapedQuotes() {
    assertThat(RoutingSupport.extractAgentName(
            "{\"agent\": \"agent-with-\\\"quotes\\\"\", \"reason\": \"ok\"}"))
            .isNull();
}

@Test
void returnsNullForEmptyString() {
    assertThat(RoutingSupport.extractAgentName("")).isNull();
}

@Test
void returnsNullForMissingAgentField() {
    assertThat(RoutingSupport.extractAgentName("{\"reason\": \"no agent\"}")).isNull();
}

@Test
void returnsNullForNumericAgentValue() {
    assertThat(RoutingSupport.extractAgentName("{\"agent\": 42}")).isNull();
}
```

- [ ] **Step 2: Replace extractAgentName with Jackson implementation**

In `RoutingSupport.java`, add field:

```java
private static final ObjectMapper MAPPER = new ObjectMapper();
```

Replace `extractAgentName` method:

```java
static @Nullable String extractAgentName(@Nullable String text) {
    if (text == null || text.isBlank()) return null;
    var trimmed = text.trim();
    if (trimmed.startsWith("```")) {
        int start = trimmed.indexOf('\n');
        int end = trimmed.lastIndexOf("```");
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start + 1, end).trim();
        }
    }
    try {
        var node = MAPPER.readTree(trimmed);
        var agentNode = node.get("agent");
        return agentNode != null && agentNode.isTextual() ? agentNode.asText() : null;
    } catch (Exception e) {
        return null;
    }
}
```

Add import: `com.fasterxml.jackson.databind.ObjectMapper`.

- [ ] **Step 3: Run parser tests**

Run: `mvn --batch-mode test -pl . -Dtest="RoutingSupportTest" -DfailIfNoTests=false`

Expected: all PASS.

- [ ] **Step 4: Write failing test — CbrAgentRoutingStrategy uses configured topK and minSimilarity**

Add to `CbrAgentRoutingStrategyTest.PlanBasedCbr`:

```java
@Test
void usesConfiguredTopKAndMinSimilarity() {
    var customStrategy = new CbrAgentRoutingStrategy(
            5, 0.8,
            presentInstance(cbrStore), presentInstance(graphQuery),
            absentInstance(), absentInstance(), absentInstance());

    when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
            .thenReturn(List.of());
    when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(5)))
            .thenReturn(List.of("agent-a"));

    var result = customStrategy.select(context("analysis"),
            List.of(candidate("agent-a"))).await().indefinitely();

    assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
}
```

- [ ] **Step 5: Add config parameters to CbrAgentRoutingStrategy constructor**

In `CbrAgentRoutingStrategy.java`, add fields:

```java
private final int topK;
private final double minSimilarity;
```

Update the `@Inject` constructor:

```java
@Inject
public CbrAgentRoutingStrategy(
        @ConfigProperty(name = "casehub.blocks.cbr.top-k", defaultValue = "10") int topK,
        @ConfigProperty(name = "casehub.blocks.cbr.min-similarity",
                        defaultValue = "0.5") double minSimilarity,
        Instance<CbrCaseMemoryStore> cbrStore,
        Instance<AgentGraphQuery> graphQuery,
        Instance<TrustCandidateClassifier> classifier,
        Instance<TrustScoreSource> scoreSource,
        Instance<TrustRoutingPolicyProvider> policyProvider) {
    this.topK = topK;
    this.minSimilarity = minSimilarity;
    this.cbrStore = cbrStore.isUnsatisfied() ? null : cbrStore.get();
    this.graphQuery = graphQuery.isUnsatisfied() ? null : graphQuery.get();
    this.classifier = classifier.isUnsatisfied() ? null : classifier.get();
    this.scoreSource = scoreSource.isUnsatisfied() ? null : scoreSource.get();
    this.policyProvider = policyProvider.isUnsatisfied() ? null : policyProvider.get();
}
```

Add import: `org.eclipse.microprofile.config.inject.ConfigProperty`.

Remove `DEFAULT_TOP_K` constant.

Update `tryCbrStore()` to use fields:

```java
CbrQuery query = CbrQuery.of(
        context.tenancyId(),
        new MemoryDomain(context.capabilityName()),
        "agent-routing",
        Map.of(),
        topK)
        .withMinSimilarity(minSimilarity);
```

Also update the `tryGraphQuery()` call to use `topK`:

```java
var ranked = graphQuery.topAgentsByOutcome(
        context.capabilityName(), null, null, topK);
```

- [ ] **Step 6: Fix all existing CbrAgentRoutingStrategy test constructors**

Every `new CbrAgentRoutingStrategy(...)` in `CbrAgentRoutingStrategyTest.java` needs the two new params. Add `10, 0.5,` as the first two arguments to each constructor call. For example:

```java
strategy = new CbrAgentRoutingStrategy(
        10, 0.5,
        presentInstance(cbrStore), presentInstance(graphQuery),
        absentInstance(), absentInstance(), absentInstance());
```

Update all constructor calls in: `PlanBasedCbr.setUp()`, `TextualFallback`, `Fallbacks`, `WithTrust`.

- [ ] **Step 7: Write mixed-pool trust edge case tests**

Add to `LlmAgentRoutingStrategyTest.WithTrust`:

```java
@Test
void qualifiedPlusBorderlineFiltersOnlyBorderline() {
    var qualified = candidate("qual-agent");
    var borderline = candidate("border-agent");
    var classified = List.of(
            new TrustCandidateClassifier.ClassifiedCandidate(
                    qualified, TrustCandidateClassifier.Phase.QUALIFIED,
                    OptionalDouble.of(0.9), 1.0),
            new TrustCandidateClassifier.ClassifiedCandidate(
                    borderline, TrustCandidateClassifier.Phase.BORDERLINE,
                    OptionalDouble.of(0.65), 1.0));
    when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
            .thenReturn(classified);
    agentReturns("{\"agent\": \"qual-agent\", \"reason\": \"only eligible\"}");

    var result = strategy.select(context("analysis"),
            List.of(qualified, borderline)).await().indefinitely();
    assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
    assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("qual-agent");
}

@Test
void qualifiedPlusBootstrapGuardOnFiltersBootstrap() {
    var bootstrapPolicy = new TrustRoutingPolicy(0.7, 5, 0.1, 0.5, Map.of(), true, null);
    when(policyProvider.forCapability("analysis")).thenReturn(bootstrapPolicy);
    var qualified = candidate("qual-agent");
    var bootstrap = candidate("boot-agent");
    var classified = List.of(
            new TrustCandidateClassifier.ClassifiedCandidate(
                    qualified, TrustCandidateClassifier.Phase.QUALIFIED,
                    OptionalDouble.of(0.9), 1.0),
            new TrustCandidateClassifier.ClassifiedCandidate(
                    bootstrap, TrustCandidateClassifier.Phase.BOOTSTRAP,
                    OptionalDouble.empty(), 1.0));
    when(classifier.classify(any(), eq("analysis"), eq(bootstrapPolicy), eq(scoreSource)))
            .thenReturn(classified);
    agentReturns("{\"agent\": \"qual-agent\", \"reason\": \"qualified\"}");

    var result = strategy.select(context("analysis"),
            List.of(qualified, bootstrap)).await().indefinitely();
    assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
    assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("qual-agent");
}

@Test
void qualifiedPlusBootstrapGuardOffPassesBothThrough() {
    var qualified = candidate("qual-agent");
    var bootstrap = candidate("boot-agent");
    var classified = List.of(
            new TrustCandidateClassifier.ClassifiedCandidate(
                    qualified, TrustCandidateClassifier.Phase.QUALIFIED,
                    OptionalDouble.of(0.9), 1.0),
            new TrustCandidateClassifier.ClassifiedCandidate(
                    bootstrap, TrustCandidateClassifier.Phase.BOOTSTRAP,
                    OptionalDouble.empty(), 1.0));
    when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
            .thenReturn(classified);
    agentReturns("{\"agent\": \"boot-agent\", \"reason\": \"picked bootstrap\"}");

    var result = strategy.select(context("analysis"),
            List.of(qualified, bootstrap)).await().indefinitely();
    assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
    assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("boot-agent");
}
```

- [ ] **Step 8: Write NullNode and empty stream tests**

Add to `LlmAgentRoutingStrategyTest.WithoutTrust`:

```java
@Test
void nullNodeCaseContextNotSentAsStringNull() {
    agentReturns("{\"agent\": \"agent-a\", \"reason\": \"ok\"}");
    var nullContext = new AgentRoutingContext(
            UUID.randomUUID(), "analysis", NullNode.instance, "test-tenant");
    var result = strategy.select(nullContext,
            List.of(candidate("agent-a"))).await().indefinitely();
    assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
}

@Test
void emptyTextDeltaStreamReturnsUnresolvable() {
    when(agentProvider.invoke(any(AgentSessionConfig.class)))
            .thenReturn(Multi.createFrom().empty());
    var result = strategy.select(context("analysis"),
            List.of(candidate("agent-a"))).await().indefinitely();
    assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
}
```

Add to `CbrAgentRoutingStrategyTest.PlanBasedCbr`:

```java
@Test
void nullNodeCaseContextBuildsQueryWithoutProblem() {
    when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
            .thenReturn(List.of());
    when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
            .thenReturn(List.of("agent-a"));

    var nullContext = new AgentRoutingContext(
            UUID.randomUUID(), "analysis", NullNode.instance, "test-tenant");
    var result = strategy.select(nullContext,
            List.of(candidate("agent-a"))).await().indefinitely();
    assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
}
```

- [ ] **Step 9: Write LLM classifier fallback tests**

Add to `LlmAgentRoutingStrategyTest.WithTrust`:

```java
@Test
void llmFailureFallsBackToClassifierDecideWhenTrustPresent() {
    var qualified = candidate("qual-agent");
    var classified = List.of(
            new TrustCandidateClassifier.ClassifiedCandidate(
                    qualified, TrustCandidateClassifier.Phase.QUALIFIED,
                    OptionalDouble.of(0.9), 1.0));
    when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
            .thenReturn(classified);
    when(agentProvider.invoke(any(AgentSessionConfig.class)))
            .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM down")));
    when(classifier.decide(any(), any(), eq("analysis")))
            .thenReturn(AgentAssignment.assign("qual-agent", "classifier fallback"));

    var result = strategy.select(context("analysis"),
            List.of(qualified)).await().indefinitely();
    assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
    verify(classifier).decide(any(), any(), eq("analysis"));
}

@Test
void unparseableLlmResponseFallsBackToClassifierDecideWhenTrustPresent() {
    var qualified = candidate("qual-agent");
    var classified = List.of(
            new TrustCandidateClassifier.ClassifiedCandidate(
                    qualified, TrustCandidateClassifier.Phase.QUALIFIED,
                    OptionalDouble.of(0.9), 1.0));
    when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
            .thenReturn(classified);
    agentReturns("not json at all");
    when(classifier.decide(any(), any(), eq("analysis")))
            .thenReturn(AgentAssignment.assign("qual-agent", "classifier fallback"));

    var result = strategy.select(context("analysis"),
            List.of(qualified)).await().indefinitely();
    assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
    verify(classifier).decide(any(), any(), eq("analysis"));
}
```

- [ ] **Step 10: Fix NullNode case context in CbrAgentRoutingStrategy.tryCbrStore()**

In `tryCbrStore()`, replace:

```java
final String caseContextText =
    context.caseContext() != null ? context.caseContext().toString() : null;
```

with:

```java
final String caseContextText =
    context.caseContext() != null && !context.caseContext().isNull()
    ? context.caseContext().toString() : null;
```

- [ ] **Step 11: Run full test suite**

Run: `mvn --batch-mode test`

Expected: BUILD SUCCESS.

- [ ] **Step 12: Commit**

```
feat(#31): routing hardening — config, parser, NullNode, mixed-pool tests

@ConfigProperty for CBR topK/minSimilarity (default 0.5 filters low-quality
matches). Jackson-based parser handles markdown-wrapped LLM responses.
NullNode.isNull() check prevents "null" string leaking to LLM/CBR.
Mixed-pool trust edge cases and classifier fallback tests added.
```

---

## CLAUDE.md Update

After all tasks complete, update `CLAUDE.md` to add MetricsListener to the package table and note the `onExecutionComplete` signature change.

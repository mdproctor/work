# Agentic Planning Phase 1 — Configurable Execution Backends

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #886 — Configurable execution backends
**Issue group:** #881, #882, #883, #884, #886

**Goal:** Create `casehub-engine-agentic` module that bridges blocks' pattern DSL
to the engine's durable worker execution pipeline, with auto-selection between
reactive (in-process) and engine-hosted backends.

**Architecture:** The blocks DSL produces an `ExecutionModel`. A new
`PatternWorkerFunctionHandler` runs the blocks driver inside the engine's worker
function boundary. `EngineAgentInvoker` bridges `AgentRef` dispatch to
`WorkerRuntime.execute()`. `EngineHostedBackend` implements `ExecutionBackend`
and is discovered via `ServiceLoader` for auto-selection.

**Tech Stack:** Java 21, Quarkus 3.32.2, CDI (ArC), Mutiny, Jandex

## Global Constraints

- All `@ConsumeEvent` handlers use `@RunOnVirtualThread` + `void` return (PP-20260723-c4c1cf)
- Plan-definition types in `engine-api`; execution types in `engine-common` (PP-20260727-5267d2)
- Foundation types (`WorkerFunction`, `WorkerResult`) live in `casehubio/worker` repo (PP-20260722-60e519)
- Module directory name: `agentic-engine` (per maven-submodule-folder-naming protocol)
- Maven version: `${version.io.casehub}` for all cross-project deps

---

### Task 1: Create casehub-engine-agentic Maven module

**Files:**
- Create: `agentic-engine/pom.xml`
- Create: `agentic-engine/src/main/java/io/casehub/engine/agentic/.gitkeep`
- Create: `agentic-engine/src/test/java/io/casehub/engine/agentic/.gitkeep`
- Create: `agentic-engine/src/test/resources/application.properties`
- Modify: `pom.xml` (root) — add `<module>agentic-engine</module>`

**Interfaces:**
- Consumes: nothing (bootstrapping)
- Produces: Maven module that compiles and runs empty test suite

- [ ] **Step 1: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-engine-parent</artifactId>
    <version>0.2-SNAPSHOT</version>
  </parent>

  <artifactId>casehub-engine-agentic</artifactId>
  <name>Case Hub :: Engine :: Agentic</name>

  <dependencies>
    <!-- Compile -->
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-blocks</artifactId>
      <version>${version.io.casehub}</version>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-engine-common</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-engine-api</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-engine</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-worker-api</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-arc</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-virtual-threads</artifactId>
    </dependency>

    <!-- Test -->
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-junit5</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-engine-persistence-memory</artifactId>
      <version>${project.version}</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-engine-scheduler-quartz</artifactId>
      <version>${project.version}</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-engine-ledger</artifactId>
      <version>${project.version}</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-ledger-testing</artifactId>
      <version>${version.io.casehub.ledger}</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-jdbc-h2</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-vertx</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <executions>
          <execution>
            <goals>
              <goal>generate-code</goal>
              <goal>generate-code-tests</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <parameters>true</parameters>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <forkedProcessTimeoutInSeconds>300</forkedProcessTimeoutInSeconds>
          <redirectTestOutputToFile>true</redirectTestOutputToFile>
          <systemPropertyVariables>
            <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
            <maven.home>${maven.home}</maven.home>
          </systemPropertyVariables>
        </configuration>
      </plugin>
      <plugin>
        <groupId>io.smallrye</groupId>
        <artifactId>jandex-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>make-index</id>
            <goals><goal>jandex</goal></goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Create test application.properties**

Create `agentic-engine/src/test/resources/application.properties`:

```properties
quarkus.http.test-port=0
quarkus.quartz.store-type=ram

quarkus.index-dependency.engine-common.group-id=io.casehub
quarkus.index-dependency.engine-common.artifact-id=casehub-engine-common

quarkus.index-dependency.engine.group-id=io.casehub
quarkus.index-dependency.engine.artifact-id=casehub-engine

quarkus.index-dependency.scheduler-quartz.group-id=io.casehub
quarkus.index-dependency.scheduler-quartz.artifact-id=casehub-engine-scheduler-quartz

quarkus.index-dependency.persistence-memory.group-id=io.casehub
quarkus.index-dependency.persistence-memory.artifact-id=casehub-engine-persistence-memory

quarkus.index-dependency.blocks.group-id=io.casehub
quarkus.index-dependency.blocks.artifact-id=casehub-blocks

quarkus.arc.selected-alternatives=\
  io.casehub.engine.persistence.memory.InMemoryCaseMetaModelRepository,\
  io.casehub.engine.persistence.memory.InMemoryCaseInstanceRepository,\
  io.casehub.engine.persistence.memory.InMemoryEventLogRepository,\
  io.casehub.engine.persistence.memory.InMemorySubCaseGroupRepository,\
  io.casehub.engine.persistence.memory.MemoryPlanItemStore

quarkus.arc.exclude-types=\
  io.casehub.ledger.service.CaseLedgerEventCapture,\
  io.casehub.ledger.service.WorkerDecisionEventCapture

quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
quarkus.datasource.username=sa
quarkus.datasource.password=
quarkus.hibernate-orm.schema-management.strategy=drop-and-create
quarkus.flyway.migrate-at-start=false
```

- [ ] **Step 3: Add module to root pom.xml**

Add `<module>agentic-engine</module>` after the `mcp` module entry in the
root `pom.xml` `<modules>` section.

- [ ] **Step 4: Verify module compiles**

Run: `mvn install -DskipTests -q -pl agentic-engine -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add agentic-engine/ pom.xml
git commit -m "feat(#886): scaffold casehub-engine-agentic module

Refs #886"
```

---

### Task 2: PatternWorkerFunction and EngineAgentInvoker

**Files:**
- Create: `agentic-engine/src/main/java/io/casehub/engine/agentic/PatternWorkerFunction.java`
- Create: `agentic-engine/src/main/java/io/casehub/engine/agentic/EngineAgentInvoker.java`
- Create: `agentic-engine/src/test/java/io/casehub/engine/agentic/PatternWorkerFunctionTest.java`
- Create: `agentic-engine/src/test/java/io/casehub/engine/agentic/EngineAgentInvokerTest.java`

**Interfaces:**
- Consumes: `WorkerFunction<Map, Map>` (worker-api), `ExecutionModel` (blocks),
  `AgentRef` (blocks), `WorkerRuntime` (engine-api), `PatternType` (blocks)
- Produces: `PatternWorkerFunction` record, `EngineAgentInvoker<T>` class.
  Later tasks use `PatternWorkerFunction` in the handler and provider.
  `EngineAgentInvoker` is constructed per-invocation by the handler with a
  `WorkerRuntime` parameter.

- [ ] **Step 1: Write PatternWorkerFunction test**

```java
package io.casehub.engine.agentic;

import io.casehub.blocks.agentic.model.PatternType;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PatternWorkerFunctionTest {

    @Test
    void inputAndOutputTypesAreMap() {
        var fn = new PatternWorkerFunction(null, PatternType.DEBATE, false);
        assertThat(fn.inputType()).isEqualTo(Map.class);
        assertThat(fn.outputType()).isEqualTo(Map.class);
    }

    @Test
    void recordFieldsAccessible() {
        var fn = new PatternWorkerFunction(null, PatternType.HTN, true);
        assertThat(fn.patternType()).isEqualTo(PatternType.HTN);
        assertThat(fn.checkpointingEnabled()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl agentic-engine -Dtest=PatternWorkerFunctionTest -q`
Expected: COMPILATION ERROR (PatternWorkerFunction not found)

- [ ] **Step 3: Implement PatternWorkerFunction**

```java
package io.casehub.engine.agentic;

import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.worker.api.WorkerFunction;
import java.util.Map;

public record PatternWorkerFunction(
    ExecutionModel<?> model,
    PatternType patternType,
    boolean checkpointingEnabled
) implements WorkerFunction<Map, Map> {

    @Override
    public Class<Map> inputType() { return Map.class; }

    @Override
    public Class<Map> outputType() { return Map.class; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl agentic-engine -Dtest=PatternWorkerFunctionTest -q`
Expected: PASS (2 tests)

- [ ] **Step 5: Write EngineAgentInvoker test**

```java
package io.casehub.engine.agentic;

import io.casehub.api.engine.WorkerRuntime;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.model.AgentResult;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineAgentInvokerTest {

    private WorkerRuntime runtime;
    private EngineAgentInvoker<Map<String, Object>> invoker;

    @BeforeEach
    void setUp() {
        runtime = mock(WorkerRuntime.class);
        invoker = new EngineAgentInvoker<>(runtime);
    }

    @Test
    void externalAgentCallsFunctionDirectly() {
        var called = new AtomicBoolean(false);
        var agent = AgentRef.external("test-agent", ctx -> {
            called.set(true);
            return Map.of("result", "done");
        });

        AgentResult result = invoker.invoke(agent, Map.of());
        assertThat(called.get()).isTrue();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void workerAgentDelegatesToRuntime() {
        when(runtime.execute(eq("analyst"), anyMap()))
            .thenReturn(WorkerResult.of(Map.of("analysis", "complete")));

        var worker = io.casehub.worker.api.Worker.builder()
            .name("analyst")
            .capabilityName("analysis")
            .noFunction()
            .build();
        var agent = AgentRef.worker(worker);

        AgentResult result = invoker.invoke(agent, Map.of("input", "data"));
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void channelAgentThrowsUnsupported() {
        var agent = AgentRef.channel(java.util.UUID.randomUUID(), (id, msg) -> {});

        assertThatThrownBy(() -> invoker.invoke(agent, Map.of()))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("ChannelAgent");
    }

    @Test
    void humanAgentThrowsUnsupported() {
        var agent = AgentRef.human(null);

        assertThatThrownBy(() -> invoker.invoke(agent, Map.of()))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("HumanAgent");
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn test -pl agentic-engine -Dtest=EngineAgentInvokerTest -q`
Expected: COMPILATION ERROR (EngineAgentInvoker not found)

- [ ] **Step 7: Implement EngineAgentInvoker**

```java
package io.casehub.engine.agentic;

import io.casehub.api.engine.WorkerRuntime;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.model.AgentInvoker;
import io.casehub.blocks.agentic.model.AgentResult;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.OrchestratedDriver;
import io.casehub.worker.api.WorkerResult;
import io.smallrye.mutiny.Uni;
import java.util.Map;

public class EngineAgentInvoker<T> implements AgentInvoker<T> {

    private final WorkerRuntime runtime;

    public EngineAgentInvoker(WorkerRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult invoke(AgentRef agent, T context) {
        return switch (agent) {
            case AgentRef.ExternalAgent ext -> invokeExternal(ext, context);
            case AgentRef.WorkerAgent wa -> invokeWorker(wa, context);
            case AgentRef.ComposedAgent ca -> invokeComposed(ca, context);
            case AgentRef.ChannelAgent ignored ->
                throw new UnsupportedOperationException(
                    "ChannelAgent dispatch not supported in v1 — requires Qhorus SPI on WorkerRuntime");
            case AgentRef.HumanAgent ignored ->
                throw new UnsupportedOperationException(
                    "HumanAgent dispatch not supported in v1 — requires WorkItem SPI on WorkerRuntime");
        };
    }

    @SuppressWarnings("unchecked")
    private AgentResult invokeExternal(AgentRef.ExternalAgent ext, T context) {
        try {
            Object result = ext.function().apply(context);
            return AgentResult.success(ext, result);
        } catch (Exception e) {
            return AgentResult.failure(ext, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private AgentResult invokeWorker(AgentRef.WorkerAgent wa, T context) {
        try {
            Map<String, Object> input = context instanceof Map
                ? (Map<String, Object>) context
                : Map.of("context", context);
            WorkerResult<?> result = runtime.execute(wa.worker().getName(), input);
            if (result.isSuccess()) {
                return AgentResult.success(wa, result.output());
            }
            return AgentResult.failure(wa, result.outcome().toString(), null);
        } catch (Exception e) {
            return AgentResult.failure(wa, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private AgentResult invokeComposed(AgentRef.ComposedAgent ca, T context) {
        try {
            var nestedInvoker = new EngineAgentInvoker<>(runtime);
            var driver = new OrchestratedDriver<>(nestedInvoker);
            ExecutionResult result = driver.execute(
                (io.casehub.blocks.agentic.model.ExecutionModel<T>) ca.model(), context)
                .await().indefinitely();
            return switch (result) {
                case ExecutionResult.Completed c -> AgentResult.success(ca, c.result());
                case ExecutionResult.Failed f -> AgentResult.failure(ca, f.reason(), f.cause());
                case ExecutionResult.Escalated e -> AgentResult.failure(ca, "Escalated: " + e.reason(), null);
                case ExecutionResult.Cancelled ignored -> AgentResult.failure(ca, "Cancelled", null);
            };
        } catch (Exception e) {
            return AgentResult.failure(ca, e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `mvn test -pl agentic-engine -Dtest="PatternWorkerFunctionTest,EngineAgentInvokerTest" -q`
Expected: PASS (6 tests)

- [ ] **Step 9: Commit**

```bash
git add agentic-engine/src/
git commit -m "feat(#886): add PatternWorkerFunction record and EngineAgentInvoker

PatternWorkerFunction wraps ExecutionModel as WorkerFunction<Map, Map>.
EngineAgentInvoker bridges AgentRef dispatch to WorkerRuntime.execute().
ExternalAgent calls function directly; WorkerAgent delegates to runtime;
ChannelAgent/HumanAgent throw UnsupportedOperationException in v1.

Refs #886"
```

---

### Task 3: PatternWorkerFunctionHandler

**Files:**
- Create: `agentic-engine/src/main/java/io/casehub/engine/agentic/PatternWorkerFunctionHandler.java`
- Create: `agentic-engine/src/test/java/io/casehub/engine/agentic/PatternWorkerFunctionHandlerTest.java`

**Interfaces:**
- Consumes: `WorkerFunctionHandler` SPI (engine-common), `WorkerRuntimeFactory` (runtime),
  `PatternWorkerFunction` (Task 2), `EngineAgentInvoker` (Task 2),
  `OrchestratedDriver` (blocks), `HandlerResult` (engine-common)
- Produces: `PatternWorkerFunctionHandler` — CDI-discovered handler that runs
  blocks patterns inside the engine worker boundary. Called by `DefaultWorkerExecutor`
  when a `PatternWorkerFunction` is dispatched.

- [ ] **Step 1: Write handler test**

```java
package io.casehub.engine.agentic;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.pattern.Patterns;
import io.casehub.engine.common.internal.executor.ExecutionMetadata;
import io.casehub.engine.common.internal.executor.HandlerResult;
import io.casehub.engine.common.internal.executor.WorkerFunctionHandler;
import io.casehub.engine.internal.executor.WorkerRuntimeFactory;
import io.casehub.api.engine.WorkerRuntime;
import io.casehub.api.model.worker.WorkerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatternWorkerFunctionHandlerTest {

    private WorkerRuntimeFactory runtimeFactory;
    private WorkerRuntime runtime;
    private PatternWorkerFunctionHandler handler;

    @BeforeEach
    void setUp() {
        runtimeFactory = mock(WorkerRuntimeFactory.class);
        runtime = mock(WorkerRuntime.class);
        when(runtimeFactory.create(any(UUID.class), anyString(), any(WorkerContext.class)))
            .thenReturn(runtime);
        handler = new PatternWorkerFunctionHandler(runtimeFactory);
    }

    @Test
    void supportsPatternWorkerFunction() {
        var fn = new PatternWorkerFunction(null, PatternType.DEBATE, false);
        assertThat(handler.supports(fn)).isTrue();
    }

    @Test
    void doesNotSupportOtherFunctions() {
        assertThat(handler.supports(io.casehub.worker.api.WorkerFunction.NONE)).isFalse();
    }

    @Test
    void executesSequencePatternWithExternalAgents() {
        var agent1 = AgentRef.external("a1", ctx -> Map.of("step", "1"));
        var agent2 = AgentRef.external("a2", ctx -> Map.of("step", "2"));

        ExecutionModel<Map<String, Object>> model = Patterns.<Map<String, Object>>sequence()
            .agents(agent1, agent2)
            .build();

        var fn = new PatternWorkerFunction(model, PatternType.SEQUENCE, false);
        var context = mock(WorkerContext.class);
        when(context.caseId()).thenReturn(UUID.randomUUID());
        var metadata = new ExecutionMetadata("test-worker", null, null, null);

        HandlerResult result = handler.execute(fn, Map.of(), context, 60000, metadata);
        assertThat(result).isNotNull();
        assertThat(result.result().isSuccess()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl agentic-engine -Dtest=PatternWorkerFunctionHandlerTest -q`
Expected: COMPILATION ERROR (PatternWorkerFunctionHandler not found)

- [ ] **Step 3: Implement PatternWorkerFunctionHandler**

```java
package io.casehub.engine.agentic;

import io.casehub.api.engine.WorkerRuntime;
import io.casehub.api.model.worker.WorkerContext;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.casehub.blocks.agentic.model.OrchestratedDriver;
import io.casehub.engine.common.internal.executor.ExecutionMetadata;
import io.casehub.engine.common.internal.executor.HandlerResult;
import io.casehub.engine.common.internal.executor.WorkerFunctionHandler;
import io.casehub.engine.internal.executor.WorkerRuntimeFactory;
import io.casehub.worker.api.WorkerFunction;
import io.casehub.worker.api.WorkerResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Map;

@ApplicationScoped
public class PatternWorkerFunctionHandler implements WorkerFunctionHandler {

    private final WorkerRuntimeFactory workerRuntimeFactory;

    @Inject
    public PatternWorkerFunctionHandler(WorkerRuntimeFactory workerRuntimeFactory) {
        this.workerRuntimeFactory = workerRuntimeFactory;
    }

    @Override
    public boolean supports(WorkerFunction<?, ?> function) {
        return function instanceof PatternWorkerFunction;
    }

    @Override
    @SuppressWarnings("unchecked")
    public HandlerResult execute(WorkerFunction<?, ?> function, Object inputData,
                                  WorkerContext context, int timeoutMs,
                                  ExecutionMetadata metadata) {
        var patternFn = (PatternWorkerFunction) function;
        WorkerRuntime runtime = workerRuntimeFactory.create(
            context.caseId(), metadata.workerName(), context);

        var invoker = new EngineAgentInvoker<>(runtime);
        var driver = new OrchestratedDriver<>(invoker);

        ExecutionResult result;
        try {
            result = driver.execute(patternFn.model(), inputData)
                .await().atMost(Duration.ofMillis(timeoutMs));
        } catch (Exception e) {
            driver.cancel();
            return new HandlerResult(
                WorkerResult.failed("Pattern execution failed: " + e.getMessage()),
                patternMetadata(patternFn));
        }

        WorkerResult<?> workerResult = switch (result) {
            case ExecutionResult.Completed c ->
                WorkerResult.of(c.result() instanceof Map m ? m : Map.of("result", c.result()));
            case ExecutionResult.Failed f -> WorkerResult.failed(f.reason());
            case ExecutionResult.Escalated e -> WorkerResult.failed("Escalated: " + e.reason());
            case ExecutionResult.Cancelled ignored -> WorkerResult.failed("Pattern cancelled");
        };

        return new HandlerResult(workerResult, patternMetadata(patternFn));
    }

    private Map<String, Object> patternMetadata(PatternWorkerFunction fn) {
        return Map.of(
            "patternType", fn.patternType().name(),
            "checkpointingEnabled", fn.checkpointingEnabled()
        );
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl agentic-engine -Dtest=PatternWorkerFunctionHandlerTest -q`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add agentic-engine/src/
git commit -m "feat(#886): add PatternWorkerFunctionHandler — runs blocks driver inside engine

Injects WorkerRuntimeFactory to create per-invocation WorkerRuntime.
EngineAgentInvoker bridges AgentRef to runtime.execute(). Timeout
enforcement via Uni.atMost() with driver.cancel() in catch block.
Returns HandlerResult with pattern metadata (patternType,
checkpointingEnabled).

Refs #886"
```

---

### Task 4: ExecutionBackend changes — reactive() rename + EngineHostedBackend + auto-selection

**Files:**
- Modify: `blocks: src/main/java/io/casehub/blocks/agentic/model/ExecutionBackend.java`
- Modify: `blocks: src/main/java/io/casehub/blocks/agentic/pattern/AbstractPatternBuilder.java`
- Create: `agentic-engine/src/main/java/io/casehub/engine/agentic/EngineHostedBackend.java`
- Create: `agentic-engine/src/main/resources/META-INF/services/io.casehub.blocks.agentic.model.ExecutionBackend`
- Create: `agentic-engine/src/test/java/io/casehub/engine/agentic/EngineHostedBackendTest.java`
- Test: `blocks: src/test/java/io/casehub/blocks/agentic/model/ExecutionBackendTest.java` (if exists, modify)

**Interfaces:**
- Consumes: `ExecutionBackend` (blocks), `PatternWorkerFunction` (Task 2),
  `ServiceLoader<ExecutionBackend>` (Java SPI)
- Produces: `ExecutionBackend.reactive()` factory (blocks), `EngineHostedBackend`
  class (engine-agentic). `AbstractPatternBuilder.execute()` auto-selects via
  ServiceLoader when no explicit backend is set.

- [ ] **Step 1: Add reactive() factory to ExecutionBackend in blocks**

Add to `ExecutionBackend.java` (blocks repo):

```java
static <T> ExecutionBackend<T> reactive() {
    return orchestrated();
}

@Deprecated
static <T> ExecutionBackend<T> orchestrated() {
    return (model, initialContext) ->
        new OrchestratedDriver<T>().execute(model, initialContext);
}
```

- [ ] **Step 2: Update AbstractPatternBuilder.execute() for ServiceLoader auto-selection**

Replace the `execute()` method in `AbstractPatternBuilder.java` (blocks repo,
lines 121-127):

```java
public Uni<ExecutionResult> execute(T initialContext) {
    var model = build();
    if (backend != null) {
        return backend.execute(model, initialContext);
    }
    return resolveDefaultBackend().execute(model, initialContext);
}

@SuppressWarnings({"unchecked", "rawtypes"})
private ExecutionBackend<T> resolveDefaultBackend() {
    ServiceLoader<ExecutionBackend> loader = ServiceLoader.load(ExecutionBackend.class);
    for (ExecutionBackend<?> candidate : loader) {
        if (!(candidate instanceof ExecutionBackend<?> eb
              && eb.getClass().getSimpleName().contains("Reactive"))) {
            return (ExecutionBackend<T>) candidate;
        }
    }
    return ExecutionBackend.reactive();
}
```

- [ ] **Step 3: Verify blocks tests still pass**

Run: `mvn test -f /Users/mdproctor/claude/casehub/slots/97/blocks/pom.xml -q`
Expected: All existing tests PASS (reactive is the default when no ServiceLoader
entries are found)

- [ ] **Step 4: Create EngineHostedBackend**

```java
package io.casehub.engine.agentic;

import io.casehub.blocks.agentic.model.ExecutionBackend;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.model.ExecutionResult;
import io.smallrye.mutiny.Uni;

public class EngineHostedBackend<T> implements ExecutionBackend<T> {

    @Override
    public Uni<ExecutionResult> execute(ExecutionModel<T> model, T initialContext) {
        throw new UnsupportedOperationException(
            "EngineHostedBackend requires engine runtime — use PatternWorkerFunction "
            + "via CaseDefinition YAML, not the programmatic builder API directly. "
            + "For programmatic use, call ExecutionBackend.reactive() explicitly.");
    }
}
```

Note: `EngineHostedBackend.execute()` is not called directly from the builder.
The programmatic path uses `ExecutionBackend.reactive()`. The engine-hosted path
constructs `PatternWorkerFunction` from YAML and dispatches via the engine's
worker pipeline. `EngineHostedBackend` exists for ServiceLoader discovery
(auto-detection that the engine module is on the classpath).

- [ ] **Step 5: Create ServiceLoader registration**

Create `agentic-engine/src/main/resources/META-INF/services/io.casehub.blocks.agentic.model.ExecutionBackend`:

```
io.casehub.engine.agentic.EngineHostedBackend
```

- [ ] **Step 6: Write EngineHostedBackend test**

```java
package io.casehub.engine.agentic;

import org.junit.jupiter.api.Test;
import java.util.ServiceLoader;
import io.casehub.blocks.agentic.model.ExecutionBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EngineHostedBackendTest {

    @Test
    void discoveredViaServiceLoader() {
        ServiceLoader<ExecutionBackend> loader = ServiceLoader.load(ExecutionBackend.class);
        boolean found = false;
        for (ExecutionBackend<?> backend : loader) {
            if (backend instanceof EngineHostedBackend) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void directExecuteThrowsUnsupported() {
        var backend = new EngineHostedBackend<>();
        assertThatThrownBy(() -> backend.execute(null, null))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("PatternWorkerFunction");
    }
}
```

- [ ] **Step 7: Run tests**

Run: `mvn test -pl agentic-engine -Dtest=EngineHostedBackendTest -q`
Expected: PASS (2 tests)

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/97/blocks add src/
git -C /Users/mdproctor/claude/casehub/slots/97/blocks commit -m "feat(#886): add ExecutionBackend.reactive(), ServiceLoader auto-selection

Rename orchestrated() to reactive() (deprecated alias kept).
AbstractPatternBuilder.execute() queries ServiceLoader for a
non-reactive backend before falling back to reactive(). When
casehub-engine-agentic is on the classpath, EngineHostedBackend
is discovered — signals the programmatic builder that engine
hosting is available.

Refs casehubio/engine#886"

git add agentic-engine/src/
git commit -m "feat(#886): add EngineHostedBackend with ServiceLoader registration

EngineHostedBackend implements ExecutionBackend for ServiceLoader
discovery. Direct execute() throws UnsupportedOperationException —
engine-hosted execution goes through PatternWorkerFunction via
CaseDefinition YAML, not the programmatic builder.

Refs #886"
```

---

### Task 5: PatternWorkerFunctionProvider — YAML detection

**Files:**
- Create: `agentic-engine/src/main/java/io/casehub/engine/agentic/PatternWorkerFunctionProvider.java`
- Create: `agentic-engine/src/test/java/io/casehub/engine/agentic/PatternWorkerFunctionProviderTest.java`

**Interfaces:**
- Consumes: `WorkerFunctionProvider` SPI (engine-api), `PatternWorkerFunction` (Task 2),
  `PatternType` (blocks), `JsonNode` (Jackson)
- Produces: `PatternWorkerFunctionProvider` — CDI-discovered provider that handles
  `pattern:` YAML blocks on worker definitions. Called by
  `DefaultWorkerFunctionProviderRegistry.createFunction()` during definition loading.

- [ ] **Step 1: Write provider test**

```java
package io.casehub.engine.agentic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.blocks.agentic.model.PatternType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PatternWorkerFunctionProviderTest {

    private PatternWorkerFunctionProvider provider;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        provider = new PatternWorkerFunctionProvider();
        mapper = new ObjectMapper();
    }

    @Test
    void handlesWorkerNodeWithPatternBlock() {
        ObjectNode node = mapper.createObjectNode();
        ObjectNode pattern = node.putObject("pattern");
        pattern.put("type", "debate");
        pattern.put("maxRounds", 5);

        assertThat(provider.handles(node)).isTrue();
    }

    @Test
    void doesNotHandleNodeWithoutPatternBlock() {
        ObjectNode node = mapper.createObjectNode();
        node.putObject("agent");

        assertThat(provider.handles(node)).isFalse();
    }

    @Test
    void createsPatternWorkerFunctionFromYaml() {
        ObjectNode node = mapper.createObjectNode();
        ObjectNode pattern = node.putObject("pattern");
        pattern.put("type", "debate");
        pattern.put("checkpointing", true);

        var fn = provider.create(node);
        assertThat(fn).isInstanceOf(PatternWorkerFunction.class);

        var patternFn = (PatternWorkerFunction) fn;
        assertThat(patternFn.patternType()).isEqualTo(PatternType.DEBATE);
        assertThat(patternFn.checkpointingEnabled()).isTrue();
    }

    @Test
    void defaultsCheckpointingToFalse() {
        ObjectNode node = mapper.createObjectNode();
        ObjectNode pattern = node.putObject("pattern");
        pattern.put("type", "sequence");

        var fn = (PatternWorkerFunction) provider.create(node);
        assertThat(fn.checkpointingEnabled()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl agentic-engine -Dtest=PatternWorkerFunctionProviderTest -q`
Expected: COMPILATION ERROR

- [ ] **Step 3: Implement PatternWorkerFunctionProvider**

```java
package io.casehub.engine.agentic;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.spi.WorkerFunctionProvider;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.worker.api.WorkerFunction;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PatternWorkerFunctionProvider implements WorkerFunctionProvider {

    @Override
    public boolean handles(JsonNode rawWorkerNode) {
        return rawWorkerNode.has("pattern");
    }

    @Override
    public WorkerFunction<?, ?> create(JsonNode rawWorkerNode) {
        JsonNode patternNode = rawWorkerNode.get("pattern");
        String typeName = patternNode.path("type").asText("sequence");
        PatternType patternType = PatternType.valueOf(typeName.toUpperCase());
        boolean checkpointing = patternNode.path("checkpointing").asBoolean(false);

        return new PatternWorkerFunction(null, patternType, checkpointing);
    }
}
```

Note: The `ExecutionModel` is null at provider time — it will be constructed
lazily at handler execution time from the `CaseDefinition` configuration in
Phase 2+ when YAML-declared patterns include agent references and routing
config. For Phase 1, only programmatically-constructed `PatternWorkerFunction`
instances (via `YamlCaseHub.augment()`) carry a populated model.

- [ ] **Step 4: Run tests**

Run: `mvn test -pl agentic-engine -Dtest=PatternWorkerFunctionProviderTest -q`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add agentic-engine/src/
git commit -m "feat(#886): add PatternWorkerFunctionProvider — detects pattern: YAML blocks

Implements WorkerFunctionProvider SPI. Handles worker nodes with
pattern: block, creates PatternWorkerFunction with patternType
and checkpointing config. ExecutionModel is constructed lazily
at handler execution time.

Refs #886"
```

---

### Task 6: Integration test — full pattern execution through engine pipeline

**Files:**
- Create: `agentic-engine/src/test/java/io/casehub/engine/agentic/PatternExecutionIntegrationTest.java`
- Create: `agentic-engine/src/test/java/io/casehub/engine/agentic/NoOpLedgerEntryRepository.java`

**Interfaces:**
- Consumes: All previous tasks. `CaseHubRuntime` (engine-api), `YamlCaseHub` (engine-api),
  `Worker` (worker-api), `PatternWorkerFunction` (Task 2), pattern builders (blocks)
- Produces: Verification that the full pipeline works: case start → binding fires →
  pattern worker dispatched → driver runs → agents execute via engine → result
  merged to context

- [ ] **Step 1: Create NoOpLedgerEntryRepository**

```java
package io.casehub.engine.agentic;

import io.casehub.ledger.spi.LedgerEntryRepository;
import io.quarkus.arc.Priority;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(1)
@ApplicationScoped
public class NoOpLedgerEntryRepository implements LedgerEntryRepository {
    // all methods return empty/no-op — same pattern as engine/src/test/
}
```

(Copy the exact implementation from `engine/src/test/java/io/casehub/engine/NoOpLedgerEntryRepository.java`)

- [ ] **Step 2: Write integration test**

```java
package io.casehub.engine.agentic;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.YamlCaseHub;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.model.PatternType;
import io.casehub.blocks.agentic.pattern.Patterns;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class PatternExecutionIntegrationTest {

    @Inject
    CaseHubRuntime runtime;

    static class TestPatternCaseHub extends YamlCaseHub {
        @Override
        protected String yamlResource() {
            return "test-pattern-case.yaml";
        }

        @Override
        protected void augment(CaseDefinition definition) {
            var agent1 = AgentRef.external("analyst-1", ctx -> Map.of("analysis", "step-1-done"));
            var agent2 = AgentRef.external("analyst-2", ctx -> Map.of("report", "step-2-done"));

            var model = Patterns.<Map<String, Object>>sequence()
                .agents(agent1, agent2)
                .build();

            definition.getWorkers().add(
                Worker.builder()
                    .name("pattern-worker")
                    .capabilityName("analysis")
                    .function(new PatternWorkerFunction(model, PatternType.SEQUENCE, false))
                    .build()
            );
        }
    }

    @Test
    void patternWorkerExecutesThroughEnginePipeline() {
        var caseId = runtime.startCase("test-pattern",
            Map.of("trigger", true), "test-tenant");

        await().atMost(10, TimeUnit.SECONDS).until(() -> {
            var ctx = runtime.getCaseContext(caseId);
            return ctx != null && ctx.layer("working").get("report") != null;
        });

        var ctx = runtime.getCaseContext(caseId);
        assertThat(ctx.layer("working").get("report")).isEqualTo("step-2-done");
    }
}
```

- [ ] **Step 3: Create test YAML case definition**

Create `agentic-engine/src/test/resources/test-pattern-case.yaml`:

```yaml
name: test-pattern
namespace: io.casehub.test
version: "1.0"

spec:
  capabilities:
    - name: analysis

  bindings:
    - name: run-analysis
      capability: analysis
      on:
        contextChange: ".trigger == true"
```

- [ ] **Step 4: Run integration test**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl agentic-engine -Dtest=PatternExecutionIntegrationTest -q`
Expected: PASS

- [ ] **Step 5: Run full module test suite**

Run: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl agentic-engine -q`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add agentic-engine/src/test/
git commit -m "test(#886): add PatternExecutionIntegrationTest — full engine pipeline

Verifies: case start → binding fires → PatternWorkerFunctionHandler
dispatches → OrchestratedDriver runs sequence pattern with
ExternalAgents → result merged to case context.

Refs #886"
```

---

## Post-Phase 1

After Phase 1 lands, write separate plans for:
- **Phase 2** (#884): PlanningConstraints, DecompositionContext extension, driver enforcement
- **Phase 3** (#882): ReplanPolicy, DecompositionStrategy.replan(), HtnBuilder integration
- **Phase 4** (#883): PatternExecutionCheckpoint, EventLog storage, recovery protocol

Each plan will be written against the actual module structure from Phase 1,
not the speculative paths in this plan.

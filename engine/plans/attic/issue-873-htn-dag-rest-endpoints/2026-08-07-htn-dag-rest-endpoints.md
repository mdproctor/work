# HTN/DAG REST Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #873 — Engine REST endpoints for HTN/DAG data
**Issue group:** #873

**Goal:** REST endpoints serving HTN decomposition trees, DAG plans,
DAG results, and case plan model snapshots as JSON matching blocks-ui
TypeScript contracts.

**Architecture:** Two model fixes in engine-api (`CompoundTask.id`,
`DecompositionMethod.guardLabel`). Non-generic snapshot types across
engine-api (plan-definition) and engine-common (execution). Two SPIs
(`ExecutionSnapshotStore`, `CasePlanModelSnapshotProvider`) with
in-memory/no-op defaults. Planning module provides live CasePlanModel
snapshots. REST module adds `PlanResource` with 5 endpoints.

**Tech Stack:** Java 21, Quarkus 3.32.2, Jackson, JAX-RS, CDI

## Global Constraints

- Pre-release platform — breaking changes are fine
- `plan-type-module-boundary` protocol (PP-20260727-5267d2): plan-definition
  types in engine-api, execution types in engine-common
- `@DefaultBean` pattern per PP-20260514-engine-spi-noops-defaultbean
- `@RunOnVirtualThread` for all REST endpoints
- No Flyway migrations
- Package: `io.casehub.engine.plan.snapshot` (engine-api),
  `io.casehub.engine.plan.execution` (engine-common)
- IntelliJ MCP for all code navigation and editing

---

### Task 1: Model Fixes — CompoundTask.id and DecompositionMethod.guardLabel

**Files:**
- Modify: `api/src/main/java/io/casehub/engine/plan/TaskNode.java:26`
- Modify: `api/src/main/java/io/casehub/engine/plan/DecompositionMethod.java:20`
- Modify: `api/src/test/java/io/casehub/engine/plan/DagPlanTest.java` (if CompoundTask used)
- Test: `api/src/test/java/io/casehub/engine/plan/TaskNodeTest.java`

**Interfaces:**
- Produces: `CompoundTask<T>(String id, String name, List<DecompositionMethod<T>> methods)` —
  new `id` field as first parameter
- Produces: `DecompositionMethod<T>(Predicate<T> guard, DecompositionStrategy<T> strategy, String guardLabel)` —
  new `guardLabel` field, nullable

- [ ] **Step 1: Write failing test for CompoundTask.id**

```java
// api/src/test/java/io/casehub/engine/plan/TaskNodeTest.java
package io.casehub.engine.plan;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;

class TaskNodeTest {

  @Test
  void compoundTaskCarriesId() {
    var compound = new TaskNode.CompoundTask<String>("ct-1", "analysis", List.of());
    assertThat(compound.id()).isEqualTo("ct-1");
    assertThat(compound.name()).isEqualTo("analysis");
  }

  @Test
  void compoundTaskRejectsNullId() {
    assertThatThrownBy(() -> new TaskNode.CompoundTask<>(null, "name", List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void decompositionMethodCarriesGuardLabel() {
    var method = new DecompositionMethod<String>(s -> true, null, "when input > threshold");
    assertThat(method.guardLabel()).isEqualTo("when input > threshold");
  }

  @Test
  void decompositionMethodAllowsNullGuardLabel() {
    var method = new DecompositionMethod<String>(s -> true, null, null);
    assertThat(method.guardLabel()).isNull();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl api -Dtest=TaskNodeTest -DfailIfNoTests=false -q`
Expected: compilation error — CompoundTask constructor has 2 args, not 3

- [ ] **Step 3: Update CompoundTask record**

Use `ide_replace_member` on `TaskNode.java`, member `CompoundTask`:

```java
record CompoundTask<T>(String id, String name, List<DecompositionMethod<T>> methods) implements TaskNode<T> {
  public CompoundTask {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    methods = List.copyOf(methods);
  }
}
```

- [ ] **Step 4: Update DecompositionMethod record**

Use `ide_replace_member` on `DecompositionMethod.java`, replace the full record:

```java
public record DecompositionMethod<T>(Predicate<T> guard, DecompositionStrategy<T> strategy,
    String guardLabel) {}
```

- [ ] **Step 5: Fix any in-repo compilation errors**

Run: `mvn compile -pl api -q`
Use `ide_diagnostics` to find remaining errors. Fix call sites (likely
only in test files within the api module).

- [ ] **Step 6: Run tests**

Run: `mvn test -pl api -Dtest=TaskNodeTest -q`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add api/src/main/java/io/casehub/engine/plan/TaskNode.java \
       api/src/main/java/io/casehub/engine/plan/DecompositionMethod.java \
       api/src/test/java/io/casehub/engine/plan/TaskNodeTest.java
git commit -m "feat(#873): add id to CompoundTask and guardLabel to DecompositionMethod

Refs #873"
```

---

### Task 2: Plan-Definition Snapshot Types (engine-api)

**Files:**
- Create: `api/src/main/java/io/casehub/engine/plan/snapshot/TaskNodeSnapshot.java`
- Create: `api/src/main/java/io/casehub/engine/plan/snapshot/LeafTaskSnapshot.java`
- Create: `api/src/main/java/io/casehub/engine/plan/snapshot/CompoundTaskSnapshot.java`
- Create: `api/src/main/java/io/casehub/engine/plan/snapshot/DecompositionMethodSnapshot.java`
- Create: `api/src/main/java/io/casehub/engine/plan/snapshot/DecompositionSnapshot.java`
- Create: `api/src/main/java/io/casehub/engine/plan/snapshot/DagNodeSnapshot.java`
- Create: `api/src/main/java/io/casehub/engine/plan/snapshot/DagPlanSnapshot.java`
- Create: `api/src/main/java/io/casehub/engine/plan/snapshot/PlanItemDefinitionSnapshot.java`
- Create: `api/src/main/java/io/casehub/engine/plan/snapshot/PrimitiveItemSnapshot.java`
- Create: `api/src/main/java/io/casehub/engine/plan/snapshot/CompoundItemSnapshot.java`
- Create: `api/src/main/java/io/casehub/engine/plan/snapshot/CompletionSemanticsSnapshot.java`
- Test: `api/src/test/java/io/casehub/engine/plan/snapshot/DagPlanSnapshotTest.java`
- Test: `api/src/test/java/io/casehub/engine/plan/snapshot/DecompositionSnapshotTest.java`
- Test: `api/src/test/java/io/casehub/engine/plan/snapshot/SnapshotJacksonTest.java`

**Interfaces:**
- Consumes: `TaskNode<T>` (Task 1), `DagPlan<T>`, `DagNode<T>`, `JoinType`, `TaskDescriptor`
- Produces: All snapshot types in `io.casehub.engine.plan.snapshot` package

- [ ] **Step 1: Write failing test for DagPlanSnapshot.from()**

```java
// api/src/test/java/io/casehub/engine/plan/snapshot/DagPlanSnapshotTest.java
package io.casehub.engine.plan.snapshot;

import static org.assertj.core.api.Assertions.*;
import io.casehub.engine.plan.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class DagPlanSnapshotTest {

  @Test
  void fromDagPlanExtractsNodeStructure() {
    var nodeA = new DagNode<>("a", "task-a", Set.of(), JoinType.ALL_OF);
    var nodeB = new DagNode<>("b", "task-b", Set.of("a"), JoinType.ANY_OF);
    var plan = DagPlan.fromNodes(List.of(nodeA, nodeB));
    var now = Instant.now();

    var snapshot = DagPlanSnapshot.from(plan, now);

    assertThat(snapshot.nodes()).hasSize(2);
    assertThat(snapshot.timestamp()).isEqualTo(now);

    var snapA = snapshot.nodes().get("a");
    assertThat(snapA.id()).isEqualTo("a");
    assertThat(snapA.joinType()).isEqualTo(JoinType.ALL_OF);
    assertThat(snapA.dependsOn()).isEmpty();

    var snapB = snapshot.nodes().get("b");
    assertThat(snapB.dependsOn()).containsExactly("a");
    assertThat(snapB.joinType()).isEqualTo(JoinType.ANY_OF);
  }

  @Test
  void fromDagPlanExtractsTaskDescriptorFields() {
    var leaf = new TestLeafTask("leaf-1", "Analyse input", "agent-alpha");
    var node = new DagNode<>("n1", leaf, Set.of(), JoinType.ALL_OF);
    var plan = DagPlan.fromNodes(List.of(node));

    var snapshot = DagPlanSnapshot.from(plan, Instant.now());
    var snap = snapshot.nodes().get("n1");

    assertThat(snap.taskId()).isEqualTo("leaf-1");
    assertThat(snap.taskDescription()).isEqualTo("Analyse input");
    assertThat(snap.executorName()).isEqualTo("agent-alpha");
  }
}
```

With a test helper:

```java
// api/src/test/java/io/casehub/engine/plan/snapshot/TestLeafTask.java
package io.casehub.engine.plan.snapshot;

import io.casehub.api.model.*;
import io.casehub.engine.plan.TaskNode;
import java.time.Instant;

record TestLeafTask(String id, String description, String executorName)
    implements TaskNode.LeafTask<TestLeafTask> {

  @Override public ExecutorRef executor() {
    return executorName != null ? ExecutorRef.of(executorName, null) : null;
  }

  @Override public TaskStatus status() { return TaskStatus.PENDING; }

  @Override public Instant createdAt() { return Instant.now(); }

  @Override public TaskSnapshot snapshot() { return null; }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl api -Dtest=DagPlanSnapshotTest -DfailIfNoTests=false -q`
Expected: compilation error — DagPlanSnapshot does not exist

- [ ] **Step 3: Create snapshot types**

Create all files using `ide_create_file` or Write (new files):

**TaskNodeSnapshot.java:**
```java
package io.casehub.engine.plan.snapshot;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = LeafTaskSnapshot.class, name = "leaf"),
    @JsonSubTypes.Type(value = CompoundTaskSnapshot.class, name = "compound")
})
public sealed interface TaskNodeSnapshot permits LeafTaskSnapshot, CompoundTaskSnapshot {}
```

**LeafTaskSnapshot.java:**
```java
package io.casehub.engine.plan.snapshot;

public record LeafTaskSnapshot(String id, String description, String executorName)
    implements TaskNodeSnapshot {}
```

**CompoundTaskSnapshot.java:**
```java
package io.casehub.engine.plan.snapshot;

import java.util.List;

public record CompoundTaskSnapshot(String id, String name,
    List<DecompositionMethodSnapshot> methods) implements TaskNodeSnapshot {}
```

**DecompositionMethodSnapshot.java:**
```java
package io.casehub.engine.plan.snapshot;

import java.util.List;

public record DecompositionMethodSnapshot(String guardLabel, String strategyId,
    List<TaskNodeSnapshot> children) {}
```

**DecompositionSnapshot.java:**
```java
package io.casehub.engine.plan.snapshot;

import io.casehub.engine.plan.DecompositionMethod;
import io.casehub.engine.plan.TaskNode;
import io.casehub.api.model.TaskDescriptor;
import io.casehub.api.model.ExecutorRef;
import java.time.Instant;
import java.util.List;

public record DecompositionSnapshot(TaskNodeSnapshot root, Instant timestamp) {

  public static DecompositionSnapshot from(TaskNode<?> root, Instant timestamp) {
    return new DecompositionSnapshot(toSnapshot(root), timestamp);
  }

  private static TaskNodeSnapshot toSnapshot(TaskNode<?> node) {
    return switch (node) {
      case TaskNode.LeafTask<?> leaf -> {
        String id = (leaf instanceof TaskDescriptor td) ? td.id() : null;
        String desc = (leaf instanceof TaskDescriptor td) ? td.description() : null;
        ExecutorRef exec = (leaf instanceof TaskDescriptor td) ? td.executor() : null;
        yield new LeafTaskSnapshot(id, desc, exec != null ? exec.name() : null);
      }
      case TaskNode.CompoundTask<?> ct -> {
        var methods = ct.methods().stream().map(DecompositionSnapshot::toMethodSnapshot).toList();
        yield new CompoundTaskSnapshot(ct.id(), ct.name(), methods);
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static DecompositionMethodSnapshot toMethodSnapshot(DecompositionMethod<?> method) {
    String strategyId = method.strategy() != null ? method.strategy().id() : null;
    return new DecompositionMethodSnapshot(method.guardLabel(), strategyId, List.of());
  }
}
```

**DagNodeSnapshot.java:**
```java
package io.casehub.engine.plan.snapshot;

import io.casehub.engine.plan.JoinType;
import java.util.Set;

public record DagNodeSnapshot(String id, String taskId, String taskDescription,
    String executorName, Set<String> dependsOn, JoinType joinType) {}
```

**DagPlanSnapshot.java:**
```java
package io.casehub.engine.plan.snapshot;

import io.casehub.api.model.ExecutorRef;
import io.casehub.api.model.TaskDescriptor;
import io.casehub.engine.plan.DagNode;
import io.casehub.engine.plan.DagPlan;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record DagPlanSnapshot(Map<String, DagNodeSnapshot> nodes, Instant timestamp) {

  public static DagPlanSnapshot from(DagPlan<?> plan, Instant timestamp) {
    Map<String, DagNodeSnapshot> snapshotNodes = new LinkedHashMap<>();
    for (var entry : plan.nodes().entrySet()) {
      DagNode<?> node = entry.getValue();
      String taskId = null;
      String taskDesc = null;
      String execName = null;
      Object task = node.task();
      if (task instanceof TaskDescriptor td) {
        taskId = td.id();
        taskDesc = td.description();
        ExecutorRef exec = td.executor();
        execName = exec != null ? exec.name() : null;
      }
      snapshotNodes.put(entry.getKey(), new DagNodeSnapshot(
          node.id(), taskId, taskDesc, execName, node.dependsOn(), node.joinType()));
    }
    return new DagPlanSnapshot(Map.copyOf(snapshotNodes), timestamp);
  }
}
```

**CompletionSemanticsSnapshot.java:**
```java
package io.casehub.engine.plan.snapshot;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CompletionSemanticsSnapshot.AllSnapshot.class, name = "All"),
    @JsonSubTypes.Type(value = CompletionSemanticsSnapshot.MOfNSnapshot.class, name = "MOfN"),
    @JsonSubTypes.Type(value = CompletionSemanticsSnapshot.FirstWinsSnapshot.class, name = "FirstWins")
})
public sealed interface CompletionSemanticsSnapshot {
  record AllSnapshot() implements CompletionSemanticsSnapshot {}
  record MOfNSnapshot(int m) implements CompletionSemanticsSnapshot {}
  record FirstWinsSnapshot() implements CompletionSemanticsSnapshot {}
}
```

**PlanItemDefinitionSnapshot.java:**
```java
package io.casehub.engine.plan.snapshot;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PrimitiveItemSnapshot.class, name = "primitive"),
    @JsonSubTypes.Type(value = CompoundItemSnapshot.class, name = "compound")
})
public sealed interface PlanItemDefinitionSnapshot permits PrimitiveItemSnapshot, CompoundItemSnapshot {}
```

**PrimitiveItemSnapshot.java:**
```java
package io.casehub.engine.plan.snapshot;

public record PrimitiveItemSnapshot(String id, String name, String executorName,
    String executorDescription, String entryCondition) implements PlanItemDefinitionSnapshot {}
```

**CompoundItemSnapshot.java:**
```java
package io.casehub.engine.plan.snapshot;

import java.util.List;
import java.util.Map;

public record CompoundItemSnapshot(String id, String name,
    List<PlanItemDefinitionSnapshot> children, String planningStrategy,
    CompletionSemanticsSnapshot completion, String dispatchMode,
    String entryCondition, String exitCondition, boolean repeatable,
    Map<String, String> scopedBindings) implements PlanItemDefinitionSnapshot {}
```

- [ ] **Step 4: Write Jackson serialization test**

```java
// api/src/test/java/io/casehub/engine/plan/snapshot/SnapshotJacksonTest.java
package io.casehub.engine.plan.snapshot;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotJacksonTest {

  private final ObjectMapper mapper = JsonMapper.builder()
      .addModule(new JavaTimeModule()).build();

  @Test
  void leafTaskSnapshotSerializesWithKindDiscriminator() throws Exception {
    TaskNodeSnapshot leaf = new LeafTaskSnapshot("id-1", "desc", "agent-1");
    String json = mapper.writeValueAsString(leaf);
    assertThat(json).contains("\"kind\":\"leaf\"");
    assertThat(json).contains("\"id\":\"id-1\"");

    TaskNodeSnapshot deserialized = mapper.readValue(json, TaskNodeSnapshot.class);
    assertThat(deserialized).isInstanceOf(LeafTaskSnapshot.class);
  }

  @Test
  void compoundTaskSnapshotSerializesWithKindDiscriminator() throws Exception {
    TaskNodeSnapshot compound = new CompoundTaskSnapshot("ct-1", "analysis", List.of());
    String json = mapper.writeValueAsString(compound);
    assertThat(json).contains("\"kind\":\"compound\"");

    TaskNodeSnapshot deserialized = mapper.readValue(json, TaskNodeSnapshot.class);
    assertThat(deserialized).isInstanceOf(CompoundTaskSnapshot.class);
  }

  @Test
  void completionSemanticsSnapshotSerializesCorrectly() throws Exception {
    CompletionSemanticsSnapshot all = new CompletionSemanticsSnapshot.AllSnapshot();
    String json = mapper.writeValueAsString(all);
    assertThat(json).contains("\"kind\":\"All\"");

    CompletionSemanticsSnapshot mOfN = new CompletionSemanticsSnapshot.MOfNSnapshot(3);
    json = mapper.writeValueAsString(mOfN);
    assertThat(json).contains("\"kind\":\"MOfN\"");
    assertThat(json).contains("\"m\":3");
  }

  @Test
  void planItemDefinitionSnapshotSerializesWithKind() throws Exception {
    PlanItemDefinitionSnapshot prim = new PrimitiveItemSnapshot(
        "p-1", "worker-a", "exec-1", "desc", ".input != null");
    String json = mapper.writeValueAsString(prim);
    assertThat(json).contains("\"kind\":\"primitive\"");

    PlanItemDefinitionSnapshot deserialized = mapper.readValue(json,
        PlanItemDefinitionSnapshot.class);
    assertThat(deserialized).isInstanceOf(PrimitiveItemSnapshot.class);
  }
}
```

- [ ] **Step 5: Run all tests**

Run: `mvn test -pl api -Dtest="DagPlanSnapshotTest,SnapshotJacksonTest,DecompositionSnapshotTest" -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add api/src/main/java/io/casehub/engine/plan/snapshot/ \
       api/src/test/java/io/casehub/engine/plan/snapshot/
git commit -m "feat(#873): plan-definition snapshot types with Jackson discriminators

TaskNodeSnapshot (leaf/compound), DecompositionSnapshot,
DagPlanSnapshot, PlanItemDefinitionSnapshot, CompletionSemanticsSnapshot.
Static from() factories extract TaskDescriptor fields from generics.

Refs #873"
```

---

### Task 3: Execution + Plan Model Snapshot Types (engine-common)

**Files:**
- Create: `common/src/main/java/io/casehub/engine/plan/execution/NodeStateSnapshot.java`
- Create: `common/src/main/java/io/casehub/engine/plan/execution/DagResultSnapshot.java`
- Create: `common/src/main/java/io/casehub/engine/plan/execution/AgendaItemSnapshot.java`
- Create: `common/src/main/java/io/casehub/engine/plan/execution/SubCaseSnapshotRecord.java`
- Create: `common/src/main/java/io/casehub/engine/plan/execution/CompoundStatusSnapshot.java`
- Create: `common/src/main/java/io/casehub/engine/plan/execution/CasePlanModelSnapshot.java`
- Test: `common/src/test/java/io/casehub/engine/plan/execution/DagResultSnapshotTest.java`
- Test: `common/src/test/java/io/casehub/engine/plan/execution/NodeStateSnapshotTest.java`

**Interfaces:**
- Consumes: `NodeState<R>`, `DagResult<R>` (engine-common),
  `CompletionSemanticsSnapshot` (Task 2)
- Produces: All execution snapshot types in `io.casehub.engine.plan.execution`

- [ ] **Step 1: Write failing test for NodeStateSnapshot.from()**

```java
// common/src/test/java/io/casehub/engine/plan/execution/NodeStateSnapshotTest.java
package io.casehub.engine.plan.execution;

import static org.assertj.core.api.Assertions.*;
import io.casehub.engine.plan.NodeState;
import org.junit.jupiter.api.Test;

class NodeStateSnapshotTest {

  @Test
  void fromPendingState() {
    var snapshot = NodeStateSnapshot.from(new NodeState.Pending<>());
    assertThat(snapshot.kind()).isEqualTo("Pending");
    assertThat(snapshot.reason()).isNull();
  }

  @Test
  void fromFailedStateWithReason() {
    var snapshot = NodeStateSnapshot.from(
        new NodeState.Failed<>("timeout", new RuntimeException("boom")));
    assertThat(snapshot.kind()).isEqualTo("Failed");
    assertThat(snapshot.reason()).isEqualTo("timeout");
  }

  @Test
  void fromSkippedState() {
    var snapshot = NodeStateSnapshot.from(new NodeState.Skipped<>("dependency failed"));
    assertThat(snapshot.kind()).isEqualTo("Skipped");
    assertThat(snapshot.reason()).isEqualTo("dependency failed");
  }
}
```

- [ ] **Step 2: Write failing test for DagResultSnapshot.from()**

```java
// common/src/test/java/io/casehub/engine/plan/execution/DagResultSnapshotTest.java
package io.casehub.engine.plan.execution;

import static org.assertj.core.api.Assertions.*;
import io.casehub.engine.plan.DagResult;
import io.casehub.engine.plan.NodeState;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DagResultSnapshotTest {

  @Test
  void fromDagResultMapsStatesAndResults() {
    var result = new DagResult<>(
        Map.of("a", new NodeState.Completed<>("result-a"),
               "b", new NodeState.Failed<>("error", null)),
        Map.of("a", "result-a"),
        false,
        Duration.ofMillis(1500));
    var now = Instant.now();

    var snapshot = DagResultSnapshot.from(result, now);

    assertThat(snapshot.allSucceeded()).isFalse();
    assertThat(snapshot.elapsed()).isEqualTo(Duration.ofMillis(1500));
    assertThat(snapshot.timestamp()).isEqualTo(now);
    assertThat(snapshot.nodeStates()).hasSize(2);
    assertThat(snapshot.nodeStates().get("a").kind()).isEqualTo("Completed");
    assertThat(snapshot.nodeStates().get("b").kind()).isEqualTo("Failed");
    assertThat(snapshot.completedResults()).containsEntry("a", "result-a");
  }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn test -pl common -Dtest="NodeStateSnapshotTest,DagResultSnapshotTest" -DfailIfNoTests=false -q`
Expected: compilation error — snapshot types don't exist

- [ ] **Step 4: Create snapshot types**

**NodeStateSnapshot.java:**
```java
package io.casehub.engine.plan.execution;

import io.casehub.engine.plan.NodeState;

public record NodeStateSnapshot(String kind, String reason) {

  public static NodeStateSnapshot from(NodeState<?> state) {
    return switch (state) {
      case NodeState.Pending<?> p -> new NodeStateSnapshot("Pending", null);
      case NodeState.Dispatched<?> d -> new NodeStateSnapshot("Dispatched", null);
      case NodeState.Completed<?> c -> new NodeStateSnapshot("Completed", null);
      case NodeState.Failed<?> f -> new NodeStateSnapshot("Failed", f.reason());
      case NodeState.Skipped<?> s -> new NodeStateSnapshot("Skipped", s.reason());
      case NodeState.Cancelled<?> x -> new NodeStateSnapshot("Cancelled", null);
    };
  }
}
```

**DagResultSnapshot.java:**
```java
package io.casehub.engine.plan.execution;

import io.casehub.engine.plan.DagResult;
import io.casehub.engine.plan.NodeState;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record DagResultSnapshot(
    Map<String, NodeStateSnapshot> nodeStates,
    Map<String, Object> completedResults,
    boolean allSucceeded,
    Duration elapsed,
    Instant timestamp) {

  @SuppressWarnings("unchecked")
  public static DagResultSnapshot from(DagResult<?> result, Instant timestamp) {
    Map<String, NodeStateSnapshot> states = new LinkedHashMap<>();
    for (var entry : result.nodeStates().entrySet()) {
      states.put(entry.getKey(), NodeStateSnapshot.from(entry.getValue()));
    }
    Map<String, Object> completed = new LinkedHashMap<>();
    for (var entry : result.completedResults().entrySet()) {
      completed.put(entry.getKey(), entry.getValue());
    }
    return new DagResultSnapshot(
        Map.copyOf(states), Map.copyOf(completed),
        result.allSucceeded(), result.elapsed(), timestamp);
  }
}
```

**AgendaItemSnapshot.java:**
```java
package io.casehub.engine.plan.execution;

public record AgendaItemSnapshot(String planItemId, String bindingName,
    String status, String description) {}
```

**SubCaseSnapshotRecord.java:**
```java
package io.casehub.engine.plan.execution;

public record SubCaseSnapshotRecord(String caseDefinition, String namespace,
    String status) {}
```

**CompoundStatusSnapshot.java:**
```java
package io.casehub.engine.plan.execution;

import io.casehub.engine.plan.snapshot.CompletionSemanticsSnapshot;

public record CompoundStatusSnapshot(String id, String name, String status,
    int childCount, int completedCount,
    CompletionSemanticsSnapshot completion) {}
```

**CasePlanModelSnapshot.java:**
```java
package io.casehub.engine.plan.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CasePlanModelSnapshot(
    UUID caseId,
    List<AgendaItemSnapshot> agenda,
    String focus,
    String focusRationale,
    Map<String, Object> resourceBudget,
    List<SubCaseSnapshotRecord> subCases,
    List<CompoundStatusSnapshot> compounds,
    Instant timestamp) {}
```

- [ ] **Step 5: Run tests**

Run: `mvn test -pl common -Dtest="NodeStateSnapshotTest,DagResultSnapshotTest" -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/io/casehub/engine/plan/execution/ \
       common/src/test/java/io/casehub/engine/plan/execution/
git commit -m "feat(#873): execution and plan model snapshot types

NodeStateSnapshot, DagResultSnapshot with from() factories.
AgendaItemSnapshot, SubCaseSnapshotRecord, CompoundStatusSnapshot,
CasePlanModelSnapshot read models for REST serialization.

Refs #873"
```

---

### Task 4: SPIs + In-Memory Store + SnapshotCapturingDagEventListener (engine-common)

**Files:**
- Create: `common/src/main/java/io/casehub/engine/plan/execution/ExecutionSnapshotStore.java`
- Create: `common/src/main/java/io/casehub/engine/plan/execution/CasePlanModelSnapshotProvider.java`
- Create: `common/src/main/java/io/casehub/engine/plan/execution/InMemoryExecutionSnapshotStore.java`
- Create: `common/src/main/java/io/casehub/engine/plan/execution/NoOpCasePlanModelSnapshotProvider.java`
- Create: `common/src/main/java/io/casehub/engine/plan/execution/SnapshotCapturingDagEventListener.java`
- Test: `common/src/test/java/io/casehub/engine/plan/execution/InMemoryExecutionSnapshotStoreTest.java`
- Test: `common/src/test/java/io/casehub/engine/plan/execution/SnapshotCapturingDagEventListenerTest.java`

**Interfaces:**
- Consumes: Snapshot types from Tasks 2-3, `DagPlan`, `DagResult`, `DagEventListener`
- Produces: `ExecutionSnapshotStore`, `CasePlanModelSnapshotProvider` SPIs,
  `InMemoryExecutionSnapshotStore`, `SnapshotCapturingDagEventListener`

- [ ] **Step 1: Write failing test for InMemoryExecutionSnapshotStore**

```java
// common/src/test/java/io/casehub/engine/plan/execution/InMemoryExecutionSnapshotStoreTest.java
package io.casehub.engine.plan.execution;

import static org.assertj.core.api.Assertions.*;
import io.casehub.engine.plan.snapshot.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class InMemoryExecutionSnapshotStoreTest {

  private final InMemoryExecutionSnapshotStore store = new InMemoryExecutionSnapshotStore();

  @Test
  void storeAndRetrieveDecomposition() {
    UUID caseId = UUID.randomUUID();
    var snapshot = new DecompositionSnapshot(
        new LeafTaskSnapshot("l1", "desc", null), Instant.now());

    store.storeDecomposition(caseId, snapshot);

    assertThat(store.getDecomposition(caseId, "tenant-1")).contains(snapshot);
  }

  @Test
  void storeAndRetrieveDagPlan() {
    UUID caseId = UUID.randomUUID();
    var snapshot = new DagPlanSnapshot(Map.of(), Instant.now());

    store.storeDagPlan(caseId, snapshot);

    assertThat(store.getDagPlan(caseId, "tenant-1")).contains(snapshot);
  }

  @Test
  void storeAndRetrieveDagResult() {
    UUID caseId = UUID.randomUUID();
    var snapshot = new DagResultSnapshot(
        Map.of(), Map.of(), true, Duration.ofSeconds(1), Instant.now());

    store.storeDagResult(caseId, snapshot);

    assertThat(store.getDagResult(caseId, "tenant-1")).contains(snapshot);
  }

  @Test
  void evictRemovesAllSnapshots() {
    UUID caseId = UUID.randomUUID();
    store.storeDecomposition(caseId, new DecompositionSnapshot(
        new LeafTaskSnapshot("l1", null, null), Instant.now()));
    store.storeDagPlan(caseId, new DagPlanSnapshot(Map.of(), Instant.now()));

    store.evict(caseId);

    assertThat(store.getDecomposition(caseId, "t")).isEmpty();
    assertThat(store.getDagPlan(caseId, "t")).isEmpty();
  }

  @Test
  void getReturnsEmptyForUnknownCase() {
    assertThat(store.getDecomposition(UUID.randomUUID(), "t")).isEmpty();
    assertThat(store.getDagPlan(UUID.randomUUID(), "t")).isEmpty();
    assertThat(store.getDagResult(UUID.randomUUID(), "t")).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl common -Dtest=InMemoryExecutionSnapshotStoreTest -DfailIfNoTests=false -q`
Expected: compilation error

- [ ] **Step 3: Create SPI interfaces and implementations**

**ExecutionSnapshotStore.java:**
```java
package io.casehub.engine.plan.execution;

import io.casehub.engine.plan.snapshot.DecompositionSnapshot;
import io.casehub.engine.plan.snapshot.DagPlanSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionSnapshotStore {
  void storeDecomposition(UUID caseId, DecompositionSnapshot snapshot);
  Optional<DecompositionSnapshot> getDecomposition(UUID caseId, String tenancyId);

  void storeDagPlan(UUID caseId, DagPlanSnapshot snapshot);
  Optional<DagPlanSnapshot> getDagPlan(UUID caseId, String tenancyId);

  void storeDagResult(UUID caseId, DagResultSnapshot snapshot);
  Optional<DagResultSnapshot> getDagResult(UUID caseId, String tenancyId);

  void evict(UUID caseId);
}
```

**CasePlanModelSnapshotProvider.java:**
```java
package io.casehub.engine.plan.execution;

import io.casehub.engine.plan.snapshot.PlanItemDefinitionSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CasePlanModelSnapshotProvider {
  Optional<CasePlanModelSnapshot> getSnapshot(UUID caseId, String tenancyId);
  List<PlanItemDefinitionSnapshot> getDefinitions(UUID caseId, String tenancyId);
}
```

**InMemoryExecutionSnapshotStore.java:**
```java
package io.casehub.engine.plan.execution;

import io.casehub.engine.plan.snapshot.DecompositionSnapshot;
import io.casehub.engine.plan.snapshot.DagPlanSnapshot;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@DefaultBean
@ApplicationScoped
public class InMemoryExecutionSnapshotStore implements ExecutionSnapshotStore {

  private static final class CaseSnapshots {
    final AtomicReference<DecompositionSnapshot> decomposition = new AtomicReference<>();
    final AtomicReference<DagPlanSnapshot> dagPlan = new AtomicReference<>();
    final AtomicReference<DagResultSnapshot> dagResult = new AtomicReference<>();
  }

  private final ConcurrentHashMap<UUID, CaseSnapshots> entries = new ConcurrentHashMap<>();

  @Override
  public void storeDecomposition(UUID caseId, DecompositionSnapshot snapshot) {
    entries.computeIfAbsent(caseId, k -> new CaseSnapshots()).decomposition.set(snapshot);
  }

  @Override
  public Optional<DecompositionSnapshot> getDecomposition(UUID caseId, String tenancyId) {
    CaseSnapshots e = entries.get(caseId);
    return e != null ? Optional.ofNullable(e.decomposition.get()) : Optional.empty();
  }

  @Override
  public void storeDagPlan(UUID caseId, DagPlanSnapshot snapshot) {
    entries.computeIfAbsent(caseId, k -> new CaseSnapshots()).dagPlan.set(snapshot);
  }

  @Override
  public Optional<DagPlanSnapshot> getDagPlan(UUID caseId, String tenancyId) {
    CaseSnapshots e = entries.get(caseId);
    return e != null ? Optional.ofNullable(e.dagPlan.get()) : Optional.empty();
  }

  @Override
  public void storeDagResult(UUID caseId, DagResultSnapshot snapshot) {
    entries.computeIfAbsent(caseId, k -> new CaseSnapshots()).dagResult.set(snapshot);
  }

  @Override
  public Optional<DagResultSnapshot> getDagResult(UUID caseId, String tenancyId) {
    CaseSnapshots e = entries.get(caseId);
    return e != null ? Optional.ofNullable(e.dagResult.get()) : Optional.empty();
  }

  @Override
  public void evict(UUID caseId) {
    entries.remove(caseId);
  }
}
```

**NoOpCasePlanModelSnapshotProvider.java:**
```java
package io.casehub.engine.plan.execution;

import io.casehub.engine.plan.snapshot.PlanItemDefinitionSnapshot;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@DefaultBean
@ApplicationScoped
public class NoOpCasePlanModelSnapshotProvider implements CasePlanModelSnapshotProvider {

  @Override
  public Optional<CasePlanModelSnapshot> getSnapshot(UUID caseId, String tenancyId) {
    return Optional.empty();
  }

  @Override
  public List<PlanItemDefinitionSnapshot> getDefinitions(UUID caseId, String tenancyId) {
    return List.of();
  }
}
```

**SnapshotCapturingDagEventListener.java:**
```java
package io.casehub.engine.plan.execution;

import io.casehub.engine.plan.DagEventListener;
import io.casehub.engine.plan.DagPlan;
import io.casehub.engine.plan.DagResult;
import io.casehub.engine.plan.snapshot.DagPlanSnapshot;
import java.time.Instant;
import java.util.UUID;

public class SnapshotCapturingDagEventListener<T, R> implements DagEventListener<T, R> {

  private final UUID caseId;
  private final ExecutionSnapshotStore store;

  public SnapshotCapturingDagEventListener(UUID caseId, ExecutionSnapshotStore store,
      DagPlan<T> plan) {
    this.caseId = caseId;
    this.store = store;
    store.storeDagPlan(caseId, DagPlanSnapshot.from(plan, Instant.now()));
  }

  @Override
  public void onExecutionComplete(DagResult<R> result) {
    store.storeDagResult(caseId, DagResultSnapshot.from(result, Instant.now()));
  }
}
```

- [ ] **Step 4: Write SnapshotCapturingDagEventListener test**

```java
// common/src/test/java/io/casehub/engine/plan/execution/SnapshotCapturingDagEventListenerTest.java
package io.casehub.engine.plan.execution;

import static org.assertj.core.api.Assertions.*;
import io.casehub.engine.plan.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class SnapshotCapturingDagEventListenerTest {

  @Test
  void constructorStoresDagPlanSnapshot() {
    var store = new InMemoryExecutionSnapshotStore();
    UUID caseId = UUID.randomUUID();
    var plan = DagPlan.singleton("node-0", "task");

    new SnapshotCapturingDagEventListener<>(caseId, store, plan);

    assertThat(store.getDagPlan(caseId, "t")).isPresent();
    assertThat(store.getDagPlan(caseId, "t").get().nodes()).hasSize(1);
  }

  @Test
  void onExecutionCompleteStoresDagResultSnapshot() {
    var store = new InMemoryExecutionSnapshotStore();
    UUID caseId = UUID.randomUUID();
    var plan = DagPlan.singleton("node-0", "task");
    var listener = new SnapshotCapturingDagEventListener<String, String>(caseId, store, plan);

    var result = new DagResult<>(
        Map.of("node-0", new NodeState.Completed<>("done")),
        Map.of("node-0", "done"), true, Duration.ofMillis(100));

    listener.onExecutionComplete(result);

    assertThat(store.getDagResult(caseId, "t")).isPresent();
    assertThat(store.getDagResult(caseId, "t").get().allSucceeded()).isTrue();
  }
}
```

- [ ] **Step 5: Run all tests**

Run: `mvn test -pl common -Dtest="InMemoryExecutionSnapshotStoreTest,SnapshotCapturingDagEventListenerTest" -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add common/src/main/java/io/casehub/engine/plan/execution/ \
       common/src/test/java/io/casehub/engine/plan/execution/
git commit -m "feat(#873): ExecutionSnapshotStore and CasePlanModelSnapshotProvider SPIs

InMemoryExecutionSnapshotStore with AtomicReference per field.
NoOpCasePlanModelSnapshotProvider @DefaultBean.
SnapshotCapturingDagEventListener for write-time DAG capture.

Refs #873"
```

---

### Task 5: PlanningCasePlanModelSnapshotProvider (planning module)

**Files:**
- Create: `planning/src/main/java/io/casehub/engine/planning/snapshot/PlanningCasePlanModelSnapshotProvider.java`
- Test: `planning/src/test/java/io/casehub/engine/planning/snapshot/PlanningCasePlanModelSnapshotProviderTest.java`

**Interfaces:**
- Consumes: `CasePlanModelSnapshotProvider` (Task 4), `BlackboardRegistry`,
  `CasePlanModel`, `PlanItemDefinition`, `CompletionSemantics`
- Produces: `PlanningCasePlanModelSnapshotProvider` — real implementation

- [ ] **Step 1: Write failing test**

```java
// planning/src/test/java/io/casehub/engine/planning/snapshot/PlanningCasePlanModelSnapshotProviderTest.java
package io.casehub.engine.planning.snapshot;

import static org.assertj.core.api.Assertions.*;
import io.casehub.api.model.ExecutorRef;
import io.casehub.api.model.TaskStatus;
import io.casehub.engine.plan.execution.CasePlanModelSnapshot;
import io.casehub.engine.plan.snapshot.*;
import io.casehub.engine.planning.plan.*;
import io.casehub.engine.planning.registry.BlackboardRegistry;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanningCasePlanModelSnapshotProviderTest {

  private BlackboardRegistry registry;
  private PlanningCasePlanModelSnapshotProvider provider;

  @BeforeEach
  void setUp() {
    registry = new BlackboardRegistry();
    provider = new PlanningCasePlanModelSnapshotProvider(registry);
  }

  @Test
  void returnsEmptyWhenNoPlanModel() {
    assertThat(provider.getSnapshot(UUID.randomUUID(), "t")).isEmpty();
  }

  @Test
  void returnsSnapshotWithAgendaItems() {
    UUID caseId = UUID.randomUUID();
    CasePlanModel plan = registry.getOrCreate(caseId, "tenant-1");
    plan.addPlanItem(PlanItem.create("binding-a",
        ExecutorRef.of("worker-1", null), 10));

    var snapshot = provider.getSnapshot(caseId, "tenant-1");

    assertThat(snapshot).isPresent();
    assertThat(snapshot.get().caseId()).isEqualTo(caseId);
    assertThat(snapshot.get().agenda()).hasSize(1);
    assertThat(snapshot.get().agenda().get(0).bindingName()).isEqualTo("binding-a");
  }

  @Test
  void returnsDefinitionsForCompound() {
    UUID caseId = UUID.randomUUID();
    CasePlanModel plan = registry.getOrCreate(caseId, "tenant-1");

    var compound = PlanItemDefinition.Compound.builder("phase-1")
        .id("compound-1")
        .child(new PlanItemDefinition.Primitive("prim-1", "step-a",
            ExecutorRef.of("exec-1", "desc"), null))
        .build();
    plan.registerDefinition(compound);

    var defs = provider.getDefinitions(caseId, "tenant-1");

    assertThat(defs).hasSize(1);
    assertThat(defs.get(0)).isInstanceOf(CompoundItemSnapshot.class);
    var cs = (CompoundItemSnapshot) defs.get(0);
    assertThat(cs.children()).hasSize(1);
    assertThat(cs.children().get(0)).isInstanceOf(PrimitiveItemSnapshot.class);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl planning -Dtest=PlanningCasePlanModelSnapshotProviderTest -DfailIfNoTests=false -q`
Expected: compilation error — class doesn't exist

- [ ] **Step 3: Implement PlanningCasePlanModelSnapshotProvider**

```java
package io.casehub.engine.planning.snapshot;

import io.casehub.api.model.TaskStatus;
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import io.casehub.engine.plan.execution.*;
import io.casehub.engine.plan.snapshot.*;
import io.casehub.engine.planning.plan.*;
import io.casehub.engine.planning.registry.BlackboardRegistry;
import io.casehub.platform.api.expression.ExpressionEvaluator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class PlanningCasePlanModelSnapshotProvider implements CasePlanModelSnapshotProvider {

  private final BlackboardRegistry registry;

  @Inject
  public PlanningCasePlanModelSnapshotProvider(BlackboardRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Optional<CasePlanModelSnapshot> getSnapshot(UUID caseId, String tenancyId) {
    return registry.get(caseId, tenancyId).map(plan -> {
      var agenda = plan.getAgenda().stream()
          .map(item -> new AgendaItemSnapshot(
              item.id(), item.getBindingName(),
              item.getStatus().name(), item.getDescription()))
          .toList();

      var subCases = plan.getSubCases().stream()
          .map(sc -> new SubCaseSnapshotRecord(
              sc.getIdentity().name(),
              sc.getIdentity().namespace(),
              null))
          .toList();

      var compounds = plan.getAllCompounds().stream()
          .map(c -> {
            TaskStatus status = plan.getDefinitionStatus(c.id());
            Set<String> children = plan.getChildrenOf(c.id());
            long completed = children.stream()
                .map(plan::getDefinitionStatus)
                .filter(s -> s == TaskStatus.COMPLETED)
                .count();
            return new CompoundStatusSnapshot(
                c.id(), c.name(), status != null ? status.name() : "PENDING",
                children.size(), (int) completed,
                toCompletionSnapshot(c.completion()));
          })
          .toList();

      return new CasePlanModelSnapshot(
          caseId, agenda,
          plan.getFocus().orElse(null),
          plan.getFocusRationale().orElse(null),
          plan.getResourceBudget(),
          subCases, compounds, Instant.now());
    });
  }

  @Override
  public List<PlanItemDefinitionSnapshot> getDefinitions(UUID caseId, String tenancyId) {
    return registry.get(caseId, tenancyId)
        .map(plan -> plan.getAllCompounds().stream()
            .map(c -> (PlanItemDefinitionSnapshot) toDefinitionSnapshot(c))
            .toList())
        .orElse(List.of());
  }

  private PlanItemDefinitionSnapshot toDefinitionSnapshot(PlanItemDefinition def) {
    return switch (def) {
      case PlanItemDefinition.Primitive p -> new PrimitiveItemSnapshot(
          p.id(), p.name(),
          p.executor() != null ? p.executor().name() : null,
          p.executor() != null ? p.executor().description() : null,
          expressionToString(p.entryCondition()));
      case PlanItemDefinition.Compound c -> new CompoundItemSnapshot(
          c.id(), c.name(),
          c.children().stream().map(this::toDefinitionSnapshot).toList(),
          c.planningStrategy(),
          toCompletionSnapshot(c.completion()),
          c.dispatchMode().name(),
          expressionToString(c.entryCondition()),
          expressionToString(c.exitCondition()),
          c.repeatable(),
          toScopedBindingsMap(c.scopedBindings()));
    };
  }

  private static CompletionSemanticsSnapshot toCompletionSnapshot(CompletionSemantics cs) {
    return switch (cs) {
      case CompletionSemantics.All a -> new CompletionSemanticsSnapshot.AllSnapshot();
      case CompletionSemantics.MOfN m -> new CompletionSemanticsSnapshot.MOfNSnapshot(m.m());
      case CompletionSemantics.FirstWins f -> new CompletionSemanticsSnapshot.FirstWinsSnapshot();
    };
  }

  private static String expressionToString(ExpressionEvaluator eval) {
    if (eval == null) return null;
    if (eval instanceof JQExpressionEvaluator jq) return jq.expression();
    return "<lambda>";
  }

  private static Map<String, String> toScopedBindingsMap(
      Map<String, io.casehub.api.model.Participation> bindings) {
    if (bindings == null || bindings.isEmpty()) return Map.of();
    Map<String, String> result = new LinkedHashMap<>();
    bindings.forEach((k, v) -> result.put(k, v.name()));
    return Map.copyOf(result);
  }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn test -pl planning -Dtest=PlanningCasePlanModelSnapshotProviderTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add planning/src/main/java/io/casehub/engine/planning/snapshot/ \
       planning/src/test/java/io/casehub/engine/planning/snapshot/
git commit -m "feat(#873): PlanningCasePlanModelSnapshotProvider from BlackboardRegistry

Maps live CasePlanModel state to CasePlanModelSnapshot and
PlanItemDefinitionSnapshot. Serializes ExpressionEvaluator conditions,
CompletionSemantics, and scoped bindings.

Refs #873"
```

---

### Task 6: PlanResource REST Endpoints

**Files:**
- Create: `rest/src/main/java/io/casehub/engine/rest/PlanResource.java`
- Test: `rest/src/test/java/io/casehub/engine/rest/PlanResourceTest.java`

**Interfaces:**
- Consumes: `CaseService.requireCaseAccess()`, `CasePlanModelSnapshotProvider`,
  `ExecutionSnapshotStore`, `CurrentPrincipal`
- Produces: 5 GET endpoints under `/api/v1/cases/{caseId}/plan/`

- [ ] **Step 1: Write failing REST test**

```java
// rest/src/test/java/io/casehub/engine/rest/PlanResourceTest.java
package io.casehub.engine.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import io.casehub.engine.plan.execution.*;
import io.casehub.engine.plan.snapshot.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PlanResourceTest {

  @Inject ExecutionSnapshotStore snapshotStore;

  private UUID caseId;

  @BeforeEach
  void setUp() {
    caseId = UUID.randomUUID();
  }

  @Test
  void getDagPlanReturns404WhenNoSnapshot() {
    given()
        .when().get("/api/v1/cases/" + caseId + "/plan/dag")
        .then()
        .statusCode(404);
  }

  @Test
  void getDagPlanReturnsSnapshotWhenPresent() {
    var node = new DagNodeSnapshot("n1", "t1", "desc", "exec",
        Set.of(), io.casehub.engine.plan.JoinType.ALL_OF);
    var plan = new DagPlanSnapshot(Map.of("n1", node), Instant.now());
    snapshotStore.storeDagPlan(caseId, plan);

    given()
        .when().get("/api/v1/cases/" + caseId + "/plan/dag")
        .then()
        .statusCode(200)
        .body("nodes.n1.id", equalTo("n1"))
        .body("nodes.n1.joinType", equalTo("ALL_OF"));
  }

  @Test
  void getDagResultReturns404WhenNoSnapshot() {
    given()
        .when().get("/api/v1/cases/" + caseId + "/plan/dag/result")
        .then()
        .statusCode(404);
  }

  @Test
  void getDecompositionReturns404WhenNoSnapshot() {
    given()
        .when().get("/api/v1/cases/" + caseId + "/plan/decomposition")
        .then()
        .statusCode(404);
  }

  @Test
  void getDecompositionReturnsWithKindDiscriminator() {
    var snapshot = new DecompositionSnapshot(
        new LeafTaskSnapshot("l1", "desc", "exec"), Instant.now());
    snapshotStore.storeDecomposition(caseId, snapshot);

    given()
        .when().get("/api/v1/cases/" + caseId + "/plan/decomposition")
        .then()
        .statusCode(200)
        .body("root.kind", equalTo("leaf"))
        .body("root.id", equalTo("l1"));
  }

  @Test
  void getPlanModelReturns404WhenNoProvider() {
    given()
        .when().get("/api/v1/cases/" + caseId + "/plan/model")
        .then()
        .statusCode(404);
  }

  @Test
  void getDefinitionsReturnsEmptyList() {
    given()
        .when().get("/api/v1/cases/" + caseId + "/plan/definitions")
        .then()
        .statusCode(200)
        .body("$", hasSize(0));
  }
}
```

Note: The REST test may need test-specific adjustments for ACL/tenancy
mocking depending on the test `application.properties` setup. Follow the
existing patterns in `CaseInstanceResourceTest` for `CurrentPrincipal`
and `AccessControlProvider` test configuration.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl rest -Dtest=PlanResourceTest -DfailIfNoTests=false -q`
Expected: compilation error — PlanResource doesn't exist

- [ ] **Step 3: Implement PlanResource**

```java
package io.casehub.engine.rest;

import io.casehub.engine.plan.execution.*;
import io.casehub.engine.plan.snapshot.*;
import io.casehub.engine.rest.dto.ProblemDetail;
import io.casehub.engine.rest.exception.EntityNotFoundException;
import io.casehub.engine.rest.service.CaseService;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/cases/{caseId}/plan")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Plan Snapshots",
    description = "HTN decomposition, DAG execution, and plan model snapshots")
public class PlanResource {

  @Inject CaseService caseService;
  @Inject CasePlanModelSnapshotProvider planModelProvider;
  @Inject ExecutionSnapshotStore snapshotStore;
  @Inject CurrentPrincipal currentPrincipal;

  @GET
  @Path("/model")
  @RunOnVirtualThread
  @Operation(summary = "Get live case plan model snapshot")
  @APIResponse(responseCode = "200", description = "Plan model snapshot")
  @APIResponse(responseCode = "404", description = "No plan model for this case",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public CasePlanModelSnapshot getPlanModel(@PathParam("caseId") UUID caseId) {
    caseService.requireCaseAccess(caseId, AclAction.READ);
    return planModelProvider.getSnapshot(caseId, currentPrincipal.tenancyId())
        .orElseThrow(() -> new EntityNotFoundException(
            "No plan model for case: " + caseId));
  }

  @GET
  @Path("/definitions")
  @RunOnVirtualThread
  @Operation(summary = "Get plan item definition hierarchy")
  @APIResponse(responseCode = "200", description = "Plan item definitions")
  public List<PlanItemDefinitionSnapshot> getDefinitions(
      @PathParam("caseId") UUID caseId) {
    caseService.requireCaseAccess(caseId, AclAction.READ);
    return planModelProvider.getDefinitions(caseId, currentPrincipal.tenancyId());
  }

  @GET
  @Path("/decomposition")
  @RunOnVirtualThread
  @Operation(summary = "Get HTN decomposition tree snapshot")
  @APIResponse(responseCode = "200", description = "Decomposition snapshot")
  @APIResponse(responseCode = "404", description = "No decomposition captured",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public DecompositionSnapshot getDecomposition(@PathParam("caseId") UUID caseId) {
    caseService.requireCaseAccess(caseId, AclAction.READ);
    return snapshotStore.getDecomposition(caseId, currentPrincipal.tenancyId())
        .orElseThrow(() -> new EntityNotFoundException(
            "No decomposition snapshot for case: " + caseId));
  }

  @GET
  @Path("/dag")
  @RunOnVirtualThread
  @Operation(summary = "Get DAG plan snapshot")
  @APIResponse(responseCode = "200", description = "DAG plan snapshot")
  @APIResponse(responseCode = "404", description = "No DAG plan captured",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public DagPlanSnapshot getDagPlan(@PathParam("caseId") UUID caseId) {
    caseService.requireCaseAccess(caseId, AclAction.READ);
    return snapshotStore.getDagPlan(caseId, currentPrincipal.tenancyId())
        .orElseThrow(() -> new EntityNotFoundException(
            "No DAG plan snapshot for case: " + caseId));
  }

  @GET
  @Path("/dag/result")
  @RunOnVirtualThread
  @Operation(summary = "Get DAG execution result snapshot")
  @APIResponse(responseCode = "200", description = "DAG result snapshot")
  @APIResponse(responseCode = "404", description = "No DAG result captured",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public DagResultSnapshot getDagResult(@PathParam("caseId") UUID caseId) {
    caseService.requireCaseAccess(caseId, AclAction.READ);
    return snapshotStore.getDagResult(caseId, currentPrincipal.tenancyId())
        .orElseThrow(() -> new EntityNotFoundException(
            "No DAG result snapshot for case: " + caseId));
  }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn test -pl rest -Dtest=PlanResourceTest -q`
Expected: PASS

- [ ] **Step 5: Run full module test suite for regression**

Run: `mvn test -pl rest -q`
Expected: PASS — no regressions in existing endpoints

- [ ] **Step 6: Commit**

```bash
git add rest/src/main/java/io/casehub/engine/rest/PlanResource.java \
       rest/src/test/java/io/casehub/engine/rest/PlanResourceTest.java
git commit -m "feat(#873): PlanResource with 5 HTN/DAG REST endpoints

GET /plan/model, /plan/definitions, /plan/decomposition,
/plan/dag, /plan/dag/result. All @RunOnVirtualThread with ACL
enforcement. Returns snapshot types directly — Jackson handles
@JsonTypeInfo discriminators.

Refs #873"
```

---

### Task 7: File GitHub Issues

**Files:** None (GitHub API only)

- [ ] **Step 1: File blocks snapshot capture issue**

```bash
gh issue create --repo casehubio/blocks \
  --title "Wire snapshot capture at decomposition/DagDriver call sites" \
  --body "## Summary

Follow-on from casehubio/engine#873 (HTN/DAG REST endpoints).

Engine now provides \`ExecutionSnapshotStore\` SPI and
\`SnapshotCapturingDagEventListener\` convenience listener. Blocks
needs to wire them at its decomposition and DagDriver call sites.

## What to do

1. Inject \`ExecutionSnapshotStore\` where decomposition runs
2. After \`DecompositionStrategy.decompose()\` returns, call
   \`store.storeDecomposition(caseId, DecompositionSnapshot.from(tree))\`
3. When constructing \`DagDriver\`, pass
   \`new SnapshotCapturingDagEventListener<>(caseId, store, plan)\`
   in the listener list — captures DagPlan on construction and
   DagResult on completion

Scale: S | Complexity: Low"
```

- [ ] **Step 2: File blocks model update issue**

```bash
gh issue create --repo casehubio/blocks \
  --title "Update call sites for CompoundTask.id and DecompositionMethod.guardLabel" \
  --body "## Summary

Follow-on from casehubio/engine#873. Breaking record changes:
- \`CompoundTask\` now requires \`String id\` as first parameter
- \`DecompositionMethod\` now has \`String guardLabel\` as third parameter (nullable)

Mechanical — find all constructors, add arguments.

Scale: S | Complexity: Low"
```

- [ ] **Step 3: File TTL cleanup issue**

```bash
gh issue create --repo casehubio/engine \
  --title "Snapshot TTL-based cleanup for ExecutionSnapshotStore" \
  --body "## Summary

Follow-on from #873. \`InMemoryExecutionSnapshotStore\` retains
snapshots indefinitely (no eviction on case terminal state — snapshots
have post-mortem value). Add a TTL-based cleanup or bounded cache to
prevent unbounded growth in long-running JVMs.

Scale: S | Complexity: Low"
```

- [ ] **Step 4: File blocks-ui type removal issue**

```bash
gh issue create --repo casehubio/blocks-ui \
  --title "Remove rationale from LeafTaskSnapshot and selectedMethodIndex from CompoundTaskSnapshot" \
  --body "## Summary

Follow-on from casehubio/engine#873. These TypeScript fields have no
source in the Java domain model:
- \`LeafTaskSnapshot.rationale\` — no \`rationale\` on \`TaskDescriptor\`
- \`CompoundTaskSnapshot.selectedMethodIndex\` — runtime state not
  available from \`TaskNode\` alone

Remove to keep contracts aligned.

Scale: XS | Complexity: Low"
```

- [ ] **Step 5: Commit nothing — issues are on GitHub**

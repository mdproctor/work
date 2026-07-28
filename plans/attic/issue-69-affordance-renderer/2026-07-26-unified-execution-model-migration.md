# Unified Execution Model Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #60 — refactor: unified execution model — promote agentic orchestration infrastructure to engine
**Issue group:** #60, engine#600 (HTN), engine#702 (ExecutorRef migration), engine#84 (Stage/Milestone/Goal alignment)

**Goal:** Migrate planning infrastructure from blocks' agentic package to engine, establishing a unified execution model where engine owns planning (task structure) and blocks owns techniques (problem-solving patterns).

**Architecture:** Two primitives (Primitive PlanItem dispatches a worker, Compound PlanItem dispatches children via a named strategy), one graph (PlanItem DAG within a CaseInstance), one runtime (CaseInstance provides context/lifecycle). The five compositional SPIs (routing, decomposition, activation, aggregation, termination) stay in blocks as technique concerns. Planning types (TaskNode, DecompositionStrategy) promote to engine-api. Stage retires as compound PlanItem configuration.

**Tech Stack:** Java 21, Quarkus 3.31+, Maven, JUnit 5 + Mockito, SmallRye Mutiny (Uni<T>)

**Scope:** Phases 0–6 migrate planning infrastructure. Phase 7 wires agent dispatch and builds advanced SPI implementations (GOAP, disposition-aware routing). Phase 8 adds the Quarkus Flow backend for workflow-shaped patterns.

## Global Constraints

- All code changes follow TDD — failing test first, then implementation
- Every commit references an issue (`Refs casehubio/blocks#60` or `Refs casehubio/engine#NNN`)
- Cross-repo changes: install the upstream module (`mvn install -DskipTests`) before compiling downstream consumers
- Engine PlanItem is the plan graph — immutable definition. Execution state is external (CasePlanModel-owned)
- Strategies are peers resolved by name at runtime — no `@Alternative @Priority` for strategy selection
- `ExecutorRef` at the engine-api boundary; `AgentRef` in blocks only
- IntelliJ MCP for all code navigation and structural editing
- No bash file operations on Java source — use `ide_move_file`, `ide_refactor_rename`, `ide_refactor_safe_delete`

## Prerequisite Status (as of 2026-07-26)

| Prerequisite | Status | Notes |
|---|---|---|
| `AgentRef extends ExecutorRef` | ✅ Done | `blocks/AgentRef.java:14` |
| PlanItem has `executor` (ExecutorRef) field | ✅ Done | engine#700 delivered |
| `StrategyResolver` SPI | ✅ Exists | `platform-api/.../routing/StrategyResolver.java` + `EngineStrategyResolver` impl |
| engine#700 (unified model design) | ✅ Closed | Design decisions locked |
| engine#634 (StrategyResolver architecture) | ✅ Closed | Universal routing pattern established |
| engine#203 (ContextBridge protocol) | ✅ Closed | Unblocks blocks Phase 2 roadmap |
| engine#694 (DagPlan structure) | ✅ Closed | `DagPlan<T>` exists in engine-common |
| engine#747 (ExpressionEvaluator unification) | ✅ Closed | Simplifies entry condition handling |
| `sequentialMerge()` on DagPlan | ❌ Not done | Reference impl exists on `ExecutionPlan` (blocks) |
| engine-common transitive to blocks | ❌ Not available | engine-api does NOT depend on engine-common |
| ChoreographyLoopControl retirement | ❌ Not done | Still in engine/runtime as `@ApplicationScoped` default |
| Module rename (blackboard → engine-planning) | ❌ Not done | Module is `casehub-engine-blackboard`, pkg `io.casehub.blackboard` |
| PlanItem sealed hierarchy | ❌ Not done | Flat mutable class, no Primitive/Compound |
| Stage retirement | ❌ Not done | Full mutable class in `blackboard/stage/` |
| PlanningStrategy compound parameter | ❌ Not done | Current: `select(CasePlanModel, PlanExecutionContext, List<Binding>)` |
| PlanItemStore compound support | ❌ Not done | Flat 11-field records, no type discriminator |
| PlanningStrategyLoopControl → StrategyResolver | ❌ Not done | Builds own `Map<String, PlanningStrategy>` internally |

## Dependency Graph

```
Phase 0 (Foundation)
  └─► Phase 1 (LoopControl unification)
       └─► Phase 2 (Module rename)
            └─► Phase 3A (PlanItem sealed hierarchy)
                 ├─► Phase 3B (CasePlanModel API)
                 │    └─► Phase 3C (Stage migration)
                 │         └─► Phase 3D (Per-compound dispatch)
                 └─► Phase 4 (DAG plan unification — blocks) [parallel with 3B-3D]
                      └─► Phase 5 (HTN SPI promotion)
                           └─► Phase 6 (Strategy wiring)
                                └─► Phase 7 (Agent dispatch + advanced SPIs)
                                     └─► Phase 8 (Quarkus Flow backend)
```

Phases 0–2 are engine-only. Phase 4 is blocks-only. Phases 3, 5, 6 span both repos. Phases 7–8 are blocks + engine-flow.

---

## Phase 0: Foundation Prerequisites

**Repo:** engine (engine-common module)
**Risk:** Low — additive, no breaking changes
**Rollback:** Revert commits

### Task 0.1: Add `sequentialMerge()` to DagPlan

**Files:**
- Modify: `engine/common/src/main/java/io/casehub/engine/plan/DagPlan.java`
- Test: `engine/common/src/test/java/io/casehub/engine/plan/DagPlanTest.java`

**Interfaces:**
- Consumes: `DagPlan<T>`, `DagNode<T>`, `JoinType`
- Produces: `DagPlan.sequentialMerge(List<DagPlan<T>> subPlans) → DagPlan<T>`

**Reference implementation:** `blocks/.../agentic/plan/ExecutionPlan.java:146-187` — port this algorithm. It prefixes node IDs with `"sub0-"`, `"sub1-"` etc. and wires exit nodes of plan N to entry nodes of plan N+1.

- [ ] **Step 1: Write failing tests**

```java
@Test
void sequentialMerge_two_plans_wires_exit_to_entry() {
    DagPlan<String> first = DagPlan.singleton("a", "task-a");
    DagPlan<String> second = DagPlan.singleton("b", "task-b");

    DagPlan<String> merged = DagPlan.sequentialMerge(List.of(first, second));

    assertThat(merged.nodes()).hasSize(2);
    assertThat(merged.entryNodeIds()).hasSize(1);
    assertThat(merged.exitNodeIds()).hasSize(1);
    // exit of first (sub0-a) should be dependency of entry of second (sub1-b)
    DagNode<String> secondNode = merged.nodes().get("sub1-b");
    assertThat(secondNode.dependsOn()).containsExactly("sub0-a");
}

@Test
void sequentialMerge_single_plan_returns_unchanged() {
    DagPlan<String> plan = DagPlan.singleton("x", "task-x");
    DagPlan<String> merged = DagPlan.sequentialMerge(List.of(plan));
    assertThat(merged).isSameAs(plan);
}

@Test
void sequentialMerge_empty_throws() {
    assertThatThrownBy(() -> DagPlan.sequentialMerge(List.of()))
            .isInstanceOf(IllegalArgumentException.class);
}

@Test
void sequentialMerge_three_plans_preserves_internal_deps() {
    // Plan with internal sequence: a -> b
    DagNode<String> a = new DagNode<>("a", "t-a", Set.of(), JoinType.ALL_OF);
    DagNode<String> b = new DagNode<>("b", "t-b", Set.of("a"), JoinType.ALL_OF);
    DagPlan<String> planAB = DagPlan.sequence(List.of(a, b));

    DagPlan<String> planC = DagPlan.singleton("c", "t-c");
    DagPlan<String> planD = DagPlan.singleton("d", "t-d");

    DagPlan<String> merged = DagPlan.sequentialMerge(List.of(planAB, planC, planD));

    assertThat(merged.nodes()).hasSize(4);
    // Internal dep preserved: sub0-a -> sub0-b
    assertThat(merged.nodes().get("sub0-b").dependsOn()).contains("sub0-a");
    // Cross-plan wiring: sub0-b (exit of plan 0) -> sub1-c (entry of plan 1)
    assertThat(merged.nodes().get("sub1-c").dependsOn()).contains("sub0-b");
    // sub1-c (exit of plan 1) -> sub2-d (entry of plan 2)
    assertThat(merged.nodes().get("sub2-d").dependsOn()).contains("sub1-c");
    assertThat(merged.topologicalSort()).hasSize(4);
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `mvn test -pl common -Dtest=DagPlanTest -DfailIfNoTests=false --batch-mode`
Expected: compilation error — `sequentialMerge` not found

- [ ] **Step 3: Implement `sequentialMerge`**

Port from `ExecutionPlan.sequentialMerge()` (blocks), adapting `ExecutionNode` → `DagNode`:

```java
public static <T> DagPlan<T> sequentialMerge(List<DagPlan<T>> subPlans) {
    if (subPlans.isEmpty()) throw new IllegalArgumentException("subPlans must not be empty");
    if (subPlans.size() == 1) return subPlans.get(0);

    Map<String, DagNode<T>> mergedNodes = new LinkedHashMap<>();
    Set<String> previousExitIds = Set.of();

    for (int planIdx = 0; planIdx < subPlans.size(); planIdx++) {
        DagPlan<T> subPlan = subPlans.get(planIdx);
        String prefix = "sub" + planIdx + "-";

        Map<String, String> idMapping = new HashMap<>();
        for (String oldId : subPlan.nodes().keySet()) {
            idMapping.put(oldId, prefix + oldId);
        }

        Set<String> subPlanEntryIds = subPlan.entryNodeIds();

        for (var entry : subPlan.nodes().entrySet()) {
            DagNode<T> oldNode = entry.getValue();
            String newId = idMapping.get(oldNode.id());

            Set<String> newDeps = new HashSet<>(oldNode.dependsOn().stream()
                    .map(idMapping::get).collect(Collectors.toSet()));
            if (subPlanEntryIds.contains(oldNode.id())) {
                newDeps.addAll(previousExitIds);
            }

            mergedNodes.put(newId, new DagNode<>(newId, oldNode.task(),
                    newDeps, oldNode.joinType()));
        }

        previousExitIds = subPlan.exitNodeIds().stream()
                .map(id -> prefix + id)
                .collect(Collectors.toSet());
    }

    return new DagPlan<>(mergedNodes);
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `mvn test -pl common -Dtest=DagPlanTest --batch-mode`
Expected: all PASS

- [ ] **Step 5: Commit**

```
feat(engine-common): add sequentialMerge to DagPlan

Refs casehubio/blocks#60
```

### Task 0.2: Make engine-common transitive to blocks via engine-api

**Files:**
- Modify: `engine/api/pom.xml`
- Verify: `blocks/pom.xml` (no change needed — blocks already depends on engine-api)

**Interfaces:**
- Produces: `DagPlan<T>`, `DagNode<T>`, `JoinType` available to blocks at compile scope

**Decision:** Add engine-common as a compile dependency of engine-api. DagPlan is a domain type (plan structure) that belongs alongside other API-level constructs like `ExecutorRef`, `Binding`, and `PlanExecutionContext`. This is cleaner than blocks depending on engine-common directly.

- [ ] **Step 1: Verify current engine-api dependencies**

Use `ide_read_file` to read `engine/api/pom.xml` — confirm engine-common is absent.

- [ ] **Step 2: Add dependency**

Add to `engine/api/pom.xml` dependencies:
```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-engine-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 3: Verify transitivity**

```bash
mvn dependency:tree -pl api --batch-mode | grep engine-common
```
Expected: `casehub-engine-common` appears as compile dependency

- [ ] **Step 4: Verify blocks can resolve DagPlan**

In blocks repo, write a scratch test importing `io.casehub.engine.plan.DagPlan` — verify it compiles. Remove after verification.

- [ ] **Step 5: Commit**

```
build(engine-api): add engine-common as compile dependency

Makes DagPlan, DagNode, JoinType available to blocks and other engine-api
consumers via transitive dependency.

Refs casehubio/blocks#60
```

### Task 0.3: Install engine to local Maven repo

After Phase 0 engine changes, install so blocks can compile against them:

```bash
mvn install -DskipTests --batch-mode -f engine/pom.xml
```

---

## Phase 1: LoopControl Unification

**Repo:** engine (runtime + blackboard modules)
**Risk:** Low — ChoreographyLoopControl is a strict subset of PlanningStrategyLoopControl
**Rollback:** Revert; re-add ChoreographyLoopControl
**Verification:** All existing engine tests pass unchanged

### Task 1.1: Retire ChoreographyLoopControl

**Files:**
- Delete: `engine/runtime/src/main/java/io/casehub/engine/internal/engine/ChoreographyLoopControl.java` (use `ide_refactor_safe_delete`)
- Modify: `engine/blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java` — remove `@Alternative @Priority(10)`, make it `@ApplicationScoped` (the only impl)
- Modify: `engine/blackboard/src/main/java/io/casehub/blackboard/control/DefaultPlanningStrategy.java` — rename to `ChoreographyStrategy` (use `ide_refactor_rename`)
- Test: existing tests must pass unchanged

**Interfaces:**
- Consumes: `LoopControl` SPI (engine-api)
- Produces: `PlanningStrategyLoopControl` as the sole `LoopControl` implementation, `ChoreographyStrategy` (id="default") as the fallback strategy

**Key insight:** `ChoreographyLoopControl` returns all eligible bindings unchanged. `PlanningStrategyLoopControl` with `DefaultPlanningStrategy` (id="default") does the same when no stages/plan exist. The removal is semantically neutral.

- [ ] **Step 1: Verify behavioural equivalence — write characterisation test**

```java
@Test
void planningStrategy_with_default_behaves_like_choreography() {
    // Setup: PlanningStrategyLoopControl with DefaultPlanningStrategy, no stages
    // Provide eligible bindings
    // Assert: all bindings returned unchanged (same as ChoreographyLoopControl)
}
```

- [ ] **Step 2: Run existing tests to establish baseline**

```bash
mvn test --batch-mode -f engine/pom.xml
```

Capture test count and verify all pass.

- [ ] **Step 3: Check ChoreographyLoopControl references**

Use `ide_find_references` on `ChoreographyLoopControl` to find all usages. Expect: only CDI discovery (no direct references from application code, since it's discovered via the `LoopControl` SPI).

- [ ] **Step 4: Remove `@Alternative @Priority(10)` from PlanningStrategyLoopControl**

Make it `@ApplicationScoped` — no longer an alternative, it's the default.

- [ ] **Step 5: Safe-delete ChoreographyLoopControl**

Use `ide_refactor_safe_delete`. If any references block deletion, update them first.

- [ ] **Step 6: Rename DefaultPlanningStrategy → ChoreographyStrategy**

Use `ide_refactor_rename` on the class. Verify the `id()` method still returns `"default"` (keep backward compatibility for YAML definitions that reference `strategy: default`).

- [ ] **Step 7: Run all tests**

```bash
mvn test --batch-mode -f engine/pom.xml
```

Expected: same test count, all pass.

- [ ] **Step 8: Commit**

```
refactor: retire ChoreographyLoopControl — PlanningStrategyLoopControl is the sole LoopControl

ChoreographyLoopControl was a strict subset of PlanningStrategyLoopControl
with DefaultPlanningStrategy (now ChoreographyStrategy). All existing
behaviour preserved.

Refs casehubio/blocks#60
```

---

## Phase 2: Module Rename (blackboard → engine-planning)

**Repo:** engine + all consumer repos
**Risk:** Medium — import changes across many repos, but purely mechanical
**Rollback:** Revert engine commit; consumer repos remain on old imports until coordinated
**Verification:** Full build of engine + blocks + all consumers that import blackboard types

### Task 2.1: Rename engine module and package

**Files:**
- Rename directory: `engine/blackboard/` → `engine/planning/` (bash `mv` OK for directories)
- Modify: `engine/planning/pom.xml` — `artifactId` → `casehub-engine-planning`
- Modify: `engine/pom.xml` — `<module>blackboard</module>` → `<module>planning</module>`
- Rename package: `io.casehub.blackboard` → `io.casehub.engine.planning` (use `ide_refactor_rename` on the package)
- Modify: all engine modules that depend on `casehub-engine-blackboard` in their pom.xml

**This is a large mechanical refactor.** Use IntelliJ's package rename to update all internal references, then manually fix Maven coordinates.

- [ ] **Step 1: Audit all references to `casehub-engine-blackboard` and `io.casehub.blackboard`**

```bash
grep -rn "casehub-engine-blackboard\|casehub.blackboard\|io\.casehub\.blackboard" engine/ --include="*.java" --include="*.xml" --include="*.yaml" | grep -v ".worktrees/" | grep -v "target/" | wc -l
```

Record the count for verification.

- [ ] **Step 2: Rename the Maven module directory**

```bash
mv engine/blackboard engine/planning
```

- [ ] **Step 3: Update Maven coordinates**

In `engine/planning/pom.xml`: change `<artifactId>casehub-engine-blackboard</artifactId>` → `<artifactId>casehub-engine-planning</artifactId>`.

In `engine/pom.xml`: change `<module>blackboard</module>` → `<module>planning</module>`.

In all engine modules' `pom.xml` that reference `casehub-engine-blackboard`: update to `casehub-engine-planning`.

- [ ] **Step 4: Rename Java package**

Use `ide_refactor_rename` on the `io.casehub.blackboard` package → `io.casehub.engine.planning`.

This updates all internal imports within the engine repo.

- [ ] **Step 5: Build engine**

```bash
mvn install -DskipTests --batch-mode -f engine/pom.xml
```

Fix any remaining references.

- [ ] **Step 6: Run all engine tests**

```bash
mvn test --batch-mode -f engine/pom.xml
```

Expected: all pass.

- [ ] **Step 7: Commit engine**

```
refactor: rename blackboard → engine-planning

Module casehub-engine-blackboard → casehub-engine-planning.
Package io.casehub.blackboard → io.casehub.engine.planning.
The module is no longer an optional CMMN-inspired alternative — it's the
engine's core planning infrastructure.

Refs casehubio/blocks#60
```

### Task 2.2: Update consumer repo imports

**Affected repos** (from blocks CLAUDE.md consumers list):
- blocks — `import io.casehub.blackboard.*` in test scope
- openclaw — `Stage`, `CasePlanModel` references
- Other consumers that directly import blackboard types (audit via workspace grep)

For each consumer:

- [ ] **Step 1: Update pom.xml** — `casehub-engine-blackboard` → `casehub-engine-planning`
- [ ] **Step 2: Update imports** — `io.casehub.blackboard` → `io.casehub.engine.planning`
- [ ] **Step 3: Build and test**
- [ ] **Step 4: Commit** — `build: update blackboard → engine-planning imports (Refs casehubio/blocks#60)`

---

## Phase 3A: PlanItem Sealed Hierarchy

**Repo:** engine (engine-planning module)
**Risk:** High — changes the core data model; persistence schema changes
**Rollback:** Revert to mutable PlanItem (kept as compatibility layer during migration)
**Verification:** All engine tests pass; PlanItem persistence round-trips correctly

This is the largest phase. The approach is:
1. Define the new sealed `PlanItem` interface alongside the existing class
2. Migrate consumers incrementally
3. Remove the old class when all consumers use the new type

### Task 3A.1: Define sealed PlanItem interface with Primitive and Compound records

**Files:**
- Create: `engine/planning/src/main/java/io/casehub/engine/planning/plan/NewPlanItem.java` (temporary name to avoid collision)
- Test: `engine/planning/src/test/java/io/casehub/engine/planning/plan/NewPlanItemTest.java`

**Interfaces:**
- Produces:
  ```java
  sealed interface NewPlanItem permits NewPlanItem.Primitive, NewPlanItem.Compound {
      String id();
      String name();
      DispatchMode dispatchMode();
  
      record Primitive(
          String id, String name,
          ExecutorRef executor,
          DispatchMode dispatchMode,
          @Nullable ExpressionEvaluator entryCondition
      ) implements NewPlanItem {}
  
      record Compound(
          String id, String name,
          List<NewPlanItem> children,
          @Nullable String planningStrategy,  // null → "default" (choreography)
          CompletionSemantics completion,
          DispatchMode dispatchMode,
          @Nullable ExpressionEvaluator entryCondition,
          @Nullable ExpressionEvaluator exitCondition,
          boolean repeatable
      ) implements NewPlanItem {}
  }
  
  enum DispatchMode { ORCHESTRATED, CHOREOGRAPHED }
  
  sealed interface CompletionSemantics
      permits CompletionSemantics.All, CompletionSemantics.MOfN, CompletionSemantics.FirstWins {
      record All() implements CompletionSemantics {}
      record MOfN(int m) implements CompletionSemantics {}
      record FirstWins() implements CompletionSemantics {}
  }
  ```

- [ ] **Step 1: Write tests for the new type hierarchy**

Test construction, validation (non-null id/name), DispatchMode, CompletionSemantics variants, Compound with children.

- [ ] **Step 2: Implement the sealed interface and records**
- [ ] **Step 3: Run tests**
- [ ] **Step 4: Commit**

```
feat: define sealed PlanItem hierarchy — Primitive and Compound records

Refs casehubio/blocks#60
```

### Task 3A.2: Create PlanItemExecutionState for CAS-guarded transitions

**Files:**
- Create: `engine/planning/src/main/java/io/casehub/engine/planning/plan/PlanItemExecutionState.java`
- Test: `engine/planning/src/test/java/io/casehub/engine/planning/plan/PlanItemExecutionStateTest.java`

**Interfaces:**
- Consumes: `TaskStatus` (engine-api)
- Produces:
  ```java
  public class PlanItemExecutionState {
      private final String planItemId;
      private final AtomicReference<TaskStatus> status;
      private final Instant createdAt;
      
      public boolean tryTransition(TaskStatus from, TaskStatus to) // CAS
      public TaskStatus getStatus()
  }
  ```

**Rationale:** The current `PlanItem` class has CAS state transitions baked in (`tryMarkRunning()`, `markCompleted()`, etc.). The new immutable PlanItem records separate plan definition from execution state. `PlanItemExecutionState` owns the state machine externally.

- [ ] **Step 1: Write tests for CAS transitions** — test legal and illegal state transitions
- [ ] **Step 2: Implement** — port state machine from current `PlanItem` class
- [ ] **Step 3: Run tests**
- [ ] **Step 4: Commit**

### Task 3A.3: Extend persistence schema for compound PlanItems

**Files:**
- Modify: `engine/common/src/main/java/io/casehub/engine/common/spi/PlanItemStore.java`
- Modify: `engine/common/...PlanItemRecord.java` and `PlanItemSaveRequest.java`
- Modify: `engine/persistence-memory/.../InMemoryPlanItemStore.java`
- Modify: `engine/persistence-hibernate/.../JpaPlanItemStore.java` + entities
- Test: update existing persistence tests

**Interfaces:**
- Consumes: `NewPlanItem.Compound`, `CompletionSemantics`, `DispatchMode`
- Produces: extended `PlanItemRecord` with:
  - `type` discriminator (`"PRIMITIVE"` / `"COMPOUND"`)
  - `planningStrategy` (nullable String)
  - `completionSemantics` (nullable String — serialised)
  - `dispatchMode` (`"ORCHESTRATED"` / `"CHOREOGRAPHED"`)
  - `repeatable` (boolean)
  - `parentCompoundId` (nullable String — for parent-child relationships)

**Note:** Stages are NOT persisted today — they're rebuilt by `BlackboardPlanConfigurer`. Compound PlanItem persistence is therefore net-new, not adaptation of existing Stage persistence. This is additive — existing primitive PlanItem persistence is unchanged (type defaults to `"PRIMITIVE"`).

- [ ] **Step 1: Write test for compound PlanItem round-trip**
- [ ] **Step 2: Extend records** — add new fields with defaults for backward compatibility
- [ ] **Step 3: Update InMemoryPlanItemStore**
- [ ] **Step 4: Update JpaPlanItemStore + entity** (if JPA is in scope)
- [ ] **Step 5: Run all persistence tests**
- [ ] **Step 6: Commit**

---

## Phase 3B: CasePlanModel API Redesign

**Repo:** engine (engine-planning module)
**Risk:** High — CasePlanModel is used by ~25 handlers and evaluators
**Rollback:** Keep old API alongside new until migration complete
**Verification:** All engine tests pass; handlers compile against new API

### Task 3B.1: Add compound lifecycle and state management methods

**Files:**
- Modify: `engine/planning/src/main/java/io/casehub/engine/planning/plan/CasePlanModel.java`
- Modify: `engine/planning/src/main/java/io/casehub/engine/planning/plan/DefaultCasePlanModel.java`
- Test: `engine/planning/src/test/java/io/casehub/engine/planning/plan/DefaultCasePlanModelTest.java`

**Interfaces:**
- Produces (new methods on CasePlanModel):
  ```java
  // State management (new)
  boolean tryTransition(String planItemId, TaskStatus from, TaskStatus to);
  TaskStatus getStatus(String planItemId);
  
  // Parent-child tracking (new)
  Set<String> getChildrenOf(String compoundId);
  Optional<String> getParentOf(String planItemId);
  void addChild(String compoundId, NewPlanItem child);
  
  // Compound lifecycle (new — replaces Stage queries)
  Set<NewPlanItem.Compound> getActiveCompounds();
  Set<NewPlanItem.Compound> getPendingCompounds();
  
  // Completion evaluation (new)
  boolean evaluateCompletion(String compoundId);
  ```

**Approach:** Add new methods alongside existing ones. The old `addPlanItem(PlanItem)`, `getStage()` etc. remain until Phase 3C migrates all consumers.

- [ ] **Step 1: Write tests for new methods**
- [ ] **Step 2: Add new methods to interface**
- [ ] **Step 3: Implement in DefaultCasePlanModel**
- [ ] **Step 4: Run all tests**
- [ ] **Step 5: Commit**

### Task 3B.2: Implement completion propagation

**Files:**
- Modify: `DefaultCasePlanModel` — `evaluateCompletion()` implementation
- Test: completion propagation tests

**Interfaces:**
- Consumes: `CompletionSemantics` (ALL, M_OF_N, FIRST_WINS)
- Produces: `evaluateCompletion(compoundId)` → `true` when compound should complete based on children states and its CompletionSemantics

When a compound completes, it triggers `evaluateCompletion` on its parent (propagation up the tree).

- [ ] **Step 1: Write tests for each CompletionSemantics variant**
  - ALL: completes when every child is terminal
  - M_OF_N(2): completes when 2 children are terminal, cancels rest
  - FIRST_WINS: completes when first child completes, cancels siblings
- [ ] **Step 2: Implement**
- [ ] **Step 3: Run tests**
- [ ] **Step 4: Commit**

---

## Phase 3C: Stage Infrastructure Migration

**Repo:** engine (engine-planning module)
**Risk:** High — touches event bus, handlers, evaluators
**Rollback:** Stage remains as thin wrapper until fully migrated
**Verification:** All engine tests pass; no Stage references remain outside compatibility layer

### Task 3C.1: Migrate Stage lifecycle evaluators

**Files:**
- Modify: `engine/planning/.../handler/StageAutocompleteEvaluator.java` → compound CompletionSemantics evaluator
- Modify: `engine/planning/.../control/StageLifecycleEvaluator.java` → compound lifecycle evaluator
- Modify: `engine/planning/.../handler/StageResetOutcomesCleaner.java` → compound reset logic
- Test: update evaluator tests

**Interfaces:**
- Consumes: `NewPlanItem.Compound`, `CompletionSemantics`, `CasePlanModel.evaluateCompletion()`
- Produces: evaluators that work with compound PlanItems instead of Stages

**Approach:** Each evaluator gains a parallel method that accepts `NewPlanItem.Compound`. Old Stage methods remain until Task 3C.3.

- [ ] **Step 1: Write tests for compound-aware evaluator methods**
- [ ] **Step 2: Implement compound-aware methods alongside existing Stage methods**
- [ ] **Step 3: Run all tests**
- [ ] **Step 4: Commit**

### Task 3C.2: Migrate Stage events to compound PlanItem events

**Files:**
- Create or modify: events for compound lifecycle (activate, complete, terminate)
- Modify: event handlers that consume Stage events
- Modify: `BlackboardEventBusAddresses` (or its renamed successor)

**Interfaces:**
- Retires: `StageActivatedEvent`, `StageCompletedEvent`, `StageTerminatedEvent`
- Produces: `CompoundPlanItemActivatedEvent`, `CompoundPlanItemCompletedEvent`, `CompoundPlanItemTerminatedEvent`

- [ ] **Step 1: Create new event types**
- [ ] **Step 2: Update event handlers to consume both old and new events** (dual-dispatch during migration)
- [ ] **Step 3: Update event publishers to emit new events**
- [ ] **Step 4: Remove old Stage events once no consumers remain**
- [ ] **Step 5: Run all tests**
- [ ] **Step 6: Commit**

### Task 3C.3: Retire Stage as a distinct type

**Files:**
- Delete: `engine/planning/.../stage/Stage.java` (use `ide_refactor_safe_delete` once all references are migrated)
- Delete: `engine/planning/.../stage/StageStatus.java`
- Modify: `BlackboardPlanConfigurer` (or renamed successor) — produce `NewPlanItem.Compound` instead of `Stage`
- Modify: all handlers that reference `Stage`

**This task can only be done when ALL Stage references have been migrated to compound PlanItem.** Run `ide_find_references` on `Stage` to verify zero remaining usages before deletion.

- [ ] **Step 1: Audit remaining Stage references** — `ide_find_references` on Stage class
- [ ] **Step 2: Migrate each reference to use `NewPlanItem.Compound`**
- [ ] **Step 3: `ide_refactor_safe_delete` on Stage and StageStatus**
- [ ] **Step 4: Run all tests**
- [ ] **Step 5: Commit**

---

## Phase 3D: Per-Compound Strategy Dispatch

**Repo:** engine (engine-planning module)
**Risk:** Medium — changes dispatch path but PlanningStrategy SPI is backward-compatible
**Rollback:** Revert per-compound grouping; fall back to flat dispatch
**Verification:** All engine tests pass; mixed-strategy cases work correctly

### Task 3D.1: Evolve PlanningStrategy SPI with compound parameter

**Files:**
- Modify: `engine/planning/.../control/PlanningStrategy.java`
- Modify: `engine/planning/.../control/ChoreographyStrategy.java` (née DefaultPlanningStrategy)
- Modify: `engine/planning/.../control/SequentialPlanningStrategy.java`
- Test: update strategy tests

**Interfaces:**
- Changes `PlanningStrategy.select()` signature:
  ```java
  // Old:
  List<Binding> select(CasePlanModel plan, PlanExecutionContext context, List<Binding> eligible);
  // New:
  List<Binding> select(CasePlanModel plan, PlanExecutionContext context,
                       NewPlanItem.Compound compound, List<Binding> eligible);
  ```

**Migration approach:** Add the new method as a default method on the interface that delegates to the old signature (ignoring compound). This keeps existing implementations working. New implementations override the new method.

- [ ] **Step 1: Add new method with default delegation**
- [ ] **Step 2: Update ChoreographyStrategy and SequentialPlanningStrategy to override**
- [ ] **Step 3: Deprecate old method**
- [ ] **Step 4: Run all tests**
- [ ] **Step 5: Commit**

### Task 3D.2: Per-compound grouping in PlanningStrategyLoopControl

**Files:**
- Modify: `engine/planning/.../control/PlanningStrategyLoopControl.java`
- Test: new integration tests for per-compound dispatch

**Interfaces:**
- Consumes: `CasePlanModel.getChildrenOf()`, `NewPlanItem.Compound.planningStrategy()`
- Produces: grouped dispatch — eligible bindings are grouped by containing compound, each compound's strategy selects from its children

```java
// Pseudocode for the new dispatch path:
eligible
  → group by containing compound PlanItem
  → for each compound: resolve strategy by name, call select(plan, ctx, compound, groupBindings)
  → merge results
  → free-floating bindings (no parent compound) use case-level strategy
```

- [ ] **Step 1: Write test — two compounds with different strategies dispatch independently**
- [ ] **Step 2: Implement grouping logic**
- [ ] **Step 3: Write test — free-floating bindings use case-level strategy**
- [ ] **Step 4: Run all tests**
- [ ] **Step 5: Commit**

### Task 3D.3: Stage builder API compatibility

**Files:**
- Modify or create: builder that produces `NewPlanItem.Compound` from `Stage.builder()` API
- Test: existing Stage builder tests pass against new implementation

**Interfaces:**
- Produces: `Stage.builder(name)` returns a builder that produces `NewPlanItem.Compound` with choreography strategy (backward compatible)

**Rationale:** There is no YAML `stages:` feature — Stage builders are used programmatically. The builder API must be preserved for backward compatibility, but the output changes from `Stage` to `NewPlanItem.Compound`.

- [ ] **Step 1: Write test — `Stage.builder("myStage")` produces a valid Compound**
- [ ] **Step 2: Implement builder compatibility**
- [ ] **Step 3: Run existing Stage builder tests**
- [ ] **Step 4: Commit**

### Task 3D.4: Rename NewPlanItem → PlanItem

Once the old `PlanItem` class is no longer referenced:

- [ ] **Step 1: `ide_find_references` on old `PlanItem` class — verify zero usages**
- [ ] **Step 2: `ide_refactor_safe_delete` old `PlanItem` class**
- [ ] **Step 3: `ide_refactor_rename` `NewPlanItem` → `PlanItem`**
- [ ] **Step 4: Run all tests**
- [ ] **Step 5: Commit**

---

## Phase 4: DAG Plan Unification (blocks)

**Repo:** blocks
**Risk:** Medium — changes internal type but API surface is small
**Rollback:** Revert; keep ExecutionPlan
**Verification:** All blocks tests pass; DecompositionStrategy return type updated
**Can run in parallel with Phase 3B-3D** (only depends on Phase 0 completing)

### Task 4.1: Migrate ExecutionPlan → DagPlan throughout blocks

**Files:**
- Modify: all files in `blocks/.../agentic/` that reference `ExecutionPlan` or `ExecutionNode`
- Delete: `blocks/.../agentic/plan/ExecutionPlan.java` (after all references migrated)
- Test: update all test references

**Key type mappings:**
| blocks (current) | engine-common (target) |
|---|---|
| `ExecutionPlan<T>` | `DagPlan<T>` |
| `ExecutionPlan.ExecutionNode<T>` | `DagNode<T>` |
| `ExecutionPlan.JoinType` | `JoinType` |
| `ExecutionPlan.sequentialMerge()` | `DagPlan.sequentialMerge()` |

**Affected classes** (find via `ide_find_references` on `ExecutionPlan`):
- `DecompositionStrategy<T>` — return type changes from `Uni<ExecutionPlan<T>>` to `Uni<DagPlan<T>>`
- `StaticDecomposition` — uses ExecutionPlan factory methods
- `HybridDecomposition` — uses ExecutionPlan
- `LlmDecomposition` — uses ExecutionPlan
- `IdentityDecomposition` — uses ExecutionPlan
- `HtnBuilder` — decomposes into ExecutionPlan
- `AbstractExecutionDriver` — operates on plan
- All tests referencing ExecutionPlan

- [ ] **Step 1: Audit all ExecutionPlan references** — `ide_find_references`
- [ ] **Step 2: Update `DecompositionStrategy` return type** — `Uni<ExecutionPlan<T>>` → `Uni<DagPlan<T>>`
- [ ] **Step 3: Update each decomposition implementation** (Static, Hybrid, LLM, Identity)
- [ ] **Step 4: Update HtnBuilder**
- [ ] **Step 5: Update AbstractExecutionDriver and tests**
- [ ] **Step 6: Delete ExecutionPlan** — `ide_refactor_safe_delete`
- [ ] **Step 7: Run all blocks tests**

```bash
mvn test --batch-mode -f blocks/pom.xml
```

- [ ] **Step 8: Commit**

```
refactor: ExecutionPlan → DagPlan — unify plan types with engine-common

Refs casehubio/blocks#60
```

---

## Phase 5: HTN Decomposition SPI Promotion

**Repo:** engine (engine-api, engine-planning) + blocks
**Risk:** Medium — API-level type changes affect blocks' decomposition implementations
**Rollback:** Revert engine-api changes; blocks keeps its own types
**Verification:** Engine + blocks + consumer tests all pass

### Task 5.1: Promote TaskNode to engine-api

**Files:**
- Create: `engine/api/src/main/java/io/casehub/api/model/TaskNode.java`
- Modify: `blocks/.../agentic/decomposition/TaskNode.java` — becomes a re-export or is deleted
- Test: engine-api tests for TaskNode

**Interfaces:**
- Produces (in engine-api):
  ```java
  sealed interface TaskNode<T> permits TaskNode.LeafTask, TaskNode.CompoundTask {
      sealed interface LeafTask<T> extends TaskNode<T>, TaskDescriptor
          permits TaskNode.PrimitiveTask, TaskNode.PlannedTask {
          ExecutorRef executor();  // NOT AgentRef
          @Nullable String description();
      }
      record PrimitiveTask<T>(..., ExecutorRef executor, ...) implements LeafTask<T> {}
      record PlannedTask<T>(..., ExecutorRef executor, ...) implements LeafTask<T> {}
      record CompoundTask<T>(String name, List<DecompositionMethod<T>> methods)
          implements TaskNode<T> {}
  }
  ```

**Key change:** `LeafTask.agent()` → `LeafTask.executor()` returning `ExecutorRef`. Blocks creates `LeafTask` instances passing `AgentRef` (which IS `ExecutorRef` via subtyping) — no data loss.

- [ ] **Step 1: Create TaskNode in engine-api with ExecutorRef**
- [ ] **Step 2: Update blocks to import from engine-api instead of local**
- [ ] **Step 3: In blocks, add convenience `agent()` method** that casts `executor()` back to `AgentRef` for blocks-internal use (or keep as a blocks-specific extension type)
- [ ] **Step 4: Run all tests in both repos**
- [ ] **Step 5: Commit engine** — `feat(engine-api): promote TaskNode sealed hierarchy`
- [ ] **Step 6: Commit blocks** — `refactor: use engine-api TaskNode`

### Task 5.2: Promote DecompositionStrategy, DecompositionMethod, DecompositionContext to engine-api

**Files:**
- Create in engine-api: `DecompositionStrategy.java`, `DecompositionMethod.java`, `DecompositionContext.java`, `NoMethodMatchedException.java`
- Delete from blocks: the original files (after updating all references)

**Interfaces:**
- `DecompositionStrategy<T>`:
  ```java
  // engine-api version:
  Uni<DagPlan<TaskNode.LeafTask<T>>> decompose(TaskNode<T> compound, DecompositionContext<T> context);
  ```
  Note: return type changes from `Uni<ExecutionPlan<T>>` → `Uni<DagPlan<LeafTask<T>>>` (Phase 4 already did ExecutionPlan → DagPlan; this additionally specifies the generic parameter)

- `DecompositionContext<T>`:
  ```java
  // engine-api version:
  record DecompositionContext<T>(T state, List<? extends ExecutorRef> executors, int depth) {}
  ```
  Note: `List<RoutingCandidate>` → `List<? extends ExecutorRef>`. Blocks strategies receive `AgentRef` instances (which ARE `ExecutorRef`) and can cast when needed.

- [ ] **Step 1: Create types in engine-api**
- [ ] **Step 2: Update blocks to import from engine-api**
- [ ] **Step 3: Update blocks decomposition implementations** — handle the type narrowing
- [ ] **Step 4: Run all tests**
- [ ] **Step 5: Commit both repos**

### Task 5.3: Promote StaticDecomposition and IdentityDecomposition to engine-planning

**Files:**
- Move: `blocks/.../agentic/decomposition/StaticDecomposition.java` → `engine/planning/.../decomposition/StaticDecomposition.java`
- Move: `blocks/.../agentic/decomposition/IdentityDecomposition.java` → `engine/planning/.../decomposition/IdentityDecomposition.java`
- Test: move corresponding tests

**These are pure logic implementations with no LLM dependency** — they belong in engine-planning.

- [ ] **Step 1: Create in engine-planning** — port from blocks, update imports
- [ ] **Step 2: Delete from blocks** — `ide_refactor_safe_delete`
- [ ] **Step 3: Update blocks references** to import from engine-planning
- [ ] **Step 4: Run all tests**
- [ ] **Step 5: Commit**

### Task 5.4: Create HTN-aware PlanningStrategy in engine-planning

**Files:**
- Create: `engine/planning/.../control/HtnPlanningStrategy.java`
- Test: `engine/planning/.../control/HtnPlanningStrategyTest.java`

**Interfaces:**
- Consumes: `DecompositionStrategy<T>`, `TaskNode.CompoundTask`, `CasePlanModel`, `NewPlanItem.Compound`
- Produces: `HtnPlanningStrategy` (id="htn") — a `PlanningStrategy` that:
  1. Receives a compound PlanItem with `planningStrategy = "htn"`
  2. Calls `DecompositionStrategy.decompose()` to break compound task into subtasks
  3. Creates child PlanItems via `CasePlanModel.addChild()`
  4. Delegates child dispatch to child strategies

- [ ] **Step 1: Write test — HTN strategy decomposes a compound task into child PlanItems**
- [ ] **Step 2: Implement**
- [ ] **Step 3: Write test — child compounds with their own strategies dispatch independently**
- [ ] **Step 4: Run all tests**
- [ ] **Step 5: Commit**

---

## Phase 6: Composable Strategy Wiring

**Repo:** engine (engine-planning module)
**Risk:** Low — StrategyResolver already exists; this is wiring
**Rollback:** Revert to internal map
**Verification:** All tests pass; strategies can delegate to each other

### Task 6.1: Wire PlanningStrategyLoopControl to StrategyResolver

**Files:**
- Modify: `engine/planning/.../control/PlanningStrategyLoopControl.java`
- Test: verify strategy resolution via StrategyResolver

**Current state:** PlanningStrategyLoopControl builds its own `Map<String, PlanningStrategy>` from `Instance<PlanningStrategy>`. **Target:** delegate to `StrategyResolver.resolve(PlanningStrategy.class, strategyId)`.

- [ ] **Step 1: Write test — strategy resolved via StrategyResolver**
- [ ] **Step 2: Replace internal map with StrategyResolver injection**
- [ ] **Step 3: Run all tests**
- [ ] **Step 4: Commit**

### Task 6.2: Enable strategy delegation via StrategyResolver

**Files:**
- Modify: `PlanningStrategy` interface — add `default` method or context parameter for accessing StrategyResolver
- Test: strategy A delegates to strategy B

**Interfaces:**
- Produces: strategies can look up sibling strategies by name and delegate

- [ ] **Step 1: Write test — strategy resolves and delegates to another strategy**
- [ ] **Step 2: Implement delegation mechanism**
- [ ] **Step 3: Run tests**
- [ ] **Step 4: Commit**

### Task 6.3: Integration test — mixed strategies in one case

**Files:**
- Test: `engine/planning/.../integration/MixedStrategyIntegrationTest.java`

- [ ] **Step 1: Write integration test**

```java
@Test
void case_with_mixed_strategies_dispatches_correctly() {
    // Compound A: strategy="sequential", children=[task1, task2]
    // Compound B: strategy="htn", root=compoundTask → decomposes to [task3, task4]
    // Free-floating task5 uses case-level strategy (default)
    
    // Verify: task1 dispatched before task2 (sequential)
    // Verify: task3, task4 dispatched after decomposition (htn)
    // Verify: task5 dispatched independently (choreography)
}
```

- [ ] **Step 2: Implement test fixtures**
- [ ] **Step 3: Run and verify**
- [ ] **Step 4: Commit**

---

## Phase 7: Agent Dispatch Wiring + Advanced SPI Implementations

**Repo:** blocks (agentic package) + engine (engine-planning, engine-flow)
**Risk:** Medium — new dispatch paths, but existing AgentInvoker SPI isolates changes
**Rollback:** Revert dispatch implementations; ExternalAgent continues to work
**Verification:** End-to-end test for each AgentRef variant dispatching through engine

Currently only `ExternalAgent` (arbitrary function) works end-to-end through blocks' `AgentInvoker`. The other four `AgentRef` variants (`WorkerAgent`, `ChannelAgent`, `HumanAgent`, `ComposedAgent`) carry type-level intent but no runtime dispatch path through engine. This phase wires them up and adds the remaining strategy implementations.

### Task 7.1: Agent dispatch wiring for AgentRef variants

**Files:**
- Modify: `blocks/.../agentic/model/AgentInvoker.java` — extend or create variant-specific dispatch
- Create: `blocks/.../agentic/model/WorkerAgentInvoker.java` (or integrate into existing invoker)
- Test: dispatch tests per variant

**Interfaces:**
- Consumes: `WorkerExecutionManager` (engine), `MessageService` (qhorus), `WorkBroker` (work-api)
- Produces: dispatch paths for each AgentRef variant:

| AgentRef variant | Dispatches via | What it does |
|---|---|---|
| `WorkerAgent(Worker)` | `WorkerExecutionManager.execute()` | Execute a worker capability through engine's dispatch |
| `ChannelAgent(channelId, handler)` | `MessageService.send()` | Send a message to a qhorus channel |
| `HumanAgent(WorkItemCreateRequest)` | `WorkBroker.createWorkItem()` | Create a human task and await completion |
| `ExternalAgent(fn)` | Direct function call | Already works — no change |
| `ComposedAgent(ExecutionModel)` | Recursive `ExecutionDriver.execute()` | Already works — nested execution |

- [ ] **Step 1: Write test — WorkerAgent dispatches through WorkerExecutionManager**

```java
@Test
void workerAgent_dispatches_via_execution_manager() {
    Worker mockWorker = mock(Worker.class);
    WorkerExecutionManager mockManager = mock(WorkerExecutionManager.class);
    AgentRef agent = AgentRef.worker(mockWorker);

    // invoke through AgentInvoker
    // verify WorkerExecutionManager.execute() called with correct worker
}
```

- [ ] **Step 2: Implement WorkerAgent dispatch**
- [ ] **Step 3: Write test — ChannelAgent dispatches through MessageService**
- [ ] **Step 4: Implement ChannelAgent dispatch**
- [ ] **Step 5: Write test — HumanAgent creates work item and awaits**

HumanAgent is async — creates a work item via `WorkBroker`, then blocks (or returns `Uni`) until the human completes it. The completion signal comes through the engine's event bus.

- [ ] **Step 6: Implement HumanAgent dispatch**
- [ ] **Step 7: Run all tests**
- [ ] **Step 8: Commit**

```
feat: wire AgentRef dispatch — WorkerAgent, ChannelAgent, HumanAgent

Each AgentRef variant now dispatches through the appropriate engine/platform
service rather than requiring manual ExternalAgent function wrappers.

Refs casehubio/blocks#60
```

### Task 7.2: Update LlmSelectedRouting for unified type hierarchy

**Files:**
- Modify: `blocks/.../agentic/routing/LlmSelectedRouting.java`
- Test: `blocks/.../agentic/routing/LlmSelectedRoutingTest.java`

**Interfaces:**
- Consumes: `AgentProvider` (platform-agent-api), `RoutingCandidate`, `AgentRef`
- Produces: updated `LlmSelectedRouting` that works with the promoted `ExecutorRef`-based types

`LlmSelectedRouting` already exists but may reference types that changed in Phase 5 (DecompositionContext type narrowing). Verify it compiles and works after the migration. If `RoutingCandidate` was removed from the decomposition path, routing-level use of `RoutingCandidate` must be verified separately.

- [ ] **Step 1: Verify LlmSelectedRouting compiles after Phase 5 changes**
- [ ] **Step 2: Update if needed — ensure it works with `ExecutorRef`-based routing context**
- [ ] **Step 3: Run routing tests**
- [ ] **Step 4: Commit**

### Task 7.3: GoalOrientedDecomposition (GOAP)

**Files:**
- Create: `blocks/.../agentic/decomposition/GoalOrientedDecomposition.java`
- Test: `blocks/.../agentic/decomposition/GoalOrientedDecompositionTest.java`

**Interfaces:**
- Consumes: `DecompositionStrategy<T>` (engine-api, promoted in Phase 5), agent I/O declarations (capability schemas)
- Produces: `GoalOrientedDecomposition` — backward-chaining from goal state, building a plan from agent capabilities' declared inputs/outputs

**Depends on:** agent capabilities declaring I/O schemas. If schema declarations don't exist yet, this task creates the minimal SPI needed (`AgentCapabilitySchema` or similar) and implements GOAP against it.

**Design note (from spec Q2):** "Goal" collides with existing `Goal`/`GoalKind`/`GoalBasedCompletion` in engine. Name this `GoalOrientedDecomposition` (not `GoalPlanner` or `GoalDecomposition`).

- [ ] **Step 1: Write test — GOAP decomposes a goal into a plan using agent capabilities**

```java
@Test
void goap_finds_plan_from_goal_to_initial_state() {
    // Agent A: input=RawData, output=ProcessedData
    // Agent B: input=ProcessedData, output=Report
    // Goal state: Report exists
    // Initial state: RawData exists
    // Expected plan: A → B (backward chain: Report needs B, B needs ProcessedData, ProcessedData needs A)
}
```

- [ ] **Step 2: Implement GoalOrientedDecomposition**
- [ ] **Step 3: Write test — no path found throws NoMethodMatchedException**
- [ ] **Step 4: Run tests**
- [ ] **Step 5: Commit**

### Task 7.4: DispositionAwareRouting (Belbin/DISC)

**Files:**
- Create: `blocks/.../agentic/routing/DispositionAwareRouting.java`
- Test: `blocks/.../agentic/routing/DispositionAwareRoutingTest.java`

**Interfaces:**
- Consumes: `RoutingStrategy<T>` (blocks), `AgentDescriptor` (eidos-api)
- Produces: `DispositionAwareRouting` — scores candidates based on personality/disposition profile from eidos

**Depends on:** eidos `AgentDescriptor` having disposition data. If not available, this task defines the expected disposition interface and implements routing against it. The eidos integration is a separate concern.

**Design note (from spec RQ5):** Composition mechanism not yet decided — decorator, filter, or scoring factor. Recommend: scoring factor that blends with other routing signals (like trust score). The routing pipeline already supports multi-factor scoring via `RoutingSignalProvider`.

- [ ] **Step 1: Write test — disposition-aware routing scores candidates by profile match**
- [ ] **Step 2: Implement as RoutingSignalProvider (composable, not standalone)**
- [ ] **Step 3: Run tests**
- [ ] **Step 4: Commit**

### Task 7.5: Update ConvergenceTermination for compound PlanItem integration

**Files:**
- Modify: `blocks/.../agentic/termination/ConvergenceTermination.java`
- Modify: `blocks/.../agentic/termination/JudgeConvergence.java`
- Test: update termination tests

**Interfaces:**
- Consumes: `ConvergencePolicy` (conversation package), `TerminationCondition<T>`
- Produces: updated convergence-based termination that bridges to compound PlanItem completion semantics when running under engine planning

`ConvergenceTermination` already bridges `ConvergencePolicy` → `TerminationCondition`. This task verifies it works correctly when the execution context is a compound PlanItem (Phase 3D) rather than a standalone blocks execution.

- [ ] **Step 1: Write test — convergence termination signals compound completion**
- [ ] **Step 2: Update if needed**
- [ ] **Step 3: Commit**

---

## Phase 8: Quarkus Flow Backend

**Repo:** engine (engine-flow module) + blocks (pattern builders)
**Risk:** Medium — builds on existing Flow infrastructure, but new code path
**Rollback:** Pattern builders continue to use custom driver backend (no regression)
**Verification:** Workflow-shaped patterns execute through Flow; non-workflow patterns still use custom drivers

For workflow-shaped patterns (`sequence()`, `parallel()`, `loop()`, `conditional()`), generate Serverless Workflow definitions from `ExecutionModel<T>` and execute via the existing `FlowWorkerFunctionHandler`. Non-workflow patterns (supervisor, debate, voting, HTN) continue to use blocks' custom `OrchestratedDriver`.

**Existing infrastructure:**
- `FlowWorkerFunction` wraps a `Workflow` as a `WorkerFunction`
- `FlowWorkerFunctionHandler` executes workflows via `WorkflowApplication`
- `CasehubFlow.dispatch()` bridges workflow steps back to engine capabilities
- `FlowExecutionRegistry` tracks in-flight executions

### Task 8.1: ExecutionModel-to-Workflow generator

**Files:**
- Create: `blocks/.../agentic/flow/FlowBackend.java`
- Create: `blocks/.../agentic/flow/WorkflowGenerator.java`
- Test: `blocks/.../agentic/flow/WorkflowGeneratorTest.java`

**Interfaces:**
- Consumes: `ExecutionModel<T>`, pattern builder configuration
- Produces: `WorkflowGenerator` that maps pattern builders to Serverless Workflow definitions

| Pattern Builder | Workflow Mapping |
|---|---|
| `sequence()` | Sequential task list — each step calls `CasehubFlow.dispatch(capability)` |
| `parallel()` | Parallel branches — concurrent dispatch |
| `loop()` | Do-while — repeat until exit condition |
| `conditional()` | Switch — predicate selects branch |

```java
public class WorkflowGenerator {
    public Workflow generate(ExecutionModel<?> model) { ... }
}
```

- [ ] **Step 1: Write test — sequence pattern generates sequential workflow**

```java
@Test
void sequence_generates_sequential_workflow() {
    ExecutionModel<Void> model = Patterns.sequence()
            .agents(agentA, agentB, agentC)
            .build();

    Workflow workflow = new WorkflowGenerator().generate(model);

    // Assert: workflow has 3 sequential steps
    // Assert: each step dispatches to the correct agent's capability
}
```

- [ ] **Step 2: Implement sequence mapping**
- [ ] **Step 3: Write test — parallel pattern generates parallel branches**
- [ ] **Step 4: Implement parallel mapping**
- [ ] **Step 5: Write test — loop pattern generates do-while**
- [ ] **Step 6: Implement loop mapping**
- [ ] **Step 7: Write test — conditional pattern generates switch**
- [ ] **Step 8: Implement conditional mapping**
- [ ] **Step 9: Run all tests**
- [ ] **Step 10: Commit**

### Task 8.2: Backend selection in pattern builders

**Files:**
- Modify: `blocks/.../agentic/pattern/AbstractPatternBuilder.java` — add backend selection
- Modify: `blocks/.../agentic/pattern/SequenceBuilder.java`, `ParallelBuilder.java`, `LoopBuilder.java`, `ConditionalBuilder.java`
- Test: backend selection tests

**Interfaces:**
- Consumes: `WorkflowGenerator`, `FlowWorkerFunctionHandler`
- Produces: `AbstractPatternBuilder.backend(Backend)` method:

```java
enum Backend { CUSTOM_DRIVER, QUARKUS_FLOW }

// On AbstractPatternBuilder:
public B backend(Backend backend) { ... }
```

Workflow-shaped builders (`sequence`, `parallel`, `loop`, `conditional`) default to `QUARKUS_FLOW` when available. Non-workflow builders (`supervisor`, `debate`, `voting`, `htn`) always use `CUSTOM_DRIVER`. The DSL is unchanged — `Patterns.sequence().agents(...).build()` produces `ExecutionModel<T>` regardless of backend.

- [ ] **Step 1: Write test — sequence with QUARKUS_FLOW backend produces workflow execution**
- [ ] **Step 2: Write test — supervisor always uses CUSTOM_DRIVER regardless of setting**
- [ ] **Step 3: Implement backend selection**
- [ ] **Step 4: Run all pattern builder tests**
- [ ] **Step 5: Commit**

### Task 8.3: Flow execution integration test

**Files:**
- Test: `blocks/.../agentic/flow/FlowBackendIntegrationTest.java`

- [ ] **Step 1: Write integration test — sequence pattern executes end-to-end through Flow**

```java
@Test
void sequence_executes_through_flow_backend() {
    // Setup: 3 agents, each does simple transformation
    // Execute via Patterns.sequence().agents(...).backend(QUARKUS_FLOW).execute(input)
    // Verify: all 3 agents called in order, final result correct
    // Verify: FlowExecutionRegistry tracks the execution
}
```

- [ ] **Step 2: Write integration test — mixed case: sequence (Flow) + supervisor (custom driver)**
- [ ] **Step 3: Run and verify**
- [ ] **Step 4: Commit**

---

## Verification Matrix

| Phase | Engine Tests | Blocks Tests | Consumer Tests | Build Stability |
|-------|-------------|-------------|----------------|-----------------|
| 0 | ✅ new DagPlan tests | — | — | `mvn install` engine |
| 1 | ✅ all existing pass | — | — | `mvn test` engine |
| 2 | ✅ all pass + rename | ✅ import updates | ✅ import updates | `mvn install` engine, then consumers |
| 3A | ✅ new PlanItem tests | — | — | `mvn test` engine |
| 3B | ✅ CasePlanModel tests | — | — | `mvn test` engine |
| 3C | ✅ evaluator migration | — | — | `mvn test` engine |
| 3D | ✅ per-compound dispatch | — | — | `mvn test` engine |
| 4 | — | ✅ DagPlan migration | — | `mvn test` blocks |
| 5 | ✅ HTN strategy | ✅ import updates | — | `mvn install` engine, `mvn test` blocks |
| 6 | ✅ strategy wiring | — | — | `mvn test` engine |
| 7 | — | ✅ dispatch + SPI tests | — | `mvn test` blocks |
| 8 | ✅ Flow integration | ✅ backend selection | — | `mvn test` engine + blocks |

**After each phase:** `mvn install -DskipTests` on engine, then `mvn test` on blocks to verify no regressions.

## Risk Register

| Risk | Phase | Mitigation |
|---|---|---|
| PlanItem state machine regression | 3A | Characterisation tests for every state transition before migration |
| CasePlanModel breaking change cascade | 3B | New methods alongside old; deprecate-then-remove |
| Stage event migration breaks event bus consumers | 3C | Dual-dispatch during migration — emit both old and new events |
| ExecutionPlan removal breaks blocks consumers (drafthouse) | 4 | drafthouse uses blocks' conversation package, not agentic directly — verify via `ide_find_references` |
| DecompositionContext type narrowing (RoutingCandidate → ExecutorRef) | 5 | Blocks impls cast back to AgentRef when needed; no data loss |
| Consumer repo coordination | 2 | Phase 2 is mechanical — can be done in a single cross-repo commit session |
| HumanAgent async completion | 7 | HumanAgent dispatch is inherently async (human completes work item) — use Uni/CompletableFuture with timeout |
| GOAP agent capability schemas | 7 | GoalOrientedDecomposition needs I/O schemas on agent capabilities — define minimal SPI if absent |
| Flow backend runtime availability | 8 | Quarkus Flow must be on classpath — degrade gracefully to custom driver when absent |
| Backend selection breaks existing tests | 8 | Default backend unchanged for non-workflow patterns — supervisor/debate/voting/htn always use custom driver |

## Consumer Repo Migration Plan

| Phase | Affected Repos | Change Type |
|---|---|---|
| 2 (Rename) | blocks, openclaw, aml, devtown, life, soc, clinical, iot, drafthouse | Import update: `io.casehub.blackboard` → `io.casehub.engine.planning` |
| 3 (PlanItem) | openclaw, engine runtime | Stage.builder() compatibility + PlanItem type updates |
| 4 (DagPlan) | — (internal to blocks) | None |
| 5 (HTN) | — (engine-api boundary, transparent to consumers) | None |

## Issue Decomposition (suggested GitHub issues)

Each phase should be a child issue of #60:

| Phase | Suggested Issue Title | Scale | Complexity |
|---|---|---|---|
| 0 | feat: sequentialMerge on DagPlan + engine-common transitivity | S | Low |
| 1 | refactor: retire ChoreographyLoopControl | S | Low |
| 2 | refactor: rename blackboard → engine-planning | M | Low |
| 3A | feat: sealed PlanItem hierarchy — Primitive and Compound records | L | High |
| 3B | feat: CasePlanModel API — compound lifecycle + state management | M | High |
| 3C | refactor: Stage infrastructure → compound PlanItem | L | High |
| 3D | feat: per-compound strategy dispatch | M | Med |
| 4 | refactor: ExecutionPlan → DagPlan in blocks | M | Med |
| 5 | feat: promote HTN decomposition SPI to engine-api | M | Med |
| 6 | feat: composable strategy wiring via StrategyResolver | S | Low |
| 7 | feat: agent dispatch wiring + advanced SPI implementations | L | Med |
| 8 | feat: Quarkus Flow backend for workflow-shaped patterns | M | Med |

## Open Questions to Resolve Before Execution

1. **Phase 2 coordination:** trebleel needs to be notified before the rename. When?
2. **PlanItemStore design decision (Phase 3A.3):** Denormalized (plan definition + execution state co-located in one record) vs split stores? Denormalized is simpler migration; split matches domain model. Recommend denormalized first, refactor later if needed.
3. **TaskNode.PlanElement relationship (RQ3 from spec):** Should `TaskNode` implement `PlanElement`? `PlanElement` is a marker interface. Recommend: no — they serve different purposes (HTN input vs engine plan graph node). Keep separate.
4. **Compound PlanItem naming during migration:** Using `NewPlanItem` temporarily to avoid collision with existing `PlanItem` class. Rename to `PlanItem` in Task 3D.4 once old class is deleted.

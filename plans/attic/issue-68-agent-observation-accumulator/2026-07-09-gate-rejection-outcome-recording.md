# Gate Rejection Outcome Recording — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #36 — feat: record gate-rejected outcomes for routing feedback
**Issue group:** #36

**Goal:** Record gate-rejected and gate-expired outcomes through `RoutingOutcomeRecorder` so the CBR feedback loop captures negative signals from oversight gate resolutions.

**Architecture:** Add a `RoutingOutcome` enum to engine-api replacing the free-form String. Extend `PendingActionGate` with `bindingName` and `capabilityName` to carry routing context through gate lifecycle. Inject `RoutingOutcomeRecorder` into gate resolution handlers. Update `CbrRoutingPromptSection` to render per-outcome distribution.

**Tech Stack:** Java 21, Quarkus (CDI), Mutiny (reactive), Mockito/JUnit 5/AssertJ (test)

## Global Constraints

- engine changes first — `mvn install -DskipTests` before blocks can compile
- `RoutingOutcome` enum is the canonical outcome vocabulary — no free-form strings
- All gate handlers use pre-mutation context snapshots for recording
- Fire-and-forget subscription pattern for recorder calls (consistent with existing)

---

### Task 1: RoutingOutcome enum + RoutingOutcomeRecorder SPI change (engine-api)

**Files:**
- Create: `engine/api/src/main/java/io/casehub/api/spi/routing/RoutingOutcome.java`
- Modify: `engine/api/src/main/java/io/casehub/api/spi/routing/RoutingOutcomeRecorder.java` (line 36, `record()` signature)
- Modify: `engine/api/src/main/java/io/casehub/api/spi/routing/ExperiencePlanStep.java` (line 38, Javadoc)

**Interfaces:**
- Produces: `RoutingOutcome` enum with `SUCCESS`, `FAILURE`, `GATE_REJECTED`, `GATE_EXPIRED`
- Produces: `RoutingOutcomeRecorder.record()` taking `RoutingOutcome outcome` instead of `String executionOutcome`

- [ ] **Step 1: Create `RoutingOutcome` enum**

```java
// engine/api/src/main/java/io/casehub/api/spi/routing/RoutingOutcome.java
package io.casehub.api.spi.routing;

/**
 * Outcome of a routing decision — recorded by {@link RoutingOutcomeRecorder} to feed back into
 * routing strategies (e.g. CBR-enriched routing).
 *
 * <p>{@link #SUCCESS} and {@link #FAILURE} are recorded from the worker completion path in
 * {@code WorkflowExecutionCompletedHandler}. {@link #GATE_REJECTED} and {@link #GATE_EXPIRED}
 * are recorded directly from the gate resolution handlers.
 */
public enum RoutingOutcome {
  /** Worker completed successfully (including gate-approved re-dispatch). */
  SUCCESS,
  /** Worker returned a non-success outcome (Declined, Failed, or Expired). */
  FAILURE,
  /** Worker's planned action was rejected by a human via the oversight gate. */
  GATE_REJECTED,
  /** Worker's planned action gate expired without review. */
  GATE_EXPIRED
}
```

- [ ] **Step 2: Update `RoutingOutcomeRecorder.record()` signature**

Change `String executionOutcome` → `RoutingOutcome outcome` at line 36.

Update the Javadoc to document all four outcomes and reflect that gate outcomes
are recorded from gate resolution handlers:

```java
/**
 * Record a routing outcome.
 *
 * <p>Called by the engine after a worker completes execution ({@link RoutingOutcome#SUCCESS},
 * {@link RoutingOutcome#FAILURE}) and by gate resolution handlers when a gate is rejected
 * ({@link RoutingOutcome#GATE_REJECTED}) or expires ({@link RoutingOutcome#GATE_EXPIRED}).
 * Gate-approved outcomes record on the re-dispatch completion path as {@code SUCCESS}.
 *
 * @param context the routing context at the time of the decision
 * @param workerId the worker that was selected and executed
 * @param bindingName the case definition binding that dispatched the worker
 * @param outcome the routing outcome
 * @param executionDuration wall-clock duration of the worker execution; nullable when the
 *     engine does not track dispatch timestamps or for gate outcomes
 * @return a Uni completing when the record is persisted
 */
Uni<Void> record(
    AgentRoutingContext context,
    String workerId,
    String bindingName,
    RoutingOutcome outcome,
    @Nullable Duration executionDuration);
```

- [ ] **Step 3: Update `ExperiencePlanStep` Javadoc**

Change `@param stepOutcome` from `"the worker's outcome (SUCCESS, FAILURE, DECLINED, etc.)"` to:

```
@param stepOutcome the routing outcome — one of {@link RoutingOutcome} enum values
    ({@code SUCCESS}, {@code FAILURE}, {@code GATE_REJECTED}, {@code GATE_EXPIRED}).
    Stored as the enum's {@code name()} string.
```

- [ ] **Step 4: Verify engine-api compiles**

Run: `mvn --batch-mode -pl api -am compile` from engine root.
Expected: BUILD SUCCESS (no implementations exist in api module).

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/engine add api/src/main/java/io/casehub/api/spi/routing/RoutingOutcome.java api/src/main/java/io/casehub/api/spi/routing/RoutingOutcomeRecorder.java api/src/main/java/io/casehub/api/spi/routing/ExperiencePlanStep.java
git -C /Users/mdproctor/claude/casehub/engine commit -m "feat(#36): add RoutingOutcome enum, update RoutingOutcomeRecorder SPI signature

Refs casehubio/blocks#36"
```

---

### Task 2: Extend PendingActionGate + wire gate-open data (engine-common + runtime)

**Files:**
- Modify: `engine/common/src/main/java/io/casehub/engine/common/internal/model/PendingActionGate.java` (line 38, record fields)
- Modify: `engine/runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java` (line 533, PendingActionGate constructor call)

**Interfaces:**
- Consumes: `extractCapabilityTag(CaseInstance, Worker, String)` (existing private method at line 751)
- Produces: `PendingActionGate.bindingName()` and `PendingActionGate.capabilityName()` — nullable Strings

- [ ] **Step 1: Add fields to `PendingActionGate`**

Add `@Nullable String bindingName` and `@Nullable String capabilityName` as the last two record components:

```java
public record PendingActionGate(
    long gateId,
    String workerId,
    String idempotency,
    Map<String, Object> deferredOutput,
    PlannedAction plannedAction,
    @Nullable String bindingName,
    @Nullable String capabilityName) {}
```

Add `import org.jspecify.annotations.Nullable;` to imports.

- [ ] **Step 2: Update constructor call in `WorkflowExecutionCompletedHandler.handleGate()`**

At line 533-539, the `PendingActionGate` is constructed. Compute `capabilityName` before construction and pass both through:

```java
// Before the groupsUni.chain() block, compute the capability name
final String capabilityName = extractCapabilityTag(caseInstance, worker, event.bindingName());

// Then in the chain, update the constructor call:
caseInstance.setPendingActionGate(
    new PendingActionGate(
        gateEventLog.id,
        worker.name(),
        event.idempotency(),
        rawOutput,
        plannedAction,
        event.bindingName(),
        capabilityName));
```

Note: `capabilityName` computation must happen BEFORE `groupsUni.chain()` because it accesses `caseInstance` and `worker` which are final locals from the enclosing method, not the lambda. Place `extractCapabilityTag` call after `groupsUni` is declared (line 520) but before the `.chain()` call.

- [ ] **Step 3: Verify engine compiles**

Run: `mvn --batch-mode -pl common,runtime -am compile` from engine root.
Expected: BUILD SUCCESS.

- [ ] **Step 4: Run existing gate tests**

Run: `mvn --batch-mode -pl runtime -Dtest=ActionGateResolutionTest test` from engine root.
Expected: All 3 tests pass (gateApproved, gateRejected, terminalStateGuard).

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/engine add common/src/main/java/io/casehub/engine/common/internal/model/PendingActionGate.java runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java
git -C /Users/mdproctor/claude/casehub/engine commit -m "feat(#36): extend PendingActionGate with bindingName + capabilityName

Refs casehubio/blocks#36"
```

---

### Task 3: Gate handler outcome recording + approved handler fix (engine runtime)

**Files:**
- Modify: `engine/runtime/src/main/java/io/casehub/engine/internal/engine/handler/ActionGateRejectedHandler.java` (line 59)
- Modify: `engine/runtime/src/main/java/io/casehub/engine/internal/engine/handler/ActionGateExpiredHandler.java` (line 52)
- Modify: `engine/runtime/src/main/java/io/casehub/engine/internal/engine/handler/ActionGateApprovedHandler.java` (line 53, `refireCompletion` at line 114)
- Modify: `engine/runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java` (line 779, `fireOutcomeRecorder`)
- Modify: `engine/runtime/src/test/java/io/casehub/engine/ActionGateResolutionTest.java` (add outcome recording assertions)

**Interfaces:**
- Consumes: `PendingActionGate.bindingName()`, `PendingActionGate.capabilityName()` (from Task 2)
- Consumes: `RoutingOutcome` enum (from Task 1)
- Consumes: `RoutingOutcomeRecorder.record()` (updated signature from Task 1)

- [ ] **Step 1: Write failing test — gate rejection records outcome**

In `ActionGateResolutionTest.java`, add a mock `RoutingOutcomeRecorder` and verify it's called on rejection:

```java
@Mock RoutingOutcomeRecorder outcomeRecorder;

@Test
void gateRejected_recordsGateRejectedOutcome() {
    // setup: enable PlannedAction, submit task, wait for gate pending
    ResolutionClassifier.nextDecision = new RiskDecision.GateRequired(
        "needs-review", true, null, null, null);
    ResolutionCaseHub.declareAction.set(true);
    UUID caseId = startCase();

    // wait for gate to be created
    await().atMost(5, SECONDS).until(() -> !GateCodecStubs.capturedSchedule.isEmpty());

    // reject the gate
    var scheduleEvent = GateCodecStubs.capturedSchedule.getFirst();
    eventBus.publish(EventBusAddresses.ACTION_GATE_REJECTED,
        new ActionGateRejectedEvent(caseId, scheduleEvent.gateId(), null, "reviewer"));

    // verify outcome recorded
    await().atMost(5, SECONDS).untilAsserted(() ->
        verify(outcomeRecorder).record(
            any(AgentRoutingContext.class),
            eq("test-worker"),
            anyString(),
            eq(RoutingOutcome.GATE_REJECTED),
            isNull()));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode -pl runtime -Dtest=ActionGateResolutionTest#gateRejected_recordsGateRejectedOutcome test`
Expected: FAIL — `outcomeRecorder` not injected into handler, never called.

- [ ] **Step 3: Update `WorkflowExecutionCompletedHandler.fireOutcomeRecorder()`**

Change parameter from `String outcomeString` to `RoutingOutcome outcome` at line 779. Update both call sites:
- Line 136: `"SUCCESS"` → `RoutingOutcome.SUCCESS`
- Line 331: `"FAILURE"` → `RoutingOutcome.FAILURE`

Add import: `import io.casehub.api.spi.routing.RoutingOutcome;`

- [ ] **Step 4: Add outcome recording to `ActionGateRejectedHandler`**

Add injection:
```java
@Inject
jakarta.enterprise.inject.Instance<io.casehub.api.spi.routing.RoutingOutcomeRecorder>
    outcomeRecorder;
```

In `onActionGateRejected()`, capture context snapshot at top (BEFORE gate clearance):
```java
final JsonNode contextSnapshot = instance.getCaseContext().snapshot().asJsonNode();
```

After `workerStatusListener.onWorkerCompleted(...)`, before the EventLog write, add:
```java
if (!outcomeRecorder.isUnsatisfied() && gate.capabilityName() != null) {
  var ctx = new AgentRoutingContext(
      instance.getUuid(), gate.capabilityName(), contextSnapshot,
      instance.tenancyId, List.of());
  outcomeRecorder.get()
      .record(ctx, gate.workerId(), gate.bindingName(),
              RoutingOutcome.GATE_REJECTED, null)
      .subscribe().with(
          ignored -> {},
          err -> LOG.warnf(err,
              "Outcome recording failed for gate-rejected caseId=%s worker=%s",
              instance.getUuid(), gate.workerId()));
}
```

Add imports:
```java
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingOutcome;
import io.casehub.api.spi.routing.RoutingOutcomeRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
```

- [ ] **Step 5: Add identical recording to `ActionGateExpiredHandler`**

Same pattern as Step 4 but using `RoutingOutcome.GATE_EXPIRED` and adjusted log message:
```java
"Outcome recording failed for gate-expired caseId=%s worker=%s"
```

- [ ] **Step 6: Fix `ActionGateApprovedHandler.refireCompletion()`**

At line 130, change:
```java
WorkflowExecutionCompleted.approved(
    instance, worker, gate.idempotency(), gate.deferredOutput(), null);
```
to:
```java
WorkflowExecutionCompleted.approved(
    instance, worker, gate.idempotency(), gate.deferredOutput(), gate.bindingName());
```

- [ ] **Step 7: Run all gate tests**

Run: `mvn --batch-mode -pl runtime -Dtest=ActionGateResolutionTest test`
Expected: All tests pass including the new one.

- [ ] **Step 8: Run full engine test suite**

Run: `mvn --batch-mode test` from engine root.
Expected: BUILD SUCCESS — no regressions.

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/engine add runtime/src/main/java/io/casehub/engine/internal/engine/handler/ runtime/src/test/java/io/casehub/engine/ActionGateResolutionTest.java
git -C /Users/mdproctor/claude/casehub/engine commit -m "feat(#36): record gate-rejected/expired outcomes, fix approved bindingName

Refs casehubio/blocks#36"
```

- [ ] **Step 10: Install engine to local Maven repo**

Run: `mvn --batch-mode install -DskipTests` from engine root.
Expected: BUILD SUCCESS — blocks can now compile against updated engine-api.

---

### Task 4: Update CbrRoutingOutcomeRecorder + tests (blocks)

**Files:**
- Modify: `src/main/java/io/casehub/blocks/routing/agent/CbrRoutingOutcomeRecorder.java` (line 42, `record()` signature)
- Modify: `src/test/java/io/casehub/blocks/routing/agent/CbrRoutingOutcomeRecorderTest.java`

**Interfaces:**
- Consumes: `RoutingOutcome` enum (from Task 1, via engine-api dependency)
- Produces: `PlanCbrCase` with `outcome.name()` as outcome string

- [ ] **Step 1: Write failing test — GATE_REJECTED outcome stored**

Add to `CbrRoutingOutcomeRecorderTest.java`:

```java
@Test
void recordsGateRejected() {
    when(cbrStore.store(any(), anyString(), anyString(), any(), anyString(), anyString()))
            .thenReturn("stored-id");

    recorder.record(context(), "agent-c", "binding-z", RoutingOutcome.GATE_REJECTED, null)
            .await().indefinitely();

    var caseCaptor = ArgumentCaptor.forClass(CbrCase.class);
    verify(cbrStore).store(caseCaptor.capture(), anyString(),
            eq("agent-routing"), any(MemoryDomain.class), anyString(), anyString());

    var stored = (PlanCbrCase) caseCaptor.getValue();
    assertThat(stored.outcome()).isEqualTo("GATE_REJECTED");
    assertThat(stored.planTrace().getFirst().stepOutcome()).isEqualTo("GATE_REJECTED");
}
```

Add import: `import io.casehub.api.spi.routing.RoutingOutcome;`

- [ ] **Step 2: Update existing tests to use `RoutingOutcome` enum**

Change all `record()` calls:
- Line 50: `"SUCCESS"` → `RoutingOutcome.SUCCESS`
- Line 71: `"FAILURE"` → `RoutingOutcome.FAILURE`
- Line 86: `"SUCCESS"` → `RoutingOutcome.SUCCESS`
- Line 95: `"SUCCESS"` → `RoutingOutcome.SUCCESS`

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -Dtest=CbrRoutingOutcomeRecorderTest`
Expected: FAIL — `record()` still takes `String`, enum doesn't match.

- [ ] **Step 4: Update `CbrRoutingOutcomeRecorder.record()` implementation**

Change signature from `String executionOutcome` to `RoutingOutcome outcome` at line 42.

Update body to use `outcome.name()`:
```java
var trace = new PlanTrace(
        bindingName, context.capabilityName(),
        workerId, outcome.name(), 0, Map.of());
var cbrCase = new PlanCbrCase(
        problem != null ? problem : context.capabilityName(),
        "Routed to " + workerId,
        outcome.name(), null,
        featureExtractor.extractFeatures(context),
        List.of(trace));
```

Add import: `import io.casehub.api.spi.routing.RoutingOutcome;`

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -Dtest=CbrRoutingOutcomeRecorderTest`
Expected: PASS — all 5 tests (4 existing + 1 new).

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add src/main/java/io/casehub/blocks/routing/agent/CbrRoutingOutcomeRecorder.java src/test/java/io/casehub/blocks/routing/agent/CbrRoutingOutcomeRecorderTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#36): update CbrRoutingOutcomeRecorder for RoutingOutcome enum

Refs casehubio/blocks#36"
```

---

### Task 5: Update CbrRoutingPromptSection per-outcome rendering + tests (blocks)

**Files:**
- Modify: `src/main/java/io/casehub/blocks/routing/agent/CbrRoutingPromptSection.java` (lines 97, 141-148, `format()` method)
- Modify: `src/test/java/io/casehub/blocks/routing/agent/CbrRoutingPromptSectionTest.java`

**Interfaces:**
- Consumes: `PlanTrace.stepOutcome()` — now may contain `"GATE_REJECTED"`, `"GATE_EXPIRED"` in addition to `"SUCCESS"`, `"FAILURE"`

- [ ] **Step 1: Write failing test — mixed outcomes render per-outcome counts**

Add to `CbrRoutingPromptSectionTest.java`:

```java
@Test
void rendersMixedOutcomes_perOutcomeCounts() {
    var traces = List.of(
        new PlanTrace("b1", "analysis", "agent-a", "SUCCESS", 0, Map.of()),
        new PlanTrace("b2", "analysis", "agent-a", "GATE_REJECTED", 0, Map.of()),
        new PlanTrace("b3", "analysis", "agent-a", "FAILURE", 0, Map.of()));
    var planCase = new PlanCbrCase("problem", "solution", "SUCCESS", null, Map.of(), traces);
    var scored = new ScoredCbrCase<CbrCase>(planCase, 0.9);

    when(cbrStore.query(any(), anyInt(), anyDouble(), any(), any()))
            .thenReturn(List.of(scored));

    var result = section.render(context(), List.of(candidate("agent-a")));

    assertThat(result).contains("1 SUCCESS, 1 FAILURE, 1 GATE_REJECTED");
    assertThat(result).doesNotContain("2 FAILURE");
    assertThat(result).contains("33% success");
}
```

Add imports for `ScoredCbrCase`, `PlanTrace`, `PlanCbrCase`, `Map`.

- [ ] **Step 2: Write failing test — summary-detail consistency**

```java
@Test
void summaryDetailConsistency_gateRejectedShownInBoth() {
    var traces = List.of(
        new PlanTrace("b1", "analysis", "agent-a", "GATE_REJECTED", 0, Map.of()));
    var planCase = new PlanCbrCase("problem", "solution", "GATE_REJECTED", null, Map.of(), traces);
    var scored = new ScoredCbrCase<CbrCase>(planCase, 0.85);

    when(cbrStore.query(any(), anyInt(), anyDouble(), any(), any()))
            .thenReturn(List.of(scored));

    var result = section.render(context(), List.of(candidate("agent-a")));

    // Summary should show GATE_REJECTED, not FAILURE
    assertThat(result).contains("1 GATE_REJECTED");
    assertThat(result).doesNotContain("1 FAILURE");
    // Detail line shows raw outcome
    assertThat(result).contains("→ GATE_REJECTED");
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -Dtest=CbrRoutingPromptSectionTest`
Expected: FAIL — format() still renders binary SUCCESS/FAILURE counts.

- [ ] **Step 4: Update `format()` to use per-outcome distribution**

Change the stats structure from `int[]` (successes, total) to `Map<String, Integer>` per agent:

```java
// Replace line 97:
Map<String, Map<String, Integer>> agentOutcomes = new HashMap<>(); // agent → {outcome → count}

// Replace lines 110-115 (inside the trace loop):
var outcomes = agentOutcomes.computeIfAbsent(
        trace.workerName(), k -> new LinkedHashMap<>());
outcomes.merge(trace.stepOutcome(), 1, Integer::sum);

// Replace lines 141-148 (the rendering block):
sb.append("\nOutcomes by agent:\n");
agentOutcomes.forEach((agent, outcomes) -> {
    int total = outcomes.values().stream().mapToInt(Integer::intValue).sum();
    int successes = outcomes.getOrDefault("SUCCESS", 0);
    int pct = total > 0 ? (int) Math.round(100.0 * successes / total) : 0;
    var parts = new StringJoiner(", ");
    outcomes.forEach((outcome, count) -> parts.add(count + " " + outcome));
    sb.append("  \"%s\": %d cases — %s (%d%% success)%n"
            .formatted(agent, total, parts, pct));
});
```

Add import: `import java.util.LinkedHashMap;` and `import java.util.StringJoiner;`

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -Dtest=CbrRoutingPromptSectionTest`
Expected: PASS — all tests including the two new ones.

- [ ] **Step 6: Run full blocks test suite**

Run: `mvn --batch-mode test`
Expected: BUILD SUCCESS — no regressions.

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add src/main/java/io/casehub/blocks/routing/agent/CbrRoutingPromptSection.java src/test/java/io/casehub/blocks/routing/agent/CbrRoutingPromptSectionTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#36): per-outcome distribution in CbrRoutingPromptSection

Refs casehubio/blocks#36"
```

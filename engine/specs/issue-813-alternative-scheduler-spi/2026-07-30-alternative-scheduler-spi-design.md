# Alternative Scheduler SPI — Design Spec

**Issue:** engine#813
**Date:** 2026-07-30
**Status:** Draft

## Problem

The engine's scheduler SPIs (`JobScheduler`, `WorkerExecutionManager`) are designed for pluggable scheduler backends, but two leaks couple them to Quartz:

1. `ScheduledJobRequest.jobClass` — vestigial `Class<?>` field. No caller sets it; `QuartzJobScheduler.resolveJobClass()` uses an implicit `triggerType` string convention instead. Dead API surface on a common-module class.
2. `CronSchedule` accepts Quartz 6-field cron expressions (seconds field, `?` character)

Additional Quartz terminology leaks into SPI interfaces and common code: `WorkerExecutionManager.getActiveCaseIds()` mentions "Quartz jobs", `SchedulerService` references Quartz module classes in `@see` tags, `ScheduleTrigger.cron()` says "Quartz cron expression", and `CronSchedule` says "Quartz cron expression".

These leaks prevent a second scheduler implementation from slotting in cleanly.

Additionally, the engine lacks a modern, lightweight scheduler alternative. The production deployment (`persistence-hibernate`) uses `quarkus.quartz.store-type=jdbc-cmt` with 11+ Quartz tables. db-scheduler (Apache 2.0, ~1,600 stars, actively maintained) offers the same capabilities with 1 table, a modern fluent API, and built-in clustering via database polling. For the in-memory profile (`persistence-memory`, `store-type=ram`), db-scheduler can use an in-memory H2 database for equivalent lightweight deployment.

## Decision

Fix both SPI leaks and build a `scheduler-dbscheduler` module in the same issue. The db-scheduler module validates the SPI changes — if it can't implement the SPIs cleanly, the SPI design is wrong.

Both scheduler modules coexist. An installation selects one — no mix and match. Quartz stays until db-scheduler proves itself across all four job types.

## Alternatives Considered

Issue #813 requires an investigation of alternatives before committing to a specific scheduler backend. The following alternatives were evaluated:

### Virtual thread executor with `ScheduledExecutorService`

A `ScheduledExecutorService` backed by virtual threads provides delay-based scheduling with modern Java concurrency. However:
- **No persistence.** Scheduled work is lost on restart. The production deployment (`persistence-hibernate`, `store-type=jdbc-cmt`) relies on persistent scheduling — a virtual thread executor cannot replace this path.
- **No cron support.** `ScheduledExecutorService` supports fixed-rate and fixed-delay but not cron expressions. The engine uses cron for scheduled triggers.
- **No clustering.** No cross-node coordination. Production deployments need this for HA.
- **Quartz worker execution already uses virtual threads** (`@RunOnVirtualThread` on handlers). The scheduling layer and the execution layer are distinct — virtual threads help with execution, not with scheduling.

**Verdict:** Cannot replace Quartz for production deployments. Only viable for the in-memory profile, where Quartz-with-RAM already works.

### In-memory priority queue with retry semantics

A custom `PriorityBlockingQueue<ScheduledTask>` with a polling thread:
- Same limitations as the virtual thread executor: no persistence, no clustering, no cron.
- Adds maintenance burden of a custom scheduler implementation.
- Retry semantics already exist in `RetryPolicies` — the scheduling layer doesn't need to own retry.

**Verdict:** Strictly worse than `ScheduledExecutorService` with more custom code to maintain.

### db-scheduler

A lightweight JDBC-backed scheduler with 1 table:
- **Persistence.** Survives restarts. Replaces Quartz JDBC (11+ tables) with 1 table.
- **Cron support.** Spring-style 5-field cron (standard, unlike Quartz's 6-field extension).
- **Built-in clustering.** Database polling provides HA without additional infrastructure.
- **Lightweight in-memory path.** H2 in-memory gives the same developer experience as Quartz-with-RAM.
- **Modern API.** Fluent task definition, no XML/annotations ceremony.

**Verdict:** Best fit. Matches all production requirements with less infrastructure cost. Validates the SPI design by being a genuinely different implementation.

### Keep Quartz only (SPI fixes without second implementation)

Approach A in the decision — fixes leaks but provides no validation that the SPI is genuinely backend-agnostic.

**Verdict:** Rejected. The SPI design cannot be verified without a second implementation.

## Approach: Approach B — Fix SPI leaks + build db-scheduler module together

Selected over:
- **A (SPI fixes only):** No second implementation to validate the SPI design
- **C (New unified SPI):** Unnecessary — the current SPIs are sound, they just have two small leaks

## SPI Leak Fixes

### Remove `jobClass` from `ScheduledJobRequest`

The `jobClass` field on `ScheduledJobRequest` is vestigial dead code — no caller sets it. `getJobClass()` has exactly 2 call sites, both inside `QuartzJobScheduler` itself (lines 130 and 232). All callers (`SchedulerService.scheduleWorker()`, `SchedulerService.scheduleConditionalWorker()`, `MilestoneActivatedEventHandler.scheduleSlaTimeoutJob()`) rely on an implicit `triggerType` string convention in the data map, and `QuartzJobScheduler.resolveJobClass()` maps it internally.

Replace the implicit `triggerType` string convention with a first-class typed field.

**New enum** in `io.casehub.engine.common.internal.scheduler`:

```java
public enum JobType {
    SCHEDULED_TRIGGER_UNCONDITIONAL,
    SCHEDULED_TRIGGER_CONDITIONAL,
    MILESTONE_SLA_TIMEOUT
}
```

**`ScheduledJobRequest` changes:**
- Remove `Class<?> jobClass` field
- Add `JobType jobType` field (required)
- Remove `Builder.jobClass(Class<?>)` method
- Add `Builder.jobType(JobType)` method

**Caller migration:**
- `SchedulerService.scheduleWorker()` — stop putting `triggerType` in data map, use `.jobType(SCHEDULED_TRIGGER_UNCONDITIONAL)`
- `SchedulerService.scheduleConditionalWorker()` — use `.jobType(SCHEDULED_TRIGGER_CONDITIONAL)`
- `MilestoneActivatedEventHandler.scheduleSlaTimeoutJob()` — use `.jobType(MILESTONE_SLA_TIMEOUT)`

**Scheduler module migration:**
- `QuartzJobScheduler.resolveJobClass()` — switch on `JobType` instead of parsing `triggerType` from data map. Maps `JobType` → Quartz `Job` class.
- `DbSchedulerJobScheduler` — maps `JobType` → db-scheduler task handler.

### Normalize Cron to 5-Field

`CronSchedule` validates at construction time: exactly 5 space-separated fields, no `?`, no `L`/`W`/`#`.

Format: `minute hour day-of-month month day-of-week`

**Translation at the edges:**
- **Quartz module:** prepends `0 ` (seconds=0), replaces `*` with `?` in day-of-week field when day-of-month is specific
- **db-scheduler module:** passes through directly (Spring-style cron is 5-field)

**Caller migration:** Existing Quartz-format expressions update at the definition site:
- `"0 */5 * * * ?"` → `"*/5 * * * *"`
- `"0 0 0 * * ?"` → `"0 0 * * *"`

**Javadoc updates:**
- `CronSchedule` record javadoc: `"Quartz cron expression (e.g., \"0 0 * * * ?\" for hourly)"` → `"5-field cron expression (minute hour day-of-month month day-of-week)"`
- `ScheduleTrigger.cron()` javadoc: `@param cronExpression Quartz cron expression` → `@param cronExpression 5-field cron expression (minute hour day-of-month month day-of-week)`

**Schema update:** `CaseDefinition.yaml` schema description changes from "Quartz cron" to "5-field cron (minute hour day-of-month month day-of-week)".

### SPI Javadoc Cleanup

Remove Quartz-specific terminology from SPI interfaces and common code:

- `WorkerExecutionManager.getActiveCaseIds()` — change "Returns the case UUIDs for all Quartz jobs currently executing for this worker" to "Returns the case UUIDs for all tasks currently executing for this worker"
- `SchedulerService` javadoc — remove `@see ScheduledTriggerJob` and `@see ConditionalScheduledTriggerJob` (references to Quartz module classes, unresolvable from `runtime` which has `scheduler-quartz` at test scope only)

## Common Orchestration Extraction

### `WorkerExecutionOrchestrator`

`QuartzWorkerExecutionJob` (280 lines) is mostly scheduler-agnostic domain logic. Only two parts are Quartz-specific: (1) reading from `JobDataMap`/`JobExecutionContext` at entry, and (2) the `implements Job` marker. Everything between — EventLog resolution, CaseInstance recovery, CaseDefinition lookup, worker/capability resolution, context bridge integration, typed input handling, execution mode dispatch, CBR experience deserialization, output transformation, and success/failure publishing — is domain logic that belongs in common code.

Extract into `common/internal/executor/WorkerExecutionOrchestrator`:

```java
@ApplicationScoped
public class WorkerExecutionOrchestrator {
    @Inject WorkerExecutor workerExecutor;
    @Inject CaseDefinitionRegistry caseDefinitionRegistry;
    @Inject WorkerContextProvider workerContextProvider;
    @Inject WorkerExecutionRecoveryService recoveryService;
    @Inject @CrossTenant CrossTenantEventLogRepository eventLogRepository;
    @Inject WorkerExecutionConfig executionConfig;
    @Inject BridgeResolver bridgeResolver;
    @Inject EventBus eventBus;

    public void execute(WorkerTaskData taskData, RetryHandler retryHandler) {
        // All domain logic from QuartzWorkerExecutionJob.execute():
        // EventLog resolution, CaseInstance recovery, CaseDefinition lookup,
        // Worker/Capability resolution, context bridge, typed input,
        // WorkerExecutor.execute(), success publishing, failure routing
    }
}
```

**`WorkerTaskData`** — scheduler-agnostic record in `common/internal/executor/`:

```java
public record WorkerTaskData(
    String eventLogId,
    String inputDataHash,
    UUID caseId,
    String workerId,
    String tenancyId,
    String bindingName,
    UUID signalId
) {}
```

This replaces `WorkerRetryContext` (currently in the Quartz module, with only its `from(JobExecutionContext)` factory being Quartz-specific). The Quartz-specific factory stays in the Quartz module as a static adapter method.

**`RetryHandler`** — functional interface in `common/internal/executor/`:

```java
@FunctionalInterface
public interface RetryHandler {
    void handleFailure(WorkerTaskData taskData, String errorMessage);
}
```

Both scheduler modules become thin adapters:
- **`QuartzWorkerExecutionJob`**: reads `JobDataMap` → builds `WorkerTaskData` → calls `orchestrator.execute(taskData, retryService::handleFailure)`
- **`WorkerExecutionTaskHandler`**: reads db-scheduler task data → builds `WorkerTaskData` → calls `orchestrator.execute(taskData, retryService::handleFailure)`

### `RetryOrchestrator`

`QuartzRetryService` (271 lines) is mostly scheduler-agnostic. Only `rescheduleWorker()` (lines 222-254, ~32 lines) is Quartz-specific — it builds a Quartz `JobDataMap`, `JobDetail`, and `Trigger`. Everything else — failure EventLog persistence, case instance recovery, retry policy resolution, failure counting, `RetryPolicies.evaluate()`, retry state building, and exhaustion event publishing — is domain logic.

Extract into `common/internal/executor/RetryOrchestrator`:

```java
@ApplicationScoped
public class RetryOrchestrator {
    @Inject EventLogRepository eventLogRepository;
    @Inject WorkerExecutionRecoveryService recoveryService;
    @Inject CaseDefinitionRegistry caseDefinitionRegistry;
    @Inject EventBus eventBus;

    public void handleFailure(WorkerTaskData taskData, String errorMessage,
                              RescheduleCallback rescheduleCallback) {
        // Persist failure EventLog
        // Load case, resolve retry policy, count failures
        // Call RetryPolicies.evaluate()
        // Retry → rescheduleCallback.reschedule(taskData, delayMs)
        // Exhaust → publish WORKER_RETRIES_EXHAUSTED
    }
}
```

**`RescheduleCallback`** — functional interface in `common/internal/executor/`:

```java
@FunctionalInterface
public interface RescheduleCallback {
    void reschedule(WorkerTaskData taskData, long delayMs);
}
```

Both scheduler modules become thin adapters (~20 lines each):
- **`QuartzRetryService`**: implements `RetryHandler`, delegates to `retryOrchestrator.handleFailure(taskData, errorMessage, this::rescheduleViaQuartz)`
- **`DbSchedulerRetryService`**: same pattern, with `this::rescheduleViaDbScheduler`

## `scheduler-dbscheduler` Module

### Maven Coordinates

```xml
<artifactId>casehub-engine-scheduler-dbscheduler</artifactId>
```

### Dependencies

- `casehub-engine-common` (compile) — SPIs, domain types, orchestrators
- `casehub-engine-api` (compile) — CaseDefinition, Binding, Worker
- `db-scheduler` (compile) — scheduler library
- `quarkus-agroal` (compile) — DataSource injection
- `quarkus-arc` (compile) — CDI

### Production Classes

| Class | Implements | Role |
|-------|-----------|------|
| `DbSchedulerJobScheduler` | `JobScheduler` | Maps `ScheduledJobRequest` → db-scheduler tasks. Uses single `"scheduled-job"` task name with `JobType` dispatched via task data. Implements `schedule`, `cancel`, `cancelGroup`, `exists`. |
| `DbSchedulerWorkerExecutionManager` | `WorkerExecutionManager` (`@WorkerBackend`) | Submits worker execution as one-time tasks. Tracks active work via db-scheduler query API. |
| `DbSchedulerLifecycle` | CDI producer | Builds `Scheduler` from injected `DataSource` on `StartupEvent`, registers task definitions, starts polling. Stops on `ShutdownEvent`. Produces `SchedulerClient` as `@ApplicationScoped`. |
| `WorkerExecutionTaskHandler` | db-scheduler `ExecutionHandler` | Thin adapter: deserializes `WorkerTaskData` from task data, calls `WorkerExecutionOrchestrator.execute()`. Registered as handler for `"worker-execution"` task. |
| `ScheduledJobDispatchHandler` | db-scheduler `ExecutionHandler` | Registered as the single handler for `"scheduled-job"` task. Reads `JobType` from task data, delegates to the appropriate `ScheduledJobHandler` bean. |
| `ScheduledTriggerTaskHandler` | `ScheduledJobHandler` | Loads case, verifies RUNNING state, publishes `WorkerScheduleEvent`. |
| `ConditionalScheduledTriggerTaskHandler` | `ScheduledJobHandler` | Same as above but evaluates binding `when` condition first. |
| `MilestoneSLATimeoutTaskHandler` | `ScheduledJobHandler` | Checks milestone is still ACTIVE, publishes `MilestoneSLAViolatedEvent`. |
| `DbSchedulerRetryService` | `RetryHandler` | Thin adapter: delegates to `RetryOrchestrator` with db-scheduler reschedule callback. |

### Instance ID and Group Mapping

db-scheduler identifies tasks by `(taskName, instanceId)` — a flat two-dimensional key. The engine's `JobIdentifier` has `(name, group)` — e.g., `JobIdentifier.of("binding-" + bindingName, "case-" + caseId)`. Quartz maps this directly to `JobKey(name, group)` with first-class group queries. db-scheduler has no group concept.

**Convention:** Encode both `JobIdentifier` dimensions into the instance ID as `"{group}:{name}"`.

| SPI method | db-scheduler mapping |
|------------|---------------------|
| `schedule(request)` | `schedulerClient.schedule(TaskInstanceId.of("scheduled-job", jobId.getGroup() + ":" + jobId.getName()), taskData, executionTime)` |
| `cancel(jobId)` | `schedulerClient.cancel(TaskInstanceId.of("scheduled-job", jobId.getGroup() + ":" + jobId.getName()))` — O(1) lookup |
| `exists(jobId)` | `schedulerClient.getScheduledExecution(TaskInstanceId.of("scheduled-job", jobId.getGroup() + ":" + jobId.getName())).isPresent()` |
| `cancelGroup(groupName)` | See below |

**`cancelGroup` strategy:** db-scheduler has no prefix-based query, but `SchedulerClient.getScheduledExecutionsForTask("scheduled-job")` returns all instances for the task. Filter by instance ID prefix `groupName + ":"`, cancel each match:

```java
public Uni<Integer> cancelGroup(String groupName) {
    String prefix = groupName + ":";
    List<ScheduledExecution<Object>> toCancel = new ArrayList<>();
    schedulerClient.getScheduledExecutionsForTask(
        "scheduled-job", Object.class, toCancel::add);
    int count = 0;
    for (var exec : toCancel) {
        if (exec.getTaskInstance().getId().startsWith(prefix)) {
            schedulerClient.cancel(exec.getTaskInstance());
            count++;
        }
    }
    return Uni.createFrom().item(count);
}
```

This is O(n) where n is the total number of scheduled jobs — acceptable since a case has at most a few dozen scheduled triggers. No direct SQL is needed.

**Single task name rationale:** All `JobScheduler`-path tasks (`SCHEDULED_TRIGGER_UNCONDITIONAL`, `SCHEDULED_TRIGGER_CONDITIONAL`, `MILESTONE_SLA_TIMEOUT`) use a single `"scheduled-job"` task name. db-scheduler allows exactly one `ExecutionHandler` per task name, so a `ScheduledJobDispatchHandler` is registered as the handler for `"scheduled-job"`. It reads `JobType` from the task data and delegates to the appropriate `ScheduledJobHandler` bean:

```java
@ApplicationScoped
class ScheduledJobDispatchHandler implements ExecutionHandler<ScheduledJobData> {
    @Inject ScheduledTriggerTaskHandler unconditional;
    @Inject ConditionalScheduledTriggerTaskHandler conditional;
    @Inject MilestoneSLATimeoutTaskHandler slaTimeout;

    void execute(TaskInstance<ScheduledJobData> task, ExecutionContext ctx) {
        switch (task.getData().jobType()) {
            case SCHEDULED_TRIGGER_UNCONDITIONAL -> unconditional.handle(task);
            case SCHEDULED_TRIGGER_CONDITIONAL -> conditional.handle(task);
            case MILESTONE_SLA_TIMEOUT -> slaTimeout.handle(task);
        }
    }
}
```

The three handler classes implement `ScheduledJobHandler` (an engine-internal interface, not db-scheduler's `ExecutionHandler`) and are CDI beans injected into the dispatcher.

This preserves `JobIdentifier`'s single-namespace semantics — `cancel(jobId)` and `exists(jobId)` are O(1) lookups without needing to know the `JobType`. Multiple task names would require trying all 3 names per cancel/exists call.

**Worker execution path** uses a separate task name `"worker-execution"` with a different key convention — the compound key from `WorkerExecutionKeys` (see below).

### Worker Execution Submission Path

`DbSchedulerWorkerExecutionManager.submit()` creates a one-time db-scheduler task:

1. **Task name:** `"worker-execution"` — constant, same for all worker executions.
2. **Instance ID:** Uses `WorkerExecutionKeys.inputDataHash(caseId, workerName, capabilityName, inputData)` — the existing compound key utility in common. Produces `"{caseId}:{workerName}:{capabilityName}:{sha256(inputData)}"`, same key that Quartz uses as `JobKey` name.
3. **Task data:** Serializes `eventLogId`, `workerId`, `caseHubInstanceUuid`, `tenancyId`, `bindingName`, `signalId` as a JSON map in the task's `data` field.
4. **Execution time:** `Instant.now()` for immediate execution.

```java
public void submit(Long eventLogId, CaseInstance instance, Worker worker,
                   Capability capability, Map<String, Object> inputData) {
    String idempotencyKey = WorkerExecutionKeys.inputDataHash(
        instance.getUuid(), worker.name(), capability.name(), inputData);
    Map<String, Object> taskData = Map.of(
        "eventLogId", eventLogId.toString(),
        "workerId", worker.name(),
        "caseHubInstanceUuid", instance.getUuid().toString(),
        "tenancyId", instance.getTenancyId(),
        "inputDataHash", idempotencyKey
        // bindingName, signalId added when present
    );
    schedulerClient.schedule(
        workerExecutionTask.instance(idempotencyKey, taskData),
        Instant.now());
}
```

Idempotency: the compound key includes `caseId`, `workerName`, `capability`, and a hash of the input data — preventing cross-case/cross-worker collisions. db-scheduler uses the instance ID as a unique key per task name. Scheduling with a duplicate instance ID is a no-op — same guarantee as Quartz's `JobKey`.

### Lifecycle Wiring

```java
@ApplicationScoped
public class DbSchedulerLifecycle {
    @Inject DataSource dataSource;
    @Inject Instance<Task<?>> tasks;
    private Scheduler scheduler;

    void onStart(@Observes StartupEvent ev) {
        scheduler = Scheduler.create(dataSource)
            .knownTasks(/* collect from CDI */)
            .threads(/* from config */)
            .build();
        scheduler.start();
    }

    void onStop(@Observes ShutdownEvent ev) {
        scheduler.stop();
    }

    @Produces @ApplicationScoped
    public SchedulerClient client() { return scheduler; }
}
```

### Job Listener Equivalent

db-scheduler's `ExecutionInterceptor` provides before/after hooks per execution. `DbSchedulerWorkerExecutionManager` registers an interceptor to fire `WorkerExecutionStarted` lifecycle events and persist start EventLog entries — same role as `QuartzWorkerExecutionJobListener`.

### Contract Tests

**`JobSchedulerContractTest`** in `common/src/test/java/` — abstract contract test verifying all `JobScheduler` implementations honor the same semantics:
- Schedule/cancel/exists for each `ScheduleStrategy` variant (`CronSchedule`, `DelaySchedule`, `FixedAtSchedule`)
- Group cancellation
- Idempotent cancel (cancelling non-existent job returns `false`)
- `JobType` routing (each type reaches the correct handler)

Both `QuartzJobScheduler` and `DbSchedulerJobScheduler` extend this contract test with their concrete wiring — same pattern as the existing `WorkerExecutionManagerContractTest`.

## Configuration and Table Management

### db-scheduler Table

Single `scheduled_tasks` table (12 columns).

**PostgreSQL production:** Flyway migration in `persistence-hibernate` alongside the existing Quartz `V1.0.0` migration. Both migrations coexist — tables don't conflict.

**H2 in-memory:** db-scheduler's built-in `startTasks.createIfNotExists()` handles table creation automatically.

### Application Properties

```properties
# db-scheduler config
casehub.scheduler.polling-interval=10s
casehub.scheduler.threads=5

# H2 in-memory path for lightweight installations
casehub.scheduler.datasource.db-kind=h2
casehub.scheduler.datasource.jdbc.url=jdbc:h2:mem:scheduler;DB_CLOSE_DELAY=-1
```

No `quarkus.quartz.*` properties needed when using db-scheduler. When using Quartz, nothing changes.

### Selection Mechanism

Installation picks one scheduler module on the classpath. Both modules' beans are `@ApplicationScoped` (not `@DefaultBean`). Having both on the classpath is a CDI ambiguity error — deliberate, forces a choice.

## Retry Handling

**Reuse:** `RetryPolicies.evaluate()` and `RetryDecision` (sealed: `Retry`/`Exhaust`) live in `common/internal/executor/` — scheduler-agnostic.

**Retry orchestration** is extracted to `RetryOrchestrator` in `common/internal/executor/` (see §Common Orchestration Extraction). Each scheduler module provides only the reschedule callback.

**db-scheduler's built-in retry is not used.** The engine's retry logic is richer — reads policy from `ExecutionPolicy`, counts from event log, uses `RetryPolicies` for backoff computation. db-scheduler's `FailureHandler` would duplicate and conflict.

**Flow:**
1. Worker execution fails → `WorkerExecutionOrchestrator` routes to scheduler-specific `RetryHandler`
2. `RetryHandler` delegates to `RetryOrchestrator.handleFailure(taskData, errorMessage, rescheduleCallback)`
3. `RetryOrchestrator` persists `WORKER_EXECUTION_FAILED` event log
4. Counts prior failures from event log
5. Calls `RetryPolicies.evaluate()` → `Retry(delay)` or `Exhaust(reason)`
6. `Retry`: invokes `rescheduleCallback.reschedule(taskData, delayMs)` — scheduler-specific
7. `Exhaust`: publishes `WORKER_RETRIES_EXHAUSTED` on event bus

Tasks are marked complete from db-scheduler's perspective on every execution. Retry is a new scheduled task, not a db-scheduler retry.

## Scope Boundary

### In Scope
- `JobType` enum and `ScheduledJobRequest` migration
- 5-field cron validation in `CronSchedule` and caller migration
- SPI javadoc cleanup (`WorkerExecutionManager`, `SchedulerService`, `ScheduleTrigger`, `CronSchedule`)
- `WorkerExecutionOrchestrator` and `RetryOrchestrator` extraction to `common/internal/executor/`
- `WorkerTaskData` record and `RetryHandler`/`RescheduleCallback` interfaces in `common/internal/executor/`
- `scheduler-dbscheduler` module with all 9 production classes
- `JobSchedulerContractTest` in common
- Flyway migration for db-scheduler table
- Configuration properties
- Remove 4 unwired methods from `QuartzWorkerExecutionManager` (`scheduleScheduledTrigger`, `scheduleConditionalTrigger`, `cancelScheduledTrigger`, `cancelAllScheduledTriggers`) — dead code that duplicates `SchedulerService` → `JobScheduler` functionality

### Out of Scope
- Removing `scheduler-quartz` — it stays as an alternative
- Migrating existing test suites from Quartz to db-scheduler
- Quartz cron features (`L`/`W`/`#`) — not supported by the 5-field SPI
- Clustering configuration (db-scheduler clusters by default, no engine work needed)
- Milestone SLA TODOs: `MilestoneLifecycleManager.java:198` (configurable SLA violation behavior) and `SlaStartFrom` enum values `PREVIOUS_MILESTONE_COMPLETED`, `EVENT_OCCURRED` (future implementations) — pre-existing design debt unrelated to the scheduler SPI

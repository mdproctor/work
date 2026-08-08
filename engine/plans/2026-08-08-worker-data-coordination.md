# Worker Data Coordination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #633 — design: Worker data coordination patterns — DataExchange and DataChannel alongside Blackboard
**Issue group:** #633

**Goal:** Add two new orthogonal Worker data coordination patterns — Exchange (discrete 1→1 handoff) and DataChannel (streaming pipe) — alongside the existing Blackboard, with Camel adapter bridge.

**Architecture:** Foundation types (Exchange, DataChannel, ExchangeProcessor) live in `casehubio/worker` (foundation tier). Engine SPIs and infrastructure live in `casehubio/engine`. Camel adapter lives in `casehubio/workers`. The engine detects `ExchangeAwareFunction` to thread Exchange metadata, apply projection strategies, and manage channel lifecycle. Blocking DataChannel API (no reactive dependency in foundation tier).

**Tech Stack:** Java 21, Quarkus 3.32.2, Jackson, Mutiny (engine-only for Multi adapter), Camel Quarkus

## Global Constraints

- Foundation tier (`casehub-worker-api`) has zero engine or CDI dependency
- `ExchangeAwareFunction` is the single detection point — all Exchange-typed WorkerFunction variants implement it
- DataChannel uses blocking API (`send()`, `receive()`) — no Mutiny/Multi in foundation tier
- Exchange is a pure value type — no ID, no exception state
- Null body on Exchange is valid (signal-only envelope)
- `ChannelDeclaration` rejects `LifecycleScope.BINDING` at construction time
- `exchangeHeaders` on `CaseInstance` is JPA-persisted (JSONB column)
- Default projection strategy is `DualWrite` — backward compatible
- Cross-repo protocol: worker foundation types must be `mvn install`ed before engine consumption

## Repo Locations (slot 93)

| Repo | Path | Branch |
|------|------|--------|
| casehubio/worker | `/Users/mdproctor/claude/casehub/slots/93/workers` (NOTE: this is the workers repo, worker-api is at `api/`) | issue-633-worker-data-coordination |
| casehubio/engine | `/Users/mdproctor/claude/casehub/slots/93/engine` | issue-633-worker-data-coordination |
| casehubio/workers | `/Users/mdproctor/claude/casehub/slots/93/workers` (same repo as worker) | issue-633-worker-data-coordination |

**IMPORTANT:** `casehub-worker-api` and `casehub-workers-camel` are in the SAME repo (`casehubio/worker`). `casehub-worker-api` is at `api/` within the repo, `casehub-workers-camel` is at `workers-camel/`.

---

### Task 1: Exchange Record + ExchangeAwareFunction + ExchangeProcessor

**Repo:** casehubio/worker
**Dependency:** None — first task

**Files:**
- Create: `api/src/main/java/io/casehub/worker/api/Exchange.java`
- Create: `api/src/main/java/io/casehub/worker/api/ExchangeAwareFunction.java`
- Modify: `api/src/main/java/io/casehub/worker/api/WorkerFunction.java` — add `ExchangeProcessor` record
- Create: `api/src/main/java/io/casehub/worker/api/ExchangeProcessorBuilder.java`
- Modify: `api/src/main/java/io/casehub/worker/api/Worker.java` — add `exchange()` builder entry points
- Test: `api/src/test/java/io/casehub/worker/api/ExchangeTest.java`
- Test: `api/src/test/java/io/casehub/worker/api/ExchangeProcessorTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `Exchange<T>` record (body, headers, properties, factories, with* methods), `ExchangeAwareFunction<T,R>` interface (bodyInputType, bodyOutputType), `WorkerFunction.ExchangeProcessor<T,R>` variant, `ExchangeProcessorBuilder<T>`, `Worker.Builder.exchange()` entry points

**Implementation:**

- [ ] **Step 1: Write Exchange record tests**

```java
// ExchangeTest.java
@Test void ofBody_createsWithEmptyHeadersAndProperties() { ... }
@Test void ofBodyAndHeaders_createsWithEmptyProperties() { ... }
@Test void nullHeaders_defaultsToEmptyMap() { ... }
@Test void nullProperties_defaultsToEmptyMap() { ... }
@Test void headers_areUnmodifiable() { ... }
@Test void properties_areUnmodifiable() { ... }
@Test void withBody_preservesHeadersAndProperties() { ... }
@Test void withBody_changesType() { ... }
@Test void withHeader_addsToExisting() { ... }
@Test void withHeaders_replacesAll() { ... }
@Test void withProperty_addsToExisting() { ... }
@Test void withoutHeader_removesKey() { ... }
@Test void withoutHeader_missingKey_noOp() { ... }
@Test void nullBody_isValid() { ... }
@Test void typedHeaderAccess() { ... }
@Test void typedHeaderAccess_withDefault() { ... }
@Test void equality_sameFields() { ... }
@Test void equality_differentHeaders() { ... }
```

- [ ] **Step 2: Run tests — verify they fail (Exchange.java doesn't exist)**

- [ ] **Step 3: Implement Exchange.java**

Full record as specified in the design spec (lines 49-99). Immutable, `with*` methods, typed access.

- [ ] **Step 4: Run tests — verify they pass**

- [ ] **Step 5: Write ExchangeAwareFunction interface**

```java
public interface ExchangeAwareFunction<T, R> extends WorkerFunction<Exchange<T>, Exchange<R>> {
    Class<T> bodyInputType();
    Class<R> bodyOutputType();
}
```

- [ ] **Step 6: Write ExchangeProcessor tests**

```java
// ExchangeProcessorTest.java
@Test void constructorRejectsNullBodyInputType() { ... }
@Test void constructorRejectsNullBodyOutputType() { ... }
@Test void constructorRejectsNullFn() { ... }
@Test void inputTypeReturnsExchangeClass() { ... }
@Test void outputTypeReturnsExchangeClass() { ... }
@Test void bodyInputTypeReturnsSpecifiedClass() { ... }
@Test void bodyOutputTypeReturnsSpecifiedClass() { ... }
@Test void implementsExchangeAwareFunction() { ... }
@Test void fnInvokesCorrectly() { ... }
```

- [ ] **Step 7: Add ExchangeProcessor to WorkerFunction.java**

Add the `ExchangeProcessor<T, R>` record inside `WorkerFunction` as specified in spec lines 144-161.

- [ ] **Step 8: Run all tests — verify they pass**

- [ ] **Step 9: Write ExchangeProcessorBuilder + Worker.Builder integration tests**

```java
@Test void builderCreatesExchangeProcessor() {
    Worker worker = Worker.builder()
        .name("test")
        .capabilityName("cap")
        .<Map<String,Object>>exchange()
        .returning(Map.class)
        .apply((ex, scope) -> WorkerResult.of(ex))
        .build();
    assertThat(worker.function()).isInstanceOf(WorkerFunction.ExchangeProcessor.class);
    assertThat(worker.function()).isInstanceOf(ExchangeAwareFunction.class);
}

@Test void convenienceExchangeBuilder() {
    Worker worker = Worker.builder()
        .name("test")
        .capabilityName("cap")
        .exchange((exchange, scope) -> WorkerResult.of(exchange.withBody(Map.of("done", true))))
        .build();
    assertThat(worker.function()).isInstanceOf(WorkerFunction.ExchangeProcessor.class);
}
```

- [ ] **Step 10: Implement ExchangeProcessorBuilder (mirrors TypedFunctionBuilder)**

- [ ] **Step 11: Add exchange() entry points to Worker.Builder**

Two overloads: `exchange(BiFunction)` convenience (Map→Map), `<T>exchange()` typed entry.

- [ ] **Step 12: Run all tests — verify pass**

- [ ] **Step 13: Commit**

```
feat(engine#633): add Exchange record, ExchangeAwareFunction, and ExchangeProcessor variant

Refs #633
```

---

### Task 2: exchangeSequence + andThen Composition

**Repo:** casehubio/worker
**Dependency:** Task 1

**Files:**
- Modify: `api/src/main/java/io/casehub/worker/api/WorkerFunction.java` — add `andThen()` to ExchangeProcessor
- Modify: `api/src/main/java/io/casehub/api/model/WorkerFunctions.java` (engine-api) — add `exchangeSequence()`
- Test: `api/src/test/java/io/casehub/worker/api/ExchangeCompositionTest.java`

**Interfaces:**
- Consumes: `Exchange<T>`, `ExchangeProcessor<T,R>`, `WorkerScope`
- Produces: `ExchangeProcessor.andThen(ExchangeProcessor)`, `WorkerFunctions.exchangeSequence(ExchangeProcessor...)`

- [ ] **Step 1: Write andThen tests**

```java
@Test void andThen_chainsBodyThroughBothSteps() { ... }
@Test void andThen_mergesHeaders() { ... }
@Test void andThen_mergesProperties() { ... }
@Test void andThen_shortCircuitsOnFailure() { ... }
@Test void andThen_preservesBodyTypes() { ... }
```

- [ ] **Step 2: Implement andThen on ExchangeProcessor**

- [ ] **Step 3: Write exchangeSequence tests**

```java
@Test void exchangeSequence_chainsMultipleSteps() { ... }
@Test void exchangeSequence_emptyThrows() { ... }
@Test void exchangeSequence_singleStep() { ... }
@Test void exchangeSequence_shortCircuitsOnDeclined() { ... }
@Test void exchangeSequence_headersAccumulate() { ... }
@Test void exchangeSequence_propertiesAccumulate() { ... }
@Test void exchangeSequence_constructionTimeTypeValidation() { ... }
```

- [ ] **Step 4: Implement exchangeSequence in WorkerFunctions**

Note: `WorkerFunctions` is in `casehub-engine-api` (`api/src/main/java/io/casehub/api/model/WorkerFunctions.java`). It already depends on `casehub-worker-api`. Add the static method with construction-time type validation.

- [ ] **Step 5: Run all tests — verify pass**

- [ ] **Step 6: Commit**

```
feat(engine#633): add Exchange composition — andThen() and exchangeSequence()

Refs #633
```

---

### Task 3: DataChannel + ChannelRef + WorkerScope Channel Access

**Repo:** casehubio/worker
**Dependency:** Task 1

**Files:**
- Create: `api/src/main/java/io/casehub/worker/api/DataChannel.java`
- Create: `api/src/main/java/io/casehub/worker/api/ChannelRef.java`
- Create: `api/src/main/java/io/casehub/worker/api/ChannelClosedException.java`
- Modify: `api/src/main/java/io/casehub/worker/api/WorkerScope.java` — add channel default methods
- Test: `api/src/test/java/io/casehub/worker/api/ChannelRefTest.java`
- Test: `api/src/test/java/io/casehub/worker/api/WorkerScopeChannelTest.java`

**Interfaces:**
- Consumes: `Exchange<T>`
- Produces: `DataChannel<T>` (send, receive, close, isClosed), `ChannelRef<T>` (name, recordType), `ChannelClosedException`, `WorkerScope.channel()`, `WorkerScope.createChannel()`

- [ ] **Step 1: Write ChannelRef tests**

```java
@Test void ofCreatesRef() { ... }
@Test void constructorRejectsNullName() { ... }
@Test void constructorRejectsNullType() { ... }
@Test void equality() { ... }
@Test void serializable() { ... }
```

- [ ] **Step 2: Implement ChannelRef, DataChannel interface, ChannelClosedException**

DataChannel — interface only, no implementation yet (InMemoryDataChannel is Task 6). ChannelClosedException — simple runtime exception.

- [ ] **Step 3: Write WorkerScope default method tests**

```java
@Test void channelDefaultThrowsUnsupported() { ... }
@Test void channelRefDelegatesToChannelName() { ... }
@Test void createChannelDefaultThrowsUnsupported() { ... }
```

- [ ] **Step 4: Add default methods to WorkerScope**

Three methods as specified in spec lines 280-291.

- [ ] **Step 5: Run all tests — verify pass**

- [ ] **Step 6: Install worker-api SNAPSHOT to local repo**

```bash
mvn -C /path/to/worker install -DskipTests -q
```

- [ ] **Step 7: Commit**

```
feat(engine#633): add DataChannel interface, ChannelRef, and WorkerScope channel access

Refs #633
```

---

### Task 4: Engine SPIs + Model Additions

**Repo:** casehubio/engine
**Dependency:** Tasks 1-3 (worker-api SNAPSHOT installed)

**Files:**
- Create: `api/src/main/java/io/casehub/api/spi/ExchangeProjectionStrategy.java`
- Create: `api/src/main/java/io/casehub/api/spi/DataChannelFactory.java`
- Create: `api/src/main/java/io/casehub/api/model/ChannelDeclaration.java`
- Modify: `api/src/main/java/io/casehub/api/model/CaseDefinition.java` — add `channels`, builder methods
- Modify: `api/src/main/java/io/casehub/api/model/Binding.java` — add `exchangeProjectionStrategy`, `produces`, `consumes`, builder helpers
- Modify: `common/src/main/java/io/casehub/engine/common/internal/model/CaseInstance.java` — add `exchangeHeaders` field
- Modify: `persistence-hibernate/.../CaseInstanceEntity.java` — add JSONB column
- Modify: `persistence-memory/.../InMemoryCaseInstanceRepository.java` — threading support
- Test: `api/src/test/java/io/casehub/api/model/ChannelDeclarationTest.java`
- Test: `api/src/test/java/io/casehub/api/model/BindingExchangeTest.java`

**Interfaces:**
- Consumes: `Exchange<T>`, `DataChannel<T>`, `ChannelRef<T>` from worker-api; `NamedStrategy`, `MutableCaseContext` from engine-api
- Produces: `ExchangeProjectionStrategy` SPI, `DataChannelFactory` SPI, `ChannelDeclaration` record, model field additions

- [ ] **Step 1: Write ChannelDeclaration tests**

```java
@Test void defaultTransportIsInMemory() { ... }
@Test void defaultScopeIsCase() { ... }
@Test void bindingScopeRejected() { ... }
@Test void blankNameRejected() { ... }
@Test void nullNameRejected() { ... }
@Test void nullRecordTypeRejected() { ... }
```

- [ ] **Step 2: Implement ChannelDeclaration**

Record as specified in spec lines 301-319.

- [ ] **Step 3: Create ExchangeProjectionStrategy and DataChannelFactory interfaces**

- [ ] **Step 4: Add model fields to CaseDefinition and Binding**

CaseDefinition: `List<ChannelDeclaration> channels` with builder `.channel(name, type)`, `.channel(name, type, transport)`.
Binding: `exchangeProjectionStrategy`, `produces`, `consumes` with builder helpers `.exchangeOnly()`, `.dualWrite()`, `.projectWith()`, `.produces()`, `.consumes()`.

- [ ] **Step 5: Write Binding builder tests**

```java
@Test void exchangeOnly_setsStrategy() { ... }
@Test void produces_setsChannelName() { ... }
@Test void consumes_setsChannelName() { ... }
```

- [ ] **Step 6: Add exchangeHeaders to CaseInstance + JPA entity**

`Map<String, Object> exchangeHeaders` — transient in CaseInstance POJO, JSONB column in CaseInstanceEntity. Include in InMemory repo.

- [ ] **Step 7: Run all tests — verify pass**

- [ ] **Step 8: Commit**

```
feat(engine#633): add Exchange SPIs, ChannelDeclaration, and model field additions

Refs #633
```

---

### Task 5: InMemoryDataChannel + DataChannelRegistry

**Repo:** casehubio/engine
**Dependency:** Task 4

**Files:**
- Create: `common/src/main/java/io/casehub/engine/common/internal/worker/channel/InMemoryDataChannel.java`
- Create: `common/src/main/java/io/casehub/engine/common/internal/worker/channel/InMemoryDataChannelFactory.java`
- Create: `common/src/main/java/io/casehub/engine/common/internal/worker/channel/DataChannelRegistry.java`
- Test: `common/src/test/java/io/casehub/engine/common/internal/worker/channel/InMemoryDataChannelTest.java`
- Test: `common/src/test/java/io/casehub/engine/common/internal/worker/channel/DataChannelRegistryTest.java`

**Interfaces:**
- Consumes: `DataChannel<T>`, `DataChannelFactory`, `Exchange<T>`, `ChannelRef<T>`
- Produces: `InMemoryDataChannel<T>` (bounded BlockingQueue, virtual-thread-safe), `InMemoryDataChannelFactory` (`@DefaultBean`, id `"in-memory"`), `DataChannelRegistry` (idempotent creation, bulk teardown)

- [ ] **Step 1: Write InMemoryDataChannel tests**

```java
@Test void sendAndReceive_singleExchange() { ... }
@Test void sendAndReceive_preservesOrder() { ... }
@Test void send_blocksWhenFull() { ... }  // virtual thread test
@Test void receive_blocksWhenEmpty() { ... }
@Test void close_terminatesSend() { ... }
@Test void close_terminatesReceive() { ... }
@Test void receiveReturnsNullWhenClosed() { ... }
@Test void sendThrowsChannelClosedException() { ... }
@Test void isClosed() { ... }
@Test void doubleClose_noOp() { ... }
```

- [ ] **Step 2: Implement InMemoryDataChannel**

Backed by `ArrayBlockingQueue<Exchange<T>>` (configurable capacity, default 1024). `send()` calls `queue.put()` (blocks under backpressure). `receive()` calls `queue.poll()` with check for closed state. `close()` sets closed flag, interrupts blocked threads.

- [ ] **Step 3: Implement InMemoryDataChannelFactory**

`@DefaultBean @ApplicationScoped`, id `"in-memory"`. Creates `InMemoryDataChannel` with configurable buffer size from `@ConfigProperty`.

- [ ] **Step 4: Write DataChannelRegistry tests**

```java
@Test void getOrCreate_createsNewChannel() { ... }
@Test void getOrCreate_returnsExistingForSameKey() { ... }
@Test void getOrCreate_throwsOnTypeMismatch() { ... }
@Test void closeByCase_closesAllChannelsForCase() { ... }
@Test void closeByCase_doesNotAffectOtherCases() { ... }
@Test void closeByExecution_closesAdHocChannels() { ... }
```

- [ ] **Step 5: Implement DataChannelRegistry**

`@ApplicationScoped`. `ConcurrentHashMap<ChannelKey, DataChannel<?>>`. Idempotent creation with type validation. Bulk teardown methods.

- [ ] **Step 6: Run all tests — verify pass**

- [ ] **Step 7: Commit**

```
feat(engine#633): add InMemoryDataChannel, DataChannelFactory, and DataChannelRegistry

Refs #633
```

---

### Task 6: Projection Strategy Implementations

**Repo:** casehubio/engine
**Dependency:** Task 4

**Files:**
- Create: `common/src/main/java/io/casehub/engine/common/internal/worker/exchange/DualWriteProjection.java`
- Create: `common/src/main/java/io/casehub/engine/common/internal/worker/exchange/ExchangeOnlyProjection.java`
- Create: `common/src/main/java/io/casehub/engine/common/internal/worker/exchange/FullProjection.java`
- Create: `common/src/main/java/io/casehub/engine/common/internal/worker/exchange/CustomJqProjection.java`
- Create: `common/src/main/java/io/casehub/engine/common/internal/worker/exchange/ExchangeSerializer.java`
- Test: `common/src/test/java/io/casehub/engine/common/internal/worker/exchange/ProjectionStrategyTest.java`
- Test: `common/src/test/java/io/casehub/engine/common/internal/worker/exchange/ExchangeSerializerTest.java`

**Interfaces:**
- Consumes: `ExchangeProjectionStrategy`, `Exchange<T>`, `MutableCaseContext`, `JQEvaluator`
- Produces: 4 built-in strategies, `ExchangeSerializer` (Exchange ↔ EventLog serialization)

- [ ] **Step 1: Write projection strategy tests**

```java
// DualWrite
@Test void dualWrite_projectsBodyToContext() { ... }
@Test void dualWrite_doesNotProjectHeaders() { ... }
@Test void dualWrite_returnsProjectedKeys() { ... }

// ExchangeOnly
@Test void exchangeOnly_projectsNothing() { ... }
@Test void exchangeOnly_returnsEmptySet() { ... }

// Full
@Test void full_projectsBodyAndHeaders() { ... }
@Test void full_namespacesHeaders() { ... }

// CustomJq
@Test void customJq_appliesExpression() { ... }
@Test void customJq_invalidExpression_throws() { ... }
```

- [ ] **Step 2: Implement all four strategies**

`DualWriteProjection` — `@DefaultBean @ApplicationScoped`, id `"dual-write"`. Serializes body to Map via Jackson, merges to context.
`ExchangeOnlyProjection` — id `"exchange-only"`. Returns empty set.
`FullProjection` — id `"full"`. Body + headers under `_exchange.<binding>.headers`.
`CustomJqProjection` — id `"jq"`. JQ expression against combined `{body, headers}` document.

- [ ] **Step 3: Write ExchangeSerializer tests**

```java
@Test void serializeRoundTrip_mapBody() { ... }
@Test void serializeRoundTrip_pojoBody() { ... }
@Test void serializeRoundTrip_nullBody() { ... }
@Test void serializeRoundTrip_withHeaders() { ... }
@Test void serializeRoundTrip_withProperties() { ... }
```

- [ ] **Step 4: Implement ExchangeSerializer**

Serializes Exchange to `ObjectNode` with `body` (via ContextBridge), `headers`, `properties`. Deserializes back with body type from `ExchangeAwareFunction.bodyInputType()`.

- [ ] **Step 5: Run all tests — verify pass**

- [ ] **Step 6: Commit**

```
feat(engine#633): add projection strategies and ExchangeSerializer

Refs #633
```

---

### Task 7: Engine Runtime — WorkerRuntime + Handler Integration

**Repo:** casehubio/engine
**Dependency:** Tasks 5, 6

This is the largest task — it wires Exchange and DataChannel into the engine's dispatch and completion pipelines. All handler changes are additive (Exchange-aware branches alongside existing paths).

**Files:**
- Modify: `api/src/main/java/io/casehub/api/engine/WorkerRuntime.java` — override channel methods
- Modify: `runtime/src/main/java/io/casehub/engine/internal/executor/DefaultWorkerRuntime.java` — channel + Exchange-aware execute
- Modify: `runtime/src/main/java/io/casehub/engine/internal/executor/WorkerRuntimeFactory.java` — inject DataChannelRegistry
- Modify: `runtime/src/main/java/io/casehub/engine/internal/engine/handler/CaseContextChangedEventHandler.java` — Exchange-aware dispatch
- Modify: `runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkerScheduleEventHandler.java` — Exchange EventLog serialization
- Modify: `scheduler-quartz/src/main/java/io/casehub/.../QuartzWorkerExecutionJob.java` — Exchange deserialization
- Modify: `runtime/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java` — projection strategy + header threading
- Modify: `runtime/src/main/java/io/casehub/engine/internal/engine/handler/CaseStatusChangedHandler.java` — channel teardown
- Modify: `runtime/src/main/java/io/casehub/engine/internal/routing/EngineStrategyResolver.java` — add strategy instances
- Test: `runtime/src/test/java/io/casehub/engine/internal/ExchangeDispatchTest.java`
- Test: `runtime/src/test/java/io/casehub/engine/internal/DataChannelIntegrationTest.java`

**Interfaces:**
- Consumes: All types from Tasks 1-6
- Produces: End-to-end Exchange dispatch and completion pipeline, channel lifecycle management

- [ ] **Step 1: Write Exchange dispatch integration test**

```java
@QuarkusTest
class ExchangeDispatchTest {
    @Test void exchangeWorker_receivesExchangeInput_returnsExchangeOutput() {
        // Define a CaseHub with an ExchangeProcessor worker
        // Start case → verify Exchange threaded → verify output projected
    }

    @Test void exchangeWorker_dualWrite_projectsBodyToBlackboard() { ... }
    @Test void exchangeWorker_exchangeOnly_skipsBlackboard() { ... }
    @Test void exchangeHeaders_threadAcrossBindings() { ... }
    @Test void exchangeProperties_dontThreadAcrossBindings() { ... }
}
```

- [ ] **Step 2: Update WorkerRuntime interface + DefaultWorkerRuntime**

Override `channel()`, `createChannel()` on WorkerRuntime. Implement in DefaultWorkerRuntime with DataChannelRegistry delegation. Add `ExchangeAwareFunction` detection in `execute()` for Tier 1.

- [ ] **Step 3: Update WorkerRuntimeFactory**

Inject `DataChannelRegistry`. Pass to DefaultWorkerRuntime constructor.

- [ ] **Step 4: Update CaseContextChangedEventHandler**

In `publishWorkerSchedule()`, detect `ExchangeAwareFunction`. Build Exchange from input projection (body) + `CaseInstance.exchangeHeaders` (inherited headers). Store via ExchangeSerializer.

- [ ] **Step 5: Update WorkerScheduleEventHandler**

Read Exchange from EventLog metadata when present. Serialize to Quartz job data.

- [ ] **Step 6: Update QuartzWorkerExecutionJob**

Detect `ExchangeAwareFunction`. Deserialize Exchange via ExchangeSerializer + ContextBridge for body type. Invoke handler with Exchange input.

- [ ] **Step 7: Update WorkflowExecutionCompletedHandler**

When result is `WorkerResult<Exchange<R>>`:
1. Resolve `ExchangeProjectionStrategy` via `EngineStrategyResolver`
2. Apply strategy (project to context or skip)
3. Merge outbound headers into `CaseInstance.exchangeHeaders` under per-case lock
4. Write Exchange metadata to EventLog
5. Handle projection failure per spec (thread Exchange, skip Blackboard, audit failure, publish PROJECTION_FAILED)

- [ ] **Step 8: Update CaseStatusChangedHandler**

Add `channelRegistry.closeByCase(caseId)` call on terminal state.

- [ ] **Step 9: Update EngineStrategyResolver**

Add `Instance<ExchangeProjectionStrategy>` and `Instance<DataChannelFactory>` injection.

- [ ] **Step 10: Write DataChannel integration test**

```java
@QuarkusTest
class DataChannelIntegrationTest {
    @Test void declaredChannel_producerAndConsumer() {
        // CaseDefinition with channel declaration + producer + consumer bindings
        // Start case → producer sends to channel → consumer receives
    }

    @Test void adHocChannel_withinWorkerExecution() { ... }
    @Test void channelClosedOnCaseTerminal() { ... }
}
```

- [ ] **Step 11: Run all tests — verify pass**

- [ ] **Step 12: Commit**

```
feat(engine#633): wire Exchange and DataChannel into engine dispatch pipeline

Refs #633
```

---

### Task 8: YAML Parsing

**Repo:** casehubio/engine
**Dependency:** Task 7

**Files:**
- Modify: `runtime/src/main/java/io/casehub/engine/internal/definition/CaseDefinitionYamlMapper.java` — parse channels, exchangeProjection, produces, consumes
- Test: `runtime/src/test/java/io/casehub/engine/internal/definition/ExchangeYamlParsingTest.java`
- Create: `runtime/src/test/resources/exchange-case.yaml` — test fixture

**Interfaces:**
- Consumes: `ChannelDeclaration`, `CaseDefinition` builder, `Binding` builder
- Produces: YAML parsing for `channels:`, `exchangeProjection:`, `produces:`, `consumes:`, `exchange: true` on workers

- [ ] **Step 1: Create test YAML fixture**

```yaml
spec:
  channels:
    - name: tx-stream
      recordType: java.util.Map
      transport: in-memory
      scope: compound

  workers:
    - name: enricher
      capabilities: [enrichment]
      exchange: true
      bodyInputType: java.util.Map
      bodyOutputType: java.util.Map

  bindings:
    - name: extract
      capability: data-extraction
      produces: tx-stream
      exchangeProjection: exchange-only
    - name: transform
      capability: data-transformation
      consumes: tx-stream
      exchangeProjection:
        strategy: jq
        expression: "{ result: .body }"
```

- [ ] **Step 2: Write YAML parsing tests**

```java
@Test void parsesChannelDeclarations() { ... }
@Test void parsesExchangeProjectionString() { ... }
@Test void parsesExchangeProjectionObject() { ... }
@Test void parsesProducesAndConsumes() { ... }
@Test void parsesExchangeWorkerFunction() { ... }
```

- [ ] **Step 3: Update CaseDefinitionYamlMapper**

Add parsing for:
- `spec.channels[]` → `ChannelDeclaration` list
- `bindings[].exchangeProjection` → string (simple) or object (strategy + expression)
- `bindings[].produces` / `bindings[].consumes` → channel names
- `workers[].exchange: true` + `bodyInputType` / `bodyOutputType` → `ExchangeProcessor`

- [ ] **Step 4: Run all tests — verify pass**

- [ ] **Step 5: Commit**

```
feat(engine#633): parse Exchange and DataChannel YAML schema

Refs #633
```

---

### Task 9: Camel Adapter

**Repo:** casehubio/workers (same repo as worker-api)
**Dependency:** Tasks 1-3 (worker-api types), Task 7 (engine handler support)

**Files:**
- Create: `workers-camel/src/main/java/io/casehub/workers/camel/CamelExchangeAdapter.java`
- Create: `workers-camel/src/main/java/io/casehub/workers/camel/CamelExchangeWorkerFunction.java`
- Create: `workers-camel/src/main/java/io/casehub/workers/camel/CamelExchangeWorkerFunctionHandler.java`
- Modify: `workers-camel/src/main/java/io/casehub/workers/camel/CamelWorkerExecutionManager.java` — Exchange-aware submit path
- Test: `workers-camel/src/test/java/io/casehub/workers/camel/CamelExchangeAdapterTest.java`
- Test: `workers-camel/src/test/java/io/casehub/workers/camel/CamelExchangeWorkerFunctionHandlerTest.java`

**Interfaces:**
- Consumes: `Exchange<T>`, `ExchangeAwareFunction<T,R>`, `WorkerResult<Exchange<R>>`, Camel `ProducerTemplate`, `org.apache.camel.Exchange`
- Produces: `CamelExchangeAdapter` (bidirectional mapping), `CamelExchangeWorkerFunction` (implements `ExchangeAwareFunction`), `CamelExchangeWorkerFunctionHandler` (dispatches to ProducerTemplate)

- [ ] **Step 1: Write CamelExchangeAdapter tests**

```java
@Test void toCamel_setsBody() { ... }
@Test void toCamel_setsHeaders() { ... }
@Test void toCamel_setsProperties() { ... }
@Test void toCamel_filtersCamelPrefixedHeaders() { ... }
@Test void fromCamel_extractsBody() { ... }
@Test void fromCamel_extractsHeaders() { ... }
@Test void fromCamel_stripsCamelInternalHeaders() { ... }
@Test void toResult_onException_returnsFailed() { ... }
@Test void toResult_onSuccess_returnsExchange() { ... }
```

- [ ] **Step 2: Implement CamelExchangeAdapter**

Static utility as specified in spec lines 557-584.

- [ ] **Step 3: Implement CamelExchangeWorkerFunction**

Record implementing `ExchangeAwareFunction` as specified in spec lines 590-599.

- [ ] **Step 4: Write handler tests**

```java
@Test void supportsOnlyCamelExchangeWorkerFunction() { ... }
@Test void executeBridgesExchangeToRoute() { ... }
@Test void executeReturnsHandlerResultWithMetadata() { ... }
@Test void executeHandlesRouteException() { ... }
```

- [ ] **Step 5: Implement CamelExchangeWorkerFunctionHandler**

`@ApplicationScoped`, implements `WorkerFunctionHandler`. Bridges via `CamelExchangeAdapter`. Returns `HandlerResult` with protocol metadata.

- [ ] **Step 6: Update CamelWorkerExecutionManager with Exchange-aware path**

- [ ] **Step 7: Run all tests — verify pass**

- [ ] **Step 8: Commit**

```
feat(engine#633): add Camel Exchange adapter — bridges casehub Exchange to Camel routes

Refs #633
```

---

### Task 10: End-to-End Integration Test + CLAUDE.md Update

**Repo:** casehubio/engine
**Dependency:** Tasks 7-9

**Files:**
- Create: `runtime/src/test/java/io/casehub/engine/ExchangeEndToEndTest.java`
- Modify: `CLAUDE.md` — document Exchange, DataChannel, ExchangeAwareFunction, projection strategies

**Interfaces:**
- Consumes: All types from all tasks
- Produces: Comprehensive integration tests, updated documentation

- [ ] **Step 1: Write end-to-end Exchange pipeline test**

```java
@QuarkusTest
class ExchangeEndToEndTest {
    @Test void fullExchangePipeline_threeWorkers_headersThread() {
        // Worker A (ExchangeProcessor) → writes Exchange with headers
        // Worker B (ExchangeProcessor) → receives Exchange with A's headers, adds more
        // Worker C (ExchangeProcessor) → receives Exchange with A+B's headers
        // Assert: final CaseContext has projected body, exchangeHeaders has all headers
    }

    @Test void mixedPipeline_exchangeAndNonExchange() {
        // Worker A (ExchangeProcessor, DualWrite) → body projected to Blackboard
        // Worker B (standard Sync) → triggers from Blackboard, produces normally
        // Assert: both paths work, no interference
    }

    @Test void dataChannel_producerConsumer_endToEnd() {
        // Declared channel on CaseDefinition
        // Producer worker sends 10 records via channel
        // Consumer worker (Persistent) receives all 10
        // Assert: all records received in order
    }

    @Test void projectionFailure_exchangeStillThreads() {
        // Worker with bad JQ projection
        // Assert: Exchange headers still thread, Blackboard unchanged, EventLog has error
    }
}
```

- [ ] **Step 2: Run integration tests — verify pass**

- [ ] **Step 3: Update CLAUDE.md**

Add sections documenting:
- Exchange type and semantics
- ExchangeAwareFunction detection interface
- ExchangeProcessor WorkerFunction variant and builder DSL
- DataChannel, ChannelRef, WorkerScope channel access
- ExchangeProjectionStrategy and built-in strategies
- Engine integration points (which handlers changed)
- Camel adapter

- [ ] **Step 4: Commit**

```
feat(engine#633): end-to-end integration tests and CLAUDE.md documentation

Refs #633
```

---

## Task Dependencies

```
Task 1 (Exchange + ExchangeProcessor)
  ├─→ Task 2 (exchangeSequence + andThen)
  └─→ Task 3 (DataChannel + ChannelRef)
        └─→ [install worker-api SNAPSHOT]
              └─→ Task 4 (Engine SPIs + Model)
                    ├─→ Task 5 (InMemoryDataChannel + Registry)
                    └─→ Task 6 (Projection Strategies)
                          └─→ Task 7 (Engine Runtime Integration)
                                └─→ Task 8 (YAML Parsing)
                                └─→ Task 9 (Camel Adapter)
                                      └─→ Task 10 (E2E Tests + Docs)
```

Tasks 2 and 3 are independent of each other.
Tasks 5 and 6 are independent of each other.
Tasks 8 and 9 are independent of each other.

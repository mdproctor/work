# Worker Data Coordination — DataExchange and DataChannel Design

> **Issue:** casehubio/engine#633
> **Status:** Design approved
> **Date:** 2026-08-08
> **Scope:** casehubio/worker (foundation tier), casehubio/engine (all modules), casehubio/workers (camel adapter)

## Overview

Workers currently have one data coordination path: the Blackboard (CaseContext) — many-to-many shared mutable state. This design adds two orthogonal patterns:

| # | Pattern | Direction | What it solves |
|---|---------|-----------|----------------|
| 1 | Blackboard (CaseContext) | Many ↔ Many | Shared mutable state (exists) |
| 2 | DataExchange | 1 → 1 | Discrete payload handoff with metadata |
| 3 | DataChannel | 1 → 1 | Continuous streaming pipe with backpressure |

All three patterns operate across all tiers: Tier 1 (in-worker composition), Tier 2 (binding-driven), and Tier 3 (cross-case SubCase delegation). Workers that don't use DataExchange or DataChannel work exactly as before.

## Design Decisions

1. **Native types, not Camel.** Exchange/Channel are built native to casehub's execution model (immutable records, WorkerResult outcomes, EventLog audit, NamedStrategy transport). A Camel adapter bridges at the boundary for route-backed workers. Rationale: Camel's mutable Exchange fights platform conventions; Camel's routing engine overlaps with casehub's binding model; the gap is inter-worker coordination, not intra-worker integration.

2. **Exchange is a pure data envelope.** No ID (value type), no exception state (WorkerOutcome handles this), no separate Message type (Exchange IS the message). Cleaner separation than Camel — data and outcome are distinct.

3. **New WorkerFunction variant.** `ExchangeProcessor<T, R>` carries `bodyInputType`/`bodyOutputType` for ContextBridge resolution at serialization boundaries. The engine detects `instanceof ExchangeProcessor` to thread Exchange metadata across bindings.

4. **Channel as `Multi<Exchange<T>>`.** Exchange is the universal atom; Channel streams them. Unified type model.

5. **Blackboard projection is a composable strategy.** Per-binding `ExchangeProjectionStrategy` (NamedStrategy) determines what reaches the Blackboard. Default `DualWrite` — backward compatible.

6. **Channel lifecycle: declared (Tier 2/3) vs ad-hoc (Tier 1).** Mirrors how Tier 1 workers call `scope.execute()` freely while Tier 2 is engine-managed via bindings.

## Exchange Type

**Module:** `casehub-worker-api` (foundation tier)
**Package:** `io.casehub.worker.api`

```java
public record Exchange<T>(
    T body,
    Map<String, Object> headers,
    Map<String, Object> properties
) {
    public Exchange {
        headers = headers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    // --- Factories ---
    public static <T> Exchange<T> of(T body) { return new Exchange<>(body, Map.of(), Map.of()); }
    public static <T> Exchange<T> of(T body, Map<String, Object> headers) {
        return new Exchange<>(body, headers, Map.of());
    }

    // --- Immutable transforms ---
    public <U> Exchange<U> withBody(U newBody) { return new Exchange<>(newBody, headers, properties); }

    public Exchange<T> withHeader(String key, Object value) {
        var h = new LinkedHashMap<>(headers); h.put(key, value);
        return new Exchange<>(body, h, properties);
    }

    public Exchange<T> withHeaders(Map<String, Object> newHeaders) {
        return new Exchange<>(body, newHeaders, properties);
    }

    public Exchange<T> withProperty(String key, Object value) {
        var p = new LinkedHashMap<>(properties); p.put(key, value);
        return new Exchange<>(body, headers, p);
    }

    public Exchange<T> withoutHeader(String key) {
        var h = new LinkedHashMap<>(headers); h.remove(key);
        return new Exchange<>(body, h, properties);
    }

    // --- Typed access ---
    @SuppressWarnings("unchecked")
    public <V> V header(String key) { return (V) headers.get(key); }

    public <V> V header(String key, V defaultValue) {
        @SuppressWarnings("unchecked")
        V value = (V) headers.get(key);
        return value != null ? value : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public <V> V property(String key) { return (V) properties.get(key); }
}
```

### Semantics

- **Headers** propagate across binding dispatches (Tier 2/3). Metadata about the data: source system, content type, correlation IDs, timestamps.
- **Properties** do NOT propagate across bindings. Scoped to Tier 1 chains (`WorkerFunctions.exchangeSequence()`). Pipeline-local state: loop counters, accumulated results, routing decisions.
- **Body** is the payload. Any type: POJO, `Path` (file reference), `URI` (remote reference), `ChannelRef<T>` (stream reference).

### What Exchange is NOT

- Not an entity — no ID, no lifecycle. Pure value type. Two Exchanges with same fields are equal.
- Not a result — no exception state. `WorkerResult<Exchange<R>>` carries outcome semantics.
- Not a message container — no IN/OUT split. Workers receive Exchange, return `WorkerResult<Exchange<R>>`.

## WorkerFunction.ExchangeProcessor

**Module:** `casehub-worker-api` (foundation tier)

New variant on `WorkerFunction` alongside `Sync`, `Persistent`, and `None`:

```java
public interface WorkerFunction<T, R> {
    // ... existing variants ...

    record ExchangeProcessor<T, R>(
        Class<T> bodyInputType,
        Class<R> bodyOutputType,
        BiFunction<Exchange<T>, WorkerScope, WorkerResult<Exchange<R>>> fn
    ) implements WorkerFunction<Exchange<T>, Exchange<R>> {

        public ExchangeProcessor {
            Objects.requireNonNull(bodyInputType);
            Objects.requireNonNull(bodyOutputType);
            Objects.requireNonNull(fn);
        }

        @Override @SuppressWarnings("unchecked")
        public Class<Exchange<T>> inputType() { return (Class) Exchange.class; }

        @Override @SuppressWarnings("unchecked")
        public Class<Exchange<R>> outputType() { return (Class) Exchange.class; }
    }
}
```

### Why a new variant (not `Sync<Exchange<T>, Exchange<R>>`)

- `bodyInputType()` / `bodyOutputType()` → ContextBridge resolution at EventLog serialization boundaries. Engine serializes the body via the appropriate bridge, not the whole Exchange.
- `instanceof ExchangeProcessor` → engine branches to thread Exchange headers across binding dispatches, apply projection strategy, store Exchange metadata in EventLog.
- Builder DSL distinguishes Exchange and non-Exchange paths at construction time.

### Builder DSL

```java
// Simple (Map body, Map output)
Worker.builder()
    .name("enricher")
    .capabilityName("enrichment")
    .exchange((exchange, scope) ->
        WorkerResult.of(exchange.withBody(Map.of("enriched", true))))
    .build();

// Typed input and output body
Worker.builder()
    .name("enricher")
    .<RawTransaction>exchange()
    .returning(EnrichedTransaction.class)
    .apply((exchange, scope) -> {
        var enriched = enrich(exchange.body());
        return WorkerResult.of(
            exchange.withBody(enriched)
                    .withHeader("enrichedAt", Instant.now().toString()));
    })
    .build();
```

`Worker.Builder.exchange()` returns `ExchangeProcessorBuilder<T>` (new class, mirrors `TypedFunctionBuilder<T>`).

### Tier 1 Composition

`WorkerFunctions` gains an Exchange-aware sequence:

```java
public static <T> WorkerFunction.ExchangeProcessor<T, T> exchangeSequence(
    WorkerFunction.ExchangeProcessor<?, ?>... steps) {
    // Chains steps: output Exchange body feeds next step's input.
    // Headers accumulate (merge across steps).
    // Properties reset per step (don't propagate).
    // Short-circuits on non-Success outcome.
}
```

## DataChannel

### Foundation tier (`casehub-worker-api`)

Minimal interface — transport-agnostic:

```java
public interface DataChannel<T> extends AutoCloseable {
    void send(Exchange<T> exchange);
    Multi<Exchange<T>> receive();
    boolean isClosed();
    @Override void close();
}
```

`send()` blocks under backpressure (virtual-thread-safe). `receive()` returns a Mutiny `Multi` — backpressure propagated by the reactive stream. `close()` terminates both ends: pending `send()` calls get `ChannelClosedException`, `receive()` Multi completes.

### ChannelRef — serializable channel reference

```java
public record ChannelRef<T>(String name, Class<T> recordType) implements Serializable {
    public ChannelRef {
        Objects.requireNonNull(name);
        Objects.requireNonNull(recordType);
    }
    public static <T> ChannelRef<T> of(String name, Class<T> type) {
        return new ChannelRef<>(name, type);
    }
}
```

Follows the `DataRef` pattern — a serializable pointer that the engine resolves to a live `DataChannel<T>` at runtime. Can be an Exchange body type: Worker A creates a channel, passes `ChannelRef` to Worker B via Exchange or Blackboard, Worker B resolves it.

### WorkerScope channel access

```java
public interface WorkerScope {
    // ... existing methods ...

    default <T> DataChannel<T> channel(String name) {
        throw new UnsupportedOperationException("DataChannel requires engine context");
    }

    default <T> DataChannel<T> channel(ChannelRef<T> ref) {
        return channel(ref.name());
    }

    default <T> ChannelRef<T> createChannel(String name, Class<T> recordType) {
        throw new UnsupportedOperationException("DataChannel requires engine context");
    }
}
```

`WorkerRuntime` (engine-api) overrides with real implementations backed by the engine's channel registry.

### Engine tier — lifecycle and transport

**DataChannelFactory** (`engine-api`, extends `NamedStrategy`):

```java
public interface DataChannelFactory extends NamedStrategy {
    <T> DataChannel<T> create(String name, Class<T> recordType, UUID caseId);
}
```

**InMemoryDataChannelFactory** (`engine-common`, `@DefaultBean @ApplicationScoped`, id `"in-memory"`):
- Backed by bounded buffer with Mutiny `Multi` adapter
- Configurable buffer size (default 1024) for backpressure
- Zero external dependencies

**DataChannelRegistry** (`engine-common`, `@ApplicationScoped`):
- `ConcurrentHashMap<ChannelKey, DataChannel<?>>` where `ChannelKey = record(UUID caseId, String name)`
- `getOrCreate(caseId, name, recordType, factoryId)` — idempotent creation
- `closeByCase(caseId)` — bulk teardown on case terminal state
- `closeByScope(caseId, scopeId)` — teardown on compound completion

### Channel lifecycle

**Tier 1 (ad-hoc):** Worker calls `scope.createChannel("pipe", MyRecord.class)`. Registered in `DataChannelRegistry` scoped to the case. Lives until the worker function returns or the case terminates.

**Tier 2/3 (declared):** Channel declared on `CaseDefinition`. Engine creates when the owning scope activates. Bindings declare produce/consume:

```java
// CaseDefinition builder
.channel("tx-stream", TransactionRecord.class)                    // default in-memory
.channel("events", AuditEvent.class, "kafka")                     // named transport

// Binding builder
.produces("tx-stream")    // this binding produces to tx-stream
.consumes("tx-stream")    // this binding consumes from tx-stream
```

YAML:

```yaml
spec:
  channels:
    - name: tx-stream
      recordType: io.example.TransactionRecord
      transport: in-memory
      scope: compound        # or case (default)

bindings:
  - name: extract
    capability: data-extraction
    produces: tx-stream
  - name: transform
    capability: data-transformation
    consumes: tx-stream
```

**Scope lifecycle** reuses `LifecycleScope`:
- `CASE` (default): channel lives for case duration
- `COMPOUND`: created when compound activates, closed when compound completes

**Engine handlers:**
- `CaseStatusChangedHandler` calls `channelRegistry.closeByCase(caseId)` on terminal state
- `ScopedWorkerTerminationHandler` extended to close compound-scoped channels

## Engine Integration

### Tier 1: In-Worker Composition

No engine involvement. Workers pass Exchanges directly:

```java
WorkerResult<Exchange<Enriched>> result = scope.execute(enricherFunction, inputExchange);

var pipeline = WorkerFunctions.exchangeSequence(validate, enrich, format);
WorkerResult<Exchange<Formatted>> result = scope.execute(pipeline, rawExchange);
```

Properties reset between steps. Headers accumulate (merge). Short-circuit on non-Success.

### Tier 2: Binding-Driven Dispatch

Changes are additive — non-Exchange bindings untouched.

```
CaseContext change
  → CaseContextChangedEventHandler.publishByTarget()
      → [NEW] if worker function instanceof ExchangeProcessor:
          1. Build Exchange from input projection (body) + accumulated headers
          2. Serialize Exchange to EventLog metadata (body via ContextBridge, headers as-is)
          3. Publish WorkerScheduleEvent with Exchange reference
      → [EXISTING] else: normal Map-based input projection

  → WorkerScheduleEventHandler
      → [NEW] read Exchange from EventLog metadata if present
      → store in Quartz job data (body serialized via ContextBridge<bodyInputType>)

  → QuartzWorkerExecutionJob
      → [NEW] if ExchangeProcessor: deserialize Exchange, invoke fn(exchange, scope)
      → [EXISTING] else: normal deserialization

  → WorkflowExecutionCompletedHandler
      → [NEW] if result is WorkerResult<Exchange<R>>:
          1. Apply ExchangeProjectionStrategy
          2. Store outbound Exchange headers for next binding
          3. Audit Exchange metadata in EventLog
      → [EXISTING] else: normal output merge
      → publish CONTEXT_CHANGED (if projection strategy wrote to Blackboard)
```

**Header threading across bindings:** When Binding A completes:
- Body → optionally projected to Blackboard (per strategy)
- Headers → stored in `CaseInstance` field `Map<String, Object> exchangeHeaders` (engine-managed, transient — not JPA-persisted in v1). Next Exchange-aware binding inherits these when building its input Exchange. Headers from successive bindings merge (last writer wins per key). Recovery after JVM restart replays from EventLog metadata (`exchangeHeaders` field).
- Properties → discarded (Tier 1 only)

**EventLog audit for Exchange-aware entries:** `exchangeHeaders`, `exchangeBodyType`, `projectionStrategy` added to EventLog metadata.

### Tier 3: SubCase Delegation

`SubCaseMapping` gains an Exchange-aware variant:

```java
public sealed interface SubCaseMapping permits Expression, Lambda, ExchangeMapping {
    record ExchangeMapping(String bodyExpression, Set<String> headerKeys) implements SubCaseMapping {}
}
```

- `bodyExpression` — JQ against Exchange body → child case input
- `headerKeys` — which headers propagate to child (empty = none, null = all)

### What stays the same

- Binding trigger evaluation — still against CaseContext. Exchange changes HOW data flows, not WHEN bindings fire.
- PlanItem lifecycle — ExchangeProcessor bindings still create PlanItems (PENDING→RUNNING→COMPLETED).
- Retry/failure — `QuartzRetryService` replays Exchange from EventLog.
- Routing — `AgentRoutingStrategy` unaffected. Exchange data doesn't influence worker selection.
- Oversight gates — `ActionRiskClassifier` still sees `PlannedAction` on `WorkerResult`.

## Blackboard Projection Strategy

**Module:** `engine-api` (SPI), `engine-common` (implementations)

### SPI

```java
public interface ExchangeProjectionStrategy extends NamedStrategy {
    Set<String> project(Exchange<?> exchange, MutableCaseContext context);
}
```

### Built-in strategies

| Strategy | ID | Body → Blackboard | Headers → Blackboard | Exchange threaded |
|----------|-----|-------------------|---------------------|-------------------|
| `DualWriteProjection` | `"dual-write"` | Yes (via ConflictResolver) | No | Yes |
| `ExchangeOnlyProjection` | `"exchange-only"` | No | No | Yes |
| `FullProjection` | `"full"` | Yes | Yes (namespaced: `_exchange.<binding>.headers`) | Yes |
| `CustomJqProjection` | `"jq"` | JQ expression result | JQ expression result | Yes |

`DualWriteProjection` is `@DefaultBean @ApplicationScoped`. Non-Exchange workers completely unaffected.

### Binding declaration

```java
Binding.builder()
    .name("enricher")
    .capability(enrichmentCapability)
    .on(new ContextChangeTrigger(".raw != null"))
    .exchangeOnly()                          // shorthand
    .build();

Binding.builder()
    .name("auditor")
    .capability(auditCapability)
    .on(new ContextChangeTrigger(".enriched != null"))
    .projectWith("jq", "{ auditRecord: .body, source: .headers.sourceSystem }")
    .build();
```

YAML:

```yaml
bindings:
  - name: enricher
    capability: enrichment
    on: { contextChange: ".raw != null" }
    exchangeProjection: exchange-only

  - name: auditor
    capability: audit
    on: { contextChange: ".enriched != null" }
    exchangeProjection:
      strategy: jq
      expression: "{ auditRecord: .body, source: .headers.sourceSystem }"
```

### Interaction with existing binding fields

- `conflictResolverStrategy` — applies to projected body under DualWrite/Full. ExchangeOnly bypasses.
- `producedKeys` — documents keys produced (Exchange headers for audit when ExchangeOnly).
- `outputSchema` (capability output projection) — applied to Exchange body before projection strategy. Headers pass through.

## Camel Adapter

**Module:** `casehubio/workers` → `workers-camel` (existing module, additions)

### CamelExchangeAdapter — static utility

```java
public final class CamelExchangeAdapter {

    public static void toCamel(Exchange<?> casehubExchange,
                                org.apache.camel.Exchange camelExchange) {
        camelExchange.getIn().setBody(casehubExchange.body());
        casehubExchange.headers().forEach(camelExchange.getIn()::setHeader);
        casehubExchange.properties().forEach(camelExchange::setProperty);
    }

    public static <R> Exchange<R> fromCamel(org.apache.camel.Exchange camelExchange,
                                             Class<R> bodyType) {
        R body = camelExchange.getIn().getBody(bodyType);
        Map<String, Object> headers = new LinkedHashMap<>(camelExchange.getIn().getHeaders());
        Map<String, Object> properties = new LinkedHashMap<>(camelExchange.getProperties());
        headers.entrySet().removeIf(e -> e.getKey().startsWith("Camel"));
        return new Exchange<>(body, headers, properties);
    }

    public static <R> WorkerResult<Exchange<R>> toResult(
            org.apache.camel.Exchange camelExchange, Class<R> bodyType) {
        if (camelExchange.getException() != null) {
            return WorkerResult.failed(camelExchange.getException().getMessage());
        }
        return WorkerResult.of(fromCamel(camelExchange, bodyType));
    }
}
```

### CamelExchangeWorkerFunction

```java
public record CamelExchangeWorkerFunction<T, R>(
    Class<T> bodyInputType,
    Class<R> bodyOutputType,
    String routeUri
) implements WorkerFunction<Exchange<T>, Exchange<R>> {
    @Override @SuppressWarnings("unchecked")
    public Class<Exchange<T>> inputType() { return (Class) Exchange.class; }
    @Override @SuppressWarnings("unchecked")
    public Class<Exchange<R>> outputType() { return (Class) Exchange.class; }
}
```

### CamelExchangeWorkerFunctionHandler

`@ApplicationScoped`, implements `WorkerFunctionHandler`. Dispatches to `ProducerTemplate.request()`, bridges via `CamelExchangeAdapter`, returns `HandlerResult` with Camel protocol metadata (`camelRouteUri`, `camelExchangeId`).

### YAML

```yaml
workers:
  - name: file-enricher
    capabilities: [enrichment]
    camel:
      route: direct:enrich-file
      exchange: true
      bodyInputType: io.example.RawRecord
      bodyOutputType: io.example.EnrichedRecord
```

`exchange: true` triggers `CamelWorkerFunctionProvider` to create `CamelExchangeWorkerFunction` instead of the existing non-Exchange path.

## Module Placement Summary

### `casehubio/worker` (foundation tier — `casehub-worker-api`)

| Type | Description |
|------|-------------|
| `Exchange<T>` | Immutable data envelope |
| `ChannelRef<T>` | Serializable channel reference |
| `DataChannel<T>` | Minimal interface — send, receive, close |
| `ChannelClosedException` | Runtime exception |
| `WorkerFunction.ExchangeProcessor<T,R>` | New WorkerFunction variant |
| `ExchangeProcessorBuilder<T>` | Builder (mirrors TypedFunctionBuilder) |

`WorkerScope` gains `channel(String)`, `channel(ChannelRef)`, `createChannel(String, Class)` as default methods.
`Worker.Builder` gains `exchange(BiFunction)` and `<T>exchange()`.
`WorkerFunctions` gains `exchangeSequence(ExchangeProcessor...)`.

### `casehubio/engine` — `casehub-engine-api`

| Type | Description |
|------|-------------|
| `ExchangeProjectionStrategy` | NamedStrategy SPI |
| `DataChannelFactory` | NamedStrategy SPI |
| `ChannelDeclaration` | Record on CaseDefinition |

### `casehubio/engine` — `casehub-engine-common`

| Type | Description |
|------|-------------|
| `DataChannelRegistry` | Per-case channel tracking |
| `InMemoryDataChannel<T>` | Bounded buffer + Multi |
| `InMemoryDataChannelFactory` | `@DefaultBean`, id `"in-memory"` |
| `DualWriteProjection` | `@DefaultBean`, id `"dual-write"` |
| `ExchangeOnlyProjection` | id `"exchange-only"` |
| `FullProjection` | id `"full"` |
| `CustomJqProjection` | id `"jq"` |
| `ExchangeSerializer` | Exchange ↔ EventLog serialization |

### `casehubio/engine` — `casehub-engine` (runtime)

Handler changes (additive — Exchange-aware branches alongside existing paths):
- `CaseContextChangedEventHandler` — build Exchange from input projection
- `WorkerScheduleEventHandler` — serialize Exchange to EventLog
- `QuartzWorkerExecutionJob` — deserialize Exchange, invoke ExchangeProcessor
- `WorkflowExecutionCompletedHandler` — apply projection strategy, store headers
- `CaseStatusChangedHandler` — channel teardown on terminal state
- `ScopedWorkerTerminationHandler` — compound-scoped channel teardown
- `DefaultWorkerRuntime` — channel()/createChannel() overrides
- `EngineStrategyResolver` — ExchangeProjectionStrategy + DataChannelFactory instances

### `casehubio/workers` — `workers-camel`

| Type | Description |
|------|-------------|
| `CamelExchangeAdapter` | casehub Exchange ↔ Camel Exchange |
| `CamelExchangeWorkerFunction<T,R>` | Route URI + body types |
| `CamelExchangeWorkerFunctionHandler` | WorkerFunctionHandler impl |
| `CamelWorkerFunctionProvider` | Detects `exchange: true` in YAML |

### Dependency flow

```
casehub-worker-api          ← Exchange, DataChannel, ChannelRef, ExchangeProcessor
    ↑
casehub-engine-api          ← ExchangeProjectionStrategy, DataChannelFactory
    ↑
casehub-engine-common       ← DataChannelRegistry, InMemoryDataChannel, projections
    ↑
casehub-engine (runtime)    ← handler changes, WorkerRuntime overrides
    ↑
casehub-workers-camel       ← CamelExchangeAdapter, CamelExchangeWorkerFunction
```

No circular dependencies. Foundation tier has zero engine knowledge.

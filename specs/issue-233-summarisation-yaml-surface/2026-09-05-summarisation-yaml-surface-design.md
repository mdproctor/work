# Summarisation YAML Surface with Composable Runtime

**Issues:** #231, #232, #233
**Design spec:** casehubio/casehub-desiredstate#74 — Summarisation→RAS Integration (this spec resolves the blocks side; Option 1 — loose coupling via CloudEvents. desiredstate#74 is closed; no child issues required on the desiredstate side.)
**Date:** 2026-09-05

## Overview

Three new Maven modules that layer a declarative YAML pipeline surface over the existing summarisation framework. The extraction (#231) creates a zero-dependency API module. The CloudEvent bridge (#232) provides ingestion and emission adapters. The YAML surface (#233) adds a canonical model, pipeline compiler, and five built-in summariser types.

## Module Architecture

```
casehub-blocks-summarisation-api  (zero external deps, pure Java)
        ↑
        ├── casehub-blocks  (existing — retains ContentSummariser + observation)
        ├── casehub-blocks-cloudevents  (api + cloudevents-api)
        └── casehub-blocks-summarisation-yaml  (api + cloudevents + platform-api)
                └── casehub-blocks-summarisation-yaml-deployment  (Quarkus build extension)
```

| Module | Artifact ID | Dependencies | Contents |
|---|---|---|---|
| `summarisation-api/` | `casehub-blocks-summarisation-api` | jspecify only | 10 extracted types + LevelEvent with tenancyId |
| `cloudevents/` | `casehub-blocks-cloudevents` | summarisation-api, cloudevents-api | Ingestion adapter, emitter, EventSink SPI, tick scheduler |
| `summarisation-yaml/` | `casehub-blocks-summarisation-yaml` | summarisation-api, cloudevents, platform-api (provided) | Canonical model, PipelineCompiler, SummariserRegistry, 5 built-in types |
| `summarisation-yaml-deployment/` | `casehub-blocks-summarisation-yaml-deployment` | summarisation-yaml, quarkus-core-deployment | Jandex scan, YAML validation, recorder, CDI bean registration |

## Module 1: Summarisation API (#231)

### Extracted Types

All move from `blocks/blocks/src/main/java/io/casehub/blocks/summarisation/` to `summarisation-api/` in the same package (`io.casehub.blocks.summarisation`). Split package — acceptable without JPMS.

| Type | Kind | Changes |
|---|---|---|
| `LevelEvent<E>` | record | Gains `@Nullable String tenancyId` as 4th component (D1) |
| `EventLevel` | record | None |
| `WindowPolicy` | record | None |
| `Summariser<IN, OUT>` | interface | None (includes `SyncSummariser` inner interface) |
| `Compactor<E>` | interface | None |
| `EventAccumulator<E>` | class | None |
| `EventStreamBus<E>` | class | Drops deprecated `clear()` — only `clearSubscriptions()` exported |
| `SummarisationRunner<IN, OUT>` | class | None |
| `KeyedAccumulator<K, E>` | class | Key extractor widened: `Function<E, K>` → `Function<LevelEvent<E>, K>` (D12) |
| `KeyedSummarisationRunner<K, IN, OUT>` | class | Same key extractor change as KeyedAccumulator (D12) |

### What Stays in blocks

`ContentSummariser<T>`, `VerbatimContentSummariser`, `TieredContentSummariser`, `ContentSummariserToSummariser`, `SummaryMode` — depend on qhorus-api (`SummaryResult`). `LlmContentSummariser` — depends on platform-agent-api. Entire `observation` package — depends on qhorus-api and platform-agent-api.

### Breaking Changes

1. **LevelEvent** — 3-arg → 4-arg canonical constructor. All callers pass tenancyId explicitly (null for single-tenant). No convenience factory — compiler-enforced decision at every call site (D1).
2. **KeyedAccumulator / KeyedSummarisationRunner** — key extractor takes `LevelEvent<E>` not `E`. Existing callers add `.payload()`: `e -> e.transactionId()` becomes `event -> event.payload().transactionId()` (D12).

3. **`casehub-blocks` gains compile dependency on `casehub-blocks-summarisation-api`** — types remaining in blocks (`ContentSummariserToSummariser`, `ChannelEventAdapter`, `ChannelEventPublisher`) reference extracted types (`Summariser`, `LevelEvent`, `EventLevel`, `EventStreamBus`). A new `<dependency>` entry in `blocks/pom.xml` is required.

### Downstream Migration

| Consumer | Call sites | Migration |
|---|---|---|
| quarkmind | ~6 production | LevelEvent constructor + key extractor |
| blocks tests | ~90 test | LevelEvent constructor |
| logistics/clinical examples | ~20 test | LevelEvent constructor |

## Module 2: CloudEvent Bridge (#232)

### Components

All plain Java — no CDI, no Quarkus dependency.

| Type | Kind | What it does |
|---|---|---|
| `EventSink<T>` | `@FunctionalInterface` | `void emit(T event)` — consumer provides CDI wiring (D2) |
| `CloudEventIngestionAdapter<E>` | class | Filters CloudEvents by type pattern, deserializes payload to `E`, wraps as `LevelEvent<E>` with tenancyId from CE extension, publishes to `EventStreamBus<E>` |
| `CloudEventEmitter<E>` | class | Subscribes to output `EventStreamBus<E>`, wraps `LevelEvent<E>` as CloudEvent with configurable type URI + tenancyId extension, fires via `EventSink<CloudEvent>` |
| `PipelineTickScheduler` | class | Drives `tick(now)` on runners at configurable interval. Tick interval is a deployment concern, not pipeline definition (D11) |

### TenancyId Flow and Tenant Isolation

Tenant isolation is a framework guarantee, not a consumer responsibility.

**Ingestion:** CloudEvent arrives with `tenancyid` extension → ingestion adapter extracts → `new LevelEvent<>(payload, timestamp, level, tenancyId)`.

**Automatic tenant partitioning:** The `PipelineCompiler` interposes tenant partitioning at each pipeline level. Each tenant's events are accumulated and summarised independently — the YAML author never thinks about tenancy.

- **Windowed grouping:** each tenant gets its own `EventAccumulator` instance. Window triggers (count, age) are evaluated per-tenant.
- **Keyed grouping:** each tenant gets its own `KeyedAccumulator` instance. Key groups are inherently tenant-scoped.
- **Single-tenant (null tenancyId):** all events share a single default partition. Zero overhead.

**TenancyId propagation:** Runners propagate tenancyId from input batch to output events. Since tenant partitioning guarantees each batch is tenant-homogeneous, the batch's tenancyId is applied to all output `LevelEvent`s via the 4-arg constructor.

**Emission:** CloudEventEmitter reads `event.tenancyId()` → sets on outbound CloudEvent as `tenancyid` extension.

### Consumer Wiring (~30 lines CDI)

```java
// Consumer provides the CDI bridge
new CloudEventEmitter<>(outputBus, ce -> cdiEvent.fireAsync(ce), typeUri);
```

## Module 3: Summarisation YAML Surface (#233)

### Canonical Model

The pipeline definition is a tree of immutable records. Both YAML (Jackson deserialization) and Java (direct construction) target these. The `PipelineCompiler` consumes them.

```java
record PipelineDefinition(
    String name,
    SourceDefinition source,
    List<LevelDefinition> levels) {}

record SourceDefinition(
    String type,                         // Java class name (Tier 2) or null (Tier 1 → Map)
    @Nullable String cloudEventType) {}  // CE type pattern for ingestion

record LevelDefinition(
    String name,
    GroupingDefinition grouping,
    SummariserDefinition summariser,
    @Nullable EmitDefinition emit,
    List<String> aggregateFields) {}     // explicit fields for pre-computation (D10)

sealed interface GroupingDefinition {
    record Windowed(WindowPolicy policy) implements GroupingDefinition {}
    record Keyed(String keyExpression,
                 String completionExpression,
                 long staleTimeout) implements GroupingDefinition {}
}

record SummariserDefinition(
    String type,                         // built-in name or @SummariserTypeId
    Map<String, Object> config) {}       // type-specific config

record EmitDefinition(
    String cloudEventType) {}            // type URI for outbound CloudEvents
```

### DSL Parity

YAML and Java are peer representations of the same canonical model (D13). The model records are the source of truth — YAML deserializes to them via Jackson, Java constructs them directly. A future `yaml-core` auto-binding capability could generate YAML deserialization from a Java builder API automatically, but the current design does not depend on this.

### YAML Grammar

Levels implicitly chain — level N's output feeds level N+1's input (D3). Per-level optional CloudEvent emission (D5).

```yaml
pipeline:
  name: logistics-hub
  source:
    type: io.example.PackageScan
    cloud-event-type: io.casehub.logistics.scan.v1
  levels:
    - name: anomalies
      grouping:
        type: windowed
        count: 10
      summariser:
        type: threshold-classify
        rules:
          - name: weight-mismatch
            when: "weight > 50.0"
            category: WEIGHT_MISMATCH
      emit:
        cloud-event-type: io.casehub.logistics.anomaly.v1

    - name: phases
      grouping:
        type: windowed
        age: 300000
      aggregate-fields: [severity]
      summariser:
        type: phase-detect
        initial: NORMAL_FLOW
        states: [NORMAL_FLOW, CONGESTION, RECOVERY]
        transitions:
          - from: NORMAL_FLOW
            to: CONGESTION
            when: "counts.severity.HIGH >= 3"
          - from: CONGESTION
            to: RECOVERY
            when: "counts.severity.HIGH == 0"
      emit:
        cloud-event-type: io.casehub.logistics.phase.v1
```

### Grouping Modes

A `grouping:` block with a `type:` discriminator (D4). Mutually exclusive modes:

| Type | Fields | Maps to |
|---|---|---|
| `windowed` | `age` (ms) and/or `count` | `WindowPolicy` → `SummarisationRunner` |
| `keyed` | `key` (expression), `completion` (expression), `stale-timeout` (ms) | `KeyedAccumulator` → `KeyedSummarisationRunner` |

### Five Built-in Summariser Types

| Type ID | Stateful? | What it does | Config |
|---|---|---|---|
| `threshold-classify` | No | Per-event MVEL3 boolean predicates. Iterates batch, evaluates each rule against each event's payload. Output: 0..N classifications per batch (one per event-rule match). | `rules: [{name, when, category}]` |
| `phase-detect` | Yes (per-partition) | State machine. Emit-on-transition-only, first-match-wins, explicit initial state (D8). See lifecycle details below. | `initial`, `states`, `transitions: [{from, to, when}]` |
| `count` | No | Per-category counts within the batch | `category-field` |
| `field-extract` | No | JQ document transformation. Tier 1 only (`E = Map`) — JQ operates on JSON documents, not typed POJOs (D7). See §Generic Payload Model. | `expression` (JQ string) |
| `pass-through` | No | Identity rebatching — level exists for grouping/emission, not transformation | (none) |

All implement `Summariser<E, OUT>`. `phase-detect` state is framework-managed, per-partition (tenant × key group), in-memory. State lost on restart — persistence is Tier 2 territory (D8).

#### phase-detect Lifecycle

**State granularity:** One state machine instance per partition. With automatic tenant partitioning, each tenant has its own state. With keyed grouping, each key group within a tenant has its own state machine.

**First-batch behavior:** The state machine starts at `initial`. On the first batch, transition rules are evaluated against the initial state. If a transition fires, it is a genuine state change that produces output. If no transition fires, state stays at initial with zero output. The initial state assignment is not itself a transition.

**No-match behavior:** If no transition's `when` predicate matches the current batch, state stays unchanged. No output is produced. Downstream levels see nothing for this tick — expected behavior for stable states.

**Self-transitions:** A transition where `from == to` IS a valid transition and DOES produce output when matched. This enables periodic "heartbeat" emissions in a stable state.

**Terminal states:** States reachable from initial but with no outbound transitions are valid. They represent stable end states (e.g., SHUTDOWN). The pipeline stops emitting after reaching a terminal state. Build-time validation reports terminal states as an info-level note, not an error.

#### Compaction

`Compactor<E>` is supported in the Java API (Tier 2) but has no YAML surface representation. Compaction logic is inherently domain-specific — deduplication strategies (by field, by time window, by priority) vary across use cases and cannot be expressed generically in YAML without creating a mini-language. Tier 2 Java-constructed pipelines have full compaction support via `SummarisationRunner` and `KeyedSummarisationRunner` constructors. Future YAML compaction support is deferred (issue TBD — `feat: YAML compactor block with built-in dedup strategy`).

### Expression Engine

Expressions evaluated via platform `ExpressionEngine` SPI — `CompiledExpression<C, R>` with `R eval(C)` (D9). This is a new precedent: typed compilation via `CompiledExpression` rather than the less-typed `ExpressionEvaluator` marker used by RAS situations.

| Expression position | Language | Context type | Result type |
|---|---|---|---|
| `threshold-classify` `when` | MVEL3 | Event payload (individual event) | `Boolean` |
| `phase-detect` `when` | MVEL3 | Pre-computed batch aggregates | `Boolean` |
| `keyed` `key` | MVEL3 | Event payload | `Object` |
| `keyed` `completion` | MVEL3 | Batch (list of events) | `Boolean` |
| `field-extract` `expression` | JQ | `JsonNode` (Tier 1 only) | `List<JsonNode>` |

Pre-computed batch aggregates (D10): `size`, `counts.<field>.<value>`, `sums.<field>`, `avgs.<field>`. Only declared `aggregate-fields` are pre-computed — bounded, no speculative enumeration. Used by `phase-detect`; not used by `threshold-classify` (which evaluates per-event, not per-batch).

### SummariserRegistry and Tier 2

```java
class SummariserRegistry {
    void register(String typeId, SummariserFactory<?> factory);
    <IN, OUT> Summariser<IN, OUT> create(String typeId, Map<String, Object> config);
}

@Retention(RUNTIME) @Target(TYPE)
@interface SummariserTypeId { String value(); }
```

Single registry, dual population (D6):
- **Build time:** Jandex discovers `@SummariserTypeId` classes, recorder populates registry
- **Runtime:** callers populate via `registry.register()` directly

### PipelineCompiler

```java
class PipelineCompiler {
    CompiledPipeline compile(
        PipelineDefinition definition,
        SummariserRegistry registry,
        ExpressionEngineRegistry expressions,
        @Nullable EventSink<CloudEvent> emitterSink);
}
```

Plain Java, no Quarkus dependency (D6). Dual-use: deployment module recorder (static path) and application code (dynamic path).

`CompiledPipeline` owns the wired object graph — buses, runners, emitters, tenant partitions.

```java
class CompiledPipeline<IN> {
    String name();
    EventStreamBus<IN> inputBus();
    CompletionStage<Void> tick(long now);
    CompletionStage<Void> flush();
}
```

- Generic over `IN` — Tier 1: `CompiledPipeline<Map<String, Object>>`, Tier 2: `CompiledPipeline<DomainType>`
- `inputBus()` — the entry point. `CloudEventIngestionAdapter` subscribes here; Java callers publish directly.
- `tick(long now)` — drives all runners across all tenant partitions. Returns `CompletionStage.allOf()` over all runner ticks.
- `flush()` — unconditional drain at shutdown. Returns `CompletionStage<Void>` for graceful shutdown coordination.
- Intermediate buses are internal — external observation is via per-level CloudEvent emission (`emit:` block) or Tier 2 direct construction.
- The `@Scheduled` tick driver is wired by the deployment module, not part of `CompiledPipeline`.

### Build-time Processing (Deployment Module)

1. Classpath scan for `META-INF/summarisation/*.yaml`
2. `@SummariserTypeId` registry scan via Jandex
3. Validation: unknown summariser types, unreachable states (phase-detect), cross-level type consistency, mutual exclusivity (windowed vs keyed)
4. `@Record(RUNTIME_INIT)` recorder: constructs `PipelineCompiler`, compiles all discovered pipelines, registers `CompiledPipeline` beans
5. `@Scheduled` tick driver registration with configurable interval:
   - Config property: `casehub.summarisation.tick-interval-ms` (long, milliseconds)
   - Default: `1000` (1 second)
   - Global — applies to all compiled pipelines in the application. Per-pipeline intervals are a future concern (issue TBD if needed).

### Validation Rules

| Rule | What it catches |
|---|---|
| Unknown summariser type | Type ID not in registry (built-in or @SummariserTypeId) |
| Unreachable states | phase-detect state not reachable from initial via any transition path |
| Grouping mutual exclusivity | Both `windowed` and `keyed` fields present |
| Missing required config | phase-detect without `initial`, threshold-classify without `rules` |
| Expression syntax | MVEL3/JQ parse errors at build time |

## Generic Payload Model

The pipeline is generic throughout — `LevelEvent<E>` (D7). Tier 1 (YAML-only) uses `E = Map<String, Object>` from JSON deserialization. Tier 2 uses domain types. MVEL3 property access handles both transparently.

JQ (`field-extract`) requires `JsonNode` — Tier 1 only. For Tier 1 (`E = Map<String, Object>`), the platform `JQExpressionEngine` handles serialization transparently via `MapAdaptedJQExpression` (`ObjectMapper.valueToTree()`). For Tier 2 (domain types), `field-extract` is not supported — use MVEL3-based summariser types (`threshold-classify`, `phase-detect`) which handle typed POJOs natively, or Java-constructed summarisers. This is a design decision: JQ is a JSON document transformation tool, and automating POJO→JsonNode serialization would create a leaky abstraction where field renames break silently at runtime (D7 trade-off).

## References

- `blocks/blocks/src/main/java/io/casehub/blocks/summarisation/LevelEvent.java` — current 3-arg record
- `blocks/blocks/src/main/java/io/casehub/blocks/summarisation/SummarisationRunner.java` — runner implementation
- `blocks/blocks/src/main/java/io/casehub/blocks/summarisation/KeyedAccumulator.java` — key extractor signature
- `blocks/blocks/src/test/java/io/casehub/blocks/summarisation/examples/logistics/LogisticsPipelineTest.java` — pipeline wiring pattern
- `io.casehub.platform.api.expression.ExpressionEngine` — platform expression SPI (javap analysis)
- `io.casehub.platform.api.expression.CompiledExpression` — generic `R eval(C)` contract
- casehubio/casehub-desiredstate#74 — RAS integration scope
- [GE-20260813-keyed-summarisation-runner-api] — KeyedSummarisationRunner uses completionTest, not WindowPolicy
- [GE-20260813-partitioned-observation-single-typed] — PartitionedObservationService is single-typed

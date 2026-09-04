# Summarisation YAML Surface with Composable Runtime

**Issues:** #231, #232, #233
**Design spec:** casehubio/casehub-desiredstate#74 — Summarisation→RAS Integration
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
| `EventStreamBus<E>` | class | None |
| `SummarisationRunner<IN, OUT>` | class | None |
| `KeyedAccumulator<K, E>` | class | Key extractor widened: `Function<E, K>` → `Function<LevelEvent<E>, K>` (D12) |
| `KeyedSummarisationRunner<K, IN, OUT>` | class | Same key extractor change as KeyedAccumulator (D12) |

### What Stays in blocks

`ContentSummariser<T>`, `VerbatimContentSummariser`, `TieredContentSummariser`, `ContentSummariserToSummariser`, `SummaryMode` — depend on qhorus-api (`SummaryResult`). `LlmContentSummariser` — depends on platform-agent-api. Entire `observation` package — depends on qhorus-api and platform-agent-api.

### Breaking Changes

1. **LevelEvent** — 3-arg → 4-arg canonical constructor. All callers pass tenancyId explicitly (null for single-tenant). No convenience factory — compiler-enforced decision at every call site (D1).
2. **KeyedAccumulator / KeyedSummarisationRunner** — key extractor takes `LevelEvent<E>` not `E`. Existing callers add `.payload()`: `e -> e.transactionId()` becomes `event -> event.payload().transactionId()` (D12).

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

### TenancyId Flow

CloudEvent arrives with `tenancyid` extension → ingestion adapter extracts → `new LevelEvent<>(payload, timestamp, level, tenancyId)` → flows through runners (tenant-homogeneous batches via KeyedAccumulator with tenancyId key) → emitter reads `event.tenancyId()` → sets on outbound CloudEvent.

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
      aggregate-fields: [weight, warehouseId]
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
| `threshold-classify` | No | MVEL3 boolean predicates, 1:1 output cardinality | `rules: [{name, when, category}]` |
| `phase-detect` | Yes (per-tenant) | State machine. Emit-on-transition-only, first-match-wins, explicit initial state (D8) | `initial`, `states`, `transitions: [{from, to, when}]` |
| `count` | No | Per-category counts within the batch | `category-field` |
| `field-extract` | No | JQ document transformation. Requires JsonNode — typed payloads serialized before JQ (D7) | `expression` (JQ string) |
| `pass-through` | No | Identity rebatching — level exists for grouping/emission, not transformation | (none) |

All implement `Summariser<E, OUT>`. `phase-detect` state is framework-managed, per-tenant, in-memory. State lost on restart — persistence is Tier 2 territory (D8).

### Expression Engine

Expressions evaluated via platform `ExpressionEngine` SPI — `CompiledExpression<C, R>` with `R eval(C)` (D9). This is a new precedent: typed compilation via `CompiledExpression` rather than the less-typed `ExpressionEvaluator` marker used by RAS situations.

| Expression position | Language | Context type | Result type |
|---|---|---|---|
| `threshold-classify` / `phase-detect` `when` | MVEL3 | Batch aggregates + `batch` escape hatch | `Boolean` |
| `keyed` `key` / `completion` | MVEL3 | Event payload (key) / batch (completion) | `Object` / `Boolean` |
| `field-extract` `expression` | JQ | `JsonNode` | `List<JsonNode>` |

Pre-computed batch aggregates (D10): `size`, `counts.<field>.<value>`, `sums.<field>`, `avgs.<field>`. Only declared `aggregate-fields` are pre-computed — bounded, no speculative enumeration.

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

`CompiledPipeline` owns the wired object graph — buses, runners, emitters. Exposes `tick(long now)` and `flush()`.

### Build-time Processing (Deployment Module)

1. Classpath scan for `META-INF/summarisation/*.yaml`
2. `@SummariserTypeId` registry scan via Jandex
3. Validation: unknown summariser types, unreachable states (phase-detect), cross-level type consistency, mutual exclusivity (windowed vs keyed)
4. `@Record(RUNTIME_INIT)` recorder: constructs `PipelineCompiler`, compiles all discovered pipelines, registers `CompiledPipeline` beans
5. `@Scheduled` tick driver registration

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

JQ (`field-extract`) requires `JsonNode` — typed payloads need serialization to JsonNode before JQ can process them (D7 trade-off).

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

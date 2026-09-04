## D1: LevelEvent tenancyId — clean break, no convenience factory

**Choice:** Add `@Nullable String tenancyId` as 4th record component. No convenience factory — all callers pass tenancyId explicitly (null or a value).
**Alternatives:**
- Convenience factory `LevelEvent.of(payload, timestamp, level)` — backward-compatibility shim; hides the tenancyId decision point behind a default
- Both constructor + factory — redundant
**Rationale:** Breaking change is the point: every caller must choose whether they are tenant-aware. A process guard ("code review must catch") is weaker than a design guard (compiler forces the choice). ~6 production call sites and ~90 test call sites — mechanical migration.
**Trade-offs:** Noisier diffs for the migration commit. Every test fixture must pass an explicit null tenancyId.
**Sources:** LevelEvent.java (current 3-arg record), #231 issue body (breaking change spec), R1-01 reviewer challenge
**Exploration:** quick
**Status:** revised — removed convenience factory per R1-01; design philosophy requires explicit decisions at every call site

## D2: CloudEvent bridge — plain Java SPI, no CDI dependency

**Choice:** CloudEvent bridge module defines a `@FunctionalInterface EventSink<T>` for emission. No quarkus-arc dependency — overrides #232's dependency list, which includes quarkus-arc. Consumer provides CDI wiring as a lambda.
**Alternatives:**
- Direct quarkus-arc dependency (module uses `@Inject Event<CloudEvent>`) — simpler consumer wiring but couples module to Quarkus, untestable without CDI container
- `Consumer<CloudEvent>` instead of named `EventSink<T>` — saves one type but loses domain naming and independent evolution
**Rationale:** Keeps the module testable with plain JUnit (no CDI container). Consumer wiring is ~1 lambda. Follows the same pattern as other blocks modules (plain Java + consumer CDI wiring). Named SPI convention is consistent with `Summariser<IN, OUT>`, `ContentSummariser<T>`, `Compactor<IN>` — domain operations get named types, not raw functional interfaces.
**Trade-offs:** Consumer writes ~1 extra line of wiring code per pipeline.
**Sources:** #232 issue body (plain Java utilities), blocks module patterns (VouchService, Summariser, ContentSummariser), R1-02 reviewer challenge
**Exploration:** quick
**Status:** captured

## D3: YAML pipeline topology — implicit chaining

**Choice:** Levels are ordered — level N's output feeds level N+1's input. A single `source:` block defines the external input. Linear topology enforced by construction.
**Alternatives:**
- Explicit source per level (each level names its input) — enables future non-linear topologies but adds verbosity and ambiguity for the linear case
**Rationale:** The issue specifies "linear topology only — non-linear requires Tier 2." Implicit chaining enforces this at the grammar level rather than requiring validation. YAGNI — non-linear can be added later by extending the grammar.
**Trade-offs:** Non-linear topologies (fan-out, diamond) require Tier 2 (Java code). If demand emerges, grammar extension needed.
**Sources:** #233 issue body (linear topology constraint), LogisticsPipelineTest.java (wiring pattern — sequential bus→runner→bus chain)
**Exploration:** quick
**Status:** captured

## D4: Grouping — `grouping:` block with type discriminator

**Choice:** A single `grouping:` block with a `type:` discriminator replaces mutually exclusive top-level blocks. Type values: `keyed` (key expression, completion predicate, stale timeout) and `windowed` (maxAge, maxCount via WindowPolicy).
**Alternatives:**
- Mutually exclusive `keyed:` / `window:` top-level blocks — self-discriminating for two modes but doesn't scale to additional grouping modes (session windowing, sliding windows)
- Separate level type field redundant with grouping block
**Rationale:** Two mutually exclusive top-level blocks is manageable; five is not. Session windowing and sliding windows are legitimate event-processing patterns that could be added as new `type:` values without new top-level keys. The `grouping:` block follows conventional YAML schema patterns.
**Trade-offs:** One extra nesting level in YAML. Type discriminator field must be validated.
**Sources:** KeyedSummarisationRunner.java, WindowPolicy.java, R1-04 reviewer challenge
**Exploration:** quick
**Status:** revised — adopted grouping: block with type discriminator per R1-04

## D5: CloudEvent emission — per-level optional

**Choice:** Any level can declare an `emit:` block with a `cloud-event-type` URI. Non-emitting levels feed the next level internally only. Emission is unconditional on the level's output — the summariser controls what is produced (including empty results for no-emit conditions). Consumers filter by CloudEvent type URI.
**Alternatives:**
- Terminal-only emission (only last level emits) — simpler but prevents RAS Ganglia from subscribing to mid-pipeline events (e.g., L2 anomalies AND L3 phases)
- Conditional `when:` predicate on `emit:` block — adds output-level filtering beyond what the summariser produces; deferred to future extension if needed
**Rationale:** The desiredstate#74 spec describes RAS consuming events at multiple altitude levels. Per-level emission enables this without separate pipelines. Per-level emission is expected to be sparse in practice (1-2 emitting levels per pipeline, not all 5). Consumers that receive overlapping information at different abstraction levels filter by type URI — this is the standard CloudEvent consumption pattern.
**Trade-offs:** More complex emission wiring; each emitting level needs a CloudEventEmitter instance. Depends on D2 (EventSink<T>).
**Sources:** casehubio/casehub-desiredstate#74 (RAS integration), #232 issue body, R1-05 reviewer challenge
**Exploration:** quick
**Status:** revised — added cardinality guidance, conditional emission rationale, D2 dependency per R1-05

## D6: Pipeline compilation — single SummariserRegistry, dual population

**Choice:** A single `SummariserRegistry` holds all discovered summariser types. Build-time: Jandex discovery populates the registry via recorder. Runtime: callers populate it via API. `PipelineCompiler` resolves types exclusively against the registry — one resolution path, one set of error messages.
**Alternatives:**
- Dual-path compiler with Jandex at build-time and registry at runtime — two type-resolution mechanisms, divergent error behavior
- Build-time only (compiler locked behind deployment module) — prevents runtime pipeline construction
- Full build-time codegen — faster startup but adds code generation complexity
**Rationale:** The compiler should have exactly one way to resolve types. Whether Jandex or a manual API populated the registry is an implementation detail of registry population, not a compiler concern. Build-time and runtime compilation use the same validation depth — acceptable since pipeline compilation is a startup-time operation, not per-request.
**Trade-offs:** Dynamic path still skips Jandex-based @SummariserTypeId discovery (no classpath scan). Dynamic callers register types explicitly. Runtime compilation pays the same validation cost as build-time.
**Sources:** #233 issue body, Quarkus extension patterns, R1-06 reviewer challenge
**Exploration:** quick
**Status:** revised — unified type resolution through single registry per R1-06

## D7: Event payload type — generic throughout

**Choice:** The pipeline stays generic (`LevelEvent<E>`) throughout. No special-casing for Tier 1 vs Tier 2. Tier 1 (YAML-only) happens to use `E = Map<String, Object>` from JSON deserialization; Tier 2 uses domain types. Built-in summariser types use expression evaluation for field access, which MVEL3 handles transparently for both maps and typed objects.
**Alternatives:**
- Map-only Tier 1 (framework special-cases Map) — makes Tier 1 a second-class citizen
- JsonNode payload — JQ-native but MVEL3 field access becomes verbose (`node.get("field").asDouble()`)
**Rationale:** The framework is correctly generic at the `LevelEvent<E>` level. Expression evaluation via MVEL3 property access handles both typed objects and maps transparently for predicate evaluation and field access.
**Trade-offs:**
- JQ expressions (D9) require JsonNode, not arbitrary types — typed payloads need serialization to JsonNode before JQ can process them. `field-extract` is not fully type-agnostic.
- Pre-computed aggregates (D10) use different code paths for maps (key enumeration) vs typed objects (reflection), with different error modes (key miss → null vs reflection failure → exception).
- Expression context assembly must handle both payload shapes.
**Sources:** Platform expression SPI, MVEL3 property access semantics, R1-07 reviewer challenge
**Exploration:** quick
**Status:** revised — acknowledged concrete trade-offs at expression evaluation and aggregate computation layers per R1-07

## D8: phase-detect initial state — explicit in YAML, framework-managed state

**Choice:** The `phase-detect` built-in declares `initial: <STATE>` in the YAML. Every new tenant starts in that state. First batch evaluates transitions from the initial state. Transitions are evaluated in YAML declaration order (first match wins). State is managed by the framework for built-in summarisers — per-tenant, in-memory.
**Alternatives:**
- First-match on first batch (no initial state — evaluate all transitions cold) — handles cold-start more accurately but adds edge cases (what if nothing matches?) and makes the default state implicit
- Stateless Summariser interface with state passed as parameter — violates @FunctionalInterface, forces state management onto all summarisers including those that don't need it
**Rationale:** Simple, predictable, no magic. The initial state is always visible in the YAML definition. The Summariser interface stays pure (batch → results) — @FunctionalInterface means single abstract method, not functional purity. Built-in YAML summarisers like phase-detect have framework-managed state internally. Transition evaluation order is YAML declaration order — explicit and documented.
**Trade-offs:** In-memory state is lost on restart — all tenants revert to initial state. State persistence (e.g., for clinical domains where restart safety matters) is Tier 2 territory. If tenant-specific initial states are needed, Tier 2 (custom Java) required.
**Sources:** #233 issue body (phase-detect spec), HubPhaseSummariser.java, R1-08 reviewer challenge
**Exploration:** quick
**Status:** revised — added evaluation order, state management model, persistence boundary per R1-08

## D9: Expression engine — platform expression SPI (new precedent)

**Choice:** Depend on `casehub-platform-api` (SPI: `ExpressionEngine`, `CompiledExpression<C,R>`, `ExpressionEngineRegistry`) and `casehub-platform-expression` (runtime: `MvelExpressionEngine`, `JQExpressionEngine`). No direct jackson-jq or MVEL3 dependency.
**Alternatives:**
- Bundle own jackson-jq/MVEL3 dependencies — duplicates expression infrastructure already provided by platform
- Depend on engine-api ExpressionEngine — thinner platform-api dependency avoids pulling in engine concepts (CaseContext-coupled methods like `evaluate(ExpressionEvaluator, CaseContext)`, `ExpressionEvaluator`-based APIs) that summarisation doesn't need
**Rationale:** Platform-api `CompiledExpression<C, R>` is fully generic (`R eval(C)`) — works for `CompiledExpression<BatchContext, Boolean>` (predicates) and `CompiledExpression<JsonNode, List<JsonNode>>` (JQ transforms). This is the first direct use of `CompiledExpression<C, R>` on the platform — RAS's `SituationDefinition` uses the marker `ExpressionEvaluator` interface instead. Establishes a new, cleaner precedent: typed compilation via `CompiledExpression` rather than the less-typed `ExpressionEvaluator`.
**Trade-offs:** Summarisation-yaml gains a `provided` dependency on casehub-platform-api. Consumers must have platform-expression on classpath for MVEL3/JQ evaluation — but any Quarkus app already does.
**Sources:** javap analysis of platform-api ExpressionEngine/CompiledExpression/ExpressionEngineRegistry, SituationDefinition.java (stores ExpressionEvaluator, not CompiledExpression), R1-09 reviewer challenge
**Exploration:** quick
**Status:** revised — corrected framing: new precedent (not following RAS), thinner dependency (not escaping coupling) per R1-09

## D10: Pre-computed batch aggregates with explicit field declaration

**Choice:** The YAML declares aggregate fields per level (`aggregate-fields: [severity, temperature]`). The runtime pre-computes batch statistics (size, counts by declared field value, sums/avgs of declared numeric fields) and exposes them as flat variables in the expression context. Raw batch available as `batch` for escape hatch.
**Alternatives:**
- Raw batch only (expression computes everything inline) — more flexible but expressions become complex and performance unpredictable
- Speculative aggregation of all fields — wasteful for large payloads, unbounded for high-cardinality fields (UUID, timestamp), surprising behavior
**Rationale:** Explicit field declaration makes pre-computation bounded and predictable. Summariser `when` expressions need aggregate statistics (e.g., "highSeverityCount >= 3"). Pre-computing only declared fields avoids speculative enumeration and unbounded count-by-value maps. The `batch` escape hatch preserves full flexibility for Tier 2 scenarios.
**Trade-offs:** One additional YAML line per level. The runtime must still introspect payloads for declared fields — reflection for typed objects, key access for maps.
**Sources:** HubPhaseSummariser.java (counts high-severity anomalies), AnomalyDetectorSummariser.java (weight statistics), R1-10 reviewer challenge
**Exploration:** quick
**Status:** revised — added explicit aggregate-fields declaration, bounded pre-computation per R1-10

## D11: Tick interval — deployment concern, not pipeline definition

**Choice:** The tick interval is a deployment/runtime parameter, not part of the pipeline YAML definition. The consumer (or deployment module) configures tick scheduling. The YAML defines WHAT the pipeline does; the tick interval defines HOW OFTEN it's checked.
**Alternatives:**
- Tick interval in YAML per pipeline — couples operational tuning to pipeline definition, requires recompilation for frequency changes
- Computed from WindowPolicy parameters — possible (Nyquist: tick at 2x shortest maxAge), but constrains operational flexibility
- Hardcoded — inflexible, different domains need different timescales
**Rationale:** Separation of concerns: pipeline topology and summarisation logic belong in YAML; scheduling frequency belongs in deployment configuration. WindowPolicy maxAge implicitly lower-bounds the useful tick interval (no point ticking faster than the shortest window), but the actual tick rate is an operational choice.
**Trade-offs:** Pipeline authors must configure tick scheduling separately. No guidance in the YAML about expected timescales.
**Sources:** SummarisationRunner.tick(long now), quarkmind game-loop origin, R1-12 reviewer challenge
**Exploration:** quick (surfaced by reviewer)
**Status:** captured

## D12: Tenant partitioning — KeyedAccumulator with tenancyId

**Choice:** Windowed levels that require tenant-homogeneous batches use `KeyedAccumulator` with `tenancyId` as the grouping key. The WindowPolicy completion test is adapted as the `completionTest` predicate. Keyed levels already partition by their declared key; if multi-tenant, the key naturally includes tenancyId.
**Alternatives:**
- New TenantAwareAccumulator wrapping EventAccumulator — adds a type that duplicates KeyedAccumulator's functionality
- One EventAccumulator per tenant — lifecycle management overhead, unbounded accumulator creation
- EventAccumulator becomes `Map<String, List<LevelEvent<E>>>` internally — breaks the simple flat-buffer contract
**Rationale:** KeyedAccumulator already exists and handles per-key grouping with completion predicates and stale timeouts. Using tenancyId as the key reuses this proven component. The framework creates the appropriate accumulator based on whether the pipeline is multi-tenant (tenancyId present in source events).
**Trade-offs:** Windowed levels in multi-tenant pipelines use keyed accumulation under the hood — slightly different batching semantics (per-tenant windows rather than global windows). This is correct behavior: tenants should have independent windows.
**Sources:** KeyedAccumulator.java, EventAccumulator.java, LevelEvent.tenancyId (D1), R1-13 reviewer challenge
**Exploration:** quick (surfaced by reviewer)
**Status:** captured

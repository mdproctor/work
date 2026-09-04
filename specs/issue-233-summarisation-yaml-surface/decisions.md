## D1: LevelEvent tenancyId — convenience factory migration

**Choice:** Add `@Nullable String tenancyId` as 4th record component with a static `LevelEvent.of(payload, timestamp, level)` convenience factory that passes null tenancyId
**Alternatives:**
- Clean break (all callers pass null explicitly) — honest but noisier diffs across quarkmind, blocks tests, examples
- Both constructor + factory — redundant; the factory IS the migration path
**Rationale:** Self-documenting intent ("I don't have a tenant"), minimal caller diff. Multi-tenant callers use the 4-arg canonical constructor directly.
**Trade-offs:** Callers that should be tenant-aware might hide behind the convenience factory; code review must catch this.
**Sources:** LevelEvent.java (current 3-arg record), #231 issue body (breaking change spec), #232 issue body (tenancyId propagation requirement)
**Exploration:** quick
**Status:** captured

## D2: CloudEvent bridge — plain Java SPI, no CDI dependency

**Choice:** CloudEvent bridge module defines a `@FunctionalInterface EventSink<T>` for emission. No quarkus-arc dependency. Consumer provides CDI wiring as a lambda.
**Alternatives:**
- Direct quarkus-arc dependency (module uses `@Inject Event<CloudEvent>`) — simpler consumer wiring but couples module to Quarkus, untestable without CDI container
**Rationale:** Keeps the module testable with plain JUnit (no CDI container). Consumer wiring is ~1 lambda. Follows the same pattern as other blocks modules (plain Java + consumer CDI wiring).
**Trade-offs:** Consumer writes ~1 extra line of wiring code per pipeline.
**Sources:** #232 issue body (plain Java utilities), blocks module patterns (e.g., VouchService — consumer-constructed, not CDI-managed)
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

## D4: Keyed levels — inline `keyed:` block

**Choice:** A `keyed:` block replaces `window:` (mutually exclusive). Contains `key` (expression), `completion` (expression), `stale-timeout` (ms).
**Alternatives:**
- Separate level type field (`type: keyed`) — redundant with presence of keyed: vs window:, adds a discriminator field
**Rationale:** Presence of `keyed:` vs `window:` is self-discriminating. No need for a type field. Flat grammar.
**Trade-offs:** Mutual exclusivity must be validated — cannot have both keyed: and window: on the same level.
**Sources:** KeyedSummarisationRunner.java (predicate-based completion + staleTimeout), GE-20260813-keyed-summarisation-runner-api (garden entry: keyed runner uses completionTest, not WindowPolicy)
**Exploration:** quick
**Status:** captured

## D5: CloudEvent emission — per-level optional

**Choice:** Any level can declare an `emit:` block with a `cloud-event-type` URI. Non-emitting levels feed the next level internally only.
**Alternatives:**
- Terminal-only emission (only last level emits) — simpler but prevents RAS Ganglia from subscribing to mid-pipeline events (e.g., L2 anomalies AND L3 phases)
**Rationale:** The desiredstate#74 spec describes RAS consuming events at multiple altitude levels. Per-level emission enables this without separate pipelines.
**Trade-offs:** More complex emission wiring; each emitting level needs a CloudEventEmitter instance.
**Sources:** casehubio/casehub-desiredstate#74 (RAS integration — Ganglia consuming L2 and L3 events), #232 issue body (CloudEventEmitter design)
**Exploration:** quick
**Status:** captured

## D6: Pipeline compilation — dual-use PipelineCompiler

**Choice:** `PipelineCompiler` is a plain Java class in the runtime module (no Quarkus dependency). Usable by the deployment module's recorder (static path: classpath YAML discovery at build time) AND by application code on demand (dynamic path: runtime pipeline construction from programmatic or tenant-configured YAML).
**Alternatives:**
- Build-time only (compiler locked behind deployment module) — prevents runtime pipeline construction
- Full build-time codegen (generate Java source from YAML) — faster startup but adds code generation complexity, harder to debug
**Rationale:** Static pipelines are convenient (deploy YAML, build, done). Dynamic pipelines are necessary (tenant config, case-driven pipelines). The compiler logic is the same — validation, type resolution, runner construction. No reason to duplicate it.
**Trade-offs:** Dynamic path skips Jandex-based @SummariserTypeId discovery (no build-time classpath scan). Dynamic callers must register custom types via a SummariserRegistry API instead.
**Sources:** #233 issue body (build-time processing spec), Quarkus extension patterns (recorder-based construction)
**Exploration:** quick
**Status:** captured

## D7: Event payload type — generic throughout

**Choice:** The pipeline stays generic (`LevelEvent<E>`) throughout. No special-casing for Tier 1 vs Tier 2. Tier 1 (YAML-only) happens to use `E = Map<String, Object>` from JSON deserialization; Tier 2 uses domain types. Built-in summariser types use expression evaluation for field access, which MVEL3 handles transparently for both maps and typed objects.
**Alternatives:**
- Map-only Tier 1 (framework special-cases Map) — breaks the ContextBridge pattern used across the platform, makes Tier 1 a second-class citizen
- JsonNode payload — JQ-native but MVEL3 field access becomes verbose (`node.get("field").asDouble()`)
**Rationale:** Follows the ContextBridge pattern used for domain contexts everywhere in CaseHub. The framework is type-agnostic — the concrete type is an implementation detail of the source/ingestion layer.
**Trade-offs:** None — this is the natural design. Expression evaluation via MVEL3 handles both typed objects and maps transparently.
**Sources:** Platform ContextBridge pattern, MVEL3 property access semantics
**Exploration:** quick
**Status:** captured

## D8: phase-detect initial state — explicit in YAML

**Choice:** The `phase-detect` built-in declares `initial: <STATE>` in the YAML. Every new tenant starts in that state. First batch evaluates transitions from the initial state.
**Alternatives:**
- First-match on first batch (no initial state — evaluate all transitions cold) — handles cold-start more accurately but adds edge cases (what if nothing matches?) and makes the default state implicit
**Rationale:** Simple, predictable, no magic. The initial state is always visible in the YAML definition. Stateful summariser state is per-tenant (via LevelEvent.tenancyId).
**Trade-offs:** If the real-world initial state varies by tenant, the YAML can't express that. Tier 2 (custom Java) needed for tenant-specific initial states.
**Sources:** #233 issue body (phase-detect spec), HubPhaseSummariser.java (existing phase classification pattern)
**Exploration:** quick
**Status:** captured

## D9: Expression engine — platform expression SPI

**Choice:** Depend on `casehub-platform-api` (SPI: `ExpressionEngine`, `CompiledExpression<C,R>`, `ExpressionEngineRegistry`) and `casehub-platform-expression` (runtime: `MvelExpressionEngine`, `JQExpressionEngine`). No direct jackson-jq or MVEL3 dependency.
**Alternatives:**
- Bundle own jackson-jq/MVEL3 dependencies — duplicates expression infrastructure already provided by platform
- Depend on engine-api ExpressionEngine — couples to `CaseContext`, which summarisation pipelines don't use
**Rationale:** Platform-api `CompiledExpression<C, R>` is fully generic (`R eval(C)`) — works for `CompiledExpression<BatchContext, Boolean>` (predicates) and `CompiledExpression<JsonNode, List<JsonNode>>` (JQ transforms). No gaps. Unifies on the same expression SPI used across the platform.
**Trade-offs:** Summarisation-yaml gains a `provided` dependency on casehub-platform-api. Consumers must have platform-expression on classpath for MVEL3/JQ evaluation — but any Quarkus app already does.
**Sources:** javap analysis of platform-api ExpressionEngine/CompiledExpression/ExpressionEngineRegistry, casehub-platform-expression implementations (MvelExpressionEngine, JQExpressionEngine, JexlExpressionEngine)
**Exploration:** quick
**Status:** captured

## D10: Pre-computed batch aggregates for expression context

**Choice:** The runtime pre-computes batch statistics (size, counts by field value, sums/avgs of numeric fields) and exposes them as flat variables in the expression context. Raw batch available as `batch` for escape hatch.
**Alternatives:**
- Raw batch only (expression computes everything inline) — more flexible but expressions become complex and performance unpredictable
- Schema-driven projection (YAML declares which fields to project/aggregate) — more explicit but verbose schema definition per level
**Rationale:** Summariser `when` expressions need aggregate statistics (e.g., "highSeverityCount >= 3"). Pre-computing these keeps expressions simple and predictable. The `batch` escape hatch preserves full flexibility.
**Trade-offs:** The runtime must introspect event payloads to compute aggregates. For typed objects, this uses reflection or MVEL3 property access. For maps, direct key access. Performance cost is per-batch, not per-event.
**Sources:** HubPhaseSummariser.java (counts high-severity anomalies), AnomalyDetectorSummariser.java (weight statistics)
**Exploration:** quick
**Status:** captured

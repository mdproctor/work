# Agentic Pattern YAML Expansion — Orchestration Topologies, Routing, Termination

**Issue:** #165
**Date:** 2026-09-07
**Parent:** casehubio/parent#422 — TypeScript Programming Model (Phase 2)

## Overview

Makes all agentic orchestration patterns YAML-expressible by introducing shared spec records as a canonical data model. Java builders and YAML both produce spec records. A compiler resolves specs to live runtime objects. MVEL3 expressions handle predicates (YAML→Java only). victools generates JSON/YAML schema from spec records for IDE completion, validation, and LLM prompt generation.

Follows the DSL-STYLE-GUIDE principle: "Three pathways, one family: pure YAML, pure Java (DSL + annotations), and hybrid. Each pathway is first-class — they are peer representations of the same models."

## Architecture

```
PatternSpec (shared data model — string strategy refs + MVEL expressions)
  ├── Patterns.supervisor().build()     ← Java builder pathway (sugar over PatternSpec)
  ├── YAML deserialization              ← YAML pathway (Jackson → PatternSpec)
  ├── PatternCompiler                   ← resolves string refs + expressions → live runtime objects
  └── victools schema generation        ← generates JSON/YAML schema from PatternSpec
```

### Layer 1: Spec Records

Purpose-built data records with string-based strategy references and MVEL expression strings. These represent the problem space declaratively — not a dump of runtime constructor parameters.

Each spec area gets its own record hierarchy:

| Area | Root Spec | Nested Specs |
|------|-----------|-------------|
| Patterns (8 topologies) | `PatternSpec` | `SupervisorSpec`, `SequenceSpec`, `LoopSpec`, `ParallelSpec`, `VotingSpec`, `DebateSpec`, `ConditionalSpec`, `HtnSpec` |
| Routing | `RoutingSpec` | Per-strategy config records |
| Termination | `TerminationSpec` | Per-condition config records |
| Aggregation | `AggregationSpec` | Per-strategy config records |
| Activation | `ActivationSpec` | Per-rule config records |
| Decomposition | `DecompositionSpec` | Per-strategy config records |
| Judgment | `JudgmentSpec` | `TriggerSpec`, `CallerSpec`, `AgreementSpec` |
| Negotiation | `NegotiationSpec` | `AcceptancePolicySpec` |
| Conversation | `ConversationSpec` | `TurnPolicySpec`, `EpistemicRuleSpec`, `ConvergencePolicySpec` |
| Normative | `ConflictResolutionSpec` | Per-strategy config records |
| Agent reference | `AgentRefSpec` | Sealed: `WorkerAgentSpec`, `ChannelAgentSpec`, `HumanAgentSpec`, `ExternalAgentSpec` |

Spec records are plain Java records with Jackson annotations. No functional interfaces. All fields are either primitives, strings, enums, nested spec records, or lists thereof.

**Strategy references use `type: name` convention:**

```java
public record RoutingSpec(
    String type,          // "first-match", "round-robin", "sequential", "llm-selected"
    @Nullable String guard,  // MVEL expression (optional)
    Map<String, Object> config  // strategy-specific config
) {}
```

### Layer 2: Strategy Registries

Each SPI with concrete named implementations gets a registry. Follows the `SummariserRegistry` pattern from summarisation-yaml.

```java
public class RoutingStrategyRegistry {
    // Built-in registrations
    // "first-match" → FirstMatchRouting
    // "round-robin" → RoundRobinRouting
    // "sequential"  → SequentialRouting
    // "llm-selected" → LlmSelectedRouting
    
    RoutingStrategy<?> resolve(RoutingSpec spec, ExpressionEngine engine);
}
```

Registries for all SPI families:

| Registry | Strategies |
|----------|-----------|
| `RoutingStrategyRegistry` | first-match, round-robin, sequential, llm-selected |
| `TerminationConditionRegistry` | max-iterations, goal-reached, judge-convergence, all-agreed, supervisor, contested, convergence |
| `AggregationStrategyRegistry` | pass-through, collect-all, majority-vote, auction |
| `ActivationRuleRegistry` | on-dispatch, max-iterations |
| `DecompositionStrategyRegistry` | identity, static, llm, hybrid, heuristic, goap |
| `AcceptancePolicyRegistry` | unanimous, majority, threshold |
| `TurnPolicyRegistry` | round-robin, addressed, point-addressed, free |
| `ConvergencePolicyRegistry` | structural, common-ground-ratio, composite |
| `ConflictResolutionRegistry` | priority, specificity, recency, most-restrictive, escalation |
| `EpistemicRuleRegistry` | explicit-acknowledgement, tacit-acceptance, commitment-resolution |
| `JudgmentTriggerRegistry` | always-yield, never-yield, iteration-based, confidence-threshold |
| `CallerStrategyRegistry` | single, fan-out, escalation-chain |

Registries are extensible — consumers register domain-specific implementations via CDI.

### Layer 3: Compilers

Compilers resolve spec records into live runtime objects. Each area gets a compiler.

```java
public class PatternCompiler {
    ExecutionModel<?> compile(PatternSpec spec);
}
```

The compiler:
1. Reads the pattern type from the spec
2. Resolves strategy references via registries
3. Compiles MVEL expressions via LambdaCatalog
4. Constructs the runtime `ExecutionModel` with live SPI instances

### Layer 4: Expression Compilation (MVEL3 + jq)

Predicate-accepting fields use MVEL3 expressions in YAML. Direction is YAML→Java only.

```yaml
routing:
  type: first-match
  guard: "priority > 5"

termination:
  - type: convergence
    when: "establishedRatio > 0.8"
```

```java
// Java path — lambdas, unchanged
supervisor()
    .route(new FirstMatchRouting<>(c -> c.priority() > 5))
    .terminate(new ConvergenceTermination<>(s -> s.establishedRatio() > 0.8))
```

Both produce identical runtime objects.

**MVEL3 LambdaCatalog** handles the YAML path:
1. **Register** — all expressions across the pipeline enter LambdaCatalog at parse time
2. **Deduplicate** — identical expressions share one compiled class
3. **Index** — catalog knows every expression and where it's used
4. **Batch compile** — MVELBatchCompiler compiles all unique expressions in one javac call
5. **Wire** — compiler resolves catalog IDs to Evaluator instances

**Two compilation modes:**
- **Compile-time** (Quarkus deployment module) — classpath YAML → bytecode during Maven build. Bad expressions fail the build. Extends SummarisationYamlProcessor pattern.
- **Runtime** (dynamic loading) — expressions compiled when YAML is loaded. Required for tenant-specific pipelines, hot-reload. Always available.

Both modes share the same LambdaCatalog.

**jq** remains supported for data transformation expressions via yaml-core.

### Layer 5: Schema Generation

victools reflects on spec records (Layer 1) to generate JSON/YAML schema.

```java
public class BlocksSchemaGenerator {
    // Reuses platform casehub-schema-generator modules:
    // - EnumInliningModule
    // - SealedHierarchyModule (promoted from neocortex via platform#279)
    // - UnevaluatedPropertiesModule
    
    JsonNode generate(Class<?> rootType);
}
```

Schema enables:
- IDE completion (VS Code, IntelliJ YAML plugin)
- YAML validation at parse time
- LLM prompt generation (schema-constrained output)
- Drift detection (CI regenerates and diffs)

### Layer 6: Builder Refactoring

Existing Java builders are refactored to wrap spec records internally. Public API unchanged.

```java
// Before (holds live SPI instance)
public class SupervisorBuilder<T> {
    private RoutingStrategy<T> routing;
    public SupervisorBuilder<T> route(RoutingStrategy<T> strategy) {
        this.routing = strategy;
        return this;
    }
}

// After (populates spec record, also accepts live instances)
public class SupervisorBuilder<T> {
    private final SupervisorSpec.Builder specBuilder = SupervisorSpec.builder();
    private RoutingStrategy<T> routingOverride;  // live instance takes precedence
    
    // Typed — existing API, unchanged
    public SupervisorBuilder<T> route(RoutingStrategy<T> strategy) {
        this.routingOverride = strategy;
        return this;
    }
    
    // String shorthand — new, optional
    public SupervisorBuilder<T> route(String strategyName) {
        specBuilder.routing(new RoutingSpec(strategyName, null, Map.of()));
        return this;
    }
    
    public ExecutionModel<T> build() {
        if (routingOverride != null) {
            return buildDirect();  // bypass spec, use live instances
        }
        return compiler.compile(specBuilder.build());  // resolve via spec
    }
}
```

When a live instance is passed, the builder uses it directly (no spec roundtrip). When string references are used, the compiler resolves via registries. Both paths produce identical runtime objects.

## YAML Shape

Following the pattern-to-YAML mapping from the typescript-programming-model spec:

```yaml
# Supervisor pattern with routing, termination, and agents
pattern:
  type: supervisor
  routing:
    strategy: first-match
    guard: "priority > 5"
  termination:
    - type: max-iterations
      iterations: 20
    - type: convergence
      threshold: 0.8
  aggregation:
    strategy: collect-all
  agents:
    - name: analyst
      type: worker
      description: "Analyses input data"
    - name: reviewer
      type: worker
      description: "Reviews analysis"

# Negotiation with acceptance policy
negotiation:
  parties: [buyer, seller]
  acceptance:
    policy: majority
  termination:
    - type: max-rounds
      rounds: 10
    - type: deadline
      duration: PT24H

# Conversation with turn policy and convergence
conversation:
  turnPolicy:
    type: round-robin
  epistemicRule:
    type: explicit-acknowledgement
    minParticipants: 2
  convergencePolicy:
    type: structural
    similarityThreshold: 0.8
    staleRounds: 3
```

## Module Structure

New code lives in a new `agentic-yaml` Maven module (parallel to `summarisation-yaml`):

```
casehub-blocks-agentic-yaml/
  src/main/java/io/casehub/blocks/agentic/yaml/
    spec/           ← Layer 1: spec records (PatternSpec, RoutingSpec, etc.)
    registry/       ← Layer 2: strategy registries
    compiler/       ← Layer 3: compilers (PatternCompiler, etc.)
    expression/     ← Layer 4: MVEL3 LambdaCatalog integration
    schema/         ← Layer 5: BlocksSchemaGenerator + SchemaModules
    
casehub-blocks-agentic-yaml-deployment/
  src/main/java/io/casehub/blocks/agentic/yaml/deployment/
    AgenticYamlProcessor.java     ← Quarkus build extension
    AgenticRecorder.java          ← runtime bean registration
```

Builder refactoring (Layer 6) happens in the existing `casehub-blocks` module — the builders already live there.

## Dependencies

**agentic-yaml module:**
- Compile: `casehub-blocks` (spec records reference blocks types), `jackson-dataformat-yaml`
- Provided: `casehub-platform-api` (ExpressionEngine SPI), `quarkus-core` (recorder)
- Test: JUnit 5, AssertJ, `casehub-platform-expression` (MvelExpressionEngine)

**agentic-yaml-deployment module:**
- Compile: `casehub-blocks-agentic-yaml`, `quarkus-core-deployment`

**Schema generation:**
- Compile: `casehub-schema-generator` (platform — EnumInliningModule, SealedHierarchyModule, UnevaluatedPropertiesModule)
- Test: `victools-jsonschema-generator`

## Scope

### In scope

| Area | Spec Records | Registry | Compiler | Tests |
|------|-------------|----------|----------|-------|
| Patterns (8 topologies) | Yes | — (pattern type dispatch) | PatternCompiler | YAML fixtures per topology |
| Routing (4 strategies) | Yes | RoutingStrategyRegistry | Part of PatternCompiler | Per-strategy round-trip |
| Termination (7 conditions) | Yes | TerminationConditionRegistry | Part of PatternCompiler | Per-condition round-trip |
| Aggregation (4 strategies) | Yes | AggregationStrategyRegistry | Part of PatternCompiler | Per-strategy round-trip |
| Activation (2 rules) | Yes | ActivationRuleRegistry | Part of PatternCompiler | Per-rule round-trip |
| Decomposition (6 strategies) | Yes | DecompositionStrategyRegistry | Part of PatternCompiler | Per-strategy round-trip |
| Judgment | Yes | JudgmentTrigger/Caller/Agreement registries | JudgmentCompiler | Judgment policy round-trip |
| Negotiation | Yes | AcceptancePolicyRegistry | NegotiationCompiler | Negotiation round-trip |
| Conversation | Yes | TurnPolicy/Epistemic/Convergence registries | ConversationCompiler | Conversation round-trip |
| Normative | Yes | ConflictResolutionRegistry | NormativeCompiler | Resolution round-trip |
| Expression compilation | MVEL3 + jq | — | Via ExpressionEngine | Expression evaluation tests |
| Schema generation | victools on spec records | — | — | Schema drift test |

### Not in scope

- Social cognition orchestrators — complex lifecycle, no declarative equivalent
- Custom functional SPIs with no concrete implementations
- Runtime tick/signal types — outcomes, not configuration
- summarisation-yaml — already has PipelineDefinition as its data model, no drift risk
- TS CDK generation (L2 from parent#422) — separate issue, depends on this work

## Testing Strategy

1. **Round-trip tests** — YAML → spec record → compiler → runtime object → verify behavior matches direct Java construction
2. **Schema drift test** — victools generates schema, CI compares to committed schema (`git diff --exit-code`)
3. **Expression tests** — MVEL expressions evaluate correctly, LambdaCatalog deduplication works
4. **Registry extensibility** — consumer-registered strategies resolve correctly
5. **Builder backward compatibility** — existing test suite passes unchanged after refactoring

## References

- parent/docs/DSL-STYLE-GUIDE.md — "peer representations of the same models"
- specs/main/2026-08-23-typescript-programming-model-design.md — pattern-to-YAML mapping table
- specs/issue-233-summarisation-yaml-surface/2026-09-05-summarisation-yaml-surface-design.md — PipelineDefinition + PipelineCompiler precedent
- engine/generator/CaseHubSchemaGenerator.java — victools + SchemaModules approach
- neocortex#246 — API-to-YAML audit template
- platform#279 — SealedHierarchyModule promotion (done)
- /Users/mdproctor/dev/mvel — MVEL3 LambdaCatalog, MVELBatchCompiler, LambdaRegistryStore
- /Users/mdproctor/dev/swf-sdk-java — SerializedLambda approach (rejected — not human-readable)

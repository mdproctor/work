# LLM Swarm Coordination with Experience Replay — Design Spec

**Issue:** casehubio/agency#285
**Parent epic:** casehubio/engine#1104 (Hive Mind)
**Depends on:** engine#1112 (Swarm execution model)

## Overview

LLM-enhanced swarm coordination that adds team formation reasoning, work distribution intelligence, collective observation synthesis, and experience replay from CBR plan traces. Builds on engine's mechanical swarm infrastructure (RoleTracker, TeamDetector, SwarmProvisioner) — engine handles emergence, agency adds intelligence.

**Research inputs:**
- SwarmExp (arXiv:2608.30661): experience extraction + replay improves swarm orchestration
- "Drop the Hierarchy and Roles" (arXiv:2603.28990): minimal scaffolding produces better LLM self-organisation

## Architecture

### Compositional Model

The swarm is the 9th `PatternDefinition` topology, compiled to an `ExecutionModel` using `ChoreographedDriver` for event-driven execution. A single `LlmSwarmCoordinator` composition root wires four capabilities:

```
                    ┌─────────────────────────────────┐
                    │       LlmSwarmCoordinator        │
                    │       (composition root)         │
                    └────┬──────┬──────┬──────┬───────┘
                         │      │      │      │
              ┌──────────┘      │      │      └──────────┐
              ▼                 ▼      ▼                 ▼
    SwarmRoutingStrategy   Experience  Collective    SwarmProvisioning
    (team formation +      Replay     Reasoning     AdvisorImpl
     work distribution)    Section    Aggregation   (scaling veto)
              │                │           │              │
              ▼                ▼           ▼              ▼
         AgentInvoker    ExperienceAnalyser  ContentSummariser  engine SPI
```

**Centralisation is a deliberate v1 trade-off.** Engine's mechanical RoleTracker/TeamDetector handle emergent role/team formation. The LLM coordinator reasons about what the mechanical layer detects — it does not replace distributed emergence with centralised control. Full distributed per-agent LLM coordination is a future evolution.

### Execution Flow

```
1. PatternCompiler compiles PatternDefinition.Swarm → ExecutionModel
2. ExecutionModel uses ChoreographedDriver with SwarmEventSource
3. Driver loop:
   a. SwarmEventSource delivers engine swarm lifecycle events as DriverEvents
   b. SwarmRoutingStrategy invokes coordinator LLM for team/work decisions
   c. AgentInvoker dispatches work to selected swarm members
   d. CollectiveReasoningAggregation synthesises member results via LLM
   e. ConvergenceTermination checks swarm convergence
   f. Loop back to (a) on next event
4. SwarmProvisioningAdvisorImpl provides LLM-reasoned scaling advice to engine
```

### Engine Integration

Agency implements two engine-api SPIs:

| SPI | Purpose | Contract |
|-----|---------|----------|
| `SwarmProvisioningAdvisor` | Scaling veto | `advise(ProvisioningContext) → ProvisioningAdvice` (shouldProvision boolean + adjusted capabilities/policy) |
| `SwarmCoordinationListener` (NEW — needed in engine-api) | Event bridge | Receives swarm lifecycle events (role changes, team changes, progress updates) without depending on engine-runtime types |

`SwarmProvisioningAdvisor` is a narrow concern: should we add/remove agents? The coordination intelligence (team formation, work distribution, collective reasoning, adaptive strategy) lives in the coordinator's own prompt/invocation pipeline.

**Event bridge:** `SwarmCoordinationListener` is a new SPI in engine-api that engine-runtime's `SwarmProvisioner` calls when swarm state changes. Agency implements `SwarmEventSource` (an `EventSource` impl) that wraps this listener and translates events to `DriverEvent` instances for the `ChoreographedDriver`'s blocking queue. This keeps the dependency direction clean: agency depends on engine-api, engine-runtime depends on engine-api, neither depends on the other.

```java
// New in engine-api (engine#1112 follow-up)
@FunctionalInterface
public interface SwarmCoordinationListener {
    void onSwarmEvent(SwarmCoordinationEvent event);
}

// engine-api — event types visible to both engine-runtime and agency
public sealed interface SwarmCoordinationEvent {
    record RoleChanged(List<DetectedRole> roles, SwarmProgress progress) implements SwarmCoordinationEvent {}
    record TeamChanged(List<DetectedTeam> teams, SwarmProgress progress) implements SwarmCoordinationEvent {}
    record ProgressUpdated(SwarmProgress progress) implements SwarmCoordinationEvent {}
    record MemberCompleted(String agentId, Object result) implements SwarmCoordinationEvent {}
    record ProvisioningChanged(int currentSize, int maxSize) implements SwarmCoordinationEvent {}
}
```

## New Types

### Package: `io.casehub.agency.swarm`

Location: `agency-core/src/main/java/io/casehub/agency/swarm/`

| Class | What it does |
|-------|-------------|
| `LlmSwarmCoordinator` | Composition root. Wires SwarmRoutingStrategy, CollectiveReasoningAggregation, SwarmExperienceSection, SwarmProvisioningAdvisorImpl. Manages coordinator prompt assembly. Not CDI-managed — constructed by PatternCompiler via SwarmCoordinatorBuilder. |
| `SwarmCoordinatorBuilder` | Builder API for constructing LlmSwarmCoordinator. Mirrors the PatternDefinition.Swarm record fields. Used by PatternCompiler and Java consumers. |
| `SwarmRoutingStrategy` | `RoutingStrategy<SwarmContext>` implementation. Invokes coordinator LLM with full agent roster (or CoalitionEvaluator-filtered shortlist for large rosters), experience replay context, and current swarm state. Returns `RoutingDecision.Selected` with team assignment. |
| `SwarmContext` | Record extending the routing context with swarm-specific data: detected roles, detected teams, swarm progress, active coordination strategy. |
| `CollectiveReasoningAggregation` | `AggregationStrategy<SwarmAggregationState>` implementation. Collects AgentResults from swarm members, invokes LLM via `StructuredAgentInvoker` with synthesis-focused prompt (not summarisation). Produces `AggregationResult.Resolved` with collective analysis identifying agreements, disagreements, complementary observations. |
| `SwarmAggregationState` | Record: collective analysis output — agreements, disagreements, complementary observations, confidence, minority viewpoints. |
| `SwarmExperienceSection` | Coordinator prompt section — contributes experience replay context to the coordinator's system prompt. Queries `ExperienceAnalyser` for team-level CBR traces matching current swarm context, formats as coordination pattern examples. Configurable via `ExperienceReplayConfig`. Not engine-api's `RoutingPromptSection` — the coordinator has its own prompt assembly. Implements a local `@FunctionalInterface SwarmPromptSection` (`String contribute(SwarmContext)`). |
| `SwarmPromptSection` | `@FunctionalInterface`: `String contribute(SwarmContext)`. Coordinator prompt composition SPI — each section contributes text for the coordinator's system prompt. Multiple sections assembled by LlmSwarmCoordinator. |
| `ExperienceReplayConfig` | Record: `maxTraces` (int, default 5), `minSimilarity` (double, default 0.3). YAML-configurable. |
| `SwarmProvisioningAdvisorImpl` | `SwarmProvisioningAdvisor` implementation. Invokes coordinator LLM to reason about whether provisioning/deprovisioning is warranted given current swarm state, experience data, and progress. Falls back to heuristic (budget-based) on LLM failure. |
| `SwarmEventSource` | `EventSource` implementation. Wraps `SwarmCoordinationListener` (engine-api SPI), translates `SwarmCoordinationEvent` to `DriverEvent`. Registered with engine at swarm startup. |
| `SwarmInvocationPolicy` | Record: `maxCallsPerExecution` (int), `maxTokensPerExecution` (long), `warnAfter` (Duration), `errorAfter` (Duration). Extends the latency-only `LlmInvocationPolicy` concept with cost budgets. Shared across all four LLM capabilities within one swarm execution. |
| `SwarmInvocationTracker` | Thread-safe counter tracking LLM calls and tokens consumed within a swarm execution. Checked before each coordinator invocation. When budget exhausted, triggers heuristic fallback. |
| `SwarmFallbackStrategy` | Heuristic fallback implementations for when LLM is unavailable or budget exhausted: `CoalitionEvaluator` for team formation, `CollectAll` for aggregation, budget-based logic for provisioning advice. |

### Package: `io.casehub.agency.agentic.yaml` (additions)

| Class | What it does |
|-------|-------------|
| `PatternDefinition.Swarm` | New sealed record variant. Topology-specific fields: `ExperienceReplaySpec experience`, `@Nullable String coordinationModel`, `@Nullable SwarmInvocationPolicySpec invocationPolicy`, `int preFilterThreshold` (default 12). |
| `ExperienceReplaySpec` | Record: `int maxTraces` (default 5), `double minSimilarity` (default 0.3). |
| `SwarmInvocationPolicySpec` | Record: `int maxCalls` (default 50), `long maxTokens` (default 500_000), `@Nullable String warnAfter`, `@Nullable String errorAfter`. |

### Enum additions

| Enum | New value |
|------|-----------|
| `PatternType` | `SWARM` (not workflow-shaped) |
| `CapabilityType` | `SWARM_COORDINATION` under `CapabilityCategory.ORCHESTRATION`, `hasYamlRoot: true` |

## YAML Surface

```yaml
type: swarm
name: incident-response-swarm
description: Coordinated incident response team
coordinationModel: claude-sonnet-4-5
preFilterThreshold: 12

experience:
  maxTraces: 5
  minSimilarity: 0.3

invocationPolicy:
  maxCalls: 50
  maxTokens: 500000
  warnAfter: 5s
  errorAfter: 30s

agents:
  - name: analyst
    description: Analyses incident data
    capabilities: [data-analysis, pattern-recognition]
  - name: responder
    description: Executes remediation steps
    capabilities: [incident-response, system-admin]
  - name: communicator
    description: Manages stakeholder communication
    capabilities: [communication, reporting]

termination:
  - type: convergence
    threshold: 0.85
  - type: max-iterations
    maxIterations: 20

judgment:
  type: convergence
  threshold: 0.9
```

The standard 12 common fields are available. Mechanical swarm tuning (maxSwarmSize, teamAffinityThreshold, roleSimilarityThreshold, detectionInterval) is configured via engine's `SwarmConfig`, not duplicated here.

## Java Builder API

```java
Patterns.swarm()
    .name("incident-response-swarm")
    .coordinationModel("claude-sonnet-4-5")
    .preFilterThreshold(12)
    .experience(new ExperienceReplayConfig(5, 0.3))
    .invocationPolicy(new SwarmInvocationPolicy(50, 500_000,
        Duration.ofSeconds(5), Duration.ofSeconds(30)))
    .agents(analyst, responder, communicator)
    .termination(ConvergenceTermination.of(0.85)
        .or(MaxIterationsTermination.of(20)))
    .build();
```

## Experience Replay

`SwarmExperienceSection` implements `PromptSection` and injects historical coordination patterns into the coordinator's system prompt. It:

1. Queries `ExperienceAnalyser.workerSuccessRates()` for per-agent historical performance
2. Filters CBR plan traces by similarity threshold (`minSimilarity`) to the current task
3. Extracts team-level coordination patterns: which agent combinations succeeded, what roles emerged, how work was distributed
4. Formats top N traces (`maxTraces`) as structured examples in the coordinator prompt

The section is assembled into the coordinator's prompt alongside swarm state context (detected roles, teams, progress). This follows the same composition pattern as `CbrRoutingPromptSection` in the routing pipeline.

## Collective Reasoning

`CollectiveReasoningAggregation` implements `AggregationStrategy<SwarmAggregationState>`. Unlike existing mechanical aggregations (PassThrough, CollectAll, MajorityVote), this is the first LLM-powered aggregation strategy.

The synthesis prompt is explicitly multi-perspective analysis, not summarisation:
- What do the swarm members agree on?
- Where do they disagree, and what evidence supports each position?
- What does each member observe that others don't (complementary observations)?
- What is the collective confidence level?
- Are there minority viewpoints that should be preserved?

The output is a `SwarmAggregationState` record that the coordinator uses for adaptive decisions.

## Adaptive Coordination

The coordinator adapts swarm strategy by re-reasoning when `SwarmCoordinationEvent` instances arrive via `SwarmEventSource`. Events are coalesced by `EventConcurrencyPolicy.coalesceBySource()` to prevent LLM call storms when rapid state changes occur.

Adaptation decisions the coordinator can make:
- Re-assign work between swarm members based on progress and role changes
- Request provisioning/deprovisioning via `SwarmProvisioningAdvisorImpl`
- Adjust coordination approach based on convergence signals
- Flag deadlocks or stalls for escalation

The coordinator does NOT have a fixed phase model (form → distribute → observe → adapt). It receives events and reasons about what coordination action, if any, is needed. This aligns with the "minimal scaffolding" principle.

## Cost Controls and Failure Degradation

### Cost Controls (D11)

`SwarmInvocationPolicy` provides per-execution budgets:
- `maxCallsPerExecution`: hard limit on LLM invocations across all four capabilities
- `maxTokensPerExecution`: hard limit on total tokens consumed
- `warnAfter` / `errorAfter`: latency thresholds per individual call

`SwarmInvocationTracker` is a thread-safe counter checked before each coordinator invocation. When budget is exhausted, the tracker returns a signal that triggers `SwarmFallbackStrategy`.

### Failure Degradation (D12)

When the coordinator LLM is unavailable (network failure, rate limit, context overflow) or budget is exhausted:

| Capability | Heuristic fallback |
|-----------|-------------------|
| Team formation | `CoalitionEvaluator` — capability-coverage scoring |
| Collective reasoning | `CollectAll` — pass-through without synthesis |
| Adaptive coordination | No-op — swarm continues without re-coordination |
| Provisioning advice | Budget-based heuristic — provision if under budget and capabilities needed |

The swarm continues operating with reduced intelligence. Observability events (extending `SwarmEvent`) signal degraded-mode operation so consumers can react.

## PatternCompiler Changes

`PatternCompiler.compile()` gets a new case in each resolver:

| Resolver | Swarm default |
|----------|---------------|
| `resolvePatternType()` | `PatternType.SWARM` |
| `resolveRouting()` | `SwarmRoutingStrategy` (constructed with coordinator, experience section, invocation tracker) |
| `resolveAggregation()` | `CollectiveReasoningAggregation` (constructed with coordinator LLM config) |
| `resolveTermination()` | `ConvergenceTermination` (if not overridden in YAML) |

The compiler constructs `LlmSwarmCoordinator` via `SwarmCoordinatorBuilder`, wiring all components before producing the `ExecutionModel`.

## Test Strategy

| Test | What it verifies |
|------|-----------------|
| `PatternDefinitionSerializationTest` | YAML round-trip for `type: swarm` with all fields |
| `PatternCompilerTest` | Swarm compilation produces correct ExecutionModel with SwarmRoutingStrategy and CollectiveReasoningAggregation |
| `SwarmRoutingStrategyTest` | Team formation with mock AgentProvider — batch reasoning path and pre-filter fallback path |
| `CollectiveReasoningAggregationTest` | Multi-agent result synthesis with mock LLM — verifies structured output (agreements, disagreements, complementary observations) |
| `SwarmExperienceSectionTest` | Experience replay formatting from mock ExperienceAnalyser data |
| `SwarmProvisioningAdvisorImplTest` | Provisioning veto reasoning + heuristic fallback on LLM failure |
| `SwarmEventSourceTest` | SwarmCoordinationEvent → DriverEvent translation |
| `SwarmInvocationTrackerTest` | Budget enforcement — call counting, token tracking, exhaustion signal |
| `SwarmFallbackStrategyTest` | Heuristic fallback behaviour for each capability |
| `LlmSwarmCoordinatorTest` | Integration test — full coordinator loop with mock agents and mock LLM |

All tests use plain JUnit 5 + Mockito (no CDI container, per project conventions).

## Files Changed

### New files (agency-core)
- `agency-core/src/main/java/io/casehub/agency/swarm/LlmSwarmCoordinator.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/SwarmCoordinatorBuilder.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/SwarmRoutingStrategy.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/SwarmContext.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/CollectiveReasoningAggregation.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/SwarmAggregationState.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/SwarmExperienceSection.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/ExperienceReplayConfig.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/SwarmProvisioningAdvisorImpl.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/SwarmEventSource.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/SwarmInvocationPolicy.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/SwarmInvocationTracker.java`
- `agency-core/src/main/java/io/casehub/agency/swarm/SwarmFallbackStrategy.java`

### New files (agentic-yaml)
- `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/ExperienceReplaySpec.java`
- `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/SwarmInvocationPolicySpec.java`

### Modified files (agentic-yaml)
- `PatternDefinition.java` — add `Swarm` sealed record variant + `@Type` annotation
- `PatternCompiler.java` — add swarm cases to all resolver switches
- `PatternType.java` — add `SWARM` enum value
- `CapabilityType.java` — add `SWARM_COORDINATION` enum value

### New test files
- `agency/src/test/java/io/casehub/agency/swarm/` — 10 test classes as listed above
- `agentic-yaml/src/test/resources/patterns/swarm.yaml` — YAML fixture

### Modified test files
- `PatternDefinitionSerializationTest.java` — add "swarm" to `@ValueSource` + field test
- `PatternCompilerTest.java` — add swarm compilation test

### Cross-repo (engine-api — separate issue/PR)
- New `SwarmCoordinationListener` SPI interface
- New `SwarmCoordinationEvent` sealed interface with 5 event types

## Out of Scope

- **Distributed per-agent LLM coordination** — future evolution where each swarm member has its own lightweight LLM decision loop
- **SwarmCoordinationListener implementation in engine-runtime** — engine#1112 follow-up work; agency provides the interface contract
- **Prompt tuning/optimisation** — the coordinator prompts will use sensible defaults; optimisation via the existing prompt optimisation framework is future work
- **Multi-swarm coordination** — coordination across multiple swarm instances

## References

- engine#1112 — Swarm execution model (mechanical infrastructure)
- engine#1104 — Hive Mind epic (parent)
- `PatternDefinition.java` — existing topology sealed record pattern
- `PatternCompiler.java` — spec → ExecutionModel compilation
- `ChoreographedDriver.java` — event-driven execution driver
- `SwarmConfig` (engine-api) — mechanical swarm configuration
- `SwarmProvisioningAdvisor` (engine-api) — provisioning veto SPI
- `ExperienceAnalyser` (engine-api) — CBR plan trace analysis utility
- `CoalitionEvaluator` (agency deliberation/coalition) — heuristic team scoring
- `CollectAll` (agency orchestration/aggregation) — pass-through aggregation
- `LlmInvocationPolicy` (agency stigmergy/llm) — latency threshold pattern
- `EventSource` / `EventConcurrencyPolicy` (agency orchestration/execution) — event-driven infrastructure
- `StructuredAgentInvoker` (agency core/invoke) — LLM invocation utility
- `ContentSummariser` (summarisation-api) — synthesis interface pattern
- `CbrRoutingPromptSection` (agency routing/agent) — CBR prompt section pattern
- SwarmExp (arXiv:2608.30661) — experience extraction + replay
- "Drop the Hierarchy and Roles" (arXiv:2603.28990) — minimal scaffolding

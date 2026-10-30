# Design: Coordinator Prompt Tuning for Swarm Coordination (#362)

Wire the three swarm coordinator prompt sites into the existing prompt
optimisation framework for systematic A/B testing and improvement.

## Background

The v1 swarm prompt infrastructure (agency#285) built the scaffolding
types: `SwarmPromptSignatures` (three `PromptSignature` constants),
`SwarmOutcomeMetric` (convergence outcome scoring), `SwarmOutcomeMapper`
(raw outcomes → `VariantOutcome`), and `SwarmOptimisedFewShotSection`
(few-shot injection for team formation). These types define the
integration surface but the plumbing is not connected — no outcome
recording pipeline, incomplete `SystemPromptCustomiser` wiring, and
no evolution loop integration.

## Scope

Three prompt sites, one optimisation pipeline:

| Prompt site | Current state | Change |
|-------------|--------------|--------|
| Team formation (`SwarmRoutingStrategy`) | No `SystemPromptCustomiser` hook | Add customiser + variant slot |
| Collective reasoning (`CollectiveReasoningAggregation`) | Has customiser, builder wires it | Wire variant slot from builder |
| Provisioning (`SwarmProvisioningAdvisorImpl`) | Has customiser, NOT wired by builder | Wire customiser + variant slot from builder |

Plus: outcome recording pipeline, outcome provider for batch cycle,
generalised few-shot injection, evolution loop integration.

## Changes

### 1. Add SystemPromptCustomiser to SwarmRoutingStrategy

Current constructor:
```java
SwarmRoutingStrategy(AgentProvider, SwarmExperienceSection,
    int preFilterThreshold, @Nullable SwarmInvocationTracker)
```

New constructor:
```java
SwarmRoutingStrategy(AgentProvider, SwarmExperienceSection,
    int preFilterThreshold, @Nullable SwarmInvocationTracker,
    @Nullable SystemPromptCustomiser promptCustomiser)
```

Add `setVariantSlot(@Nullable String variantSlot)` matching the pattern
on `CollectiveReasoningAggregation` and `SwarmProvisioningAdvisorImpl`.

In `route()`, after `buildPrompt()` returns and before
`AgentSessionConfig.of()`, apply the customiser:
```java
if (promptCustomiser != null) {
    prompt = promptCustomiser.customise(prompt,
        TEAM_FORMATION.id(), variantSlot);
}
```

### 2. Wire SwarmCoordinatorBuilder

Builder currently passes `promptCustomiser` only to
`CollectiveReasoningAggregation`. Changes:

- Pass `promptCustomiser` to `SwarmRoutingStrategy` constructor
- Accept optional `VariantSelector` via `variantSelector(VariantSelector)`
- In `build()`: if `VariantSelector` is present, call
  `selectSlot(caseId, capabilityName)` once and propagate the slot to
  all three sites via their `setVariantSlot()` methods
- Accept optional `SwarmProvisioningAdvisorImpl` via
  `provisioningAdvisor(SwarmProvisioningAdvisorImpl)` so the builder can
  wire its customiser and variant slot. If not provided, provisioning
  optimisation is skipped (backward compatible).
- Pass `additionalSections` to `SwarmRoutingStrategy` so
  `SwarmOptimisedFewShotSection` can contribute to team formation
  prompts. This requires adding `List<SwarmPromptSection>` to
  `SwarmRoutingStrategy`'s constructor and composing their output into
  `buildPrompt()`.

### 3. SwarmOutcomeRecorder (new class)

Package: `io.casehub.agency.orchestration.swarm.prompt`

```java
@ApplicationScoped
public class SwarmOutcomeRecorder {
    SwarmOutcomeRecorder(PromptVariantStore store) { ... }

    void onSwarmConverged(@Observes SwarmCoordinationEvent event,
                          String signatureId, String variantId,
                          String convergenceOutcome,
                          double similarityScore,
                          @Nullable Duration executionDuration) { ... }
}
```

Responsibilities:
- Observe swarm convergence outcomes (terminal events from
  `SwarmConvergenceTermination`)
- Map via `SwarmOutcomeMapper` (one mapper per signature)
- Record `VariantOutcome` to `PromptVariantStore`

The recorder needs to know which variant slot was active for the
execution. Two options for plumbing the variant ID:

**Approach:** The recorder is injected into `SwarmCoordinatorBuilder`
and `build()` calls `recorder.registerExecution(executionId, variantSlot)`
before the coordinator runs. Terminal events carry the execution context
(available from `SwarmContext`). The recorder correlates execution ID →
variant slot → recorded outcome.

This avoids passing variant metadata through the event hierarchy.

### 4. SwarmPromptOutcomeProvider (new class)

Package: `io.casehub.agency.orchestration.swarm.prompt`

```java
@ApplicationScoped
public class SwarmPromptOutcomeProvider implements PromptOutcomeProvider {
    SwarmPromptOutcomeProvider(SwarmOutcomeRecorder recorder) { ... }

    List<VariantOutcome> outcomes(String signatureId) { ... }
}
```

Reads recorded outcomes from `SwarmOutcomeRecorder`'s in-memory store.
The batch cycle calls this to score control vs experiment variants.

### 5. Generalise SwarmOptimisedFewShotSection

`SwarmOptimisedFewShotSection` already takes a `signatureId` in its
3-arg constructor. The 2-arg constructor defaults to `TEAM_FORMATION`.

Changes:
- No structural changes needed — the class is already parameterised.
- Builder creates three instances (one per signature) and wires them
  to the appropriate prompt sites.
- Team formation: instance added to `additionalSections` passed to
  `SwarmRoutingStrategy`.
- Collective reasoning and provisioning: instances contribute via
  `SwarmPromptSection.contribute()` called before the customiser in
  each site's prompt construction.

### 6. Evolution Loop Integration (new classes)

Package: `io.casehub.agency.orchestration.swarm.prompt`

All implementations use domain ID `"swarm-prompt-optimisation"`.

| Class | Role | What it does |
|-------|------|-------------|
| `SwarmPromptProposalSource` | `ImprovementProposalSource` | Scans three `SwarmPromptSignatures`, evaluates quality via `SwarmOutcomeMetric`, proposes tuning when quality drops |
| `SwarmPromptRegressionEvaluator` | `RegressionEvaluator` | Monitors `swarm-prompt:*` component scores for regression |
| `SwarmPromptCategoryProvider` | `ImprovementCategoryProvider` | Single category `"swarm-prompt-tuning"`, four stages: evaluate → optimise → promote → outcome-recording |
| `SwarmPromptConflictStrategy` | `ConflictStrategy` | Signature-level mutual exclusion via metadata key `signatureId` |

These follow the exact pattern of the existing `PromptOptimisation*`
evolution types, scoped to swarm signatures.

## Testing

| What | How |
|------|-----|
| SwarmRoutingStrategy customiser hook | Unit test: verify prompt includes instruction delta when customiser provided |
| SwarmCoordinatorBuilder wiring | Unit test: build coordinator, verify all three sites receive customiser and variant slot |
| SwarmOutcomeRecorder | Unit test: register execution, fire terminal event, verify VariantOutcome recorded |
| SwarmPromptOutcomeProvider | Unit test: record outcomes, call outcomes(signatureId), verify correct list returned |
| SwarmOptimisedFewShotSection per-site | Unit test: verify contribute() returns formatted examples for each signature |
| Evolution loop types | Unit test per type following existing PromptOptimisation* test patterns |
| Integration: builder → execution → recording | Integration test: build coordinator, run mock execution to convergence, verify outcome recorded with correct variant |

## Non-Goals

- Actual prompt content changes (this wires the framework; content
  tuning happens via the batch cycle at runtime)
- Persistent outcome storage (in-memory via `PromptVariantStore`
  `@DefaultBean` is sufficient; consumers override with persistence)
- Cross-site independent A/B testing (D5: consistent slot per execution)

## References

- `SwarmPromptSignatures.java` — three PromptSignature constants
- `SwarmOptimisedFewShotSection.java` — existing few-shot section
- `SwarmOutcomeMetric.java` — existing quality metric
- `SwarmOutcomeMapper.java` — existing outcome mapper
- `SwarmCoordinatorBuilder.java` — builder to modify
- `SwarmRoutingStrategy.java` — team formation prompt site (no customiser hook)
- `CollectiveReasoningAggregation.java` — reasoning prompt site (has customiser)
- `SwarmProvisioningAdvisorImpl.java` — provisioning prompt site (has customiser, not wired by builder)
- `PromptOptimisationBatch.java` — batch cycle orchestrator
- `PromptOptimisationProposalSource.java` — evolution loop pattern to follow
- `OptimisedFewShotSection.java` — routing few-shot pattern to follow
- agency#285 — v1 swarm prompt scaffolding
- agency#362 — this issue

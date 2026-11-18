# Design: Remove residual cognitive code — complete neocortex migration

**Issue:** casehubio/agency#447
**Date:** 2026-11-18
**Status:** Draft

## Summary

Delete the 20 `social/memory/` source files and their 9 test files from agency — the implementations now live in neocortex (landed via neocortex#1115). Remove the `MemoryHygieneSpiAdapter` bridge. Re-wire `AgencyBeans` and `AgencyAutoConfiguration` to reference neocortex's implementations. Update the 11 evolution files that stay in agency to import from neocortex where needed.

## Scope

### Delete from agency (20 main + 9 test files)

**`agency-core/src/main/java/io/casehub/agency/social/memory/`:**

| File | Neocortex replacement |
|------|----------------------|
| `MemoryHygieneOrchestrator` | `cognition/memory/MemoryHygieneOrchestratorImpl` |
| `MemoryHygieneScheduler` | `cognition/memory/MemoryHygieneScheduler` |
| `ConfidenceScorer` | `cognition-api/memory/ConfidenceScorer` |
| `ArousalScorer` | `cognition/memory/ArousalScorer` |
| `SurpriseScorer` | `cognition/memory/SurpriseScorer` |
| `CompositeConfidenceScorer` | `cognition/memory/CompositeConfidenceScorer` |
| `WeightedScorer` | `cognition/memory/WeightedScorer` |
| `DefaultIntegrityChecker` | `cognition/memory/DefaultIntegrityChecker` |
| `IntegrityChecker` | `cognition-api/memory/IntegrityChecker` |
| `IntegrityViolation` | `cognition-api/memory/IntegrityViolation` |
| `ViolationType` | `cognition-api/memory/ViolationType` |
| `SemanticIntegrityChecker` | `cognition-api/memory/SemanticIntegrityChecker` |
| `NoOpSemanticIntegrityChecker` | `cognition/memory/NoOpSemanticIntegrityChecker` |
| `ReflectionStore` | (evaluate — may be dead or folded into neocortex reflection) |
| `NoOpReflectionStore` | (evaluate — may be dead) |
| `ReflectionQueryStore` | (evaluate — agency's extends neocortex's; check if still needed) |
| `NoOpReflectionQueryStore` | (evaluate — may be dead) |
| `RetentionConfig` | `cognition-api/memory/RetentionConfig` |
| `RetentionScore` | `cognition-api/memory/RetentionScore` |
| `HygieneEvent` | `cognition-api/memory/HygieneEvent` |
| `HygieneTick` | `cognition-api/memory/HygieneTick` |
| `MaintenanceTick` | `cognition-api/memory/MaintenanceTick` |

**`agency-core/src/main/java/io/casehub/agency/social/cognition/`:**

| File | Action |
|------|--------|
| `MemoryHygieneSpiAdapter` | Delete — bridge unnecessary when impl is in neocortex |

**Test files (agency/src/test/):**

| File | Action |
|------|--------|
| `memory/MemoryHygieneOrchestratorTest` | Delete — tests moved to neocortex |
| `memory/MemoryHygieneSchedulerTest` | Delete |
| `memory/ConfidenceScorerTest` | Delete |
| `memory/DefaultIntegrityCheckerTest` | Delete |
| `memory/FoundationTypesTest` | Delete |

### Stay in agency (11 files)

**`agency-core/src/main/java/io/casehub/agency/social/memory/evolution/` (7 files):**
- `MemoryHygieneProposalSource`, `MemoryHygieneRegressionEvaluator`, `MemoryHygieneCapabilityArea`, `MemoryHygieneCategoryProvider`, `MemoryHygieneConflictStrategy`, `MemoryHygieneOutcome`, `MemoryHygieneOutcomeProvider`
- These implement engine-api SPIs with zero neocortex imports.

**`agency-core/src/main/java/io/casehub/agency/social/cognition/evolution/` (4 files):**
- `CognitiveEvolutionAdapter`, `CognitiveEvolutionConfig`, `CircuitBreakerMoodMapper`, `EvolutionTickBridge`
- Bridge types connecting engine's evolution tick to neocortex's drives/mood.

**Test files for evolution (stay):**
- 5 memory/evolution tests + 3 cognition/evolution tests + 1 cognition adapter test

### Deferred to #490

- `speech-api` cognitive types (PromptSection, PromptContext, AssembledPrompt deletion; AvatarCognition thinning)
- `CognitionAvatarAdapter` simplification

## Re-wiring

### AgencyBeans

1. **Remove** `noOpReflectionStore()` producer — evaluate if still needed
2. **Remove** `noOpSemanticIntegrityChecker()` producer — now in neocortex
3. **Remove** `noOpReflectionQueryStore()` producer — evaluate if still needed
4. **Remove** `neocortexMemoryHygieneSpi()` producer — the adapter bridge is dead
5. **Update** `MemoryHygieneOutcomeProvider` wire-up (D4) — point at neocortex's `MemoryHygieneOrchestratorImpl` via CDI

### AgencyAutoConfiguration

Same changes as AgencyBeans, adapted for Spring's `@Bean`/`@ConditionalOnMissingBean`.

### Import updates in evolution files

The evolution files have zero neocortex imports today, but after deletion they may need to import types that moved. Specifically:
- `MemoryHygieneOutcomeProvider` references `MemoryHygieneOutcome` — both stay in agency, no change needed
- If any evolution file references the old agency `MemoryHygieneOrchestrator` class directly, update the import to `io.casehub.neocortex.cognition.memory.MemoryHygieneOrchestrator`

## CLAUDE.md updates

Remove deleted directories from the Key Directories table. Update package descriptions to reflect that memory hygiene implementation is now in neocortex. Keep evolution directory entries.

## Execution approach

Use IntelliJ refactoring tools with both agency and neocortex in the workspace. No subagents for refactoring. Safe Delete for file removal (catches any remaining references). After deletion, compile both repos to verify no dangling references.

## Testing

1. `mvn --batch-mode install` on agency — must compile clean
2. Run agency tests — evolution tests must still pass
3. Verify neocortex tests still pass (no regression from agency-side cleanup)

## References

- casehubio/agency#447 — this issue
- casehubio/neocortex#1115 — receiving side (closed)
- casehubio/agency#490 — unified prompt assembly (deferred)
- casehubio/eidos#240 — AssembledPrompt extension (closed)
- casehubio/neocortex#1105 — unified prompt assembly master (closed)
- casehubio/neocortex#1119 — native consolidation-phase (future)
- decisions.md — D1 through D4

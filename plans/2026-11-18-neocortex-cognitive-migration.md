# Remove Residual Cognitive Code Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #447 — Remove residual cognitive code — complete neocortex migration
**Issue group:** #447

**Goal:** Delete the 20 `social/memory/` source files and 9 test files from agency whose implementations now live in neocortex (landed via neocortex#1115), remove the MemoryHygieneSpiAdapter bridge, and re-wire AgencyBeans/AgencyAutoConfiguration.

**Architecture:** This is a deletion + re-wiring task. The neocortex `cognition` module already contains the implementations (package `io.casehub.neocortex.cognition.memory`). Agency's copies are dead code. The 11 evolution files stay — they implement engine-api SPIs and bridge engine↔neocortex.

**Tech Stack:** Java 17, Maven, Quarkus CDI (AgencyBeans), Spring Boot (AgencyAutoConfiguration)

## Global Constraints

- IntelliJ workspace must have both `/Users/mdproctor/claude/casehub/agency` and `/Users/mdproctor/claude/casehub/neocortex` open
- Use `ide_refactor_safe_delete` for all Java file deletions — catches remaining references
- Use `ide_refactor_rename` or `ide_edit_member` for import/type changes — never sed/awk
- Compile both repos after changes: `mvn --batch-mode install` on agency
- Commit after every task

---

## Batch 1: Delete dead source files and bridge adapter

### Task 1: Safe-delete the 20 social/memory source files and MemoryHygieneSpiAdapter

**Files:**
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/MemoryHygieneOrchestrator.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/MemoryHygieneScheduler.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/ConfidenceScorer.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/ArousalScorer.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/SurpriseScorer.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/CompositeConfidenceScorer.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/WeightedScorer.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/DefaultIntegrityChecker.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/IntegrityChecker.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/IntegrityViolation.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/ViolationType.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/SemanticIntegrityChecker.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/NoOpSemanticIntegrityChecker.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/ReflectionStore.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/NoOpReflectionStore.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/ReflectionQueryStore.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/NoOpReflectionQueryStore.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/RetentionConfig.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/RetentionScore.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/HygieneEvent.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/HygieneTick.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/memory/MaintenanceTick.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/social/cognition/MemoryHygieneSpiAdapter.java`

**Interfaces:**
- Consumes: nothing
- Produces: clean compilation (deletions only break AgencyBeans/AutoConfig — fixed in Task 2)

- [ ] **Step 1: Dry-run safe-delete on each file to identify blocking usages**

For each of the 23 files above, run `ide_refactor_safe_delete` with `dryRun: true` to see what references would break. Expected blocking usages:
- `MemoryHygieneOrchestrator` → referenced by `AgencyBeans.java:111`, `AgencyAutoConfiguration.java:11`, `MemoryHygieneSpiAdapter.java`
- `NoOpReflectionStore` → referenced by `AgencyBeans.java:139`, `AgencyAutoConfiguration.java:108`
- `NoOpSemanticIntegrityChecker` → referenced by `AgencyBeans.java:144`, `AgencyAutoConfiguration.java:114`
- `NoOpReflectionQueryStore` → referenced by `AgencyBeans.java:149`, `AgencyAutoConfiguration.java:120`
- `ReflectionQueryStore` → referenced by `AgencyBeans.java:29,432`, `AgencyAutoConfiguration.java:22,456`
- `MemoryHygieneSpiAdapter` → referenced by `AgencyBeans.java:339`, `AgencyAutoConfiguration.java:351`

All blocking usages should be in AgencyBeans, AgencyAutoConfiguration, or other files being deleted in this batch. Files only referenced by other deleted files won't block.

- [ ] **Step 2: Force-delete all 23 files**

Use `ide_refactor_safe_delete` with `force: true` for each file. The blocking usages in AgencyBeans/AutoConfig will be fixed in Task 2. Order: delete leaf files first (no-ops, records, enums), then interfaces, then implementations, then the adapter.

Start with files that have no dependents within the delete set:
1. `HygieneEvent.java`, `HygieneTick.java`, `MaintenanceTick.java`, `RetentionConfig.java`, `RetentionScore.java`, `IntegrityViolation.java`, `ViolationType.java`, `WeightedScorer.java`
2. `SemanticIntegrityChecker.java`, `NoOpSemanticIntegrityChecker.java`, `IntegrityChecker.java`, `DefaultIntegrityChecker.java`
3. `ConfidenceScorer.java`, `ArousalScorer.java`, `SurpriseScorer.java`, `CompositeConfidenceScorer.java`
4. `ReflectionStore.java`, `NoOpReflectionStore.java`, `ReflectionQueryStore.java`, `NoOpReflectionQueryStore.java`
5. `MemoryHygieneOrchestrator.java`, `MemoryHygieneScheduler.java`
6. `MemoryHygieneSpiAdapter.java`

- [ ] **Step 3: Delete the 9 test files**

Use `ide_refactor_safe_delete` with `target_type: "file"` for each:
1. `agency/src/test/java/io/casehub/agency/social/memory/MemoryHygieneOrchestratorTest.java`
2. `agency/src/test/java/io/casehub/agency/social/memory/MemoryHygieneSchedulerTest.java`
3. `agency/src/test/java/io/casehub/agency/social/memory/ConfidenceScorerTest.java`
4. `agency/src/test/java/io/casehub/agency/social/memory/DefaultIntegrityCheckerTest.java`
5. `agency/src/test/java/io/casehub/agency/social/memory/FoundationTypesTest.java`

(The 5 evolution test files and 4 cognition evolution test files stay.)

- [ ] **Step 4: Commit deletions**

```bash
git add -A agency-core/src/main/java/io/casehub/agency/social/memory/ agency/src/test/java/io/casehub/agency/social/memory/ agency-core/src/main/java/io/casehub/agency/social/cognition/MemoryHygieneSpiAdapter.java
git commit -m "refactor(#447): delete 23 source + 5 test files migrated to neocortex#1115

Memory hygiene orchestrator, scorers, integrity checkers, reflection
stores, retention logic, and MemoryHygieneSpiAdapter bridge — all now
in neocortex cognition/cognition-api modules.

Evolution files (11) stay in agency — they implement engine-api SPIs.

Refs #447

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 2: Re-wire AgencyBeans and AgencyAutoConfiguration

### Task 2: Fix AgencyBeans — remove dead producers, update ReflectionQueryStore type

**Files:**
- Modify: `agency/src/main/java/io/casehub/agency/AgencyBeans.java`

**Interfaces:**
- Consumes: deletions from Task 1
- Produces: compiling AgencyBeans with no references to deleted types

- [ ] **Step 1: Remove dead imports**

Use `ide_edit_member` to remove these imports from `AgencyBeans.java`:
- `import io.casehub.agency.social.memory.MemoryHygieneOrchestrator;`
- `import io.casehub.agency.social.memory.NoOpReflectionQueryStore;`
- `import io.casehub.agency.social.memory.NoOpReflectionStore;`
- `import io.casehub.agency.social.memory.NoOpSemanticIntegrityChecker;`
- `import io.casehub.agency.social.memory.ReflectionQueryStore;`

Then run `ide_optimize_imports` on the file.

- [ ] **Step 2: Remove dead producer methods**

Remove these methods from `AgencyBeans.java` using `ide_edit_member` (or delete the members):

1. `noOpReflectionStore()` (line ~139-141) — `ReflectionStore` is dead
2. `noOpSemanticIntegrityChecker()` (line ~144-146) — now in neocortex
3. `noOpReflectionQueryStore()` (line ~149-151) — replaced by inline no-op
4. `neocortexMemoryHygieneSpi()` (line ~337-341) — bridge adapter deleted

Also remove the field:
- `@Inject Instance<MemoryHygieneOrchestrator> hygieneOrchestratorInstance;` (line ~111)

- [ ] **Step 3: Add inline no-op DefaultBean for neocortex ReflectionQueryStore**

Since neocortex doesn't provide a `@DefaultBean` for `ReflectionQueryStore`, add one in AgencyBeans to satisfy CDI resolution for `narrativePipeline()`:

```java
@Produces @DefaultBean
public io.casehub.neocortex.memory.ReflectionQueryStore noOpReflectionQueryStore() {
    return new io.casehub.neocortex.memory.ReflectionQueryStore() {
        @Override
        public java.util.List<io.casehub.neocortex.memory.ReflectionEntry> findSince(
                String agentId, String tenantId, java.time.Instant since) {
            return java.util.List.of();
        }
        @Override
        public int countSince(String agentId, String tenantId, java.time.Instant since) {
            return 0;
        }
    };
}
```

- [ ] **Step 4: Update narrativePipeline() parameter type**

Change the `ReflectionQueryStore` parameter type in `narrativePipeline()` (line ~432) from `io.casehub.agency.social.memory.ReflectionQueryStore` to `io.casehub.neocortex.memory.ReflectionQueryStore`.

- [ ] **Step 5: Verify AgencyBeans compiles**

Run `ide_diagnostics` on `AgencyBeans.java`. Expected: no errors.

- [ ] **Step 6: Commit AgencyBeans changes**

```bash
git add agency/src/main/java/io/casehub/agency/AgencyBeans.java
git commit -m "refactor(#447): re-wire AgencyBeans — remove dead memory hygiene producers

Remove: noOpReflectionStore, noOpSemanticIntegrityChecker,
noOpReflectionQueryStore (old agency type), neocortexMemoryHygieneSpi,
hygieneOrchestratorInstance field.

Add: inline no-op DefaultBean for neocortex ReflectionQueryStore.
Update: narrativePipeline() uses neocortex ReflectionQueryStore directly.

Refs #447

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 3: Fix AgencyAutoConfiguration — same cleanup for Spring

**Files:**
- Modify: `agency-spring/src/main/java/io/casehub/agency/spring/AgencyAutoConfiguration.java`

**Interfaces:**
- Consumes: deletions from Task 1
- Produces: compiling AgencyAutoConfiguration with no references to deleted types

- [ ] **Step 1: Remove dead imports and NoOp producers**

Remove these imports:
- `import io.casehub.agency.social.memory.MemoryHygieneOrchestrator;`
- `import io.casehub.agency.social.memory.NoOpReflectionQueryStore;`
- `import io.casehub.agency.social.memory.NoOpReflectionStore;`
- `import io.casehub.agency.social.memory.NoOpSemanticIntegrityChecker;`
- `import io.casehub.agency.social.memory.ReflectionQueryStore;`

Remove these `@Bean` methods:
1. `noOpReflectionStore()` (line ~108)
2. `noOpSemanticIntegrityChecker()` (line ~114)
3. `noOpReflectionQueryStore()` (line ~120)

- [ ] **Step 2: Replace MemoryHygieneSpiAdapter reference in cognitionAvatarAdapter**

At line ~351, change:
```java
.memoryHygiene(hygieneOrchestrator.map(io.casehub.agency.social.cognition.MemoryHygieneSpiAdapter::new).orElse(null))
```
to reference the neocortex implementation directly. Since `MemoryHygieneOrchestratorImpl` now implements the SPI interface, the builder can accept it without an adapter. Change the `Optional<MemoryHygieneOrchestrator>` parameter (the agency type) to `Optional<io.casehub.neocortex.cognition.memory.MemoryHygieneOrchestrator>` (the neocortex SPI), and pass it directly:
```java
.memoryHygiene(hygieneOrchestrator.orElse(null))
```

- [ ] **Step 3: Add inline no-op DefaultBean for neocortex ReflectionQueryStore**

Same as AgencyBeans — add:
```java
@Bean
@ConditionalOnMissingBean
public io.casehub.neocortex.memory.ReflectionQueryStore noOpReflectionQueryStore() {
    return new io.casehub.neocortex.memory.ReflectionQueryStore() {
        @Override
        public java.util.List<io.casehub.neocortex.memory.ReflectionEntry> findSince(
                String agentId, String tenantId, java.time.Instant since) {
            return java.util.List.of();
        }
        @Override
        public int countSince(String agentId, String tenantId, java.time.Instant since) {
            return 0;
        }
    };
}
```

- [ ] **Step 4: Update narrativePipeline() parameter type**

Change the `ReflectionQueryStore` parameter from agency's type to `io.casehub.neocortex.memory.ReflectionQueryStore`.

- [ ] **Step 5: Run ide_optimize_imports and ide_diagnostics**

Verify no compilation errors remain.

- [ ] **Step 6: Commit AgencyAutoConfiguration changes**

```bash
git add agency-spring/src/main/java/io/casehub/agency/spring/AgencyAutoConfiguration.java
git commit -m "refactor(#447): re-wire AgencyAutoConfiguration — same cleanup as AgencyBeans

Remove dead NoOp producers, replace MemoryHygieneSpiAdapter with direct
neocortex SPI reference, add inline no-op for neocortex ReflectionQueryStore.

Refs #447

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 3: Verify and update documentation

### Task 4: Build verification and CLAUDE.md update

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: all deletions and re-wiring from Tasks 1-3
- Produces: clean build, accurate documentation

- [ ] **Step 1: Full build**

```bash
mvn --batch-mode install
```

Expected: BUILD SUCCESS. If failures, diagnose — likely a missed import or deleted type reference.

- [ ] **Step 2: Run tests**

```bash
mvn --batch-mode test
```

Expected: all tests pass. The evolution tests (5 memory + 3 cognition + 1 adapter) should pass unchanged.

- [ ] **Step 3: Update CLAUDE.md Key Directories table**

Remove the `social/memory/` row that describes the deleted types. Update the `social/cognition/` row to note that `MemoryHygieneSpiAdapter` was removed. Keep the evolution directory entries.

Replace the `social/memory/` entry with a brief pointer:
```
| `agency-core/src/main/java/io/casehub/agency/social/memory/evolution/` | Memory hygiene evolution loop — `MemoryHygieneProposalSource` (`ImprovementProposalSource`), `MemoryHygieneRegressionEvaluator` (`RegressionEvaluator`), `MemoryHygieneCategoryProvider`, `MemoryHygieneConflictStrategy`, `MemoryHygieneCapabilityArea`. Core memory hygiene implementation moved to neocortex `cognition` module (neocortex#1115). |
```

Update the `social/cognition/` entry to remove `MemoryHygieneSpiAdapter` from the description.

- [ ] **Step 4: Commit documentation**

```bash
git add CLAUDE.md
git commit -m "docs(#447): update CLAUDE.md — memory hygiene moved to neocortex

Remove deleted social/memory/ directory entry. Update social/cognition/
to remove MemoryHygieneSpiAdapter. Keep evolution directory entries.

Refs #447

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## References

- [2026-11-18-neocortex-cognitive-migration-design.md] — design spec this plan implements
- [decisions.md] — D1 through D4
- [AgencyBeans.java] — Quarkus CDI wiring (lines 111, 139-151, 337-341, 432)
- [AgencyAutoConfiguration.java] — Spring Boot wiring (lines 108-122, 351, 456)
- [neocortex cognition/memory/] — destination of moved types
- [neocortex cognition-api/memory/] — SPI interfaces
- [GitHub #447] — focal issue
- [GitHub neocortex#1115] — receiving side (closed)
- [GitHub #490] — unified prompt assembly (deferred)

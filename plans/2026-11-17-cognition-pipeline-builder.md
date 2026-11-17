# CognitionCore Pipeline Builder Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #484 — Assemble CognitionCore through CognitivePipelineBuilder
**Issue group:** #484

**Goal:** Route all three CognitionCore assembly sites through `CognitivePipelineBuilder` / `CognitionCoreFactory`, then make the constructor package-private to prevent regression.

**Architecture:** Quarkus CDI path delegates to the existing `CognitionCoreFactory` (neocortex). Spring path uses `CognitivePipelineBuilder` directly (no CDI `Instance<T>` available). Test path uses the builder at each fidelity stage. The neocortex constructor visibility change is the last step — enforcement after all callers are migrated.

**Tech Stack:** Java 21, Quarkus CDI, Spring Boot, JUnit 5, Mockito, AssertJ

## Global Constraints

- All three CognitionCore constructor overloads in neocortex become package-private
- `CognitivePipelineBuilder` and `CognitionCoreFactory` are the only public construction paths
- Agency-specific wiring (EmergentGoalPromptSection customizer) stays in agency
- neocortex snapshot must be installed (`mvn install -DskipTests` in neocortex) before agency can pick up the visibility change
- Cross-repo commits: neocortex constructor change is a separate commit in the neocortex repo

---

## Batch 1: Quarkus CDI migration — CognitiveGoalOrchestrator producer + factory delegation

### Task 1: Extract CognitiveGoalOrchestrator as a producer bean and migrate AgencyBeans to CognitionCoreFactory

**Files:**
- Modify: `agency/src/main/java/io/casehub/agency/AgencyBeans.java:118-447`
- Test: `agency/src/test/java/io/casehub/agency/AgencyBeansCognitionWiringTest.java` (create)

**Interfaces:**
- Consumes: `CognitionCoreFactory.create(CognitionConfig)` from neocortex (existing, zero callers until now)
- Produces: `CognitionAvatarAdapter` CDI bean (unchanged), `CognitiveGoalOrchestrator` CDI bean (new producer)

- [ ] **Step 1: Write failing test — CognitionCoreFactory produces a wired core**

Create a unit test that verifies AgencyBeans produces a `CognitionAvatarAdapter` using the factory path. Use Mockito to mock `CognitionCoreFactory` and verify it's called.

```java
package io.casehub.agency;

import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionCore;
import io.casehub.neocortex.cognition.core.CognitionCoreFactory;
import io.casehub.neocortex.cognition.goal.CognitiveGoalConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgencyBeansCognitionWiringTest {

    @Mock CognitionCoreFactory coreFactory;
    @Mock CognitionCore core;
    @Mock MoodOrchestrator mood;

    @Test
    void cognitionAvatarAdapterDelegatesToFactory() {
        when(coreFactory.create(any(CognitionConfig.class))).thenReturn(core);

        // This will fail until AgencyBeans accepts CognitionCoreFactory
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl agency -Dtest=AgencyBeansCognitionWiringTest -f /Users/mdproctor/claude/casehub/agency/pom.xml`
Expected: compilation error — AgencyBeans doesn't accept CognitionCoreFactory yet

- [ ] **Step 3: Add CognitiveGoalOrchestrator producer method to AgencyBeans**

Extract the inline CognitiveGoalOrchestrator construction (AgencyBeans.java:380-407) into a separate `@Produces @ApplicationScoped` method. This allows `CognitionCoreFactory` to resolve it via CDI.

Use `ide_insert_member` to add before `cognitionAvatarAdapter`:

```java
@Produces
@ApplicationScoped
public io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator cognitiveGoalOrchestrator(
        MoodOrchestrator mood,
        CognitiveGoalConfig cognitiveGoalConfig,
        Instance<io.casehub.neocortex.mindmap.MindMapStore> mindMapStore,
        Instance<io.casehub.neocortex.mindmap.GoalAppraisal> goalAppraisal,
        Instance<io.casehub.neocortex.memory.CaseMemoryStore> caseMemoryStore) {
    if (!mindMapStore.isResolvable() || !goalAppraisal.isResolvable()
            || !caseMemoryStore.isResolvable()) {
        return null;
    }
    return new io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator(
            mindMapStore.get(), goalAppraisal.get(),
            caseMemoryStore.get(), cognitiveGoalConfig,
            (agentId, tenantId) -> mood.currentMood(agentId, tenantId)
                    .map(ms -> new io.casehub.neocortex.cognitive.PadProjection(
                            ms.pleasure(), ms.arousal(), ms.dominance()))
                    .orElse(io.casehub.neocortex.cognitive.PadProjection.NEUTRAL),
            java.time.Clock.systemUTC());
}
```

- [ ] **Step 4: Rewrite cognitionAvatarAdapter to use CognitionCoreFactory**

Use `ide_replace_member` to replace the entire `cognitionAvatarAdapter` method (lines 351-447). The new method:
- Injects `CognitionCoreFactory` and `CognitiveGoalOrchestrator` (nullable — from the new producer)
- Calls `coreFactory.create(CognitionConfig.all())`
- Chains the agency-specific `EmergentGoalPromptSection` customizer
- Returns `CognitionAvatarAdapter`

```java
@Produces
@ApplicationScoped
public CognitionAvatarAdapter cognitionAvatarAdapter(
        CognitionCoreFactory coreFactory,
        CognitiveGoalConfig cognitiveGoalConfig,
        Instance<io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator> cgoInstance,
        Instance<GoalProposalOrchestrator> goalProposalInstance) {
    var core = coreFactory.create(CognitionConfig.all());

    var cgo = cgoInstance.isResolvable() ? cgoInstance.get() : null;
    if (cgo != null) {
        var driveGoalOrch = goalProposalInstance.isResolvable()
                ? goalProposalInstance.get() : null;
        core.chainSectionCustomizer(sections -> {
            var result = new java.util.ArrayList<>(sections);
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i) instanceof EmergentGoalPromptSection) {
                    result.set(i, new EmergentGoalPromptSection(
                            driveGoalOrch, cgo, cognitiveGoalConfig));
                    return result;
                }
            }
            result.add(new EmergentGoalPromptSection(
                    driveGoalOrch, cgo, cognitiveGoalConfig));
            return result;
        });
    }

    return new CognitionAvatarAdapter(core,
            nullableFrom(agentRegistryInstance), cgo);
}
```

- [ ] **Step 5: Remove unused Instance fields from AgencyBeans**

Remove the `Instance<T>` fields that the factory now resolves (lines 120-150). Keep only:
- `agentRegistryInstance` — used in CognitionAvatarAdapter construction
- `civilityConstraintInstance`, `traitPressureSourceInstance`, etc. — used by other producer methods
- `agentProviderInstance`, `classifierInstance`, `scoreSourceInstance`, etc. — used by routing producers

Fields to remove (the factory resolves these via its own `@Inject Instance<T>`):
- `hygieneOrchestratorInstance`, `narrativeOrchestratorInstance`, `goalProposalOrchestratorInstance`
- `innerLifeOrchestratorInstance`, `attentionMediatorInstance`, `engagementRecorderInstance`
- `temporalFocusOrchestratorInstance`, `reflectionOrchestratorInstance`, `consolidationMediatorInstance`
- `mindMapStoreInstance`, `goalAppraisalInstance`, `caseMemoryStoreInstance`
- `cognitiveProfileInstance`, `domainActivationInstance`, `subThoughtParticipantInstance`
- `sessionPipelineInstance`

**Careful:** verify each field is not used by any other method before removing. Use `ide_find_references` on each field.

- [ ] **Step 6: Remove unused imports**

Use `ide_optimize_imports` on AgencyBeans.java.

- [ ] **Step 7: Update the test to verify factory delegation**

Complete the test from Step 1 — verify `coreFactory.create()` is called and the result is wrapped in `CognitionAvatarAdapter`.

- [ ] **Step 8: Run all agency tests**

Run: `mvn test -pl agency -f /Users/mdproctor/claude/casehub/agency/pom.xml`
Expected: all tests pass

- [ ] **Step 9: Compile the full reactor**

Run: `mvn compile -f /Users/mdproctor/claude/casehub/agency/pom.xml`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add agency/src/main/java/io/casehub/agency/AgencyBeans.java \
       agency/src/test/java/io/casehub/agency/AgencyBeansCognitionWiringTest.java
git commit -m "feat(#484): route AgencyBeans CognitionCore through CognitionCoreFactory

Extract CognitiveGoalOrchestrator as @Produces bean so the factory
resolves it. Replace 18-arg constructor + 4 manual addParticipant/
chainSectionCustomizer blocks with factory.create(). Agency-specific
EmergentGoalPromptSection customizer stays post-factory.

Closes #484"
```

---

## Batch 2: Spring migration + CognitionStack

### Task 2: Replace AgencyAutoConfiguration hand-build with CognitivePipelineBuilder

**Files:**
- Modify: `agency-spring/src/main/java/io/casehub/agency/spring/AgencyAutoConfiguration.java:314-382`
- Test: existing Spring integration test (verify no regression)

**Interfaces:**
- Consumes: `CognitivePipelineBuilder.create(mood, drives)` from neocortex
- Produces: `CognitionAvatarAdapter` Spring bean (unchanged)

- [ ] **Step 1: Rewrite cognitionAvatarAdapter bean method to use the builder**

Use `ide_replace_member` to replace the method body. The new implementation:
- Creates `CognitivePipelineBuilder` with all resolved parameters
- Calls `builder.build()`
- Chains the EmergentGoalPromptSection customizer (same pattern as Quarkus)
- Returns `CognitionAvatarAdapter`

```java
@Bean
public io.casehub.agency.social.cognition.CognitionAvatarAdapter cognitionAvatarAdapter(
        MoodOrchestrator mood, DriveOrchestrator drives,
        MentalModelOrchestrator mentalModel,
        UserModelOrchestrator userModel,
        StrategyLearningOrchestrator strategy,
        Optional<NarrativeOrchestrator> narrativeOrchestrator,
        Optional<GoalProposalOrchestrator> goalProposalOrchestrator,
        Optional<MemoryHygieneOrchestrator> hygieneOrchestrator,
        Optional<InnerLifeOrchestrator> innerLifeOrchestrator,
        Optional<AgentRegistry> agentRegistry,
        Optional<CognitiveAttentionMediator> attentionMediator,
        Optional<io.casehub.neocortex.memory.engagement.runtime.EngagementRecorderCore> engagementRecorder,
        Optional<TemporalFocusOrchestrator> temporalFocus,
        Optional<ReflectionRetrievalOrchestrator> reflectionOrchestrator,
        Optional<io.casehub.neocortex.cognition.core.ConsolidationMediator> consolidationMediator,
        Optional<io.casehub.neocortex.mindmap.MindMapStore> mindMapStore,
        Optional<io.casehub.neocortex.mindmap.GoalAppraisal> goalAppraisal,
        Optional<io.casehub.neocortex.memory.CaseMemoryStore> memoryStore,
        io.casehub.neocortex.cognition.goal.CognitiveGoalConfig cognitiveGoalConfig,
        Optional<io.casehub.neocortex.cognitive.index.CognitiveProfile> cognitiveProfile,
        Optional<io.casehub.neocortex.cognitive.index.DomainActivation> domainActivation,
        Optional<io.casehub.neocortex.cognitive.index.EffectiveCognitiveDefaults> effectiveDefaults,
        Optional<io.casehub.neocortex.cognition.appraisal.AppraisalStrategy> appraisalStrategy,
        Optional<io.casehub.neocortex.cognition.appraisal.SalienceStrategy> salienceStrategy,
        Optional<io.casehub.neocortex.cognition.subthought.SubThoughtTickParticipant> subThought,
        Optional<io.casehub.neocortex.knowledge.TermNormalizer> termNormalizer,
        Optional<io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator> cogGoalOrch,
        Optional<io.casehub.neocortex.cognition.mood.GoalEmotionMoodBridge> goalEmotionBridge,
        Optional<io.casehub.neocortex.cognitive.index.CognitiveMemoryRetriever> cogMemRetriever,
        Optional<io.casehub.neocortex.entity.token.cdi.EntityIdentityCache> entityCache,
        Optional<io.casehub.neocortex.cognition.journal.CognitiveJournal> cogJournal,
        @Nullable SessionPipeline sessionPipeline) {

    var builder = io.casehub.neocortex.cognition.core.CognitivePipelineBuilder
            .create(mood, drives)
            .userModel(userModel).mentalModel(mentalModel).strategy(strategy)
            .narrative(narrativeOrchestrator.orElse(null))
            .goals(goalProposalOrchestrator.orElse(null))
            .memoryHygiene(hygieneOrchestrator.orElse(null))
            .innerLife(innerLifeOrchestrator.orElse(null))
            .config(io.casehub.neocortex.cognition.core.CognitionConfig.all())
            .mindMapStore(mindMapStore.orElse(null))
            .attentionMediator(attentionMediator.orElse(null))
            .engagementPersister(engagementRecorder
                    .map(r -> (java.util.function.Consumer<io.casehub.neocortex.memory.engagement.EngagementEvent>) r::record)
                    .orElse(null))
            .temporalFocus(temporalFocus.orElse(null))
            .reflectionOrchestrator(reflectionOrchestrator.orElse(null))
            .consolidationMediator(consolidationMediator.orElse(null))
            .sessionPipeline(sessionPipeline);

    effectiveDefaults.ifPresent(builder::effectiveDefaults);
    if (appraisalStrategy.isPresent() && salienceStrategy.isPresent()) {
        builder.appraisal(appraisalStrategy.get(), salienceStrategy.get());
    }
    if (memoryStore.isPresent()) {
        builder.gutFeeling(memoryStore.get());
    }
    subThought.ifPresent(builder::subThoughts);
    if (mindMapStore.isPresent() && termNormalizer.isPresent()) {
        builder.driveGoalBridge(mindMapStore.get(), termNormalizer.get());
    }
    cognitiveProfile.ifPresent(builder::cognitiveProfile);
    domainActivation.ifPresent(builder::domainActivation);
    cogGoalOrch.ifPresent(builder::cognitiveGoalOrchestrator);
    goalEmotionBridge.ifPresent(builder::goalEmotionMoodBridge);
    cogMemRetriever.ifPresent(builder::cognitiveMemoryRetriever);
    entityCache.ifPresent(c -> builder.entityRendering(
            (entityId, tenantId) -> c.getIdentity(entityId, tenantId)));
    cogJournal.ifPresent(builder::cognitiveJournal);

    var core = builder.build();

    // Agency-specific EmergentGoalPromptSection customizer
    var cgo = cogGoalOrch.orElse(null);
    if (cgo != null) {
        var driveGoalOrch = goalProposalOrchestrator.orElse(null);
        core.chainSectionCustomizer(sections -> {
            var result = new java.util.ArrayList<>(sections);
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i) instanceof io.casehub.neocortex.cognition.prompt.EmergentGoalPromptSection) {
                    result.set(i, new io.casehub.neocortex.cognition.prompt.EmergentGoalPromptSection(
                            driveGoalOrch, cgo, cognitiveGoalConfig));
                    return result;
                }
            }
            result.add(new io.casehub.neocortex.cognition.prompt.EmergentGoalPromptSection(
                    driveGoalOrch, cgo, cognitiveGoalConfig));
            return result;
        });
    }

    return new io.casehub.agency.social.cognition.CognitionAvatarAdapter(
            core, agentRegistry.orElse(null), cgo);
}
```

- [ ] **Step 2: Add @ConditionalOnMissingBean for new SPI types**

Add fallback beans for SPIs the builder now resolves that the Spring config didn't previously expose. Use `ide_insert_member` to add near the existing `@ConditionalOnMissingBean` group:

```java
@Bean @ConditionalOnMissingBean
public io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator cognitiveGoalOrchestrator(
        Optional<io.casehub.neocortex.mindmap.MindMapStore> mindMapStore,
        Optional<io.casehub.neocortex.mindmap.GoalAppraisal> goalAppraisal,
        Optional<io.casehub.neocortex.memory.CaseMemoryStore> memoryStore,
        io.casehub.neocortex.cognition.goal.CognitiveGoalConfig config,
        MoodOrchestrator mood) {
    if (mindMapStore.isEmpty() || goalAppraisal.isEmpty() || memoryStore.isEmpty()) {
        return null;
    }
    return new io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator(
            mindMapStore.get(), goalAppraisal.get(), memoryStore.get(), config,
            (agentId, tenantId) -> mood.currentMood(agentId, tenantId)
                    .map(ms -> new io.casehub.neocortex.cognitive.PadProjection(
                            ms.pleasure(), ms.arousal(), ms.dominance()))
                    .orElse(io.casehub.neocortex.cognitive.PadProjection.NEUTRAL),
            java.time.Clock.systemUTC());
}
```

- [ ] **Step 3: Run Spring integration tests**

Run: `mvn test -pl agency-spring -f /Users/mdproctor/claude/casehub/agency/pom.xml`
Expected: all tests pass

- [ ] **Step 4: Compile the full reactor**

Run: `mvn compile -f /Users/mdproctor/claude/casehub/agency/pom.xml`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add agency-spring/src/main/java/io/casehub/agency/spring/AgencyAutoConfiguration.java
git commit -m "feat(#484): route AgencyAutoConfiguration through CognitivePipelineBuilder

Replace 17-arg CognitionCore constructor with builder fluent API.
Add @ConditionalOnMissingBean for CognitiveGoalOrchestrator.
Builder handles all configure*() calls — agency chains only
the EmergentGoalPromptSection customizer.

Refs #484"
```

### Task 3: Replace CognitionStack hand-build with CognitivePipelineBuilder

**Files:**
- Modify: `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/llm/CognitionStack.java`

**Interfaces:**
- Consumes: `CognitivePipelineBuilder.create(mood, drives)` from neocortex
- Produces: `CognitionCore` at five fidelity levels (unchanged API)

- [ ] **Step 1: Rewrite the `from()` factory method to use the builder**

Use `ide_replace_member` to replace the `from(CompiledCognition, AgentProvider, Stage)` method. Each Stage configures the builder incrementally:

```java
public static CognitionStack from(CompiledCognition config,
                                  @Nullable AgentProvider agentProvider,
                                  Stage stage) {
    var moodConfig = config.mood();
    var mood = new MoodOrchestrator(moodConfig);

    DriveOrchestrator drives;
    if (stage.ordinal() >= Stage.REAL_DRIVES.ordinal()) {
        // Real drive sources — same as before
        var curiosity = new CuriosityDrive();
        var competence = new CompetenceDrive();
        var affiliation = new AffiliationDrive();
        var autonomy = new AutonomyDrive();
        drives = new DriveOrchestrator(config.drive(),
                List.of(curiosity, competence, affiliation, autonomy));
    } else {
        drives = new DriveOrchestrator(config.drive(),
                List.of((agentId, tenantId, ctx) ->
                        new DriveIntensity(DriveAxis.CURIOSITY, 0.5, "baseline")));
    }

    var builder = CognitivePipelineBuilder.create(mood, drives);

    if (stage.ordinal() >= Stage.SIGNALS.ordinal()) {
        var cbrStore = /* existing in-memory CBR store setup */;
        var userProfileMemory = new UserProfileMemory(cbrStore);
        var mentalModelMemory = new MentalModelMemory(cbrStore);
        builder.userModel(new UserModelOrchestrator(config.userModel(), userProfileMemory))
               .mentalModel(new MentalModelOrchestrator(config.mentalModel(), mentalModelMemory));
    }

    if (stage.ordinal() >= Stage.REAL_DRIVES.ordinal()) {
        var strategyMemory = new StrategyMemory(cbrStore);
        builder.strategy(new StrategyLearningOrchestrator(config.strategyLearning(), strategyMemory));
        // MemoryHygiene wiring via MemoryHygieneSpiAdapter
    }

    builder.narrative(new NarrativeOrchestrator(config.narrative(), narrativeMemory));

    if (stage.ordinal() >= Stage.FULL.ordinal()) {
        // GoalProposalOrchestrator with drive-goal mappers
        builder.goals(goalProposalOrchestrator);
    }

    var core = builder.build();
    return new CognitionStack(core, mood, drives, /* other orchestrators */);
}
```

The exact orchestrator wiring mirrors the existing code — only the `new CognitionCore(...)` call changes to `builder.build()`. Synthetic CBR priming and LLM-backed narrative extraction stay unchanged.

- [ ] **Step 2: Run agentic-yaml tests**

Run: `mvn test -pl agentic-yaml -f /Users/mdproctor/claude/casehub/agency/pom.xml`
Expected: all tests pass (including CognitionCompilerTest — 9 tests)

- [ ] **Step 3: Commit**

```bash
git add agentic-yaml/src/test/java/
git commit -m "feat(#484): route CognitionStack through CognitivePipelineBuilder

Replace 8-arg CognitionCore constructor with builder at each Stage
fidelity level. Builder now wires regulation and appraisal at
REAL_DRIVES+ stages — capabilities the hand-build lacked.
Synthetic CBR priming and LLM narrative extraction unchanged.

Refs #484"
```

---

## Batch 3: Neocortex enforcement + full build verification

### Task 4: Make CognitionCore constructors package-private in neocortex

**Files:**
- Modify: `neocortex/cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java:154,166,181`

**Interfaces:**
- Produces: compile error for any external `new CognitionCore(...)` — enforcement

- [ ] **Step 1: Change constructor visibility**

In the neocortex repo, use `ide_replace_text_in_file` to change all three constructors from `public` to package-private:

Line 154: `public CognitionCore(` → `CognitionCore(`
Line 166: `public CognitionCore(` → `CognitionCore(`
Line 181: `public CognitionCore(` → `CognitionCore(`

- [ ] **Step 2: Run neocortex tests**

Run: `mvn test -pl cognition -f /Users/mdproctor/claude/casehub/neocortex/pom.xml`
Expected: all tests pass (builder and factory are in the same package)

- [ ] **Step 3: Install neocortex snapshot**

Run: `mvn install -DskipTests -f /Users/mdproctor/claude/casehub/neocortex/pom.xml`

- [ ] **Step 4: Full agency reactor build**

Run: `mvn install -f /Users/mdproctor/claude/casehub/agency/pom.xml`
Expected: BUILD SUCCESS — all three sites now go through the builder

- [ ] **Step 5: Commit neocortex change**

```bash
git -C /Users/mdproctor/claude/casehub/neocortex add cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java
git -C /Users/mdproctor/claude/casehub/neocortex commit -m "feat(#484): make CognitionCore constructors package-private

External code must use CognitivePipelineBuilder or CognitionCoreFactory.
All agency call sites migrated in casehubio/agency#484.

Refs casehubio/agency#484"
```

- [ ] **Step 6: Commit agency changes if any adjustments were needed**

If the full build revealed any issues (import changes, method signature adjustments), commit fixes in agency.

---

## References

- [2026-11-17-cognition-pipeline-builder-design.md] — design spec this plan implements
- [CognitionCoreFactory.java] — neocortex CDI factory
- [CognitivePipelineBuilder.java] — 291-line builder with 14 configure calls
- [CognitionCore.java:154-196] — three public constructors to make package-private
- [AgencyBeans.java:351-447] — Quarkus hand-build to replace
- [AgencyAutoConfiguration.java:314-382] — Spring hand-build to replace
- [CognitionStack.java] — test helper with 5 fidelity stages
- [GitHub #484] — focal issue

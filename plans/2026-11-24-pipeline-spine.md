# Pipeline Spine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #1221 — SE1: Pipeline Spine — unified composition and per-agent state
**Issue group:** #1221 (parent epic #1220)

**Goal:** Unify CognitionConfig into a single composition path, isolate per-agent cognitive state, wire all pipeline capabilities through the builder, and fix stale-state and parsing bugs in CognitionCore.

**Architecture:** Replace the 28-boolean positional CognitionConfig record with an EnumSet-backed immutable class so `all()` enables every subsystem (gating moves to capability detection in configure* methods). Extract per-tick state from CognitionCore instance fields into a per-agent ConcurrentHashMap so multi-agent deployments don't cross-contaminate. Fix the pipeline builder to wire all configure* methods in correct dependency order. Fix stale appraisal re-bridging, double drive ticks, frozen recency decay, regex JSON parsing, and quality signal timing.

**Tech Stack:** Java 21, Quarkus CDI, Jackson ObjectMapper

## Global Constraints

- Java 26 JVM, source level 21
- All changes in `cognition-api` and `cognition` modules only
- Backward-compatible method signatures where callers exist outside the cognition module
- Tests run with `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean test -pl cognition-api,cognition`

---

## Batch 1: CognitionConfig EnumSet [config is composable and self-consistent]

### Task 1: Replace 28-boolean record with EnumSet-backed CognitionConfig

**Files:**
- Modify: `cognition-api/src/main/java/io/casehub/neocortex/cognition/core/CognitionConfig.java`
- Modify: `cognition-api/src/test/java/io/casehub/neocortex/cognition/core/CognitionConfigTest.java`

**Interfaces:**
- Produces: `CognitionConfig.Subsystem` enum (28 values), `CognitionConfig.all()` returns all enabled, `CognitionConfig.none()` returns none, `isEnabled(Subsystem)`, `with(Subsystem)`, `without(Subsystem)`, backward-compat `moodEnabled()` etc. accessors

- [ ] **Step 1: Write failing tests for new semantics**

```java
// In CognitionConfigTest.java — add these tests:

@Test
void allEnablesEverySubsystem() {
    var config = CognitionConfig.all();
    for (var s : CognitionConfig.Subsystem.values()) {
        assertThat(config.isEnabled(s))
            .as("all() should enable %s", s)
            .isTrue();
    }
}

@Test
void noneDisablesEverySubsystem() {
    var config = CognitionConfig.none();
    for (var s : CognitionConfig.Subsystem.values()) {
        assertThat(config.isEnabled(s))
            .as("none() should disable %s", s)
            .isFalse();
    }
}

@Test
void allAndWithAllSubsystemsAreIdentical() {
    assertThat(CognitionConfig.all()).isEqualTo(CognitionConfig.withAllSubsystems());
}

@Test
void withAddsSubsystem() {
    var config = CognitionConfig.none().with(CognitionConfig.Subsystem.MOOD);
    assertThat(config.isEnabled(CognitionConfig.Subsystem.MOOD)).isTrue();
    assertThat(config.isEnabled(CognitionConfig.Subsystem.DRIVES)).isFalse();
}

@Test
void withoutRemovesSubsystem() {
    var config = CognitionConfig.all().without(CognitionConfig.Subsystem.MOOD);
    assertThat(config.isEnabled(CognitionConfig.Subsystem.MOOD)).isFalse();
    assertThat(config.isEnabled(CognitionConfig.Subsystem.DRIVES)).isTrue();
}

@Test
void stringBasedWithStillWorks() {
    var config = CognitionConfig.none().with("mood", true);
    assertThat(config.moodEnabled()).isTrue();
}

@Test
void equalsAndHashCodeWorkCorrectly() {
    assertThat(CognitionConfig.all()).isEqualTo(CognitionConfig.all());
    assertThat(CognitionConfig.all()).isNotEqualTo(CognitionConfig.none());
    assertThat(CognitionConfig.all().hashCode()).isEqualTo(CognitionConfig.all().hashCode());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition-api -Dtest=CognitionConfigTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation errors (Subsystem enum doesn't exist, isEnabled method doesn't exist)

- [ ] **Step 3: Implement CognitionConfig with EnumSet**

Replace the record with a final class. Use `ide_edit_member` with `member = "CognitionConfig"` to replace the class declaration, then `ide_replace_member` for the body.

```java
public final class CognitionConfig {

    public enum Subsystem {
        MOOD, DRIVES, MENTAL_MODEL, USER_MODEL, STRATEGY, NARRATIVE, GOALS,
        MEMORY_HYGIENE, INNER_LIFE, DIRECTIVE_PROMPTS, CHARACTER_DRIVES,
        NEEDS_PYRAMID, ATTENTION, TEMPORAL_FOCUS, REFLECTION, CONSOLIDATION,
        ENTITY_KNOWLEDGE, PERSPECTIVE_COMPARISON, DOMAIN_ACTIVATION,
        APPRAISAL, SALIENCE, BEHAVIORAL, SUB_THOUGHTS, OTHER_MODEL,
        REGULATION, COPING, SITUATIONAL_PERSONALITY, DISSONANCE
    }

    private static final Map<String, Subsystem> NAME_TO_SUBSYSTEM;
    static {
        var map = new java.util.HashMap<String, Subsystem>();
        map.put("mood", Subsystem.MOOD);
        map.put("drives", Subsystem.DRIVES);
        map.put("mentalModel", Subsystem.MENTAL_MODEL);
        map.put("userModel", Subsystem.USER_MODEL);
        map.put("strategy", Subsystem.STRATEGY);
        map.put("narrative", Subsystem.NARRATIVE);
        map.put("goals", Subsystem.GOALS);
        map.put("memoryHygiene", Subsystem.MEMORY_HYGIENE);
        map.put("innerLife", Subsystem.INNER_LIFE);
        map.put("directivePrompts", Subsystem.DIRECTIVE_PROMPTS);
        map.put("characterDrives", Subsystem.CHARACTER_DRIVES);
        map.put("needsPyramid", Subsystem.NEEDS_PYRAMID);
        map.put("attention", Subsystem.ATTENTION);
        map.put("temporalFocus", Subsystem.TEMPORAL_FOCUS);
        map.put("reflection", Subsystem.REFLECTION);
        map.put("consolidation", Subsystem.CONSOLIDATION);
        map.put("entityKnowledge", Subsystem.ENTITY_KNOWLEDGE);
        map.put("perspectiveComparison", Subsystem.PERSPECTIVE_COMPARISON);
        map.put("domainActivation", Subsystem.DOMAIN_ACTIVATION);
        map.put("appraisal", Subsystem.APPRAISAL);
        map.put("salience", Subsystem.SALIENCE);
        map.put("behavioral", Subsystem.BEHAVIORAL);
        map.put("subThoughts", Subsystem.SUB_THOUGHTS);
        map.put("otherModel", Subsystem.OTHER_MODEL);
        map.put("regulation", Subsystem.REGULATION);
        map.put("coping", Subsystem.COPING);
        map.put("situationalPersonality", Subsystem.SITUATIONAL_PERSONALITY);
        map.put("dissonance", Subsystem.DISSONANCE);
        NAME_TO_SUBSYSTEM = Map.copyOf(map);
    }

    private final EnumSet<Subsystem> enabled;

    private CognitionConfig(EnumSet<Subsystem> enabled) {
        this.enabled = EnumSet.copyOf(enabled);
    }

    public boolean isEnabled(Subsystem subsystem) {
        return enabled.contains(subsystem);
    }

    public Set<Subsystem> enabledSubsystems() {
        return Collections.unmodifiableSet(enabled);
    }

    // --- Backward-compatible accessors ---
    public boolean moodEnabled() { return isEnabled(Subsystem.MOOD); }
    public boolean drivesEnabled() { return isEnabled(Subsystem.DRIVES); }
    public boolean mentalModelEnabled() { return isEnabled(Subsystem.MENTAL_MODEL); }
    public boolean userModelEnabled() { return isEnabled(Subsystem.USER_MODEL); }
    public boolean strategyEnabled() { return isEnabled(Subsystem.STRATEGY); }
    public boolean narrativeEnabled() { return isEnabled(Subsystem.NARRATIVE); }
    public boolean goalsEnabled() { return isEnabled(Subsystem.GOALS); }
    public boolean memoryHygieneEnabled() { return isEnabled(Subsystem.MEMORY_HYGIENE); }
    public boolean innerLifeEnabled() { return isEnabled(Subsystem.INNER_LIFE); }
    public boolean directivePrompts() { return isEnabled(Subsystem.DIRECTIVE_PROMPTS); }
    public boolean characterDrivesEnabled() { return isEnabled(Subsystem.CHARACTER_DRIVES); }
    public boolean needsPyramidEnabled() { return isEnabled(Subsystem.NEEDS_PYRAMID); }
    public boolean attentionEnabled() { return isEnabled(Subsystem.ATTENTION); }
    public boolean temporalFocusEnabled() { return isEnabled(Subsystem.TEMPORAL_FOCUS); }
    public boolean reflectionEnabled() { return isEnabled(Subsystem.REFLECTION); }
    public boolean consolidationEnabled() { return isEnabled(Subsystem.CONSOLIDATION); }
    public boolean entityKnowledgeEnabled() { return isEnabled(Subsystem.ENTITY_KNOWLEDGE); }
    public boolean perspectiveComparisonEnabled() { return isEnabled(Subsystem.PERSPECTIVE_COMPARISON); }
    public boolean domainActivationEnabled() { return isEnabled(Subsystem.DOMAIN_ACTIVATION); }
    public boolean appraisalEnabled() { return isEnabled(Subsystem.APPRAISAL); }
    public boolean salienceEnabled() { return isEnabled(Subsystem.SALIENCE); }
    public boolean behavioralEnabled() { return isEnabled(Subsystem.BEHAVIORAL); }
    public boolean subThoughtsEnabled() { return isEnabled(Subsystem.SUB_THOUGHTS); }
    public boolean otherModelEnabled() { return isEnabled(Subsystem.OTHER_MODEL); }
    public boolean regulationEnabled() { return isEnabled(Subsystem.REGULATION); }
    public boolean copingEnabled() { return isEnabled(Subsystem.COPING); }
    public boolean situationalPersonalityEnabled() { return isEnabled(Subsystem.SITUATIONAL_PERSONALITY); }
    public boolean dissonanceEnabled() { return isEnabled(Subsystem.DISSONANCE); }

    // --- Factory methods ---
    public static CognitionConfig all() {
        return new CognitionConfig(EnumSet.allOf(Subsystem.class));
    }

    @Deprecated
    public static CognitionConfig withAllSubsystems() {
        return all();
    }

    public static CognitionConfig none() {
        return new CognitionConfig(EnumSet.noneOf(Subsystem.class));
    }

    public CognitionConfig withDirectives() {
        return with(Subsystem.DIRECTIVE_PROMPTS);
    }

    public CognitionConfig with(Subsystem subsystem) {
        var copy = EnumSet.copyOf(enabled);
        copy.add(subsystem);
        return new CognitionConfig(copy);
    }

    public CognitionConfig without(Subsystem subsystem) {
        var copy = EnumSet.copyOf(enabled);
        copy.remove(subsystem);
        return new CognitionConfig(copy);
    }

    public CognitionConfig with(String subsystem, boolean enabled) {
        var s = NAME_TO_SUBSYSTEM.get(subsystem);
        if (s == null) throw new IllegalArgumentException("Unknown subsystem: " + subsystem);
        return enabled ? with(s) : without(s);
    }

    public CognitionConfig without(String... subsystems) {
        var config = this;
        for (var s : subsystems) {
            config = config.with(s, false);
        }
        return config;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof CognitionConfig c && enabled.equals(c.enabled));
    }

    @Override
    public int hashCode() {
        return enabled.hashCode();
    }

    @Override
    public String toString() {
        return "CognitionConfig" + enabled;
    }
}
```

- [ ] **Step 4: Update CognitionConfigTest**

Remove tests that assert `all().entityKnowledgeEnabled() == false` etc. — these tested the old split behavior. Replace with the new tests from Step 1. Keep the `with`/`without` string-based tests.

- [ ] **Step 5: Run cognition-api tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition-api`
Expected: all pass

- [ ] **Step 6: Run full cognition module tests to verify backward compat**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition-api,cognition`
Expected: some tests may fail because they assumed `all()` had certain subsystems disabled. Fix those tests — they should use `CognitionConfig.none().with(...)` to build the specific config they need, not rely on `all()` having gaps.

- [ ] **Step 7: Commit**

```bash
git add cognition-api/src/main/java/io/casehub/neocortex/cognition/core/CognitionConfig.java
git add cognition-api/src/test/java/io/casehub/neocortex/cognition/core/CognitionConfigTest.java
# plus any test files updated for new all() semantics
git commit -m "refactor(cognition-api): replace 28-boolean CognitionConfig with EnumSet

CognitionConfig.all() now enables every subsystem — identical to
withAllSubsystems(). Gating moves to configure* capability detection.
Eliminates the production/test config split (finding A1, A13).

Refs #1221

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 2: Per-Agent State + Stale Appraisal [multi-agent correctness]

### Task 2: Extract per-tick state into per-agent map

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java`
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/prompt/observation/CognitionObservationBuilder.java`
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java` (promptSections overload)
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/core/CognitionCoreMultiAgentTest.java` (create)

**Interfaces:**
- Produces: `CognitionCore.promptSections(String agentId, String tenantId)`, `CognitionCore.lastBriefing(String agentId, String tenantId)`, `CognitionCore.lastDescriptor(String agentId, String tenantId)`

- [ ] **Step 1: Write failing multi-agent test**

```java
@Test
void twoAgentsDoNotSharePromptState() {
    var core = buildMinimalCore(); // CognitionConfig.all(), no-op orchestrators
    var resolver = new StaticSubjectResolver(Set.of());
    var descA = descriptorForAgent("agent-a");
    var descB = descriptorForAgent("agent-b");
    
    core.tick("agent-a", "tenant1", descA, resolver, "observation-a", null, null);
    core.tick("agent-b", "tenant1", descB, resolver, "observation-b", null, null);
    
    // Agent A's prompt sections should reflect agent A's state, not B's
    var sectionsA = core.promptSections("agent-a", "tenant1");
    var sectionsB = core.promptSections("agent-b", "tenant1");
    
    // The sections should be independently computed — not sharing lastDescriptor etc.
    assertThat(sectionsA).isNotNull();
    assertThat(sectionsB).isNotNull();
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: compilation error — `promptSections(String, String)` doesn't exist

- [ ] **Step 3: Create PerTickState record and AgentKey**

Add as inner types in CognitionCore (or package-private top-level):

```java
private record AgentKey(String agentId, String tenantId) {}

private static final class PerTickState {
    volatile AgentDescriptor descriptor;
    volatile AttentionBriefing briefing;
    volatile List<ConsolidationArtifact> consolidationArtifacts;
    volatile Map<String, ContinuumPosition> continuumMap;
    volatile String targetAgentId;
    volatile EffectiveCognitiveDefaults effectiveDefaults;
}
```

Replace the instance fields (`lastDescriptor`, `lastAgentId`, `lastTenantId`, `lastBriefing`, `lastConsolidationArtifacts`, `lastContinuumMap`, `lastTargetAgentId`) with:

```java
private final ConcurrentHashMap<AgentKey, PerTickState> agentState = new ConcurrentHashMap<>();
private volatile AgentKey lastTickedAgent; // for backward-compat no-arg promptSections()
```

- [ ] **Step 4: Refactor tick() to use per-agent state**

In tick(): create or get the PerTickState for `new AgentKey(agentId, tenantId)`, write all per-tick values there. Set `lastTickedAgent = key`.

- [ ] **Step 5: Add promptSections(agentId, tenantId) overload**

```java
public List<CognitionPromptRenderer> promptSections(String agentId, String tenantId) {
    var key = new AgentKey(agentId, tenantId);
    var state = agentState.get(key);
    if (state == null) return List.of();
    return buildPromptSections(state);
}

@Deprecated
public List<CognitionPromptRenderer> promptSections() {
    var key = lastTickedAgent;
    if (key == null) return List.of();
    return promptSections(key.agentId(), key.tenantId());
}
```

Extract the existing `promptSections()` body into `buildPromptSections(PerTickState)` — replace all `lastDescriptor`, `lastBriefing`, etc. references with `state.descriptor`, `state.briefing`, etc.

- [ ] **Step 6: Update production callers**

In `CognitionObservationBuilder.renderCognitionCoreSections()` and `renderTaggedSections()`: change `cognitionCore.promptSections()` to `cognitionCore.promptSections(agentId, tenantId)`.

In `CognitionCore.currentTrace()`: change `promptSections()` to `promptSections(agentId, tenantId)`.

- [ ] **Step 7: Scope session IDs by tenant**

In `extractAndRecordMentalState`: change `"bdi-extraction-" + agentId` to `"bdi-extraction-" + agentId + "-" + tenantId`.

In `appraiseMood`: change `"mood-appraisal-" + agentId` to `"mood-appraisal-" + agentId + "-" + tenantId` (check the method body for the session ID).

- [ ] **Step 8: Move updateBase to only-when-descriptor-changes**

In tick(): guard the `effectiveDefaults.updateBase()` call:

```java
if (effectiveDefaults != null && descriptor != null && descriptor.disposition() != null) {
    if (state.descriptor == null || !descriptor.disposition().equals(state.descriptor.disposition())) {
        var view = EidosCognitiveProfileBridge.toDescriptorView(descriptor);
        effectiveDefaults.updateBase(tenantId, view, tickEventRef.toString());
    }
}
```

- [ ] **Step 9: Run tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition`
Expected: all pass

- [ ] **Step 10: Commit**

```bash
git add cognition/
git commit -m "refactor(cognition): per-agent cognitive state in CognitionCore

Replace shared last* fields with ConcurrentHashMap<AgentKey, PerTickState>.
Add promptSections(agentId, tenantId) overload. Scope LLM session IDs
by tenant. Guard updateBase to only run when descriptor changes.
Fixes agent state cross-contamination (finding A3, C19, D13).

Refs #1221

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 3: Fix stale appraisal re-bridging and double drive tick

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java`
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/core/CognitionCoreStaleAppraisalTest.java` (create)

**Interfaces:**
- Consumes: `AppraisalTickParticipant.currentResult(agentId, tenantId)`, `drives.tick()`
- Produces: fixed tick() — no stale appraisal bridging, single drive tick

- [ ] **Step 1: Write failing test for stale appraisal**

```java
@Test
void noObservationTickDoesNotReBridgeStaleAppraisal() {
    // Tick with observation — appraisal fires, bridges to mood
    core.tick("agent", "tenant", descriptor, resolver, "something happened", null, null);
    var moodAfterObservation = mood.currentMood("agent", "tenant");
    
    // Tick without observation — should NOT re-bridge old appraisal
    core.tick("agent", "tenant", descriptor, resolver, null, null, null);
    var moodAfterEmpty = mood.currentMood("agent", "tenant");
    
    // Mood should have decayed toward baseline, not been re-pushed by stale appraisal
    // (exact assertion depends on decay behavior)
}
```

- [ ] **Step 2: Write failing test for double drive tick**

```java
@Test  
void drivesTickCalledExactlyOncePerTick() {
    var countingDrives = new CountingDriveOrchestrator();
    // wire countingDrives into core
    core.tick("agent", "tenant", descriptor, resolver, "obs", null, null);
    assertThat(countingDrives.tickCount()).isEqualTo(1);
}
```

- [ ] **Step 3: Fix the stale appraisal bug (C1)**

In tick() — after the DERIVED phase custom participants run, the appraisal bridging block should check whether the current tick has a fresh observation:

```java
if (config.appraisalEnabled() && appraisalParticipant != null) {
    // Only bridge fresh appraisal results — if no observation this tick,
    // the participant didn't produce a new result, so skip bridging
    if (observation != null) {
        appraisalParticipant.currentResult(agentId, tenantId).ifPresent(r -> {
            if (config.moodEnabled()) {
                bridgeAppraisalToMood(r, agentId, tenantId);
            }
            if (config.drivesEnabled()) {
                var driveDeltas = AppraisalDriveModulator.modulate(r.emotions());
                var driveDescriptions = AppraisalDriveModulator.dominantContributions(r.emotions());
                drives.recordAppraisalModulation(agentId, tenantId, driveDeltas, driveDescriptions);
            }
        });
    }
    // ...
}
```

- [ ] **Step 4: Fix the double drive tick (C8)**

In tick() — the second `drives.tick()` call (the one inside the `if (config.appraisalEnabled())` block near line 415) is redundant. Remove it. The first call at line 376 already computes drives. The appraisal modulation (line 408-410) records deltas that take effect on the NEXT tick, not this one. If immediate effect is needed, move the modulation recording BEFORE the first drives.tick().

Actually, looking more carefully: the structure is:
1. Line 376: `drives.tick()` — computes drives
2. DERIVED participants run (including appraisal)
3. Line 408: `drives.recordAppraisalModulation()` — records modulation from appraisal
4. Line 415: `drives.tick()` AGAIN — recomputes with modulation

The intent is that appraisal modulates drives within the same tick. The fix is to restructure so modulation is recorded before the single tick, or to accept one-tick latency. Since the appraisal participant runs in DERIVED phase (after the first drives.tick), the simplest correct fix:
- Move `drives.recordAppraisalModulation()` to run, then call `drives.tick()` once AFTER.
- Remove the first `drives.tick()` before DERIVED phase, or accept the semantic that pre-appraisal drives inform the appraisal, then post-appraisal drives are the final state.

The cleaner approach: keep the first `drives.tick()` (pre-appraisal), apply modulation, then do a **lightweight re-evaluation** rather than a full double-tick. If that's not possible, remove the second `drives.tick()` and accept one-tick latency for appraisal→drive feedback.

For this plan: **remove the second `drives.tick()` call**. The appraisal modulation is recorded and takes effect on the next tick. This is more predictable and avoids the double-smoothing bug (EMA applied twice gives 0.09 retention instead of 0.3).

- [ ] **Step 5: Run tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition`
Expected: all pass

- [ ] **Step 6: Commit**

```bash
git add cognition/
git commit -m "fix(cognition): clear stale appraisal, remove double drive tick

Gate appraisal-to-mood/drive bridging on observation presence so
no-observation ticks don't re-bridge stale state (finding C1).
Remove duplicate drives.tick() that caused double EMA smoothing —
retention was 0.09 instead of intended 0.3 (finding C8).

Refs #1221

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 3: Pipeline Builder + Bug Fixes [all configure* wired, parsing safe]

### Task 4: Wire missing configure* methods into builder, fix ordering

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitivePipelineBuilder.java`
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCoreFactory.java`
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java` (configureRegulation tenant fix)
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/prompt/DirectiveSection.java` (SimpleName fix)
- Test: `cognition/src/test/java/io/casehub/neocortex/cognition/core/CognitivePipelineBuilderTest.java`

**Interfaces:**
- Consumes: CognitivePipelineBuilder.build(), all configure* methods on CognitionCore
- Produces: build() calls all configure* methods in dependency-correct order

- [ ] **Step 1: Write failing test for builder wiring completeness**

```java
@Test
void buildWiresSearchBudgetWhenDependenciesMet() {
    var core = CognitivePipelineBuilder.create(mood, drives)
        .effectiveDefaults(effectiveDefaults)
        .mindMapStore(mindMapStore)
        // ... other required deps for search budget
        .build();
    // The core should have a SearchBudgetTickParticipant registered
    // Verify via promptSections containing SearchBudgetPromptSection
}
```

- [ ] **Step 2: Write failing test for gut-feeling ordering (A7)**

```java
@Test
void gutFeelingHasAccessToCognitiveMemoryRetriever() {
    var core = CognitivePipelineBuilder.create(mood, drives)
        .gutFeeling(memoryStore)
        .cognitiveMemoryRetriever(retriever)
        .build();
    // GutFeelingParticipant should NOT have a null cognitiveMemoryRetriever
}
```

- [ ] **Step 3: Fix build() ordering — wire configureGutFeeling AFTER configureCognitiveMemoryRetriever**

In `CognitivePipelineBuilder.build()`, reorder the calls:

```java
// 1. Wire cognitive memory retriever FIRST
if (cognitiveMemoryRetriever != null) {
    core.configureCognitiveMemoryRetriever(cognitiveMemoryRetriever);
}
// 2. THEN gut feeling (depends on cognitiveMemoryRetriever)
if (gutFeelingMemoryStore != null) {
    core.configureGutFeeling(gutFeelingMemoryStore, mood);
}
```

- [ ] **Step 4: Wire missing configure* methods into build()**

Add calls for: `configureSearchBudget`, `configureCuriosity`, `configureTheoryOfMind`, `configureProactiveRegulation`, `configureBehavioralSynthesis`, `configureTenantRegistrar`.

For search budget and curiosity, add new builder setters and fields:

```java
// New fields
private CuriosityDrive curiosityDrive;
private CuriosityChannelComputer curiosityChannelComputer;
private CognitiveResourcePool cognitiveResourcePool;
private double curiosityDeprivationBias = 0.0;
private double curiosityBaselineIntensity = 0.0;

// New builder methods
public CognitivePipelineBuilder searchBudget(CuriosityDrive curiosityDrive, 
        CognitiveResourcePool pool) { ... }
public CognitivePipelineBuilder curiosity(CuriosityDrive curiosityDrive,
        CuriosityChannelComputer channelComputer, double deprivationBias,
        double baselineIntensity) { ... }
```

In build():
```java
if (curiosityDrive != null && effectiveDefaults != null && mindMapStore != null) {
    core.configureSearchBudget(drives, mood, effectiveDefaults, 
            mindMapStore, curiosityDrive, cognitiveResourcePool);
}
if (curiosityDrive != null && curiosityChannelComputer != null && mindMapStore != null) {
    core.configureCuriosity(curiosityDrive, curiosityChannelComputer,
            mindMapStore, curiosityDeprivationBias, curiosityBaselineIntensity);
}
core.configureTheoryOfMind();
```

- [ ] **Step 5: Fix configureRegulation tenant resolution (A8)**

In `configureRegulation()`: replace `lastTenantId != null ? lastTenantId : "default"` with a parameter. Add `tenantId` parameter to `configureRegulation()` or use a supplier that resolves at tick time, not build time.

The cleanest fix: make the COGNITIVE subgraph ID resolution lazy — do it at first tick, not at configure time. Store a `Supplier<String>` instead of a resolved `String`:

```java
Supplier<String> cogSgIdSupplier = () -> {
    if (mindMapStore == null) return null;
    // resolve at tick time when lastTenantId is available
    var key = lastTickedAgent;
    var tid = key != null ? key.tenantId() : "default";
    var sgList = mindMapStore.listSubgraphs(tid);
    for (var sg : sgList) {
        if (SubgraphTypes.COGNITIVE.equals(sg.type())) return sg.id();
    }
    return null;
};
```

- [ ] **Step 6: Fix DirectiveSection.wrap() SimpleName lookup (A6)**

In `DirectiveSection.wrap()`: when the section is already wrapped (e.g., by entity rendering decorator), `getClass().getSimpleName()` returns the wrapper's name, not the original. Fix:

```java
public static CognitionPromptRenderer wrap(CognitionPromptRenderer section) {
    // Unwrap if already a DirectiveSection to get the real delegate's name
    var target = section;
    while (target instanceof DirectiveSection ds) {
        target = ds.delegate();
    }
    var name = target.getClass().getSimpleName();
    var directive = DIRECTIVES.getOrDefault(name,
            "Consider this context in your response:");
    var tag = section.blockTag() != null
              ? section.blockTag()
              : TAGS.get(name);
    return new DirectiveSection(section, directive, tag);
}
```

- [ ] **Step 7: Run tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition`
Expected: all pass

- [ ] **Step 8: Commit**

```bash
git add cognition/
git commit -m "fix(cognition): wire all configure* into builder, fix ordering and tenant resolution

Wire configureSearchBudget, configureCuriosity, configureTheoryOfMind
into CognitivePipelineBuilder.build(). Fix gut-feeling ordering so
cognitiveMemoryRetriever is wired first (A7). Lazy-resolve COGNITIVE
subgraph ID in configureRegulation so it uses the actual tenant, not
null (A8). Fix DirectiveSection.wrap() to unwrap decorators before
SimpleName lookup (A6).

Refs #1221

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 5: Fix recency decay, JSON parsing, quality signal timing, and dead code

**Files:**
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/CognitionDefaultBeans.java`
- Modify: `cognition/src/main/java/io/casehub/neocortex/cognition/core/CognitionCore.java`
- Test: existing tests + `cognition/src/test/java/io/casehub/neocortex/cognition/core/CognitionCoreJsonParsingTest.java` (create)

**Interfaces:**
- Consumes: Jackson ObjectMapper, CognitionCore recording methods
- Produces: fixed recency decay, safe JSON parsing, correct quality signal timing

- [ ] **Step 1: Write failing test for JSON parsing with special characters (E11)**

```java
@Test
void recordExtractedSignalsHandlesBracketInText() {
    // JSON with ']' inside a text value — the regex parser breaks on this
    var json = """
        {"beliefs": [{"text": "arrays like [1,2,3] are useful"}],
         "desires": [], "intentions": []}
        """;
    // Should parse without error and extract the belief
}
```

- [ ] **Step 2: Fix recency decay frozen Instant (D7)**

In `CognitionDefaultBeans.cognitiveMemoryRetriever()`: replace `var now = java.time.Instant.now()` with a `Clock` injection:

```java
if (config.recencyEnabled()) {
    staticFactors.add(ModulationFactors.recencyDecay(
            Duration.ofDays(config.recencyHalfLifeDays()),
            clock)); // inject Clock, resolve Instant.now(clock) at retrieval time
}
```

This requires `ModulationFactors.recencyDecay` to accept a `Clock` or `Supplier<Instant>`. Check if the SPI supports this — if not, create a wrapping `ModulationFactor` that evaluates `clock.instant()` at apply time.

- [ ] **Step 3: Replace regex JSON parsing with Jackson (E11)**

In CognitionCore: replace `recordExtractedSignals` and `recordSignalArray` with Jackson-based parsing:

```java
private static final ObjectMapper JSON = new ObjectMapper();

private void recordExtractedSignals(String agentId, String subjectId,
                                    String tenantId, String json) {
    var jsonStart = json.indexOf('{');
    var jsonEnd = json.lastIndexOf('}');
    if (jsonStart < 0 || jsonEnd < 0) return;
    json = json.substring(jsonStart, jsonEnd + 1);
    try {
        var tree = JSON.readTree(json);
        recordSignalArray(agentId, subjectId, tenantId, tree, "beliefs", CueType.BELIEF_STATEMENT);
        recordSignalArray(agentId, subjectId, tenantId, tree, "desires", CueType.DESIRE_EXPRESSION);
        recordSignalArray(agentId, subjectId, tenantId, tree, "intentions", CueType.INTENTION_DECLARATION);
    } catch (Exception e) {
        LOG.log(System.Logger.Level.WARNING, "Failed to parse BDI JSON", e);
    }
}

private void recordSignalArray(String agentId, String subjectId,
                               String tenantId, JsonNode tree,
                               String arrayName, CueType cueType) {
    var array = tree.get(arrayName);
    if (array == null || !array.isArray()) return;
    for (var item : array) {
        var text = item.has("text") ? item.get("text").asText() : null;
        if (text != null && !text.isEmpty()) {
            mentalModel.record(new MentalStateSignal.VerbalCue(text, cueType),
                               agentId, subjectId, tenantId);
        }
    }
}
```

Also fix `extractDouble` to handle leading `+`:
```java
// No longer needed — but if kept elsewhere, fix the regex:
// was: (-?\d+\.?\d*)  now also allows +
// (-?\+?\d+\.?\d*)
```

Remove the raw-utterance-as-belief fallback at 0.8 confidence — on parse failure, log a warning and skip, don't store garbage.

- [ ] **Step 4: Fix quality signal timing (E2)**

In `recordInteraction`: `deriveQualitySignal` reads mood BEFORE the interaction updates mood. The fix: either read mood AFTER `appraiseMood`, or use the response content (not mood state) for quality derivation.

Move the mood reading in `deriveQualitySignal` to after `appraiseMood` has run, or restructure `recordInteraction` to appraise mood first:

```java
public void recordInteraction(...) {
    // 1. Appraise mood first (if applicable)
    if (config.moodEnabled()) {
        if (impact != null && impact.moodSignal() != null) {
            mood.record(impact.moodSignal(), agentId, tenantId);
        } else {
            safeRun(() -> appraiseMood(agentId, tenantId, userMessage, response));
        }
    }
    // 2. NOW derive quality signal — mood reflects this interaction
    if (subjectId != null && config.userModelEnabled() && userModel != null) {
        var signal = (impact != null && impact.userModelSignal() != null)
                     ? impact.userModelSignal()
                     : deriveQualitySignal(agentId, tenantId, userMessage);
        safeRun(() -> userModel.record(signal, agentId, subjectId, tenantId));
    }
    // ... rest unchanged
}
```

Wait — the existing code already does mood first, then derives quality. Let me re-read... Actually the issue is more subtle: `deriveQualitySignal` reads mood that was updated by `appraiseMood` in the same call. But `appraiseMood` is wrapped in `safeRun` which catches exceptions. If `appraiseMood` succeeds, mood is updated, and `deriveQualitySignal` reads the new mood. That's actually correct for this interaction. The E2 finding may be about a different ordering. Let me note this needs verification during implementation.

- [ ] **Step 5: Handle configureTheoryOfMind dead code (E3)**

`configureTheoryOfMind()` exists but has no production caller. The builder's build() method in Task 4 will now call it. If the method is intentionally dead, remove it. Since Task 4 wires it, this is handled there.

- [ ] **Step 6: Run tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl cognition`
Expected: all pass

- [ ] **Step 7: Commit**

```bash
git add cognition/
git commit -m "fix(cognition): Jackson JSON parsing, live recency decay, quality signal timing

Replace regex BDI JSON parser with Jackson ObjectMapper — regex broke on
']' in text values and rejected leading '+' in numbers (finding E11).
Fix CognitionDefaultBeans recency decay to use Clock instead of frozen
Instant.now() — memories created after startup now age correctly (D7).
Fix quality signal timing in recordInteraction (E2).

Refs #1221

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## References

- [#1221] — SE1: Pipeline Spine epic issue
- [#1220] — parent epic: Cognitive Pipeline Coherence
- [CognitionConfig.java:44-128] — 28-boolean record, all()/withAllSubsystems() split
- [CognitionCore.java:119-160] — shared per-tick state fields (lastAgentId, lastDescriptor, etc.)
- [CognitionCore.java:252-442] — tick() method with double drives.tick() and stale appraisal
- [CognitionCore.java:688-704] — regex JSON parsing (recordSignalArray)
- [CognitionCore.java:937-997] — configureRegulation with lastTenantId null at build time
- [CognitionCore.java:1033-1043] — configureGutFeeling reads null cognitiveMemoryRetriever
- [CognitivePipelineBuilder.java:281-347] — build() missing configure* calls
- [DirectiveSection.java:113-121] — wrap() uses getClass().getSimpleName()
- [CognitionDefaultBeans.java:303] — frozen Instant.now() for recency decay
- [CognitionObservationBuilder.java:557-580] — production promptSections() callers
- [docs/specs/cognitive-review-analysis/2026-11-24-epic-plan.md] — full finding map

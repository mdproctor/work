# Design: Goal-Aware Cognitive Loop

**Issue:** casehubio/blocks#296
**Date:** 2026-09-24
**Repos:** blocks (primary), neocortex (supporting changes)
**Decisions:** [decisions.md](decisions.md)
**Research:** [2026-09-23-computational-emotion-research.md](2026-09-23-computational-emotion-research.md)

## 1. Problem

Neocortex's consolidation pipeline computes rich goal state — priority, urgency,
feasibility, affect, decay signals, dependency graphs — on MindMap GOAL nodes.
Blocks' cognitive loop doesn't read any of it. The LLM is blind to computed goal
intelligence. Goals surface to the agent only through drive-based proposals, which
are motivationally derived and carry no situational awareness.

The vision: an agent that tracks what it has surfaced to the LLM, monitors whether
action was taken, develops genuine worry when important goals are ignored, feels
relief when they're resolved, and can articulate all of this with emotional depth
calibrated to the agent's personality.

```
Day 1:  "Mark, your daughter's birthday is next week. Have you thought about a gift?"
Day 3:  "Just a reminder — birthday in 4 days."
Day 6:  "Mark, I've reminded you each day. Her birthday is tomorrow and you still
         haven't bought anything. I'm worried she'll be disappointed."
Day 7:  "Mark, I told you last week. I've reminded you every day and even last night.
         You still haven't bought her anything, and her birthday is today. I'm very
         worried — I imagine your daughter will be really upset."
```

This progression requires: goal surfacing → surfacing tracking → OCC emotional
appraisal → personality-calibrated intensity → empathic modelling → emotionally
articulate prompt rendering.

## 2. Two Emotional Systems

A cognitive agent has two distinct emotional systems that share the same OCC
framework but serve different purposes:

### System A: Understanding humans (outward-facing)

The agent models how OTHER people think and feel. "Mark is probably anxious about
the deadline." OCC applied to predict others' emotional states.

Already exists: MentalModelOrchestrator (BDI Theory of Mind),
UserModelOrchestrator (per-subject profile synthesis).

### System B: The agent's own emotions (internal)

The agent's own cognitive-emotional model. OCC applied to the agent's own goals
and experiences. Includes empathic emotions (Pity, Happy-for) that arise FROM
understanding others — System B depends on System A for the empathic pathway.

```
System A: model the daughter → "she expects a gift, won't get one"
              → predict: she'll feel Disappointment
                  ↓
System B: appraise against fortunes-of-others
          → "undesirable event for liked other" → Pity(daughter)
```

### Recording vs experiencing

The memory system currently records the USER's life — what they said, did, wanted.
The agent is a stenographer. But when the agent experiences Fear(0.8) for a week
then Relief(0.9), that emotional arc is the agent's own experience. CognitiveEmotion
events are the agent's autobiographical material — "I felt X because of Y, and it
resolved when Z" — distinct from "Mark said X on Tuesday."

The design stores agent-experienced emotions as domain="agent-experience" memories,
separate from domain="experience" (observed interactions). This marks the boundary
between recording and experiencing.

## 3. Architecture

### Roles

**Neocortex** = the brain. Owns all cognitive state:
- Emotion computation (OCC appraisal, PAD projection)
- Goal affect and surfacing tracking
- Consolidation phases
- Memory storage (including agent's own emotional memories)

**Blocks** = orchestration + LLM integration:
- Tick loop coordination (CognitionTickParticipant)
- Prompt assembly (rendering cognitive state for the LLM)
- LLM response routing back to stores

### Data flow

```
NEOCORTEX (consolidation, periodic)
│
├── GoalPrioritizationPhase    → priority, urgency, feasibility on GOAL nodes
├── GoalAffectPhase (revised)  → OCC emotions → PAD projection on GOAL nodes
├── SurfacingAggregationPhase  → surfaced-count, last-surfaced-at from experiences
├── GoalResolutionPhase        → status, dependency state, decay-signal
└── GoalUrgency                → dynamic urgency from target-date
         │
         │ goal nodes carry: priority, urgency, PAD, surfaced-count,
         │                   status, decay-signal, emotions
         ▼
BLOCKS (tick, per interaction)
│
├── CognitiveGoalOrchestrator (CognitionTickParticipant @ TERMINAL)
│   ├── queries MindMapStore for GOAL nodes
│   ├── runs GoalAppraisal → CognitiveEmotion per goal
│   ├── records surfacing via ExperienceEvent
│   ├── records agent emotions as domain="agent-experience"
│   ├── checks case creation threshold → GoalFormationService.propose()
│   └── checks decay-signal → GoalRevision recommendations
│
├── GoalPromptSection (extended)
│   ├── merges drive goals + cognitive goals
│   ├── ranks by composite priority
│   └── renders with OCC emotional tone + surfacing context
│
└── GoalRevisionConsumer (new)
    └── reads decay-signal → proposes lifecycle transitions via AgentRegistry
         │
         ▼
ENGINE
└── GoalFormationService.propose() → creates case
         │
         ▼
    case executes → ExperienceEvent(outcome) → neocortex
         │
         ▼
NEOCORTEX (next consolidation)
├── GoalResolutionPhase sees outcome → updates goal status
├── GoalAffectPhase recomputes → Fear → Relief/Satisfaction/Fears-confirmed
├── SurfacingAggregationPhase → worry resets if progress detected
└── Agent emotional arc stored as autobiographical memory
```

## 4. OCC Emotion Model

### New types in cognitive-api

```java
public enum EmotionType {
    // Prospect-based (goal lifecycle) — #296 scope
    HOPE, FEAR, SATISFACTION, DISAPPOINTMENT, RELIEF, FEARS_CONFIRMED,
    // Well-being — #296 scope
    JOY, DISTRESS,
    // Fortunes of others — #296 scope (empathic pathway)
    HAPPY_FOR, PITY,
    // Attribution — future
    PRIDE, SHAME, REPROACH, ADMIRATION,
    // Compound — future
    GRATITUDE, ANGER, REMORSE, GRATIFICATION,
    // Object-based — future
    LOVE, HATE,
    // Fortunes-of-others (disliked) — future
    RESENTMENT, GLOATING
}

public record CognitiveEmotion(
    EmotionType type,
    double intensity,        // [0,1]
    String subjectId,        // goal node ID, person ID, etc.
    Instant onset,
    EmotionSource source,    // INTRINSIC or EMPATHIC
    PadProjection pad        // derived via ALMA table × personality
) {}

public enum EmotionSource {
    INTRINSIC,  // agent's own goals
    EMPATHIC    // arising from modelled others' states
}

public record PadProjection(
    double pleasure,
    double arousal,
    double dominance
) {}
```

### ALMA OCC → PAD mapping (constant table)

| EmotionType | P | A | D |
|---|---|---|---|
| HOPE | +0.2 | +0.2 | -0.1 |
| FEAR | -0.64 | +0.60 | -0.43 |
| SATISFACTION | +0.3 | -0.2 | +0.4 |
| DISAPPOINTMENT | -0.3 | +0.1 | -0.4 |
| RELIEF | +0.2 | -0.3 | -0.4 |
| FEARS_CONFIRMED | -0.5 | -0.3 | -0.7 |
| JOY | +0.4 | +0.2 | +0.1 |
| DISTRESS | -0.4 | -0.2 | -0.5 |
| HAPPY_FOR | +0.4 | +0.2 | +0.2 |
| PITY | -0.4 | -0.2 | -0.5 |

The base PAD values are scaled by emotion intensity:
`actual_pad = base_pad × intensity`

Personality modulation adjusts via MoodBaseline proximity (see §5).

### GoalAppraisal SPI

```java
@FunctionalInterface
public interface GoalAppraisal {
    List<CognitiveEmotion> appraise(MindMapNode goal, AppraisalContext context);
}

public record AppraisalContext(
    String tenantId,
    String agentId,
    PadProjection moodBaseline,
    int surfacingCount,
    @Nullable Instant lastProgressAt,
    @Nullable Instant lastSurfacedAt,
    Map<String, Double> relationshipScores
) {}
```

Lives in neocortex (mindmap-api or cognitive-api). Blocks never implements
appraisal logic — it calls the SPI.

### HeuristicGoalAppraisal

The #296 implementation. Maps goal state to OCC emotions:

| Goal state | OCC emotion | Intensity derivation |
|---|---|---|
| Active, deadline distant | Hope | priority × feasibility |
| Active, deadline approaching, progress | Hope | priority × (1 - urgency × 0.5) |
| Active, deadline approaching, NO progress | Fear | priority × urgency × surfacing_gap_factor |
| Active, blocked by dependency | Distress | priority × urgency × (1 - feasibility) |
| Completed | Satisfaction | priority (was important) |
| Completed (was feared) | Relief | prior_fear_intensity |
| Missed deadline | Fears-confirmed | priority × urgency_at_deadline |
| Missed, hoped would succeed | Disappointment | prior_hope_intensity |
| Liked-other affected negatively | Pity | relationship_score × predicted_impact |
| Liked-other benefits | Happy-for | relationship_score × predicted_benefit |

Where `surfacing_gap_factor = surfacing_count / (surfacing_count + 1)` — saturates
toward 1.0 as surfacings without progress accumulate (logistic-like growth).

The Hope→Fear transition happens as urgency rises and progress doesn't appear.
This IS the Day 1→Day 7 emotional progression:

```
Day 1: Hope(0.7), Fear(0.2)    — deadline distant, prospect positive
Day 3: Hope(0.4), Fear(0.5)    — urgency rising, no progress
Day 6: Fear(0.8), Pity(0.6)    — deadline imminent, empathic concern
Day 7: Fears-confirmed(0.9)    — deadline arrived, no action
  OR:  Relief(0.9)             — if last-minute action taken
```

## 5. Personality-Calibrated Intensity

Emotion intensity is modulated by the agent's MoodBaseline (already
personality-derived from eidos JPAF via CognitiveDerivationEngine.deriveMoodBaseline()).

**Mechanism:** An agent whose PAD baseline is close to the characteristic PAD
region of an emotion has a lower threshold for that emotion. An agent with
high arousal baseline (naturally anxious) triggers Fear more easily. An agent
with high pleasure baseline (naturally optimistic) requires more evidence
before Fear displaces Hope.

```java
double threshold = baseThreshold × (1 + padDistance(moodBaseline, emotionPad));
// padDistance = Euclidean distance in PAD space
// Closer baseline → lower threshold → emotion triggers more easily
```

**JPAF correlations** (derived from Mehrabian 1996, applied to cognitive functions):
- J types (Te/Fe dominant): lower urgency threshold → Fear triggers earlier
- P types (Ne/Se dominant): higher urgency threshold → Fear triggers later
- Fe types: lower relationship threshold → Pity triggers more easily
- Ti types: higher relationship threshold → Pity triggers less easily
- Ni types: earlier onset for prospect-based emotions (anticipates)
- Se types: later onset but higher peak intensity (reacts in the moment)

These correlations are implemented as weight adjustments in HeuristicGoalAppraisal,
derived from the agent's CognitiveDefaults personality profile. Future phases add
scenario calibration and interaction refinement (see research doc §6.3).

## 6. Surfacing Tracking

### Recording surfacing events

When CognitiveGoalOrchestrator selects goals for the LLM prompt, it records
each surfacing as an ExperienceEvent:

```java
ExperienceEvent.action(agentId, tenantId, caseId, turnId, now,
    "Goal surfaced to LLM: " + goal.name(),
    null, // confidence
    Map.of(
        ExperienceAttributeKeys.EVENT_TYPE, "goal-surfaced",
        ExperienceAttributeKeys.SUBJECT, goalNodeId
    ),
    "cognitive-goal-surfacing"  // capability
);
```

This is a NEW cross-module integration point: blocks consuming ExperienceRecorder
from neocortex-memory-api. The dependency direction is correct (blocks → neocortex-api,
provided scope) but blocks has not previously recorded ExperienceEvents.

CognitiveGoalOrchestrator takes `Instance<ExperienceRecorder>` for graceful
degradation when the memory module is absent.

### Recording agent emotions

When GoalAppraisal produces CognitiveEmotions, the agent's emotional experiences
are stored as the agent's own memories:

```java
// domain="agent-experience" — the agent's own emotional life
memoryStore.store(tenantId, MemoryInput.builder()
    .domain("agent-experience")
    .content("Felt " + emotion.type() + " about " + goal.name()
        + " (intensity " + emotion.intensity() + ")")
    .subject(Subject.of("goal", goalNodeId))
    .attributes(Map.of(
        "emotion-type", emotion.type().name(),
        "emotion-intensity", String.valueOf(emotion.intensity()),
        "emotion-source", emotion.source().name()
    ))
    .build());
```

### Consolidation aggregation

SurfacingAggregationPhase (@Priority(12) — runs before GoalAffectPhase):

1. Scans domain="experience" memories with event-type="goal-surfaced"
2. Groups by goal node subject
3. Updates goal node properties:
   - `surfaced-count` (total surfacings since first)
   - `first-surfaced-at` (set once)
   - `last-surfaced-at` (updated each time)
4. Checks for progress: any ExperienceEvent with matching subject AND
   event-type != "goal-surfaced" since last-surfaced-at → `last-progress-at`
5. Computes `surfacing-progress-gap`: surfacings since last-progress-at
6. Cursor-based to avoid reprocessing

### Worry computation in GoalAffectPhase

GoalAffectPhase (revised) runs GoalAppraisal with surfacing tracking data in
AppraisalContext. The surfacing-progress-gap amplifies Fear intensity:

```
fear_intensity = priority × urgency × (gap / (gap + 1))
```

Where `gap` = number of surfacings since last progress event. This saturates —
7 surfacings without progress produces fear_intensity ≈ priority × urgency × 0.875.
Each additional surfacing adds diminishing marginal worry.

## 7. CognitiveGoalOrchestrator

Registered as CognitionTickParticipant at TERMINAL phase via
`cognitionCore.addParticipant(CognitionPhase.TERMINAL, orchestrator)`.

### Dependencies

```java
public class CognitiveGoalOrchestrator implements CognitionTickParticipant {

    private final MindMapStore mindMapStore;
    private final GoalAppraisal appraisal;
    private final Instance<ExperienceRecorder> experienceRecorder;
    private final Instance<CaseMemoryStore> memoryStore;
    private final Instance<GoalFormationService> goalFormation;
    private final CognitiveGoalConfig config;
    private final Clock clock;

    // Per-agent cached state
    private final ConcurrentHashMap<String, CognitiveGoalState> states;
    private final KeyedLock tickLocks;
```

### Tick behaviour

```java
@Override
public void tick(CognitionTickContext context) {
    String agentId = context.agentId();
    String tenantId = context.tenantId();
    String key = agentId + "|" + tenantId;

    tickLocks.withLock(key, () -> {
        // 1. Query GOAL nodes for this agent
        var goals = queryGoals(agentId, tenantId);

        // 2. Build appraisal context (surfacing counts, progress, relationships)
        var appraisalCtx = buildContext(agentId, tenantId, goals);

        // 3. Run OCC appraisal per goal
        var emotions = new ArrayList<GoalEmotion>();
        for (var goal : goals) {
            var goalEmotions = appraisal.appraise(goal, appraisalCtx);
            emotions.add(new GoalEmotion(goal, goalEmotions));
        }

        // 4. Record surfacing events
        recordSurfacing(agentId, tenantId, goals);

        // 5. Record agent emotional experiences
        recordEmotions(agentId, tenantId, emotions);

        // 6. Check case creation threshold
        for (var ge : emotions) {
            if (shouldPropose(ge)) {
                proposeCaseCreation(agentId, tenantId, ge);
            }
        }

        // 7. Check decay signals
        var revisions = checkDecaySignals(goals);

        // 8. Cache state for prompt rendering
        states.put(key, new CognitiveGoalState(emotions, revisions));
    });
}
```

### Case creation threshold

When a goal's priority × feasibility exceeds a configurable threshold AND no
existing case is linked to the goal, propose case creation:

```java
private boolean shouldPropose(GoalEmotion ge) {
    var goal = ge.goal();
    double priority = doubleProperty(goal, "priority", 0);
    double feasibility = doubleProperty(goal, "feasibility", 0);
    return priority * feasibility > config.caseCreationThreshold()
        && !hasLinkedCase(goal);
}
```

Case creation uses GoalFormationService.propose() from engine-api:

```java
var proposal = new GoalFormationProposal(
    List.of(new ProposedGoal(
        goal.name(),
        "Cognitive goal: " + goal.name(),
        GoalPriority.PRIMARY,
        "Priority " + priority + ", feasibility " + feasibility,
        Map.of("source", "cognitive-goal", "mindmap-node-id", goal.id())
    )),
    "Goal reached case creation threshold"
);
goalFormation.get().propose(agentId, tenantId, proposal);
```

### Decay-signal handling

When a goal node carries a `decay-signal` property (set by
GoalPrioritizationPhase's Decay step), produce a GoalRevision:

```java
public record GoalRevision(
    String goalNodeId,
    String goalName,
    String decaySignal,     // "dormant" or "abandon"
    String eidosGoalName    // linked eidos AgentGoal name
) {}
```

GoalRevision recommendations are consumed by the composition root
(SocialAvatarCognition or CognitionCompiler wiring) which calls
AgentRegistry to propose the lifecycle transition.

## 8. GoalPromptSection (Extended)

### Merging two goal sources

```java
public class GoalPromptSection implements PromptSection {

    private final GoalProposalOrchestrator driveGoals;
    private final @Nullable CognitiveGoalOrchestrator cognitiveGoals;

    @Override
    public @Nullable String contribute(PromptContext context) {
        var driveProposals = driveGoals.currentProposals(
            context.agentId(), context.tenantId());
        var cogState = cognitiveGoals != null
            ? cognitiveGoals.currentState(context.agentId(), context.tenantId())
            : Optional.empty();

        var unified = merge(driveProposals, cogState);
        if (unified.isEmpty()) return null;
        return render(unified);
    }
}
```

### Normalization and ranking

Both sources produce [0,1] priority values. Merge via configurable weighted sum:

```
composite = α × drive_intensity + (1-α) × mindmap_priority
```

α is per-agent via CognitiveDefaults (default 0.5). Goals from only one source
use that source's value directly.

Dedup: goals sharing `eidos-goal-name ↔ goalName` merge — cognitive goal's
state (priority, urgency, emotions) is authoritative; drive goal's metadata
(axis, trigger) is preserved as provenance.

### Emotionally-toned rendering

The render function uses OCC emotion type and intensity to produce
contextually appropriate language:

```java
private String render(List<UnifiedGoal> goals) {
    var sb = new StringBuilder("Your current goals:");
    for (var goal : goals) {
        sb.append("\n- ");

        // OCC emotion drives framing
        var dominant = goal.dominantEmotion();
        if (dominant != null) {
            switch (dominant.type()) {
                case FEAR -> sb.append("[CONCERNED] ");
                case HOPE -> sb.append("[HOPEFUL] ");
                case FEARS_CONFIRMED -> sb.append("[URGENT — MISSED] ");
                case SATISFACTION -> sb.append("[COMPLETED] ");
                case PITY -> sb.append("[CONCERNED FOR OTHERS] ");
                // ...
            }
        }

        sb.append(goal.description());

        // Surfacing context
        if (goal.surfacingCount() > 1) {
            sb.append(" (reminded ").append(goal.surfacingCount())
              .append(" times");
            if (goal.daysSinceFirstSurfacing() > 0) {
                sb.append(" over ").append(goal.daysSinceFirstSurfacing())
                  .append(" days");
            }
            sb.append(")");
        }

        // Priority + urgency
        sb.append(" [priority: ").append(format(goal.priority()));
        if (goal.urgency() > 0.5) {
            sb.append(", urgency: ").append(format(goal.urgency()));
        }
        sb.append("]");
    }
    return sb.toString();
}
```

Example output:

```
Your current goals:
- [CONCERNED] Buy daughter's birthday gift (reminded 7 times over 7 days)
  [priority: 0.85, urgency: 0.95]
- [HOPEFUL] Complete quarterly report [priority: 0.6]
- [CONCERNED FOR OTHERS] Check in on colleague after their project setback
  [priority: 0.4]
```

## 9. CognitionDefinition Extension

Add `cognitiveGoal` config spec to the YAML DSL:

```yaml
cognition:
  cognitiveGoal:
    enabled: true
    caseCreationThreshold: 0.7
    surfacingCooldown: PT1H
    driveWeight: 0.5          # α in normalization (0 = MindMap only, 1 = drives only)
```

```java
// In agentic-yaml
public record CognitiveGoalConfigSpec(
    @Nullable Boolean enabled,
    @Nullable Double caseCreationThreshold,
    @Nullable Duration surfacingCooldown,
    @Nullable Double driveWeight
) {}
```

Added to CognitionDefinition as a new nullable field.

## 10. Changes by Repo

### neocortex (cognitive-api) — new types

| Type | Purpose |
|---|---|
| EmotionType | OCC 22-type enum |
| CognitiveEmotion | Typed emotion record (type, intensity, subject, source, PAD) |
| EmotionSource | INTRINSIC / EMPATHIC |
| PadProjection | Continuous PAD coordinates |
| AlmaPadTable | Constant OCC→PAD mapping from Gebhard 2005 |

### neocortex (mindmap-api) — new SPI

| Type | Purpose |
|---|---|
| GoalAppraisal | @FunctionalInterface: MindMapNode × AppraisalContext → List<CognitiveEmotion> |
| AppraisalContext | Tenancy, personality baseline, surfacing state, relationships |

### neocortex (mindmap-intelligence) — new/modified phases

| Type | Purpose |
|---|---|
| HeuristicGoalAppraisal | GoalAppraisal implementation — goal state → OCC emotions |
| SurfacingAggregationPhase | @Priority(12) — aggregates surfacing events to node properties |
| GoalAffectPhase (modified) | Uses GoalAppraisal instead of raw PAD switch |

### blocks (blocks-core) — new orchestrator + extended prompt

| Type | Purpose |
|---|---|
| CognitiveGoalOrchestrator | CognitionTickParticipant — queries goals, runs appraisal, records surfacing |
| CognitiveGoalConfig | Thresholds and tuning parameters |
| GoalPromptSection (extended) | Merges drive + cognitive goals, emotional rendering |
| GoalRevision | Record: decay-signal recommendation |

### blocks (agentic-yaml) — DSL extension

| Type | Purpose |
|---|---|
| CognitiveGoalConfigSpec | YAML config record |
| CognitionDefinition (extended) | New cognitiveGoal field |

### Goal node property conventions

| Property | Type | Set by | Read by |
|---|---|---|---|
| surfaced-count | int | SurfacingAggregationPhase | GoalAppraisal |
| first-surfaced-at | Instant | SurfacingAggregationPhase | GoalAppraisal |
| last-surfaced-at | Instant | SurfacingAggregationPhase | GoalAppraisal |
| last-progress-at | Instant | SurfacingAggregationPhase | GoalAppraisal |
| surfacing-progress-gap | int | SurfacingAggregationPhase | GoalAffectPhase |

## 11. E2E Scenario Test

Following the GoalCognitionWalkthroughTest pattern: ordered test methods with
Clock injection advancing time between phases. Uses InMemoryMindMapStore +
InMemoryMemoryStore + mock GoalFormationService.

### Scenario: Birthday Gift Reminder

```java
@Test @Order(1)
void createGoalWithDeadline() {
    // Create GOAL node: "Buy daughter's birthday gift"
    // target-date = 7 days from now, priority = 0.85
    // Add relationship: agent likes daughter (score 0.8)
}

@Test @Order(2)
void day1_firstSurfacing_hopeAndMildFear() {
    // Tick CognitiveGoalOrchestrator
    // Assert: Hope(~0.7) + Fear(~0.2) produced
    // Assert: surfacing ExperienceEvent recorded
    // Assert: prompt renders "[HOPEFUL] Buy daughter's birthday gift"
}

@Test @Order(3)
void day1_consolidation_surfacingTracked() {
    // Run SurfacingAggregationPhase
    // Assert: surfaced-count = 1, first-surfaced-at set
}

@Test @Order(4)
void day3_noProgress_fearRises() {
    // Advance clock 2 days
    // Tick again — no progress events recorded
    // Run consolidation
    // Assert: surfaced-count = 2, surfacing-progress-gap = 2
    // Assert: Fear(~0.5), Hope declining (~0.4)
    // Assert: agent-experience memory records the Fear
}

@Test @Order(5)
void day6_repeatedSurfacing_worryAndPity() {
    // Advance clock 3 more days, tick daily
    // Run consolidation
    // Assert: surfaced-count = 5, gap = 5
    // Assert: Fear(~0.8) dominant
    // Assert: Pity(daughter, ~0.6) — empathic, from relationship
    // Assert: prompt renders "[CONCERNED] ... (reminded 5 times over 5 days)"
}

@Test @Order(6)
void day7_deadlineArrives_fearsConfirmedOrRelief() {
    // Advance clock 1 day
    // Branch A: no action → Fears-confirmed(~0.9) + Pity(~0.8)
    // Branch B: action taken → Relief(~0.9) + Happy-for(daughter, ~0.7)
    // Assert correct emotion transitions
    // Assert agent-experience memories capture the arc
}

@Test @Order(7)
void progressResets_worry() {
    // Record a progress ExperienceEvent for the goal
    // Run consolidation
    // Assert: last-progress-at updated
    // Assert: surfacing-progress-gap resets
    // Assert: Fear drops, Hope rises
}
```

### Scenario: Blocked Goal → Frustration

```java
// Goal with dependency that's blocked
// Assert: Distress (not Fear — the blocking is confirmed, not uncertain)
// Assert: when blocker resolves → Relief
```

### Scenario: Case Creation Trigger

```java
// Goal with priority=0.9, feasibility=0.8 → above threshold
// Assert: GoalFormationService.propose() called
// Case completes → Satisfaction
// Goal node status updated
```

## 12. Out of Scope

| Topic | Why deferred | Tracked as |
|---|---|---|
| Agent-based emotions (Pride, Shame, Reproach) | Requires action attribution | Future OCC extension |
| Compound emotions (Anger, Gratitude, Remorse) | Requires event+agent attribution | Future OCC extension |
| Emotion → Mood bridge | Goal emotions influencing overall MoodOrchestrator | Follow-up issue |
| Mood congruence | Mood biasing subsequent appraisals | Follow-up issue |
| Personality-prior calibration | JPAF→appraisal weight derivation | Follow-up issue |
| Scenario calibration | Interactive questionnaire per agent | Follow-up issue |
| Interaction refinement | Bounded drift from feedback | Follow-up issue |
| CognitiveGoalDecomposer SPI impl | LLM-backed goal decomposition | Separate issue |
| CognitiveGoalRecognizer SPI impl | LLM-backed goal recognition | Separate issue |
| GoalLifecycleProvider SPI impl | AgentRegistry lifecycle bridge | Separate issue |
| Push-based LLM invocation | CognitiveAttentionAccumulator | neocortex#381 |
| Migration of mood/drives to neocortex | Architectural realignment | Broader initiative |

## References

- [decisions.md](decisions.md) — 6 design decisions with review findings
- [2026-09-23-computational-emotion-research.md](2026-09-23-computational-emotion-research.md) — research foundations, datasets, mappings
- neocortex design spec: `docs/specs/issue-345-goal-cognition/2026-09-23-goal-cognition-design.md`
- GoalProposalOrchestrator: `blocks-core/.../social/goal/GoalProposalOrchestrator.java`
- GoalPromptSection: `blocks-core/.../social/prompt/GoalPromptSection.java`
- CognitionCore: `blocks-core/.../social/CognitionCore.java`
- GoalAffectPhase: `neocortex/mindmap-intelligence/.../consolidation/GoalAffectPhase.java`
- GoalPrioritizationPhase: `neocortex/mindmap-intelligence/.../consolidation/GoalPrioritizationPhase.java`
- GoalFormationService: `engine/api/.../spi/routing/GoalFormationService.java`
- [OCC Model Revisited (Steunebrink 2009)](https://people.idsia.ch/~steunebrink/Publications/KI09_OCC_revisited.pdf)
- [ALMA (Gebhard 2005)](https://alma.dfki.de/papers/aamas05.pdf)
- [Scherer CPM (2001)](https://psu.pb.unizin.org/psych425/chapter/component-process-model-cpm/)

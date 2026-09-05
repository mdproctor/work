# Avatar Social Cognition Integration

Wire all 9 avatar-relevant social cognition capabilities into the speech pipeline, giving the avatar personality, mood, drives, memory of users, theory of mind, learned interaction strategies, narrative identity, autonomous goals, and proactive initiation.

## Scope

| # | Capability | Orchestrator | Integration type | Issue |
|---|---|---|---|---|
| 1 | Personality | PersonalityEvolutionOrchestrator | PromptSection | #198 |
| 2 | Mood | MoodOrchestrator | PromptSection | #199 |
| 3 | Drives | DriveOrchestrator | PromptSection | #200 |
| 4 | Proactive speech | InnerLifeOrchestrator | Proactive initiation | #200 |
| 5 | Mental model | MentalModelOrchestrator | PromptSection | #201 |
| 6 | User model | UserModelOrchestrator | PromptSection | #202 |
| 7 | Strategy | StrategyLearningOrchestrator | PromptSection | #203 |
| 8 | Narrative | NarrativeOrchestrator | PromptSection | new |
| 9 | Goals | GoalProposalOrchestrator | PromptSection | new |

**Not in scope:** GroupNarrativeOrchestrator, SocialNormDetector, CollectiveGoalFormation (multi-agent only).

## Architecture

Three layers of change:

1. **speech-api** (SPI layer) — PromptAssembler, AssembledPrompt, ConversationTurn move here. New PromptSection SPI.
2. **blocks** (integration layer) — 8 PromptSection implementations + SocialPromptAssembler compositor + ProactiveSpeechSupport. New compile dep on speech-api.
3. **speech-ws** (endpoint layer) — SpeechWebSocket gains avatar identity resolution. SpeechSession gains proactive tick loop and signal feedback.

### Layer 1: speech-api (SPI)

Move from speech-ws to speech-api (pure Java, no framework dependency):

```java
// Already exists in speech-ws, moves to speech-api
@FunctionalInterface
public interface PromptAssembler {
    AssembledPrompt assemble(String userMessage, List<ConversationTurn> history);
}

public record AssembledPrompt(String systemPrompt, String userPrompt, @Nullable String model) {
    public AssembledPrompt(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, null);
    }
}
```

New types in speech-api:

```java
@FunctionalInterface
public interface PromptSection {
    @Nullable String contribute(String agentId, String tenantId);
}
```

`PromptSection` is deliberately minimal: each implementation reads its orchestrator's cached state and returns a formatted text section (or null when nothing to contribute). No framework annotations — pure Java.

```java
public interface AvatarCognition {
    PromptAssembler wrapAssembler(PromptAssembler base, String agentId, String tenantId);

    default @Nullable String evaluateProactive(String agentId, String tenantId,
                                                String channelContext) {
        return null;
    }

    default void recordInteraction(String agentId, String tenantId,
                                    @Nullable String subjectId,
                                    String userMessage, String response) {}
}
```

`AvatarCognition` is the composition root — the single interface speech-ws uses to access all social cognition capabilities. speech-ws injects `Instance<AvatarCognition>` and checks `isResolvable()`. blocks provides the implementation. This avoids speech-ws needing any dependency on blocks.

All types move to package `io.casehub.blocks.speech` (from `io.casehub.blocks.speech.ws` and `io.casehub.blocks.speech.ws.protocol`). speech-ws retains `DefaultPromptAssembler` (re-imports from speech-api).

### Layer 2: blocks (integration)

blocks gains `speech-api` as a compile dependency. New package: `io.casehub.blocks.agentic.social.prompt`.

#### SocialPromptAssembler

Compositor that wraps a base `PromptAssembler` and appends all `PromptSection` contributions to the system prompt:

```java
public class SocialPromptAssembler implements PromptAssembler {
    private final PromptAssembler delegate;
    private final List<PromptSection> sections;
    private final String agentId;
    private final String tenantId;

    @Override
    public AssembledPrompt assemble(String userMessage, List<ConversationTurn> history) {
        AssembledPrompt base = delegate.assemble(userMessage, history);
        var enriched = new StringBuilder(base.systemPrompt());
        for (var section : sections) {
            String contribution = section.contribute(agentId, tenantId);
            if (contribution != null) {
                enriched.append("\n\n").append(contribution);
            }
        }
        return new AssembledPrompt(enriched.toString(), base.userPrompt(), base.model());
    }
}
```

Not CDI-managed — constructed internally by `SocialAvatarCognition`.

#### SocialAvatarCognition

`@ApplicationScoped` CDI bean implementing `AvatarCognition`. Injects all orchestrators and constructs PromptSections:

```java
@ApplicationScoped
public class SocialAvatarCognition implements AvatarCognition {
    @Inject MoodOrchestrator mood;
    @Inject DriveOrchestrator drives;
    @Inject MentalModelOrchestrator mentalModel;
    @Inject UserModelOrchestrator userModel;
    @Inject StrategyLearningOrchestrator strategy;
    @Inject Instance<NarrativeOrchestrator> narrative;
    @Inject Instance<GoalProposalOrchestrator> goals;
    @Inject Instance<InnerLifeOrchestrator> innerLife;
    @Inject Instance<AgentRegistry> agentRegistry;

    @Override
    public PromptAssembler wrapAssembler(PromptAssembler base, String agentId, String tenantId) {
        var sections = buildSections(agentId, tenantId);
        return new SocialPromptAssembler(base, sections, agentId, tenantId);
    }

    @Override
    public @Nullable String evaluateProactive(String agentId, String tenantId,
                                               String channelContext) {
        // delegates to InnerLifeOrchestrator when available
    }

    @Override
    public void recordInteraction(String agentId, String tenantId,
                                   @Nullable String subjectId,
                                   String userMessage, String response) {
        // dispatches signals to mood, userModel, mentalModel, strategy
    }
}
```

Consumers activate by indexing blocks: `quarkus.index-dependency.casehub-blocks.group-id=io.casehub`.

#### 8 PromptSection implementations

Each reads cached state from its orchestrator. All are plain classes (not CDI beans) — constructed by `SocialAvatarCognition.buildSections()`.

| PromptSection | Reads from | Renders |
|---|---|---|
| `PersonalityPromptSection` | `AgentDescriptor.disposition()` | "Your personality traits: [dominant] with [auxiliary]. You tend to be [trait descriptions]." |
| `MoodPromptSection` | `MoodOrchestrator.currentMood()` | "Current emotional state: pleasure=[P], arousal=[A], dominance=[D]. [Natural language interpretation]." |
| `DrivePromptSection` | `DriveOrchestrator.currentDrives()` | Delegates to `CognitiveObservationSections.motivationalStateSection()` |
| `MentalModelPromptSection` | `MentalModelOrchestrator.activeSnapshots()` | "What you believe about the user: [beliefs]. What you think they want: [desires]. Their likely intentions: [intentions]." |
| `UserModelPromptSection` | `UserModelOrchestrator.currentProfile()` | "User profile: familiarity=[level], relationship stage=[stage], preferences=[prefs], topics of interest=[topics]." |
| `StrategyPromptSection` | `StrategyLearningOrchestrator.currentStrategy()` | "Interaction approach: [effective strategies]. Avoid: [ineffective strategies]." |
| `NarrativePromptSection` | `NarrativeOrchestrator.currentNarrative()` | Delegates to `CognitiveObservationSections.narrativeSection()` |
| `GoalPromptSection` | `GoalProposalOrchestrator.currentProposals()` | "Your current goals: [goal descriptions with priorities]." |

The `PersonalityPromptSection` is unique — it reads from `AgentDescriptor` directly (passed at construction), not from an orchestrator query method.

The `UserModelPromptSection` additionally needs a `subjectId` (the user being spoken to). This comes from SpeechSession's speaker identification or defaults to a fixed ID.

#### ProactiveSpeechSupport

Encapsulates the InnerLifeOrchestrator integration for proactive initiation:

```java
public class ProactiveSpeechSupport {
    private final InnerLifeOrchestrator innerLife;
    private final AgentDescriptor descriptor;

    public @Nullable String evaluateProactive(String channelContext) {
        InnerLifeTick tick = innerLife.tick(descriptor, channelContext);
        return switch (tick) {
            case InnerLifeTick.Initiated i -> i.content();
            case InnerLifeTick.Silent s -> null;
        };
    }
}
```

### Layer 3: speech-ws (endpoint)

#### AvatarConfig changes

```java
@ConfigMapping(prefix = "casehub.avatar")
public interface AvatarConfig {
    @WithDefault("16000")
    int sampleRate();
    @WithDefault("2")
    int maxDestructiveness();
    Optional<String> systemPrompt();
    Optional<String> agentId();
    Optional<String> tenantId();
    @WithDefault("30")
    int proactiveTickIntervalSeconds();
}
```

#### SpeechWebSocket changes

```java
@Inject
Instance<AvatarCognition> avatarCognition;
```

`onOpen()`:
1. Creates `DefaultPromptAssembler` as before (base assembler from config system prompt).
2. If `avatarCognition.isResolvable()` and `agentId`/`tenantId` are configured: wraps the base assembler via `avatarCognition.get().wrapAssembler(base, agentId, tenantId)`.
3. Falls back to `DefaultPromptAssembler` when not configured (backward compatible).

Proactive speech: if `AvatarCognition` is available, starts a periodic virtual thread that calls `avatarCognition.get().evaluateProactive(agentId, tenantId, channelContext)` at `proactiveTickIntervalSeconds` intervals. When content is returned, calls `session.handleText(content)` to generate and speak the response.

#### Signal feedback (SpeechSession)

SpeechSession accepts an optional `AvatarCognition` reference. After each conversation turn:

```java
private void recordInteraction(String userMessage, String response) {
    if (cognition != null) {
        cognition.recordInteraction(agentId, tenantId, subjectId, userMessage, response);
    }
}
```

`subjectId` comes from speaker identification (SpeechSession already tracks identified speakers via `SpeakerRegistry`). Defaults to `"anonymous"` when no speaker is identified.

`SocialAvatarCognition.recordInteraction()` (in blocks) dispatches to:
- `UserModelOrchestrator.record(InteractionSignal.neutral(), agentId, subjectId, tenantId)` — basic interaction tracking
- `MoodOrchestrator.record(MoodSignal, agentId, tenantId)` — neutral mood signal from interaction
- `MentalModelOrchestrator.record(MentalStateSignal.verbal(userMessage), agentId, subjectId, tenantId)` — tracks user's verbal cues
- `StrategyLearningOrchestrator.record(EngagementSignal.turnOutcome(...), agentId, subjectId, tenantId)` — tracks engagement

## Dependency changes

```
speech-api (no new deps — receives types from speech-ws)
    ^              ^
    |  compile     |  compile (existing)
    |              |
blocks         speech-ws
(new dep:      (re-imports from speech-api)
 speech-api)
```

- **speech-api**: gains PromptAssembler, AssembledPrompt, ConversationTurn, PromptSection, AvatarCognition. No new external dependencies.
- **blocks**: new compile dep on speech-api. Provides SocialAvatarCognition implementing AvatarCognition.
- **speech-ws**: re-imports types from speech-api. Injects `Instance<AvatarCognition>` — resolved only when a consumer's classpath includes blocks with Jandex indexing enabled. No direct dependency on blocks.

Consumer activation: add blocks as a compile dep + `quarkus.index-dependency.casehub-blocks.group-id=io.casehub` in `application.properties`.

## Backward compatibility

- speech-ws continues to work without blocks or eidos on the classpath. `DefaultPromptAssembler` is the fallback. No social cognition, no proactive speech.
- Existing consumers of `PromptAssembler` from speech-ws update imports from `io.casehub.blocks.speech.ws` to `io.casehub.blocks.speech`. The interface is unchanged.
- `AssembledPrompt` and `ConversationTurn` move packages similarly.

## Testing

All new code is plain JUnit 5 + Mockito (no Quarkus runtime), following the blocks testing pattern.

- **Per-PromptSection unit tests** — mock orchestrator, verify rendered text format and null-when-empty behavior.
- **SocialPromptAssembler test** — verify composition: base prompt + N sections appended, null sections skipped.
- **ProactiveSpeechSupport test** — mock InnerLifeOrchestrator, verify Initiated → content, Silent → null.
- **SocialSignalRecorder test** — verify correct signal types dispatched per turn.
- **Integration test** — construct full SocialPromptAssembler with all 8 sections, verify the assembled prompt contains all social cognition context.

## Issue mapping

| Task | Issue | What it does |
|---|---|---|
| Move types to speech-api + PromptSection SPI | Foundation | Types move, new SPI, blocks dep |
| PersonalityPromptSection | #198 | Personality traits in prompt |
| MoodPromptSection | #199 | PAD state in prompt |
| DrivePromptSection + ProactiveSpeechSupport | #200 | Drive context + proactive initiation |
| MentalModelPromptSection | #201 | Theory of Mind in prompt |
| UserModelPromptSection | #202 | User memory in prompt |
| StrategyPromptSection | #203 | Learned strategies in prompt |
| NarrativePromptSection | new issue | Self-narrative in prompt |
| GoalPromptSection | new issue | Autonomous goals in prompt |
| SocialSignalRecorder + SpeechWebSocket wiring | Foundation | Feedback loop + endpoint integration |

## References

- PersonalityEvolutionOrchestrator.java — trait evolution API
- MoodOrchestrator.java:60 — currentMood() query
- DriveOrchestrator.java:152 — currentDrives() query
- InnerLifeOrchestrator.java:88-194 — tick() proactive initiation pipeline
- InnerLifeTick.java — Initiated/Silent sealed outcomes
- MentalModelOrchestrator.java:144 — activeSnapshots() query
- UserModelOrchestrator.java:96 — currentProfile() query
- StrategyLearningOrchestrator.java:141 — currentStrategy() query
- NarrativeOrchestrator.java:64 — currentNarrative() query
- GoalProposalOrchestrator.java:126 — currentProposals() query
- CognitiveObservationSections.java — existing rendering for drives, narrative, goals
- PromptAssembler.java, DefaultPromptAssembler.java, AssembledPrompt.java — current speech-ws types
- SpeechWebSocket.java — endpoint wiring
- SpeechSession.java — session lifecycle
- AvatarConfig.java — configuration interface
- capability-ownership.md — platform capability registry
- boundary-rules.md — cross-repo boundary rules
- GE-20260729-172d18 — evolve existing SPIs, don't create parallel ones
- GE-20260615-c234fc — @DefaultBean requires quarkus-arc on classpath
- decisions.md — D1-D6 decision log

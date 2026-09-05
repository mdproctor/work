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

1. **speech-api** (SPI layer) — SpeechPromptAssembler (renamed from PromptAssembler), AssembledPrompt, ConversationTurn move here. New SpeechTurnContext + PromptSection SPI.
2. **blocks** (integration layer) — 8 PromptSection implementations + SocialPromptAssembler compositor + ProactiveSpeechSupport. New compile dep on speech-api.
3. **speech-ws** (endpoint layer) — SpeechWebSocket gains avatar identity resolution. SpeechSession gains proactive tick loop and signal feedback.

### Layer 1: speech-api (SPI)

Move from speech-ws to speech-api (pure Java, no framework dependency):

```java
// Already exists in speech-ws as PromptAssembler, moves to speech-api and renamed
@FunctionalInterface
public interface SpeechPromptAssembler {
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
public record PromptContext(String agentId, String tenantId, @Nullable String subjectId) {}

@FunctionalInterface
public interface PromptSection {
    @Nullable String contribute(PromptContext context);
}
```

`PromptSection` is deliberately minimal: each implementation reads its orchestrator's cached state and returns a formatted text section (or null when nothing to contribute). `PromptContext` carries per-turn context — `subjectId` identifies the current speaker (null when no speaker identification is active). No framework annotations — pure Java.

```java
public interface AvatarCognition {
    SpeechPromptAssembler wrapAssembler(SpeechPromptAssembler base, String agentId, String tenantId,
                                   Supplier<String> subjectIdSupplier);

    default void initialize(String agentId, String tenantId) {}

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

`initialize()` is called at session open to hydrate orchestrator state before the first user turn. `wrapAssembler()` takes a `Supplier<String>` for the current speaker's subjectId, which is resolved per-turn by SpeechSession and threaded through `PromptContext` to sections that need it.

All types move to package `io.casehub.blocks.speech` (from `io.casehub.blocks.speech.ws` and `io.casehub.blocks.speech.ws.protocol`). speech-ws retains `DefaultPromptAssembler` (re-imports from speech-api).

### Layer 2: blocks (integration)

blocks gains `speech-api` as a compile dependency. New package: `io.casehub.blocks.agentic.social.prompt`.

#### SocialPromptAssembler

Compositor that wraps a base `SpeechPromptAssembler` and appends all `PromptSection` contributions to the system prompt:

```java
public class SocialPromptAssembler implements SpeechPromptAssembler {
    private final SpeechPromptAssembler delegate;
    private final List<PromptSection> sections;
    private final String agentId;
    private final String tenantId;
    private final Supplier<String> subjectIdSupplier;

    @Override
    public AssembledPrompt assemble(String userMessage, List<ConversationTurn> history) {
        AssembledPrompt base = delegate.assemble(userMessage, history);
        var enriched = new StringBuilder(base.systemPrompt());
        var context = new PromptContext(agentId, tenantId, subjectIdSupplier.get());
        for (var section : sections) {
            try {
                String contribution = section.contribute(context);
                if (contribution != null) {
                    enriched.append("\n\n").append(contribution);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        "PromptSection failed: " + section.getClass().getSimpleName(), e);
            }
        }
        return new AssembledPrompt(enriched.toString(), base.userPrompt(), base.model());
    }
}
```

A failing `PromptSection` is logged and skipped — the conversation continues with the remaining sections rather than failing entirely.

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
    public SpeechPromptAssembler wrapAssembler(SpeechPromptAssembler base, String agentId, String tenantId,
                                          Supplier<String> subjectIdSupplier) {
        var sections = buildSections(agentId, tenantId);
        return new SocialPromptAssembler(base, sections, agentId, tenantId, subjectIdSupplier);
    }

    @Override
    public void initialize(String agentId, String tenantId) {
        // Hydrate drive state so DrivePromptSection contributes from the first turn.
        // DriveOrchestrator.tick() is computational (no LLM call).
        if (agentRegistry.isResolvable()) {
            agentRegistry.get().findById(agentId, tenantId)
                    .ifPresent(desc -> drives.tick(agentId, tenantId, desc));
        }
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
        // Each orchestrator is isolated — a failure in one does not prevent others from recording.
        // Mood is agent-scoped (no subjectId). The others are subject-scoped and require a subjectId.
        record(() -> mood.record(
                new MoodSignal.InteractionAppraisal(0.0, 0.0, 0.0, null),
                agentId, tenantId));
        if (subjectId != null) {
            record(() -> userModel.record(
                    new InteractionSignal.CustomSignal(userMessage, QualitySignal.NEUTRAL),
                    agentId, subjectId, tenantId));
            record(() -> mentalModel.record(
                    new MentalStateSignal.VerbalCue(userMessage, CueType.BELIEF_STATEMENT),
                    agentId, subjectId, tenantId));
            record(() -> strategy.record(
                    new EngagementSignal.TurnOutcome(
                            EngagementEvent.ofTurn(agentId, subjectId, tenantId, userMessage, response),
                            Map.of(), response),
                    agentId, subjectId, tenantId));
        }
    }

    private void record(Runnable action) {
        try { action.run(); }
        catch (Exception e) { LOG.log(Level.WARNING, "Signal recording failed", e); }
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
| `DrivePromptSection` | `DriveOrchestrator.currentDrives()` | Calls `CognitiveObservationSections.motivationalStateSection()` → renders `ObservationSection` via `AffordanceRenderer.renderObservation()` |
| `MentalModelPromptSection` | `MentalModelOrchestrator.activeSnapshots()` | "What you believe about the user: [beliefs]. What you think they want: [desires]. Their likely intentions: [intentions]." |
| `UserModelPromptSection` | `UserModelOrchestrator.currentProfile()` | "User profile: familiarity=[level], relationship stage=[stage], preferences=[prefs], topics of interest=[topics]." |
| `StrategyPromptSection` | `StrategyLearningOrchestrator.currentStrategy()` | "Interaction approach: [effective strategies]. Avoid: [ineffective strategies]." |
| `NarrativePromptSection` | `NarrativeOrchestrator.currentNarrative()` | Calls `CognitiveObservationSections.narrativeSection()` → renders `ObservationSection` via `AffordanceRenderer.renderObservation()` |
| `GoalPromptSection` | `GoalProposalOrchestrator.currentProposals()` | Renders directly from `DriveGoalProposal`: "Your current goals: \n- [PRIORITY] description (drive: axis, intensity)" |

The `PersonalityPromptSection` is unique — it reads from `AgentDescriptor` directly (passed at construction), not from an orchestrator query method. Personality is session-static by design: `PersonalityEvolutionOrchestrator` evolves personality across sessions (via `DispositionProfileStore.update()`), not within a single conversation. The next session open picks up the evolved descriptor from `AgentRegistry`.

The `UserModelPromptSection` needs a `subjectId` (the user being spoken to), obtained from `PromptContext.subjectId()`. This is resolved per-turn by the `subjectIdSupplier` passed to `SocialPromptAssembler`. SpeechSession resolves the current speaker via `SpeakerRegistry` and defaults to `"anonymous"` when no speaker is identified.

`DrivePromptSection` and `NarrativePromptSection` both delegate to `CognitiveObservationSections` methods that return `ObservationSection` (a sealed interface — `EntityGroup`, `TextBlock`, `ItemList`). These sections render the result to `String` via `new AffordanceRenderer().renderObservation(List.of(section))`, reusing the existing rendering pipeline rather than inventing a separate text-formatting path.

`GoalPromptSection` does NOT delegate to `CognitiveObservationSections.goalsSection()` — that method takes `List<AgentGoal>` (eidos-api), while `GoalProposalOrchestrator.currentProposals()` returns `Optional<List<DriveGoalProposal>>` (blocks). These are different types: `AgentGoal` represents adopted goals in the agent's profile, while `DriveGoalProposal` represents drive-generated goal proposals. GoalPromptSection renders `DriveGoalProposal` directly, formatting each proposal with its priority, description, and driving axis.

#### ProactiveSpeechSupport

Encapsulates the InnerLifeOrchestrator integration for proactive initiation:

```java
public class ProactiveSpeechSupport {
    private static final System.Logger LOG = System.getLogger("proactive-speech");
    private final InnerLifeOrchestrator innerLife;
    private final AgentDescriptor descriptor;

    public @Nullable String evaluateProactive(String channelContext) {
        InnerLifeTick tick = innerLife.tick(descriptor, channelContext);
        return switch (tick) {
            case InnerLifeTick.Initiated i -> {
                LOG.log(Level.DEBUG, "Proactive initiation: score={0}", i.motivationScore());
                // channelHint is intentionally dropped — speech-ws is a single-channel
                // WebSocket. channelHint is relevant in multi-channel contexts (Slack,
                // email, in-game chat) where the orchestrator hints at the target channel.
                yield i.content();
            }
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
2. If `avatarCognition.isResolvable()` and `agentId`/`tenantId` are configured:
   - Creates an `AtomicReference<String>("anonymous")` as the subjectId holder. SpeechSession updates this reference when speaker identification succeeds.
   - Calls `avatarCognition.get().initialize(agentId, tenantId)` to hydrate orchestrator state (drives).
   - Wraps the base assembler via `avatarCognition.get().wrapAssembler(base, agentId, tenantId, subjectIdRef::get)`.
   - If `agentId`/`tenantId` are configured but `AvatarCognition` is not resolvable (blocks not on classpath), logs a warning: social cognition config is present but the implementation is missing.
3. Falls back to `DefaultPromptAssembler` when not configured (backward compatible).

Proactive speech: if `AvatarCognition` is available, starts a proactive tick loop on a virtual thread with the following contract:

1. **Fixed-delay loop** — waits `proactiveTickIntervalSeconds` after each tick *completes* before starting the next. This prevents tick queuing when a tick takes longer than the interval (e.g., slow LLM).
2. **Turn-busy gating** — an `AtomicBoolean turnInProgress` is set `true` at the start of `handleText()`/`handleStop()` and cleared when the turn completes. The proactive tick checks this flag and skips the tick if a user turn is in progress. This prevents concurrent `handleText()` calls on SpeechSession, which is not thread-safe (`history` is a plain `ArrayList`).
3. **Thread lifecycle** — the proactive tick thread reference is stored in SpeechSession. `close()` sets an `isClosed` flag and interrupts the thread. The tick loop checks `isClosed` before each iteration and before calling `evaluateProactive()`.
4. **Tick timeout** — `ProactiveSpeechSupport.evaluateProactive()` wraps the `InnerLifeOrchestrator.tick()` call in a try-catch. If the underlying LLM call hangs, the tick thread is interruptible via the close() path. The fixed-delay loop prevents queuing regardless.

When `evaluateProactive()` returns content, calls `session.handleText(content)` to generate and speak the response.

**channelContext definition:** The `channelContext` string passed to `evaluateProactive()` is assembled by SpeechWebSocket from available session state:

```
Channel: WebSocket speech session
Turn count: {history.size()}
Last user message: {last turn's user text, or "none"}
Time since last interaction: {duration since last turn}
Speaker: {identified speaker name, or "anonymous"}
```

This matches how `InnerLifeOrchestrator.assemblePrompt()` uses channelContext — it appears under "Available channels and context:" in the LLM prompt. The content is intentionally simple (no conversation summary, no RAG retrieval) because the inner life pipeline already has its own observation buffer and reflection sources.

#### Signal feedback (SpeechSession)

SpeechSession gains a `withAvatarCognition(AvatarCognition cognition, String agentId, String tenantId)` method (follows the existing `withSpeakerServices()` pattern — no constructor change, no new overload). After each conversation turn:

```java
private void recordInteraction(String userMessage, String response) {
    if (cognition != null) {
        cognition.recordInteraction(agentId, tenantId, subjectId, userMessage, response);
    }
}
```

`subjectId` comes from speaker identification (SpeechSession already tracks identified speakers via `SpeakerRegistry`). Defaults to `"anonymous"` when no speaker is identified.

`SocialAvatarCognition.recordInteraction()` (in blocks) dispatches to each orchestrator with per-call exception isolation (see `SocialAvatarCognition` code above). Concrete signal construction:

| Orchestrator | Signal construction | Scope |
|---|---|---|
| `MoodOrchestrator.record()` | `new MoodSignal.InteractionAppraisal(0.0, 0.0, 0.0, null)` — zero PAD delta (neutral interaction, no emotional shift) | agent-scoped |
| `UserModelOrchestrator.record()` | `new InteractionSignal.CustomSignal(userMessage, QualitySignal.NEUTRAL)` — records interaction text for profile synthesis | subject-scoped |
| `MentalModelOrchestrator.record()` | `new MentalStateSignal.VerbalCue(userMessage, CueType.BELIEF_STATEMENT)` — feeds heuristic belief extraction | subject-scoped |
| `StrategyLearningOrchestrator.record()` | `new EngagementSignal.TurnOutcome(EngagementEvent.ofTurn(...), Map.of(), response)` — see note below | subject-scoped |
| `InnerLifeOrchestrator.observeResponse()` | `observeResponse(descriptor)` — resets `consecutiveInitiationsWithoutResponse` counter | agent-scoped |

Subject-scoped signals are only dispatched when `subjectId != null` (speaker identified).

**EngagementEvent.ofTurn() factory:** `EngagementEvent` has 15 constructor parameters. The spec proposes a static factory `EngagementEvent.ofTurn(agentId, subjectId, tenantId, userMessage, response)` that supplies sensible defaults: `turnId` = UUID, `timestamp` = now, `responded` = true, `responseLength` = response length, remaining fields null. This factory is a new addition to the EngagementEvent API in neocortex-memory-api — it does not exist today.

All `record()` calls are in-memory operations (ConcurrentHashMap writes + counter increments). They are synchronous by design — the microsecond-level latency does not justify the complexity of async dispatch.

**Orchestrators that do NOT receive conversation signals, by design:**
- `PersonalityEvolutionOrchestrator` — `record(E, AgentDescriptor)` dispatches to typed `TraitPressureSource<E>` implementations. Conversation turns are not a registered pressure source event type. Personality evolves across sessions via domain events (MoodSignal, engagement metrics), not within a single conversation turn.
- `NarrativeOrchestrator` — tick-driven only (no `record()` method). Narrative state is synthesised from episodes on periodic ticks, not from individual conversation turns.
- `GoalProposalOrchestrator` — tick-driven only (no `record()` method). Goal proposals derive from drive state, not conversation signals.
- `DriveOrchestrator` — tick-driven. Its `tick()` is called internally by `InnerLifeOrchestrator.doTick()` during the proactive initiation pipeline. Drive levels change based on temporal decay and drive-specific sources, not conversation events.

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

- **speech-api**: gains SpeechPromptAssembler (renamed from PromptAssembler), AssembledPrompt, ConversationTurn, PromptContext, PromptSection, AvatarCognition. Adds jspecify (1.0.0) as a compile dependency — needed for `@Nullable` annotations on moved types (`AssembledPrompt.model()`, `PromptContext.subjectId()`). jspecify is a compile-time-only annotations jar with zero transitive dependencies, preserving the module's lightweight character. Module charter expands from "STT/TTS SPIs" to "Speech pipeline SPIs" — prompt assembly is part of the speech pipeline, and the social cognition integration points (`PromptSection`, `AvatarCognition`) define how capabilities plug into that pipeline.
- **blocks**: new compile dep on speech-api. Provides SocialAvatarCognition implementing AvatarCognition.
- **speech-ws**: re-imports types from speech-api. Injects `Instance<AvatarCognition>` — resolved only when a consumer's classpath includes blocks with Jandex indexing enabled. No direct dependency on blocks.

Consumer activation: add blocks as a compile dep + `quarkus.index-dependency.casehub-blocks.group-id=io.casehub` in `application.properties`.

## Backward compatibility

- speech-ws continues to work without blocks or eidos on the classpath. `DefaultPromptAssembler` is the fallback. No social cognition, no proactive speech.
- Existing consumers of `PromptAssembler` from speech-ws update imports from `io.casehub.blocks.speech.ws.PromptAssembler` to `io.casehub.blocks.speech.SpeechPromptAssembler`. The rename disambiguates from the unrelated `io.casehub.blocks.conversation.orchestration.PromptAssembler` (which stays as-is).
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
- SpeechPromptAssembler.java (was PromptAssembler.java), DefaultPromptAssembler.java, AssembledPrompt.java — current speech-ws types
- SpeechWebSocket.java — endpoint wiring
- SpeechSession.java — session lifecycle
- AvatarConfig.java — configuration interface
- capability-ownership.md — platform capability registry
- boundary-rules.md — cross-repo boundary rules
- GE-20260729-172d18 — evolve existing SPIs, don't create parallel ones
- GE-20260615-c234fc — @DefaultBean requires quarkus-arc on classpath
- decisions.md — D1-D6 decision log

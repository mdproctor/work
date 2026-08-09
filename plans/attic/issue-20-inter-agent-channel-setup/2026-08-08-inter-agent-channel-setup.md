# Inter-Agent Channel Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #20 — inter-agent channel setup during execution
**Issue group:** #20

**Goal:** Enable pattern builders (Debate, Voting, Parallel) to run
collaborative agent patterns over qhorus channels with automatic
lifecycle management.

**Architecture:** New `agentic.channel` sub-package with value types
(ChannelConfig, ChannelBinding, ConversationConfig), a sealed
ChannelExecutionStrategy (Conversation, FanIn, Barrier), a
ChannelBackend (ExecutionBackend impl), and ChannelTeardownListener.
Pattern builders gain `.overChannel()` methods. ExecutionModel gains
an optional backend field for ComposedAgent bridging.

**Tech Stack:** Java 21, Mutiny, qhorus-api (ChannelManager,
MessageDispatcher, ChannelCreateRequest, ChannelSemantic), blocks
conversation orchestration (ConversationOrchestrator, TurnPolicy,
PromptAssembler, AgentParticipant)

## Global Constraints

- Pre-release: breaking changes are fine — no backward compat shims
- No CDI in blocks — consumers provide runtime instances
- No Quarkus test runtime — plain JUnit 5, Mockito, AssertJ
- IntelliJ MCP mandatory for all .java edits
- Package: `io.casehub.blocks.agentic.channel`

---

### Task 1: ExecutionModel backend field + builder + invoker

Add `@Nullable ExecutionBackend<T> backend` to ExecutionModel.
Update AbstractPatternBuilder.build() and AgentInvoker.invokeComposed().

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/model/ExecutionModel.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/AbstractPatternBuilder.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/model/AgentInvoker.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/HtnBuilder.java` (construction site)
- Test: `src/test/java/io/casehub/blocks/agentic/model/ExecutionModelTest.java`
- Test: `src/test/java/io/casehub/blocks/agentic/model/AgentInvokerTest.java`

**Interfaces:**
- Consumes: existing `ExecutionBackend<T>`, `ExecutionModel`, `AgentInvoker`
- Produces: `ExecutionModel.backend()` accessor, updated `build()`, updated `invokeComposed()`

- [ ] **Step 1: Write failing test — ExecutionModel backend field**

```java
// In ExecutionModelTest, add:
@Test
void backendDefaultsToNull() {
    var model = new ExecutionModel<>(
        mock(RoutingStrategy.class), mock(DecompositionStrategy.class),
        mock(ActivationRule.class), mock(AggregationStrategy.class),
        mock(TerminationCondition.class), List::of,
        FailurePolicy.defaults(), List.of(), "test", null);
    assertThat(model.backend()).isNull();
}

@Test
void backendCarriedOnModel() {
    ExecutionBackend<String> backend = (m, ctx) -> Uni.createFrom().item(new ExecutionResult.Completed("ok"));
    var model = new ExecutionModel<>(
        mock(RoutingStrategy.class), mock(DecompositionStrategy.class),
        mock(ActivationRule.class), mock(AggregationStrategy.class),
        mock(TerminationCondition.class), List::of,
        FailurePolicy.defaults(), List.of(), "test", null, backend);
    assertThat(model.backend()).isSameAs(backend);
}
```

- [ ] **Step 2: Run test — verify fail**

Run: `mvn test -pl . -Dtest=ExecutionModelTest -DfailIfNoTests=false`
Expected: compilation error — no 11-arg constructor, no `backend()` accessor

- [ ] **Step 3: Add backend field to ExecutionModel**

Add `@Nullable ExecutionBackend<T> backend` as 11th field in the record header.
Add 10-arg secondary constructor (matching old canonical signature) that passes null for backend.
Update 9-arg secondary constructor to pass null for both patternType and backend.

```java
public record ExecutionModel<T>(
        RoutingStrategy<T> routing,
        DecompositionStrategy<T> decomposition,
        ActivationRule<T> activation,
        AggregationStrategy<T> aggregation,
        TerminationCondition<T> termination,
        Supplier<List<RoutingCandidate>> candidateSupplier,
        FailurePolicy failurePolicy,
        List<ExecutionEventListener> listeners,
        String task,
        PatternType patternType,
        @Nullable ExecutionBackend<T> backend
) {
    // Compact constructor: unchanged validation (backend is nullable)

    // Old 10-arg signature — backward compat
    public ExecutionModel(RoutingStrategy<T> routing, DecompositionStrategy<T> decomposition,
                          ActivationRule<T> activation, AggregationStrategy<T> aggregation,
                          TerminationCondition<T> termination,
                          Supplier<List<RoutingCandidate>> candidateSupplier,
                          FailurePolicy failurePolicy, List<ExecutionEventListener> listeners,
                          String task, PatternType patternType) {
        this(routing, decomposition, activation, aggregation, termination,
             candidateSupplier, failurePolicy, listeners, task, patternType, null);
    }

    // Old 9-arg signature
    public ExecutionModel(RoutingStrategy<T> routing, DecompositionStrategy<T> decomposition,
                          ActivationRule<T> activation, AggregationStrategy<T> aggregation,
                          TerminationCondition<T> termination,
                          Supplier<List<RoutingCandidate>> candidateSupplier,
                          FailurePolicy failurePolicy, List<ExecutionEventListener> listeners,
                          String task) {
        this(routing, decomposition, activation, aggregation, termination,
             candidateSupplier, failurePolicy, listeners, task, null, null);
    }
}
```

- [ ] **Step 4: Run test — verify pass**

Run: `mvn test -pl . -Dtest=ExecutionModelTest -DfailIfNoTests=false`
Expected: PASS. Existing callers still compile via 10-arg secondary constructor.

- [ ] **Step 5: Update AbstractPatternBuilder.build() to pass backend**

```java
public ExecutionModel<T> build() {
    return new ExecutionModel<>(routing, decomposition, activation,
                                aggregation, termination, candidateSupplier,
                                failurePolicy, listeners, task, patternType,
                                backend);
}
```

- [ ] **Step 6: Update HtnBuilder.execute() construction site**

HtnBuilder constructs ExecutionModel inline in execute() — add null for backend:
```java
var localModel = new ExecutionModel<>(
    routing, decomposition, activation, aggregation, termination,
    () -> dynamicCandidates, failurePolicy, listeners,
    task, patternType, null);
```

Or use the 10-arg secondary — either works. Check which is cleaner.

- [ ] **Step 7: Write failing test — AgentInvoker backend bridging**

```java
// In AgentInvokerTest, add:
@Test
void composedAgentUsesBackendWhenPresent() {
    var backendResult = new ExecutionResult.Completed("from-backend");
    ExecutionBackend<Object> backend = (m, ctx) ->
        Uni.createFrom().item(backendResult);
    var model = new ExecutionModel<>(
        mock(RoutingStrategy.class), mock(DecompositionStrategy.class),
        mock(ActivationRule.class), mock(AggregationStrategy.class),
        mock(TerminationCondition.class), List::of,
        FailurePolicy.defaults(), List.of(), "test", null, backend);
    var composed = AgentRef.composed(model);
    AgentInvoker<String> invoker = AgentInvoker.defaultInvoker();
    var result = invoker.invoke(composed, "input").await().indefinitely();
    assertThat(result.status()).isEqualTo(AgentResult.AgentResultStatus.SUCCESS);
    assertThat(result.output()).isEqualTo("from-backend");
}
```

- [ ] **Step 8: Run test — verify fail**

Run: `mvn test -pl . -Dtest=AgentInvokerTest#composedAgentUsesBackendWhenPresent`
Expected: FAIL — invokeComposed() ignores backend, creates OrchestratedDriver

- [ ] **Step 9: Update AgentInvoker.invokeComposed()**

```java
private static <T> AgentResult invokeComposed(AgentRef agent,
                                              AgentRef.ComposedAgent composed,
                                              T state, Instant start) {
    var model = (ExecutionModel<Object>) (ExecutionModel<?>) composed.model();

    Uni<ExecutionResult> execution;
    if (model.backend() != null) {
        execution = model.backend().execute(model, state);
    } else {
        AgentInvoker<Object> nested = defaultInvoker();
        execution = new OrchestratedDriver<>(nested).execute(model, state);
    }

    var executionResult = execution.await().indefinitely();
    var elapsed = Duration.between(start, Instant.now());
    return switch (executionResult) {
        case ExecutionResult.Completed c -> new AgentResult(agent, c.result(), elapsed,
                AgentResult.AgentResultStatus.SUCCESS);
        case ExecutionResult.Failed f -> new AgentResult(agent, f.reason(), elapsed,
                AgentResult.AgentResultStatus.FAILURE);
        case ExecutionResult.Escalated e -> new AgentResult(agent, "Escalated: " + e.reason(),
                elapsed, AgentResult.AgentResultStatus.FAILURE);
        case ExecutionResult.Cancelled ignored -> new AgentResult(agent, "Cancelled", elapsed,
                AgentResult.AgentResultStatus.FAILURE);
    };
}
```

- [ ] **Step 10: Run all tests — verify green**

Run: `mvn test -pl .`
Expected: all pass (existing callers unchanged via secondary constructor)

- [ ] **Step 11: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add \
  src/main/java/io/casehub/blocks/agentic/model/ExecutionModel.java \
  src/main/java/io/casehub/blocks/agentic/pattern/AbstractPatternBuilder.java \
  src/main/java/io/casehub/blocks/agentic/model/AgentInvoker.java \
  src/main/java/io/casehub/blocks/agentic/pattern/HtnBuilder.java \
  src/test/java/io/casehub/blocks/agentic/model/ExecutionModelTest.java \
  src/test/java/io/casehub/blocks/agentic/model/AgentInvokerTest.java
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#20): add backend field to ExecutionModel + ComposedAgent bridging"
```

---

### Task 2: Value types — ChannelConfig, ChannelBinding, ConversationConfig

Create the three value records in the new `agentic.channel` package.

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/channel/ChannelConfig.java`
- Create: `src/main/java/io/casehub/blocks/agentic/channel/ChannelBinding.java`
- Create: `src/main/java/io/casehub/blocks/agentic/channel/ConversationConfig.java`
- Test: `src/test/java/io/casehub/blocks/agentic/channel/ChannelConfigTest.java`
- Test: `src/test/java/io/casehub/blocks/agentic/channel/ConversationConfigTest.java`

**Interfaces:**
- Consumes: `ChannelManager`, `MessageDispatcher`, `ChannelSemantic` (from qhorus-api), `PromptAssembler`, `ResponseMessageBuilder`, `AgentParticipant`, `TurnPolicy`, `ConversationProjection` (from conversation.orchestration)
- Produces: `ChannelConfig`, `ChannelBinding`, `ConversationConfig<T>`

- [ ] **Step 1: Write failing tests for ChannelConfig**

```java
package io.casehub.blocks.agentic.channel;

class ChannelConfigTest {
    @Test
    void rejectsNullChannelManager() {
        assertThatThrownBy(() -> new ChannelConfig(null, mock(MessageDispatcher.class),
                ChannelSemantic.APPEND, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSemantic() {
        assertThatThrownBy(() -> new ChannelConfig(mock(ChannelManager.class),
                mock(MessageDispatcher.class), null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void protocolsDefaultToEmptyList() {
        var config = ChannelConfig.of(mock(ChannelManager.class),
                mock(MessageDispatcher.class), ChannelSemantic.APPEND);
        assertThat(config.protocols()).isEmpty();
    }

    @Test
    void protocolsDefensivelyCopied() {
        var protocols = new ArrayList<>(List.of("proto-1"));
        var config = new ChannelConfig(mock(ChannelManager.class),
                mock(MessageDispatcher.class), ChannelSemantic.APPEND, protocols);
        protocols.add("proto-2");
        assertThat(config.protocols()).containsExactly("proto-1");
    }
}
```

- [ ] **Step 2: Run test — verify fail (class doesn't exist)**

- [ ] **Step 3: Implement ChannelConfig**

```java
package io.casehub.blocks.agentic.channel;

import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageDispatcher;
import java.util.List;
import java.util.Objects;

public record ChannelConfig(
    ChannelManager channelManager,
    MessageDispatcher messageDispatcher,
    ChannelSemantic semantic,
    List<String> protocols
) {
    public ChannelConfig {
        Objects.requireNonNull(channelManager, "channelManager");
        Objects.requireNonNull(messageDispatcher, "messageDispatcher");
        Objects.requireNonNull(semantic, "semantic");
        protocols = protocols != null ? List.copyOf(protocols) : List.of();
    }

    public static ChannelConfig of(ChannelManager channelManager,
                                    MessageDispatcher messageDispatcher,
                                    ChannelSemantic semantic) {
        return new ChannelConfig(channelManager, messageDispatcher, semantic, null);
    }
}
```

- [ ] **Step 4: Run test — verify pass**

- [ ] **Step 5: Write failing tests for ConversationConfig**

```java
package io.casehub.blocks.agentic.channel;

class ConversationConfigTest {
    @Test
    void rejectsNullPromptAssembler() {
        assertThatThrownBy(() -> new ConversationConfig<String>(
                null, mock(ResponseMessageBuilder.class), s -> mock(MessageView.class),
                null, null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullTriggerMapper() {
        assertThatThrownBy(() -> new ConversationConfig<String>(
                mock(PromptAssembler.class), mock(ResponseMessageBuilder.class),
                null, null, null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void optionalFieldsAcceptNull() {
        var config = new ConversationConfig<String>(
                mock(PromptAssembler.class), mock(ResponseMessageBuilder.class),
                s -> mock(MessageView.class), null, null, null, null, null);
        assertThat(config.projectionFactory()).isNull();
        assertThat(config.conversationInvoker()).isNull();
        assertThat(config.participantMapper()).isNull();
        assertThat(config.turnPolicyOverride()).isNull();
        assertThat(config.terminationOverride()).isNull();
    }
}
```

- [ ] **Step 6: Implement ConversationConfig and ChannelBinding**

ConversationConfig: record with required promptAssembler, responseBuilder, triggerMapper and nullable optional fields.

ChannelBinding: `record ChannelBinding(UUID channelId, ChannelSemantic semantic) {}`

- [ ] **Step 7: Run all tests — verify pass**

Run: `mvn test -pl . -Dtest="ChannelConfigTest,ConversationConfigTest"`

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#20): add ChannelConfig, ChannelBinding, ConversationConfig value types"
```

---

### Task 3: ChannelTeardownListener

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/channel/ChannelTeardownListener.java`
- Test: `src/test/java/io/casehub/blocks/agentic/channel/ChannelTeardownListenerTest.java`

**Interfaces:**
- Consumes: `ChannelBinding`, `ChannelManager`, `ExecutionEventListener`
- Produces: `ChannelTeardownListener` with `setBinding()`, `binding()`, `onExecutionComplete()`

- [ ] **Step 1: Write failing tests**

```java
class ChannelTeardownListenerTest {
    @Test
    void deletesChannelOnComplete() {
        var manager = mock(ChannelManager.class);
        var listener = new ChannelTeardownListener(manager);
        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.APPEND);
        listener.setBinding(binding);

        listener.onExecutionComplete(new ExecutionResult.Completed("done"),
                Duration.ofSeconds(1), 1);

        verify(manager).delete(binding.channelId(), true);
    }

    @Test
    void logsWarningWhenNoBinding() {
        var manager = mock(ChannelManager.class);
        var listener = new ChannelTeardownListener(manager);

        listener.onExecutionComplete(new ExecutionResult.Completed("done"),
                Duration.ofSeconds(1), 1);

        verifyNoInteractions(manager);
    }

    @Test
    void swallowsDeleteException() {
        var manager = mock(ChannelManager.class);
        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.APPEND);
        when(manager.delete(any(), anyBoolean())).thenThrow(new RuntimeException("gone"));
        var listener = new ChannelTeardownListener(manager);
        listener.setBinding(binding);

        assertThatCode(() -> listener.onExecutionComplete(
                new ExecutionResult.Failed("err", null), Duration.ofSeconds(1), 1))
            .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run test — verify fail**
- [ ] **Step 3: Implement ChannelTeardownListener** (per spec §ChannelTeardownListener)
- [ ] **Step 4: Run test — verify pass**
- [ ] **Step 5: Commit**

---

### Task 4: ChannelExecutionStrategy sealed interface + FanIn + Barrier

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/channel/ChannelExecutionStrategy.java`
- Test: `src/test/java/io/casehub/blocks/agentic/channel/FanInStrategyTest.java`
- Test: `src/test/java/io/casehub/blocks/agentic/channel/BarrierStrategyTest.java`

**Interfaces:**
- Consumes: `ChannelBinding`, `ExecutionModel<T>`, `MessageDispatcher`, `AgentInvoker<T>`, `AgentResult`
- Produces: `ChannelExecutionStrategy<T>` sealed interface, `FanIn<T>`, `Barrier<T>` record variants

- [ ] **Step 1: Write failing test for FanIn**

```java
class FanInStrategyTest {
    @Test
    void invokesAllAgentsAndDispatchesResults() {
        var dispatcher = mock(MessageDispatcher.class);
        var invoker = AgentInvoker.<String>defaultInvoker();
        var agent1 = AgentRef.external("voter-1", ctx ->
            CompletableFuture.completedFuture(new AgentResult(null, "yes", Duration.ZERO,
                AgentResult.AgentResultStatus.SUCCESS)));
        var agent2 = AgentRef.external("voter-2", ctx ->
            CompletableFuture.completedFuture(new AgentResult(null, "no", Duration.ZERO,
                AgentResult.AgentResultStatus.SUCCESS)));

        Function<AgentResult, MessageDispatch.Builder> resultMapper = result ->
            MessageDispatch.builder().content((String) result.output())
                .type(MessageType.MESSAGE).actorType(ActorType.AGENT);

        var strategy = new ChannelExecutionStrategy.FanIn<String>(invoker, resultMapper);
        var binding = new ChannelBinding(UUID.randomUUID(), ChannelSemantic.COLLECT);

        var model = buildModel(agent1, agent2);
        var execResult = strategy.run(binding, model, "vote please", dispatcher)
                .await().indefinitely();

        assertThat(execResult).isInstanceOf(ExecutionResult.Completed.class);
        verify(dispatcher, times(2)).dispatch(any(MessageDispatch.class));
    }
}
```

- [ ] **Step 2: Run test — verify fail**
- [ ] **Step 3: Implement sealed interface + FanIn + Barrier**

FanIn.run():
1. Get candidates from model.candidateSupplier().get()
2. Invoke each agent via agentInvoker.invoke(ref, initialContext)
3. Map each AgentResult through resultMapper, inject channelId and sender
4. Dispatch via dispatcher
5. Return ExecutionResult.Completed with collected results

Barrier.run(): same as FanIn — the BARRIER semantic is enforced by the channel infrastructure (qhorus), not by the strategy. The strategy's role is dispatch + collect.

- [ ] **Step 4: Write test for Barrier**
- [ ] **Step 5: Run all tests — verify pass**
- [ ] **Step 6: Commit**

---

### Task 5: ConversationChannelAdapter + Conversation strategy

The most complex task. Wires ConversationOrchestrator for APPEND channels.

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/channel/ConversationChannelAdapter.java`
- Add Conversation variant to: `src/main/java/io/casehub/blocks/agentic/channel/ChannelExecutionStrategy.java`
- Create: `src/main/java/io/casehub/blocks/agentic/channel/DefaultConversationProjection.java`
- Test: `src/test/java/io/casehub/blocks/agentic/channel/ConversationChannelAdapterTest.java`

**Interfaces:**
- Consumes: `ConversationConfig<T>`, `ChannelBinding`, `ExecutionModel<T>`, `ConversationOrchestrator`, `TurnPolicy`, `PromptAssembler`, `ResponseMessageBuilder`, `AgentParticipant`, `PartitionedObservationService`, `ConversationProjection`
- Produces: `ConversationChannelAdapter<T>`, `Conversation<T>` strategy variant, `DefaultConversationProjection`

- [ ] **Step 1: Create DefaultConversationProjection**

ConversationProjection is abstract. Need a concrete default that handles basic message accumulation.

```java
package io.casehub.blocks.agentic.channel;

class DefaultConversationProjection extends ConversationProjection {
    // Override the three hook methods with no-op defaults
    // This accumulates messages without domain-specific classification
}
```

Read ConversationProjection abstract methods to determine required overrides.

- [ ] **Step 2: Write failing test — participant mapping**

```java
class ConversationChannelAdapterTest {
    @Test
    void mapsModelCandidatesToParticipants() {
        var agent1 = AgentRef.external("analyst", ctx -> ...);
        var agent2 = AgentRef.external("critic", ctx -> ...);
        var mapper = (Function<AgentRef, AgentParticipant>) agent ->
            new AgentParticipant(agent, agent.name(), null);

        // Verify participants list matches model candidates
        ...
    }
}
```

- [ ] **Step 3: Write failing test — turn policy inference**

```java
@Test
void infersRoundRobinForDebatePattern() {
    // model.patternType() == DEBATE → RoundRobinTurnPolicy
}

@Test
void usesTurnPolicyOverrideWhenProvided() {
    // config.turnPolicyOverride() set → uses that
}
```

- [ ] **Step 4: Write failing test — outcome mapping**

```java
@Test
void mapsCompleteOutcomeToCompletedResult() {
    // ConversationOutcome with Complete → ExecutionResult.Completed
}

@Test
void mapsFailedOutcomeToFailedResult() {
    // ConversationOutcome with Failed → ExecutionResult.Failed
}
```

- [ ] **Step 5: Implement ConversationChannelAdapter**

Follow the spec's 8-step execute() method:
1. Map candidates → AgentParticipants via resolvedParticipantMapper
2. Create ConversationProjection (factory or DefaultConversationProjection)
3. Determine TurnPolicy (override or infer from patternType)
4. Create PartitionedObservationService (in-memory)
5. Wire responseDispatcher → MessageDispatch → channel feedback loop
6. Convert T → triggering MessageView via triggerMapper
7. Construct ConversationOrchestrator
8. Run and map ConversationOutcome → ExecutionResult

- [ ] **Step 6: Add Conversation variant to ChannelExecutionStrategy**

```java
record Conversation<T>(
    ConversationConfig<T> config,
    TerminationCondition<ConversationState> conversationTermination,
    Function<AgentRef, AgentParticipant> resolvedParticipantMapper
) implements ChannelExecutionStrategy<T> {
    @Override
    public Uni<ExecutionResult> run(ChannelBinding binding,
                                    ExecutionModel<T> model,
                                    T initialContext,
                                    MessageDispatcher dispatcher) {
        return new ConversationChannelAdapter<>(this)
            .execute(binding, model, initialContext, dispatcher);
    }
}
```

- [ ] **Step 7: Run all tests — verify pass**
- [ ] **Step 8: Commit**

---

### Task 6: ChannelBackend

**Files:**
- Create: `src/main/java/io/casehub/blocks/agentic/channel/ChannelBackend.java`
- Test: `src/test/java/io/casehub/blocks/agentic/channel/ChannelBackendTest.java`

**Interfaces:**
- Consumes: `ChannelConfig`, `ChannelExecutionStrategy<T>`, `ExecutionModel<T>`, `ChannelTeardownListener`, `ChannelCreateRequest.Builder`
- Produces: `ChannelBackend<T>` implementing `ExecutionBackend<T>`, `lastBinding()` accessor

- [ ] **Step 1: Write failing test — lifecycle**

```java
class ChannelBackendTest {
    @Test
    void createsChannelBeforeStrategyAndDeletesAfter() {
        var manager = mock(ChannelManager.class);
        var channel = mock(Channel.class);
        var channelId = UUID.randomUUID();
        when(channel.id()).thenReturn(channelId);
        when(manager.create(any())).thenReturn(channel);

        var dispatcher = mock(MessageDispatcher.class);
        var config = ChannelConfig.of(manager, dispatcher, ChannelSemantic.APPEND);
        var strategy = mock(ChannelExecutionStrategy.class);
        when(strategy.run(any(), any(), any(), any()))
            .thenReturn(Uni.createFrom().item(new ExecutionResult.Completed("ok")));

        var backend = new ChannelBackend<>(config, strategy);
        var model = buildMinimalModel();
        backend.execute(model, "input").await().indefinitely();

        var inOrder = inOrder(manager, strategy);
        inOrder.verify(manager).create(any());
        inOrder.verify(strategy).run(any(), any(), any(), any());
        inOrder.verify(manager).delete(channelId, true);
    }

    @Test
    void deletesChannelEvenOnStrategyFailure() {
        // strategy.run() throws → finally block still deletes
    }

    @Test
    void rejectsAgentsWithDuplicateNames() {
        // Two agents with same name() → IllegalArgumentException
    }

    @Test
    void propagatesBindingToTeardownListener() {
        // ChannelTeardownListener in model.listeners() gets setBinding() called
    }

    @Test
    void setsBarrierContributorsFromCandidatesForBarrierSemantic() {
        // BARRIER semantic → barrierContributors derived from agent names
    }
}
```

- [ ] **Step 2: Run test — verify fail**
- [ ] **Step 3: Implement ChannelBackend** (per spec §ChannelBackend)

execute() method:
1. Collect agent names from model.candidateSupplier(), validate uniqueness
2. Build ChannelCreateRequest with auto-generated name, semantic, protocols, allowedWriters, barrierContributors
3. Create channel via channelManager.create()
4. Build ChannelBinding
5. Propagate binding to any ChannelTeardownListener in model.listeners()
6. Notify onExecutionStart
7. Delegate to strategy.run()
8. Notify onExecutionComplete
9. Delete channel (finally block, force=true)
10. Return result

- [ ] **Step 4: Run all tests — verify pass**
- [ ] **Step 5: Commit**

---

### Task 7: DebateBuilder.overChannel()

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/DebateBuilder.java`
- Test: `src/test/java/io/casehub/blocks/agentic/channel/DebateBuilderChannelTest.java`

**Interfaces:**
- Consumes: `ChannelConfig`, `ConversationConfig<T>`, `ChannelBackend`, `ChannelTeardownListener`, `ChannelExecutionStrategy.Conversation`, `AgentParticipant`, `JudgeConvergence`, `MaxIterationsTermination`
- Produces: `DebateBuilder.overChannel(ChannelConfig, ConversationConfig<T>)`

- [ ] **Step 1: Write failing test — overChannel sets backend**

```java
class DebateBuilderChannelTest {
    @Test
    void overChannelSetsBackendAndTeardownListener() {
        var config = ChannelConfig.of(mock(ChannelManager.class),
                mock(MessageDispatcher.class), ChannelSemantic.APPEND);
        var conversation = new ConversationConfig<String>(
                mock(PromptAssembler.class), mock(ResponseMessageBuilder.class),
                s -> mock(MessageView.class), null, null, null, null, null);

        var model = Patterns.<String>debate()
                .debaters(AgentRef.external("a", ctx -> ...),
                          AgentRef.external("b", ctx -> ...))
                .maxRounds(3)
                .overChannel(config, conversation)
                .build();

        assertThat(model.backend()).isNotNull();
        assertThat(model.listeners()).anyMatch(l -> l instanceof ChannelTeardownListener);
    }

    @Test
    void defaultParticipantMapperAssignsDebaterAndJudgeRoles() {
        // Without custom participantMapper, judge gets "judge" role, others get "debater"
    }

    @Test
    void derivesMaxIterationsTerminationWhenNoJudge() {
        // No judge, maxRounds=5 → MaxIterationsTermination(5)
    }

    @Test
    void derivesJudgeConvergenceTerminationWhenJudgeSet() {
        // judge set → JudgeConvergence(judge, maxRounds)
    }

    @Test
    void usesTerminationOverrideFromConversationConfig() {
        // conversation.terminationOverride() set → uses that, ignores maxRounds/judge
    }
}
```

- [ ] **Step 2: Run test — verify fail**

- [ ] **Step 3: Implement DebateBuilder.overChannel()**

```java
public DebateBuilder<T> overChannel(ChannelConfig config,
                                     ConversationConfig<T> conversation) {
    var resolvedMapper = conversation.participantMapper();
    if (resolvedMapper == null) {
        var judgeRef = this.judge;
        resolvedMapper = agent -> {
            String role = agent.equals(judgeRef) ? "judge" : "debater";
            return new AgentParticipant(agent, role, null);
        };
    }

    TerminationCondition<ConversationState> convTermination;
    if (conversation.terminationOverride() != null) {
        convTermination = conversation.terminationOverride();
    } else if (judge != null) {
        convTermination = new JudgeConvergence<>(judge, maxRounds);
    } else {
        convTermination = new MaxIterationsTermination<>(maxRounds);
    }

    var strategy = new ChannelExecutionStrategy.Conversation<>(
        conversation, convTermination, resolvedMapper);
    this.backend = new ChannelBackend<>(config, strategy);
    this.listeners.add(new ChannelTeardownListener(config.channelManager()));
    return this;
}
```

- [ ] **Step 4: Run all tests — verify pass**
- [ ] **Step 5: Commit**

---

### Task 8: VotingBuilder.overChannel() + ParallelBuilder.overChannel()

**Files:**
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/VotingBuilder.java`
- Modify: `src/main/java/io/casehub/blocks/agentic/pattern/ParallelBuilder.java`
- Test: `src/test/java/io/casehub/blocks/agentic/channel/VotingBuilderChannelTest.java`
- Test: `src/test/java/io/casehub/blocks/agentic/channel/ParallelBuilderChannelTest.java`

**Interfaces:**
- Consumes: `ChannelConfig`, `ChannelBackend`, `ChannelTeardownListener`, `ChannelExecutionStrategy.FanIn`, `ChannelExecutionStrategy.Barrier`, `AgentInvoker<T>`, `AgentResult`, `MessageDispatch.Builder`
- Produces: `VotingBuilder.overChannel()` (2 overloads), `ParallelBuilder.overChannel()` (2 overloads)

- [ ] **Step 1: Write failing tests for VotingBuilder**

```java
class VotingBuilderChannelTest {
    @Test
    void overChannelSetsFanInBackend() {
        var config = ChannelConfig.of(mock(ChannelManager.class),
                mock(MessageDispatcher.class), ChannelSemantic.COLLECT);
        Function<AgentResult, MessageDispatch.Builder> resultMapper = result ->
            MessageDispatch.builder().content((String) result.output())
                .type(MessageType.MESSAGE).actorType(ActorType.AGENT);

        var model = Patterns.<String>voting()
                .evaluators(AgentRef.external("v1", ctx -> ...))
                .overChannel(config, resultMapper)
                .build();

        assertThat(model.backend()).isNotNull();
    }

    @Test
    void overChannelWithCustomInvoker() {
        // 3-arg overload with custom AgentInvoker<T>
    }
}
```

- [ ] **Step 2: Write failing tests for ParallelBuilder**

```java
class ParallelBuilderChannelTest {
    @Test
    void overChannelSetsBarrierBackend() {
        var config = ChannelConfig.of(mock(ChannelManager.class),
                mock(MessageDispatcher.class), ChannelSemantic.BARRIER);
        // Similar to voting but with Barrier strategy
    }
}
```

- [ ] **Step 3: Implement VotingBuilder.overChannel()**

```java
public VotingBuilder<T> overChannel(ChannelConfig config,
                                     Function<AgentResult, MessageDispatch.Builder> resultMapper,
                                     AgentInvoker<T> agentInvoker) {
    var strategy = new ChannelExecutionStrategy.FanIn<>(agentInvoker, resultMapper);
    this.backend = new ChannelBackend<>(config, strategy);
    this.listeners.add(new ChannelTeardownListener(config.channelManager()));
    return this;
}

public VotingBuilder<T> overChannel(ChannelConfig config,
                                     Function<AgentResult, MessageDispatch.Builder> resultMapper) {
    return overChannel(config, resultMapper, AgentInvoker.defaultInvoker());
}
```

- [ ] **Step 4: Implement ParallelBuilder.overChannel()** (same pattern, Barrier strategy)

- [ ] **Step 5: Run all tests — verify pass**

Run: `mvn test -pl .`

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#20): add overChannel() to VotingBuilder and ParallelBuilder"
```

---

### Task 9: CLAUDE.md + docs update

Update CLAUDE.md Key Directories, package documentation, and consumer/contributor guides.

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/guides/contributor-guide.md`
- Modify: `docs/guides/consumer-guide.md`

**Interfaces:**
- Consumes: all types from Tasks 1-8
- Produces: documentation

- [ ] **Step 1: Add agentic.channel to CLAUDE.md Key Directories**

Add entry for `src/main/java/io/casehub/blocks/agentic/channel/` and
`src/test/java/io/casehub/blocks/agentic/channel/`.

- [ ] **Step 2: Add Package section to CLAUDE.md**

Document ChannelConfig, ConversationConfig, ChannelBinding, ChannelExecutionStrategy,
ConversationChannelAdapter, ChannelBackend, ChannelTeardownListener.

- [ ] **Step 3: Update contributor-guide.md**

Add channel execution section explaining the architecture, `.overChannel()` API,
and how ChannelBackend composes with ConversationOrchestrator.

- [ ] **Step 4: Update consumer-guide.md**

Add usage examples for debate-over-channel, voting-over-channel, and
composed-agent-with-channel patterns.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks commit -m "docs(#20): add agentic.channel package documentation"
```

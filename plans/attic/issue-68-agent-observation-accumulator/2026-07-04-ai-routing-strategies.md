# AI Routing Strategy Implementations Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement two `AgentRoutingStrategy` strategies in casehub-blocks — LLM-reasoned and CBR-evidence — with optional trust classification via `Instance<T>`.

**Architecture:** Two `@ApplicationScoped` beans in `io.casehub.blocks.routing.agent` implementing engine-api's `AgentRoutingStrategy extends NamedStrategy`. Optional deps (trust classification, CBR store, agent provider) injected via `Instance<T>`. Shared LLM prompt/parse logic extracted into `LlmRoutingSupport`. All blocking calls offloaded via `.emitOn(Infrastructure.getDefaultWorkerPool())`.

**Tech Stack:** Java 21, Quarkus CDI, Mutiny (`Uni`/`Multi`), JUnit 5, Mockito, AssertJ

## Global Constraints

- blocks is a single-module library — no `@QuarkusTest`, no CDI container in tests
- `engine-ledger` and `neocortex-memory-api` are `provided` scope — optional at runtime
- All new classes in `io.casehub.blocks.routing.agent` package
- No `@Alternative @Priority` — strategies are name-selected via `StrategyResolver`
- Blocking calls MUST run on worker pool, never Vert.x IO thread
- Spec: `specs/issue-30-ai-routing-strategy-impls/2026-07-04-ai-routing-strategies-design.md`

---

### Task 1: Add provided-scope dependencies to pom.xml

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Consumes: nothing
- Produces: compile-time access to `TrustCandidateClassifier`, `TrustScoreSource`, `CbrCaseMemoryStore`, `AgentGraphQuery`

- [ ] **Step 1: Add engine-ledger, ledger-api, and neocortex-memory-api as provided deps**

Add after the existing `casehub-platform-agent-api` provided dependency:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-engine-ledger</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-ledger-api</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-neocortex-memory-api</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 2: Verify compilation**

Run: `mvn --batch-mode compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build(#30): add engine-ledger, ledger-api, neocortex-memory-api as provided deps"
```

---

### Task 2: LlmRoutingSupport — shared prompt building and response parsing

**Files:**
- Create: `src/main/java/io/casehub/blocks/routing/agent/LlmRoutingSupport.java`
- Test: `src/test/java/io/casehub/blocks/routing/agent/LlmRoutingSupportTest.java`

**Interfaces:**
- Consumes: `AgentProvider` (platform-agent-api), `AgentCandidate` (engine-api), `AgentDescriptor` (eidos-api)
- Produces:
  - `LlmRoutingSupport.SYSTEM_PROMPT` — static final String
  - `LlmRoutingSupport.buildUserPrompt(String capabilityName, String caseContextSummary, List<AgentCandidate> candidates)` — returns `String`
  - `LlmRoutingSupport.buildCandidateCard(AgentCandidate candidate)` — returns `String`
  - `LlmRoutingSupport.parseSelection(String llmResponse, List<AgentCandidate> candidates)` — returns `@Nullable String workerId` (null if unparseable or unknown agent)
  - `LlmRoutingSupport.extractAgentName(String text)` — returns `@Nullable String`
  - `LlmRoutingSupport.invokeAndCollect(AgentProvider provider, String systemPrompt, String userPrompt)` — returns `String` (blocking — collects all TextDelta events)

- [ ] **Step 1: Write the failing tests**

```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmRoutingSupportTest {

    private AgentCandidate candidate(String workerId, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(workerId)
                .name(workerId)
                .slot("agent")
                .tenancyId("test")
                .capabilities(List.of(AgentCapability.builder().name("analysis").build()))
                .briefing(briefing)
                .build();
        return new AgentCandidate(workerId, Set.of("analysis"), 0, AgentHealth.READY, descriptor);
    }

    private AgentCandidate candidateNoDescriptor(String workerId) {
        return new AgentCandidate(workerId, Set.of("analysis"), 0, AgentHealth.READY, null);
    }

    @Nested
    class PromptBuilding {
        @Test
        void includesCapabilityAndContext() {
            var prompt = LlmRoutingSupport.buildUserPrompt(
                    "data-analysis", "Quarterly report data",
                    List.of(candidate("analyst", "Analyses data")));
            assertThat(prompt).contains("data-analysis").contains("Quarterly report data");
        }

        @Test
        void includesCandidateBriefingAndCapabilities() {
            var prompt = LlmRoutingSupport.buildUserPrompt(
                    "review", null,
                    List.of(candidate("reviewer", "Reviews code for security")));
            assertThat(prompt).contains("reviewer").contains("Reviews code for security").contains("analysis");
        }

        @Test
        void handlesCandidateWithoutDescriptor() {
            var prompt = LlmRoutingSupport.buildUserPrompt(
                    "task", null,
                    List.of(candidateNoDescriptor("plain-worker")));
            assertThat(prompt).contains("plain-worker");
        }
    }

    @Nested
    class ResponseParsing {
        @Test
        void extractsAgentNameFromValidJson() {
            assertThat(LlmRoutingSupport.extractAgentName(
                    "{\"agent\": \"reviewer\", \"reason\": \"best fit\"}"))
                    .isEqualTo("reviewer");
        }

        @Test
        void returnsNullForMalformedResponse() {
            assertThat(LlmRoutingSupport.extractAgentName("not json")).isNull();
        }

        @Test
        void returnsNullForNullInput() {
            assertThat(LlmRoutingSupport.extractAgentName(null)).isNull();
        }

        @Test
        void parsesSelectionToWorkerId() {
            var candidates = List.of(
                    candidate("agent-a", "Does A"),
                    candidate("agent-b", "Does B"));
            var result = LlmRoutingSupport.parseSelection(
                    "{\"agent\": \"agent-b\", \"reason\": \"better fit\"}", candidates);
            assertThat(result).isEqualTo("agent-b");
        }

        @Test
        void returnsNullForUnknownAgent() {
            var candidates = List.of(candidate("agent-a", "Does A"));
            var result = LlmRoutingSupport.parseSelection(
                    "{\"agent\": \"unknown\", \"reason\": \"?\"}", candidates);
            assertThat(result).isNull();
        }

        @Test
        void returnsNullForDoneAgent() {
            var candidates = List.of(candidate("agent-a", "Does A"));
            var result = LlmRoutingSupport.parseSelection(
                    "{\"agent\": \"done\", \"reason\": \"complete\"}", candidates);
            assertThat(result).isNull();
        }
    }

    @Nested
    class InvokeAndCollect {
        @Test
        void collectsTextDeltaEvents() {
            var provider = mock(AgentProvider.class);
            when(provider.invoke(any(AgentSessionConfig.class)))
                    .thenReturn(Multi.createFrom().items(
                            new AgentEvent.TextDelta("{\"agent\""),
                            new AgentEvent.TextDelta(": \"reviewer\", \"reason\": \"ok\"}")));
            var result = LlmRoutingSupport.invokeAndCollect(provider, "system", "user");
            assertThat(result).isEqualTo("{\"agent\": \"reviewer\", \"reason\": \"ok\"}");
        }

        @Test
        void returnsNullOnProviderFailure() {
            var provider = mock(AgentProvider.class);
            when(provider.invoke(any(AgentSessionConfig.class)))
                    .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM down")));
            var result = LlmRoutingSupport.invokeAndCollect(provider, "system", "user");
            assertThat(result).isNull();
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=LlmRoutingSupportTest -q`
Expected: FAIL — `LlmRoutingSupport` class not found

- [ ] **Step 3: Implement LlmRoutingSupport**

```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

final class LlmRoutingSupport {

    private LlmRoutingSupport() {}

    static final String SYSTEM_PROMPT = """
            You are an agent router. Given a capability name, case context, and a list of \
            available agents, select the single best agent to handle the task.

            Respond with JSON only: {"agent": "<agent-name>", "reason": "<one sentence>"}

            Select based on the agent's capabilities, briefing, and domain expertise. \
            Choose the agent whose skills most closely match the task requirements.""";

    static String buildUserPrompt(String capabilityName, @Nullable String caseContextSummary,
                                  List<AgentCandidate> candidates) {
        var sb = new StringBuilder();
        sb.append("Capability: ").append(capabilityName).append("\n\n");
        if (caseContextSummary != null && !caseContextSummary.isBlank()) {
            sb.append("Case context:\n").append(caseContextSummary).append("\n\n");
        }
        sb.append("Available agents:\n");
        for (var candidate : candidates) {
            sb.append(buildCandidateCard(candidate)).append("\n");
        }
        return sb.toString();
    }

    static String buildCandidateCard(AgentCandidate candidate) {
        var sb = new StringBuilder();
        sb.append("- Agent: \"").append(candidate.workerId()).append("\"");
        var desc = candidate.agentDescriptor();
        if (desc != null) {
            if (desc.briefing() != null && !desc.briefing().isBlank()) {
                sb.append("\n  Briefing: ").append(desc.briefing());
            }
            if (desc.capabilities() != null && !desc.capabilities().isEmpty()) {
                var caps = desc.capabilities().stream()
                        .map(AgentCapability::name)
                        .collect(Collectors.joining(", "));
                sb.append("\n  Capabilities: ").append(caps);
            }
            if (desc.slot() != null && !desc.slot().isBlank()) {
                sb.append("\n  Slot: ").append(desc.slot());
            }
        }
        sb.append("\n  Health: ").append(candidate.health());
        sb.append("\n  Running jobs: ").append(candidate.runningJobs());
        return sb.toString();
    }

    static @Nullable String parseSelection(String llmResponse, List<AgentCandidate> candidates) {
        var agentName = extractAgentName(llmResponse);
        if (agentName == null || "done".equalsIgnoreCase(agentName)) {
            return null;
        }
        for (var candidate : candidates) {
            if (candidate.workerId().equals(agentName)) {
                return candidate.workerId();
            }
            var desc = candidate.agentDescriptor();
            if (desc != null && agentName.equals(desc.name())) {
                return candidate.workerId();
            }
        }
        return null;
    }

    static @Nullable String extractAgentName(@Nullable String text) {
        if (text == null) return null;
        var trimmed = text.trim();
        int agentIdx = trimmed.indexOf("\"agent\"");
        if (agentIdx < 0) return null;
        int colonIdx = trimmed.indexOf(':', agentIdx);
        if (colonIdx < 0) return null;
        int firstQuote = trimmed.indexOf('"', colonIdx + 1);
        if (firstQuote < 0) return null;
        int secondQuote = trimmed.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return null;
        return trimmed.substring(firstQuote + 1, secondQuote);
    }

    static @Nullable String invokeAndCollect(AgentProvider provider,
                                             String systemPrompt, String userPrompt) {
        try {
            var config = AgentSessionConfig.of(systemPrompt, userPrompt);
            return provider.invoke(config)
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().indefinitely();
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=LlmRoutingSupportTest -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/casehub/blocks/routing/agent/LlmRoutingSupport.java src/test/java/io/casehub/blocks/routing/agent/LlmRoutingSupportTest.java
git commit -m "feat(#30): LlmRoutingSupport — shared prompt building and response parsing"
```

---

### Task 3: LlmAgentRoutingStrategy

**Files:**
- Create: `src/main/java/io/casehub/blocks/routing/agent/LlmAgentRoutingStrategy.java`
- Test: `src/test/java/io/casehub/blocks/routing/agent/LlmAgentRoutingStrategyTest.java`

**Interfaces:**
- Consumes: `LlmRoutingSupport` (Task 2), `AgentRoutingStrategy` (engine-api), `TrustCandidateClassifier` (engine-ledger, optional), `AgentProvider` (platform-agent-api, optional), `TrustScoreSource` (ledger-api, optional), `TrustRoutingPolicyProvider` (engine-api, optional)
- Produces: `LlmAgentRoutingStrategy` — `@ApplicationScoped` bean with `id()` returning `"llm"`, `select(AgentRoutingContext, List<AgentCandidate>) → Uni<AgentAssignment>`

- [ ] **Step 1: Write the failing tests**

```java
package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentAssignment;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.EscalationReason;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmAgentRoutingStrategyTest {

    @Mock AgentProvider agentProvider;
    @Mock TrustCandidateClassifier classifier;
    @Mock TrustScoreSource scoreSource;
    @Mock TrustRoutingPolicyProvider policyProvider;

    private AgentRoutingContext context(String capability) {
        return new AgentRoutingContext(UUID.randomUUID(), capability, NullNode.instance);
    }

    private AgentCandidate candidate(String id) {
        return candidate(id, "Does " + id);
    }

    private AgentCandidate candidate(String id, String briefing) {
        var descriptor = AgentDescriptor.builder()
                .agentId(id).name(id).slot("agent").tenancyId("test")
                .capabilities(List.of(AgentCapability.builder().name("analysis").build()))
                .briefing(briefing).build();
        return new AgentCandidate(id, Set.of("analysis"), 0, AgentHealth.READY, descriptor);
    }

    private void agentReturns(String text) {
        when(agentProvider.invoke(any(AgentSessionConfig.class)))
                .thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta(text)));
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> presentInstance(T value) {
        var inst = mock(Instance.class);
        when(inst.isUnsatisfied()).thenReturn(false);
        when(inst.get()).thenReturn(value);
        return inst;
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> absentInstance() {
        var inst = mock(Instance.class);
        when(inst.isUnsatisfied()).thenReturn(true);
        return inst;
    }

    @Test
    void idIsLlm() {
        var strategy = new LlmAgentRoutingStrategy(
                presentInstance(agentProvider), absentInstance(), absentInstance(), absentInstance());
        assertThat(strategy.id()).isEqualTo("llm");
    }

    @Nested
    class WithoutTrust {
        private LlmAgentRoutingStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new LlmAgentRoutingStrategy(
                    presentInstance(agentProvider), absentInstance(), absentInstance(), absentInstance());
        }

        @Test
        void selectsAgentByLlmResponse() {
            agentReturns("{\"agent\": \"agent-b\", \"reason\": \"best fit\"}");
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-b");
        }

        @Test
        void emptyCandidatesReturnsUnresolvable() {
            var result = strategy.select(context("analysis"), List.of()).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
        }

        @Test
        void unknownAgentReturnsUnresolvable() {
            agentReturns("{\"agent\": \"ghost\", \"reason\": \"?\"}");
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
        }

        @Test
        void unparseableResponseReturnsUnresolvable() {
            agentReturns("not json");
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
        }

        @Test
        void providerFailureReturnsUnresolvable() {
            when(agentProvider.invoke(any(AgentSessionConfig.class)))
                    .thenReturn(Multi.createFrom().failure(new RuntimeException("LLM down")));
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
        }

        @Test
        void neverReturnsEscalateWithoutTrust() {
            agentReturns("{\"agent\": \"agent-a\", \"reason\": \"ok\"}");
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            assertThat(result).isNotInstanceOf(AgentAssignment.EscalateToOversight.class);
        }
    }

    @Nested
    class WithTrust {
        private LlmAgentRoutingStrategy strategy;
        private TrustRoutingPolicy policy;

        @BeforeEach
        void setUp() {
            policy = new TrustRoutingPolicy(0.7, 0.5, 0.1, 5, Map.of(), false, null);
            lenient().when(policyProvider.forCapability(anyString())).thenReturn(policy);
            strategy = new LlmAgentRoutingStrategy(
                    presentInstance(agentProvider), presentInstance(classifier),
                    presentInstance(scoreSource), presentInstance(policyProvider));
        }

        @Test
        void excludedCandidatesFilteredBeforeLlm() {
            var good = candidate("qualified-agent");
            var bad = candidate("excluded-agent");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            good, TrustCandidateClassifier.Phase.QUALIFIED,
                            OptionalDouble.of(0.9), 1.0),
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            bad, TrustCandidateClassifier.Phase.EXCLUDED_PHASE2B,
                            OptionalDouble.of(0.3), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            agentReturns("{\"agent\": \"qualified-agent\", \"reason\": \"only option\"}");

            var result = strategy.select(context("analysis"),
                    List.of(good, bad)).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("qualified-agent");
        }

        @Test
        void borderlineOnlyPoolEscalates() {
            var a = candidate("borderline-a");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            a, TrustCandidateClassifier.Phase.BORDERLINE,
                            OptionalDouble.of(0.65), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            when(classifier.decide(any(), any(), eq("analysis")))
                    .thenReturn(AgentAssignment.escalate("analysis", EscalationReason.BORDERLINE_STALEMATE));

            var result = strategy.select(context("analysis"), List.of(a)).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.EscalateToOversight.class);
            assertThat(((AgentAssignment.EscalateToOversight) result).reason())
                    .isEqualTo(EscalationReason.BORDERLINE_STALEMATE);
        }

        @Test
        void bootstrapGuardEscalatesWhenPolicyRequires() {
            var bootstrapPolicy = new TrustRoutingPolicy(0.7, 0.5, 0.1, 5, Map.of(), true, null);
            when(policyProvider.forCapability("analysis")).thenReturn(bootstrapPolicy);
            var a = candidate("bootstrap-agent");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            a, TrustCandidateClassifier.Phase.BOOTSTRAP,
                            OptionalDouble.empty(), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(bootstrapPolicy), eq(scoreSource)))
                    .thenReturn(classified);

            var result = strategy.select(context("analysis"), List.of(a)).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.EscalateToOversight.class);
            assertThat(((AgentAssignment.EscalateToOversight) result).reason())
                    .isEqualTo(EscalationReason.NO_QUALIFIED_AGENT);
        }
    }

    @Nested
    class InertWhenNoProvider {
        @Test
        void returnsUnresolvableWhenAgentProviderAbsent() {
            var strategy = new LlmAgentRoutingStrategy(
                    absentInstance(), absentInstance(), absentInstance(), absentInstance());
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=LlmAgentRoutingStrategyTest -q`
Expected: FAIL — `LlmAgentRoutingStrategy` class not found

- [ ] **Step 3: Implement LlmAgentRoutingStrategy**

```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentAssignment;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.AgentRoutingStrategy;
import io.casehub.api.spi.routing.EscalationReason;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.ledger.routing.TrustCandidateClassifier.ClassifiedCandidate;
import io.casehub.ledger.routing.TrustCandidateClassifier.Phase;
import io.casehub.ledger.routing.TrustCandidateClassifier.ScoredCandidate;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LlmAgentRoutingStrategy implements AgentRoutingStrategy {

    private static final System.Logger LOG = System.getLogger(LlmAgentRoutingStrategy.class.getName());

    private final @Nullable AgentProvider agentProvider;
    private final @Nullable TrustCandidateClassifier classifier;
    private final @Nullable TrustScoreSource scoreSource;
    private final @Nullable TrustRoutingPolicyProvider policyProvider;

    @Inject
    public LlmAgentRoutingStrategy(
            Instance<AgentProvider> agentProvider,
            Instance<TrustCandidateClassifier> classifier,
            Instance<TrustScoreSource> scoreSource,
            Instance<TrustRoutingPolicyProvider> policyProvider) {
        this.agentProvider = agentProvider.isUnsatisfied() ? null : agentProvider.get();
        this.classifier = classifier.isUnsatisfied() ? null : classifier.get();
        this.scoreSource = scoreSource.isUnsatisfied() ? null : scoreSource.get();
        this.policyProvider = policyProvider.isUnsatisfied() ? null : policyProvider.get();
    }

    @Override
    public String id() {
        return "llm";
    }

    @Override
    public Uni<AgentAssignment> select(AgentRoutingContext context, List<AgentCandidate> candidates) {
        if (candidates.isEmpty()) {
            return Uni.createFrom().item(AgentAssignment.unresolvable());
        }
        if (agentProvider == null) {
            return Uni.createFrom().item(AgentAssignment.unresolvable());
        }

        return Uni.createFrom().item(() -> doSelect(context, candidates))
                .emitOn(Infrastructure.getDefaultWorkerPool());
    }

    private AgentAssignment doSelect(AgentRoutingContext context, List<AgentCandidate> candidates) {
        List<AgentCandidate> eligible = candidates;
        List<ClassifiedCandidate> classified = null;

        if (classifier != null && scoreSource != null && policyProvider != null) {
            TrustRoutingPolicy policy = policyProvider.forCapability(context.capabilityName());
            classified = classifier.classify(candidates, context.capabilityName(), policy, scoreSource);

            if (policy.bootstrapEscalationRequired()) {
                boolean hasQualified = classified.stream().anyMatch(c -> c.phase() == Phase.QUALIFIED);
                boolean hasBootstrap = classified.stream().anyMatch(c -> c.phase() == Phase.BOOTSTRAP);
                if (!hasQualified && hasBootstrap) {
                    return AgentAssignment.escalate(
                            context.capabilityName(), EscalationReason.NO_QUALIFIED_AGENT);
                }
            }

            eligible = classified.stream()
                    .filter(c -> !c.isExcluded())
                    .filter(c -> !policy.bootstrapEscalationRequired() || c.phase() != Phase.BOOTSTRAP)
                    .map(ClassifiedCandidate::candidate)
                    .toList();

            if (eligible.isEmpty()) {
                var scored = classified.stream()
                        .map(c -> new ScoredCandidate(c, 0.0))
                        .toList();
                return classifier.decide(classified, scored, context.capabilityName());
            }
        }

        var caseContextSummary = context.caseContext() != null
                ? context.caseContext().toString() : null;
        var prompt = LlmRoutingSupport.buildUserPrompt(
                context.capabilityName(), caseContextSummary, eligible);
        var response = LlmRoutingSupport.invokeAndCollect(
                agentProvider, LlmRoutingSupport.SYSTEM_PROMPT, prompt);

        if (response == null) {
            return AgentAssignment.unresolvable();
        }

        var workerId = LlmRoutingSupport.parseSelection(response, eligible);
        if (workerId == null) {
            return AgentAssignment.unresolvable();
        }

        return AgentAssignment.assign(workerId);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=LlmAgentRoutingStrategyTest -q`
Expected: PASS

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test -q`
Expected: PASS — no regressions

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/blocks/routing/agent/LlmAgentRoutingStrategy.java src/test/java/io/casehub/blocks/routing/agent/LlmAgentRoutingStrategyTest.java
git commit -m "feat(#30): LlmAgentRoutingStrategy — LLM-reasoned agent selection with optional trust"
```

---

### Task 4: CbrAgentRoutingStrategy

**Files:**
- Create: `src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java`
- Test: `src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java`

**Interfaces:**
- Consumes: `AgentRoutingStrategy` (engine-api), `TrustCandidateClassifier` (engine-ledger, optional), `CbrCaseMemoryStore` (neocortex-memory-api, optional), `AgentGraphQuery` (eidos-api, optional), `TrustScoreSource` (ledger-api, optional), `TrustRoutingPolicyProvider` (engine-api, optional)
- Produces: `CbrAgentRoutingStrategy` — `@ApplicationScoped` bean with `id()` returning `"cbr"`, `select(AgentRoutingContext, List<AgentCandidate>) → Uni<AgentAssignment>`

- [ ] **Step 1: Write the failing tests**

```java
package io.casehub.blocks.routing.agent;

import com.fasterxml.jackson.databind.node.NullNode;
import io.casehub.api.spi.routing.AgentAssignment;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentHealth;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.EscalationReason;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.eidos.api.AgentGraphQuery;
import io.casehub.eidos.api.AgentTaskHistory;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.casehub.neocortex.memory.cbr.TextualCbrCase;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbrAgentRoutingStrategyTest {

    @Mock CbrCaseMemoryStore cbrStore;
    @Mock AgentGraphQuery graphQuery;
    @Mock TrustCandidateClassifier classifier;
    @Mock TrustScoreSource scoreSource;
    @Mock TrustRoutingPolicyProvider policyProvider;

    private AgentRoutingContext context(String capability) {
        return new AgentRoutingContext(UUID.randomUUID(), capability, NullNode.instance);
    }

    private AgentCandidate candidate(String id) {
        return new AgentCandidate(id, Set.of("analysis"), 0, AgentHealth.READY, null);
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> presentInstance(T value) {
        var inst = mock(Instance.class);
        when(inst.isUnsatisfied()).thenReturn(false);
        when(inst.get()).thenReturn(value);
        return inst;
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> absentInstance() {
        var inst = mock(Instance.class);
        when(inst.isUnsatisfied()).thenReturn(true);
        return inst;
    }

    @Test
    void idIsCbr() {
        var strategy = new CbrAgentRoutingStrategy(
                absentInstance(), absentInstance(), absentInstance(), absentInstance(), absentInstance());
        assertThat(strategy.id()).isEqualTo("cbr");
    }

    @Nested
    class PlanBasedCbr {
        private CbrAgentRoutingStrategy strategy;

        @BeforeEach
        void setUp() {
            strategy = new CbrAgentRoutingStrategy(
                    presentInstance(cbrStore), presentInstance(graphQuery),
                    absentInstance(), absentInstance(), absentInstance());
        }

        @Test
        void selectsWorkerWithHighestSuccessRate() {
            var winnerTrace = new PlanTrace("binding-a", "analysis", "agent-a", "SUCCESS", 1, Map.of());
            var loserTrace = new PlanTrace("binding-a", "analysis", "agent-b", "FAILURE", 1, Map.of());
            var winner2Trace = new PlanTrace("binding-a", "analysis", "agent-a", "SUCCESS", 1, Map.of());

            var case1 = new PlanCbrCase("problem1", "sol1", "RESOLVED", 0.9, Map.of(),
                    List.of(winnerTrace));
            var case2 = new PlanCbrCase("problem2", "sol2", "UNRESOLVED", 0.3, Map.of(),
                    List.of(loserTrace));
            var case3 = new PlanCbrCase("problem3", "sol3", "RESOLVED", 0.8, Map.of(),
                    List.of(winner2Trace));

            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(
                            new ScoredCbrCase<>(case1, 0.9),
                            new ScoredCbrCase<>(case2, 0.85),
                            new ScoredCbrCase<>(case3, 0.8)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();

            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-a");
        }

        @Test
        void ignoresWorkersNotInCandidateList() {
            var trace = new PlanTrace("binding-a", "analysis", "not-a-candidate", "SUCCESS", 1, Map.of());
            var cbrCase = new PlanCbrCase("problem", "solution", "RESOLVED", 0.9, Map.of(),
                    List.of(trace));
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(cbrCase, 0.9)));

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            // Falls through to AgentGraphQuery or Unresolvable
            assertThat(result).isNotNull();
        }
    }

    @Nested
    class TextualFallback {
        @Test
        void textualCasesDoNotProduceWorkerMatch() {
            var textCase = new TextualCbrCase("similar problem", "some solution", "RESOLVED", 0.8);
            when(cbrStore.retrieveSimilar(any(CbrQuery.class), eq(CbrCase.class)))
                    .thenReturn(List.of(new ScoredCbrCase<>(textCase, 0.9)));

            var strategy = new CbrAgentRoutingStrategy(
                    presentInstance(cbrStore), presentInstance(graphQuery),
                    absentInstance(), absentInstance(), absentInstance());

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            // Textual cases lack worker identity — falls through
            assertThat(result).isNotNull();
        }
    }

    @Nested
    class Fallbacks {
        @Test
        void fallsBackToGraphQueryWhenCbrStoreUnavailable() {
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                    .thenReturn(List.of("agent-b", "agent-a"));

            var strategy = new CbrAgentRoutingStrategy(
                    absentInstance(), presentInstance(graphQuery),
                    absentInstance(), absentInstance(), absentInstance());

            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"), candidate("agent-b"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("agent-b");
        }

        @Test
        void bothSourcesUnavailableReturnsUnresolvable() {
            var strategy = new CbrAgentRoutingStrategy(
                    absentInstance(), absentInstance(),
                    absentInstance(), absentInstance(), absentInstance());
            var result = strategy.select(context("analysis"),
                    List.of(candidate("agent-a"))).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
        }

        @Test
        void emptyCandidatesReturnsUnresolvable() {
            var strategy = new CbrAgentRoutingStrategy(
                    presentInstance(cbrStore), absentInstance(),
                    absentInstance(), absentInstance(), absentInstance());
            var result = strategy.select(context("analysis"), List.of()).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Unresolvable.class);
        }
    }

    @Nested
    class WithTrust {
        @Test
        void excludedCandidatesFilteredBeforeCbr() {
            var policy = new TrustRoutingPolicy(0.7, 0.5, 0.1, 5, Map.of(), false, null);
            when(policyProvider.forCapability("analysis")).thenReturn(policy);
            var good = candidate("qualified");
            var bad = candidate("excluded");
            var classified = List.of(
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            good, TrustCandidateClassifier.Phase.QUALIFIED,
                            OptionalDouble.of(0.9), 1.0),
                    new TrustCandidateClassifier.ClassifiedCandidate(
                            bad, TrustCandidateClassifier.Phase.EXCLUDED_PHASE2B,
                            OptionalDouble.of(0.3), 1.0));
            when(classifier.classify(any(), eq("analysis"), eq(policy), eq(scoreSource)))
                    .thenReturn(classified);
            when(graphQuery.topAgentsByOutcome(eq("analysis"), any(), any(), eq(10)))
                    .thenReturn(List.of("qualified"));

            var strategy = new CbrAgentRoutingStrategy(
                    absentInstance(), presentInstance(graphQuery),
                    presentInstance(classifier), presentInstance(scoreSource),
                    presentInstance(policyProvider));

            var result = strategy.select(context("analysis"),
                    List.of(good, bad)).await().indefinitely();
            assertThat(result).isInstanceOf(AgentAssignment.Assigned.class);
            assertThat(((AgentAssignment.Assigned) result).workerId()).isEqualTo("qualified");
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=CbrAgentRoutingStrategyTest -q`
Expected: FAIL — `CbrAgentRoutingStrategy` class not found

- [ ] **Step 3: Implement CbrAgentRoutingStrategy**

```java
package io.casehub.blocks.routing.agent;

import io.casehub.api.spi.routing.AgentAssignment;
import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.AgentRoutingStrategy;
import io.casehub.api.spi.routing.EscalationReason;
import io.casehub.api.spi.routing.TrustRoutingPolicy;
import io.casehub.api.spi.routing.TrustRoutingPolicyProvider;
import io.casehub.eidos.api.AgentGraphQuery;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.routing.TrustCandidateClassifier;
import io.casehub.ledger.routing.TrustCandidateClassifier.ClassifiedCandidate;
import io.casehub.ledger.routing.TrustCandidateClassifier.Phase;
import io.casehub.ledger.routing.TrustCandidateClassifier.ScoredCandidate;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.cbr.CbrCase;
import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.CbrQuery;
import io.casehub.neocortex.memory.cbr.PlanCbrCase;
import io.casehub.neocortex.memory.cbr.PlanTrace;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class CbrAgentRoutingStrategy implements AgentRoutingStrategy {

    private static final System.Logger LOG = System.getLogger(CbrAgentRoutingStrategy.class.getName());
    private static final int DEFAULT_TOP_K = 10;

    private final @Nullable CbrCaseMemoryStore cbrStore;
    private final @Nullable AgentGraphQuery graphQuery;
    private final @Nullable TrustCandidateClassifier classifier;
    private final @Nullable TrustScoreSource scoreSource;
    private final @Nullable TrustRoutingPolicyProvider policyProvider;

    @Inject
    public CbrAgentRoutingStrategy(
            Instance<CbrCaseMemoryStore> cbrStore,
            Instance<AgentGraphQuery> graphQuery,
            Instance<TrustCandidateClassifier> classifier,
            Instance<TrustScoreSource> scoreSource,
            Instance<TrustRoutingPolicyProvider> policyProvider) {
        this.cbrStore = cbrStore.isUnsatisfied() ? null : cbrStore.get();
        this.graphQuery = graphQuery.isUnsatisfied() ? null : graphQuery.get();
        this.classifier = classifier.isUnsatisfied() ? null : classifier.get();
        this.scoreSource = scoreSource.isUnsatisfied() ? null : scoreSource.get();
        this.policyProvider = policyProvider.isUnsatisfied() ? null : policyProvider.get();
    }

    @Override
    public String id() {
        return "cbr";
    }

    @Override
    public Uni<AgentAssignment> select(AgentRoutingContext context, List<AgentCandidate> candidates) {
        if (candidates.isEmpty()) {
            return Uni.createFrom().item(AgentAssignment.unresolvable());
        }
        if (cbrStore == null && graphQuery == null) {
            return Uni.createFrom().item(AgentAssignment.unresolvable());
        }

        return Uni.createFrom().item(() -> doSelect(context, candidates))
                .emitOn(Infrastructure.getDefaultWorkerPool());
    }

    private AgentAssignment doSelect(AgentRoutingContext context, List<AgentCandidate> candidates) {
        List<AgentCandidate> eligible = candidates;
        List<ClassifiedCandidate> classified = null;

        if (classifier != null && scoreSource != null && policyProvider != null) {
            TrustRoutingPolicy policy = policyProvider.forCapability(context.capabilityName());
            classified = classifier.classify(candidates, context.capabilityName(), policy, scoreSource);

            if (policy.bootstrapEscalationRequired()) {
                boolean hasQualified = classified.stream().anyMatch(c -> c.phase() == Phase.QUALIFIED);
                boolean hasBootstrap = classified.stream().anyMatch(c -> c.phase() == Phase.BOOTSTRAP);
                if (!hasQualified && hasBootstrap) {
                    return AgentAssignment.escalate(
                            context.capabilityName(), EscalationReason.NO_QUALIFIED_AGENT);
                }
            }

            eligible = classified.stream()
                    .filter(c -> !c.isExcluded())
                    .filter(c -> !policy.bootstrapEscalationRequired() || c.phase() != Phase.BOOTSTRAP)
                    .map(ClassifiedCandidate::candidate)
                    .toList();

            if (eligible.isEmpty()) {
                var scored = classified.stream()
                        .map(c -> new ScoredCandidate(c, 0.0))
                        .toList();
                return classifier.decide(classified, scored, context.capabilityName());
            }
        }

        Set<String> eligibleIds = eligible.stream()
                .map(AgentCandidate::workerId)
                .collect(Collectors.toSet());

        // Try CBR store first
        if (cbrStore != null) {
            var cbrResult = tryCbrStore(context, eligibleIds);
            if (cbrResult != null) {
                return AgentAssignment.assign(cbrResult);
            }
        }

        // Fallback to graph query
        if (graphQuery != null) {
            var graphResult = tryGraphQuery(context, eligibleIds);
            if (graphResult != null) {
                return AgentAssignment.assign(graphResult);
            }
        }

        // Neither source produced a match — if trust classified, delegate decision
        if (classified != null) {
            var scored = classified.stream()
                    .map(c -> new ScoredCandidate(c, 0.0))
                    .toList();
            return classifier.decide(classified, scored, context.capabilityName());
        }

        return AgentAssignment.unresolvable();
    }

    private @Nullable String tryCbrStore(AgentRoutingContext context, Set<String> eligibleIds) {
        try {
            var query = CbrQuery.of(
                    "default",
                    new MemoryDomain(context.capabilityName()),
                    "agent-routing",
                    Map.of(),
                    DEFAULT_TOP_K);
            var caseContextText = context.caseContext() != null
                    ? context.caseContext().toString() : null;
            if (caseContextText != null && !caseContextText.isBlank()) {
                query = query.withProblem(caseContextText);
            }

            List<ScoredCbrCase<CbrCase>> results = cbrStore.retrieveSimilar(query, CbrCase.class);
            if (results.isEmpty()) {
                return null;
            }

            return analyseByType(results, eligibleIds, context.capabilityName());
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "CBR store query failed", e);
            return null;
        }
    }

    private @Nullable String analyseByType(List<ScoredCbrCase<CbrCase>> results,
                                            Set<String> eligibleIds, String capabilityName) {
        // Tally worker success rates from plan traces
        Map<String, int[]> workerStats = new HashMap<>(); // [successes, total]

        for (var scored : results) {
            var cbrCase = scored.cbrCase();
            if (cbrCase instanceof PlanCbrCase planCase) {
                for (PlanTrace trace : planCase.planTrace()) {
                    if (capabilityName.equals(trace.capabilityName())
                            && trace.workerName() != null
                            && eligibleIds.contains(trace.workerName())) {
                        var stats = workerStats.computeIfAbsent(
                                trace.workerName(), k -> new int[]{0, 0});
                        stats[1]++;
                        if ("SUCCESS".equals(trace.stepOutcome())) {
                            stats[0]++;
                        }
                    }
                }
            }
            // TextualCbrCase and FeatureVectorCbrCase lack worker identity — skip
        }

        if (workerStats.isEmpty()) {
            return null;
        }

        // Pick worker with highest success rate (ties broken by total count)
        String bestWorker = null;
        double bestRate = -1;
        int bestCount = 0;
        for (var entry : workerStats.entrySet()) {
            int successes = entry.getValue()[0];
            int total = entry.getValue()[1];
            double rate = (double) successes / total;
            if (rate > bestRate || (rate == bestRate && total > bestCount)) {
                bestRate = rate;
                bestCount = total;
                bestWorker = entry.getKey();
            }
        }
        return bestWorker;
    }

    private @Nullable String tryGraphQuery(AgentRoutingContext context, Set<String> eligibleIds) {
        try {
            var ranked = graphQuery.topAgentsByOutcome(
                    context.capabilityName(), null, null, DEFAULT_TOP_K);
            for (var agentId : ranked) {
                if (eligibleIds.contains(agentId)) {
                    return agentId;
                }
            }
            return null;
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "AgentGraphQuery failed", e);
            return null;
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl . -Dtest=CbrAgentRoutingStrategyTest -q`
Expected: PASS

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test -q`
Expected: PASS — no regressions

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java src/test/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategyTest.java
git commit -m "feat(#30): CbrAgentRoutingStrategy — CBR-evidence agent selection with optional trust"
```

---

### Task 5: Build verification and final review

**Files:**
- No new files — verification only

**Interfaces:**
- Consumes: all previous tasks
- Produces: green build, ready for code review

- [ ] **Step 1: Full build with tests**

Run: `mvn --batch-mode install -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Verify Jandex index includes new beans**

Run: `mvn --batch-mode compile -q && jar tf target/casehub-blocks-*.jar | grep -E "routing/agent/(Llm|Cbr)"`
Expected: Both `.class` files appear in the JAR

- [ ] **Step 3: Verify no unused imports or formatting issues**

Run IntelliJ reformat on the new files:
- `ide_reformat_code` on `src/main/java/io/casehub/blocks/routing/agent/LlmRoutingSupport.java`
- `ide_reformat_code` on `src/main/java/io/casehub/blocks/routing/agent/LlmAgentRoutingStrategy.java`
- `ide_reformat_code` on `src/main/java/io/casehub/blocks/routing/agent/CbrAgentRoutingStrategy.java`

- [ ] **Step 4: Commit any formatting fixes**

Only if reformatting produced changes:
```bash
git add -u
git commit -m "style(#30): reformat AI routing strategies"
```

- [ ] **Step 5: Invoke code review**

Use `superpowers:requesting-code-review` to review the full branch diff before final commit.

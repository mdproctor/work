# Governed Yield Orchestration Layer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #170 — governed yield epic
**Issue group:** #170, #171, #172, #173

**Goal:** Build `JudgmentPolicy<T> implements JudgmentPhase<T>` — a composition root that adds conditional triggers, multi-party dispatch, verification composition, and configurable retry on top of the landed minimal judgment loop.

**Architecture:** `JudgmentPolicy` implements the existing `JudgmentPhase<T>` SPI (landed by slot 160). It composes `JudgmentTrigger` (when to yield), `CallerStrategy` (who judges), `JudgmentDispatcher` (how to reach callers), `JudgmentVerifier` (response quality gate from engine-api), and `RetryPolicy` (failure handling). All new types live in `io.casehub.blocks.agentic.judgment`. No changes to `ExecutionModel`, `AbstractExecutionDriver`, or pattern builders — they already accept `JudgmentPhase<T>`.

**Tech Stack:** Java 17, JUnit 5, Mockito, AssertJ. No CDI/Quarkus runtime in tests.

## Global Constraints

- Package: `io.casehub.blocks.agentic.judgment` (blocks core module)
- All files in: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/`
- Tests in: `blocks/src/test/java/io/casehub/blocks/agentic/judgment/`
- No new compile dependencies — engine-api types (`JudgmentVerifier`, `VerificationResult`, `VerificationContext`, `CallerIdentity`, `Evidence`) already on provided classpath
- `JudgmentPhase<T>`, `JudgmentDecision`, `JudgmentContext<T>` already exist in this package — do not modify them
- TDD: write failing test → verify fail → implement → verify pass → commit
- Build verification: `mvn --batch-mode test -pl blocks`

---

## Batch 1: Foundation Types

### Task 1: JudgmentTrigger SPI + four implementations

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/JudgmentTrigger.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/AlwaysYield.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/NeverYield.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/IterationBased.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/ConfidenceThreshold.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/judgment/JudgmentTriggerTest.java`

**Interfaces:**
- Consumes: `JudgmentContext<T>` (existing)
- Produces: `JudgmentTrigger<T>` SPI — `boolean shouldYield(JudgmentContext<T>)`, composable via `and()`/`or()`

- [ ] **Step 1: Write failing tests for AlwaysYield and NeverYield**

```java
package io.casehub.blocks.agentic.judgment;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class JudgmentTriggerTest {

    private static JudgmentContext<String> ctx(int iteration) {
        return new JudgmentContext<>("state", List.of(), null, iteration, null);
    }

    @Test
    void alwaysYield_returnsTrue() {
        assertThat(new AlwaysYield<String>().shouldYield(ctx(0))).isTrue();
    }

    @Test
    void neverYield_returnsFalse() {
        assertThat(new NeverYield<String>().shouldYield(ctx(0))).isFalse();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest="JudgmentTriggerTest" -DfailIfNoTests=false`
Expected: compilation failure — `JudgmentTrigger`, `AlwaysYield`, `NeverYield` do not exist

- [ ] **Step 3: Implement JudgmentTrigger SPI, AlwaysYield, NeverYield**

```java
// JudgmentTrigger.java
package io.casehub.blocks.agentic.judgment;

@FunctionalInterface
public interface JudgmentTrigger<T> {
    boolean shouldYield(JudgmentContext<T> context);

    default JudgmentTrigger<T> and(JudgmentTrigger<T> other) {
        return ctx -> this.shouldYield(ctx) && other.shouldYield(ctx);
    }

    default JudgmentTrigger<T> or(JudgmentTrigger<T> other) {
        return ctx -> this.shouldYield(ctx) || other.shouldYield(ctx);
    }
}

// AlwaysYield.java
package io.casehub.blocks.agentic.judgment;

public final class AlwaysYield<T> implements JudgmentTrigger<T> {
    @Override
    public boolean shouldYield(JudgmentContext<T> context) { return true; }
}

// NeverYield.java
package io.casehub.blocks.agentic.judgment;

public final class NeverYield<T> implements JudgmentTrigger<T> {
    @Override
    public boolean shouldYield(JudgmentContext<T> context) { return false; }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest="JudgmentTriggerTest" -DfailIfNoTests=false`
Expected: 2 tests PASS

- [ ] **Step 5: Add tests for IterationBased and ConfidenceThreshold**

```java
@Test
void iterationBased_yieldsEveryNIterations() {
    var trigger = new IterationBased<String>(3);
    assertThat(trigger.shouldYield(ctx(0))).isTrue();
    assertThat(trigger.shouldYield(ctx(1))).isFalse();
    assertThat(trigger.shouldYield(ctx(2))).isFalse();
    assertThat(trigger.shouldYield(ctx(3))).isTrue();
    assertThat(trigger.shouldYield(ctx(6))).isTrue();
}

@Test
void confidenceThreshold_yieldsWhenBelowThreshold() {
    var trigger = ConfidenceThreshold.<String>below(0.8,
            ctx -> ctx.iteration() * 0.3);
    assertThat(trigger.shouldYield(ctx(0))).isTrue();   // 0.0 < 0.8
    assertThat(trigger.shouldYield(ctx(2))).isTrue();   // 0.6 < 0.8
    assertThat(trigger.shouldYield(ctx(3))).isFalse();  // 0.9 >= 0.8
}
```

- [ ] **Step 6: Implement IterationBased and ConfidenceThreshold**

```java
// IterationBased.java
package io.casehub.blocks.agentic.judgment;

public final class IterationBased<T> implements JudgmentTrigger<T> {
    private final int every;

    public IterationBased(int every) {
        if (every < 1) throw new IllegalArgumentException("every must be >= 1");
        this.every = every;
    }

    @Override
    public boolean shouldYield(JudgmentContext<T> context) {
        return context.iteration() % every == 0;
    }
}

// ConfidenceThreshold.java
package io.casehub.blocks.agentic.judgment;

import java.util.function.ToDoubleFunction;

public final class ConfidenceThreshold<T> implements JudgmentTrigger<T> {
    private final double threshold;
    private final ToDoubleFunction<JudgmentContext<T>> extractor;

    private ConfidenceThreshold(double threshold, ToDoubleFunction<JudgmentContext<T>> extractor) {
        this.threshold = threshold;
        this.extractor = extractor;
    }

    public static <T> ConfidenceThreshold<T> below(double threshold,
                                                     ToDoubleFunction<JudgmentContext<T>> extractor) {
        return new ConfidenceThreshold<>(threshold, extractor);
    }

    @Override
    public boolean shouldYield(JudgmentContext<T> context) {
        return extractor.applyAsDouble(context) < threshold;
    }
}
```

- [ ] **Step 7: Add tests for and()/or() composition**

```java
@Test
void and_compositionRequiresBoth() {
    var always = new AlwaysYield<String>();
    var never = new NeverYield<String>();
    assertThat(always.and(always).shouldYield(ctx(0))).isTrue();
    assertThat(always.and(never).shouldYield(ctx(0))).isFalse();
}

@Test
void or_compositionRequiresEither() {
    var always = new AlwaysYield<String>();
    var never = new NeverYield<String>();
    assertThat(never.or(always).shouldYield(ctx(0))).isTrue();
    assertThat(never.or(never).shouldYield(ctx(0))).isFalse();
}
```

- [ ] **Step 8: Run all trigger tests**

Run: `mvn --batch-mode test -pl blocks -Dtest="JudgmentTriggerTest" -DfailIfNoTests=false`
Expected: 6 tests PASS

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add blocks/src/main/java/io/casehub/blocks/agentic/judgment/JudgmentTrigger.java blocks/src/main/java/io/casehub/blocks/agentic/judgment/AlwaysYield.java blocks/src/main/java/io/casehub/blocks/agentic/judgment/NeverYield.java blocks/src/main/java/io/casehub/blocks/agentic/judgment/IterationBased.java blocks/src/main/java/io/casehub/blocks/agentic/judgment/ConfidenceThreshold.java blocks/src/test/java/io/casehub/blocks/agentic/judgment/JudgmentTriggerTest.java
```
`feat(#170): add JudgmentTrigger SPI with four implementations  Refs #170`

### Task 2: CallerRef + CallerStrategy + RetryPolicy + VerifierFailurePolicy

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/CallerRef.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/CallerStrategy.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/RetryPolicy.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/ExhaustionPolicy.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/VerifierFailurePolicy.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/judgment/CallerStrategyTest.java`

**Interfaces:**
- Consumes: `AgentRef` (existing blocks type)
- Produces: `CallerRef`, `CallerStrategy` sealed hierarchy, `RetryPolicy`, `ExhaustionPolicy`, `VerifierFailurePolicy`

- [ ] **Step 1: Write failing tests for CallerRef and CallerStrategy**

```java
package io.casehub.blocks.agentic.judgment;

import io.casehub.blocks.agentic.AgentRef;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class CallerStrategyTest {

    @Test
    void callerRef_agentFactory() {
        var agent = AgentRef.external("judge", ctx -> null);
        var ref = CallerRef.agent("judge-1", agent);
        assertThat(ref.id()).isEqualTo("judge-1");
        assertThat(ref.agentRef()).isEqualTo(agent);
        assertThat(ref.routingHints()).isNull();
    }

    @Test
    void callerRef_humanFactory() {
        var ref = CallerRef.human("approver", Map.of("candidateGroups", "managers"));
        assertThat(ref.id()).isEqualTo("approver");
        assertThat(ref.agentRef()).isNull();
        assertThat(ref.routingHints()).containsEntry("candidateGroups", "managers");
    }

    @Test
    void single_strategy() {
        var ref = CallerRef.agent("j", null);
        var strategy = CallerStrategy.single(ref);
        assertThat(strategy).isInstanceOf(CallerStrategy.Single.class);
        assertThat(((CallerStrategy.Single) strategy).caller()).isEqualTo(ref);
    }

    @Test
    void fanOut_strategy() {
        var refs = List.of(CallerRef.agent("a", null), CallerRef.agent("b", null));
        var strategy = CallerStrategy.fanOut(refs, responses -> null);
        assertThat(strategy).isInstanceOf(CallerStrategy.FanOut.class);
        assertThat(((CallerStrategy.FanOut) strategy).callers()).hasSize(2);
    }

    @Test
    void escalationChain_strategy() {
        var refs = List.of(CallerRef.agent("a", null), CallerRef.agent("b", null));
        var strategy = new CallerStrategy.EscalationChain(refs);
        assertThat(strategy.callers()).hasSize(2);
    }

    @Test
    void retryPolicy_defaults() {
        var policy = RetryPolicy.defaults();
        assertThat(policy.maxRetries()).isEqualTo(3);
        assertThat(policy.exhaustionPolicy()).isEqualTo(ExhaustionPolicy.FAIL);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest="CallerStrategyTest" -DfailIfNoTests=false`
Expected: compilation failure

- [ ] **Step 3: Implement all types**

```java
// CallerRef.java
package io.casehub.blocks.agentic.judgment;

import io.casehub.blocks.agentic.AgentRef;
import org.jspecify.annotations.Nullable;
import java.util.Map;

public record CallerRef(
        String id,
        @Nullable String description,
        @Nullable Map<String, Object> routingHints,
        @Nullable AgentRef agentRef) {

    public static CallerRef agent(String id, @Nullable AgentRef ref) {
        return new CallerRef(id, null, null, ref);
    }

    public static CallerRef human(String id, Map<String, Object> routingHints) {
        return new CallerRef(id, null, routingHints, null);
    }
}

// CallerStrategy.java
package io.casehub.blocks.agentic.judgment;

import java.util.List;

public sealed interface CallerStrategy {
    record Single(CallerRef caller) implements CallerStrategy {}
    record FanOut(List<CallerRef> callers, AgreementPolicy agreementPolicy) implements CallerStrategy {
        public FanOut { callers = List.copyOf(callers); }
    }
    record EscalationChain(List<CallerRef> callers) implements CallerStrategy {
        public EscalationChain { callers = List.copyOf(callers); }
    }

    static Single single(CallerRef caller) { return new Single(caller); }
    static Single single() { return new Single(CallerRef.agent("default", null)); }
    static FanOut fanOut(List<CallerRef> callers, AgreementPolicy policy) {
        return new FanOut(callers, policy);
    }
}

// AgreementPolicy.java (needed by FanOut — define as interface now, impl in Batch 2)
package io.casehub.blocks.agentic.judgment;

import java.util.List;

@FunctionalInterface
public interface AgreementPolicy {
    AgreementResult evaluate(List<JudgmentResponse> responses);
}

// AgreementResult.java
package io.casehub.blocks.agentic.judgment;

public sealed interface AgreementResult {
    record Agreed(JudgmentResponse selectedResponse) implements AgreementResult {}
    record Disagreed(String reason) implements AgreementResult {}
}

// JudgmentResponse.java (blocks-level response wrapper)
package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.api.spi.judgment.Evidence;
import java.util.List;

public record JudgmentResponse(
        Object decision,
        List<Evidence> evidence,
        CallerIdentity callerIdentity) {}

// RetryPolicy.java
package io.casehub.blocks.agentic.judgment;

public record RetryPolicy(int maxRetries, ExhaustionPolicy exhaustionPolicy) {
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, ExhaustionPolicy.FAIL);
    }
}

// ExhaustionPolicy.java
package io.casehub.blocks.agentic.judgment;

public enum ExhaustionPolicy {
    FAIL,
    ESCALATE_NEXT_CALLER,
    TERMINATE_PATTERN
}

// VerifierFailurePolicy.java
package io.casehub.blocks.agentic.judgment;

public enum VerifierFailurePolicy {
    RETRY_WITH_FEEDBACK,
    ESCALATE,
    FAIL
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest="CallerStrategyTest" -DfailIfNoTests=false`
Expected: 6 tests PASS

- [ ] **Step 5: Commit**

`feat(#170): add CallerRef, CallerStrategy, RetryPolicy, VerifierFailurePolicy  Refs #170`

---

## Batch 2: Verification Composition + Dispatch

### Task 3: CompositeVerifier + NoOpVerifier

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/CompositeVerifier.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/NoOpVerifier.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/judgment/CompositeVerifierTest.java`

**Interfaces:**
- Consumes: `JudgmentVerifier` (engine-api), `VerificationResult` (engine-api), `VerifierFailurePolicy` (Task 2)
- Produces: `CompositeVerifier` (chains verifiers, most-restrictive-wins), `CompositeVerifier.VerifierEntry`, `NoOpVerifier`

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CompositeVerifierTest {

    private static JudgmentVerifier accepting() {
        return new JudgmentVerifier() {
            @Override public String id() { return "accept"; }
            @Override public VerificationResult verify(VerificationContext ctx) {
                return new VerificationResult.Accepted();
            }
        };
    }

    private static JudgmentVerifier rejecting(String reason) {
        return new JudgmentVerifier() {
            @Override public String id() { return "reject"; }
            @Override public VerificationResult verify(VerificationContext ctx) {
                return new VerificationResult.Rejected(reason);
            }
        };
    }

    private static VerificationContext dummyCtx() {
        return new VerificationContext(null, "", "", null, java.util.Map.of(), null, null, List.of(), null, null);
    }

    @Test
    void allAccepted_returnsAccepted() {
        var composite = CompositeVerifier.of(accepting(), accepting());
        assertThat(composite.verify(dummyCtx())).isInstanceOf(VerificationResult.Accepted.class);
    }

    @Test
    void anyRejected_returnsRejected() {
        var composite = CompositeVerifier.of(accepting(), rejecting("bad"));
        assertThat(composite.verify(dummyCtx())).isInstanceOf(VerificationResult.Rejected.class);
    }

    @Test
    void emptyChain_returnsAccepted() {
        var composite = CompositeVerifier.of();
        assertThat(composite.verify(dummyCtx())).isInstanceOf(VerificationResult.Accepted.class);
    }

    @Test
    void noOpVerifier_alwaysAccepts() {
        var noop = new NoOpVerifier();
        assertThat(noop.id()).isEqualTo("noop");
        assertThat(noop.verify(dummyCtx())).isInstanceOf(VerificationResult.Accepted.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest="CompositeVerifierTest" -DfailIfNoTests=false`
Expected: compilation failure

- [ ] **Step 3: Implement CompositeVerifier and NoOpVerifier**

```java
// CompositeVerifier.java
package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;
import java.util.Arrays;
import java.util.List;

public final class CompositeVerifier implements JudgmentVerifier {

    private final List<VerifierEntry> entries;

    private CompositeVerifier(List<VerifierEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static CompositeVerifier of(JudgmentVerifier... verifiers) {
        return new CompositeVerifier(
                Arrays.stream(verifiers)
                      .map(v -> new VerifierEntry(v, VerifierFailurePolicy.RETRY_WITH_FEEDBACK))
                      .toList());
    }

    public static CompositeVerifier of(VerifierEntry... entries) {
        return new CompositeVerifier(List.of(entries));
    }

    public List<VerifierEntry> entries() { return entries; }

    @Override
    public String id() { return "composite"; }

    @Override
    public VerificationResult verify(VerificationContext context) {
        for (var entry : entries) {
            var result = entry.verifier().verify(context);
            if (!(result instanceof VerificationResult.Accepted)) {
                return result;
            }
        }
        return new VerificationResult.Accepted();
    }

    public VerifierFailurePolicy mostRestrictivePolicy() {
        var worst = VerifierFailurePolicy.RETRY_WITH_FEEDBACK;
        for (var entry : entries) {
            if (entry.failurePolicy().compareTo(worst) > 0) {
                worst = entry.failurePolicy();
            }
        }
        return worst;
    }

    public record VerifierEntry(JudgmentVerifier verifier, VerifierFailurePolicy failurePolicy) {}
}

// NoOpVerifier.java
package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;

public final class NoOpVerifier implements JudgmentVerifier {
    @Override public String id() { return "noop"; }
    @Override public VerificationResult verify(VerificationContext context) {
        return new VerificationResult.Accepted();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest="CompositeVerifierTest" -DfailIfNoTests=false`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

`feat(#170): add CompositeVerifier and NoOpVerifier  Refs #172`

### Task 4: JudgmentDispatcher + DirectJudgmentDispatcher

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/JudgmentDispatcher.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/JudgmentDispatchRequest.java`
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/DirectJudgmentDispatcher.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/judgment/DirectJudgmentDispatcherTest.java`

**Interfaces:**
- Consumes: `AgentInvoker<T>` (existing), `AgentRef` (existing), `AgentResult` (existing), `CallerRef` (Task 2), `JudgmentContext<T>` (existing), `JudgmentResponse` (Task 2)
- Produces: `JudgmentDispatcher` SPI, `JudgmentDispatchRequest`, `DirectJudgmentDispatcher`

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.blocks.agentic.judgment;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.api.spi.judgment.Evidence;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class DirectJudgmentDispatcherTest {

    private static JudgmentContext<String> ctx() {
        return new JudgmentContext<>("state", List.of(), null, 0, null);
    }

    @Test
    void dispatchesViaAgentInvokerAndMapsStringResult() {
        var agent = AgentRef.external("judge", ctx ->
                java.util.concurrent.CompletableFuture.completedFuture(
                        new AgentResult(null, "APPROVE: looks good", Duration.ZERO,
                                AgentResult.AgentResultStatus.SUCCESS)));
        var caller = CallerRef.agent("judge-1", agent);
        var dispatcher = new DirectJudgmentDispatcher();
        var request = new JudgmentDispatchRequest(ctx(), caller, null);
        var response = dispatcher.dispatch(request);
        assertThat(response.decision()).isEqualTo("APPROVE: looks good");
    }

    @Test
    void mapsMapResult() {
        var agent = AgentRef.external("judge", ctx ->
                java.util.concurrent.CompletableFuture.completedFuture(
                        new AgentResult(null, Map.of("decision", "approve", "confidence", 0.9),
                                Duration.ZERO, AgentResult.AgentResultStatus.SUCCESS)));
        var caller = CallerRef.agent("judge-1", agent);
        var dispatcher = new DirectJudgmentDispatcher();
        var response = dispatcher.dispatch(new JudgmentDispatchRequest(ctx(), caller, null));
        assertThat(response.decision()).isEqualTo("approve");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest="DirectJudgmentDispatcherTest" -DfailIfNoTests=false`
Expected: compilation failure

- [ ] **Step 3: Implement JudgmentDispatcher, JudgmentDispatchRequest, DirectJudgmentDispatcher**

```java
// JudgmentDispatcher.java
package io.casehub.blocks.agentic.judgment;

@FunctionalInterface
public interface JudgmentDispatcher {
    JudgmentResponse dispatch(JudgmentDispatchRequest request);
}

// JudgmentDispatchRequest.java
package io.casehub.blocks.agentic.judgment;

import org.jspecify.annotations.Nullable;

public record JudgmentDispatchRequest(
        JudgmentContext<?> context,
        CallerRef caller,
        @Nullable String feedback) {}

// DirectJudgmentDispatcher.java
package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.api.spi.judgment.Evidence;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.model.AgentInvoker;

import java.util.List;
import java.util.Map;

public final class DirectJudgmentDispatcher implements JudgmentDispatcher {

    @Override
    public JudgmentResponse dispatch(JudgmentDispatchRequest request) {
        AgentRef agentRef = request.caller().agentRef();
        if (agentRef == null) {
            throw new IllegalArgumentException(
                    "DirectJudgmentDispatcher requires CallerRef with agentRef");
        }

        AgentInvoker<Object> invoker = AgentInvoker.defaultInvoker();
        AgentResult result = invoker.invoke(agentRef, request.context().executionContext())
                                     .await().indefinitely();

        return mapResult(result, request.caller());
    }

    @SuppressWarnings("unchecked")
    private JudgmentResponse mapResult(AgentResult result, CallerRef caller) {
        var callerIdentity = CallerIdentity.of(caller.id(), "agent");
        Object output = result.output();

        if (output instanceof JudgmentResponse jr) {
            return jr;
        }
        if (output instanceof Map<?, ?> map) {
            Object decision = map.get("decision");
            return new JudgmentResponse(decision, List.of(), callerIdentity);
        }
        return new JudgmentResponse(output, List.of(), callerIdentity);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest="DirectJudgmentDispatcherTest" -DfailIfNoTests=false`
Expected: 2 tests PASS

- [ ] **Step 5: Commit**

`feat(#170): add JudgmentDispatcher SPI and DirectJudgmentDispatcher  Refs #171`

---

## Batch 3: JudgmentPolicy Composition Root

### Task 5: ConsensusAgreement

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/ConsensusAgreement.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/judgment/ConsensusAgreementTest.java`

**Interfaces:**
- Consumes: `AgreementPolicy` (Task 2), `JudgmentResponse` (Task 2)
- Produces: `ConsensusAgreement` with `ConsensusMode` (UNANIMOUS, MAJORITY, THRESHOLD)

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.CallerIdentity;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConsensusAgreementTest {

    private static JudgmentResponse resp(String decision) {
        return new JudgmentResponse(decision, List.of(), CallerIdentity.of("test", "agent"));
    }

    @Test
    void unanimous_allAgree_returnsAgreed() {
        var policy = ConsensusAgreement.unanimous();
        var result = policy.evaluate(List.of(resp("yes"), resp("yes"), resp("yes")));
        assertThat(result).isInstanceOf(AgreementResult.Agreed.class);
    }

    @Test
    void unanimous_oneDisagrees_returnsDisagreed() {
        var policy = ConsensusAgreement.unanimous();
        var result = policy.evaluate(List.of(resp("yes"), resp("no"), resp("yes")));
        assertThat(result).isInstanceOf(AgreementResult.Disagreed.class);
    }

    @Test
    void majority_moreThanHalf_returnsAgreed() {
        var policy = ConsensusAgreement.majority();
        var result = policy.evaluate(List.of(resp("yes"), resp("yes"), resp("no")));
        assertThat(result).isInstanceOf(AgreementResult.Agreed.class);
    }

    @Test
    void majority_noMajority_returnsDisagreed() {
        var policy = ConsensusAgreement.majority();
        var result = policy.evaluate(List.of(resp("a"), resp("b"), resp("c")));
        assertThat(result).isInstanceOf(AgreementResult.Disagreed.class);
    }

    @Test
    void threshold_meetsThreshold_returnsAgreed() {
        var policy = ConsensusAgreement.threshold(2);
        var result = policy.evaluate(List.of(resp("yes"), resp("yes"), resp("no")));
        assertThat(result).isInstanceOf(AgreementResult.Agreed.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl blocks -Dtest="ConsensusAgreementTest" -DfailIfNoTests=false`
Expected: compilation failure

- [ ] **Step 3: Implement ConsensusAgreement**

```java
package io.casehub.blocks.agentic.judgment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ConsensusAgreement implements AgreementPolicy {

    private final int requiredAgreement;
    private final ConsensusMode mode;

    public enum ConsensusMode { UNANIMOUS, MAJORITY, THRESHOLD }

    private ConsensusAgreement(int requiredAgreement, ConsensusMode mode) {
        this.requiredAgreement = requiredAgreement;
        this.mode = mode;
    }

    public static ConsensusAgreement unanimous() {
        return new ConsensusAgreement(0, ConsensusMode.UNANIMOUS);
    }

    public static ConsensusAgreement majority() {
        return new ConsensusAgreement(0, ConsensusMode.MAJORITY);
    }

    public static ConsensusAgreement threshold(int required) {
        return new ConsensusAgreement(required, ConsensusMode.THRESHOLD);
    }

    @Override
    public AgreementResult evaluate(List<JudgmentResponse> responses) {
        Map<String, List<JudgmentResponse>> grouped = responses.stream()
                .collect(Collectors.groupingBy(r -> String.valueOf(r.decision())));

        var largest = grouped.entrySet().stream()
                .max(Map.Entry.comparingByValue((a, b) -> a.size() - b.size()))
                .orElse(null);

        if (largest == null) {
            return new AgreementResult.Disagreed("No responses");
        }

        int count = largest.getValue().size();
        boolean agreed = switch (mode) {
            case UNANIMOUS -> count == responses.size();
            case MAJORITY -> count > responses.size() / 2;
            case THRESHOLD -> count >= requiredAgreement;
        };

        if (agreed) {
            return new AgreementResult.Agreed(largest.getValue().get(0));
        }
        return new AgreementResult.Disagreed(
                count + " of " + responses.size() + " agreed (mode: " + mode + ")");
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl blocks -Dtest="ConsensusAgreementTest" -DfailIfNoTests=false`
Expected: 5 tests PASS

- [ ] **Step 5: Commit**

`feat(#170): add ConsensusAgreement — M-of-N agreement policy  Refs #172`

### Task 6: JudgmentPolicy — the composition root

**Files:**
- Create: `blocks/src/main/java/io/casehub/blocks/agentic/judgment/JudgmentPolicy.java`
- Test: `blocks/src/test/java/io/casehub/blocks/agentic/judgment/JudgmentPolicyTest.java`

**Interfaces:**
- Consumes: `JudgmentPhase<T>` (existing), `JudgmentTrigger<T>` (Task 1), `CallerStrategy` (Task 2), `JudgmentDispatcher` (Task 4), `JudgmentVerifier` (engine-api), `RetryPolicy` (Task 2), `VerifierFailurePolicy` (Task 2), `CompositeVerifier` (Task 3), `AgreementPolicy` (Task 2)
- Produces: `JudgmentPolicy<T> implements JudgmentPhase<T>` — the composition root

- [ ] **Step 1: Write failing tests — trigger skips judgment**

```java
package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.api.spi.judgment.VerificationResult;
import io.casehub.blocks.agentic.AgentRef;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class JudgmentPolicyTest {

    private static JudgmentContext<String> ctx(int iteration) {
        return new JudgmentContext<>("state", List.of(), null, iteration, null);
    }

    private static JudgmentDispatcher approvingDispatcher() {
        return req -> new JudgmentResponse("approved", List.of(),
                CallerIdentity.of("test", "agent"));
    }

    @Test
    void triggerReturnsFalse_returnsApprovedSkipped() {
        var policy = JudgmentPolicy.<String>builder()
                .trigger(new NeverYield<>())
                .caller(CallerStrategy.single())
                .verifier(new NoOpVerifier())
                .dispatcher(approvingDispatcher())
                .retryPolicy(RetryPolicy.defaults())
                .build();
        var decision = policy.evaluate(ctx(0));
        assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl blocks -Dtest="JudgmentPolicyTest" -DfailIfNoTests=false`
Expected: compilation failure — `JudgmentPolicy` does not exist

- [ ] **Step 3: Implement JudgmentPolicy with builder**

```java
package io.casehub.blocks.agentic.judgment;

import io.casehub.api.spi.judgment.CallerIdentity;
import io.casehub.api.spi.judgment.Evidence;
import io.casehub.api.spi.judgment.JudgmentVerifier;
import io.casehub.api.spi.judgment.VerificationContext;
import io.casehub.api.spi.judgment.VerificationResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class JudgmentPolicy<T> implements JudgmentPhase<T> {

    private final JudgmentTrigger<T> trigger;
    private final CallerStrategy callerStrategy;
    private final JudgmentVerifier verifier;
    private final RetryPolicy retryPolicy;
    private final JudgmentDispatcher dispatcher;
    private final Duration dispatchTimeout;

    private JudgmentPolicy(Builder<T> b) {
        this.trigger = Objects.requireNonNull(b.trigger, "trigger");
        this.callerStrategy = Objects.requireNonNull(b.callerStrategy, "callerStrategy");
        this.verifier = Objects.requireNonNull(b.verifier, "verifier");
        this.retryPolicy = Objects.requireNonNull(b.retryPolicy, "retryPolicy");
        this.dispatcher = Objects.requireNonNull(b.dispatcher, "dispatcher");
        this.dispatchTimeout = b.dispatchTimeout != null ? b.dispatchTimeout : Duration.ofMinutes(5);
    }

    public static <T> Builder<T> builder() { return new Builder<>(); }

    @Override
    public JudgmentDecision evaluate(JudgmentContext<T> context) {
        if (!trigger.shouldYield(context)) {
            return new JudgmentDecision.Approved("skipped", List.of(), CallerIdentity.of("system", "system"));
        }

        return switch (callerStrategy) {
            case CallerStrategy.Single single -> evaluateSingle(single, context);
            case CallerStrategy.FanOut fanOut -> evaluateFanOut(fanOut, context);
            case CallerStrategy.EscalationChain chain -> evaluateChain(chain, context);
        };
    }

    private JudgmentDecision evaluateSingle(CallerStrategy.Single single,
                                             JudgmentContext<T> context) {
        return dispatchAndVerify(single.caller(), context, 0);
    }

    private JudgmentDecision evaluateFanOut(CallerStrategy.FanOut fanOut,
                                             JudgmentContext<T> context) {
        String feedback = context.previousFeedback();
        for (int attempt = 0; attempt <= retryPolicy.maxRetries(); attempt++) {
            var responses = new ArrayList<JudgmentResponse>();
            for (var caller : fanOut.callers()) {
                var response = dispatcher.dispatch(
                        new JudgmentDispatchRequest(context, caller, feedback));
                responses.add(response);
            }

            var agreement = fanOut.agreementPolicy().evaluate(responses);
            if (agreement instanceof AgreementResult.Disagreed disagreed) {
                feedback = disagreed.reason();
                continue;
            }

            var agreed = ((AgreementResult.Agreed) agreement).selectedResponse();
            var verificationResult = verify(agreed, context);
            if (verificationResult instanceof VerificationResult.Accepted) {
                return new JudgmentDecision.Approved(agreed.decision(),
                        agreed.evidence(), agreed.callerIdentity());
            }
            feedback = extractFeedback(verificationResult);
        }
        return applyExhaustion(context);
    }

    private JudgmentDecision evaluateChain(CallerStrategy.EscalationChain chain,
                                            JudgmentContext<T> context) {
        for (var caller : chain.callers()) {
            var decision = dispatchAndVerify(caller, context, 0);
            if (decision instanceof JudgmentDecision.Approved) {
                return decision;
            }
        }
        return applyExhaustion(context);
    }

    private JudgmentDecision dispatchAndVerify(CallerRef caller, JudgmentContext<T> context,
                                                int callerIndex) {
        String feedback = context.previousFeedback();
        for (int attempt = 0; attempt <= retryPolicy.maxRetries(); attempt++) {
            var response = dispatcher.dispatch(
                    new JudgmentDispatchRequest(context, caller, feedback));

            var verificationResult = verify(response, context);
            if (verificationResult instanceof VerificationResult.Accepted) {
                return new JudgmentDecision.Approved(response.decision(),
                        response.evidence(), response.callerIdentity());
            }

            var failurePolicy = resolveFailurePolicy();
            if (failurePolicy == VerifierFailurePolicy.FAIL) {
                return new JudgmentDecision.Rejected(extractFeedback(verificationResult),
                        response.evidence(), response.callerIdentity());
            }
            if (failurePolicy == VerifierFailurePolicy.ESCALATE) {
                break;
            }
            feedback = extractFeedback(verificationResult);
        }
        return applyExhaustion(context);
    }

    private VerificationResult verify(JudgmentResponse response, JudgmentContext<T> context) {
        var verificationCtx = new VerificationContext(
                null, "", "", null, java.util.Map.of(), null,
                String.valueOf(response.decision()), response.evidence(),
                response.callerIdentity(), null);
        return verifier.verify(verificationCtx);
    }

    private VerifierFailurePolicy resolveFailurePolicy() {
        if (verifier instanceof CompositeVerifier composite) {
            return composite.mostRestrictivePolicy();
        }
        return VerifierFailurePolicy.RETRY_WITH_FEEDBACK;
    }

    private JudgmentDecision applyExhaustion(JudgmentContext<T> context) {
        return switch (retryPolicy.exhaustionPolicy()) {
            case FAIL -> new JudgmentDecision.Rejected("Judgment exhausted retries",
                    List.of(), CallerIdentity.of("system", "system"));
            case ESCALATE_NEXT_CALLER -> new JudgmentDecision.Rejected(
                    "Escalation chain exhausted", List.of(), CallerIdentity.of("system", "system"));
            case TERMINATE_PATTERN -> new JudgmentDecision.Escalated(
                    "Judgment terminated pattern", CallerIdentity.of("system", "system"));
        };
    }

    private String extractFeedback(VerificationResult result) {
        return switch (result) {
            case VerificationResult.Rejected r -> r.reason();
            case VerificationResult.InsufficientEvidence ie -> ie.feedback();
            case VerificationResult.TrustTooLow t ->
                    "Trust too low: required=" + t.requiredLevel() + " actual=" + t.actualLevel();
            case VerificationResult.Accepted a -> "";
        };
    }

    public static final class Builder<T> {
        private JudgmentTrigger<T> trigger;
        private CallerStrategy callerStrategy;
        private JudgmentVerifier verifier;
        private RetryPolicy retryPolicy;
        private JudgmentDispatcher dispatcher;
        private Duration dispatchTimeout;

        public Builder<T> trigger(JudgmentTrigger<T> trigger) { this.trigger = trigger; return this; }
        public Builder<T> caller(CallerStrategy callerStrategy) { this.callerStrategy = callerStrategy; return this; }
        public Builder<T> verifier(JudgmentVerifier verifier) { this.verifier = verifier; return this; }
        public Builder<T> retryPolicy(RetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; return this; }
        public Builder<T> dispatcher(JudgmentDispatcher dispatcher) { this.dispatcher = dispatcher; return this; }
        public Builder<T> dispatchTimeout(Duration timeout) { this.dispatchTimeout = timeout; return this; }

        public JudgmentPolicy<T> build() { return new JudgmentPolicy<>(this); }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl blocks -Dtest="JudgmentPolicyTest" -DfailIfNoTests=false`
Expected: 1 test PASS

- [ ] **Step 5: Add tests for dispatch + verify cycle, retry, and escalation**

```java
@Test
void singleCaller_dispatchesAndVerifies() {
    var policy = JudgmentPolicy.<String>builder()
            .trigger(new AlwaysYield<>())
            .caller(CallerStrategy.single(CallerRef.agent("j", null)))
            .verifier(new NoOpVerifier())
            .dispatcher(approvingDispatcher())
            .retryPolicy(RetryPolicy.defaults())
            .build();
    var decision = policy.evaluate(ctx(0));
    assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
    assertThat(((JudgmentDecision.Approved) decision).result()).isEqualTo("approved");
}

@Test
void verificationRejected_retriesWithFeedback() {
    var callCount = new java.util.concurrent.atomic.AtomicInteger(0);
    JudgmentDispatcher dispatcher = req -> {
        callCount.incrementAndGet();
        return new JudgmentResponse("result", List.of(), CallerIdentity.of("t", "a"));
    };
    JudgmentVerifier rejectThenAccept = new JudgmentVerifier() {
        private int calls = 0;
        @Override public String id() { return "test"; }
        @Override public io.casehub.api.spi.judgment.VerificationResult verify(
                io.casehub.api.spi.judgment.VerificationContext ctx) {
            return ++calls <= 2
                    ? new io.casehub.api.spi.judgment.VerificationResult.Rejected("not yet")
                    : new io.casehub.api.spi.judgment.VerificationResult.Accepted();
        }
    };
    var policy = JudgmentPolicy.<String>builder()
            .trigger(new AlwaysYield<>())
            .caller(CallerStrategy.single(CallerRef.agent("j", null)))
            .verifier(rejectThenAccept)
            .dispatcher(dispatcher)
            .retryPolicy(new RetryPolicy(3, ExhaustionPolicy.FAIL))
            .build();
    var decision = policy.evaluate(ctx(0));
    assertThat(decision).isInstanceOf(JudgmentDecision.Approved.class);
    assertThat(callCount.get()).isEqualTo(3);
}

@Test
void retriesExhausted_returnRejected() {
    JudgmentVerifier alwaysReject = new JudgmentVerifier() {
        @Override public String id() { return "reject"; }
        @Override public io.casehub.api.spi.judgment.VerificationResult verify(
                io.casehub.api.spi.judgment.VerificationContext ctx) {
            return new io.casehub.api.spi.judgment.VerificationResult.Rejected("bad");
        }
    };
    var policy = JudgmentPolicy.<String>builder()
            .trigger(new AlwaysYield<>())
            .caller(CallerStrategy.single(CallerRef.agent("j", null)))
            .verifier(alwaysReject)
            .dispatcher(approvingDispatcher())
            .retryPolicy(new RetryPolicy(2, ExhaustionPolicy.FAIL))
            .build();
    var decision = policy.evaluate(ctx(0));
    assertThat(decision).isInstanceOf(JudgmentDecision.Rejected.class);
}

@Test
void terminatePattern_exhaustion_returnsEscalated() {
    JudgmentVerifier alwaysReject = new JudgmentVerifier() {
        @Override public String id() { return "reject"; }
        @Override public io.casehub.api.spi.judgment.VerificationResult verify(
                io.casehub.api.spi.judgment.VerificationContext ctx) {
            return new io.casehub.api.spi.judgment.VerificationResult.Rejected("bad");
        }
    };
    var policy = JudgmentPolicy.<String>builder()
            .trigger(new AlwaysYield<>())
            .caller(CallerStrategy.single(CallerRef.agent("j", null)))
            .verifier(alwaysReject)
            .dispatcher(approvingDispatcher())
            .retryPolicy(new RetryPolicy(0, ExhaustionPolicy.TERMINATE_PATTERN))
            .build();
    var decision = policy.evaluate(ctx(0));
    assertThat(decision).isInstanceOf(JudgmentDecision.Escalated.class);
}
```

- [ ] **Step 6: Run all JudgmentPolicy tests**

Run: `mvn --batch-mode test -pl blocks -Dtest="JudgmentPolicyTest" -DfailIfNoTests=false`
Expected: 5 tests PASS

- [ ] **Step 7: Run full module test suite**

Run: `mvn --batch-mode test -pl blocks`
Expected: all existing tests still pass + new tests pass

- [ ] **Step 8: Commit**

`feat(#170): add JudgmentPolicy — composition root implementing JudgmentPhase  Refs #170`

---

## References

- [2026-08-29-governed-yield-agentic-design.md] — design spec this plan implements
- `blocks/src/main/java/io/casehub/blocks/agentic/judgment/JudgmentPhase.java` — SPI we implement
- `blocks/src/main/java/io/casehub/blocks/agentic/judgment/JudgmentDecision.java` — sealed return type
- `blocks/src/main/java/io/casehub/blocks/agentic/judgment/JudgmentContext.java` — context record
- `blocks/src/main/java/io/casehub/blocks/agentic/model/AgentInvoker.java` — used by DirectJudgmentDispatcher
- `blocks/src/main/java/io/casehub/blocks/agentic/model/AbstractExecutionDriver.java:136-150` — existing phase 3.5 wiring
- `blocks/src/main/java/io/casehub/blocks/agentic/pattern/AbstractPatternBuilder.java:128-131` — existing `.judgment()` method
- Engine-api `io.casehub.api.spi.judgment.JudgmentVerifier` — engine#997
- Engine-api `io.casehub.api.spi.judgment.VerificationResult` — sealed verification outcomes
- GitHub blocks#170, #171, #172, #173

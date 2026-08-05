# Trust SPIs + FewShotOptimiser Diversity Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #79 — LifecycleAttestationObserver
**Issue group:** #78, #79, #80, #81

**Goal:** Add three trust-lifecycle SPIs (attestation observer, intake classifier, vouch service) and a diversity-aware selection strategy for the FewShotOptimiser.

**Architecture:** Two independent workstreams on one branch. The trust SPIs split across two packages: `blocks.attestation` (observer + existing write-path types) and `blocks.trust` (intake classifier, vouch service). The diversity enhancement adds a `DiversityStrategy` SPI to `blocks.prompt` with `TopNDiversityStrategy` (identity) and `OutcomeAwareDiversityStrategy` (MMR) in `blocks.prompt.optimiser`.

**Tech Stack:** Java 21, JUnit 5, Mockito, AssertJ. No new dependencies.

## Global Constraints

- No Quarkus runtime — plain JUnit 5 tests with Mockito. No CDI container in tests.
- All records use jspecify `@Nullable` annotations where applicable.
- Compact constructors validate inputs (ranges, non-null).
- `mvn --batch-mode test` must pass after every task.

---

### Task 1: Commit existing attestation types + add causedByEntryId

**Files:**
- Modify: `src/main/java/io/casehub/blocks/attestation/AttestationIntent.java`
- Existing: `src/main/java/io/casehub/blocks/attestation/AttestationIntentWriter.java` (commit as-is)
- Test: `src/test/java/io/casehub/blocks/attestation/AttestationIntentTest.java`

**Interfaces:**
- Consumes: `AttestationVerdict` (ledger-api), `ActorType` (platform-api)
- Produces: `AttestationIntent` record with 12 fields including `@Nullable UUID causedByEntryId`. `AttestationIntentWriter` SPI with `void write(AttestationIntent, String)`.

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.blocks.attestation;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttestationIntentTest {

    @Test
    void causedByEntryIdIsNullable() {
        var intent = new AttestationIntent(
                UUID.randomUUID(), UUID.randomUUID(),
                AttestationVerdict.SOUND, 0.9, "routing",
                "actor-1", ActorType.WORKER, "analyst",
                Map.of("accuracy", 0.95), "evidence text",
                UUID.randomUUID(), null);
        assertThat(intent.causedByEntryId()).isNull();
    }

    @Test
    void causedByEntryIdIsCarriedThrough() {
        var causedBy = UUID.randomUUID();
        var intent = new AttestationIntent(
                UUID.randomUUID(), UUID.randomUUID(),
                AttestationVerdict.SOUND, 0.9, "routing",
                "actor-1", ActorType.WORKER, "analyst",
                Map.of(), "evidence",
                UUID.randomUUID(), causedBy);
        assertThat(intent.causedByEntryId()).isEqualTo(causedBy);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=AttestationIntentTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `AttestationIntent` has 11 fields, constructor expects 11 args, test passes 12.

- [ ] **Step 3: Add causedByEntryId to AttestationIntent**

Modify `src/main/java/io/casehub/blocks/attestation/AttestationIntent.java`:

```java
package io.casehub.blocks.attestation;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public record AttestationIntent(
        UUID entryId,
        UUID subjectId,
        AttestationVerdict verdict,
        double confidence,
        String capabilityTag,
        String attestorId,
        ActorType actorType,
        String attestorRole,
        Map<String, Double> dimensions,
        String evidence,
        UUID namespace,
        @Nullable UUID causedByEntryId) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=AttestationIntentTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/casehub/blocks/attestation/ src/test/java/io/casehub/blocks/attestation/
git commit -m "feat(#79): commit attestation types and add causedByEntryId to AttestationIntent"
```

---

### Task 2: LifecycleAttestationObserver + AttestationContext

**Files:**
- Create: `src/main/java/io/casehub/blocks/attestation/LifecycleAttestationObserver.java`
- Create: `src/main/java/io/casehub/blocks/attestation/AttestationContext.java`
- Test: `src/test/java/io/casehub/blocks/attestation/LifecycleAttestationObserverTest.java`

**Interfaces:**
- Consumes: `AttestationIntent` (Task 1)
- Produces: `LifecycleAttestationObserver<E>` `@FunctionalInterface` with `List<AttestationIntent> observe(E, AttestationContext)`. `AttestationContext(String tenancyId, UUID caseId, String capabilityTag)`.

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.blocks.attestation;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleAttestationObserverTest {

    record TestEvent(String type, String subjectId) {}

    @Test
    void observerProducesIntentsFromEvent() {
        LifecycleAttestationObserver<TestEvent> observer = (event, ctx) -> List.of(
                new AttestationIntent(
                        UUID.randomUUID(), UUID.fromString(event.subjectId()),
                        AttestationVerdict.SOUND, 0.9, ctx.capabilityTag(),
                        "system", ActorType.WORKER, "observer",
                        Map.of("accuracy", 1.0), event.type(),
                        UUID.randomUUID(), null));

        var ctx = new AttestationContext("tenant-1", UUID.randomUUID(), "routing");
        var intents = observer.observe(new TestEvent("MERGED", "00000000-0000-0000-0000-000000000001"), ctx);

        assertThat(intents).hasSize(1);
        assertThat(intents.getFirst().verdict()).isEqualTo(AttestationVerdict.SOUND);
        assertThat(intents.getFirst().capabilityTag()).isEqualTo("routing");
        assertThat(intents.getFirst().evidence()).isEqualTo("MERGED");
    }

    @Test
    void observerCanReturnEmptyListForIrrelevantEvent() {
        LifecycleAttestationObserver<TestEvent> observer = (event, ctx) -> List.of();

        var ctx = new AttestationContext("tenant-1", UUID.randomUUID(), "routing");
        var intents = observer.observe(new TestEvent("IGNORED", "00000000-0000-0000-0000-000000000002"), ctx);

        assertThat(intents).isEmpty();
    }

    @Test
    void observerCanProduceMultipleIntentsFromSingleEvent() {
        LifecycleAttestationObserver<TestEvent> observer = (event, ctx) -> List.of(
                new AttestationIntent(UUID.randomUUID(), UUID.fromString(event.subjectId()),
                        AttestationVerdict.SOUND, 1.0, ctx.capabilityTag(),
                        "system", ActorType.WORKER, "observer",
                        Map.of("merge-rate", 1.0), "merged", UUID.randomUUID(), null),
                new AttestationIntent(UUID.randomUUID(), UUID.fromString(event.subjectId()),
                        AttestationVerdict.SOUND, 1.0, ctx.capabilityTag(),
                        "system", ActorType.WORKER, "observer",
                        Map.of("first-attempt-quality", 1.0), "merged", UUID.randomUUID(), null));

        var ctx = new AttestationContext("tenant-1", UUID.randomUUID(), "routing");
        var intents = observer.observe(new TestEvent("MERGED", "00000000-0000-0000-0000-000000000001"), ctx);

        assertThat(intents).hasSize(2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=LifecycleAttestationObserverTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `LifecycleAttestationObserver` and `AttestationContext` do not exist.

- [ ] **Step 3: Create AttestationContext**

```java
package io.casehub.blocks.attestation;

import java.util.Objects;
import java.util.UUID;

public record AttestationContext(
        String tenancyId,
        UUID caseId,
        String capabilityTag) {

    public AttestationContext {
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(capabilityTag, "capabilityTag");
    }
}
```

- [ ] **Step 4: Create LifecycleAttestationObserver**

```java
package io.casehub.blocks.attestation;

import java.util.List;

@FunctionalInterface
public interface LifecycleAttestationObserver<E> {
    List<AttestationIntent> observe(E event, AttestationContext context);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=LifecycleAttestationObserverTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/blocks/attestation/LifecycleAttestationObserver.java src/main/java/io/casehub/blocks/attestation/AttestationContext.java src/test/java/io/casehub/blocks/attestation/LifecycleAttestationObserverTest.java
git commit -m "feat(#79): add LifecycleAttestationObserver SPI and AttestationContext"
```

---

### Task 3: IntakeClassifier + IntakeContext + IntakeResult

**Files:**
- Create: `src/main/java/io/casehub/blocks/trust/IntakeClassifier.java`
- Create: `src/main/java/io/casehub/blocks/trust/IntakeContext.java`
- Create: `src/main/java/io/casehub/blocks/trust/IntakeResult.java`
- Test: `src/test/java/io/casehub/blocks/trust/IntakeClassifierTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `IntakeClassifier<S>` `@FunctionalInterface` with `IntakeResult classify(S, IntakeContext)`. `IntakeContext(String tenancyId, @Nullable String capabilityTag, Map<String, Object> attributes)`. `IntakeResult(String lane, double confidence, String reason, Map<String, Object> metadata)`.

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.blocks.trust;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntakeClassifierTest {

    record TestSubject(String id, double trustScore) {}

    @Test
    void classifierReturnsResultForSubject() {
        IntakeClassifier<TestSubject> classifier = (subject, ctx) ->
                new IntakeResult(
                        subject.trustScore() >= 0.75 ? "FAST_TRACK" : "STANDARD",
                        subject.trustScore(),
                        "trust-based classification",
                        Map.of("trustScore", subject.trustScore()));

        var result = classifier.classify(
                new TestSubject("sub-1", 0.85),
                new IntakeContext("tenant-1"));

        assertThat(result.lane()).isEqualTo("FAST_TRACK");
        assertThat(result.confidence()).isEqualTo(0.85);
        assertThat(result.reason()).isEqualTo("trust-based classification");
        assertThat(result.metadata()).containsEntry("trustScore", 0.85);
    }

    @Test
    void intakeContextCompactConstructorDefaultsAttributes() {
        var ctx = new IntakeContext("tenant-1");
        assertThat(ctx.tenancyId()).isEqualTo("tenant-1");
        assertThat(ctx.capabilityTag()).isNull();
        assertThat(ctx.attributes()).isEmpty();
    }

    @Test
    void intakeResultCompactConstructorDefaultsMetadata() {
        var result = new IntakeResult("TRIAGE", 0.3, "low trust");
        assertThat(result.metadata()).isEmpty();
    }

    @Test
    void intakeResultRejectsConfidenceOutOfRange() {
        assertThatThrownBy(() -> new IntakeResult("FAST_TRACK", 1.5, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IntakeResult("FAST_TRACK", -0.1, "bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void intakeContextPassesAttributesToClassifier() {
        IntakeClassifier<String> classifier = (subject, ctx) -> {
            double score = (double) ctx.attributes().get("trustScore");
            return new IntakeResult(score > 0.5 ? "STANDARD" : "TRIAGE", score, "from attributes");
        };

        var ctx = new IntakeContext("tenant-1", "routing", Map.of("trustScore", 0.6));
        var result = classifier.classify("subject-1", ctx);

        assertThat(result.lane()).isEqualTo("STANDARD");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=IntakeClassifierTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `IntakeClassifier`, `IntakeContext`, `IntakeResult` do not exist.

- [ ] **Step 3: Create IntakeContext**

```java
package io.casehub.blocks.trust;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public record IntakeContext(
        String tenancyId,
        @Nullable String capabilityTag,
        Map<String, Object> attributes) {

    public IntakeContext {
        Objects.requireNonNull(tenancyId, "tenancyId");
        if (attributes == null) attributes = Map.of();
    }

    public IntakeContext(String tenancyId) {
        this(tenancyId, null, Map.of());
    }
}
```

- [ ] **Step 4: Create IntakeResult**

```java
package io.casehub.blocks.trust;

import java.util.Map;
import java.util.Objects;

public record IntakeResult(
        String lane,
        double confidence,
        String reason,
        Map<String, Object> metadata) {

    public IntakeResult {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(reason, "reason");
        if (confidence < 0 || confidence > 1)
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        if (metadata == null) metadata = Map.of();
    }

    public IntakeResult(String lane, double confidence, String reason) {
        this(lane, confidence, reason, Map.of());
    }
}
```

- [ ] **Step 5: Create IntakeClassifier**

```java
package io.casehub.blocks.trust;

@FunctionalInterface
public interface IntakeClassifier<S> {
    IntakeResult classify(S subject, IntakeContext context);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=IntakeClassifierTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/casehub/blocks/trust/ src/test/java/io/casehub/blocks/trust/
git commit -m "feat(#80): add IntakeClassifier SPI, IntakeContext, and IntakeResult"
```

---

### Task 4: VouchService + VouchConstraint + VouchRequest + VouchResult + VouchEligibility

**Files:**
- Create: `src/main/java/io/casehub/blocks/trust/VouchConstraint.java`
- Create: `src/main/java/io/casehub/blocks/trust/VouchEligibility.java`
- Create: `src/main/java/io/casehub/blocks/trust/VouchRequest.java`
- Create: `src/main/java/io/casehub/blocks/trust/VouchResult.java`
- Create: `src/main/java/io/casehub/blocks/trust/VouchService.java`
- Test: `src/test/java/io/casehub/blocks/trust/VouchServiceTest.java`

**Interfaces:**
- Consumes: `AttestationIntent`, `AttestationIntentWriter` (Task 1), `AttestationVerdict` (ledger-api), `ActorType` (platform-api)
- Produces: `VouchService(List<VouchConstraint>, AttestationIntentWriter)` with `VouchResult vouch(VouchRequest)`. `VouchResult` sealed: `Accepted(UUID)`, `Rejected(List<String>)`. `VouchEligibility` sealed: `Eligible()`, `Ineligible(String)`. `VouchRequest(String voucherId, UUID voucheeId, String capabilityTag, String tenancyId, ActorType voucherActorType, String voucherRole, @Nullable UUID namespace, Map<String, Object> attributes)`.

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.blocks.trust;

import io.casehub.blocks.attestation.AttestationIntent;
import io.casehub.blocks.attestation.AttestationIntentWriter;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VouchServiceTest {

    private final AttestationIntentWriter writer = mock(AttestationIntentWriter.class);

    private VouchRequest request() {
        return new VouchRequest("voucher-1", UUID.randomUUID(), "pr-review",
                "tenant-1", ActorType.HUMAN, "senior-dev", null, Map.of());
    }

    @Test
    void allConstraintsPassWritesEndorsedAttestation() {
        var service = new VouchService(List.of(r -> new VouchEligibility.Eligible()), writer);
        var req = request();
        var result = service.vouch(req);

        assertThat(result).isInstanceOf(VouchResult.Accepted.class);
        var accepted = (VouchResult.Accepted) result;
        assertThat(accepted.attestationEntryId()).isNotNull();

        var captor = ArgumentCaptor.forClass(AttestationIntent.class);
        verify(writer).write(captor.capture(), eq("tenant-1"));
        var intent = captor.getValue();
        assertThat(intent.verdict()).isEqualTo(AttestationVerdict.ENDORSED);
        assertThat(intent.confidence()).isEqualTo(1.0);
        assertThat(intent.attestorId()).isEqualTo("voucher-1");
        assertThat(intent.subjectId()).isEqualTo(req.voucheeId());
        assertThat(intent.capabilityTag()).isEqualTo("pr-review");
        assertThat(intent.actorType()).isEqualTo(ActorType.HUMAN);
        assertThat(intent.attestorRole()).isEqualTo("senior-dev");
        assertThat(intent.dimensions()).isEmpty();
        assertThat(intent.evidence()).isEqualTo("vouch");
        assertThat(intent.entryId()).isEqualTo(accepted.attestationEntryId());
    }

    @Test
    void anyConstraintFailsReturnsRejectedWithAllReasons() {
        var constraints = List.<VouchConstraint>of(
                r -> new VouchEligibility.Ineligible("trust too low"),
                r -> new VouchEligibility.Eligible(),
                r -> new VouchEligibility.Ineligible("capacity exceeded"));
        var service = new VouchService(constraints, writer);
        var result = service.vouch(request());

        assertThat(result).isInstanceOf(VouchResult.Rejected.class);
        var rejected = (VouchResult.Rejected) result;
        assertThat(rejected.reasons()).containsExactly("trust too low", "capacity exceeded");
        verify(writer, never()).write(any(), any());
    }

    @Test
    void noConstraintsAlwaysPasses() {
        var service = new VouchService(List.of(), writer);
        var result = service.vouch(request());

        assertThat(result).isInstanceOf(VouchResult.Accepted.class);
        verify(writer).write(any(), eq("tenant-1"));
    }

    @Test
    void writerExceptionPropagates() {
        doThrow(new RuntimeException("db down"))
                .when(writer).write(any(), any());
        var service = new VouchService(List.of(), writer);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.vouch(request()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=VouchServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Create VouchEligibility**

```java
package io.casehub.blocks.trust;

public sealed interface VouchEligibility {
    record Eligible() implements VouchEligibility {}
    record Ineligible(String reason) implements VouchEligibility {}
}
```

- [ ] **Step 4: Create VouchConstraint**

```java
package io.casehub.blocks.trust;

public interface VouchConstraint {
    VouchEligibility check(VouchRequest request);
}
```

- [ ] **Step 5: Create VouchRequest**

```java
package io.casehub.blocks.trust;

import io.casehub.platform.api.identity.ActorType;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record VouchRequest(
        String voucherId,
        UUID voucheeId,
        String capabilityTag,
        String tenancyId,
        ActorType voucherActorType,
        String voucherRole,
        @Nullable UUID namespace,
        Map<String, Object> attributes) {

    public VouchRequest {
        Objects.requireNonNull(voucherId, "voucherId");
        Objects.requireNonNull(voucheeId, "voucheeId");
        Objects.requireNonNull(capabilityTag, "capabilityTag");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(voucherActorType, "voucherActorType");
        Objects.requireNonNull(voucherRole, "voucherRole");
        if (attributes == null) attributes = Map.of();
    }
}
```

- [ ] **Step 6: Create VouchResult**

```java
package io.casehub.blocks.trust;

import java.util.List;
import java.util.UUID;

public sealed interface VouchResult {
    record Accepted(UUID attestationEntryId) implements VouchResult {}
    record Rejected(List<String> reasons) implements VouchResult {}
}
```

- [ ] **Step 7: Create VouchService**

```java
package io.casehub.blocks.trust;

import io.casehub.blocks.attestation.AttestationIntent;
import io.casehub.blocks.attestation.AttestationIntentWriter;
import io.casehub.ledger.api.model.AttestationVerdict;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VouchService {

    private final List<VouchConstraint> constraints;
    private final AttestationIntentWriter writer;

    public VouchService(List<VouchConstraint> constraints, AttestationIntentWriter writer) {
        this.constraints = List.copyOf(constraints);
        this.writer = writer;
    }

    public VouchResult vouch(VouchRequest request) {
        var reasons = constraints.stream()
                .map(c -> c.check(request))
                .filter(e -> e instanceof VouchEligibility.Ineligible)
                .map(e -> ((VouchEligibility.Ineligible) e).reason())
                .toList();

        if (!reasons.isEmpty()) {
            return new VouchResult.Rejected(reasons);
        }

        var entryId = UUID.randomUUID();
        var intent = new AttestationIntent(
                entryId,
                request.voucheeId(),
                AttestationVerdict.ENDORSED,
                1.0,
                request.capabilityTag(),
                request.voucherId(),
                request.voucherActorType(),
                request.voucherRole(),
                Map.of(),
                "vouch",
                request.namespace(),
                null);

        writer.write(intent, request.tenancyId());
        return new VouchResult.Accepted(entryId);
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=VouchServiceTest`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/io/casehub/blocks/trust/Vouch*.java src/test/java/io/casehub/blocks/trust/VouchServiceTest.java
git commit -m "feat(#81): add VouchService, VouchConstraint, VouchRequest, VouchResult, VouchEligibility"
```

---

### Task 5: DiversityStrategy SPI + TopNDiversityStrategy

**Files:**
- Create: `src/main/java/io/casehub/blocks/prompt/DiversityStrategy.java`
- Create: `src/main/java/io/casehub/blocks/prompt/optimiser/TopNDiversityStrategy.java`
- Test: `src/test/java/io/casehub/blocks/prompt/optimiser/TopNDiversityStrategyTest.java`

**Interfaces:**
- Consumes: `ExampleCandidate` (existing)
- Produces: `DiversityStrategy` `@FunctionalInterface` with `List<ExampleCandidate> select(List<ExampleCandidate>, int)`. `TopNDiversityStrategy` returns first N from input.

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.DiversityStrategy;
import io.casehub.blocks.prompt.ExampleCandidate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopNDiversityStrategyTest {

    private ExampleCandidate candidate(String outcome, double quality) {
        return new ExampleCandidate("input", "output", outcome, quality, 0.8,
                "v1", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void returnsFirstNFromShortlist() {
        DiversityStrategy strategy = new TopNDiversityStrategy();
        var shortlist = List.of(
                candidate("SUCCESS", 0.9),
                candidate("SUCCESS", 0.8),
                candidate("SUCCESS", 0.7));
        var result = strategy.select(shortlist, 2);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).qualityScore()).isEqualTo(0.9);
        assertThat(result.get(1).qualityScore()).isEqualTo(0.8);
    }

    @Test
    void shortlistSmallerThanMaxReturnsAll() {
        DiversityStrategy strategy = new TopNDiversityStrategy();
        var shortlist = List.of(candidate("SUCCESS", 0.9));
        var result = strategy.select(shortlist, 5);
        assertThat(result).hasSize(1);
    }

    @Test
    void emptyShortlistReturnsEmpty() {
        DiversityStrategy strategy = new TopNDiversityStrategy();
        var result = strategy.select(List.of(), 5);
        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=TopNDiversityStrategyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `DiversityStrategy` and `TopNDiversityStrategy` do not exist.

- [ ] **Step 3: Create DiversityStrategy**

```java
package io.casehub.blocks.prompt;

import java.util.List;

@FunctionalInterface
public interface DiversityStrategy {
    List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples);
}
```

- [ ] **Step 4: Create TopNDiversityStrategy**

```java
package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.DiversityStrategy;
import io.casehub.blocks.prompt.ExampleCandidate;

import java.util.List;

public class TopNDiversityStrategy implements DiversityStrategy {

    @Override
    public List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples) {
        return shortlist.stream().limit(maxExamples).toList();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=TopNDiversityStrategyTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/blocks/prompt/DiversityStrategy.java src/main/java/io/casehub/blocks/prompt/optimiser/TopNDiversityStrategy.java src/test/java/io/casehub/blocks/prompt/optimiser/TopNDiversityStrategyTest.java
git commit -m "feat(#78): add DiversityStrategy SPI and TopNDiversityStrategy identity impl"
```

---

### Task 6: OutcomeAwareDiversityStrategy

**Files:**
- Create: `src/main/java/io/casehub/blocks/prompt/optimiser/OutcomeAwareDiversityStrategy.java`
- Test: `src/test/java/io/casehub/blocks/prompt/optimiser/OutcomeAwareDiversityStrategyTest.java`

**Interfaces:**
- Consumes: `DiversityStrategy` (Task 5), `ExampleCandidate` (existing)
- Produces: `OutcomeAwareDiversityStrategy(double diversityWeight)` implementing `DiversityStrategy`. Validates `diversityWeight` in [0, 1].

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.ExampleCandidate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutcomeAwareDiversityStrategyTest {

    private ExampleCandidate candidate(String input, String output, String outcome,
                                       double quality, double similarity) {
        return new ExampleCandidate(input, output, outcome, quality, similarity,
                "v1", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void seedsFromMultipleOutcomeCategories() {
        var strategy = new OutcomeAwareDiversityStrategy(0.5);
        var shortlist = List.of(
                candidate("a", "x", "SUCCESS", 0.9, 0.9),
                candidate("b", "y", "SUCCESS", 0.85, 0.85),
                candidate("c", "z", "FAILURE", 0.7, 0.7));
        var result = strategy.select(shortlist, 2);
        var outcomes = result.stream().map(ExampleCandidate::outcome).toList();
        assertThat(outcomes).contains("SUCCESS", "FAILURE");
    }

    @Test
    void penalisesTextuallyIdenticalCandidates() {
        var strategy = new OutcomeAwareDiversityStrategy(0.8);
        var shortlist = List.of(
                candidate("same input", "same output", "SUCCESS", 0.9, 0.9),
                candidate("same input", "same output", "SUCCESS", 0.88, 0.88),
                candidate("different input entirely", "different output", "SUCCESS", 0.7, 0.7));
        var result = strategy.select(shortlist, 2);
        assertThat(result).anyMatch(c -> c.input().equals("different input entirely"));
    }

    @Test
    void zeroWeightDegeneratesToPureRelevance() {
        var strategy = new OutcomeAwareDiversityStrategy(0.0);
        var shortlist = List.of(
                candidate("a", "x", "SUCCESS", 0.9, 0.9),
                candidate("a", "x", "SUCCESS", 0.8, 0.8),
                candidate("b", "y", "FAILURE", 0.5, 0.5));
        var result = strategy.select(shortlist, 2);
        assertThat(result.get(0).qualityScore()).isEqualTo(0.9);
        assertThat(result.get(1).qualityScore()).isEqualTo(0.8);
    }

    @Test
    void emptyShortlistReturnsEmpty() {
        var strategy = new OutcomeAwareDiversityStrategy(0.5);
        assertThat(strategy.select(List.of(), 5)).isEmpty();
    }

    @Test
    void shortlistSmallerThanMaxReturnsAll() {
        var strategy = new OutcomeAwareDiversityStrategy(0.5);
        var shortlist = List.of(candidate("a", "x", "SUCCESS", 0.9, 0.9));
        assertThat(strategy.select(shortlist, 5)).hasSize(1);
    }

    @Test
    void rejectsWeightOutOfRange() {
        assertThatThrownBy(() -> new OutcomeAwareDiversityStrategy(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutcomeAwareDiversityStrategy(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handlesEmptyInputOutputWithoutCrashing() {
        var strategy = new OutcomeAwareDiversityStrategy(0.5);
        var shortlist = List.of(
                candidate("", "", "SUCCESS", 0.9, 0.9),
                candidate("", "", "FAILURE", 0.8, 0.8),
                candidate("some text", "other text", "SUCCESS", 0.7, 0.7));
        var result = strategy.select(shortlist, 2);
        assertThat(result).hasSize(2);
    }

    @Test
    void outcomeGroupingIsCaseInsensitive() {
        var strategy = new OutcomeAwareDiversityStrategy(0.5);
        var shortlist = List.of(
                candidate("a", "x", "SUCCESS", 0.9, 0.9),
                candidate("b", "y", "success", 0.85, 0.85),
                candidate("c", "z", "FAILURE", 0.7, 0.7));
        var result = strategy.select(shortlist, 2);
        var outcomes = result.stream().map(c -> c.outcome().toUpperCase()).toList();
        assertThat(outcomes).contains("SUCCESS", "FAILURE");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl . -Dtest=OutcomeAwareDiversityStrategyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `OutcomeAwareDiversityStrategy` does not exist.

- [ ] **Step 3: Create OutcomeAwareDiversityStrategy**

```java
package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.DiversityStrategy;
import io.casehub.blocks.prompt.ExampleCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class OutcomeAwareDiversityStrategy implements DiversityStrategy {

    private final double diversityWeight;

    public OutcomeAwareDiversityStrategy(double diversityWeight) {
        if (diversityWeight < 0 || diversityWeight > 1)
            throw new IllegalArgumentException("diversityWeight must be in [0, 1]");
        this.diversityWeight = diversityWeight;
    }

    @Override
    public List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples) {
        if (shortlist.isEmpty() || maxExamples <= 0) return List.of();
        if (shortlist.size() <= maxExamples) return shortlist;

        var selected = new ArrayList<ExampleCandidate>();

        // Seed: one candidate per outcome group (highest relevance from each)
        var byOutcome = new LinkedHashMap<String, List<ExampleCandidate>>();
        for (var c : shortlist) {
            byOutcome.computeIfAbsent(c.outcome().toLowerCase(), k -> new ArrayList<>()).add(c);
        }
        for (var group : byOutcome.values()) {
            if (selected.size() >= maxExamples) break;
            group.stream()
                    .max(Comparator.comparingDouble(c -> c.qualityScore() * c.similarityScore()))
                    .ifPresent(selected::add);
        }

        // Fill remaining via MMR
        var remaining = new ArrayList<>(shortlist);
        remaining.removeAll(selected);

        while (selected.size() < maxExamples && !remaining.isEmpty()) {
            ExampleCandidate best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (var candidate : remaining) {
                double relevance = candidate.qualityScore() * candidate.similarityScore();
                double maxSim = selected.stream()
                        .mapToDouble(s -> jaccard(candidate, s))
                        .max().orElse(0.0);
                double score = (1 - diversityWeight) * relevance - diversityWeight * maxSim;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            selected.add(best);
            remaining.remove(best);
        }

        // Return ordered by original relevance
        selected.sort(Comparator.comparingDouble(
                (ExampleCandidate c) -> c.qualityScore() * c.similarityScore()).reversed());
        return List.copyOf(selected);
    }

    private static double jaccard(ExampleCandidate a, ExampleCandidate b) {
        Set<String> tokensA = tokenise(a);
        Set<String> tokensB = tokenise(b);
        if (tokensA.isEmpty() && tokensB.isEmpty()) return 0.0;
        var intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);
        var union = new HashSet<>(tokensA);
        union.addAll(tokensB);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> tokenise(ExampleCandidate c) {
        String text = c.input() + " " + c.output();
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return Set.of();
        return Set.of(trimmed.split("\\s+"));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl . -Dtest=OutcomeAwareDiversityStrategyTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/casehub/blocks/prompt/optimiser/OutcomeAwareDiversityStrategy.java src/test/java/io/casehub/blocks/prompt/optimiser/OutcomeAwareDiversityStrategyTest.java
git commit -m "feat(#78): add OutcomeAwareDiversityStrategy with MMR and Jaccard similarity"
```

---

### Task 7: Wire DiversityStrategy into FewShotOptimiser

**Files:**
- Modify: `src/main/java/io/casehub/blocks/prompt/optimiser/FewShotOptimiser.java`
- Modify: `src/test/java/io/casehub/blocks/prompt/optimiser/FewShotOptimiserTest.java`

**Interfaces:**
- Consumes: `DiversityStrategy` (Task 5), `TopNDiversityStrategy` (Task 5)
- Produces: `FewShotOptimiser()` (no-arg, uses `TopNDiversityStrategy`) and `FewShotOptimiser(DiversityStrategy)`.

- [ ] **Step 1: Write the failing tests — add to existing test file**

Append these tests to `FewShotOptimiserTest.java`:

```java
@Test
void noArgConstructorUsesTopNStrategy() {
    var optimiser = new FewShotOptimiser();
    var config = new OptimiserConfig(2, 0.0, 1, 1);
    var candidates = List.of(
            candidate("SUCCESS", 0.9, 0.9),
            candidate("SUCCESS", 0.8, 0.8),
            candidate("SUCCESS", 0.7, 0.7),
            candidate("SUCCESS", 0.6, 0.6));
    var result = optimiser.optimise(signature(), null, dataset(candidates), config)
            .toCompletableFuture().join();
    // TopN: returns top 2 by relevance — identical to current behaviour
    assertThat(result.examples()).hasSize(2);
    assertThat(result.examples().get(0).qualityScore()).isEqualTo(0.9);
    assertThat(result.examples().get(1).qualityScore()).isEqualTo(0.8);
}

@Test
void customStrategyReceivesDoubleShortlist() {
    var capturedSize = new java.util.concurrent.atomic.AtomicInteger();
    DiversityStrategy spy = (shortlist, max) -> {
        capturedSize.set(shortlist.size());
        return shortlist.stream().limit(max).toList();
    };
    var optimiser = new FewShotOptimiser(spy);
    var config = new OptimiserConfig(2, 0.0, 1, 1);
    var candidates = List.of(
            candidate("SUCCESS", 0.9, 0.9),
            candidate("SUCCESS", 0.8, 0.8),
            candidate("SUCCESS", 0.7, 0.7),
            candidate("SUCCESS", 0.6, 0.6));
    optimiser.optimise(signature(), null, dataset(candidates), config)
            .toCompletableFuture().join();
    assertThat(capturedSize.get()).isEqualTo(4); // 2x maxExamples = 4
}
```

Add this import at the top:

```java
import io.casehub.blocks.prompt.DiversityStrategy;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl . -Dtest=FewShotOptimiserTest`
Expected: FAIL — `FewShotOptimiser` has no constructor taking `DiversityStrategy`.

- [ ] **Step 3: Modify FewShotOptimiser**

Replace the full class:

```java
package io.casehub.blocks.prompt.optimiser;

import io.casehub.blocks.prompt.DiversityStrategy;
import io.casehub.blocks.prompt.ExampleCandidate;
import io.casehub.blocks.prompt.FewShotExample;
import io.casehub.blocks.prompt.OptimisationDataset;
import io.casehub.blocks.prompt.OptimiserConfig;
import io.casehub.blocks.prompt.OptimiserResult;
import io.casehub.blocks.prompt.PromptOptimiser;
import io.casehub.blocks.prompt.PromptSignature;
import io.casehub.blocks.prompt.PromptVariant;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class FewShotOptimiser implements PromptOptimiser {

    private final DiversityStrategy diversityStrategy;

    public FewShotOptimiser() {
        this(new TopNDiversityStrategy());
    }

    public FewShotOptimiser(DiversityStrategy diversityStrategy) {
        this.diversityStrategy = diversityStrategy;
    }

    @Override
    public String id() {
        return "few-shot";
    }

    @Override
    public CompletionStage<OptimiserResult> optimise(
            PromptSignature signature,
            @Nullable PromptVariant currentVariant,
            OptimisationDataset dataset,
            OptimiserConfig config) {

        var shortlist = dataset.candidates().stream()
                .filter(c -> c.qualityScore() >= config.minQualityThreshold())
                .sorted(Comparator.comparingDouble(
                        (ExampleCandidate c) -> c.qualityScore() * c.similarityScore()).reversed())
                .limit(config.maxExamples() * 2L)
                .toList();

        var selected = diversityStrategy.select(shortlist, config.maxExamples());

        var examples = selected.stream()
                .map(c -> new FewShotExample(c.input(), c.output(), c.outcome(), c.qualityScore(), null))
                .toList();

        return CompletableFuture.completedFuture(new OptimiserResult(examples, null, 0.0));
    }
}
```

- [ ] **Step 4: Run all FewShotOptimiser tests**

Run: `mvn --batch-mode test -pl . -Dtest=FewShotOptimiserTest`
Expected: PASS (all existing tests + new tests)

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test`
Expected: PASS — no regressions.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/blocks/prompt/optimiser/FewShotOptimiser.java src/test/java/io/casehub/blocks/prompt/optimiser/FewShotOptimiserTest.java
git commit -m "feat(#78): wire DiversityStrategy into FewShotOptimiser with TopN default"
```

---

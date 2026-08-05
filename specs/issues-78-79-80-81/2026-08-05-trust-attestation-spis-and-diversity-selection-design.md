# Trust Attestation SPIs + FewShotOptimiser Diversity Selection

**Issues:** #78, #79, #80, #81
**Date:** 2026-08-05
**Status:** Draft

## Overview

Four enhancements on a single branch: three trust-attestation SPIs extracted from domain repo duplication (#79, #80, #81) and one prompt optimiser enhancement (#78). The trust SPIs share a package and compose with the existing `AttestationIntent`/`AttestationIntentWriter` in blocks. The diversity enhancement is independent.

---

## 1. Attestation Package (#79, #80, #81)

**Package:** `io.casehub.blocks.attestation`

### 1.1 Existing Types (commit + modify)

**`AttestationIntent`** — existing untracked record. Add one field:

- `@Nullable UUID causedByEntryId` — source event entry ID for idempotent writes. Nullable because not all attestation sources have a causal entry.

Full shape after modification:

```java
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

**`AttestationIntentWriter`** — existing untracked SPI. Unchanged:

```java
public interface AttestationIntentWriter {
    void write(AttestationIntent intent, String tenancyId);
}
```

### 1.2 LifecycleAttestationObserver (#79)

**`LifecycleAttestationObserver<E>`** — `@FunctionalInterface` SPI. Domain repos implement this to map lifecycle events to attestation intents.

```java
@FunctionalInterface
public interface LifecycleAttestationObserver<E> {
    List<AttestationIntent> observe(E event, AttestationContext context);
}
```

**`AttestationContext`** — ambient context the observer needs:

```java
public record AttestationContext(
        String tenancyId,
        UUID caseId,
        String capabilityTag) {}
```

**No chaining.** Unlike `ActionRiskClassifier` (multiple classifiers compete on the same action, most-restrictive-wins), lifecycle observers are typed per event — `LifecycleAttestationObserver<PrLifecycleEvent>` is a different type from `LifecycleAttestationObserver<WorkItemOutcomeEvent>`. CDI dispatches by type parameter.

**Consumer pattern:**

```java
@ApplicationScoped
public class PrLifecycleAttestationObserver
        implements LifecycleAttestationObserver<PrLifecycleEvent> {

    @Override
    public List<AttestationIntent> observe(PrLifecycleEvent event, AttestationContext ctx) {
        // map PR outcome -> verdict + dimensions
    }
}
```

The caller (domain lifecycle handler) invokes the observer, then feeds each intent to `AttestationIntentWriter.write()`.

### 1.3 IntakeClassifier (#80)

**`IntakeClassifier<S>`** — `@FunctionalInterface` SPI for classifying subjects into intake lanes.

```java
@FunctionalInterface
public interface IntakeClassifier<S> {
    IntakeResult classify(S subject, IntakeContext context);
}
```

**`IntakeContext`** — lightweight record the consumer populates:

```java
public record IntakeContext(
        String tenancyId,
        @Nullable String capabilityTag,
        Map<String, Object> attributes) {

    public IntakeContext(String tenancyId) {
        this(tenancyId, null, Map.of());
    }
}
```

`attributes` is the escape hatch — consumers put trust scores, observation counts, or any other signal here. The SPI does not prescribe what the classification signal is.

**`IntakeResult`** — classification output:

```java
public record IntakeResult(
        String lane,
        double confidence,
        String reason,
        Map<String, Object> metadata) {

    public IntakeResult(String lane, double confidence, String reason) {
        this(lane, confidence, reason, Map.of());
    }
}
```

`lane` is a string, not an enum — domains define their own lanes (devtown: FAST_TRACK/STANDARD/TRIAGE; clinical: URGENT/ROUTINE/DEFERRED). `confidence` is [0.0, 1.0]. `reason` is human-readable for audit trails.

**Package placement:** same `io.casehub.blocks.attestation` — intake classification is part of the trust-attestation lifecycle (classify subject -> observe lifecycle -> write attestation).

### 1.4 VouchService (#81)

**`VouchConstraint`** — pluggable eligibility check SPI:

```java
public interface VouchConstraint {
    VouchEligibility check(VouchRequest request);
}
```

**`VouchEligibility`** — sealed result:

```java
public sealed interface VouchEligibility {
    record Eligible() implements VouchEligibility {}
    record Ineligible(String reason) implements VouchEligibility {}
}
```

**`VouchRequest`** — what the voucher is asking to do:

```java
public record VouchRequest(
        String voucherId,
        String voucheeId,
        String capabilityTag,
        String tenancyId,
        Map<String, Object> attributes) {}
```

**`VouchResult`** — sealed outcome:

```java
public sealed interface VouchResult {
    record Accepted(UUID attestationEntryId) implements VouchResult {}
    record Rejected(List<String> reasons) implements VouchResult {}
}
```

**`VouchService`** — orchestrator:

```java
public class VouchService {

    private final List<VouchConstraint> constraints;
    private final AttestationIntentWriter writer;

    public VouchService(List<VouchConstraint> constraints,
                        AttestationIntentWriter writer) { ... }

    public VouchResult vouch(VouchRequest request) {
        // 1. Run all constraints — collect Ineligible reasons
        // 2. If any Ineligible -> return Rejected(reasons)
        // 3. Build AttestationIntent with ENDORSED verdict,
        //    voucherId as attestorId, voucheeId as subjectId
        // 4. Write via AttestationIntentWriter
        // 5. Return Accepted(entryId)
    }
}
```

**Design decisions:**

- **Not `@ApplicationScoped`** — constructed by the consumer with domain-specific constraints. Vouching constraints are context-specific (devtown vouch has different rules from clinical vouch), not a global chain.
- **All-constraints-must-pass** — any `Ineligible` rejects. All rejection reasons collected for transparency.
- **No vouch revocation** — out of scope. Undo via CHALLENGED attestation against the same subject.
- **Risk propagation via EigenTrust** — the ENDORSED attestation creates a trust-graph edge. EigenTrust propagates reputation naturally. The vouch linkage (voucherId -> voucheeId in the attestation) is the data hook for future asymmetric weighting in ledger.

---

## 2. FewShotOptimiser Diversity (#78)

**Package:** `io.casehub.blocks.prompt` (SPI) + `io.casehub.blocks.prompt.optimiser` (default impl)

### 2.1 DiversityStrategy SPI

```java
package io.casehub.blocks.prompt;

@FunctionalInterface
public interface DiversityStrategy {
    List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples);
}
```

Contract: receives a pre-scored, pre-filtered shortlist (2x `maxExamples`), returns the final selection of up to `maxExamples`. Return order is prompt order.

### 2.2 Default Implementation

**`OutcomeAwareDiversityStrategy`** — outcome coverage + token-level Jaccard MMR:

```java
package io.casehub.blocks.prompt.optimiser;

public class OutcomeAwareDiversityStrategy implements DiversityStrategy {

    private final double diversityWeight;

    @Override
    public List<ExampleCandidate> select(List<ExampleCandidate> shortlist, int maxExamples) {
        // 1. Group by outcome category
        // 2. Seed: pick highest-scoring candidate from each outcome category (up to maxExamples)
        // 3. Fill remaining slots via MMR:
        //    score = (1 - lambda) * relevance - lambda * maxSimilarity(candidate, selected)
        //    where relevance = qualityScore * similarityScore
        //    and similarity = Jaccard on whitespace-tokenised (input + " " + output)
        // 4. Return selected list ordered by original relevance score
    }
}
```

**Jaccard similarity** — hand-written, ~10 lines. Whitespace-tokenised set intersection over union. No third-party NLP dependency. Consumers needing embedding-based diversity implement their own `DiversityStrategy`.

### 2.3 OptimiserConfig Change

Add `diversityWeight` field:

```java
public record OptimiserConfig(
        int maxExamples,
        double minQualityThreshold,
        int minOutcomeCount,
        int minVariantOutcomes,
        double diversityWeight) {

    // backward-compatible constructor with default diversityWeight=0.3
}
```

Default 0.3 — mild diversity pressure. At 0.0: pure relevance (backward compatible). At 1.0: maximises diversity.

### 2.4 FewShotOptimiser Changes

Minimal:

1. Take 2x `maxExamples` from existing score-and-filter pipeline (instead of capping at `maxExamples`)
2. Pass shortlist to `DiversityStrategy.select(shortlist, maxExamples)`
3. Map result to `FewShotExample` as before

`FewShotOptimiser` receives `DiversityStrategy` via constructor. If null, falls back to current top-N behaviour.

---

## Testing Strategy

### Attestation package

- `LifecycleAttestationObserverTest` — verify observer contract: event in, intents out. Test with a stub observer that maps a test event to known intents.
- `IntakeClassifierTest` — verify classification contract: subject + context in, result out. Test with a stub classifier. Verify compact constructor defaults.
- `VouchServiceTest` — verify orchestration:
  - All constraints pass -> ENDORSED attestation written, Accepted returned
  - Any constraint fails -> no write, Rejected with all reasons
  - Multiple constraints fail -> all reasons collected
  - Verify AttestationIntent shape: ENDORSED verdict, voucherId as attestorId, voucheeId as subjectId
- `AttestationIntentTest` — verify causedByEntryId is nullable and carried through

### Diversity selection

- `OutcomeAwareDiversityStrategyTest`:
  - Homogeneous outcomes: verify seed phase picks from available categories
  - MMR fill: verify textually similar candidates are penalised
  - diversityWeight=0.0: degenerates to pure relevance ranking
  - diversityWeight=1.0: maximises diversity
  - Shortlist smaller than maxExamples: returns all
  - Empty shortlist: returns empty
- `FewShotOptimiserTest` updates:
  - Verify 2x shortlist passed to strategy
  - Verify null strategy falls back to current top-N
  - Integration test: end-to-end with OutcomeAwareDiversityStrategy

---

## Dependencies

No new compile dependencies. All types used:
- `AttestationVerdict` — already a provided dependency via `casehub-ledger-api`
- `ActorType` — already a provided dependency via `casehub-platform-api`
- `@Nullable` — already a compile dependency via `org.jspecify:jspecify`

---

## Scope Exclusions

- No CDI discovery/chaining for `LifecycleAttestationObserver` — typed per event, no chain needed
- No vouch revocation — use CHALLENGED attestation
- No risk propagation logic — EigenTrust handles via trust graph
- No embedding vectors on `ExampleCandidate` — Jaccard on text is sufficient for the default
- No new provided dependencies

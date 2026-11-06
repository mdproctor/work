# Bottom-Up Formation Orchestrator — Design Spec

**Issue:** casehubio/agency#410
**Parent:** casehubio/agency#408 (Society integration hardening)
**Date:** 2026-11-06

## Summary

Wire `CoalitionProposal` → `NegotiationProtocol` → `GroupLifecycleService` into a single orchestrator that handles the full bottom-up group formation flow. An agent proposes a coalition, the orchestrator evaluates viability, opens a negotiation channel for peer consensus, and on acceptance creates the group via eidos.

## Scope

1. `BottomUpFormationOrchestrator` — composition root wiring the three components
2. `FormationResult` — sealed outcome type
3. `MessageObserverRegistrar` — functional interface for observer registration
4. `CoalitionProposal` cleanup — add `proposer` field, remove `coveredCapabilities()`
5. New dependency: `casehub-eidos-org-api` (provided) in agency-core

Out of scope: top-down formation, emergence-based formation, `FormationRules` enforcement (caller responsibility), JointIntention role adoption post-formation.

## Package

`io.casehub.agency.deliberation.coalition.formation` in `agency-core`.

Mirrors `deliberation.conversation.orchestration` but uses the domain-specific term "formation". Sub-package of `coalition` because bottom-up formation is inherently a coalition operation.

## Components

### BottomUpFormationOrchestrator

Plain class, not CDI-managed. Consumer constructs with domain-specific dependencies (same pattern as `VouchService`).

**Constructor parameters:**

| Parameter | Type | Source |
|-----------|------|--------|
| `evaluator` | `CoalitionEvaluator` | Coalition viability scoring |
| `groupLifecycle` | `GroupLifecycleService` | eidos-org-api group creation |
| `channelManager` | `ChannelManager` | qhorus channel lifecycle |
| `messageDispatcher` | `MessageDispatcher` | Send initial PROPOSE message |
| `observerRegistrar` | `MessageObserverRegistrar` | Register observer for channel messages |
| `specId` | `String` | Group spec governing formation target |
| `tenancyId` | `String` | Tenancy context |
| `timeout` | `Duration` | Negotiation deadline |

**API:**

```java
Uni<FormationResult> propose(CoalitionProposal proposal, CoalitionContext context)
```

### Flow

```
propose(proposal, context)
  │
  ├─ 1. evaluator.evaluate(proposal, context)
  │     └─ not viable? → FormationResult.rejected(reason)
  │
  ├─ 2. channelManager.findOrCreate(request)
  │     ├─ protocol: "NEGOTIATION"
  │     ├─ parties: proposal.proposedMembers().map(AgentRef::name)
  │     ├─ name: "formation:<taskId>:<proposer.name()>"
  │     └─ boundGroupId: null (group doesn't exist yet)
  │
  ├─ 3. ChannelObserver<NegotiationState> wrapping NegotiationProjection
  │     ├─ parties = proposedMembers names (Set<String>)
  │     ├─ acceptancePolicy = UnanimousAcceptance (default)
  │     └─ register via observerRegistrar.register(observer)
  │
  ├─ 4. messageDispatcher.dispatch(PROPOSE message)
  │     ├─ sender: proposal.proposer().name()
  │     ├─ content: proposal.taskId() + required capabilities
  │     └─ channelId: from step 2
  │
  ├─ 5. Uni bridge: subscribe to observer events
  │     ├─ on terminal outcome → emit result
  │     ├─ timeout: Uni.ifNoItem().after(timeout)
  │     └─ cleanup via emitter.onTermination()
  │
  ├─ 6a. AGREED → groupLifecycle.proposeGroup(specId, tenancyId, proposer.name())
  │       └─ groupLifecycle.approveProposal(instanceId, tenancyId)
  │           └─ FormationResult.accepted(instanceId)
  │
  ├─ 6b. DEADLOCKED/WITHDRAWN → FormationResult.negotiationFailed(reason)
  │
  ├─ 6c. Timeout → FormationResult.timeout()
  │
  └─ 7. Cleanup (all exit paths via emitter.onTermination()):
        ├─ cancel observer subscription
        ├─ cancel observer registration
        └─ channelManager.pause(channelId)
```

### FormationResult

Sealed interface in `deliberation.coalition.formation`:

```java
sealed interface FormationResult {
    record Accepted(UUID groupInstanceId) implements FormationResult {}
    record Rejected(String reason) implements FormationResult {}
    record NegotiationFailed(String reason) implements FormationResult {}
    record Timeout() implements FormationResult {}

    static FormationResult accepted(UUID id) { return new Accepted(id); }
    static FormationResult rejected(String reason) { return new Rejected(reason); }
    static FormationResult negotiationFailed(String reason) { return new NegotiationFailed(reason); }
    static FormationResult timeout() { return new Timeout(); }
}
```

### MessageObserverRegistrar

Functional interface in `deliberation.coalition.formation`:

```java
@FunctionalInterface
interface MessageObserverRegistrar {
    EventSource.Cancellation register(MessageObserver observer);
}
```

The orchestrator calls `registrar.register(observer)` during `propose()`. The CDI wiring layer in the consuming application provides the implementation. The returned `Cancellation` is used during cleanup (step 7).

### CoalitionProposal Changes

**Add `proposer` field:**

```java
// Before
record CoalitionProposal(String taskId, Set<String> requiredCapabilities,
                          List<AgentRef> proposedMembers) {
    Set<String> coveredCapabilities() { ... }
}

// After
record CoalitionProposal(AgentRef proposer, String taskId,
                          Set<String> requiredCapabilities,
                          List<AgentRef> proposedMembers) {
    // coveredCapabilities() removed
}
```

`proposer` is explicit and self-documenting. `coveredCapabilities()` is removed because `CapabilityCoverageEvaluator` already resolves capabilities correctly via `CoalitionContext.capabilitiesOf()`. No current consumers call `coveredCapabilities()`.

### Idempotent Channel Creation

Use `ChannelManager.findOrCreate(ChannelCreateRequest)` with a deterministic channel name: `"formation:<taskId>:<proposer.name()>"`. On retry, the same channel is reused rather than creating duplicates.

### Channel Cleanup

After terminal outcome (or timeout), the orchestrator:
1. Cancels the `ChannelObserver` subscription (stops event delivery)
2. Cancels the `MessageObserverRegistrar` registration (removes from dispatch)
3. Calls `ChannelManager.pause(channelId)` (prevents new messages, preserves history for audit)

All cleanup runs via `emitter.onTermination()`, which fires on normal completion, timeout, and external Uni cancellation.

### Timeout

Uni-native: `Uni.ifNoItem().after(timeout).recoverWithItem(FormationResult.timeout())`. The timeout duration is constructor-injected. Before emitting a timeout result, the orchestrator checks observer state — a negotiation could reach terminal state simultaneously with the timeout firing.

## Type Bridge

The orchestrator bridges agency-layer types to eidos-layer operations:

| Agency concept | Eidos operation |
|----------------|-----------------|
| `proposal.proposer().name()` | `proposeGroup(specId, tenancyId, proposerAgentId)` |
| `specId` (constructor param) | Group spec defining formation target |
| `tenancyId` (constructor param) | Tenancy context |
| `FormationOrigin.PROPOSAL` | Bottom-up formation origin |
| Negotiation AGREED | `approveProposal(instanceId, tenancyId)` |

Cross-layer parameters (`specId`, `tenancyId`) are constructor-injected, not carried on `CoalitionProposal`. The coalition domain is tenancy-agnostic.

## Dependencies

**New in agency-core/pom.xml:**

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-eidos-org-api</artifactId>
    <scope>provided</scope>
</dependency>
```

Consistent with existing provided-scope dependencies (`casehub-platform-api`, `casehub-engine-ledger`, `casehub-neocortex-*`).

## Test Strategy

Plain JUnit 5 with Mockito (no CDI container). Mock `GroupLifecycleService`, `ChannelManager`, `MessageDispatcher`, `MessageObserverRegistrar`.

| Test case | Scenario |
|-----------|----------|
| Happy path | Viable coalition → negotiation AGREED → group created |
| Non-viable coalition | Low score → `FormationResult.rejected()` |
| Negotiation rejected | DEADLOCKED → `FormationResult.negotiationFailed()` |
| Negotiation withdrawn | WITHDRAWN → `FormationResult.negotiationFailed()` |
| Timeout | No terminal outcome within deadline → `FormationResult.timeout()` |
| Cleanup on success | Observer unsubscribed, registration cancelled, channel paused |
| Cleanup on failure | Same cleanup on all failure paths |
| Cleanup on cancellation | External Uni cancellation triggers cleanup |
| Idempotent retry | `findOrCreate` returns existing channel on retry |
| Proposer identity | PROPOSE message sender matches `proposal.proposer().name()` |
| CoalitionProposal proposer | Explicit proposer field used correctly |
| CoalitionProposal no coveredCapabilities | Method removed, evaluator uses CoalitionContext |

## References

- `agency-core/.../deliberation/coalition/CoalitionProposal.java` — current record, `coveredCapabilities()` bug
- `agency-core/.../deliberation/coalition/CapabilityCoverageEvaluator.java` — correct capability resolution via CoalitionContext
- `agency-core/.../deliberation/negotiation/NegotiationProjection.java` — channel projection for negotiation state
- `agency-core/.../deliberation/negotiation/protocol/NegotiationProtocol.java` — ChannelProtocol impl
- `agency-core/.../communication/channel/ChannelObserver.java` — observation + EventSource bridge
- `agency-core/.../governance/trust/VouchService.java` — composition root pattern (non-CDI, constructor-injected)
- `agency-core/.../deliberation/conversation/orchestration/ConversationOrchestrator.java` — Uni return type convention
- Engine spec §3.3 — authoritative bottom-up formation design
- Agency hardening spec §7 — implementation-level detail
- casehubio/agency#408 — parent epic
- casehubio/desiredstate#230 — cross-repo reference

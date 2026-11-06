# Decisions — #410 Bottom-Up Formation Orchestrator

## D1: Orchestrator sends initial PROPOSE message

**Choice:** Orchestrator creates the negotiation channel AND sends the initial PROPOSE message via MessageDispatcher on behalf of the proposing agent.
**Alternatives:**
- Caller sends separately — more flexible but leaks negotiation mechanics to consumers
**Rationale:** `propose()` should be a single call that starts the full formation flow. Consumers shouldn't need to understand negotiation channel internals.
**Trade-offs:** Less flexibility if a consumer wants custom proposal message content. Acceptable — the coalition proposal carries the task description.
**Sources:** Engine spec §3.3, agency hardening spec §7
**Exploration:** quick
**Status:** captured

## D2: Explicit proposer field on CoalitionProposal

**Choice:** Add `proposer(AgentRef)` field to `CoalitionProposal` record.
**Alternatives:**
- Convention: proposedMembers[0] is the proposer — implicit contract, easy to violate
**Rationale:** Self-documenting, no ordering dependency, clear who initiated the formation.
**Trade-offs:** Minor breaking change to the record constructor. No current consumers outside tests.
**Sources:** CoalitionProposal.java, CoalitionFormationTest.java
**Exploration:** quick
**Status:** captured

## D3: Orchestrator explicitly closes channel

**Choice:** After terminal outcome (AGREED/DEADLOCKED/WITHDRAWN/timeout), orchestrator calls ChannelManager to close the negotiation channel.
**Alternatives:**
- Channel auto-closes via TerminationCondition — relies on qhorus supporting auto-close
- No cleanup, rely on TTL — leaves orphaned channels
**Rationale:** Deterministic cleanup. The orchestrator owns the channel lifecycle.
**Trade-offs:** Requires ChannelManager.close() or equivalent. If it doesn't exist, we need a channel deletion mechanism.
**Sources:** ChannelConfig.java, ChannelBinding.java
**Exploration:** quick
**Status:** captured

## D4: Synchronous composition root architecture

**Choice:** Plain class (not CDI-managed), constructor-injected dependencies, `CompletionStage<FormationResult>` return type. Follows VouchService pattern.
**Alternatives:**
- ChoreographedDriver — event-driven via ChannelObserver EventSource. More reactive but flow is inherently sequential; choreography adds complexity without benefit.
**Rationale:** The formation flow is a linear pipeline (evaluate → negotiate → create group). CompletionStage handles the async negotiation observation cleanly.
**Trade-offs:** Not CDI-discoverable — consumers construct explicitly. Consistent with VouchService pattern.
**Sources:** VouchService.java, ChoreographedDriver.java
**Exploration:** quick
**Status:** captured

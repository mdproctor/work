# Decisions — #417 FORMING → ACTIVE transition

## D1: Separate activator vs inline in orchestrator

**Choice:** Separate `FormationActivator` class in `deliberation.coalition.formation`
**Alternatives:**
- Extend `BottomUpFormationOrchestrator` to call `activateGroup()` inline — simpler but conflates formation and activation concerns, breaks existing contract
**Rationale:** Formation (negotiation + group creation) and activation (commitment validation + state transition) are distinct concerns. Callers may want deferred activation. Follows the `VouchService` / `ConversationOrchestrator` composition root pattern.
**Trade-offs:** Two-step API for callers who always want immediate activation (minor — a convenience method can compose both)
**Sources:** `BottomUpFormationOrchestrator.java`, `FormationResult.java`, predecessor spec #410 §Out of scope
**Exploration:** quick
**Status:** captured

## D2: Result type — new sealed type vs extend FormationResult

**Depends on:** D1 (separate activator needs its own result type)
**Choice:** New `ActivationResult` sealed type with `Activated(groupInstanceId, intention)`, `MonitorRejected(signal)`, `ActivationFailed(reason)`
**Alternatives:**
- Extend `FormationResult` with activation variants — simpler for composed callers but muddies the sealed contract, mixing two lifecycle phases in one type
**Rationale:** Formation and activation are distinct lifecycle phases. Separate sealed types keep each narrowly scoped to its phase's outcomes.
**Trade-offs:** Callers composing both steps must handle two result types (minor ergonomic cost)
**Sources:** `FormationResult.java`, `JointIntention.java`, `ReconsiderationSignal.java`
**Exploration:** quick
**Status:** captured

## D3: IntentionMonitor — optional vs required

**Depends on:** D1 (activator uses monitor for validation)
**Choice:** Optional `@Nullable IntentionMonitor` in constructor
**Alternatives:**
- Required monitor, callers pass `intention -> null` for unconditional activation — adds ceremony for the common case
**Rationale:** Nullable optional SPIs are the established pattern in this codebase (e.g., nullable `DegradationCascadeFactory`, nullable `FeedbackHandler`). Unconditional activation is legitimate — negotiation already validated consensus.
**Trade-offs:** Null check in activation path (trivial)
**Sources:** `IntentionMonitor.java`, `ComposableTopology` (nullable pattern precedent)
**Exploration:** quick
**Status:** captured

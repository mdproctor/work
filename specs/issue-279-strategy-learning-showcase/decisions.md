## D1: Fix 1-signal-per-tick accumulation problem

**Choice:** Fix signal correlation + send ConversationOutcome checkpoints
**Alternatives:**
- Accumulate-don't-drain (change tick semantics) — per-orchestrator fix, doesn't address correlation gap
- Cadenced ticking (tick at different frequencies) — adds state to CognitionCore, doesn't fix correlation
**Rationale:** The existing ConversationOutcome path in StrategyLearningOrchestrator was designed for exactly this purpose — batching turns into conversation cases. It just was never wired up. Giving each conversation a stable ID and sending periodic ConversationOutcome signals activates the existing code path without changing orchestrator internals.
**Trade-offs:** Requires ConversationRunner to manage a conversation ID and emit checkpoints — test infrastructure concern, not production. Production consumers (SocialAvatarCognition) would need their own ConversationOutcome emission.
**Sources:** StrategyLearningOrchestrator.java:194-256 (ConversationOutcome matching path), CognitionCore.java:170-183 (random UUID caseId), ConversationRunner.java:88-93 (recordInteraction call site)
**Exploration:** quick
**Status:** captured

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

## D2: How to integrate reflect() into the conversation loop

**Choice:** Auto-trigger reflect() inside tick() when enough cases exist
**Alternatives:**
- Explicit reflect() call at a turn threshold in ConversationRunner — test runner manages brain lifecycle, not platform behavior; every consumer must know when to call reflect()
- Separate reflect cadence in CognitionCore — adds coupling in wrong direction; CognitionCore shouldn't know strategy-specific lifecycle
**Rationale:** The orchestrator should be self-sufficient. When it has enough evidence (case count >= minCasesForReflection AND cooldown elapsed since lastReflectTimestamp), it reflects automatically. This is how a real cognitive system works — the subsystem recognises when it has enough experience to update its understanding. Any consumer (showcase, wacky-manor, production) gets strategy adaptation for free. The showcase demonstrates platform intelligence, not plumbing.
**Trade-offs:** reflect() is an LLM call, but cooldown prevents repeated invocations. Cost is bounded and intentional — the brain learning IS the feature.
**Sources:** StrategyLearningOrchestrator.java:115-129 (tick), StrategyLearningOrchestrator.java:131-140 (reflect), StrategyLearningOrchestrator.java:307-358 (doReflect with LLM)
**Exploration:** deep-analysis
**Depends on:** D1 (signal correlation must work so cases get stored before reflect can fire)
**Status:** captured

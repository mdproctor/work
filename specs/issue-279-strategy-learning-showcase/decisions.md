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

## D3: Cold-start strategy via neurocortex priming

**Choice:** Prime CBR stores with synthetic interaction cases from descriptor data, let the pipeline synthesise
**Alternatives:**
- Construct StrategyProfile directly from descriptor heuristics — bypasses the pipeline; special-case seeding that doesn't demonstrate emergence
- One-shot LLM bootstrap call at construction — adds async startup cost and doesn't exercise the normal reflect pathway
- No seeding, wait for organic evidence — strategy contributes too late (turn 6-8 at best)
**Rationale:** The brain should wake up with memories and think about them through its normal cognitive loop. Priming the CBR store with synthetic cases (derived from descriptor constraints and disposition) means the auto-reflect in tick (D2) fires on the first tick, synthesising guidelines through the same LLM reflection path used for ongoing learning. No special-case code — primed data and lived experience flow through the same pipeline. This pattern generalises to all orchestrators (#283): goals, beliefs, strategies all emerge from primed neurocortex data rather than directive text.
**Trade-offs:** Synthetic cases are approximations — they model "what effective interaction looks like" based on the agent's constraints, not actual experience. Early guidelines may be generic until real evidence refines them. Priming enough cases to cross minCasesForReflection requires thoughtful case construction.
**Sources:** StrategyLearningOrchestrator.java:307-358 (doReflect — processes CBR cases into guidelines), AgentDescriptor constraints + disposition (source data for synthetic cases), #283 (directive-minimal architecture — broader context)
**Exploration:** deep-analysis
**Depends on:** D1 (case storage), D2 (auto-reflect)
**Status:** captured

## D4: UserModel quality signal derivation

**Choice:** Pluggable quality strategy with mood-derived default + adaptive fallback
**Alternatives:**
- Hardcoded mood-to-quality mapping — no adaptability; if mood appraisal is noisy, quality degrades silently
- Always require explicit InteractionMapper — pushes complexity to every consumer
**Rationale:** Make quality derivation a strategy (SPI). Default implementation derives quality from the mood appraisal pleasure delta already computed per turn (positive pleasure → POSITIVE, negative → NEGATIVE, near-zero → NEUTRAL). Zero additional LLM cost. If the strategy detects it's not differentiating well — all subjects converge to same familiarity, quality doesn't correlate with engagement — it falls back to the InteractionMapper path (CognitiveImpact.userModelSignal from #282). This is adaptive meta-cognition: the brain evaluates its own signal quality and switches strategies when one isn't working.
**Trade-offs:** The effectiveness detection adds complexity. For this branch: implement the pluggable SPI + mood-derived default. Effectiveness detection and adaptive switching is a follow-on — the SPI makes it possible without requiring it now.
**Sources:** CognitionCore.java:143-157 (hardcoded NEUTRAL), MoodOrchestrator (pleasure delta already computed), #282 CognitiveImpact (explicit override path), blocks prompt optimisation framework (VariantSelector pattern for A/B)
**Exploration:** quick
**Status:** captured

## D5: Goals — cooldown, missing sources, and priming scope

**Choice:** Fix mechanical issues (cooldown config, wire CuriosityGoalMapper) in this branch. Goal priming follows D3 pattern — implement as part of neurocortex priming, same mechanism for strategy and goals.
**Alternatives:**
- Goals-only priming (separate from strategy priming) — duplicates the priming mechanism
- Defer all goal fixes to #283 — leaves goals broken in the showcase
**Rationale:** The cooldown (60 min default) and missing CuriosityGoalMapper are config/wiring bugs, not design questions. Fix them. Goal priming is architecturally identical to strategy priming (D3): load descriptor goal data into neurocortex stores with metadata (priority, emotional valence, time, links), let GoalProposalOrchestrator process them through its normal tick. Building one priming mechanism that handles both strategy cases and goal records keeps the code unified.
**Trade-offs:** Goal records need richer metadata than strategy CBR cases (priority, valence, temporal metadata, links to other goals/entities). The priming mechanism must be extensible to different store types, not just CBR.
**Sources:** GoalProposalConfig.java (60min cooldown default), CognitionStack.java:118-133 (FULL stage — no CuriosityGoalMapper), #283 (directive-minimal architecture)
**Exploration:** quick
**Depends on:** D3 (priming mechanism)
**Status:** captured

## D6: LLM proof tests structure

**Choice:** Per-orchestrator assertion tests in HistoricalEncounterLlmTest
**Alternatives:**
- Single full-conversation test with post-hoc assertions — coarser, harder to diagnose failures
**Rationale:** Each test isolates one pipeline and runs N turns to assert non-null prompt section contribution by a specific turn. Test name tells you what broke. E.g.: strategyContributesByTurn4(), goalsProposedByTurn6(), userModelDifferentiatesSubjects(). Run under -Pllm with real LLM. Can add full-conversation integration test later (approach B) for end-to-end confidence.
**Trade-offs:** More test methods means more LLM calls in the test suite. Each test may run a short conversation (4-8 turns) rather than the full 12. Acceptable — focused proofs are more valuable than one long test.
**Sources:** HistoricalEncounterLlmTest.java (existing test class), ConversationRunner.java (drives the conversation loop)
**Exploration:** quick
**Status:** captured

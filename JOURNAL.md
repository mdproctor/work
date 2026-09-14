# Design Journal — issue-261-measurement-driven-cognition

## 2026-09-14 — Session 1: Infrastructure + first live tests

### What was built

- **CognitionCore** (blocks main scope) — shared orchestrator composition: tick ordering, signal recording, prompt section building. 8 nullable orchestrator slots for staged wiring.
- **CognitionSnapshot + CognitionDelta + CognitionMetrics** (blocks main scope) — observable cognitive state, structured diff (mood/drive/BDI/narrative/goal deltas), per-turn measurement via snapshot comparison. Designed for neocortex #333 compatibility.
- **ConversationRunner rewrite** — switched from multi-turn AgentSession to single-turn AgentProvider.invoke(). Cognition sections now go in system prompt each turn (matching production SocialPromptAssembler pattern). Per-turn metrics capture.
- **CognitionStack stage gating** — Stage enum (BASELINE→FULL) controls which orchestrators are wired. Delegates to CognitionCore.
- **ResultsWriter, MermaidGenerator, ConversationEvaluator** — results output infrastructure.
- **Stage 0-6 test methods** in HistoricalEncounterLlmTest.
- **LLM-backed BDI extraction** (#261) — CognitionCore.recordInteraction() uses AgentProvider to extract structured beliefs/desires/intentions from dialogue instead of raw utterance dump.
- **Mood sentiment analysis** (#262) — LLM-backed PAD appraisal from conversation exchanges.
- **Directive prompt sections** (#263) — test-scope DirectivePromptSection wrappers that add behavioral framing.
- **Config tuning** — lowered minSignalsForInference to 1, inferenceCooldown to PT0S, userModel synthesisCooldown to PT0S.
- **MoodState fix** — added activeContextIds parameter for neocortex-memory-api compatibility.

### What was validated

- Stage 0 baseline: 4-turn conversation, all metrics zero. Control established.
- Stage 1 with fixes: mood shifts visible (P+0.20 A+0.15 D+0.10 on Turn 3), mental model forming (4B 1D on Turn 4), LLM BDI extraction producing structured beliefs.
- Pre-existing NarrativeSynthesiserTest failure (NoClassDefFoundError) — unrelated to our work.

### Key finding

Neurocortex IS populating (mood shifts, mental models form, drives activate) but NOT yet visibly altering dialogue. Root causes identified:
1. Only 1 of 7 prompt sections contributes per turn (#264)
2. 4 turns too short for compounding (#265)
3. Narrative episodes are heuristic stubs, not LLM-synthesized (#268)

### Decision review finding applied

D5 revised: switched from user-prompt injection to system-prompt injection matching production SocialPromptAssembler pattern. Single-turn queries instead of multi-turn AgentSession.

### Architecture decision: configurable subsystem toggles (#271)

The Stage enum is too coarse for A/B testing. CognitionConfig with per-subsystem boolean flags enables isolating each subsystem's contribution via config changes only. The directive prompt behavior itself becomes a toggle.

# HANDOFF — casehub-neocortex

## Last Session

Implementation + landing of cognitive dissonance (#593). Executed the full plan (3 tasks, 2 batches), landed on main, issue closed.

1. **DissonanceDetectionPhase** — LLM-based contradiction detection with additive decay lifecycle, order-independent deduplication, in-memory cache for per-tick consumers. 8 tests.
2. **DissonanceTickParticipant + DissonancePromptSection + BlockTag.DISSONANCE(212)** — PAD adjustment (arousal up, pleasure down per Cooper & Fazio 1984), felt tension rendering between DRIVES and CURIOSITY in Tier 2 State. 11 tests.
3. **Pipeline wiring** — CognitionConfig.dissonanceEnabled (28th field), CognitionCore.configureDissonance(), CognitivePipelineBuilder.dissonance(), CognitionCoreFactory CDI wiring, DirectiveSection entries.

Branch `issue-593-cognitive-dissonance` stamped and closed. 4 commits on main. Issue #593 closed.

Pre-existing test compilation failures in `SituationalPersonalityIntegrationTest` and `SituationalPersonalityParticipantTest` (AgentDescriptor constructor mismatch from upstream eidos-api change) — not caused by this branch.

## Immediate Next Step

Next queue items: #1210 (Dissonance-coping integration), #1211 (NLI fast-path), #1212 (direct CAPS evaluation). All need brainstorming and design before implementation.

## References

- `specs/issue-593-cognitive-dissonance/2026-11-23-cognitive-dissonance-design.md` — design spec
- `specs/issue-593-cognitive-dissonance/decisions.md` — 9 design decisions
- `plans/2026-11-23-cognitive-dissonance.md` — implementation plan (3 tasks, 2 batches)
- `blog/2026-11-23-mdp01-the-villain-who-cant-feel-the-contradiction.md` — diary entry

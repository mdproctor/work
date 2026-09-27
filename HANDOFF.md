# HANDOFF — casehub-blocks

## Last Session

Implemented **casehubio/blocks#304** — CognitiveAttentionMediator: bridge neocortex attention signals to CognitionCore. Full TDD implementation across 5 commits:

1. `CognitiveAttentionMediator` — @ApplicationScoped CDI bean, per-principal ConcurrentLinkedQueue, merge semantics (dedup by sourceNodeId+category, latest urgencyP75, significance-sorted)
2. `AttentionRelevance` — package-private static utility with EnumSet<SignalCategory> constants mapping cognitive domains to relevant signal categories. Type-safe, no string keys.
3. `CognitionConfig` — added `attentionEnabled` (13th field)
4. `CognitionCore` — nullable mediator field, drain-and-discard in tick() FOUNDATION, `isEnabled()` helper for attention-aware section gating, `AttentionPromptSection` rendering, `lastBriefing()` accessor
5. CDI wiring — BlocksBeans, SocialAvatarCognition, BlocksAutoConfiguration all pass mediator

Docs updated: CLAUDE.md, consumer guide, contributor guide. #305 closed as premature (topN truncation doesn't exist — when it does, retrieval-modulated scoring is the right approach, not arbitrary cutoffs).

2209 tests pass. Full project builds.

## Follow-up Issues

| # | Repo | Title | Scale | Complexity | Notes |
|---|------|-------|-------|------------|-------|
| 306 | blocks | Runtime-customizable attention override policy | S | Med | Extract AttentionRelevance to SPI |
| 307 | blocks | CognitionSnapshot — capture lastBriefing | XS | Low | Attention invisible to observability |
| 308 | blocks | Context-budget-aware prompt rendering | L | High | Session lifecycle, window management, new vs continuing |
| 91 | examples | Replace CognitiveBudget.forSituation() with attention gating | S | Med | wacky-manor, depends on #304 + #306 |
| 89 | examples | Redefine briefing as voice card | — | — | Open, should reference #304 mediator |

## Design Decisions

- **D1:** Mediator in blocks-core alongside CognitionCore
- **D2:** attentionEnabled config flag with drain-and-discard when disabled
- **D3:** AttentionRelevance reverse mapping (domain → categories) with EnumSet constants — type-safe, O(1) bitwise, testable

## References

- Spec: `/Users/mdproctor/claude/casehub/slots/203/wsp-casehub-blocks/specs/issue-304-cognitive-attention-mediator/2026-09-27-cognitive-attention-mediator-design.md`
- Decisions: `/Users/mdproctor/claude/casehub/slots/203/wsp-casehub-blocks/specs/issue-304-cognitive-attention-mediator/decisions.md`
- Plan: `/Users/mdproctor/claude/casehub/slots/203/wsp-casehub-blocks/plans/2026-09-27-cognitive-attention-mediator.md`
- Neocortex spec §7: `/Users/mdproctor/claude/casehub/slots/203/neocortex/docs/specs/issue-381-progressive-attention-model/2026-09-25-progressive-attention-model-design.md`

# HANDOFF — casehub-blocks

## Last Session

Branch `issue-304-cognitive-attention-mediator` scaffolded. Design brainstorming in progress — 3 decisions captured (D1: mediator in blocks-core, D2: attentionEnabled config flag, D3: AttentionRelevance EnumSet gating). Full design presented and approved. Next: decision review, spec writing, then implementation planning.

## Immediate Next Step

**Write spec and implementation plan for blocks#304.** Design is complete — proceed through decision review gate, write spec, invoke writing-plans.

## Design Decisions (D1–D3)

- **D1:** CognitiveAttentionMediator lives in blocks-core alongside CognitionCore
- **D2:** CognitionConfig gets `attentionEnabled` boolean (13th field)
- **D3:** Section gating via `AttentionRelevance` — reverse mapping with `EnumSet<SignalCategory>` constants per cognitive domain, `isEnabled(configFlag, relevanceSet)` helper in CognitionCore. Fully type-safe, no string-based subsystem keys in the override path.

## Components

1. **CognitiveAttentionMediator** (@ApplicationScoped) — @Observes CognitiveAttentionRequired, per-principal ConcurrentLinkedQueue, drainAttention() with merge
2. **AttentionPromptSection** — renders top-N signals by significance
3. **AttentionRelevance** — package-private static utility, EnumSet constants + overrides() method
4. **CognitionConfig** — add attentionEnabled field
5. **CognitionCore** — nullable mediator field, lastBriefing per-tick, 5th constructor, isEnabled() helper
6. **BlocksBeans** — produce mediator, pass to SocialAvatarCognition

## Out of Scope (tracked)

| # | Issue | What |
|---|-------|------|
| 305 | TopN expansion for attention-aware prompt sections | Triggered when first section adds topN truncation |
| 306 | Runtime-customizable attention override policy | Extract AttentionRelevance to SPI for runtime customization |
| 297 | Spring coverage expansion | BlocksAutoConfiguration needs same mediator wiring as BlocksBeans |

## Design Reference

- Neocortex spec §7: `/Users/mdproctor/claude/casehub/slots/203/neocortex/docs/specs/issue-381-progressive-attention-model/2026-09-25-progressive-attention-model-design.md`
- Decisions: `/Users/mdproctor/claude/casehub/slots/203/wsp-casehub-blocks/specs/issue-304-cognitive-attention-mediator/decisions.md`

## Dependencies

- **casehubio/neocortex#381** (landed) — provides AttentionBriefing, AttentionSignal, CognitiveAttentionRequired, SignalCategory in mindmap-api
- CognitionCore in blocks-core module
- blocks already depends on neocortex mindmap-api

# HANDOFF — casehub-blocks

## Last Session

Branch scaffolded for casehubio/blocks#304 — CognitiveAttentionMediator. No implementation yet.

## Immediate Next Step

**Brainstorm then implement blocks#304.** Three components:

1. **CognitiveAttentionMediator** (`@ApplicationScoped`) — `@Observes CognitiveAttentionRequired` CDI event, maintains per-principal `ConcurrentLinkedQueue<AttentionBriefing>`, exposes `drainAttention(String principalId) → Optional<AttentionBriefing>`.

2. **CognitionCore integration** — add `lastBriefing` field. Both `tick()` (wacky-manor) and push-based agents drain attention through `drainAttention()` delegating to the mediator.

3. **Attention prompt section** — when `lastBriefing` present, render top-N signals ranked by significance in the cognitive prompt.

## Design Reference

The neocortex spec covers the blocks contract in §7:
- Spec location: `neocortex` repo at `docs/specs/issue-381-progressive-attention-model/2026-09-25-progressive-attention-model-design.md`
- Slot path: `/Users/mdproctor/claude/casehub/slots/203/neocortex/docs/specs/issue-381-progressive-attention-model/2026-09-25-progressive-attention-model-design.md`

Key design decisions from §7:
- CognitionCore is a manually-constructed POJO (no CDI) — mediator bridges CDI events to it
- Mediator uses ConcurrentLinkedQueue per principal — same threading model as neocortex accumulator
- CognitionCore renders attention as a prompt section, not a separate system message

## Dependencies

- **casehubio/neocortex#381** (landed) — provides `CognitiveAttentionRequired`, `AttentionBriefing`, `AttentionSignal` CDI event types
- CognitionCore lives in blocks-core module
- blocks already depends on neocortex mindmap-api (where the event types live)

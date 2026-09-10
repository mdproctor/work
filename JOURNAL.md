# Design Journal — main

## 2026-09-10 — #248 Affordance world model + #249 Channel infrastructure

Two YAML surface issues landed. Coverage 39%→50%.

**#248 — Affordance world model:** WorldDefinition root type, 7 spec records
(EntitySpec, InlineEntitySpec, AffordanceSpec, ActionDescriptorSpec, ObservationSectionSpec
sealed with 3 variants, ObservationFilterSpec, RendererSpec), WorldCompiler with entity
ref resolution + AnnotatedSection wrapping from inline properties, ObservationFilterRegistry
with built-in perception type, deployment processor. Design decision: support both
top-level entity map and inline entities in sections (D2). AnnotatedSection expressed as
inline properties on section specs rather than explicit wrapper type (D3).

**#249 — Channel infrastructure:** Extended TerminationSpec with 3 negotiation variants
(accepted, terminal-outcome, deadline). NegotiationCompiler closes the spec-only gap.
5 new spec records (AgentParticipantSpec, ChannelBindingSpec, ChannelExecutionStrategySpec
with conversation-only variant, ConversationProtocolSpec, ProgressRendererSpec).
ConversationCompiler produces declarative config — consumer wires ConversationOrchestrator
with CDI-injected functional impls (D1).

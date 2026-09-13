# HANDOFF — casehub-blocks

## Last Session

Closed #258 (historical encounter showcase). Built shared LLM test infrastructure in agentic-yaml: TestAgentProvider (Claude SDK via ClaudeAgentClient), CognitionStack (orchestrator builder from CompiledCognition), DescriptorLoader (eidos YAML), ConversationRunner (session-based with prompt caching). 4-turn Leonardo/Tesla conversation runs at ~37s/turn. Cognition stack wired but producing baseline-only output — no signal extraction, no recordInteraction().

## Immediate Next Step

Run `work start #261` — measurement-driven cognition integration. Start with Stage 0 (baseline capture) then Stage 1 (wire SocialAvatarCognition.recordInteraction() + inject cognition sections into user prompt). Key insight: the LLM won't RAG from neurocortex without explicit prompt preparation each turn. Consolidate shared code with wacky-manor's social cognition wiring.

## References

| Artifact | Path |
|----------|------|
| LLM test infra | `agentic-yaml/src/test/java/.../llm/` |
| Design spec | `docs/specs/issue-258-historical-encounter-showcase/` |
| Implementation plan | workspace `plans/2026-09-12-historical-encounter-showcase.md` |
| Next issue | https://github.com/casehubio/blocks/issues/261 |
| Wacky-manor consolidation | `casehub/examples/wacky-manor/` — wiring social cognition in parallel |

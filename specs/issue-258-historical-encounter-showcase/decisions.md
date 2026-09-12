## D1: Location and module split

**Choice:** Two-part split:
- Shared LLM test infrastructure (AgentProvider adapter, orchestrator wiring from cognition.yaml) → `agentic-yaml` test scope in blocks
- Historical encounter showcase with UI → `casehub/examples/historical-encounter` as a Quarkus app (like wacky-manor)

**Alternatives:**
- New `showcase/` module in blocks — functionally the same as examples/, false distinction
- Everything in `agentic-yaml` test scope — can't have UI, blocks must not have UI dependencies
- Everything in examples/ only — loses the shared test infrastructure that all YAML examples need

**Rationale:** blocks is a pure library with no Quarkus runtime or UI. The LLM test utilities (no UI) belong there. The showcase app (UI, CDI, full orchestration) belongs in examples/ alongside wacky-manor. The split also means the shared test infrastructure benefits all six YAML examples, not just the historical encounter.

**Trade-offs:** Two repos touched — blocks (test infra) and examples (showcase app). Coordination needed at implementation time.

**Sources:** wacky-manor in casehub/examples/ (reference pattern), blocks CLAUDE.md (no Quarkus runtime constraint)

**Exploration:** quick
**Status:** captured

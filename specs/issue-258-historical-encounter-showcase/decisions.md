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

## D2: LLM test gating in blocks

**Choice:** Maven profile (e.g., `-Pllm`) in `agentic-yaml` that activates LLM test dependencies (langchain4j-google-ai-gemini, platform-agent-api). LLM test classes also use `@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY")` as a second guard. A thin test-only class wraps a langchain4j `ChatModel` implementing `AgentProvider` — no CDI, no routing, just invoke → ChatRequest → TextDelta. Default `mvn test` skips LLM tests entirely (no heavy deps on classpath, no env var needed).

**Alternatives:**
- Always include LLM deps in test scope, gate only via `@EnabledIfEnvironmentVariable` — deps always on classpath even when unused, slower resolution
- Skip LLM tests in blocks entirely, only test in examples/ — loses fast feedback on orchestrator wiring without the full app

**Rationale:** Maven profile keeps the default test classpath lean. `@EnabledIfEnvironmentVariable` provides a safety net if the profile is active but credentials are missing. The thin wrapper avoids CDI dependency in blocks test scope.

**Trade-offs:** Two-level gating is slightly more ceremony than a single mechanism. Developers must remember `-Pllm` to run these tests.

**Depends on:** D1 (location split — LLM tests live in blocks agentic-yaml)

**Sources:** wacky-manor AgentInvocationService (reference for AgentProvider usage pattern), platform ChatModelAgentProvider (CDI-managed, not usable without CDI)

**Exploration:** quick
**Status:** captured

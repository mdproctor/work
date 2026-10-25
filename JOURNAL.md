# Design Journal — issue-284-llm-observation-local-rules

## 2026-10-25 — Design session: LLM interpretation layer for stigmergy

### What we built
Complete design spec and implementation plan for LLM-driven observation and local rules (agency#284). 10 architectural decisions, design spec through 3-round standard review, 7-task implementation plan across 4 batches.

### Key architectural choices
- **Event-driven, not SPI** — LLM sits alongside the engine tick loop rather than implementing EnvironmentObserver (100ms hardcoded timeout makes the SPI unusable for LLM)
- **Composition via builder templates** — LlmSummarisers factory produces ContentSummariser implementations that plug into existing SummarisationRunner. No new runner type.
- **Handler dispatch** — LLM observes and groups events; caller-registered handlers keyed to the observation vocabulary decide what to do. Prompt and handlers are two sides of the same configuration.
- **InFlightGate at EmissionPolicy level** — prevents drain-before-skip data loss (garden entry GE-20261025-ea78d7)

### What's next
Implementation: Batch 1 (foundation records + SPIs + EmissionPolicy.window), then Batch 2 (ChangeGatedEmissionPolicy + InFlightGate), Batch 3 (handler dispatch), Batch 4 (LLM templates + integration test).

# HANDOFF — casehub-agency

## Session summary (2026-11-18, session 3)

Landed #447 (remove residual cognitive code — complete neocortex migration). Filed #493, #494. Scaffolded #490 for next session.

### Landed

| # | SHA | Summary |
|---|-----|---------|
| #447 | `effef119` | Delete 28 files (1429 lines) of memory hygiene code migrated to neocortex#1115. Re-wire AgencyBeans/AgencyAutoConfiguration. Update agentic-yaml imports. |

**Published:** agency canonical (3 commits) + workspace (8 commits) pushed to origin.

**Blog:** `docs/blog/2026-11-18-mdp01-the-bridge-that-outlived-its-reason.md`

### Filed

| # | Title | Scale | Complexity |
|---|-------|-------|------------|
| #493 | PersonalityEvolutionConfig compile failures (pre-existing) | S | Low |
| #494 | CognitionStack constructor verification | XS | Low |

### Next: #490 — Adopt unified prompt assembly from eidos-api

**Branch:** `issue-490-unified-prompt-assembly` (created, on main HEAD)
**Scale:** M | **Complexity:** Med

**What it does:**
- Delete 3 speech-api duplicates: `PromptSection`, `PromptContext`, `AssembledPrompt`
- Keep speech-specific: `SpeechPromptAssembler`, `ConversationTurn`
- Thin `AvatarCognition` to extend `CognitiveLifecycle` (from cognition-api, neocortex#1105)
- Simplify `CognitionAvatarAdapter` — cognitive lifecycle delegation to neocortex
- Update `speech-ws` to depend on eidos-api `AssembledPrompt` + cognition-api `CognitiveLifecycle`
- Update agentic-yaml test code (`ConversationRunner`, `DirectivePromptSection`, `CognitionDiagnostic`)

**Prerequisites (all landed):**
- eidos#240 — `AssembledPrompt` with optional `model` field
- neocortex#1105 — `CognitiveLifecycle` in cognition-api

**Key context:**
- The issue body has a complete spec — brainstorming can be light or skipped
- neocortex cognition module must be installed locally (`mvn install -DskipTests` on cognition + cognition-api)
- #493 (PersonalityEvolutionConfig) blocks full build of agency-spring and agentic-yaml — fix first or work around

### Queue

| # | Title | Status |
|---|-------|--------|
| #490 | Adopt unified prompt assembly from eidos-api | **active** |
| #493 | PersonalityEvolutionConfig compile failures | queued |
| #494 | CognitionStack constructor verification | queued |

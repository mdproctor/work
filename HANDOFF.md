# HANDOFF — casehub-neocortex

## Last Session

Unified prompt assembly across eidos, neocortex, and agency (#1105). Added `CognitiveLifecycle` SPI to cognition-api and wired CognitionCore as its implementation.

### Landed and published (33e67c71)

2 commits published to GitHub:

1. **feat:** `CognitiveLifecycle` interface in cognition-api — 4 lifecycle methods + `promptSections()` + default `contributeBlocks()` bridge that wraps `CognitionPromptRenderer` output into eidos `PromptBlock(COGNITIVE, tag, 0f, content)`. 7 unit tests.
2. **feat:** `CognitionCore implements CognitiveLifecycle` — two bridge overloads (`tick` with `Set<String>`, `recordInteraction` 5-param), 3 integration tests + 3 lifecycle tests.

### Issues closed

| # | Title |
|---|-------|
| #1105 | Unify prompt assembly across eidos, neocortex, and agency |

### Issues filed

| # | Title | Scale | Complexity |
|---|-------|-------|-----------|
| #1110 | Add salience computation to CognitiveLifecycle.contributeBlocks() | S | Low |
| #1111 | Defensive tag fallback in contributeBlocks() for anonymous renderers | XS | Low |

### Cross-repo state

- **eidos#240** — OPEN. Branch `issue-240-extend-prompt-types` exists with `AssembledPrompt.model` field. Prerequisite for agency adoption.
- **agency#490** — OPEN. No branch yet. Downstream consumer of `CognitiveLifecycle`. Deletes speech-api dead types, simplifies `CognitionAvatarAdapter`.

### Pre-existing test failures

4 tests failing in cognition module, pre-existing (not from this change):
- `AppraisalStatePropagationTest.highNoveltyAppraisalDrivesCuriosityViaHope` — tracked in #1037
- `EmotionRegulationTickParticipantTest` — 3 `dispatchCopingEffects` tests — tracked in #1037

### Queue status

Queue has #1105 appended at position 85. Epic #1050 (84 items) was completed in the prior session. The queue is active with no remaining items to work.

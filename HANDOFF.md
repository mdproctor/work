# HANDOFF — casehub-agency

## Session summary (2026-11-18, session 4)

Landed #490 (adopt unified prompt assembly from eidos-api). Filed #500, #501. Unpublished — 2 commits on canonical, 3 on workspace.

### Landed

| # | SHA | Summary |
|---|-----|---------|
| #490 | `7b769bcf` | Delete 3 speech-api duplicates (AssembledPrompt, PromptSection, PromptContext). Thin AvatarCognition to extend CognitiveLifecycle. CognitionAvatarAdapter gains promptSections(). speech-ws switches to eidos-api AssembledPrompt (userPrompt→userMessage). agentic-yaml test code migrated to CognitionPromptRenderer. |

**Blog:** `docs/blog/2026-11-18-mdp02-three-prompts-one-answer.md`
**Also fixed:** Extracted inline SVG in prior entry (`2026-11-18-mdp01-the-bridge-that-outlived-its-reason.md`) to `images/memory-hygiene-migration.svg` for Typora/CommonMark rendering.

**Unpublished:** agency canonical (2 commits) + workspace (3 commits) not yet pushed. Run `canonical_sync.py publish` to push.

### Filed

| # | Title | Scale | Complexity |
|---|-------|-------|------------|
| #500 | speech-demo: ensureQwen3SharedLibrary missing from Provisioner | XS | Low |
| #501 | consumer-guide: stale SocialAvatarCognition and prompt section references | S | Low |

### Pre-existing blockers

- **#493** (PersonalityEvolutionConfig compile failures) — blocks full build of `agency` CDI module, `agency-spring`, and `agentic-yaml` test compilation. The agentic-yaml test code changes from #490 are correct but can't be verified until #493 is fixed.
- **#494** (CognitionStack constructor verification) — queued, low priority.

### Queue

| # | Title | Status |
|---|-------|--------|
| #458 | Author 23 conversational body poses | stale — landed in prior session, not checked off |
| #457 | Avatar demo integrate M2M animation library | stale — landed in prior session, not checked off |
| #493 | PersonalityEvolutionConfig compile failures | queued |
| #494 | CognitionStack constructor verification | queued |
| #447 | Remove residual cognitive code | stale — landed session 3 |
| #500 | speech-demo ensureQwen3SharedLibrary missing | queued |
| #501 | consumer-guide stale cognition references | queued |

**Queue health:** 3 items are stale (landed but not checked off from prior sessions). Next session should clean the queue before starting new work.

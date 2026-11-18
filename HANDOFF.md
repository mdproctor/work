# HANDOFF — casehub-agency

## Session summary (2026-11-18, session 4)

Landed #490 (unified prompt assembly) and #493 (PersonalityEvolutionConfig compile fix). Filed #500, #501. All published.

### Landed

| # | SHA | Summary |
|---|-----|---------|
| #490 | `7b769bcf` | Delete 3 speech-api duplicates (AssembledPrompt, PromptSection, PromptContext). Thin AvatarCognition to extend CognitiveLifecycle. CognitionAvatarAdapter gains promptSections(). speech-ws switches to eidos-api AssembledPrompt (userPrompt→userMessage). agentic-yaml test code migrated to CognitionPromptRenderer. |
| #493 | `f31d78f2` | Remove PersonalityEvolutionOrchestrator producer from AgencyBeans/AgencyAutoConfiguration (CDI-managed in neocortex now). Update agentic-yaml to DispositionEvolutionConfig — 7 YAML fixtures, compiler, tests. |

**Blog:** `docs/blog/2026-11-18-mdp02-three-prompts-one-answer.md`
**Also fixed:** Extracted inline SVG in prior entry to `images/memory-hygiene-migration.svg`. Closed stale #457 on GitHub.

**Published:** All commits pushed to origin (agency canonical + workspace).

### Filed

| # | Title | Scale | Complexity |
|---|-------|-------|------------|
| #500 | speech-demo: ensureQwen3SharedLibrary missing from Provisioner | XS | Low |
| #501 | consumer-guide: stale SocialAvatarCognition and prompt section references | S | Low |

### Build status

Full project compiles except speech-demo (#500 — pre-existing `ensureQwen3SharedLibrary` missing from Provisioner). All test suites pass: 555 agency-core, 120 speech-ws, 416 agentic-yaml.

### Queue

| # | Title | Status |
|---|-------|--------|
| #458 | Author 23 conversational body poses | stale — landed, closed, not checked off |
| #457 | Avatar demo integrate M2M animation library | stale — landed, closed, not checked off |
| #447 | Remove residual cognitive code | stale — landed session 3, closed |
| #490 | Adopt unified prompt assembly | stale — landed this session, closed |
| #493 | PersonalityEvolutionConfig compile failures | stale — landed this session, closed |
| #494 | CognitionStack constructor verification | queued |
| #500 | speech-demo ensureQwen3SharedLibrary missing | queued |
| #501 | consumer-guide stale cognition references | queued |

**Queue health:** 5 stale items (landed+closed but unchecked). 3 actionable items remaining (#494, #500, #501). Next session should clean stale items first.

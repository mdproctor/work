# HANDOFF — casehub-agency

## Session summary (2026-11-18, session 3)

Completed #447 implementation — delete residual cognitive code from agency after neocortex#1115 migration.

### Branch: `issue-447-neocortex-cognitive-migration`

9 commits (needs rebase onto current main):

| Commit | Summary |
|--------|---------|
| `bdade558` | Delete 23 source + 5 test files migrated to neocortex#1115 |
| `f6227dd8` | Re-wire AgencyBeans — remove dead producers, add neocortex ReflectionQueryStore no-op |
| `49660465` | Re-wire AgencyAutoConfiguration — same cleanup, direct SPI reference |
| `2ffefccc` | Update agentic-yaml imports to neocortex cognition.memory |
| `ef5256b9` | Add casehub-neocortex-cognition provided dep to agentic-yaml pom |
| `2d73b1d9` | Fix: restore connectors-voice-sherpa dep accidentally dropped |
| `d1b5e31b` | Fix: remove MemoryHygieneSpiAdapter from CognitionStack test |
| `c7d86425` | Update CLAUDE.md — memory hygiene moved to neocortex |
| `1ad58499` | Blog: the bridge that outlived its reason |

### What was deleted (28 files, 1429 lines)

- `social/memory/` — 22 main files (MemoryHygieneOrchestrator, scorers, integrity, reflection stores, retention, events)
- `social/cognition/MemoryHygieneSpiAdapter` — bridge adapter
- 5 test files for the above

### What stays in agency

- `social/memory/evolution/` — 7 files (engine-api SPI implementations)
- `social/cognition/evolution/` — 4 files (engine-to-neocortex bridge)
- `social/cognition/CognitionAvatarAdapter` — handled by #490

### Build status

Core modules compile and pass tests (agency-core, agency, summarisation-api, cloudevents, summarisation-yaml, speech-api, speech-ws). Pre-existing failures in agency-spring and agentic-yaml from missing PersonalityEvolutionConfig in neocortex — tracked by #493.

### Filed issues

| # | Title | Scale | Complexity |
|---|-------|-------|------------|
| #493 | PersonalityEvolutionConfig compile failures (pre-existing) | S | Low |
| #494 | CognitionStack constructor verification | XS | Low |

Both appended to the queue.

### Queue state

The workspace queue diverged during this session — another session advanced it to #457/#458 while we worked on #447's feature branch. The queue no longer tracks #447. Next session should:
1. Rebase `issue-447-neocortex-cognitive-migration` onto current main
2. Resolve any conflicts (likely CLAUDE.md)
3. Land via work-end (may need queue repair to include #447)

### Key context for next session

**neocortex cognition module must be installed locally** before building agency. Run `mvn install -DskipTests` on neocortex's cognition and cognition-api modules first. The remote snapshot may be stale.

**#490 is unblocked** — eidos#240 and neocortex#1105 both landed. It handles the speech-api types and CognitionAvatarAdapter that this branch intentionally left alone.

### Unpublished

agency project: 9 commits on feature branch. agency workspace: specs and plans.

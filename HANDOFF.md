# HANDOFF — casehub-agency

## Session summary (2026-11-18, session 5)

Cleaned up post-migration debris: #494, #500, #501 — all landed and closed. Filed #502, #503 for remaining doc and sweep work. Queue synced (5 stale items fixed).

### Landed

| # | SHA | Summary |
|---|-----|---------|
| #494 | `1e1aeec6` | Replace null ConsolidationSummariser with no-op lambda in CognitionStack test helper. Constructor's 5th param changed from AgentProvider (nullable) to ConsolidationSummariser (not nullable) during neocortex migration. |
| #500 | `34bad637` | Rename `Provisioner.ensureQwen3SharedLibrary()` → `ensureQwen3Binary()` in speech-demo. Also renamed local wrapper method. |
| #501 | `a63b1feb` + `436a0f49` | Removed 12 ghost types from consumer-guide `io.casehub.agency.core.social.prompt` section. Replaced with actual `io.casehub.agency.social.cognition` types. Removed stale SocialCognitionDefaultBeans, situational context SPIs, attention mediator sections. Blog: "The Debris Field". |

**Blog:** `docs/blog/2026-11-18-mdp02-the-debris-field.md`

### Queue fix

Session started with 5 stale items in the queue (landed+closed on GitHub but unchecked): #458, #457, #493, #447, #490. All synced via plan_manager.py.

### Filed

| # | Title | Scale | Complexity |
|---|-------|-------|------------|
| #502 | contributor-guide: stale cognition references match consumer-guide cleanup | XS | Low |
| #503 | Post-extraction debris sweep: audit all test helpers and examples for stale neocortex refs | S | Low |

### Build status

Full project compiles. All test suites pass: agentic-yaml 416, speech-demo compiles (was broken before #500).

### Queue

| # | Title | Status |
|---|-------|--------|
| #502 | contributor-guide stale cognition refs | queued, next |
| #503 | post-extraction debris sweep | queued |

**Unpublished:** 4 commits on main ahead of origin. `canonical_sync.py publish` to push.

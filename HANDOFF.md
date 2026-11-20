# HANDOFF — casehub-agency (slot 223)

## Active — #527 Update speech-demo index.html to consume /api/avatar/config

**Branch:** `issue-525-avatar-voices-endpoint` (stacked — #525 landed, #527 next)
**Status:** Scaffolded — not started. Project repo on main, workspace on branch.

### What previous sessions delivered

**#519 — Migrate speech-demo SpeechProducers to AvatarDefinition YAML (LANDED)**

AvatarCompiler, LipSyncMode, AvatarRecorder + AvatarSpeechProcessor deployment pipeline. speech-demo reduced from 322-line SpeechProducers to 86-line avatar.yaml. Three-tier precedence: @DefaultBean → YAML → CDI override.

**#525 — Add /api/avatar/voices endpoint (LANDED)**

`GET /api/avatar/voices` returns sorted TtsModelRegistry keys. Frontend dropdown fetches dynamically instead of hardcoded options. Tradeoff: lost MOS scores and locale labels (filed as #528).

### Queue

```
- [x] #525 — Add /api/avatar/voices endpoint (landed)
- [ ] #527 — Update index.html config consumption ← active (not started)
- [ ] #526 — Zero-Java avatar self-wiring extension
- [ ] #528 — Add voice metadata to voices response
- [ ] #529 — Default voice selection in dynamic dropdown
```

### Issues filed this session

| # | Title | Scale | Complexity |
|---|-------|-------|------------|
| #528 | Add voice metadata to /api/avatar/voices response | S | Med |
| #529 | Default voice selection in dynamic dropdown | XS | Low |

### Design direction noted

Zero-Java avatar deployment target: any Pages + Mosaic app gets a talking avatar by adding a dependency and dropping config. Tracked in #526. #528 and #529 are UX follow-ups to #525's dynamic dropdown.

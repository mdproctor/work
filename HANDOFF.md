# HANDOFF — casehub-agency (slot 223)

## Active — #449 Avatar reusable mosaic component with YAML configuration

**Branch:** `issue-449-avatar-mosaic-component` (3 commits on branch, not yet landed)
**Status:** All 5 plan tasks complete. Code review fixes applied. Blog written. Follow-up issues filed and scheduled.

### What this session delivered

**Agency — YAML spec + deployment (3 commits):**
- `AvatarDefinition`, `TtsSpec`, `TtsEngineSpec`, `TtsVoiceSpec`, `SttSpec` — 5 Jackson-deserializable records in `agentic-yaml/spec/`
- `TtsVoiceSpec` supports both string shorthand (`- af_heart`) and object form (`- name: af_heart, model: custom`)
- `AvatarConfigBuildItem` + build steps in `AgenticYamlProcessor` — discovers `META-INF/avatar.yaml`
- `AvatarConfigBean` + `AvatarConfigResource` — REST endpoint at `/api/avatar/config` serving compiled config JSON
- `AgenticRecorder.createAvatarConfigBean()` — registers bean as CDI singleton via Quarkus recorder
- 7 serialization tests passing

**Mosaic — component decomposition (4 commits on main):**
- `AvatarSession` — shared EventTarget service extracted from `AvatarWsController`. Fetches config from `/api/avatar/config`, owns WebSocket, publishes typed events (`avatar:turn`, `avatar:audio`, `avatar:status`, `avatar:timing`, `avatar:config-loaded`, `avatar:config-changed`). 11 tests.
- `casehub-avatar-controls` — data-driven LLM/voice/speed selectors from `AvatarSession.config`. Zero hardcoded options. 8 tests.
- `.session` property added to `casehub-avatar`, `casehub-transcript`, `casehub-speech` — each subscribes to session events when set, falls back to existing property-based API for standalone use
- `casehub-avatar-panel` refactored from 293-line monolith to 107-line thin shell — creates `AvatarSession` internally when none provided, passes it to all children via `.session`
- Removed 88 hardcoded voice entries (the `VOICE_GROUPS` constant)
- 60 total tests passing, typecheck clean

**Code review fixes applied:**
- CRITICAL: build steps were inserted into main agency repo via IntelliJ MCP, not the slot copy — re-applied to slot
- Panel `_session` getter returns nullable with optional chaining at all call sites
- `casehub-speech` session property typed with structural interface instead of `any`
- `casehub-transcript` binds session in `connectedCallback` for consistency

**Design spec:** `docs/specs/2026-11-19-avatar-mosaic-component-design.md`
**Blog entry:** "The Avatar Is Not the Agent" — body/mind separation in avatar architecture

### Cross-repo commits (mosaic)

4 commits on `main` in `casehub-mosaic`:
- `b81c1f73` — extract AvatarSession service
- `7dc5774c` — add casehub-avatar-controls
- `99823d2b` — decompose avatar panel
- `7e56a6ee` — review fixes (null safety, type safety, session binding)

### Issues filed

| # | Repo | Title | Scale | Complexity |
|---|------|-------|-------|------------|
| #518 | agency | Add kebab-case `@JsonProperty` aliases to AvatarDefinition | XS | Low |
| #519 | agency | Migrate speech-demo SpeechProducers to AvatarDefinition YAML | M | Med |
| mosaic#326 | mosaic | Deprecate AvatarWsController — replaced by AvatarSession | XS | Low |
| mosaic#327 | mosaic | Rename types.ts AvatarConfig to avoid confusion with AvatarSessionConfig | XS | Low |

### Queue

```
- [x] #424
- [x] #449 ← this session (all tasks complete, branch not yet landed)
- [ ] #518 — Add kebab-case aliases (XS/Low)
- [ ] #519 — Migrate speech-demo to YAML (M/Med)
```

### To land #449

The branch has 3 commits. Before landing:
1. Run `git-squash` on the branch (3 commits — may compact to 2)
2. Run `work-end` ceremony — will rebase onto main and FF-merge

The mosaic commits are already on main (not gated by the ceremony).

### Known issues

- `AvatarWsController` is orphaned in mosaic — still exported, still tested, no consumer. mosaic#326 tracks deprecation.
- YAML uses camelCase property names. agency#518 tracks adding kebab-case aliases.
- `speech-demo/SpeechProducers` still hardcodes all CDI wiring. agency#519 tracks migration to `AvatarDefinition` YAML.

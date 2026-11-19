# HANDOFF — casehub-agency (slot 223)

## Active — #519 Migrate speech-demo SpeechProducers to AvatarDefinition YAML

**Branch:** `issue-519-migrate-speechproducers-to-avatar-yaml`
**Status:** Scaffolded — branch created, queue active, no implementation yet.

Both project and workspace repos are checked out on the feature branch. `work continue` will route to `load_context` → brainstorming.

### What this session delivered

**#521 — Add kebab-case @JsonAlias to speech-ws protocol DTOs (DONE)**

Landed on main as 2 commits: `f634c9f1`, `a0850456`. Post-ceremony fix: `0451a95e`.

- **Gson→Jackson migration:** Replaced hand-written Gson `MessageCodec` (130 lines of switch/JsonObject) with Jackson `ObjectMapper` backed by `@JsonTypeInfo`/`@JsonSubTypes` on the `AvatarMessage` and `Cue` sealed hierarchies
- **Kebab-case aliases:** 16 multi-word record components across `AvatarMessage`, `Cue`, `VisemeFrame` now accept kebab-case via `@JsonAlias`
- **Type discriminator aliases:** 5 multi-word type names (`playbackComplete`, `userActivity`, `speakerIdentify`, `speakerPrompt`, `turnComplete`) accept kebab-case via `@JsonSubTypes.Type(names={...})`
- **Wire format fix:** `@JsonInclude(NON_NULL)` on `Start` and `Text` records to prevent null fields appearing in output (caught by code review)
- **sampleRate default:** `@JsonCreator` factory on `Start` preserves the 16000 default when field is absent
- **Tests:** 32 tests (17 migrated from Gson, 12 kebab-case, 3 null-field suppression). 205 total speech-ws tests pass.
- **Blog revised:** "The Config Your Fingers Expect" updated with WebSocket protocol migration section and new SVG diagram
- Dependency swapped: `com.google.code.gson:gson` → `com.fasterxml.jackson.core:jackson-databind`

### Issues filed

| # | Repo | Title | Scale | Complexity |
|---|------|-------|-------|------------|
| #523 | agency | Remove Gson dependency from speech-sherpa module | S | Med |
| #524 | agency | Add @JsonInclude(NON_NULL) to remaining AvatarMessage records | XS | Low |

### Queue

```
- [x] #521 — Gson→Jackson migration + kebab aliases for speech-ws (landed)
- [ ] #519 — Migrate speech-demo SpeechProducers to AvatarDefinition YAML ← active (scaffolded)
- [ ] #424 — multi-swarm-review-findings
- [ ] #523 — Remove Gson from speech-sherpa
- [ ] #524 — NON_NULL audit remaining records
```

### Published

- agency: 3 commits to origin + fork (feat, test, fix)
- public/agency: 8 commits to origin (workspace artifacts)

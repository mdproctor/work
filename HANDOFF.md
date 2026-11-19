# HANDOFF — casehub-agency (slot 223)

## Active — #521 Add kebab-case @JsonAlias to speech-ws protocol DTOs

**Branch:** none yet (needs scaffolding)
**Status:** Queued, not started.

### What this session delivered

**#520 — Add kebab-case @JsonAlias to all agentic-yaml spec records (DONE)**

Landed on main as 3 commits: `cb45da0f`, `740558b8`, `60943f65`.

- 242 multi-word camelCase record components across 57 spec files now accept kebab-case YAML via `@JsonAlias`
- CognitionDefinition personality field merges semantic alias `"personality"` with naming alias `"personality-evolution"` via array form `@JsonAlias({"personality", "personality-evolution"})`
- 30 kebab-case deserialization tests across 7 nested test classes (PatternDefinition, GovernanceSpec, TerminationSpec, JudgmentSpec, TopologySpec, CallerConfigSpec, CognitionDefinition + sub-specs, World specs, secondary specs)
- Code review: clean — 1 MINOR (annotation ordering in GovernanceSpec, fixed), 3 unused imports removed
- Blog revised: "The Config Your Fingers Expect" updated from #518 scope (3 records) to full #520 scope (57 files, 242 fields). SVG diagram updated.

### Issues filed

| # | Repo | Title | Scale | Complexity |
|---|------|-------|-------|------------|
| #521 | agency | Add kebab-case @JsonAlias to speech-ws protocol DTOs | S | Low |

### Queue

```
- [x] #520 — kebab aliases for agentic-yaml (landed)
- [x] #519 — ⚠️ INCORRECTLY MARKED DONE by ceremony (see below)
- [ ] #521 — Add kebab-case aliases to speech-ws protocol DTOs ← active
```

### Known issues

- **#519 incorrectly closed in queue:** The close ceremony marked #519 as `[x]` when it landed #520's branch. #519 (Migrate speech-demo SpeechProducers to AvatarDefinition YAML) has NOT been implemented. GitHub issue is still OPEN. The plan_manager won't re-add it (duplicate detection). Next session should either manually fix the queue or create a fresh queue entry.

### Published

All 7 repos published to remotes:
- agency (4 commits), public/agency (2), connectors (1), engine (1), public/connectors (2), public/engine (1), public/neocortex (1)

All blog entries present at all destinations.

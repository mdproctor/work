# Handoff — casehub-blocks

**Branch:** issue-129-drive-architecture (squashed, ready to land)
**Issue:** casehubio/blocks#129
**Date:** 2026-08-21

## Status

Implementation complete. 44 tests passing. Code review and branch audit clean.
Squashed to 3 commits. **Blocked on landing** — original blocks repo diverged
from origin/main (11 ahead, 10 behind).

## What Was Built

Drive Architecture — intrinsic motivation system for the social cognition stack.
Layer 1 of three (Layer 2: autonomous goals #136, Layer 3: narrative identity #142).

**New package:** `io.casehub.blocks.agentic.social.drive`
- `DriveOrchestrator` — compositor lifecycle (tick/cache/accessor, no record())
- `DriveComposer` — personality + mood modulation, weighted composition
- `DriveSource` — functional interface SPI
- `CuriosityDrive` — reads MemoryHygiene knowledge gaps
- `CompetenceDrive` — reads StrategyLearning engagement trends
- `AffiliationDrive` — reads UserModel familiarity decay
- `AutonomyDrive` — reads MentalModel intention pressure
- Foundation types: DriveAxis, DriveIntensity, DriveProfile, DriveTick, DriveConfig

**Upstream API additions (additive, no breaking changes):**
- `MemoryHygieneOrchestrator.knowledgeGaps()` + `KnowledgeGapSummary` (in memory package)
- `StrategyLearningOrchestrator.engagementTrend()` + `EngagementTrend` (in social package)
- `UserModelOrchestrator.activeProfiles()`
- `MentalModelOrchestrator.activeSnapshots()`

**Also fixed:** pre-existing compile errors from qhorus MessageView/MessageReceivedEvent
constructor changes (separate commit).

## Commits (squashed)

1. `fix: adapt to qhorus MessageView/MessageReceivedEvent constructor changes`
2. `feat(#129): add Drive Architecture — intrinsic motivation from social cognition outputs`
3. `docs(#129): add ADR-0001 compositor pattern, update CLAUDE.md and contributor guide`

## Key Design Decisions

- **Compositor pattern** (ADR-0001): DriveOrchestrator has tick() without record() —
  pulls from source orchestrators rather than accumulating signals
- **DriveComposer separate** from orchestrator (decision review revision D4)
- **Personality via AgentDescriptor.disposition()** tick parameter (D5 revision)
- **Types in source packages**: KnowledgeGapSummary in memory, EngagementTrend in social
  (code review fix — avoids circular dependency)
- **In-memory only** — drives are derivable state, no persistence

## Blocker

`merge-slot` preflight fails:
```
ERROR=diverged_main path=/Users/mdproctor/claude/casehub/blocks ahead=11 behind=10
```

To resolve:
1. Open a session on the original blocks repo (`/Users/mdproctor/claude/casehub/blocks`)
2. Sync main: `git pull --rebase origin main` (or resolve manually if conflicts)
3. Re-run: `python3 ~/.claude/skills/work-slot/slot_manager.py merge-slot /Users/mdproctor/claude/casehub slot=145`

## Follow-up Issues to File

| Title | Scope |
|-------|-------|
| InnerLife reads DriveProfile for proactive initiation | MotivationAssessment evolves to use drive intensity |
| DriveStore persistence for Layer 2 consumption | Add optional persistence when GoalProposer needs drive history |
| Narrative feedback into drive modulation (Layer 3) | NarrativeOrchestrator feeds back into drive system |

## What's Next

| Item | Scale | Complexity |
|------|-------|------------|
| Resolve blocks main divergence and land slot 145 | XS | Low |
| Autonomous Goal Generation (#136) — Layer 2 | L | High |
| Narrative Identity + Social Emergence (#142) — Layer 3 | L | High |

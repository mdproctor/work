# HANDOFF — casehub-neocortex

## Last Session

Completed #1213 (XS/Low bug fix).

**#1213 — AgentDescriptor constructor mismatch in SituationalPersonality tests.** The `AgentDescriptor` record (eidos) gained a `RoleProfile roleProfile` field as its 31st parameter. Four test helper methods in `SituationalPersonalityIntegrationTest` (1) and `SituationalPersonalityParticipantTest` (3) used raw positional constructors with 30 arguments — each needed `null` appended. Fix: add `null` for `roleProfile` in all 4 calls. Tests compile and pass.

**Blog:** `blog/2026-11-23-mdp05-thirty-one-nulls.md` — positional constructor fragility in 31-field records.

**Filed:**
- #1215 (refactor: use AgentDescriptor.builder() in SituationalPersonality test helpers — prevents this class of breakage)
- Hortora/soredium#628 (work pipeline alignment bug — workspace left on old branch after work-end)

## Pipeline State Warning

The work pipeline has an alignment bug (soredium#628): after the previous session's work-end landed branches to main, the workspace stayed on `issue-1167-per-agent-retention-adapt` with uncommitted `.queue` changes. The `continue` pipeline completed without detecting the mismatch. Manual branch creation was required for #1213.

**Queue discrepancy:** #1214 (style: FQ imports in cognition production code) is marked `[x]` in the queue but was never worked on. It should be un-checked and either activated or re-queued. The GitHub issue remains OPEN.

## Queue State

#1215 (refactor: AgentDescriptor builder in test helpers) is active. #1214 needs its queue state corrected.

## Immediate Next Step

Fix #1214 queue state (un-mark done), then work #1215 (XS/Low) or #1214 (XS/Low) — both are small cleanup tasks in the same area.

# HANDOFF — casehub-neocortex

## Last Session

Completed #1213 (XS/Low bug fix). Another slot had already independently fixed the same issue — our branch was redundant and deleted.

**#1213 — AgentDescriptor constructor mismatch in SituationalPersonality tests.** The `AgentDescriptor` record (eidos) gained a `RoleProfile roleProfile` field as its 31st parameter. Four test helper methods in `SituationalPersonalityIntegrationTest` (1) and `SituationalPersonalityParticipantTest` (3) used raw positional constructors with 30 arguments — each needed `null` appended. Fix already on main from another session (`4b23a6cd0`).

**Blog:** `blog/2026-11-23-mdp05-thirty-one-nulls.md` — positional constructor fragility in 31-field records. Published.

**Filed:**
- casehubio/neocortex#1215 (refactor: use AgentDescriptor.builder() in SituationalPersonality test helpers — prevents this class of breakage)
- Hortora/soredium#628 (work pipeline alignment bug — workspace left on old branch after work-end)

## Pipeline State Warning

The work pipeline has an alignment bug (soredium#628): after a work-end lands branches to main, the workspace can be left on the old feature branch with uncommitted `.queue` changes. The `continue` pipeline completes without detecting the mismatch. Manual branch creation was required this session. The queue also incorrectly auto-marked #1214 as done (now corrected via `uncheck`).

## Queue State

Both project and workspace are now on branch `issue-1215-agentdescriptor-builder-test-helpers`. Queue state:

- **#1214** (style: FQ imports in cognition production code — XS/Low) — open, not started
- **#1215** (refactor: AgentDescriptor.builder() in test helpers — XS/Low) — **active**, scaffolded

## Immediate Next Step

#1215 (XS/Low) — refactor the 4 `descriptorWith*()` helper methods in `SituationalPersonalityParticipantTest` and `SituationalPersonalityIntegrationTest` to use `AgentDescriptor.builder()` instead of the raw 31-argument positional constructor. Files:
- `cognition/src/test/java/io/casehub/neocortex/cognition/situational/SituationalPersonalityParticipantTest.java`
- `cognition/src/test/java/io/casehub/neocortex/cognition/situational/SituationalPersonalityIntegrationTest.java`

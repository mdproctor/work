# HANDOFF — casehub-agency

## Last Session

Implemented #423 — 24 behavioral e2e tests for multi-swarm coordination across
6 scenarios (consumer setup, signal lifecycle, convergence, dynamic membership,
strategy switching, error paths). Exercises public API only. Landed on main as
`6c865717`, issue closed.

Key finding: AGM belief revision makes CONFLICT notifications and escalation
gates dead code. `revise()` restores consistency by construction — the
post-revision check can never fire. BJT scoring sensitive to Jaccard overlap
below ~0.33 (tests use high-overlap capabilities). Pre-existing dependency fix:
`casehub-neocortex-inference-bayes` needed rebuild for `getInstanceSnapshot()`.

Started #424 (multi-swarm review findings — 12 findings epic). Branch created,
no implementation yet.

## Queue Repair Needed

`.queue` was reset during #424 start. Shows #376/#361/#363 as unchecked — all
done. #423/#424 missing. Fix: check off completed items, add #424 as active.

## Active Branch

`issue-424-multi-swarm-review-findings` — project and workspace.

## References

| Artifact | Path |
|----------|------|
| Behavioral tests | `agency-core/src/test/.../multiswarm/MultiSwarmBehavioralTest.java` |
| Design spec | `specs/issue-423-behavioral-e2e-multi-swarm/2026-11-08-*-design.md` |
| Blog | `blog/2026-11-08-mdp01-when-all-the-tests-pass-and-half-the-system-is-dead.md` |
| Plan | `plans/2026-11-08-behavioral-e2e-multi-swarm.md` |
| Issue #424 body | 12 findings: F1 pipeline wiring, F2 concurrency, F3 lifecycle, F4-F12 quality |

# HANDOFF — casehub-agency

## Last Session

Landed agency#362 — wired the existing prompt optimisation framework to the swarm coordinator's three LLM call sites (team formation, collective reasoning, provisioning). Core framework was already generic; swarm-specific work was leaf classes only. Filed agency#366 to unify the prompt batch cycle with engine#1115's evolution loop (landed same day). Engine session also filed agency#365 (wire EvolutionTicker into CognitionCore.tick).

Fixed workspace corruption: repointed `proj` symlink from old `blocks` path to `agency`, cleaned stale `.plan` on main.

## Immediate Next Step

Pick up agency#365 or #366 — both depend on engine#1115 (landed). #365 is cognitive timing integration, #366 is prompt optimisation as an evolution participant.

## Cross-Module

- **engine#1259** — SwarmCoordinationListener impl needed for real lifecycle events (blocks full swarm event flow)
- **engine#1115** — landed. Evolution loop infrastructure that #365 and #366 integrate with.

## References

- New package: `agency-core/.../orchestration/swarm/prompt/` (SwarmPromptSignatures, SwarmOptimisedFewShotSection, SwarmOutcomeMetric, SwarmOutcomeMapper)
- Blog: `blog/2026-10-26-mdp01-the-framework-was-already-generic.md`
- Engine evolution: `engine/runtime-core/.../improvement/EvolutionTicker.java`

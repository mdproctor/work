# HANDOFF — casehub-agency

## Last Session

Completed agency#365 — wired engine EvolutionTicker into the cognitive agent's tick cycle. Four new types in `io.casehub.agency.social.cognition.evolution`: `EvolutionTickBridge` (@FunctionalInterface SPI), `CognitiveEvolutionConfig` (curiosity thresholds), `CircuitBreakerMoodMapper` (TickTrace gate → MoodSignal.DirectShift), `CognitiveEvolutionAdapter` (composition root with CURIOSITY-modulated tick frequency). `CognitionAvatarAdapter` gained a backward-compatible four-arg constructor that calls the adapter after `core.tick()`. 15 tests, landed as `af4d1947` on main, issue closed.

Also completed agency#366 — prompt optimisation as an evolution loop participant. Five new types in `io.casehub.agency.optimisation.prompt.evolution`. Landed as `6181a111`.

## Immediate Next Step

Both #365 and #366 are done. Check issue backlog for next work.

## Cross-Module

- **engine#1259** — SwarmCoordinationListener impl needed for real lifecycle events (blocks full swarm event flow)

## References

- New package: `agency-core/.../social/cognition/evolution/` (#365 — landed)
- New package: `agency-core/.../optimisation/prompt/evolution/` (#366 — landed)
- Engine evolution: `engine/runtime-core/.../improvement/EvolutionTicker.java`

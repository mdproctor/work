# HANDOFF — casehub-agency

## Last Session

Completed agency#365 — wired engine EvolutionTicker into the cognitive agent's tick cycle. Four new types in `io.casehub.agency.social.cognition.evolution`: `EvolutionTickBridge` (@FunctionalInterface SPI), `CognitiveEvolutionConfig` (curiosity thresholds), `CircuitBreakerMoodMapper` (TickTrace gate → MoodSignal.DirectShift), `CognitiveEvolutionAdapter` (composition root with CURIOSITY-modulated tick frequency). `CognitionAvatarAdapter` gained a backward-compatible four-arg constructor that calls the adapter after `core.tick()`. 15 tests, landed as `af4d1947` on main, issue closed.

Also completed agency#366 — prompt optimisation as an evolution loop participant. Five new types in `io.casehub.agency.optimisation.prompt.evolution`. Landed as `6181a111`.

Filed agency#367 — memory hygiene as the second evolution loop participant. Builds directly on #365 and #366.

Published 4 blog entries (2 from Oct 25, 2 from Oct 26) to personal-notes and casehub-notes.

## Immediate Next Step

Start agency#367 — wire `MemoryHygieneOrchestrator` as an evolution loop participant. Follow the pattern established by `PromptOptimisationProposalSource` (#366). New types needed:
- `ImprovementProposalSource` impl proposing retention/consolidation/scoring adjustments
- `RegressionEvaluator` impl monitoring memory quality metrics
- `ImprovementCategoryProvider` for memory-hygiene tuning category
- Wiring into `MemoryHygieneScheduler.maintain()` tick

Run `work start #367` to begin.

## Cross-Module

- **engine#1259** — SwarmCoordinationListener impl needed for real lifecycle events (blocks full swarm event flow)

## References

- New package: `agency-core/.../social/cognition/evolution/` (#365 — landed)
- New package: `agency-core/.../optimisation/prompt/evolution/` (#366 — pattern to follow for #367)
- `MemoryHygieneOrchestrator` — `agency-core/.../social/memory/`
- `MemoryHygieneScheduler` — `agency-core/.../social/memory/`
- Engine evolution SPIs: `ImprovementProposalSource`, `RegressionEvaluator`, `ImprovementCategoryProvider` — `engine-api`
- Engine evolution: `engine/runtime-core/.../improvement/EvolutionTicker.java`

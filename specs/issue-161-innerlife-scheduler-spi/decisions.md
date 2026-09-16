## D1: Formalise CognitionCore as the scheduler

**Choice:** Make CognitionCore's ordering declarative via a phase model and bring InnerLife under its ordering contract, rather than extracting a separate CognitionScheduler SPI.
**Alternatives:**
- Extract a separate `CognitionScheduler` SPI — adds indirection without clear consumer benefit; CognitionCore already owns the composition
**Rationale:** CognitionCore is the natural composition root. The problem is implicit ordering and InnerLife's exclusion, not who schedules.
**Trade-offs:** CognitionCore gains more responsibility (phase dispatch) but avoids a new abstraction layer
**Sources:** CognitionCore.java (lines 97-137), InnerLifeOrchestrator.java (line 97)
**Exploration:** quick
**Status:** captured

## D2: Open participation with built-in defaults

**Choice:** Consumers can register custom `CognitionTickParticipant` implementations at specific phases. By default, only the built-in orchestrators are registered — consumers who don't extend get the same behaviour as today.
**Alternatives:**
- Configuration-only — consumers toggle built-in orchestrators but cannot inject custom participants. Simpler but closed to extension.
**Rationale:** Open participation is opt-in. No consumer pays complexity unless they add custom participants. Future-proofs without penalising the default case.
**Trade-offs:** Slightly more complex participant registration API
**Sources:** Issue #161 scope ("consumer implementations can extend with timer-based or event-based strategies")
**Exploration:** quick
**Status:** captured

## D3: Phase enum for ordering

**Choice:** Named phases (`FOUNDATION`, `SOURCE`, `SOURCE_PER_SUBJECT`, `DERIVED`, `TERMINAL`) with participants registered at a phase. Within a phase, ordering is undefined.
**Alternatives:**
- Dependency declaration (`after(X)` / `before(Y)`) — flexible but can produce surprising orderings and cycle errors
- Integer priority — simple but not self-documenting, fragile to collisions
**Rationale:** Phases capture the actual semantic structure (foundation → source → derived → terminal). Self-documenting, readable, avoids graph resolution complexity.
**Trade-offs:** Less flexible than dependency graphs — a participant that needs "after X but before Y within the same phase" has no mechanism
**Sources:** CognitionCore.java tick ordering analysis, #136 spec ordering constraint
**Exploration:** quick
**Status:** captured

## D4: Per-subject loop stays special

**Choice:** The per-subject iteration (userModel, mentalModel) remains hardcoded in CognitionCore as the `SOURCE_PER_SUBJECT` phase. Custom per-subject participants are not supported through the SPI.
**Alternatives:**
- Subject-aware participant interface with `tickPerSubject()` variant — adds interface complexity for a pattern used by exactly two orchestrators
**Rationale:** Per-subject ticking is a specific pattern with no consumer extension need. A consumer needing per-subject custom ticking can register a global participant that runs its own subject loop internally.
**Trade-offs:** Consumers cannot register per-subject participants — must handle subject iteration themselves
**Sources:** CognitionCore.java (lines 125-132), SubjectResolver SPI
**Exploration:** quick
**Status:** captured

## D5: InnerLife joins TERMINAL phase (revised)

**Choice:** InnerLife registers at the TERMINAL phase alongside GoalProposalOrchestrator. It runs every tick cycle, with its existing content quality gate filtering most ticks to Silent at microsecond cost. The redundant `driveOrchestrator.tick()` call inside InnerLife.doTick() is removed — structural ordering guarantees DERIVED has completed.
**Alternatives:**
- Ordering-only integration — InnerLife stays outside the tick cycle but with a documented ordering contract. Creates unenforced protocol: nothing prevents `evaluateProactive()` from being called without a prior `tick()`, leading to stale/empty drive data.
**Rationale:** InnerLife's content quality gate (empty buffer check, observation count, novelty threshold) short-circuits most ticks before the LLM call. The per-tick cost is a null check and integer comparison — microseconds, not wasteful. Full integration eliminates: (a) the defensive re-tick of drives, (b) the unenforced ordering protocol, (c) InnerLife as a special case outside the phase model.
**Trade-offs:** InnerLife runs on every tick cycle (negligible cost when gated). `evaluateProactive()` on SocialAvatarCognition reads InnerLife's cached result from the last tick instead of triggering evaluation. InnerLife's tick signature needs adapting to match the participant interface (currently takes `AgentDescriptor` + `channelContext`; channelContext can be null during scheduled ticks, with on-demand proactive evaluation passing it separately).
**Sources:** InnerLifeOrchestrator.java (doTick line 97, content quality gate lines 130-143), ProactiveSpeechSupport.java, SocialAvatarCognition.java (evaluateProactive), decision review R1-01/R1-02/R1-03
**Exploration:** quick → surfaced-by-review
**Status:** revised

## D6: Fix drive ordering — move after source orchestrators

**Choice:** Move drives from current position 4 (before strategy, userModel, mentalModel) to DERIVED phase (after SOURCE and SOURCE_PER_SUBJECT), fixing the data staleness for 3 of 4 drive sources.
**Alternatives:**
- Keep current ordering — drives sees one-tick-stale data from CompetenceDrive, AffiliationDrive, AutonomyDrive. Preserves current behaviour but contradicts #136 spec.
**Rationale:** The #136 spec says "source orchestrators → DriveOrchestrator → GoalProposalOrchestrator". Current ordering violates this. CompetenceDrive reads StrategyLearningOrchestrator, AffiliationDrive reads UserModelOrchestrator, AutonomyDrive reads MentalModelOrchestrator — all tick after drives today.
**Trade-offs:** Changes observable behaviour — drives will now see current-tick data instead of previous-tick data. Unlikely to cause issues but is a semantic change.
**Sources:** DriveOrchestrator.java (CuriosityDrive, CompetenceDrive, AffiliationDrive, AutonomyDrive), #136 spec ordering constraint, CognitionCore.java tick ordering
**Exploration:** quick
**Status:** captured

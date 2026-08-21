# Design Decisions — Drive Architecture (#129)

## D1: Input model

**Choice:** Pull — drives read `currentXxx()` from source orchestrators
**Alternatives:**
- Tick outputs as signals — wire *Tick outcomes as DriveSignals via adapter. Adds push plumbing.
- Event-bus subscription — EventStreamBus pub/sub. Overkill for 4 data sources.
**Rationale:** Drives are fundamentally consumers of existing orchestrator state. Pull model avoids new signal types and wiring.
**Trade-offs:** Drives depend on source orchestrators being ticked first. Caller must ensure ordering.
**Sources:** Research blog (2026-08-21-mdp04), existing orchestrator record()/tick() pattern
**Exploration:** quick
**Status:** captured

## D2: Drive structure

**Choice:** Distinct classes per drive — CuriosityDrive, CompetenceDrive, AffiliationDrive, AutonomyDrive
**Alternatives:**
- Generic DriveSource<T> — single parameterised class. Extraction logic differs enough to make the generic forced.
- Enum-driven with strategy — DriveAxis enum with DriveEvaluator per axis. Conflates identity with evaluation.
**Rationale:** Each drive reads fundamentally different data (reflections vs engagement trends vs familiarity decay vs intention projections). Distinct classes match distinct extraction logic.
**Trade-offs:** More classes. Acceptable given each is small and focused.
**Sources:** Issue #129 epic children, SDT (Deci & Ryan) four-axis model
**Exploration:** quick
**Status:** captured

## D3: Tick pattern

**Choice:** Hybrid — tick() evaluates + caches DriveProfile, currentDrives() returns cached state, no record()
**Alternatives:**
- tick() only (no cache) — recomputes every call. Breaks orchestrator pattern.
- Pure accessor (no tick) — always recomputes. No lifecycle control.
**Rationale:** Consistent with other orchestrators (tick updates state, accessor reads it). No record() because drives pull rather than receive pushes. Idempotent between ticks.
**Trade-offs:** None significant — this is the established pattern minus record().
**Sources:** PersonalityEvolutionOrchestrator, MoodOrchestrator patterns (contributor guide)
**Exploration:** quick
**Status:** captured

## D4: Composer scope

**Choice:** Internal to DriveOrchestrator — composition runs inside tick()
**Alternatives:**
- Separate DriveComposer class — separates evaluation from composition. Adds coordination point.
- DriveComposer as SPI — pluggable composition strategy. YAGNI for Layer 1.
**Rationale:** tick() already evaluates all drives; composing them is the natural final step. One class, one tick call, one cached result.
**Trade-offs:** If composition logic becomes complex, it's harder to test in isolation. Unlikely given composition is weighted averaging with modulation.
**Sources:** Research blog Layer 1 description
**Exploration:** quick
**Status:** captured

## D5: Modulation

**Choice:** Direct orchestrator dependencies — constructor takes PersonalityEvolutionOrchestrator and MoodOrchestrator
**Alternatives:**
- Config-only modulation — static personality-to-drive mappings. Loses dynamic emergence.
- Signal-based modulation — personality/mood push signals. Contradicts D1 pull model.
**Rationale:** Personality and mood are live state that changes between ticks. Reading them directly is consistent with the pull model. Six constructor params (4 source + personality + mood) is within reason.
**Trade-offs:** Constructor has many dependencies. Acceptable for a compositor that by definition composes across orchestrators.
**Sources:** Research blog ("personality modulates drive intensity"), PersonalityEvolutionOrchestrator currentTraits() API
**Exploration:** quick
**Status:** captured

## D6: Persistence

**Choice:** In-memory only — drives are derived state, recomputed on tick
**Alternatives:**
- DriveStore SPI with CbrDriveStore — persist for historical analysis. YAGNI for Layer 1.
- Interface-only (no-op default) — reserves extension point. Middle ground but still YAGNI.
**Rationale:** Drive state is fully derivable from source orchestrators' state. If the agent restarts, source orchestrators restore from their stores, and the next tick() recomputes drives. Same pattern as MoodOrchestrator.
**Trade-offs:** No drive history for trend analysis. Can add a store when Layer 2/3 needs it.
**Sources:** MoodOrchestrator (no store), StrategyLearningOrchestrator (has store — for non-derivable state)
**Exploration:** quick
**Status:** captured

## D7: MotivationAssessment integration

**Choice:** Leave as-is in this issue. File follow-up issue for InnerLife integration.
**Alternatives:**
- Evolve in this issue — InnerLife derives score from DriveProfile. Expands scope of #129.
- Replace entirely — DriveProfile subsumes MotivationAssessment. Largest scope expansion.
**Rationale:** Keeps #129 focused on the drive system. Integration is tracked as a follow-up, not forgotten.
**Trade-offs:** Temporary disconnection between drives and InnerLife's proactive initiation. Tracked via issue.
**Sources:** MotivationAssessment.java (package-private record in social/)
**Exploration:** quick
**Status:** captured

## D8: Type system architecture

**Choice:** DriveSource SPI + DriveOrchestrator composition
**Alternatives:**
- Monolithic DriveOrchestrator with inline evaluation — all drive logic in private methods. Simpler but harder to test individually, doesn't compose for Layer 3.
**Rationale:** DriveSource is a lightweight functional interface (one method). Each drive is independently testable. New drive axes can be added without touching the orchestrator. Layer 3 narrative feedback can add new sources.
**Trade-offs:** One extra interface. Justified by testability and extensibility for Layer 3.
**Sources:** GE-20260729-172d18 (check before creating — confirmed no existing DriveSource SPI), research blog Layer 3 feedback loop
**Exploration:** deep-analysis
**Depends on:** D2 (distinct classes implement DriveSource)
**Status:** captured

## D9: API boundary for drive source data

**Choice:** Public API only — drives read currentXxx() accessors. Add new public accessors to source orchestrators where needed.
**Alternatives:**
- Package-private access — put drives in same package. Tight coupling, no package separation.
- Intermediate signal types — new records per data flow. Most decoupled but most new types.
**Rationale:** Clean boundaries between drive sources and their upstream orchestrators. If MemoryHygiene doesn't expose knowledge gap data, add a public accessor rather than breaking encapsulation.
**Trade-offs:** May require adding public methods to existing orchestrators. Acceptable — those methods are useful beyond drives.
**Sources:** Orchestrator public APIs (currentTraits, currentMood, currentProfile, etc.)
**Exploration:** quick
**Depends on:** D1 (pull model), D8 (DriveSource SPI)
**Status:** captured

# Handoff — casehub-blocks

**Branch:** issue-142-narrative-identity
**Issue:** casehubio/blocks#142
**Date:** 2026-08-24

## Status

#142 (epic) foundation types complete — 29 new files, 71 new tests, 1811 total tests passing. Advanced queue to #143 (NarrativeOrchestrator). Ready for brainstorming + implementation of #143.

## What Was Built

Layer 3 foundation types for narrative identity and social emergence. Two new packages under `social`:

**`narrative` package (13 files):**
- `NarrativeFragment` sealed hierarchy: `IndividualEpisode`, `GroupEpisode`, `DerivedTheme`
- `NarrativeState` with `NarrativeScope` (INDIVIDUAL/GROUP) and typed accessors
- `NarrativeStore` SPI + `NoOpNarrativeStore` @DefaultBean
- `NarrativeModulation` utility (NarrativeState → `Map<DriveAxis, Double>`)
- Config: `NarrativeConfig`, `NarrativeSynthesisGate` (composite: count + novelty + quiet period)
- Tick types: `NarrativeTick`, `NarrativeSynthesisTick`

**`emergence` package (10 files):**
- `SocialNorm`, `NormObservation`, `DetectedNorms`, `NormDetectionConfig`
- `DriveAlignment`, `CollectiveGoalProposal`, `CollectiveGoalConfig`
- Tick types: `NormDetectionTick`, `CollectiveGoalTick`

**Integration changes:**
- `DriveConfig` + 11th field: `narrativeModulationStrength`
- `DriveComposer.compose()` + 8th param: `@Nullable Map<DriveAxis, Double> narrativeModulation`
- `CognitiveObservationSections.narrativeSection()` — agent prompt rendering
- `ReflectionQueryStore` SPI + `NoOpReflectionQueryStore` in memory package

## Key Design Decisions

- **Split architecture (D3):** NarrativeSynthesiser (effectful, LLM) + NarrativeOrchestrator (compositor, store-read) — mirrors MemoryHygieneScheduler pattern
- **DriveComposer modulation (D4):** narrative enters drives as pre-computed `Map<DriveAxis, Double>`, not as a DriveSource — avoids Map.put overwrite (GE-20260824-7263e2)
- **Sealed hierarchy (D5):** NarrativeFragment permits IndividualEpisode, GroupEpisode, DerivedTheme — shared across individual and group scope
- **Tick ordering (D10):** sources → NarrativeSynthesiser → NarrativeOrchestrator → DriveOrchestrator → GoalProposalOrchestrator

## What's Next

| Item | Scale | Complexity |
|------|-------|------------|
| #143 NarrativeOrchestrator — compositor implementation | M | Med |
| #144 NarrativeStore — CbrNarrativeStore persistence | S | Low |
| #145 NarrativeFeedback — wire modulation + GoalPriorityEscalationPolicy | M | High |
| #146 SocialNormDetector — norm detection from CBR | L | High |
| #147 CollectiveGoalFormation — drive alignment → JointIntention | L | High |
| #148 GroupNarrativeOrchestrator — collective self-story | L | High |

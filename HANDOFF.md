# Handoff — casehub-blocks

**Branch:** issue-142-narrative-identity
**Issue:** casehubio/blocks#142
**Date:** 2026-08-25

## Status

#144, #145, #146 complete — CbrNarrativeStore, narrative-to-drive modulation wiring, SocialNormDetector. Queue at position 4/7, active issue #147 (CollectiveGoalFormation). 1877 tests passing.

## What Was Built

- **CbrNarrativeStore** (#144) — CBR-backed NarrativeStore with NarrativeStateSchema handling polymorphic NarrativeFragment serialization (type discriminators for episode/group_episode/theme). NarrativeConfig gained memoryDomain/caseType. CbrNarrativeStore is now @DefaultBean; NoOpNarrativeStore lost default status.

- **NarrativeFeedback** (#145) — DriveOrchestrator wired to NarrativeOrchestrator via Instance<> (CDI optional). tick() reads current narrative, computes modulation via NarrativeModulation.compute(), passes to DriveComposer.compose(). CognitiveObservationSections.narrativeSection() already existed. GoalPriorityEscalationPolicy deferred → filed as #166 (cross-repo engine-api).

- **SocialNormDetector** (#146) — compositor reading NormObservation cases from CBR, grouping by behavioralPattern, computing adherence rates, classifying EMERGING/ESTABLISHED/DECLINING. Prior-state tracking for decline detection. NormDetectionConfig gained memoryDomain/caseType. NormObservationSchema for CBR serialization.

## Key Design Decisions

- Polymorphic CBR serialization: hand-rolled JSON with type discriminators in a single FeatureValue.string feature, consistent with MentalModelSchema pattern
- DECLINING norm detection requires cached previous state — norms that were ESTABLISHED but dropped below decliningThreshold transition to DECLINING

## What's Next

| Item | Scale | Complexity |
|------|-------|------------|
| #147 CollectiveGoalFormation — drive alignment to JointIntention | L | High |
| #148 GroupIdentity — shared narrative across agent collectives | L | High |

## Cross-Module

**Enabled:**
- `engine-api` — GoalPriorityEscalationPolicy SPI (#166) can now be implemented; narrative types and modulation infrastructure are in place · S · Med

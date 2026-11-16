# HANDOFF — casehub-neocortex

## Last Session

Implemented #1020 (channel weight polarization) and expanded scope to systematically fix all sub-4.0 coherence eval transitions. Eight structural fixes across the pipeline raised all 5 scenarios above the 4.0 coherence gate. The CAPS consolidation scenario — previously the worst at 3.55 — jumped to 4.38, driven primarily by the attractor filter threshold fix (0.1→0.55) which eliminated unstimulated unipolar output nodes from the behavioral attractor set.

### Branch: `issue-1020-channel-weight-polarization`

11 commits covering:
1. **Channel weight polarization** — blend factors 0.4→0.75, sharpened AVOIDANCE/ANTAGONISM/ATTENDING profiles
2. **Self-blame emotion routing** — EmotionMapper suppresses ANGER/ANTAGONISM/DOMINANCE when internal-standards < 0.5; routes to AVOIDANCE+SUBMISSION
3. **Urgency triggers ATTENDING** — high urgency (>0.6) captures attention independently of novelty (Ohman 1986)
4. **CAPS settling convergence** — momentum damping (0.4), wider oscillation detection (20-iter min, 50x epsilon), state averaging
5. **Attractor filter threshold** — 0.1→0.55, filters unstimulated logistic-resting nodes
6. **Drive trace enrichment** — PipelineTrace carries driveIntensities map, eval serializer outputs per-drive intensity
7. **Attractor trace enrichment** — CapsSettlingSnapshot carries AttractorSnapshot list
8. **SearchBudget channel coupling** — channel weights modulate search budget (somatic/actionTendency→convergence, attentional→breadth, temporal→depth)
9. **LLM calibration anchors** — crisis novelty + self-blame clarification in LlmAppraisalStrategy system prompt
10. **CAPS eval harness** — contextual observation + user profile seeding for consolidation tick

### Eval results after fixes

| Scenario | Before | After | Key transitions fixed |
|---|---|---|---|
| Curiosity | 3.97 | 4.32 | appraisal-to-drives 3.3→4.6 (drive enrichment) |
| Shame | 4.50 | 4.62 | observation-to-appraisal now stable (self-blame routing), full-trace 4.3→4.6 |
| Security | 4.13 | 4.37 | appraisal-to-channel-weights 3.8→4.2, channel-weights-to-prompt 3.8→4.3 |
| Trajectory | 3.80 | 4.10 | 10-tick coherence improved by channel weight + drive trace enrichment |
| CAPS | 3.55 | 4.38 | caps-input-to-settling 2.8→4.5, settling-to-prompt 3.8→4.1, full-consolidation 3.0→4.3 |

### Issues completed this session

| # | Title | Outcome |
|---|-------|---------|
| #1020 | Channel weight polarization | 8 structural fixes across pipeline, all 5 scenarios pass 4.0 gate |

### Issues partially addressed (scope overlaps)

| # | What was done | What remains |
|---|---------------|-------------|
| #1021 | Drive intensities added to PipelineTrace + eval serializer | Full drive trace enrichment (sub-threshold drives, modulation chain, DriveProfile in trace) |
| #1022 | Channel weight → search budget coupling wired | SearchBudget prose still shows curiosity-default in some CAPS scenarios; source selection needs CAPS-aware override |
| #1027 | Momentum damping + attractor filter + attractor trace enrichment | BehavioralPromptSection drops mid-tier attractors; activation-function-aware filtering |

### Key design decisions

- **Self-blame threshold**: `internalStd < 0.5` gates self-blame. Maps exactly to the SEC internal-standards dimension — below 0.5 means the agent violated its own moral standards. This is a hard cutoff, not graduated.
- **Damping factor 0.4**: Chosen empirically. 0.3 didn't suppress social-threat oscillation within 100 iterations. 0.5 was too aggressive — attenuated genuine signal propagation. 0.4 converges in 20-30 iterations.
- **Attractor filter 0.55**: Above logistic(0)=0.5 resting level. Only works because all current output nodes are unipolar. If bipolar outputs are added to the topology, the filter needs to be activation-function-aware.
- **Urgency × 0.9 for ATTENDING**: Urgency-driven attention is slightly weaker than novelty-driven. You focus intensely on a known threat type, but novelty captures attention more completely.

### Cross-slot note for slot 196 (regulation/coping)

- EmotionMapper now produces AVOIDANCE+SUBMISSION for self-blame scenarios (was ANTAGONISM+DOMINANCE). Regulation strategy selection handles AVOIDANCE already. SUBMISSION is a new ActionReadiness value that regulation should map (not yet wired — currently passes through without regulation-specific handling).

## Next Session Plan

### Remaining coherence floor work

| Order | # | Title | Notes |
|-------|---|-------|-------|
| 1 | #1021 | Drive trace enrichment | Remaining: sub-threshold drives, modulation chain in trace. Foundational for #1022/#1023. |
| 2 | #1027 | CAPS settling (remaining) | BehavioralPromptSection mid-tier attractor rendering. Activation-function-aware filter. |
| 3 | #1025 | Secondary boost mappings | HOPE→COMPETENCE, JOY→AFFILIATION. Quick win. |
| 4 | #1026 | Drive temporal smoothing | Trajectory-specific. Exponential smoothing in DriveOrchestrator. |
| 5 | #1023 | Drive annotation provenance | DriveComposer per-source contribution tracking. Builds on #1021. |
| 6 | #1022 | SearchBudget intensity coupling (remaining) | CAPS-aware source selection. Builds on #1021. |

Also in queue from prior sessions: #1015, #1016, #1012, #1013.

### Context to carry forward

- `docs/specs/2026-11-16-coherence-eval-rerun-findings.md` — updated with post-#1020 results, issues marked done.
- Eval JSON in `caps-llm-classifier/target/experiment-results/` (not committed — regenerate with `-Pllm-eval`).
- Pre-existing test failures: `SchererAppraisalStrategyTest.disabledSecs_reducedOutput` (pre-existing on main), `RegulationTopologyTest.topologyNodeCountIsCorrect` (pre-existing topology YAML mismatch).
- `DocumentSearchableProvider.search()` API change in upstream — build with `-pl cognition -am` to avoid.

### Files changed this session

| File | What changed |
|------|-------------|
| `caps-engine/.../CapsSettler.java` | Momentum damping, wider oscillation detection, averageStates, dead code removal |
| `caps-engine/.../CapsSettlingConfig.java` | +dampingFactor (0.4), attractorStrengthMinFilter 0.1→0.55 |
| `cognition-api/.../PipelineTrace.java` | +driveIntensities, +AttractorSnapshot, backward-compat constructors |
| `cognition/.../EmotionMapper.java` | Self-blame routing, urgency→ATTENDING, selfBlame boolean gate |
| `cognition/.../LlmAppraisalStrategy.java` | Crisis novelty + self-blame calibration anchors |
| `cognition/.../TopologyChannelWeighter.java` | Blend factors 0.75, sharpened profiles |
| `cognition/.../CognitionCore.java` | driveIntensities(), attractor snapshot population, journal entries |
| `cognition/.../SearchBudgetTickParticipant.java` | Channel weight modulation block |
| `cognition/.../EmotionMapperTest.java` | +5 tests (self-blame, urgency ATTENDING, channel weight) |
| `cognition/.../TopologyChannelWeighterTest.java` | +3 tests (attending dominance, avoidance polarization, profile differentiation) |
| `cognition/.../SearchBudgetTickParticipantTest.java` | +1 test (channel weight convergence boost) |
| `caps-llm-classifier/.../PipelineCoherenceEvalTest.java` | Drive+attractor serializers, CAPS observation+profiles, enhanced serializeDrives |
| `cognitive-observability-testing/.../PipelineSnapshotStoreContractTest.java` | Drive intensities field |
| `mindmap-intelligence/.../BehavioralSynthesisPhaseTest.java` | CopingDefaults constructor arg |
| `docs/specs/2026-11-16-coherence-eval-rerun-findings.md` | Post-#1020 results section |

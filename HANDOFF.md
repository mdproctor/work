# HANDOFF — casehub-neocortex

## Last Session

Drive pipeline enrichment — 10 issues completed across the cognition/appraisal/drive pipeline. The drive layer was a black box: emotions went in, four floats came out, with no visible composition trace. This session made every drive evaluation traceable from source signal through each modulation delta to final intensity, with the dominant emotion named in the trigger string. Also added temporal smoothing, expanded emotion→drive and action tendency→channel weight mappings, and coupled SearchBudget prose tone to appraisal state.

### Branch: `issue-1015-remorse-affiliation-secondary-boost`

11 commits (10 issues + 1 review fix):

1. **#1015** — REMORSE→AFFILIATION secondary boost (Tangney relational argument)
2. **#1016** — APPROACH/SUBMISSION/DOMINANCE action tendency → channel weight profiles
3. **#1012** — DocumentSearchableProvider compilation fix (no-arg search() API)
4. **#1013** — JVM shutdown hook for ClaudeAgentClient in eval tests (surefire timeout fix)
5. **#1021** — DriveEvaluation record: per-axis composition breakdown (raw, mood, personality, appraisal, other modulation deltas, composed intensity, activated flag)
6. **#1022** — SearchBudget prose coupled to appraisal intensity (crisis/urgency qualifiers)
7. **#1023** — Emotion→drive causal chain provenance in triggers ("elevated by SHAME" not "elevated by emotional response")
8. **#1025** — INTEREST→CURIOSITY(BOOST), DISTRESS→AFFILIATION(SECONDARY_BOOST) mappings
9. **#1026** — Asymmetric exponential smoothing for drive deactivation (smoothingAlpha=0.3, instant activation, gradual decay)
10. **#1028** — LLM appraisal action_targets field populates tendency target strings
11. **Review fix** — applySmoothing recomputes composite/dominant, urgency variable naming, smoothingAlpha validation, magic number replacement

### Issues filed this session

| # | Title | Priority |
|---|-------|----------|
| #1039 | SchererAppraisalStrategyTest.disabledSecs_reducedOutput — pre-existing failure | XS/Low |
| #1040 | LlmAppraisalStrategy action_targets schema — example entries in JSON template | XS/Low |

### Key design decisions

- **Asymmetric smoothing**: only downward transitions are smoothed; upward spikes bypass smoothing. Threat activation stays instant. α=0.3 gives ~3-tick ramp-down.
- **DriveEvaluation pre-smoothing**: composedIntensity records the pre-smoothing value from DriveComposer. The orchestrator applies smoothing afterward. Both values are meaningful for different consumers.
- **Urgency not arousal**: SearchBudgetPromptSection reads SEC "urgency" dimension as the intensity proxy, not arousal. Variable naming matches the actual data source.
- **INDIFFERENCE stays unmapped**: default channel weights (0.25 each) are correct for neutral action readiness. No mapping needed.

### Cross-slot note for slot 196 (regulation/coping)

- SUBMISSION action readiness from prior session still not wired in regulation strategy selection — passes through without regulation-specific handling.

## Next Session Plan

### Remaining queue items (3 open issues)

| Order | # | Title | Scale/Complexity | Notes |
|-------|---|-------|-----------------|-------|
| 1 | #1027 | CAPS settling convergence — MAX_ITERATIONS, zero attractors | M/High | Different subsystem (caps-engine). Needs investigation of CapsSettler oscillation dynamics for social-threat inputs. |
| 2 | #1033 | BehavioralPromptSection drops mid-tier attractors | — | Related to #1034; attractor rendering filtering |
| 3 | #1034 | Attractor filter threshold is activation-function-dependent | — | The 0.55 filter only works for unipolar outputs; bipolar outputs need different threshold |

Also filed but not in queue: #1039 (pre-existing test), #1040 (schema cleanup).

### Context to carry forward

- Blog entry: `wsp-casehub-neocortex/blog/2026-11-17-mdp01-the-black-box-that-shrugged.md`
- Pre-existing test failures: `SchererAppraisalStrategyTest.disabledSecs_reducedOutput` (#1039), `RegulationTopologyTest.topologyNodeCountIsCorrect` (pre-existing topology YAML mismatch)
- `DocumentSearchableProvider.search()` API fix landed in this branch — no longer needs `-pl cognition -am` workaround
- Eval JSON in `caps-llm-classifier/target/experiment-results/` not committed — regenerate with `-Pllm-eval`

### Files changed this session

| File | What changed |
|------|-------------|
| `cognition-api/.../DriveEvaluation.java` | **New** — per-axis composition trace record |
| `cognition-api/.../DriveConfig.java` | +smoothingAlpha field (0.3 default) |
| `cognition-api/.../PipelineTrace.java` | +driveEvaluations field |
| `cognition/.../DriveComposer.java` | composeWithEvaluations() — per-axis delta tracking, emotion provenance in triggers |
| `cognition/.../DriveOrchestrator.java` | Asymmetric EMA smoothing, lastDriveEvaluations(), appraisal descriptions |
| `cognition/.../ModulationLayer.java` | +descriptions field for per-axis provenance |
| `cognition/.../AppraisalDriveModulator.java` | +INTEREST, DISTRESS→AFFILIATION, REMORSE→AFFILIATION; dominantContributions() |
| `cognition/.../LlmAppraisalStrategy.java` | action_targets schema + parsing + application |
| `cognition/.../TopologyChannelWeighter.java` | APPROACH/SUBMISSION/DOMINANCE profiles and switch cases |
| `cognition/.../SearchBudgetPromptSection.java` | Appraisal-coupled prose (crisis/urgency qualifiers) |
| `cognition/.../CognitionCore.java` | driveEvaluations(), appraisal supplier wiring, descriptions passing |
| `caps-llm-classifier/.../EvalTestSupport.java` | JVM shutdown hook for ClaudeAgentClient |
| `knowledge-pipeline/.../DocumentSearchableProvider.java` | search(null) → search() |
| `cognitive-observability-testing/.../PipelineSnapshotStoreContractTest.java` | +driveEvaluations param |

# HANDOFF — casehub-neocortex

## Last Session

Full coherence eval audit and OCC vocabulary expansion. Ran all 5 PipelineCoherenceEvalTest scenarios, identified 8 sub-4.0 transitions across 7 structural root causes. Filed #1020–#1028 to systematically raise the coherence floor to 4.0+. Implemented #1024 (the upstream fix) — INTEREST emotion, GUILT/SHAME split, DOMINANCE tendency, arousal calibration, EmotionReinforcementMapper crash fix.

### Issues completed this session

| # | Title | Scale | Outcome |
|---|-------|-------|---------|
| #1014 | Re-run coherence eval post-fix | XS/Low | Shame full-trace 2.8→4.2, curiosity channel weights 3.2→3.8. Full 5-scenario audit: 8 transitions below 4.0, 3 scenarios fail gate |
| #1024 | LLM appraisal OCC vocabulary | S/Low | INTEREST, REMORSE/GUILT split, DOMINANCE tendency, arousal boost from novelty, calibration anchors, EmotionReinforcementMapper crash fix |

### Issues filed this session

| # | Title | Scale | Complexity | Priority |
|---|-------|-------|------------|----------|
| #1020 | Channel weight polarization | M | Med | MUST FIX — 3×3.8 |
| #1021 | Drive activation trace enrichment | M | Med | MUST FIX — 3.3 variance |
| #1022 | SearchBudget intensity coupling | S | Med | SHOULD FIX — 4.3 risk zone |
| #1023 | Drive annotation provenance | S | Med | SHOULD FIX — 4.2 risk zone |
| #1025 | AppraisalDriveModulator secondary boosts | S | Low | IMPROVE — +0.2 estimated |
| #1026 | Drive temporal smoothing | S | Med | MUST FIX — trajectory 3.8 |
| #1027 | CAPS settling convergence | M | High | MUST FIX — CAPS mean 3.60 |
| #1028 | Action tendency targets | XS | Low | IMPROVE — empty strings |

### Key design decisions

- **INTEREST PAD projection**: P=0.3, A=0.4, D=-0.1. Higher arousal than HOPE (0.2) — INTEREST is an activating emotion. Slightly lower pleasure than JOY — anticipatory, not consummatory.
- **GUILT/SHAME split via controllability**: controllability > 0.5 → REMORSE (OCC: "I chose to act badly"), ≤ 0.5 → SHAME (OCC: "I couldn't help it"). Existing test with default controllability=0.5 stays SHAME (backward compatible).
- **Arousal boost formula**: `urgency * 0.4 + novelty * 0.3` (was `urgency * 0.5`). Novelty now contributes to arousal — discovery scenarios no longer under-aroused.
- **DOMINANCE tendency**: urgency > 0.6 AND conduciveness < -0.2. Independent of AVOIDANCE/ANTAGONISM chain — both can fire simultaneously for crisis scenarios.

### Cross-slot note for slot 196 (regulation/coping)

Same as prior session. Additionally:
- EmotionMapper now generates INTEREST and REMORSE — regulation strategy selection should handle these (INTEREST → POSITIVE category in RegulationStrategyPredictor, already wired; REMORSE → SELF_CONSCIOUS category, same as SHAME, already wired).

## Next Session Plan

### Recommended execution order (coherence floor to 4.0+)

| Order | # | Title | Scale | Notes |
|-------|---|-------|-------|-------|
| 1 | #1020 | Channel weight polarization | M/Med | Fixes 3 deterministic 3.8s. Multiplicative modulation in SearchBudgetTickParticipant. |
| 2 | #1021 | Drive activation trace enrichment | M/Med | Foundational API change. Unblocks #1022 and #1023. |
| 3 | #1027 | CAPS settling convergence | M/High | CAPS mean 3.60 — worst scenario. Topology tuning + oscillation handling. |
| 4 | #1025 | Secondary boost mappings | S/Low | HOPE→COMPETENCE, JOY→AFFILIATION. Quick win. |
| 5 | #1026 | Drive temporal smoothing | S/Med | Exponential smoothing in DriveOrchestrator for trajectory coherence. |
| 6 | #1023 | Drive annotation provenance | S/Med | DriveComposer tracks per-source contributions. Builds on #1021. |
| 7 | #1022 | SearchBudget intensity coupling | S/Med | Appraisal arousal in prompt rendering. Builds on #1021. |
| 8 | #1028 | Action tendency targets | XS/Low | LLM generates targets alongside SEC dimensions. |

Also still in queue from prior session: #1015, #1016, #1012, #1013.

### Context to carry forward

- `docs/specs/2026-11-16-coherence-eval-rerun-findings.md` — comprehensive floor analysis with all 5 scenarios, root causes, issue mapping. Updated with #1024 status.
- Full eval JSON results in `caps-llm-classifier/target/experiment-results/` (not committed — regenerate by running `-Pllm-eval`).
- The 7 structural problems are independent except: #1021 unblocks #1022 and #1023. #1024 (done) feeds into #1025 (new emotion types need secondary boost mappings).
- EmotionType now has 23 values. Any exhaustive switch (only RegulationStrategyPredictor) is covered. EmotionReinforcementMapper coverage verified.

### Files changed this session

| File | What changed |
|------|-------------|
| `cognitive-api/.../EmotionType.java` | +INTEREST enum value, +3 aliases (curiosity/fascination/intrigue) |
| `cognitive-api/.../AlmaPadTable.java` | +INTEREST PAD projection (0.3, 0.4, -0.1) |
| `cognition/.../EmotionMapper.java` | +INTEREST mapping, REMORSE/SHAME split, +DOMINANCE tendency, arousal boost formula |
| `cognition/.../LlmAppraisalStrategy.java` | +SEC dimension calibration anchors |
| `cognition/.../RegulationStrategyPredictor.java` | +INTEREST in POSITIVE switch branch |
| `mindmap-intelligence/.../EmotionReinforcementMapper.java` | +INTEREST in BAS group (crash fix) |
| `cognition/.../EmotionMapperTest.java` | +6 new tests (interest, remorse, shame-boundary, dominance, arousal-novelty) |
| `caps-llm-classifier/.../PipelineCoherenceEvalTest.java` | Shame scenario seeds UserProfile via harness |
| `docs/specs/2026-11-16-coherence-eval-rerun-findings.md` | Full 5-scenario audit + floor analysis + issue mapping |

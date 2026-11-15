# HANDOFF — casehub-neocortex

## Last Session

Completed #972 on branch `issue-972-tension-threshold-sensitivity`. Made the tension threshold configurable and ran a sensitivity analysis that revealed the threshold is a formality — binary score distribution makes any value below 0.6 equivalent.

### Issues completed

| # | Title | Scale | Outcome |
|---|-------|-------|---------|
| #972 | Tension threshold sensitivity analysis for A/C prompt routing | S/Med | Threshold configurable, 0.5 confirmed optimal via analytical analysis |

### Issues filed

| # | Title | Scale | Why |
|---|-------|-------|-----|
| #989 | Re-run tension threshold LLM eval when Vertex deployment updated | XS/Low | Eval test ready but fable-5-2 model unavailable on Vertex |
| #990 | LlmSituationClassifier silently degrades on unavailable models | S/Low | Discovered during eval — non-JSON responses produce empty results silently |

### What was built

- **Configurable threshold** — `LlmSituationClassifier` now accepts `tensionThreshold` as a 4th constructor parameter with [0,1] validation. CDI producer wires `casehub.caps.classification.tension-threshold` (default 0.5).
- **Sensitivity analysis** — all 6 curated tension entries score 0.6–1.0; no scores exist in (0.0, 0.6). Thresholds 0.3 and 0.5 produce identical routing. Threshold 0.7 drops three meaningful tensions. Findings documented in `docs/specs/issue-972-tension-threshold-sensitivity.md`.
- **Eval test** — `TensionThresholdSensitivityEvalTest` exercises all 6 tension table entries via 7 profiles (6 tension + 1 baseline) across thresholds 0.3/0.5/0.7. Measures inter-profile variance, dual-activation rates, and stability. Zero-vector guard prevents false-positive assertions when LLM is unavailable.
- **Code review fixes** — import ordering, parameter validation, missing profiles (bold-avoidant and independent-low-autonomy at tension 0.7), false-positive guard on inter-profile variance assertion.

### Key finding

The tension table is binary by construction — pairs either create a named psychological conflict (scoring 0.6–1.0) or don't (scoring 0.0). There are no borderline entries. The routing threshold is a safety margin between the lowest legitimate score and zero, not a tuning knob. The real control surface for personality-aware classification is the tension table itself — which axis-value pairs get entries and what narratives they carry.

### Pending verification

- LLM eval data collection for #972 blocked by Vertex model availability (fable-5-2) — tracked in #989
- crossTickTrajectory eval score (was 2.3) still needs LLM eval run (from prior session)
- DriveProfilePersonalityEvalTest needs LLM eval run (from prior session)

### Next queue items

- #973 — CBR disposition filter isolation test
- #983 — SHAME affiliation suppression

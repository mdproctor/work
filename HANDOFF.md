# HANDOFF — casehub-neocortex

## Last Session

Extended personality-aware situation classification with PID-5 facet scores and AppraisalWeights. The CAPS classifier now distinguishes characters who share disposition axes but differ in personality magnitude — an anxious cooperator perceives threat where a calm cooperator perceives information. Post-implementation: deep audit, blog revision with SVG, research doc updates, full queue reconciliation.

### Landed on main (f2e09f2), all published to remote

8 commits total:

1. **#977** — `PersonalityPromptBuilder`: `pid5Suffix()` and `appraisalSuffix()` with high/low/moderate labels, wired into Path A and Path C prompts. `pid5Facets` config property. Model version `haiku-pid5`.
2. **#977** — `CbrCachingSituationDecorator`: SHA-256 extended with sorted `pid5.*` and `appraisal.*`. `addPersonalityFeatures()`.
3. **#977** — `addPersonalityMetadata()` in BehavioralSynthesisPhase and DriveProfileDerivationPhase.
4. **#977** — Eval: anxious-cooperative vs calm-cooperative profiles.
5. Artifact promotion (spec, decisions, diary)
6. CLAUDE.md module descriptions updated
7. Blog revision + SVG diagram promoted
8. Era7 research doc updated (#977 downstream consumer section, #980 marked done)

### Issues closed

| # | Title |
|---|-------|
| #977 | PID-5 facet extension personality classification |

### Issues filed

| # | Title | Scale | Complexity |
|---|-------|-------|-----------|
| #1070 | Wire pid5Facets config to metadata enrichment and cache key | S | Low |
| #1071 | Extract addPersonalityMetadata to shared utility | S | Low |
| #1072 | Remove hasPid5 and strengthen eval assertions | XS | Low |

### Queue reconciliation

Discovered 25 items in the queue that were closed on GitHub but never ticked. All verified — 24 have commits on main (some squashed into batch commits), 1 (#1001) was correctly closed without code changes. Queue now: **80 done, 4 remaining** (was incorrectly showing 37 remaining).

### Key findings

- **pid5Facets config not wired** — declared but no production code reads it. Filed as #1070.
- **DRY violation** — `addPersonalityMetadata()` duplicated verbatim in two phases. Filed as #1071.
- **Dead code** — `hasPid5()` defined but unused. Filed as #1072.
- **IntelliJ optimize-imports strips static assertThat** after `ide_insert_member` — needs manual re-add.

### Pre-existing failures

- `AttentionSignalTest` in mindmap-api
- `CopingStrategyPredictorTest` in cognition — CopingDefaults 8 vs 5 args

## Queue — 4 remaining under epic #1050

| # | Title | Scale | Complexity |
|---|-------|-------|-----------|
| #1070 | Wire pid5Facets config | S | Low |
| #1071 | Extract addPersonalityMetadata shared utility | S | Low |
| #1072 | Remove hasPid5 + eval guards | XS | Low |
| #976 | DriveProfileDerivationPhase personality classification | M | High |

### Recommendation

#1070 + #1071 + #1072 are quick follow-ups from this session's audit — bundle into one session (< 30 min). #976 is the last substantial item and needs its own design pass. After these 4, epic #1050 is complete.

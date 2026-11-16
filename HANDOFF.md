# HANDOFF — casehub-neocortex

## Last Session

Batch close of 7 issues (#973, #983, #984, #985, #986, #989, #990) — downstream coherence fixes from the #957 SEC→OCC eval and the #972 tension threshold analysis. Each branch separate, all landing on main via work-end.

### Issues completed

| # | Title | Scale | Outcome |
|---|-------|-------|---------|
| #973 | CBR disposition filter isolation test | XS/Low | 3 integration tests with real InMemoryCbrRecordStore prove cross-personality cache isolation |
| #983 | SHAME affiliation suppression | S/Med | Removed AFFILIATION REDUCE from SHAME mapping; withdrawal captured by AUTONOMY boost alone |
| #984 | Drive saturation clamping | S/Med | Math.tanh() on accumulated drive deltas prevents co-occurring FEAR+SHAME saturating AUTONOMY at 1.0 |
| #985 | Curiosity attentional channel weight | S/Med | TopologyChannelWeighter.weight(activations, actionTendencies) blends toward attention-heavy profile when ATTENDING > 0.3 |
| #986 | ATTENDING channel boost | XS/Low | Covered by #985 — empty commit close |
| #989 | Re-run tension threshold eval | XS/Low | Fixed — model made nullable; eval tests use backend default instead of hardcoded "haiku" |
| #990 | LlmSituationClassifier silent degradation | S/Low | WARNING log when extractJson() returns null on non-empty response |

### Issues filed

| # | Title | Scale | Why |
|---|-------|-------|-----|
| #1000 | Map AVOIDANCE and ANTAGONISM action tendencies to channel weights | S/Low | ATTENDING→attentional done (#985), other Frijda tendencies logically implied |
| #1001 | DriveProfilePersonalityEvalTest compilation failure — stale module dependency | XS/Low | Pre-existing: 4-arg DriveProfileDerivationPhase constructor from #970 not picked up by caps-llm-classifier module |

### Key design decisions

- **SHAME mapping**: Removed AFFILIATION REDUCE rather than making it context-dependent. Simpler, and the withdrawal tendency is fully captured by AUTONOMY BOOST.
- **Diminishing returns**: `tanh()` over alternatives (weighted averaging, hard cap) because it preserves monotonicity, works symmetrically for negative deltas, and doesn't change individual emotion-to-drive weights.
- **ATTENDING blend**: Uses existing `ChannelWeightProfile.blend()` with proportional factor (`intensity * 0.4`) rather than additive boost. Normalisation is guaranteed by blend().
- **Silent degradation**: Warning log (not exception) because callers handle empty activations gracefully and the error is about deployment configuration, not logic.
- **Model nullable**: LlmSituationClassifier model parameter made nullable; when null, skips .withModel() so the backend uses its configured default. Removes hardcoded model dependency from eval tests.

### Pending verification

- shameRegulationChain eval should improve with #983+#984 — needs LLM re-run
- curiosityExplorationChain drives-to-channel-weights should improve with #985 — needs LLM re-run
- DriveProfilePersonalityEvalTest compilation — tracked in #1001

### Research docs updated

- `docs/research/2026-11-05-post-era6-regulation-and-identity.md` — Stream 15 added
- `wksp/specs/issue-957-sec-occ-emotion-mapping/2026-11-14-sec-occ-emotion-mapping-design.md` — AppraisalDriveModulator status updated

### Blog

- `blog/2026-11-16-mdp01-the-pipeline-that-argued-with-itself.md` — covers all four code fixes with tanh diminishing returns diagram

### Queue state

Epic #841 queue drained. Next work: #1000, #1001, or new queue items.

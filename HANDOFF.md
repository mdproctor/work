# HANDOFF — casehub-blocks

## Last Session

Completed 2 YAML surface issues from the #246 epic queue, fixed a .plan corruption, and filed 2 soredium hardening issues.

**#252 (Trust/routing/oversight):** Delivered 5 config-shaped types — TrustRoutingPolicyKeysSpec (adapted record), CbrOutcomeWeightsSpec + CoordinationOutcomeWeightsSpec (weight map specs), DispositionProfile (direct reuse), RiskDecisionSpec + CandidateSetStrategySpec (sealed interfaces). CandidateSetStrategy direct reuse failed (StaticSetStrategy has no Jackson creators) — created spec mirror instead. 4 runtime types (AttestationContext, AttestationIntent, ClassificationContext, GateOutcome) marked N/A in coverage matrix.

**#253 (Speech configs):** Delivered 5 config-shaped types — TranscriptionOptions + SynthesisOptions (direct reuse from speech-api), SherpaConfigSpec + KokoroConfigSpec + Audio8ConfigSpec (adapted records with Path→String). Added speech-api and speech-sherpa as provided-scope deps to agentic-yaml. 7 types marked N/A (runtime data, already @ConfigMapping, utility class, @FunctionalInterface, filesystem-derived).

**#254 (Summarisation gaps):** Spec and plan written, not yet executed. 6 gaps: keyed grouping compiler (UnsupportedOperationException in PipelineCompiler), field-extract + verbatim registration in SummarisationRecorder, SummaryMode/typePrefix/tickInterval config fields.

**Other:** Fixed #246 epic incorrectly checked in .plan (was advanced past without commits). Filed Hortora/soredium#359 (epic guard) and #360 (uncheck command) for plan_manager.py hardening.

Coverage moved from 59% to ~68%. Remaining gaps: engine adapter module only (§21).

## Immediate Next Step

Run `work continue` — the .plan queue is at position 6/13 with #254 (Summarisation gaps) active. Plan is written at `plans/2026-09-11-summarisation-gaps.md` — proceed directly to `executing-plans`.

## References

| Artifact | Path |
|----------|------|
| Coverage matrix | `docs/yaml-coverage.md` |
| Epic | casehubio/blocks#246 |
| .plan | 7 issues remaining, #254 next |
| #252 spec | `specs/issue-252-trust-routing-oversight/` |
| #253 spec | `specs/issue-253-speech-configs/` |
| #254 spec | `specs/issue-254-summarisation-gaps/` |
| #254 plan | `plans/2026-09-11-summarisation-gaps.md` |
| soredium issues | Hortora/soredium#359, #360 |

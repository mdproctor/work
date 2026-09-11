## D1: Scope — 5 config-shaped types only

**Choice:** Scope #252 to 5 config-shaped types: TrustRoutingPolicyKeys, CbrOutcomeWeights, CoordinationOutcomeWeights, DispositionProfile, RiskDecision. Drop 4 runtime data types from YAML surface scope.
**Alternatives:**
- All 9 types as listed in issue — would build empty scaffolding around runtime data carriers (AttestationContext, AttestationIntent, ClassificationContext, GateOutcome) that authors never configure
- 5 config types + attestation templates — adds attestation observer compilation, a separate complex feature
**Rationale:** YAML surface types must be declarations (author-time knowable, compilable, choice-bearing). The 4 runtime types fail all criteria — they're data carriers populated by the engine at runtime, not config an author writes.
**Trade-offs:** Coverage matrix won't reach 100% for §19/§20 — 4 types marked N/A. Attestation observer YAML is deferred.
**Sources:** AttestationContext.java, ClassificationContext.java, GateOutcome.java, AttestationIntent.java — field analysis; prior spec patterns (ExecutionBackendSpec, AffordanceSpec)
**Exploration:** quick
**Status:** captured

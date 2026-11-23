## D1: Architecture approach

**Choice:** Reorder DissonanceDetectionPhase before BehavioralSynthesisPhase and extend `mergeDriveIntensities()` to consume dissonance-derived CAPS activations
**Alternatives:**
- New DissonanceActivationPhase at @Priority(18) — clean separation but adds indirection and one-tick delay for newly detected tensions
- CapsActivationProvider SPI — cleanest abstraction for extensible activation injection, but YAGNI for a single new activation source
**Rationale:** `mergeDriveIntensities()` already handles external activation injection (drive→CAPS mappings via `DriveCompetitionDetector`). Dissonance activations follow the same pattern — they are CAPS input node activations that should be merged into the settling pass. The D6 constraint from #593 was scoped to that iteration; #1212 explicitly evaluates extending BehavioralSynthesisPhase.
**Trade-offs:** Couples DissonanceDetectionPhase and BehavioralSynthesisPhase via shared cache. Modifies the consolidation pipeline ordering (DissonanceDetectionPhase moves from @Priority(25) to @Priority(17)).
**Sources:** BehavioralSynthesisPhase.mergeDriveIntensities(), DriveCompetitionDetector.DRIVE_CAPS_MAPPINGS, D4/D6 from #593 decisions
**Exploration:** deep-analysis
**Status:** captured

## D2: Activation merge strategy

**Choice:** Inject both sides of each contradiction (capsActivationsA and capsActivationsB) into the CAPS activation map, letting the settler resolve the conflict through its constraint-satisfaction algorithm
**Alternatives:**
- Net activation (difference between competing maps) — loses the constraint-satisfaction dynamics that ARE the theoretical model of dissonance
- Strength-weighted strongest side — simpler but doesn't model the actual conflict; just biases toward one side
**Rationale:** Shultz & Lepper (1996) define dissonance as competing activations in a constraint network. The CAPS settler already handles competing inputs via excitatory/inhibitory connections — this is exactly what it was designed for. Injecting both sides lets the network settle to a resolution that reflects the full tension, not a pre-computed summary.
**Trade-offs:** More CAPS input nodes activated simultaneously may produce oscillation in marginal cases. Mitigated: CapsSettler has oscillation detection and converges within bounded iterations.
**Sources:** Shultz & Lepper 1996, CapsSettler.java (oscillation detection, convergence bounds), DissonanceDetectionPhase capsActivationsA/capsActivationsB fields
**Exploration:** quick
**Status:** captured

## D3: Pipeline priority slot

**Choice:** @Priority(17) for DissonanceDetectionPhase (moved from 25)
**Alternatives:**
- @Priority(18) — right before BehavioralSynthesisPhase (19), no buffer slot
**Rationale:** After SubThoughtConsolidation (16), before BehavioralSynthesisPhase (19). Gives one slot of buffer. DissonanceDetectionPhase reads Belieflike nodes and behavioral attractors from MindMap (previous-tick state is acceptable), so no data dependency on phases 16-18.
**Trade-offs:** Dissonance detection now runs before BehavioralSynthesisPhase, so it no longer sees same-tick attractor changes. Accepted: previous-tick attractors are stable enough for contradiction detection.
**Depends on:** D1 (reorder approach requires priority change)
**Sources:** ConsolidationPhase priority assignments: ExperienceConsolidation(15), SubThought(16), BehavioralSynthesis(19)
**Exploration:** quick
**Status:** captured

## D4: Activation coupling mechanism

**Choice:** In-memory cache — BehavioralSynthesisPhase reads `DissonanceDetectionPhase.activeTensions()` to extract CAPS activation maps
**Alternatives:**
- MindMap node convention (cognitiveKind=dissonance-activation nodes) — durable and observable but unnecessary MindMap round-trip; activation maps are already on the tension nodes
- CDI event — decoupled but fragile ordering within same consolidation tick
**Rationale:** DissonanceDetectionPhase already maintains a `ConcurrentHashMap<String, List<TensionSnapshot>>` cache, populated during each run. BehavioralSynthesisPhase already uses the same cache pattern for its own `lastSettlingResults()` and `lastInputActivations()` accessors. Reading the cache is a direct method call with no I/O. The tension nodes already persist the activation maps in MindMap for observability — no need to duplicate.
**Trade-offs:** Runtime coupling: BehavioralSynthesisPhase holds a reference to DissonanceDetectionPhase (via CDI injection). If dissonance is disabled, the cache returns empty and merge is a no-op.
**Depends on:** D1 (reorder ensures DissonanceDetectionPhase runs first), D3 (priority ordering guarantees cache is populated)
**Sources:** DissonanceDetectionPhase.activeTensions(), BehavioralSynthesisPhase.lastSettlingResults() pattern
**Exploration:** quick
**Status:** captured

## D5: Activation scaling

**Choice:** Scale dissonance-derived CAPS activations by tension strength: `effectiveActivation = llmConfidence * tensionStrength`
**Alternatives:**
- Raw LLM confidences — ignores tension lifecycle; a decaying tension at strength 0.2 would produce the same CAPS input as a fresh strength-1.0 tension
**Rationale:** Tension strength already models the lifecycle of a contradiction (fresh=1.0, decaying toward 0.15 resolution threshold). Scaling activations by strength couples the CAPS behavioural effects to the same lifecycle. As a tension fades, its influence on CAPS settling fades proportionally. This prevents decaying tensions from producing disproportionate behavioural effects.
**Trade-offs:** Very weak tensions (strength near 0.15) produce near-zero activations; they're functionally invisible to CAPS. Acceptable: they're about to be resolved anyway.
**Depends on:** D2 (both sides injected; scaling applies to both)
**Sources:** DissonanceDetectionPhase tension strength lifecycle, additive decay model from #593
**Exploration:** quick
**Status:** captured

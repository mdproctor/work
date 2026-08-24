# Decisions — #142 Narrative Identity + Social Emergence

## D1: Epic scope — design spec + foundation types

**Choice:** #142 delivers a design spec covering all six children, plus foundation types/SPIs that children implement against
**Alternatives:**
- Design spec only — no code, children start from scratch
- Design spec + first child (#143) — spec and implement together
**Rationale:** Foundation types force the design to be concrete at the interface level. If the types compile and compose correctly, the design is proven before any orchestrator implementation. Children implement against stable interfaces.
**Trade-offs:** More upfront work on #142; types may need revision as children discover implementation-level issues
**Sources:** Layer 1 (#129) and Layer 2 (#136) both delivered foundation types alongside specs
**Exploration:** quick
**Status:** captured

## D2: Thread depth — both threads equally

**Choice:** Full type-level design for both narrative identity (#143-#145) and social emergence (#146-#148)
**Alternatives:**
- Narrative deep, social sketch — defer social emergence types
- Narrative only — defer social emergence entirely
**Rationale:** Social emergence composes with existing multi-agent infrastructure (ConversationOrchestrator, CoalitionFormation, JointIntention). The composition points are concrete enough for type-level design. Deferring would mean designing social emergence without the narrative foundation types it needs to compose with.
**Trade-offs:** Larger spec; social emergence types are more speculative and may change more during implementation
**Sources:** Issue #142 body; blog 2026-08-21-mdp04
**Exploration:** quick
**Status:** captured

## D3: NarrativeOrchestrator follows compositor pattern

**Choice:** Compositor — tick() + currentNarrative(), no record(). Reads from ReflectionStore and other orchestrator outputs on tick.
**Alternatives:**
- Record + tick — receive narrative-relevant events via record()
- Hybrid — compositor for reflections, record() for narrative-specific events
**Rationale:** Consistent with ADR-0001 (DriveOrchestrator compositor pattern). Reflections already accumulate in MemoryHygiene's ReflectionStore — no new signal path needed. Narrative state is derivable from existing accumulated data.
**Trade-offs:** If future narrative-specific events need fast-path ingestion (not flowing through MemoryHygiene), the compositor may need to evolve. ADR-0001 anticipated this: "Layer 3 may re-introduce a signal path."
**Sources:** ADR-0001-drive-compositor-pattern.md; InnerLifeOrchestrator.java; DriveOrchestrator (spec)
**Exploration:** quick
**Status:** captured

## D4: Narrative feedback enters drive system as DriveComposer modulation layer

**Choice:** Third modulation layer in DriveComposer.compose(), alongside mood and personality
**Alternatives:**
- New DriveSource implementation — wrong abstraction; DriveSource extracts raw intensity for one axis from one data source; narrative modulates ALL axes simultaneously based on story themes; Map.put semantics means a NarrativeDriveSource would overwrite existing axis values rather than modulate them
- Direct intensity override — couples narrative to drive internals
**Rationale:** Narrative doesn't generate new raw drive intensities — it contextually amplifies/dampens existing ones based on self-story themes. This is semantically identical to what mood and personality modulation already do. DriveComposer.compose() gains a @Nullable NarrativeState parameter; null = no modulation.
**Trade-offs:** Changes DriveComposer.compose() signature (additive — new nullable parameter). All existing callers pass null until NarrativeOrchestrator is wired in.
**Sources:** DriveComposer design (spec #129); DriveOrchestrator.java; ADR-0001
**Exploration:** deep-analysis
**Status:** captured

## D5: Narrative model — hybrid (episodes + themes)

**Choice:** Two-level hierarchy: NarrativeEpisodes (significant experiences with emotional valence) as raw material, NarrativeThemes (typed with salience scores) derived from episodes
**Alternatives:**
- Theme-based only — simpler, maps cleanly to drive modulation, but loses narrative richness
- Episode-based only — richer narrative fidelity, but requires additional extraction step for drive modulation
**Rationale:** Episodes serve prompt rendering (what the agent tells others about itself). Themes serve drive modulation (how the self-story shapes motivation). Both consumption patterns are needed: CognitiveObservationSections renders episodes for LLM prompts; NarrativeFeedback uses themes for drive modulation weights.
**Trade-offs:** More complex type system; LLM synthesis must produce both episodes and themes
**Sources:** CognitiveObservationSections.java; DriveComposer (spec #129); blog 2026-08-21-mdp04
**Exploration:** quick
**Status:** captured

## D6: Narrative update trigger — reflection-count threshold

**Choice:** Re-synthesise narrative when N new reflections have accumulated since last synthesis
**Alternatives:**
- Significance-gated — re-synthesise only on narratively significant reflections (requires heuristic)
- Time-windowed — fixed interval regardless of reflection volume
**Rationale:** Simple, predictable, bounded cost. Similar to MemoryHygiene's batch consolidation pattern. Configurable threshold in NarrativeConfig. Avoids wasted LLM calls (time-windowed) and complex significance heuristics (significance-gated).
**Trade-offs:** May miss urgently narrative-relevant reflections if threshold is too high; may over-synthesise if too low. Tunable via config.
**Sources:** MemoryHygieneOrchestrator consolidation pattern; InnerLifeConfig content quality gate pattern
**Exploration:** quick
**Status:** captured

## D7: Social norm detection via cross-conversation memory

**Choice:** SocialNormDetector reads from a NormObservationStore (CBR-backed) that accumulates interaction patterns across conversations. Compositor pattern — reads accumulated data on tick().
**Alternatives:**
- ConversationOrchestrator listener — real-time observation via ConversationListener.onDispatch(), record+tick pattern
- CommonGroundState projection — reuse epistemic machinery, project established facts across sessions
**Rationale:** Norms emerge across conversations over time, not within a single conversation. CBR storage enables pattern frequency and consistency analysis across agents. CommonGroundState captures epistemic claims, not behavioral patterns (actions, sequences, rituals). The listener approach couples too tightly to a single interaction mechanism.
**Trade-offs:** Requires a new store SPI (NormObservationStore). CBR queries for pattern frequency need careful feature design. Someone must record observations into the store — likely via a ConversationListener or channel observer, but the detector itself is a compositor.
**Sources:** CommonGroundAnalyser.java; ConversationOrchestrator; CbrCaseMemoryStore (neocortex)
**Exploration:** quick
**Status:** captured

## D8: Collective goal formation via drive profile aggregation

**Choice:** CollectiveGoalFormation reads multiple agents' DriveProfiles (via DriveOrchestrator.currentDrives()) and detects alignment. Proposes JointIntention when alignment exceeds threshold.
**Alternatives:**
- Goal overlap detection — scan individual currentProposals() for similar names; depends on name heuristics
- Explicit negotiation — full NegotiationProjection cycle; heavyweight but robust
**Rationale:** Drive alignment is more fundamental than goal naming similarity — two agents may express the same drive through differently-named goals. JointIntention already handles the commitment lifecycle (form/activate/reconsider/drop/fulfill). CollectiveGoalFormation bridges DriveProfiles to JointIntention proposals.
**Trade-offs:** Requires access to multiple agents' DriveOrchestrator instances (cross-agent read). Alignment detection heuristic may over-trigger for agents with naturally similar profiles (e.g., same personality archetype).
**Sources:** JointIntention.java; CoalitionProposal.java; DriveProfile (spec #129)
**Exploration:** quick
**Status:** captured

## D9: Group identity shares narrative type system

**Choice:** GroupIdentity uses the same NarrativeFragment/NarrativeState foundation types as individual narrative. Difference is scope (group vs individual) and source (collective experiences vs personal reflections).
**Alternatives:**
- Distinct type system — group narratives track membership, role differentiation, group achievements
**Rationale:** The narrative model (episodes + themes) applies at any scope. A group has episodes ("we handled that crisis together") and themes ("we're the team that catches fraud early"). Sharing types enables composition — an individual's narrative can reference group episodes, and group identity can aggregate individual themes.
**Trade-offs:** Group-specific dimensions (membership changes, role differentiation) may need additional fields or subtypes on the shared types. If group narratives diverge significantly in structure, the shared types may become awkwardly general.
**Sources:** Blog 2026-08-21-mdp04 (group identity section); NarrativeOrchestrator design direction
**Exploration:** quick
**Status:** captured

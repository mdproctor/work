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

## D3: Split architecture — NarrativeSynthesiser (effectful) + NarrativeOrchestrator (compositor)

**Choice:** Two components: (1) NarrativeSynthesiser — effectful, scheduled, calls LLM to produce narrative from reflections, writes to NarrativeStore; (2) NarrativeOrchestrator — true compositor, reads from NarrativeStore on tick(), no LLM, no side effects. Mirrors the MemoryHygieneScheduler/MemoryHygieneOrchestrator split.
**Alternatives:**
- Single NarrativeOrchestrator with LLM in tick() — breaks compositor guarantee (non-deterministic, expensive LLM calls are side effects; ADR-0001 defines compositors as deterministic with no persistence)
- Record + tick — receive narrative-relevant events via record()
- Hybrid — compositor for reflections, record() for narrative-specific events
**Rationale:** LLM synthesis (producing episodes and themes from reflections) is non-deterministic and expensive — it cannot live in a compositor tick(). Splitting into synthesiser + compositor preserves ADR-0001's guarantees while supporting the LLM requirement. The synthesiser is called by the consumer's scheduler; the compositor reads pre-synthesised state from NarrativeStore.
**Trade-offs:** Two components instead of one. The synthesiser needs its own scheduling and the consumer must call it before ticking the orchestrator. This is the same pattern as MemoryHygieneScheduler calling ReflectionOrchestrator before ticking MemoryHygieneOrchestrator.
**Note:** ReflectionStore is currently write-only (`void store(ReflectionEntry)`). NarrativeSynthesiser needs to read stored reflections. This requires either: (a) a query method added to ReflectionStore, or (b) a separate ReflectionQueryStore SPI. Decision deferred to spec — depends on neocortex-memory-api design.
**Sources:** ADR-0001-drive-compositor-pattern.md; MemoryHygieneScheduler.java; MemoryHygieneOrchestrator.java; ReflectionStore.java (write-only SPI); decision review R1-02, R1-03, R1-11
**Exploration:** quick → revised after decision review
**Status:** revised

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

## D5: Narrative model — sealed hierarchy with episodes and themes

**Choice:** NarrativeFragment sealed interface with three permits: IndividualEpisode (personal experience with emotional valence), GroupEpisode (collective experience with membership + role attributions), DerivedTheme (typed salience score with per-axis modulation weights). Episodes are raw material; themes are derived. Both share the NarrativeFragment base (temporal span, thematic tags).
**Alternatives:**
- Theme-based only — simpler, maps cleanly to drive modulation, but loses narrative richness
- Episode-based only — richer narrative fidelity, but requires additional extraction step for drive modulation
- Flat hybrid without sealed hierarchy — episodes and themes as separate types with no common interface; loses composition between individual and group scope
**Rationale:** Episodes serve BOTH prompt rendering and drive modulation (episode emotional valence directly informs drive intensity). Themes also serve BOTH consumers (themes appear in agent prompts for self-consistent behavior, and provide structured drive modulation weights). The sealed hierarchy enables individual narrative to reference GroupEpisode instances and group identity to aggregate IndividualEpisode themes — composition across scopes.
**Trade-offs:** More complex type system; LLM synthesis must produce structured output matching the sealed hierarchy. GroupEpisode adds membership and role fields that IndividualEpisode doesn't need.
**Depends on:** D9 (group identity shares type system)
**Sources:** CognitiveObservationSections.java; DriveComposer (spec #129); blog 2026-08-21-mdp04; decision review R1-05, R1-09
**Exploration:** quick → revised after decision review
**Status:** revised

## D6: Narrative update trigger — composite gate (count + novelty + quiet period)

**Choice:** Re-synthesise when: (a) N new reflections accumulated AND at least one scores above a novelty threshold (vs previous narrative state), OR (b) quiet period bypass — re-synthesise after a configurable inactivity duration regardless of count. Modelled after InnerLifeConfig.ContentQualityGate.
**Alternatives:**
- Count-only threshold — regresses from ContentQualityGate pattern; misses urgently relevant reflections and wastes LLM on redundant ones
- Significance-gated — per-reflection significance scoring; too complex for the trigger mechanism
- Time-windowed — fixed interval regardless of reflection volume; wastes LLM on empty windows
**Rationale:** ContentQualityGate combines three mechanisms: count gate (minObservations), quality gate (noveltyThreshold), inactivity bypass (quietPeriodBypass). The narrative trigger follows the same pattern — count bounds cost, novelty ensures quality, quiet period ensures responsiveness to long idle periods.
**Trade-offs:** Novelty scoring requires comparing new reflections against current narrative state. Simple approach: token Jaccard distance (already used by InnerLifeOrchestrator.TokenJaccardDistance) between new reflection text and current narrative text.
**Sources:** InnerLifeConfig.ContentQualityGate; TokenJaccardDistance.java; decision review R1-06
**Exploration:** quick → revised after decision review
**Status:** revised

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

## D9: Group identity shares narrative type system via sealed hierarchy

**Choice:** GroupIdentity uses the NarrativeFragment sealed hierarchy (D5). GroupEpisode is a permit of NarrativeFragment alongside IndividualEpisode and DerivedTheme. Group-specific fields (membership set, role attributions, consensus level) live on GroupEpisode — not forced into the base interface.
**Alternatives:**
- Distinct type system — separate GroupNarrative hierarchy with no composition with individual narrative
- Flat shared types — same type for both scopes, generalized fields for group-specific dimensions
**Rationale:** The sealed hierarchy preserves composition (individual narrative can reference GroupEpisode instances, group identity can aggregate IndividualEpisode themes) while allowing structural differences (GroupEpisode tracks membership dynamics, IndividualEpisode tracks protagonist introspection). Identity persists despite member turnover — the group narrative is maintained by the GroupNarrativeOrchestrator, not by any individual member.
**Trade-offs:** Group narratives require a mechanism for negotiated identity — members may disagree about the group story. This is deferred to GroupNarrativeOrchestrator implementation (#148), not solved at the type level.
**Depends on:** D5 (sealed hierarchy)
**Sources:** Blog 2026-08-21-mdp04 (group identity section); decision review R1-09
**Exploration:** quick → revised after decision review
**Status:** revised

## D10: Tick ordering — narrative before drives, two-phase

**Choice:** Scheduler ordering: source orchestrators → NarrativeSynthesiser (if triggered) → NarrativeOrchestrator → DriveOrchestrator → GoalProposalOrchestrator. NarrativeOrchestrator sees current reflections but previous-cycle drives. DriveOrchestrator sees current narrative.
**Alternatives:**
- Drives before narrative — narrative would see current drives but drives wouldn't reflect current narrative themes; backwards dependency
- Simultaneous / lag-one on both — breaks the derivability guarantee; both see stale data from the other
**Rationale:** D3 (narrative reads reflections) and D4 (drives read narrative) create a feedback loop. The loop is broken by making NarrativeOrchestrator tick before DriveOrchestrator in each cycle. Narrative state changes slowly (only on synthesis, gated by D6 composite trigger), so the one-cycle lag on drives is semantically negligible. The scheduler owns this ordering — same pattern as Layer 1's "source ticks must precede drive tick."
**Trade-offs:** NarrativeSynthesiser (if triggered in this cycle) must complete before the compositor chain runs. LLM synthesis latency gates the tick cycle when synthesis is triggered.
**Sources:** ADR-0001 ordering constraint; Layer 2 spec ordering section; decision review R1-10
**Exploration:** quick (surfaced by decision review)
**Status:** captured

## D11: Layer 2 deferrals — cross-axis composition and governed priority escalation

**Choice:** Explicitly address two capabilities Layer 2 deferred to Layer 3:
(1) **Cross-axis goal composition** — compound goals like "learn about X by engaging with Y" (curiosity + affiliation). NarrativeOrchestrator naturally identifies cross-axis themes from episodes. When a DerivedTheme has high salience across multiple axes, the GoalProposalOrchestrator (or a future CrossAxisGoalMapper) can propose compound goals. Foundation type support: DerivedTheme carries per-axis modulation weights; when multiple weights are significant, the theme is cross-axis.
(2) **Governed priority escalation** — narrative context justifies elevating a drive-sourced goal from SECONDARY to PRIMARY. The NarrativeState provides the provenance: "this goal aligns with my core identity theme with salience > threshold." The escalation mechanism is a new GoalPriorityEscalationPolicy SPI in engine-api, checked by GoalFormationService before registration.
Both are designed at the type level in this spec; orchestrator implementation is deferred to children.
**Alternatives:**
- Re-defer entirely — push to a hypothetical Layer 4; leaves Layer 2's design debts unresolved
**Rationale:** These were explicit design debts assigned to Layer 3 by the Layer 2 spec (D4 and D6 in #136 decisions). The type system being designed in #142 can accommodate both without additional architectural complexity — DerivedTheme's per-axis weights naturally support cross-axis detection, and NarrativeState provides the provenance for priority escalation.
**Trade-offs:** GoalPriorityEscalationPolicy adds a new SPI to engine-api. Implementation requires engine changes (GoalFormationService checks the policy).
**Sources:** Layer 2 spec D4 (cross-axis composition deferred), Layer 2 spec D6 (priority escalation deferred); decision review R1-12
**Exploration:** quick (surfaced by decision review)
**Status:** captured

## D12: Agent discovery for collective goal formation

**Choice:** CollectiveGoalFormation receives the agent set as a constructor parameter — List<String> agentIds scoped to a single tenant. The consumer (quarkmind scheduler, claudony) provides the set from its own agent registry. CollectiveGoalFormation does not discover agents itself.
**Alternatives:**
- DriveOrchestrator enumeration — add a method to list known agents from its internal ConcurrentHashMap; leaks internal state
- AgentRegistry injection — inject eidos AgentRegistry directly; couples blocks to eidos runtime
**Rationale:** The consumer already knows which agents are active in its tenant context. Passing the set explicitly avoids blocks needing agent discovery infrastructure. Same pattern as ConversationOrchestrator receiving participant lists at construction time.
**Trade-offs:** The consumer must keep the agent list current. Stale agent lists produce stale alignment analysis.
**Depends on:** D8 (drive profile aggregation)
**Sources:** ConversationOrchestrator constructor pattern; AgentParticipant; decision review R1-08
**Exploration:** quick (surfaced by decision review)
**Status:** captured

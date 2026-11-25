## D1: NeedsCapability data source

**Choice:** Static derivation from CognitiveDerivationEngine (personality weights from disposition axes)
**Alternatives:**
- Drive memory query (CaseMemoryStore domain="drive") — shows runtime drive state but requires cognition tick loop to have run; duplicates DrivePromptSection from cognition module
**Rationale:** cognitive-runtime is the lightweight avatar layer that works without the full emergent pipeline. Static derivation is deterministic, works standalone, and represents "who I am" rather than "what I'm feeling now" — which is the right abstraction for this layer.
**Trade-offs:** No runtime drive activation state — only personality-derived needs hierarchy. When full cognition is running, its own DrivePromptSection handles live state.
**Sources:** CognitiveDerivationEngine (cognitive-index), DrivePromptSection (cognition), PersonalityWeights (memory-api)
**Exploration:** quick
**Status:** captured

## D2: PracticalAppraisalCapability data source

**Choice:** Render stored appraisal/affect memories from CaseMemoryStore (PAD values, emotional metadata)
**Alternatives:**
- Accept AppraisalStrategy via Instance<T> and invoke at render time — requires extending CognitionRenderContext with observation field, couples render-time to LLM calls, duplicates AppraisalTickParticipant from cognition module
**Rationale:** Keeps cognitive-runtime as a read-and-render layer. Surfaces existing emotional state from memory rather than computing new appraisals. Same pattern as MemoryCapability.
**Trade-offs:** No live appraisal — only renders what was previously stored. Requires some component to have written affect-annotated memories.
**Depends on:** D1 (same philosophy — read existing state, don't compute)
**Sources:** CaseMemoryStore.scan() (memory-api), MemoryInput PAD fields, AppraisalTickParticipant (cognition)
**Exploration:** quick
**Status:** captured

## D3: BlockTag assignments for new capabilities

**Choice:** Use existing BlockTag values — NeedsCapability→NEEDS(230), PracticalAppraisalCapability→APPRAISAL(220), InnerThoughtsCapability→REFLECTION(510), ConsolidationCapability→CONSOLIDATION(520)
**Alternatives:**
- Create new BlockTag values — unnecessary since all 4 map naturally to existing tags designed for these purposes
**Rationale:** Each tag was already defined for exactly this semantic purpose. NEEDS is in the State tier, APPRAISAL is in the State tier, REFLECTION and CONSOLIDATION are in the Signals tier. No ordinal conflicts.
**Trade-offs:** None — reusing existing tags is the intended design.
**Sources:** BlockTag enum (cognition-api), existing capability BlockTag assignments
**Exploration:** quick
**Status:** captured

## D4: ConsolidationCapability query strategy

**Choice:** Single query on COGNITIVE subgraph, then group results by trait for structured rendering
**Alternatives:**
- Flat recent-nodes only (no trait grouping) — simpler but loses the semantic distinction between graduated entities, summaries, and curiosity descriptions
- Separate per-trait queries — 3-4 queries per render, unnecessary since traits are already on nodes from a single query
**Rationale:** One query gives both simplicity and structure. The traits (graduated-sub-thought, Summary, CuriosityDescriptionGenerated) are already on the MindMapNodes — grouping them in the renderer costs nothing and gives the prompt section meaningful subsections.
**Trade-offs:** None significant — combines the efficiency of a single query with the precision of trait-aware rendering.
**Sources:** MindMapStore.nodesIn() (mindmap-api), ConsolidationPhase trait conventions (mindmap-intelligence)
**Exploration:** quick
**Status:** captured

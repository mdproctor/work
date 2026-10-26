## D1: Swarm topology placement

**Choice:** 9th PatternDefinition topology — full parity with existing topologies
**Alternatives:**
- Secondary definition (like NegotiationDefinition) — better if swarm shape diverges from 5-SPI model, but breaks Java/YAML parity expectation
- Hybrid topology + companion spec — two-level YAML adds complexity without clear benefit
**Rationale:** Java/YAML parity is a hard requirement. All 8 existing topologies follow the sealed record + PatternCompiler pattern. A 9th topology is the most natural addition.
**Trade-offs:** Swarm must map onto the 5-SPI ExecutionModel (routing/decomposition/activation/aggregation/termination), which constrains how coordination logic is expressed
**Sources:** PatternDefinition.java, PatternCompiler.java, PatternType enum, issue #285
**Exploration:** quick
**Status:** captured

## D2: LLM capability scope

**Choice:** All four capabilities in scope — team formation, experience replay, collective reasoning, adaptive coordination
**Alternatives:**
- Core two (team + replay) — ships faster but misses the adaptive loop that makes swarms valuable
- Core three (team + replay + adaptive) — drops collective reasoning, but that's the synthesis mechanism adaptive coordination depends on
**Rationale:** L/High issue warrants full set. The four capabilities compose naturally: experience replay informs team formation, collective reasoning feeds adaptive coordination. Shipping partial breaks the feedback loop.
**Trade-offs:** Larger implementation surface; risk of scope creep in each capability
**Sources:** Issue #285 body, SwarmExp paper (arXiv:2608.30661)
**Exploration:** quick
**Status:** captured

## D3: Engine integration model

**Choice:** Implement engine SPIs directly — SwarmProvisioningAdvisor with LLM intelligence, ChoreographedDriver for event-driven execution
**Alternatives:**
- Self-contained with engine types — more independent but duplicates mechanical swarm logic
- Thin adapter + rich agency layer — adds an abstraction layer between SPI and real logic without clear benefit
**Rationale:** Clean dependency direction. Agency has provided dependency on engine-api. SwarmProvisioningAdvisor is the designed extension point. ChoreographedDriver's event-driven model (blocking queue + signal()) is a natural fit for swarm event processing.
**Trade-offs:** Couples agency's coordination to engine's provisioning lifecycle; changes to SwarmProvisioningAdvisor contract affect agency
**Sources:** SwarmProvisioningAdvisor SPI (engine-api), ChoreographedDriver (agency-core), SwarmProvisioner (engine runtime)
**Exploration:** quick
**Status:** captured

## D4: Experience replay mechanism

**Choice:** PromptSection + ExperienceAnalyser — SwarmExperienceSection formats CBR team-level traces into coordinator's system prompt
**Alternatives:**
- Dedicated ExperienceReplayProvider SPI — over-engineers what's a prompt construction concern
- Inline in coordinator — simpler but not composable or YAML-configurable
**Rationale:** PromptSection is the established pattern for contributing context to LLM prompts. ExperienceAnalyser already provides worker success rates and plan trace data. A SwarmExperienceSection composes naturally with the prompt assembly pipeline.
**Trade-offs:** Limited to what fits in a system prompt; very large experience sets need pre-filtering heuristics
**Sources:** PromptSection SPI (speech-api), ExperienceAnalyser (engine-api), CbrAgentRoutingStrategy (agency routing.agent)
**Exploration:** quick
**Status:** captured

## D5: Coordination structure level

**Choice:** Light scaffolding — LLM drives coordination decisions
**Alternatives:**
- Structured phases (form → distribute → observe → adapt) — more predictable but constrains self-organisation
- Configurable structure level — flexible but adds complexity for uncertain benefit
**Rationale:** Aligns with "Drop the Hierarchy" paper finding that minimal scaffolding produces better self-organisation. The topology provides the event loop, team/role awareness, and experience context; the LLM decides how to coordinate.
**Trade-offs:** Less predictable behaviour; harder to debug than structured phases; LLM quality directly determines coordination quality
**Sources:** "Drop the Hierarchy and Roles" (arXiv:2603.28990), issue #285
**Exploration:** quick
**Status:** captured

## D6: Overall architecture

**Choice:** LLM Coordinator as unified SPI composition — LlmSwarmCoordinator composes SwarmRoutingStrategy, CollectiveReasoningAggregation, ConvergenceTermination, SwarmExperienceSection behind a single composition root
**Alternatives:**
- Specialised SwarmDriver subclass — cleaner swarm semantics but breaks the pattern where all topologies share the same driver → SPI composition; PatternCompiler needs special case
- Decomposition-centric swarm — elegant decomposition SPI mapping but forces swarm into task-breakdown mental model; doesn't support adaptive redistribution
**Rationale:** Stays within existing 5-SPI ExecutionModel so PatternCompiler works naturally. Concentrates LLM intelligence in coordinator with full context for reasoning. YAML surface is clean and consistent with existing topologies.
**Trade-offs:** Coordinator holds significant responsibility; single point of LLM reasoning rather than distributed intelligence
**Sources:** OrchestratedDriver/ChoreographedDriver (agency-core), PatternCompiler (agentic-yaml), ConvergenceTermination (agency-core)
**Exploration:** quick
**Status:** captured

## D7: Team formation reasoning model

**Choice:** Batch reasoning — one LLM call with full agent roster, capabilities, experience data, and detected roles/teams
**Alternatives:**
- Iterative selection — evaluate one candidate at a time against growing team; lower per-call cost but loses holistic composition reasoning
- Two-phase filter-then-reason — heuristic pre-filter via CoalitionEvaluator then LLM reasons about shortlist; balances cost but adds a pipeline stage
**Rationale:** Swarm team formation is inherently a combinatorial problem where agent complementarity matters. Batch reasoning lets the LLM consider all trade-offs simultaneously. Token cost is acceptable for a coordination decision that shapes the entire execution.
**Trade-offs:** Higher single-call token cost; roster size limited by context window
**Depends on:** D6 (coordinator architecture)
**Sources:** CoalitionEvaluator (agency deliberation/coalition), SwarmBootstrapContext (engine-api)
**Exploration:** quick
**Status:** captured

## D8: Collective reasoning mechanism

**Choice:** AggregationStrategy with LLM summariser — CollectiveReasoningAggregation collects AgentResults from members, feeds through ContentSummariser to produce synthesised view
**Alternatives:**
- ObservationDispatcher pipeline — reuses topological sort and handler precedence; more structured but adds handler registration complexity for what's fundamentally a synthesis task
- Dedicated SwarmSynthesiser SPI — clean abstraction but duplicates what ContentSummariser already handles
**Rationale:** AggregationStrategy is the existing SPI for combining agent outputs. ContentSummariser is the proven LLM synthesis pattern. Composing both reuses established abstractions without new SPIs.
**Trade-offs:** Aggregation happens at fixed points in the driver loop, not continuously; fine for iteration-based synthesis
**Depends on:** D6 (coordinator architecture)
**Sources:** AggregationStrategy SPI (agency orchestration/aggregation), ContentSummariser (summarisation-api), LlmContentSummariser (agency perception/llm)
**Exploration:** quick
**Status:** captured

## D9: Adaptive coordination triggers

**Choice:** Engine swarm events via ChoreographedDriver — SwarmEvents arrive as DriverEvents, event loop wakes coordinator to re-reason with updated context
**Alternatives:**
- Tick-based polling — fixed interval, simpler but wastes cycles and misses fast changes
- Threshold-triggered — only re-evaluate on metric threshold crossings; efficient but requires domain-specific threshold tuning
**Rationale:** Natural fit with D3 (ChoreographedDriver) and D5 (light scaffolding). Engine already emits SwarmEvents for role changes, team changes, and progress updates. The coordinator reacts to meaningful state changes rather than polling or requiring pre-defined thresholds.
**Trade-offs:** Event volume could cause excessive LLM calls; needs a coalescing EventConcurrencyPolicy
**Depends on:** D3 (engine integration), D5 (light scaffolding)
**Sources:** ChoreographedDriver (agency-core), SwarmEvent (engine runtime), EventConcurrencyPolicy (agency-core)
**Exploration:** quick
**Status:** captured

## D10: YAML surface shape

**Choice:** Rich surface — maxSize, experience (replay config), coordinationModel, roleAffinityThreshold, teamFormationStrategy, adaptationFrequency, observationWindow
**Alternatives:**
- Minimal (maxSize + experience + model) — keeps surface small but gives domain teams no control over swarm behaviour tuning
- Extensible with properties map — flexible but untyped and hard to validate
**Rationale:** Domain teams need control over swarm behaviour without changing code. Role affinity and adaptation frequency are tunable knobs that vary by use case. Typed fields with validation are better than a generic map.
**Trade-offs:** Larger spec record; more fields to document and test; slight tension with D5 (light scaffolding) but these configure the coordinator's context, not its logic
**Depends on:** D1 (topology placement), D5 (coordination structure)
**Sources:** PatternDefinition existing topology fields, SwarmConfig (engine-api)
**Exploration:** quick
**Status:** captured

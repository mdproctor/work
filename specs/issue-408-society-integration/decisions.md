## D1: Topology architecture — composable builder, not class hierarchy

**Choice:** Composable `TopologyBuilder` with factory methods. HiveTopology as a class disappears — becomes `TopologyBuilder.hive(agentRoles, patternType)`. Extract `DegradationCascade` and `OrchestrationPatternExecutor` as reusable components.
**Alternatives:**
- Thin classes (Approach A) — keep HiveTopology as a class that composes with extracted infrastructure. More incremental but preserves the misleading class hierarchy.
- Hive-only fix — wire HivePatternAdapter to pattern builders without refactoring. Fastest but leaves 4x copy-pasted degradation cascade and stub execution across all topologies.
**Rationale:** The design spec explicitly says "Hive is not a separate construct." Analysis showed all 6 topology implementations share copy-pasted degradation cascade code and stub execution methods. The root cause is tangled orthogonal concerns (loop planning, outcome execution, degradation) — fixing Hive alone would leave the structural problem. CentralisedTopology<C> stays as-is for now (swarm-specific reasoner entanglement).
**Trade-offs:** Bigger change with more touch points. All HiveTopology consumers need updating. CentralisedTopology remains as an exception to the builder pattern until its reasoner wiring can be disentangled.
**Sources:** HiveTopology.java, CentralisedTopology.java, HierarchicalTopology.java, HybridTopology.java — identical degradation cascade in 4 files. Design spec §2.1: "Hive is not a separate construct."
**Exploration:** deep-analysis
**Status:** captured

## D2: Bottom-up formation orchestrator lives in agency

**Choice:** `BottomUpFormationOrchestrator` in agency-core. Takes `GroupLifecycleService` (eidos org-api) as a constructor param (provided dependency). Composes CoalitionProposal → CoalitionEvaluator → NegotiationProtocol (via Qhorus channel) → GroupLifecycleService.proposeGroup() → approveProposal().
**Alternatives:**
- Engine/desiredstate — orchestrator in engine where full lifecycle integration happens. Splits formation logic across repos.
- SPI in agency, impl in engine — adds an interface that only has one implementation. Premature abstraction.
**Rationale:** Agency already owns CoalitionProposal, CoalitionEvaluator, and NegotiationProtocol. The orchestrator is a composition of these building blocks. GroupLifecycleService is a provided dependency (eidos org-api is already on agency's provided classpath). Keeping all formation orchestration in one repo makes the flow traceable.
**Trade-offs:** Agency gains a runtime dependency on eidos org-api for the GroupLifecycleService parameter type. This is already a provided dependency.
**Sources:** Design spec §3.3 "Bottom-Up Proposal". GroupLifecycleService.java (eidos org-api). CoalitionProposal.java, NegotiationProtocol (agency-core).
**Exploration:** quick
**Status:** captured

## D3: RolePolicySpec compiles to eidos RoleSpec

**Choice:** Agency's `TopologySpec.RolePolicySpec` becomes a YAML-layer convenience that compiles to eidos `RoleSpec`. Compiler in `TopologySpecRegistry` or a dedicated `RolePolicyCompiler`. Mapping: obligations → `RolePolicy(cap, OBLIGATION, {})`, permissions → `RolePolicy(cap, PERMISSION, {})`, YAML gains optional `min_agents`, `max_agents`, `incompatible_with` fields mapping to `RoleSpec.minCardinality/maxCardinality/incompatibleWith`.
**Alternatives:**
- Agency defines its own canonical role model — creates a parallel type system alongside eidos. Wrong: eidos owns the org model.
- Remove RolePolicySpec entirely, use RoleSpec in YAML — YAML becomes verbose (PolicyKind, expectations map for simple cases). Wrong: YAML should be concise.
**Rationale:** Design spec §2.2 establishes eidos RoleSpec as canonical. RolePolicySpec is syntactic sugar for the common case (named obligations/permissions). Full RoleSpec can be expressed in YAML when needed via an explicit `role_spec` block.
**Trade-offs:** Two representations of the same concept (YAML convenience + canonical type). Compiler must be kept in sync with eidos RoleSpec changes.
**Sources:** Design spec §2.2. RoleSpec.java, RolePolicy.java, PolicyKind.java (eidos org-api). TopologySpec.RolePolicySpec (agency agentic-yaml).
**Exploration:** quick
**Depends on:** D1 (TopologyBuilder consumes compiled RoleSpecs)
**Status:** captured

## D4: TopologyContext gains groupId and tenancyId

**Choice:** Add `@Nullable String groupId` and `@Nullable String tenancyId` to the `TopologyContext` record. Nullable because case-scoped topologies (pre-society, no group context) legitimately have no group. Populated from the society model when a topology is activated for a group.
**Alternatives:**
- Non-nullable with sentinel values — violates null-safety conventions, forces callers to check sentinel strings.
- Separate GroupTopologyContext subclass — Java records can't extend. A wrapper adds unnecessary indirection.
**Rationale:** Design spec mandates it. TopologyContext is the shared context for all topology operations. Without groupId/tenancyId, HiveTopology can't query the org model for group policy. @Nullable is the standard pattern for optional context in CaseHub records.
**Trade-offs:** Existing callers must update to pass the new fields (null for non-group topologies). All existing tests need a minor update.
**Sources:** Design spec §2.1. TopologyContext.java. Issue #412.
**Exploration:** quick
**Status:** captured

## D5: TopologyFactory bridges YAML spec to TopologyBuilder

**Choice:** `TopologyFactory` in agentic-yaml maps `TopologySpec` variants to `TopologyBuilder` factory calls. For `TopologySpec.Hive`: reads `orchestration` field → `OrchestrationPatternType`, reads `roles` + `policy` → compiled `RoleSpec` list, calls `TopologyBuilder.hive(roles, patternType)`. Registered as a step in `PatternCompiler`.
**Alternatives:**
- Inline compilation in PatternCompiler — mixes YAML parsing with topology construction. PatternCompiler is already large.
- Per-variant factory classes (HiveFactory, SwarmFactory, etc.) — class explosion for what are configuration variations.
**Rationale:** Single factory with exhaustive switch on the sealed TopologySpec hierarchy. Each variant maps to a TopologyBuilder factory method. Clean separation: YAML parsing (Jackson) → TopologyFactory → TopologyBuilder → CoordinationTopology.
**Trade-offs:** TopologyFactory depends on both agentic-yaml types and agency-core TopologyBuilder. This is the natural dependency direction (YAML surface depends on runtime).
**Sources:** TopologySpec.java (agentic-yaml). TopologySpecRegistry.java. Issue #413.
**Exploration:** quick
**Depends on:** D1 (TopologyBuilder), D3 (RolePolicySpec compiler)
**Status:** captured

## D6: Migrate all non-CentralisedTopology implementations to TopologyBuilder

**Choice:** Migrate Distributed, Hierarchical, Hybrid, Graduated, and Hive topologies to `TopologyBuilder` factory methods. CentralisedTopology<C> stays as-is (swarm-specific reasoner entanglement). Extract `DegradationCascade` for reuse across all topologies that have cascade logic.
**Alternatives:**
- Hive only — migrate only HiveTopology, leave others. Creates inconsistency: one topology uses the new pattern, four don't.
**Rationale:** The degradation cascade is copy-pasted across 4 topology classes. Extracting it once and migrating all topologies eliminates the duplication in one pass. Each topology becomes a factory method that describes WHAT it configures, not HOW the infrastructure works.
**Trade-offs:** Larger change surface. All topology tests must be updated. But the alternative (doing it incrementally across 4 follow-up issues) is more total work.
**Sources:** HiveTopology.java, HierarchicalTopology.java, HybridTopology.java, GraduatedTopology.java, DistributedTopology.java — shared infrastructure analysis.
**Exploration:** quick
**Depends on:** D1 (TopologyBuilder design)
**Status:** captured

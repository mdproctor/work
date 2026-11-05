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

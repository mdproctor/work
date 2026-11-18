## D1: Adaptation phase placement

**Choice:** HygieneAdaptationPhase runs in the ConsolidationScheduler, reads KnowledgeGapSummary cross-system from MemoryHygieneOrchestratorImpl, stores adjusted parameters for the next hygiene tick to pick up. Two tick loops stay decoupled.
**Alternatives:**
- Move hygiene into consolidation scheduler — larger refactor, risks breaking separate cadence
- CDI event bridge — adds coupling without colocation benefit
**Rationale:** Preserves existing architecture boundaries. Consolidation and hygiene already run on independent schedules. Async parameter propagation is natural for a system where parameter changes take effect gradually.
**Trade-offs:** One-tick lag between adaptation computation and hygiene applying the new params. Acceptable because retention parameters are slow-moving.
**Sources:** MemoryHygieneOrchestratorImpl (cognition), ConsolidationScheduler (mindmap-intelligence), KnowledgeGapSummary (memory-api)
**Exploration:** quick
**Status:** captured

## D2: Making RetentionConfig dynamic

**Choice:** AtomicReference<RetentionConfig> in a shared @ApplicationScoped holder bean (AdaptiveRetentionConfig). The adaptation phase writes new configs; the hygiene orchestrator reads the current one each tick. The underlying RetentionConfig record stays immutable — only the reference swaps. Learned parameters persist via CBR (parameter configs stored as cases, best-performing one retrieved on startup).
**Alternatives:**
- MindMap sentinel node persistence — couples hygiene to MindMap, adds store dependency to a currently-independent subsystem
- RetentionConfigProvider SPI — maximum flexibility but adds indirection for a single consumer
**Rationale:** Simplest mechanism with no new SPIs or cross-module dependencies. Volatile AtomicReference gives thread-safe reads without coordination. CBR handles persistence and learning — the holder is just the runtime propagation channel.
**Trade-offs:** In-memory only between restarts — CBR retrieval on startup recovers learned state but the holder starts with DEFAULT until the first consolidation tick. Acceptable for a gradually-adapting system.
**Depends on:** D1 (cross-system async propagation requires a shared mutable reference)
**Sources:** RetentionConfig (cognition-api), MemoryHygieneOrchestratorImpl (cognition)
**Exploration:** quick
**Status:** captured

## D3: Adaptation algorithm

**Choice:** Ratio-based heuristic with bounded step sizes. Compute quality ratios from KnowledgeGapSummary (eviction pressure = lowRetentionCount/totalScored, consolidation activity = consolidationGroups/totalScored). Identify the weakest dimension, apply small delta steps (±0.05) clamped to valid ranges. Deterministic, explainable, no cold-start problem.
**Alternatives:**
- CBR-driven adaptation — stores (params → quality outcome) as cases, retrieves best-performing config. Learns from history but has cold-start problem; better as a future layer
- Combined heuristic + CBR — heuristics bootstrap, CBR refines. Appealing but adds complexity that can be layered later
**Rationale:** A working system needs to function from tick 1 without historical data. Ratio-based heuristics are interpretable and testable. CBR case recording can be added as a follow-on issue without changing the phase's core logic.
**Trade-offs:** No learning from history — each adaptation is stateless (based on current quality signals only, not trends). Sufficient for v1; CBR layering adds trend awareness later.
**Depends on:** D1 (phase reads KnowledgeGapSummary cross-system)
**Sources:** KnowledgeGapSummary (memory-api), RetentionConfig (cognition-api)
**Exploration:** quick
**Status:** captured

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

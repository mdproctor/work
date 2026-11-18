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

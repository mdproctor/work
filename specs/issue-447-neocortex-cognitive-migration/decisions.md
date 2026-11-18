# Decisions — #447 Remove residual cognitive code

## D1: Migration target module

**Choice:** Move memory hygiene + evolution code into neocortex's existing `cognition` module
**Alternatives:**
- New `neocortex-memory-hygiene` module — adds module boundary for no consumer benefit; memory hygiene is wired exclusively through CognitionCore.tick()
- Split: core to neocortex, evolution SPIs stay in agency — splits a cohesive unit across repos; evolution types have no neocortex imports anyway
**Rationale:** 17 of 33 files already import neocortex types. The MemoryHygieneOrchestrator SPI is in cognition-api; placing the implementation in cognition eliminates the circular bridge pattern. CognitionCoreFactory discovers it via CDI directly.
**Trade-offs:** cognition module grows by ~33 files. Acceptable — it's already 140+ files and this is a natural cohesive addition.
**Sources:** neocortex cognition-api/MemoryHygieneOrchestrator.java, agency social/memory/* audit, consumer audit (zero external consumers)
**Exploration:** quick
**Status:** captured

## D3: Evolution types stay in agency

**Choice:** All 11 evolution files (7 memory hygiene evolution + 4 cognition evolution) stay in agency-core
**Alternatives:**
- Move all 11 to neocortex cognition — adds engine-api as a neocortex dependency for no structural benefit; the 7 memory hygiene evolution files have zero neocortex imports
- Move only the 4 cognition evolution files (they bridge engine↔neocortex) — splits them from their cohesive partner in memory hygiene evolution for no gain
**Rationale:** The 7 memory/evolution files implement engine-api SPIs (ImprovementProposalSource, RegressionEvaluator, CapabilityArea, etc.) with zero neocortex deps. The 4 cognition/evolution files are genuine bridge types connecting engine's evolution tick to neocortex's drives/mood — that's exactly what agency-core is for. None are wired in AgencyBeans/AgencyAutoConfiguration — they're CDI-discovered by the engine's improvement loop.
**Trade-offs:** Memory hygiene evolution types in agency will reference types that now live in neocortex (via MemoryHygieneOutcomeProvider callback). This is the correct dependency direction — agency depends on neocortex, not the reverse.
**Sources:** Dependency audit of all 11 files, engine-api SPI interfaces, neocortex#1119 (native consolidation-phase approach)
**Exploration:** quick
**Status:** captured

## D2: Speech-api types — defer to #490

**Choice:** #447 does not touch speech-api types or CognitionAvatarAdapter. #490 handles that.
**Alternatives:**
- Do both in #447 — larger scope, blocks on eidos#240 and neocortex#1105
- Do #490 first, then #447 — safe but delays the independent memory hygiene work
**Rationale:** #490 has cross-repo API dependencies (eidos#240, neocortex#1105). #447's memory hygiene move is self-contained. They touch different files and different AgencyBeans wiring.
**Trade-offs:** CognitionAvatarAdapter and MemoryHygieneSpiAdapter temporarily remain in agency after #447. #490 cleans them up.
**Sources:** agency#490 issue body, eidos#240, neocortex#1105
**Exploration:** quick
**Status:** captured

## D1: Three separate SPIs, not one unified interface

**Choice:** Split into `SubjectResolver`, `InteractionMapper`, and `NormFilter` — three independent `@FunctionalInterface` SPIs with distinct integration points.
**Alternatives:**
- Single `CognitionContextStrategy` interface with 3 methods — cohesive but `filterNorms()` has no call site in CognitionCore, and `mapInteraction()` return type (`InteractionSignal`) is too narrow for the 4-orchestrator recording surface
- Context object approach (`CognitionContext` record) — forces immutable snapshot (thread-safe) but risks God object growth
- Decorator wrapping CognitionCore — CognitionCore stays unchanged but adds indirection
**Rationale:** Devil's advocate + judge analysis confirmed two HIGH-severity attacks on the unified approach: (1) `mapInteraction() → InteractionSignal` covers only 1 of 4 orchestrator channels — need `CognitiveImpact` record instead; (2) `filterNorms()` has zero references in CognitionCore — norms are consumed at prompt assembly, not in CognitionCore. Three SPIs respect the actual integration boundaries.
**Trade-offs:** Three injection points instead of one. Consumers implementing all three (like wacky-manor) have three classes instead of one — but each is a `@FunctionalInterface`, so lambdas work.
**Sources:** CognitionCore.java:135-169 (recordInteraction 4-orchestrator surface), SocialNormDetector.java (no CognitionCore reference), CognitiveObservationSections (norm rendering at prompt assembly)
**Exploration:** deep-analysis (devil's advocate + judge)
**Status:** captured

## D2: CognitiveImpact record — typed fields per channel

**Choice:** `CognitiveImpact` record with one nullable field per orchestrator channel: `@Nullable InteractionSignal`, `@Nullable MoodSignal`, `boolean suppressBdiExtraction`, `@Nullable QualitySignal engagementQuality`. Null = use default behaviour. Static `passthrough(String)` factory for the common case.
**Alternatives:**
- Map-based generic bag (`Map<String, Object>`) — extensible but loses type safety
- Per-channel nested records (`MoodImpact`, `EngagementImpact`) — over-engineers 4 nullable fields
**Rationale:** Smallest surface that covers the 4-channel problem. Type-safe, obvious at the call site, no runtime key lookups.
**Trade-offs:** Adding a 5th orchestrator channel requires changing the record — acceptable since new channels are rare and warrant a deliberate API change.
**Sources:** CognitionCore.java:135-169 (4 orchestrator channels in recordInteraction)
**Exploration:** quick
**Status:** captured

## D3: Backward-compatible overloads for tick() and recordInteraction()

**Choice:** Add new overloads, keep existing methods unchanged. `tick(agentId, tenantId, descriptor)` calls `resolver.relevantSubjects()` when resolver is present. `recordInteraction(agentId, tenantId, subjectId, interactionType)` calls `mapper.mapInteraction()` when mapper is present. Existing 4-param `tick()` and 5-param `recordInteraction()` stay for consumers without SPIs.
**Alternatives:**
- Replace parameters — remove `activeSubjects` from signature, require resolver. Breaking change for all callers.
**Rationale:** No breaking change. Both paths coexist. Resolver/mapper overloads are the preferred path for new consumers.
**Trade-offs:** Two entry points per method — documentation must clarify which to use.
**Depends on:** D1 (separate SPIs), D2 (CognitiveImpact record)
**Exploration:** quick
**Status:** captured

## D4: Package location — io.casehub.blocks.agentic.social

**Choice:** All three SPIs + `CognitiveImpact` in `io.casehub.blocks.agentic.social` alongside CognitionCore and the existing signal/orchestrator types.
**Alternatives:**
- New sub-package `io.casehub.blocks.agentic.social.context` — cleaner separation but over-organizes 4 types
**Rationale:** The social package already contains the signal types, config types, and orchestrators these SPIs interact with. Co-location with CognitionCore is natural.
**Trade-offs:** None significant — package is already large but well-organized by convention.
**Exploration:** quick
**Status:** captured

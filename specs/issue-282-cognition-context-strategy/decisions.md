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

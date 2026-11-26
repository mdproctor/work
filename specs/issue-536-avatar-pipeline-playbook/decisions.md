## D1: Pipeline architecture approach

**Choice:** Pipeline-position-aware extension of TextFilter/CleanupConfig — extend the existing text-filtering infrastructure with pipeline-position awareness and YAML-level composition. Compile YAML pipeline definitions via the established compilation pattern (spec → compiler → compiled runtime) used by `AvatarCompiler`, `PatternCompiler`, and `PipelineCompiler`.
**Alternatives:**
- New sealed `SpeechStepSpec` hierarchy — ignores existing TextFilter/CleanupConfig, creates a parallel text-filtering SPI
- Stage Interceptors — interceptor hooks at fixed points; too limited, can't reorder or compose stages
- Generic Pipeline DSL — reusable beyond speech; over-engineered, no second consumer today
**Rationale:** The existing `TextFilter`/`CleanupConfig` from `connectors-voice-spi` already provides the core text→text transformation SPI with 4 production implementations (`FillerRemovalFilter`, `CasingFilter`, `PunctuationFilter`, `GectorFilter`), automatic destructiveness-based ordering, and compiler-based resolution via `AvatarCompiler.compileCleanup()`. Rather than creating a parallel SPI, the pipeline abstraction builds on this infrastructure by adding position awareness (post-STT, post-LLM, post-segmenter) and YAML-level composition. The compilation pattern (spec records → compiler → compiled runtime) is well-established in the codebase.
**Trade-offs:** Ties pipeline evolution to the TextFilter SPI in `connectors-voice-spi` (a cross-project dependency). Non-text step types (audio transforms) would require extending the SPI surface; deferred under YAGNI — no concrete non-text use case exists today.
**Sources:** `connectors-voice-spi: TextFilter`, `connectors-voice-spi: CleanupConfig`, `avatar-yaml/compiler/AvatarCompiler.java`, `agentic-yaml/compiler/PatternCompiler.java`
**Exploration:** quick
**Status:** revised (R1 — acknowledged TextFilter/CleanupConfig as prior art, integrated rather than duplicated)

## D2: Module placement

**Choice:** Extend `avatar-yaml` module for pipeline spec records + compiler. Text→text pipeline steps implement `TextFilter` from `connectors-voice-spi` (no new SPI module needed for the common case).
**Alternatives:**
- New `speech-pipeline` module — cleaner separation but adds a module to an already large project
- Step SPI in `speech-api` — wrong layer: `speech-api` holds orchestration-level SPIs (SpeechPromptAssembler, AvatarCognition, PerformanceVocabulary, CorrectionHooks, TtsModelRegistry), not data-transformation SPIs
- In `speech-ws` directly — wrong layer, speech-ws is Quarkus runtime not a spec module
**Rationale:** Keeps related YAML surface together (AvatarDefinition + pipeline specs in same module). The data-transformation SPIs (`TextFilter`, `PhonemeAligner`, `TextToSpeech`, `StreamingSpeechToText`, `CleanupConfig`, `LipSyncEnricher`, `SpeakerEmbeddingExtractor`, `SpeakerRegistry`) all live in `connectors-voice-spi`. The `speech-api` module holds orchestration-level SPIs. For the common case (text→text pipeline steps), no new SPI is needed — steps implement the existing `TextFilter` interface.
**Trade-offs:** avatar-yaml grows in scope. Acceptable — the addition is incremental (~3-5 types alongside the existing ~9), and avatar YAML surface remains the cohesive concern.
**Sources:** `avatar-yaml/` module structure, `connectors-voice-spi` SPI interfaces, `speech-api/` SPI interfaces
**Exploration:** quick
**Status:** revised (R1 — corrected PhonemeAligner location error, aligned SPI placement with actual module boundaries)

## D3: Step ordering mechanism

**Choice:** Explicit YAML ordering for pipeline structure (heterogeneous step positions), with compiler-time type-chain validation and preserved automatic destructiveness ordering within text-filter phases.
**Alternatives:**
- Pure explicit ordering without validation — user manually positions all steps, type mismatches detected at runtime only
- Phase-based with priority — steps declare phase and priority; framework sorts; hides ordering from user
- Named insertion points — custom steps reference built-in step names; couples custom to built-in names
**Rationale:** The pipeline has steps at different insertion points (post-STT, post-LLM, post-segmenter) that operate on different data. YAML ordering gives users explicit control over pipeline structure. The compiler validates that the step sequence forms a valid type chain — a text→text step placed after TTS would be caught at compile time, not at runtime. Within a text-filter phase, the existing `CleanupConfig` automatic destructiveness-based ordering is preserved — users don't need to manually order cleanup filters.
**Trade-offs:** Two ordering mechanisms (explicit YAML for pipeline structure, automatic destructiveness for text filters within a phase). This matches the existing design — `CleanupConfig` already sorts by destructiveness, and the pipeline adds the structural layer on top.
**Sources:** `connectors-voice-spi: CleanupConfig` (automatic destructiveness ordering), `summarisation-yaml/PipelineDefinition.java` (levels ordered by list position)
**Exploration:** quick
**Status:** revised (R1 — added compiler-time type validation, preserved automatic destructiveness ordering)

## D4: Streaming model

**Choice:** Per-segment processing — post-LLM custom steps run per sentence segment via UtteranceSegmenter, preserving current streaming behaviour. Pipeline covers three insertion points: post-STT/pre-LLM, post-LLM/pre-segmenter, and post-segmenter/pre-TTS.
**Alternatives:**
- Whole-turn — steps process full turn text; breaks streaming, user waits for full LLM response
- Configurable per step — each step declares segment vs whole-turn; more complex
**Rationale:** Preserves the current low-latency streaming architecture. The pipeline addresses all three insertion points in the existing flow (Mic → STT → **[post-STT]** → corrector → LLM → **[post-LLM]** → UtteranceSegmenter → **[post-segmenter]** → TTS → lip-sync). Post-STT steps integrate with the existing `CleanupConfig` chain. Post-LLM steps operate on the full LLM response before segmentation. Post-segmenter steps run per-segment, maintaining streaming.
**Trade-offs:** Steps that need full-turn context (e.g. a summary-based topic detector) cannot see the whole LLM response before segmentation. This is a known limitation, explicitly out of scope for the initial design — not a deferred escape hatch.
**Depends on:** D1 (pipeline abstraction provides the per-segment execution model)
**Sources:** `speech-ws/TurnExecutor.java` (sentence-level segmentation), `speech-ws/UtteranceSegmenter.java`, `speech-ws/SpeechSession.java` (cleanup chain in handleStop/handleText)
**Exploration:** quick
**Status:** revised (R1 — addressed all three insertion points, removed hand-waved "async opt-in path", explicit scoping)

## D5: Custom step SPI shape

**Choice:** Text→text pipeline steps implement `TextFilter` from `connectors-voice-spi` directly. The pipeline compiler resolves step implementations from YAML configuration via direct construction (consistent with `AvatarCompiler.compileCleanup()`). Position in the pipeline is determined by the YAML step list and insertion-point metadata, not by a Phase enum on the step interface.
**Alternatives:**
- New single interface `SpeechPipelineStep` with Phase enum — duplicates TextFilter for text→text, creates closed-type problem with Phase enum, splits step resolution between CDI and compiler
- CDI `@Named` discovery — contradicts the existing compilation pattern where the compiler directly constructs implementations
- Sealed interface per data-type pair (`TextTransformStep`, `AudioTransformStep`) — type-safe but proliferates interfaces for a problem currently dominated by one type (text→text)
**Rationale:** `TextFilter` already provides exactly what text→text pipeline steps need: `String apply(String text)` with `name()` for identification and `destructiveness()` for automatic ordering within cleanup phases. `AvatarCompiler.compileCleanup()` directly instantiates filter implementations — no CDI container required. The YAML surface fully describes the pipeline and the compiler validates the full step chain at compile time. Non-text step types are deferred — no concrete use case exists today.
**Trade-offs:** Non-text step types (audio transforms) would require a new SPI interface in `connectors-voice-spi`. Adding a type-safe interface per data-type pair at that point is straightforward.
**Depends on:** D1 (pipeline builds on TextFilter), D2 (TextFilter lives in connectors-voice-spi)
**Sources:** `connectors-voice-spi: TextFilter`, `avatar-yaml/compiler/AvatarCompiler.compileCleanup()`, `speech-ws/SpeechSession.handleStop()` (cleanup chain usage)
**Exploration:** quick
**Status:** revised (R1 — builds on TextFilter, compiler-based resolution, removed Phase enum and CDI @Named)

## D6: Extend existing infrastructure vs create parallel system

**Choice:** Extend existing `TextFilter`/`CleanupConfig` infrastructure with position awareness and YAML composition, rather than creating a parallel text-filtering SPI.
**Alternatives:**
- New parallel SPI (`SpeechPipelineStep`) with `processText(String) → String` — duplicates TextFilter, requires consumers to choose between two text-filtering SPIs with no clear guidance on when to use which
- Extend TextFilter interface itself with position fields — adds pipeline concerns to a cross-project SPI, potentially breaking existing implementations
- Wrapper/adapter — TextFilter stays unchanged, pipeline-level adapter adds position awareness without modifying the SPI
**Rationale:** TextFilter/CleanupConfig provides text→text transformation (4 production implementations), automatic destructiveness ordering, and compiler-based resolution. What it lacks — position awareness and YAML step naming — is added at the pipeline compilation layer without modifying TextFilter. The compiler maps YAML step definitions to TextFilter implementations and positions them in the pipeline. This is consistent with the existing design where `CleanupConfig` is composed by `AvatarCompiler`, not self-assembled.
**Sources:** `connectors-voice-spi: TextFilter`, `connectors-voice-spi: CleanupConfig`, `avatar-yaml/compiler/AvatarCompiler.compileCleanup()`
**Exploration:** quick (implicit decision surfaced by reviewer in R1)
**Status:** captured

## D7: Post-STT vs post-LLM processing abstraction level

**Choice:** Separate pipeline positions with shared TextFilter SPI — post-STT and post-LLM text processing share the same `TextFilter` interface but are positioned differently in the pipeline, with different default ordering semantics.
**Alternatives:**
- Separate SPIs per position — different interfaces for post-STT and post-LLM steps; type-safe position enforcement but over-engineers the distinction when both are String→String
- Single undifferentiated position — all text steps in one phase; loses the semantic distinction between mechanical STT cleanup and policy-based LLM post-processing
**Rationale:** Post-STT cleanup (filler removal, casing, punctuation, grammar correction) is mechanical, model-based, and benefits from automatic destructiveness ordering. Post-LLM processing (profanity filtering, vocabulary normalization) is policy-based and benefits from explicit ordering. Both are String→String transforms implementing `TextFilter`. The pipeline compiler distinguishes them by position, applying automatic destructiveness ordering for post-STT and explicit YAML ordering for post-LLM.
**Trade-offs:** A filter could be placed at the wrong position (e.g. GectorFilter post-LLM). The YAML surface makes positioning explicit and reviewable; this is a user-correctness concern, not a type-safety gap.
**Sources:** `speech-ws/SpeechSession.handleStop()` (post-STT cleanup), `speech-ws/TurnExecutor.processUserTurn()` (post-LLM segmentation)
**Exploration:** quick (implicit decision surfaced by reviewer in R1)
**Status:** captured

## D8: Step discovery mechanism

**Choice:** Compiler-based resolution — the pipeline compiler directly constructs step implementations from YAML configuration, consistent with `AvatarCompiler.compileCleanup()`.
**Alternatives:**
- CDI `@Named` qualifier — runtime discovery; contradicts the compilation pattern and means the YAML surface doesn't fully describe the pipeline without CDI context
- ServiceLoader — standard Java SPI discovery; adds classpath scanning overhead for a problem the compiler can solve at build time
**Rationale:** `AvatarCompiler.compileCleanup()` directly instantiates `FillerRemovalFilter`, `CasingFilter`, `PunctuationFilter`, `GectorFilter` from YAML configuration. The pipeline compiler follows the same pattern: YAML step name → implementation class → direct construction. The full pipeline is known and validatable at compile time, and steps can be unit-tested without a CDI container.
**Trade-offs:** Adding a new step type requires updating the compiler's resolution logic (a switch expression), not just deploying a jar with a `@Named` qualifier. Acceptable — adding a new step type is a deliberate design change that should go through the compilation layer.
**Sources:** `avatar-yaml/compiler/AvatarCompiler.compileCleanup()`, `speech-ws/SpeechWebSocket.java` (CDI is for runtime wiring, not compilation)
**Exploration:** quick (implicit decision surfaced by reviewer in R1)
**Status:** captured

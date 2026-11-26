## D1: Pipeline architecture approach

**Choice:** Typed Step Chain — sealed `SpeechStepSpec` hierarchy with ordered step list in `avatar.yaml`, compiled by `SpeechPipelineCompiler` into `CompiledSpeechPipeline`.
**Alternatives:**
- Stage Interceptors — interceptor hooks at fixed points; too limited, can't reorder or compose stages
- Generic Pipeline DSL — reusable beyond speech; over-engineered, no second consumer today
**Rationale:** Purpose-built for the speech pipeline's heterogeneous step types (audio→text, text→text, text→audio). Follows the established pattern (sealed spec records → compiler → compiled runtime) used by `PatternDefinition`/`PatternCompiler` and `PipelineDefinition`/`PipelineCompiler`. Type-safe, backward compatible.
**Trade-offs:** Not reusable for non-speech pipelines; if a second pipeline consumer appears, may need a generic layer. Acceptable — YAGNI.
**Sources:** `avatar-yaml/spec/AvatarDefinition.java`, `avatar-yaml/compiler/AvatarCompiler.java`, `summarisation-yaml/PipelineDefinition.java`, `agentic-yaml/spec/PatternDefinition.java`
**Exploration:** quick
**Status:** captured

## D2: Module placement

**Choice:** Extend `avatar-yaml` module for spec records + compiler. Step SPI interface in `speech-api`.
**Alternatives:**
- New `speech-pipeline` module — cleaner separation but adds a module to an already large project
- In `speech-ws` directly — wrong layer, speech-ws is Quarkus runtime not a spec module
**Rationale:** Keeps related YAML surface together (AvatarDefinition + SpeechPipelineSpec in same module). Step SPI in speech-api follows the existing pattern where SPIs live (SpeechPromptAssembler, AvatarCognition, PhonemeAligner).
**Trade-offs:** avatar-yaml grows in scope. Acceptable — it's the natural home for avatar-related YAML surfaces.
**Sources:** `avatar-yaml/` module structure, `speech-api/` SPI interfaces
**Exploration:** quick
**Status:** captured

## D3: Step ordering mechanism

**Choice:** Explicit YAML ordering — steps execute in the order they appear in the YAML list.
**Alternatives:**
- Phase-based with priority — steps declare phase (PRE_STT, POST_STT etc.) and priority; framework sorts; hides ordering from user
- Named insertion points — custom steps reference built-in step names; couples custom to built-in names
**Rationale:** Simple, explicit, no magic. The user sees exactly what order steps run by reading the YAML top to bottom. No hidden sorting or priority conflicts.
**Trade-offs:** User must manually position custom steps correctly. No automatic validation that a text→text step appears between STT and TTS rather than after TTS. Acceptable — explicit is better than implicit.
**Sources:** `summarisation-yaml/PipelineDefinition.java` (levels are also ordered by list position)
**Exploration:** quick
**Status:** captured

## D4: Streaming model

**Choice:** Per-segment processing — post-LLM steps (custom text filters, TTS, lip-sync) run per sentence segment, preserving current streaming behaviour.
**Alternatives:**
- Whole-turn — steps process full turn text; breaks streaming, user waits for full LLM response
- Configurable per step — each step declares segment vs whole-turn; more complex
**Rationale:** Preserves the current low-latency streaming architecture. UtteranceSegmenter stays as the boundary between LLM output and per-segment processing. Post-LLM custom steps are text filters that naturally operate per-segment.
**Trade-offs:** Steps that need full-turn context (e.g. a summary-based topic detector) can't see the whole response. Acceptable — such use cases are rare and can use the async opt-in path.
**Depends on:** D1 (typed step chain provides the per-segment execution model)
**Sources:** `speech-ws/TurnExecutor.java` (sentence-level segmentation), `speech-ws/UtteranceSegmenter.java`
**Exploration:** quick
**Status:** captured

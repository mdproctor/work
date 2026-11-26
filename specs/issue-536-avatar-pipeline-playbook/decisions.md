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

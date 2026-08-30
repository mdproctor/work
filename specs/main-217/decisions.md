# Decisions — CosyVoice TTS Pipeline Framework (#217)

## D1: Model target — CosyVoice3 instead of CosyVoice2

**Choice:** Integrate CosyVoice3 (ayousanz/cosy-voice3-onnx) instead of CosyVoice2 (Lourdle/CosyVoice2-0.5B_ONNX)
**Alternatives:**
- CosyVoice2 ONNX — incomplete export (only flow+hift, missing LLM backbone). Cannot do text-to-speech without the LLM.
- Wait for complete CosyVoice2 export — blocks the issue indefinitely
- Skip to Dia (#214) — viable but CosyVoice3's 4-stage architecture is a stronger genericisation test
**Rationale:** CosyVoice3 has a complete 14-file ONNX pipeline (campplus, speech tokenizer, LLM backbone, flow decoder, HiFT vocoder). 3.8GB total. Apache 2.0. Includes reference Python inference (`onnx_inference_pure.py`) for porting. The 4-stage pipeline is architecturally different from Audio8's DualAR — proving the pipeline framework works for both validates the abstraction. CosyVoice2's ONNX export is missing the LLM backbone; the Lourdle repo only exported flow+hift.
**Trade-offs:** CosyVoice3 is 3.8GB (vs Audio8's 1GB). CPU performance is 73-226s per utterance (addressed by D4). onnxruntime 1.18.0 constraint documented for Python bindings — C API via FFM may not hit same issue (risk to verify).
**Sources:** [HuggingFace Lourdle/CosyVoice2-0.5B_ONNX](https://huggingface.co/Lourdle/CosyVoice2-0.5B_ONNX), [HuggingFace ayousanz/cosy-voice3-onnx](https://huggingface.co/ayousanz/cosy-voice3-onnx), GitHub issue #217
**Exploration:** deep-analysis
**Status:** captured

## D2: Pipeline abstraction — manifest-driven with stage SPIs

**Choice:** Pipeline descriptor approach — a `TtsPipelineManifest` (JSON in model directory) declares stages, ONNX files, tensor mappings, hyperparameters, and execution provider. Four typed stage SPIs: `TtsTokenizer`, `TtsGenerator`, `TtsDecoder`, `TtsVoiceEncoder`. Framework manages session lifecycle, streaming, voice registry, provisioning.
**Alternatives:**
- Functional composition (builder DSL) — more flexible but "adding a model" means writing code, not configuration. Builder DSL becomes complex with stage interdependencies.
- Template method per model family — too rigid for architectural diversity between Audio8 (DualAR + Mamba) and CosyVoice3 (transformer KV-cache + flow diffusion + HiFT vocoder)
- Shared interface + model impls (no manifest) — less ambitious, doesn't genericise session lifecycle or provisioning
**Rationale:** Matches the "configuration of pipelines" constraint. The manifest handles everything the framework can genericise (ONNX files, tensor names, hyperparameters, execution providers, streaming config). Stage SPIs stay minimal — four interfaces per model. Adding a new model = write stage implementations + create manifest JSON.
**Trade-offs:** The autoregressive generator stage is always model-specific (DualAR with Mamba state vs transformer KV-cache with flow diffusion). The SPI can't abstract that away — it standardises the contract around it. Manifest parsing adds a layer of indirection.
**Sources:** Audio8TextToSpeech.java, DualARLoop.java, CosyVoice3 inference pipeline
**Exploration:** deep-analysis
**Status:** captured

## D3: Retrofit scope — Audio8 + CosyVoice3 together

**Choice:** Extract Audio8's existing stages into the pipeline framework alongside CosyVoice3 as a second configuration. Both models validate the abstraction.
**Alternatives:**
- CosyVoice3 first, retrofit Audio8 later — faster to ship but risks an abstraction that only fits one model
- Parallel but separate — leave Audio8 as-is, both share TextToSpeechService contract already
**Rationale:** Building both together proves the pipeline framework works for two architecturally different models: DualAR autoregressive (Audio8) vs LLM + flow diffusion + HiFT (CosyVoice3). An abstraction validated by only one model is untested.
**Trade-offs:** Higher upfront effort. Audio8's existing tests must continue passing after refactoring into the pipeline framework.
**Sources:** Audio8TextToSpeech.java, DualARLoop.java, CodecDecoder.java, VoiceRegistry.java
**Exploration:** quick
**Status:** captured

## D4: Performance — GPU execution providers from day one

**Choice:** Add ORT GPU execution provider support (CUDA, CoreML) as part of the pipeline framework. Pipeline manifest declares provider preference; framework auto-detects availability and falls back to CPU.
**Alternatives:**
- CPU-only, accept slow — validates framework and quality but CosyVoice3 is unusable for interactive speech (73-226s)
- CPU with streaming focus — reduces time-to-first-audio but total latency remains high
**Rationale:** CosyVoice3 on CPU takes 73-226 seconds per utterance. GPU acceleration makes it usable for interactive avatar speech. ORT's execution provider API is a session-level configuration — adding it to the pipeline manifest is a natural fit. macOS gets CoreML; Linux/cloud gets CUDA.
**Trade-offs:** Additional complexity — provider detection, session option configuration, potential memory management differences. Need `onnxruntime-gpu` native library on CUDA path. CoreML provider may need model conversion to CoreML format.
**Depends on:** D2 (pipeline manifest carries provider config)
**Sources:** [OnnxRuntime Execution Providers docs](https://onnxruntime.ai/docs/execution-providers/), OnnxRuntimeLibrary.java (line 49: ORT_API_VERSION = 18)
**Exploration:** quick
**Status:** captured

## D5: Voice cloning — unified VoiceRegistry with pluggable VoiceEncoder SPI

**Choice:** Extend VoiceRegistry with a pluggable `VoiceEncoder` SPI. Audio8 encodes to codec tokens; CosyVoice3 encodes to speaker embedding + speech tokens. Registry caches whatever the encoder produces. Same register/release/get API for consumers.
**Alternatives:**
- Per-model voice cloning — each pipeline handles its own cloning. Duplicates lifecycle management.
- Defer voice cloning — CosyVoice3 ships with default voice only. Reduces scope but loses a key feature.
**Rationale:** VoiceRegistry already has the right lifecycle (register → cache → release). The only model-specific part is encoding reference audio to a model-native representation. A pluggable `VoiceEncoder` makes the registry generic. CosyVoice3's encoder runs campplus (speaker embedding) + speech_tokenizer (speech tokens) — a different representation from Audio8's codec tokens, but the same lifecycle.
**Trade-offs:** VoiceEncoder output type must be generic (`Object` or a sealed hierarchy) since Audio8 produces `int[]` (codec tokens) and CosyVoice3 produces `VoiceData(float[] embedding, int[] speechTokens)`. The generator stage must know how to consume the model-specific voice data.
**Depends on:** D2 (pipeline framework owns voice registry)
**Sources:** VoiceRegistry.java, CosyVoice3 campplus + speech_tokenizer architecture
**Exploration:** quick
**Status:** captured

## D6: Streaming — first-class pipeline concern

**Choice:** Streaming-aware pipeline. Stages declare whether they support incremental processing. The autoregressive generator pushes tokens to downstream stages that can run on chunks. Audio8's existing windowed overlap-subtract becomes a standard pipeline capability.
**Alternatives:**
- Batch first, stream later — simpler initial implementation but requires rework to add streaming
- Stream at TextToSpeechService level only — keep streaming external (like Audio8's current synthesiseStreaming). Pipeline batch-only internally.
**Rationale:** Both Audio8 and CosyVoice3 have autoregressive generators that produce tokens incrementally. The decoder (codec or flow+HiFT) can potentially run on partial output. Making streaming a pipeline-level concern means the framework handles chunking, overlap, and delivery — model implementations just produce tokens and declare their streaming capabilities.
**Trade-offs:** More complex pipeline infrastructure. CosyVoice3's flow decoder uses 10-step Euler diffusion — running it incrementally on partial token sequences needs investigation (may not be possible without significant quality loss). If flow can't stream, the pipeline falls back to batch for that stage while still streaming the LLM tokens.
**Depends on:** D2 (pipeline framework manages streaming)
**Sources:** Audio8TextToSpeech.synthesiseStreaming(), DualARLoop.iterateFrames(), CosyVoice3 flow.decoder.estimator architecture
**Exploration:** quick
**Status:** captured

## D7: Multi-model stages — stage groups in manifest

**Choice:** Each stage declares a list of ONNX files. The stage implementation knows how to wire them internally. Example: HiFT stage lists `[hift_f0.onnx, hift_source.onnx, hift_decoder.onnx]`.
**Alternatives:**
- Flat model list with stage tags — all files listed flat, grouped by tag at load time. More flexible but loses the stage grouping semantics.
- Nested stage hierarchy — stages contain sub-stages. Maximum flexibility but over-complex manifest schema.
**Rationale:** CosyVoice3 has 14 ONNX files across 4 stages. HiFT alone uses 3 models. The stage implementation knows the internal wiring (f0 → source → decoder). The manifest only needs to declare which files belong to which stage — the framework loads sessions and passes them to the stage constructor.
**Trade-offs:** Stage implementations must handle their own internal multi-session coordination. But this is inherently model-specific anyway.
**Depends on:** D2 (manifest-driven pipeline)
**Sources:** CosyVoice3 architecture (14 ONNX files, 4 stages)
**Exploration:** quick
**Status:** captured

## D8: Voice type variance — generic VoiceData record

**Choice:** `VoiceData(Map<String, byte[]> segments)` — named byte segments. Audio8 stores `"codec_tokens"`, CosyVoice3 stores `"speaker_embedding"` + `"speech_tokens"`. Generator reads segments by key.
**Alternatives:**
- Sealed hierarchy (CodecVoice, EmbeddingVoice) — type-safe but requires adding a class per model
- Raw Object with casts — minimal framework code but no type safety at registry boundary
**Rationale:** Named segments are generic without losing identity. Each model documents its expected segment keys. The generator stage reads what it needs by key — type-safe within model code, generic across models. Segments are byte[] for serialisation simplicity; the generator deserialises to its native types.
**Trade-offs:** Segment keys are stringly-typed — a typo silently returns null. Mitigated by model-specific constants for key names.
**Depends on:** D5 (unified VoiceRegistry)
**Sources:** VoiceRegistry.java, CosyVoice3 campplus (float[] embedding) + speech_tokenizer (int[] speech tokens)
**Exploration:** quick
**Status:** captured

## D9: GPU execution providers — auto-detect with manifest override

**Choice:** Framework auto-detects available GPU providers at startup (probe CUDA library, CoreML availability). Manifest can override with preferred provider and per-stage provider. Fallback chain: manifest preference → auto-detected GPU → CPU.
**Alternatives:**
- Explicit-only — no auto-detection, manifest must declare provider. Requires users to know hardware.
- Global config — system property or env var, not per-pipeline. Can't mix CPU/GPU stages.
**Rationale:** Auto-detection makes the default fast (GPU when available) without requiring manifest changes per deployment. Per-stage override enables keeping HiFT on CPU for numerical stability if needed (CosyVoice3 docs note FP32 is "required for HiFT numerical stability"). Manifest override gives control when auto-detection picks wrong.
**Trade-offs:** Provider detection adds startup complexity. CUDA requires `onnxruntime-gpu` native library. CoreML may need model conversion. Provider probing must be fast (check library availability, not session creation).
**Depends on:** D2 (manifest carries provider config), D4 (GPU from day one)
**Sources:** [OnnxRuntime Execution Providers](https://onnxruntime.ai/docs/execution-providers/), OnnxRuntimeLibrary.java, CosyVoice3 precision strategy notes
**Exploration:** quick
**Status:** captured

## D10: Module placement — all in speech-sherpa

**Choice:** Pipeline framework lives in `speech-sherpa` alongside all ONNX-based TTS implementations. Stage SPIs, manifest parsing, session lifecycle, GPU provider detection — all in the same module.
**Alternatives:**
- New speech-pipeline module — cleaner separation but adds a module for a single-consumer abstraction
- Split SPIs to speech-api — maximum decoupling but SPIs inherently reference ORT sessions
**Rationale:** The pipeline framework is ONNX-specific — it manages ORT sessions, GPU providers, and model provisioning. These are all speech-sherpa concerns. Splitting into speech-api would create a leaky abstraction since the SPIs need ONNX session references. A separate module adds build complexity without adding consumers.
**Trade-offs:** speech-sherpa grows larger. But the pipeline framework replaces boilerplate currently in Audio8TextToSpeech — net size increase is moderate.
**Depends on:** D2 (pipeline framework architecture)
**Sources:** speech-sherpa module structure, speech-api module (PhonemeAligner, LipSyncEnricher already there)
**Exploration:** quick
**Status:** captured

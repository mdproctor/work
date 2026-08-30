# HANDOFF — casehub-blocks

## Last Session

Designed and began implementing the TTS pipeline framework (#217). CosyVoice2's ONNX export was incomplete (no LLM backbone) — pivoted to CosyVoice3 which has a complete 14-model pipeline. Built the manifest-driven pipeline framework with sealed hierarchies (VoiceData, GeneratorOutput, TtsPipelineManifest), four stage SPIs, generalised VoiceRegistry, and TtsPipeline implementing TextToSpeechService. Batch 1 of 4 complete — 362 tests passing.

## Immediate Next Step

Batch 2: DSP primitives — FFT (Cooley-Tukey radix-2), MelSpectrogram (3 configs for campplus/whisper/flow), STFT/ISTFT (n_fft=16 for HiFT), AudioResampler (sinc interpolation). All pure Java, verified against librosa/scipy reference values.

## References

- Spec: `specs/main-217/2026-08-30-tts-pipeline-framework-cosyvoice3-design.md`
- Decisions: `specs/main-217/decisions.md` (13 decisions, D1-D13)
- Plan: `plans/2026-08-30-tts-pipeline-cosyvoice3.md` (10 tasks, 4 batches)
- Blog: `blog/2026-08-30-mdp01-the-pipeline-that-isnt-hard-coded.md`
- CosyVoice3 reference: `ayousanz/cosy-voice3-onnx` on HuggingFace (14 ONNX files, `onnx_inference_pure.py`)

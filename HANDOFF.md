# HANDOFF — casehub-blocks

## Last Session

Completed Batches 2 and 3 of the CosyVoice3 TTS pipeline (#217). Batch 2 added pure-Java DSP primitives: Cooley-Tukey radix-2 FFT, MelSpectrogram with three mel configs (campplus 80-band, whisper 128-band, flow 80-band), STFT/ISTFT with overlap-add reconstruction (verified with HiFT n_fft=16 params), and windowed sinc audio resampler. Batch 3 added the Qwen2 byte-level BPE tokenizer (vocab.json + merges.txt format) and CosyVoice3VoiceEncoder (campplus speaker embedding + speech tokenizer + flow prompt mel extraction with functional interfaces for testability). 420 tests passing, 0 failures.

## Immediate Next Step

Batch 4: CosyVoice3 generator + decoder + end-to-end wiring — the most complex batch. Three tasks:
- Task 8: CosyVoice3Generator — LLM autoregressive pipeline with KV-cache (5 ONNX sessions: text_embedding, llm_speech_embedding, llm_backbone_initial, llm_backbone_decode, llm_decoder). Top-k sampling, EOS detection, NaN guard.
- Task 9: CosyVoice3Decoder — flow diffusion (10-step Euler ODE, 4 ONNX sessions) + HiFT vocoder (f0 + source + STFT/ISTFT decoder, 3 ONNX sessions). Uses StftUtils with n_fft=16.
- Task 10: Provisioner + manifest + end-to-end wiring via TtsPipeline.fromModelDir().

## Design Notes for Batch 4

- Generator and Decoder should follow the same testability pattern as CosyVoice3VoiceEncoder — functional interfaces for ORT inference calls, with fromSessions() factory methods for production wiring.
- KV-cache: [48, 1, 2, seq_len, 64] allocated per-call via Arena.ofConfined().
- Flow decoder: 10 Euler steps, Gaussian noise init, speaker embedding L2-normalized.
- HiFT: f0 → source → STFT(n_fft=16, hop=4) → decoder → ISTFT → clamp [-0.99, 0.99].
- ORT FP16 auto-cast: 9 of 14 models are FP16. C API should auto-cast FP32 inputs — verify in integration test.

## References

- Spec: `specs/main-217/2026-08-30-tts-pipeline-framework-cosyvoice3-design.md`
- Decisions: `specs/main-217/decisions.md` (13 decisions, D1-D13)
- Plan: `plans/2026-08-30-tts-pipeline-cosyvoice3.md` (10 tasks, 4 batches)
- CosyVoice3 reference: `ayousanz/cosy-voice3-onnx` on HuggingFace (14 ONNX files, `onnx_inference_pure.py`)

# Decisions — STT Accuracy Pipeline (#212)

## D1: Correction mechanism

**Choice:** SymSpell edit-distance spelling correction (pure algorithmic, no neural model)
**Alternatives:**
- Neural contextual spelling correction (ONNX seq2seq) — 51% WER reduction in research, but no pre-built model available; would need training
- LLM-based zero-shot correction — research shows this often increases errors via over-correction
**Rationale:** Zero model weight, microsecond latency, proven approach (phone keyboards). Combined with contextual vocabulary, handles most ASR errors without network calls or large models.
**Trade-offs:** Won't handle complex phonetic confusions where the word sounds completely different. Hotwords and contextual ranking mitigate this.
**Sources:** arXiv:2108.07493, SymSpell algorithm, sherpa-onnx#2570
**Exploration:** deep-analysis
**Status:** captured

## D2: Pipeline architecture

**Choice:** Mechanical layer first (ONNX + pure Java), LLM optional overlay
**Alternatives:**
- LLM-primary pipeline — highest accuracy but adds 200-500ms latency per correction
- Neural-only pipeline — requires training/finding ASR-specific ONNX models
**Rationale:** Mechanical processing is fast, deterministic, and works offline. LLM adds quality when available but isn't required.
**Trade-offs:** Mechanical layer has a ceiling — some errors only an LLM can fix. The optional LLM layer addresses this.
**Sources:** Research summary on zero-shot LLM over-correction, GECToR architecture precedent
**Exploration:** deep-analysis
**Status:** captured

## D3: Contextual candidate ranking

**Choice:** N-gram language model for ranking SymSpell candidates in context
**Alternatives:**
- Unigram frequency only — simpler but context-blind
- Neural language model — more accurate but heavier
**Rationale:** Bigram/trigram frequencies give contextual ranking at negligible cost. "hear a limerick" ranks higher than "hear a relimberate" because "a limerick" is a known bigram.
**Trade-offs:** N-gram models need a corpus. A pre-built English n-gram table (~50MB) covers general vocabulary. Domain terms added dynamically from conversation.
**Sources:** SymSpell documentation, standard n-gram language modeling
**Exploration:** quick
**Status:** captured

## D4: Hotword source

**Choice:** Dynamic hotwords from conversation context + static domain vocabulary
**Alternatives:**
- Static vocabulary only — misses conversation-specific terms
- LLM response mining only — reactive, not proactive
**Rationale:** Conversation terms (from both user and avatar responses) build the hotword list dynamically. Static domain vocabulary covers expected terms from the start.
**Trade-offs:** Hotwords only help the streaming Zipformer (transducer), not the Whisper offline model.
**Sources:** sherpa-onnx hotwords documentation, k2-fsa hotwords guide
**Exploration:** quick
**Status:** captured

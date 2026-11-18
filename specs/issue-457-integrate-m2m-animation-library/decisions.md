# Decisions — #457 Integrate M2M Animation Library

## Verification notes (decision review rounds 1–2)

Facts checked against code during the decision review. Later decisions rely on them.

- **Streaming path ordering.** `SpeechSession.handleStop` (L239–245) and `handleText` (L360–370) send `Response` only after `streamingGenerator.generate(...)` returns. By then every sentence has been synthesised and its `Phonemes` and audio sent. The non-streaming path (L261–271) and `handleProactiveSpeech` (L175–176) send `Response` before any audio. `Response` therefore has no consistent position relative to audio.
- **Turns can run concurrently on one connection.** `SpeechWebSocket.startProactiveTick` (L160–187) calls `session.handleProactiveSpeech` from its own virtual thread, while `@OnTextMessage` may be running `handleText`/`handleStop`. `SpeechSession` has no turn serialisation, and `history` is an unsynchronised `ArrayList`. Binary audio frames carry no identity.
- **Word boundaries survive inside VITS.** `EspeakLibrary.textToPhonemes` joins espeak clauses with `' '`, and espeak IPA output separates words with spaces. `VitsConfig.tokenize` keeps every `phoneme_id_map` key except `^ $ _`. `VitsTextToSpeech.extractPhonemeTimings` emits a `PhonemeTiming(" ", start, end)` for each space token where `endMs > startMs`. Caveats:
  - Space tokens that round to a 0 ms duration are dropped.
  - espeak word count ≠ text word count when espeak expands numbers or abbreviations.
  - Clause joins also produce space tokens.
- **The TTS SPI is cross-repo.** `SynthesisOptions(voice, language, audioFormat, includePhonemes)`, `SynthesisResult(audioData, audioFormat, phonemes)` and `LipSyncEnricher` live in `casehub-connectors-voice-spi`.
- **Tag pollution in STT vocabulary is real.** `ConversationVocabulary.addFromText` strips characters outside `[a-z']`. `[gesture:doublefacepalm]` therefore becomes the term `gesturedoublefacepalm` and enters the Whisper hint and the corrector dictionary.
- **The speech-api `PromptSection` has no production composer.** `SocialPromptAssembler` does not exist in production code; `docs/guides/consumer-guide.md` L668 is stale. (Round 1 claimed `SocialPromptAssembler` composes sections when cognition is present. That was wrong and is corrected here.) The only implementers and composers are in `agentic-yaml` test code. The cognition path is `CognitionAvatarAdapter.wrapAssembler` (agency-core L51–75): it calls `base.assemble(...)` first, then appends neocortex `core.promptSections()` renderers through `CognitionRenderContext`. That is a different abstraction.
- **Proactive speech is decided and prompted inside neocortex.** `CognitionAvatarAdapter.evaluateProactive` (L94–103) delegates to neocortex `ProactiveSpeechSupport.evaluateProactive(channelContext)`, which returns text or null. agency cannot add prompt sections to it, or add non-verbal outcomes, without a neocortex API change.
- **Cognition has its own mood model.** agency-core `CircuitBreakerMoodMapper` emits neocortex `MoodSignal.DirectShift`, so cognition maintains a mood state.
- **Demo client baseline.** The demo client imports **TalkingHead 1.8** (not 1.7) and `three@0.180.0`. It drives the body mixer from its own `requestAnimationFrame` loop (`tickBody`), separate from TalkingHead's loop. The #450-era build already has a client `case 'animation'` handler, but main's `AvatarMessage` has no `Animation` record, so #450 D5 was never completed server-side.
- **Not yet verified (#458 code).** `BodyAnimationSystem` was only reviewed from the build copy (`target/classes`, 16 Nov). Known problems there:
  - `GESTURE_MAP` still references clips deleted in #458 (`thinker`, `shocked-recoil`, `hands-interlocked-chest`, `hit-chest`).
  - The `finished` handler does not restart the cycle timer.
  - `TAXONOMY` (pool data) lives in JS.

  None of this has been checked against the #458 tip.

---

## D1: Branch base for #457 — land #458 first

**Choice:** Land #458 through `work-end` first, then recreate the #457 branch from the updated main.
**Alternatives:**
- Rebase on the #458 branch (original choice). Rejected: `work-end` squashes before landing. That rewrites #458's commits under #457 and forces a rebase onto the squash. Landing both "together" puts two issues in one squash, which loses the per-issue landing SHA and the `chore: branch closed — landed as <SHA>` stamp.
- Cherry-pick files. Rejected: it loses history and still duplicates #458's content.
**Rationale:** #458 is complete (HANDOFF: CLOSED). The #457 branch has no commits (its ref equals main `f8cffec`), so recreating it costs nothing. Per-issue landing keeps history auditable.
**Trade-offs:** #458 has to go through `work-end` (verification and review gate) before #457 implementation starts.
**Sources:** HANDOFF.md (#458 CLOSED), working-style branch-closure convention, `work-end` squash behaviour
**Exploration:** quick
**Status:** revised (round 1, R1-18). Was "rebase on #458 branch".

## D2: Gesture tag parsing — the server is the single parser

**Choice:** The server is the only parser of `[gesture:…]` and `[mood:…]` markup. A single `UtteranceSegmenter` in `speech-ws` turns the LLM text stream into `ParsedSegment(cleanText, List<Cue>)`. All three speech paths use it: streaming (`handleStop`/`handleText`), non-streaming, and `handleProactiveSpeech`. The client never sees markup. It receives clean text and typed cues (see D3).

Segmentation rules:
1. **Tags are parsed before sentence segmentation.** The segmenter runs over the streamed buffer. After a sentence boundary it holds the sentence back until it can tell whether a tag-only remainder follows. It releases the sentence when either:
   - any open `[` has closed **and** the next non-whitespace character is a word character; or
   - the stream ends.

   The hold is bounded by the length of one tag. A tag split across deltas extends it by at most one more delta.
2. **Forward binding.** A tag binds to the next spoken word. This matches the D7 prompt rule ("place the tag immediately before the word it accompanies").
3. **Trailing tags.** A tag with no following word in its sentence (`"Nice to meet you! [gesture:greeting]"`) binds to the **first word of that sentence**, which treats the whole sentence as the affiliate. It is never emitted as its own segment.
4. **No empty segments.** A segment whose clean text is empty is never sent to TTS. Its cues merge into the previous segment, or into the next one if there is no previous segment.
5. **Malformed or truncated markup is stripped.** Anything matching `\[(gesture|mood):[^\]]*\]?$` at end of stream, or an unclosed `[gesture:`/`[mood:` prefix, is removed before TTS. Unknown tags are validated against the performance vocabulary (D12) and dropped.
6. **Mood markup** is parsed by the same component. Whether it becomes a `MoodCue` or is stripped depends on who owns mood (D16). The client's `detectMood(msg.text)` is removed.

The client's `parseGestureTags` is deleted from `index.html` and from `body-animations.mjs`. No non-server consumer exists. A future TalkingHead upstream contribution would get its own API designed for that context, not a leftover from this one.
**Alternatives:**
- Server strips before TTS while `Response` keeps the tagged text (original choice). Rejected: the premise was false. On the streaming path `Response` arrives after all audio has been sent, so client-parsed gestures fire late. Combined with D3's server-delivered cues, every gesture would fire twice. The chat bubble would also show raw markup.
- Client-only parsing. Rejected: TTS would speak the tags.
- Per-path parsing (separate code in each of the three paths). Rejected: the paths already diverge (different splitters, different `Response` ordering). That divergence is the bug class here.
**Rationale:** One parser, one segmentation, one cue representation. Every consumer gets a deliberately chosen form (D9). The server already owns sentence segmentation, so it should own the markup that segmentation has to respect.
**Trade-offs:** A short hold after each sentence boundary. Forward binding misreads tags the LLM placed after their affiliate (`"Hello! [gesture:greeting] Let me explain."` binds to "Let"). The D7 placement rule is the contract that makes forward binding correct.
**Sources:** SpeechSession L174–183, L239–247, L261–271, L360–372; SpeechWebSocket `findSentenceBoundary` L219–227; index.html L151–152; SSML `<mark>` semantics (Azure, Google, Polly)
**Exploration:** deep-analysis, revised under review with new evidence (streaming-path ordering)
**Status:** revised (round 1, R1-02, R1-13; round 2: lookahead wording, mood routed through D16)

## D3: Protocol — segments carry text, visemes and cues; audio frames are identified; separate channels for non-speech gestures and mood

**Choice:** Replace `AvatarMessage.Phonemes` and `AvatarMessage.Response` with turn-framed segment messages.

```java
record Segment(int turnSeq, int index, String text, boolean hasAudio,
               List<VisemeFrame> visemes, List<Cue> cues) implements AvatarMessage {}
record TurnComplete(int turnSeq, int segmentCount, TurnOrigin origin) implements AvatarMessage {}   // D10
record Gesture(String tag, GestureOrigin origin) implements AvatarMessage {}                        // non-speech
record Mood(String state) implements AvatarMessage {}                                               // D16, cognition-owned baseline

sealed interface Cue {
    record GestureCue(String tag, long offsetMs) implements Cue {}
    record MoodCue(String mood, long offsetMs) implements Cue {}      // D16, turn-scoped, LLM-owned only without cognition affect
}
```

**Binary audio frames carry their identity.** Each frame starts with an 8-byte little-endian header, followed by the WAV bytes:
- `u8 version` (=1)
- `u8 reserved`
- `u16 segmentIndex`
- `u32 turnSeq`

The client pairs a frame with a segment by `(turnSeq, index)`, never by arrival order. A `Segment` with `hasAudio=false` never gets a frame, and the client does not wait for one.
- **`turnSeq`** is a per-session monotonically increasing `int`. It is used in both text and binary messages instead of a string `turnId`.
- **`cues`** is a list. Multiple gestures per sentence are allowed, and the density rule (D7) limits them.
- **`text`** is clean display text. The client builds the chat bubble from segments as they arrive, including segments with `hasAudio=false`. `Response` is removed: its content is the concatenation of the segment texts, and its ordering was inconsistent across paths.
- **`Gesture`** completes #450 D5, whose server-side record was never added to `AvatarMessage`. It carries only a vocabulary tag; #450's raw `url`/`durationSec` form is superseded. It is used for gestures that are not tied to speech: cognition-driven non-verbal acts and attention bids (D8).
- **`Mood`** sets the persistent mood baseline when cognition owns mood (D16).

**Alternatives:**
- Optional `gesture` + `wordIndex`/`wordCount` on `Phonemes` (original choice). Rejected:
  - A scalar field drops every tag after the first.
  - Delivery depended on visemes being non-empty.
  - The message name no longer described its content.
- Pair segments and frames by arrival order (round-1 text). Rejected: it is only correct if exactly one turn is ever in flight and every segment has audio. The header makes pairing correct by construction rather than by scheduling discipline (D10 also serialises turns, but correctness should not rest on that alone).
- `hasAudio` flag alone, without a frame header. Rejected: it still pairs by order, so any interleaving or dropped frame mis-pairs every later segment.
- Separate `Animation` message per sentence. Rejected for speech-timed cues: it needs ordering coupling with the audio frame. It is kept, as `Gesture`, for cues that are not timed to speech.
- Batch gesture map in `Response`. Rejected: `Response` has inconsistent timing across paths (D2).
**Rationale:** Speech-timed cues belong to the segment they are timed against, and audio must be addressable to that segment. Non-speech gestures need their own channel, which #450 already decided; the decision lapsed only because the server side never landed.
**Trade-offs:** Breaking protocol change: `Phonemes` and `Response` are gone, and binary frames gain a header. This is deliberate: every client and test that consumes them must be updated explicitly. The client slices 8 bytes off before `decodeAudioData`.
**Sources:** AvatarMessage.java (main); #450 decisions D1, D5; built index.html `case 'animation'`; index.html L154–160, L229–233; SpeechWebSocket L160–187; blog 2026-11-13 L76 (multiple tags per response)
**Exploration:** deep-analysis, revised under review with new evidence (#450 D5 lapse, viseme-gated delivery, concurrent turns)
**Depends on:** D2, D10, D16
**Status:** revised (round 1, R1-09, R1-10; round 2, R2-01: frame header, `hasAudio`, `turnSeq`; R2-04: `Mood` message)

## D4: Gesture timing — the server owns speech time, the client owns animation time

**Choice:**
- **Speech time (server).** Each cue carries `offsetMs`, the onset of its anchor word within the segment's audio, in **buffer time** (unscaled). The value comes from the TTS SPI as SSML-style mark timing:
  - `SynthesisOptions` gains `List<TextMark(String id, int charOffset)> marks`.
  - `SynthesisResult` gains `List<MarkTiming(String id, long offsetMs)> marks`.
- **Engines resolve marks with the best evidence available.** This is a cross-repo change in `casehub-connectors-voice-spi`, filed against connectors and sequenced in D17:
  - **VITS/Piper: exact, guarded.** Each mark's text prefix is phonemised with espeak and its IPA words are counted. That count indexes the space-token boundaries in the model's duration output, which handles number and abbreviation expansion. **Consistency guard:** the segment is accepted as exact only if `words(prefix_i) + words(suffix_i) == spaceTokens(full) + 1` for every mark, and the resulting offsets are monotonic. This catches context-dependent phonemisation, espeak function-word joining, clause-join spaces and dropped 0 ms space tokens. If the guard fails, that segment falls back to the phoneme-proportional tier and the fallback is logged.
  - **Phoneme-only engines: phoneme-proportional.** Engines with phoneme timings but no word boundaries (`LipSyncEnricher`/`EspeakPhonemeAligner`) use phoneme-proportional timing over the aligned phonemes.
  - **No phonemes: character-proportional** over the WAV duration.
  - A `MarkTimingEnricher` decorator, analogous to `LipSyncEnricher`, provides the two fallback tiers for any engine that returns no marks.
- **Animation time (client).** The client schedules each gesture as `trigger = max(0, offsetMs / rate − strokeMs(clip) − crossfade / 2)`:
  - `rate` is the playback rate. The viseme loop already uses buffer-time × rate.
  - `strokeMs` is per-clip metadata from the performance vocabulary (D12). The client computes it because it resolves subcategory picks, so only the client knows the concrete clip.
  - Triggers clamped to 0 are logged in debug mode, so clips with long preparation phases can be found.
- **Stroke metadata.** Initial `strokeMs` values come from an offline analysis script: peak wrist displacement or velocity relative to the clip's first frame. Values can be overridden by hand in the vocabulary file.

**Alternatives:**
- Word-index proportional: `wordIndex/wordCount × duration` on the client (original choice). Rejected for two reasons:
  - The client has no word information, because visemes merge word boundaries away. Any accuracy upgrade would have needed a protocol change, contradicting the original rationale.
  - The server already has exact word onsets for the default engine.
- Viseme-boundary mapping on the client. Rejected for the same reason: no word boundaries reach the client.
- Read space tokens directly in `speech-ws` from `synthesis.phonemes()`. Rejected: it leaks a VITS-specific detail (space phoneme = word boundary) into the engine-agnostic speech layer. Mark timing is the standard TTS abstraction (Azure `BookmarkReached`, Google timepoints, Polly speech marks).
- An interim timing estimator inside `speech-ws` until connectors lands. Rejected: it would duplicate the `MarkTimingEnricher` fallback tiers in the wrong layer and then have to be deleted. D17 sequences the connectors change first instead.
- Sentence-start only. Rejected: too coarse.
**Rationale:** The original rationale misapplied the McNeill/Kendon synchrony rule, which concerns the gesture **stroke**:
- The stroke precedes or coincides with its lexical affiliate.
- The M2M clips include a preparation phase.
- A crossfade adds latency rather than absorbing error.
Both effects push the stroke late, so the error is biased, not symmetric. Correct synchrony needs speech time (server-side knowledge) and stroke time (clip knowledge), each owned by the party that has it. The guard keeps "exact" honest: it is exact only when it can be shown to be.
**Trade-offs:**
- SPI change in a peer repo (connectors).
- Per-clip `strokeMs` metadata to maintain, mitigated by automatic derivation.
- Strokes that should precede segment start are clamped. The first segment of a turn cannot lead its own audio.
- Two extra espeak calls per mark, which are cheap compared with VITS inference.
**Sources:** VitsConfig.tokenize L94–127; VitsTextToSpeech.extractPhonemeTimings L135–171; EspeakLibrary.textToPhonemes L81–124; casehub-connectors-voice-spi SynthesisOptions/SynthesisResult/LipSyncEnricher; index.html L257–274 (rate handling); McNeill 1992 / Kendon 2004 (stroke–affiliate synchrony)
**Exploration:** deep-analysis, revised under review with new evidence (VITS word boundaries, stroke vs onset)
**Depends on:** D3, D12, D17
**Status:** revised (round 1, R1-04, R1-05; round 2, R2-08: consistency guard, no interim shim)

## D5: Speaking-state activation — follows the turn, not queue occupancy

**Choice:** The client enters **speaking** when the first audio-bearing segment of a turn starts playing. It leaves speaking only when both of these are true:
1. `TurnComplete(turnSeq)` has been received (D10).
2. Every `hasAudio` segment of that turn has been played and its audio has ended.

On leaving speaking, the client sends `PlaybackComplete(turnSeq)` to the server (D8, D10). Mid-turn queue drains, caused by slow synthesis of sentence N+1, do not change state. A 10 s safety timeout after the last audio ends forces idle if `TurnComplete` never arrives (dropped connection). The leave transition also respects D11: an in-flight gesture finishes before the base layer changes pool.
**Alternatives:**
- Audio playback start/end of the queue (original choice). Rejected: `playNextQueued` sets `isPlaying = false` whenever the queue drains between sentences. With Kokoro, Audio8 or CosyVoice3, or with multi-sentence proactive speech, the pose flickers idle → speaking every sentence.
- `Response` arrival. Rejected: it arrives early on the non-streaming and proactive paths and late on the streaming path (D2), and `Response` is removed anyway (D3).
- Queue drain plus hysteresis only. Rejected: a fixed hysteresis guesses at synthesis latency. An explicit end-of-turn signal makes the guess unnecessary.
**Rationale:** Speaking is a property of the turn. Only the server knows when a turn has no more segments, so it must say so. Only the client knows when the user has actually heard the turn, so it says so back.
**Trade-offs:** Depends on the server always sending `TurnComplete`, including on error paths (enforced in a `finally` block, see D10).
**Sources:** index.html `playNextQueued` L248–249, L299–307; BodyAnimationSystem.setSpeaking
**Exploration:** quick, revised under review
**Depends on:** D10, D11, D13
**Status:** revised (round 1, R1-06; round 2, R2-10: `PlaybackComplete` on leave)

## D6: Morph target conflicts — strip at load, re-apply as additive expression curves through one face owner

**Choice:**
- **At load**, `BodyAnimationSystem` removes all `*.morphTargetInfluences` tracks from every clip, exactly as it already removes head-bone tracks. GLTFLoader emits glTF `weights` channels as a whole-array track, so leaving them in writes all 72 influences: blink, eye look, mood and every `viseme_*`.
- **Expression curves.** Before discarding a morph track, the loader extracts a sparse, **name-keyed** expression curve:
  1. **Name resolution.** Track value index *i* is resolved to a morph target name through the **animation bundle's** mesh `morphTargetDictionary` (the bundle's `gltf.scene` `Wolf3D_Head`/`Wolf3D_Teeth`), never through the avatar's. The bundle's injected target order is not guaranteed to match `avatar.glb`.
  2. **Filtering.** Only allow-listed expression channels are kept: `brow*`, `mouthFrown*`, `mouthSmile*`, `mouthPress*`, `cheek*`, `noseSneer*`, `mouthPucker`. `viseme_*`, `eyeBlink*`, `eyeLook*` and `jaw*` are excluded.
  3. **Teeth.** The Teeth mesh carries no allow-listed expression channel and is dropped.
  4. **Application.** Curves are applied by name through the **avatar** mesh's `morphTargetDictionary`. A name that is missing on the avatar is skipped with a warning.

  Authoring stays in the GLB, where #458 put it, so there is no hand-maintained second copy.
- **One face owner (D14)** composes the face each frame, in order, before render:
  1. Base: TalkingHead's blink, mood and eyes.
  2. Visemes: replace the `viseme_*` channels.
  3. Gesture expression curves: added to the expression channels and clamped to [0, 1].
  4. The gesture expression weight follows the gesture layer's weight (D11), so it fades with the gesture.
- The shrug frown therefore also shows **during speech**, which is when the gesture means most (`shrug` sits in `conversational.uncertainty`, which is in the speaking pool and is a tag target).
- **Build-time test (D12).** It asserts that every allow-listed name used by any clip's curve exists in `avatar.glb`'s head mesh.

**Alternatives:**
- Conditional pass-through: body morphs only when not speaking (original choice). Rejected:
  - The whole-array track clobbers blink, mood and visemes.
  - It suppresses the frown exactly when the gesture is semantically relevant.
  - It produces a pop at speech boundaries.
- Strip all with no re-application. Rejected: it loses the authored expression.
- Hand-authored expression metadata in the vocabulary. Rejected in favour of automatic extraction: same runtime model, but a single source of authoring truth (the GLB).
- Index-based extraction. Rejected: it silently retargets expressions whenever the bundle and avatar target orders differ.
- Additive blending of raw tracks in the mixer. Rejected: the mixer's normalised weighted accumulation is not additive across separate writers, and it would still write the viseme and blink channels.
**Rationale:** ARKit-style expression shapes compose additively with visemes; TalkingHead layers mood over speech the same way. The defect was having several writers, not the choice of blending policy. Name keying makes expression data independent of mesh layout.
**Trade-offs:**
- Requires the frame-ordered face owner from D14.
- The allow-list must be maintained when new expression channels appear.
- The extraction assumes morph tracks are keyed in clip time and keeps that alignment.
**Sources:** three.js GLTFLoader weights → `<mesh>.morphTargetInfluences` whole-array track (behaviour to confirm against the loaded clip during implementation); HANDOFF "Morph target injection" (72 targets, injected into the bundle); index.html viseme rAF loop L272–297
**Exploration:** quick, revised under review
**Depends on:** D11, D12, D14
**Status:** revised (round 1, R1-08; round 2, R2-05: name-keyed remapping)

## D7: LLM gesture instructions — generated from the vocabulary, with descriptions, placement and density rules

**Choice:**
- **Generated section.** The gesture instruction is a speech-api `PromptSection` (`GesturePromptSection`) generated from the performance vocabulary (D12). It lists each promptable tag with a one-line description, including allegories (`tightrope-walk` = careful).
- **Rules in the section:**
  - **Placement:** put the tag immediately before the word it accompanies.
  - **Density:** at most one tag per sentence, and none when no gesture fits.
  - **Register:** dramatic or theatrical tags are only for playful or role-play contexts. Their descriptions say so.
  - Mood markup is prompted only when the LLM owns mood (D16).
- **Prompt composition: speech-api `PromptSection` is the canonical speech-prompt extension point.**
  - `DefaultPromptAssembler` gains `PromptSection` composition (CDI `Instance<PromptSection>`, ordered). It is the first production composer of that SPI.
  - Neocortex `promptSections()` renderers stay cognition-internal. `CognitionAvatarAdapter.wrapAssembler` renders them after `base.assemble(...)`.
- **User-reply path.** The cognition-wrapped assembler inherits the gesture section **because it delegates to `base.assemble(...)`**. That delegation is now a recorded contract of `AvatarCognition.wrapAssembler`: wrappers must enrich, not replace, the base prompt. A test asserts that a cognition-wrapped prompt contains the gesture section.
- **Proactive path.** The prompt is built inside neocortex (`ProactiveSpeechSupport`). Getting the gesture section there needs a neocortex API that accepts caller-supplied prompt sections, filed as a peer issue (D17). Until it lands, proactive speech has no gesture instructions: it produces untagged text, which the segmenter handles as a no-cue turn. It is never extended by smuggling instruction text through `channelContext`.
- **Docs.** `docs/guides/consumer-guide.md` L668 (stale `SocialPromptAssembler` entry) is corrected in the same change.
- **Full vocabulary is kept.** Promptability is a per-entry data flag (`promptable`) in the vocabulary file, not a code decision.
- **Validation and stripping** happen in the segmenter (D2 rule 5).
- **Demo budget.** `max_tokens: 80` against the 20-word cap leaves room for one or two tags (about 27 + about 20 tokens). Truncated tails are stripped (D2), not spoken.

**Alternatives:**
- Bare tag list appended to the system prompt, validated against `GESTURE_MAP` (original choice). Rejected:
  - Names alone mislead the model (`yes` plays thumbs-up, `bow` plays a head-nod, `death`/`hit` read literally).
  - `GESTURE_MAP` is JavaScript in `speech-demo` and cannot be the server's validation source.
  - The original choice gave no placement rule, which D4 needs.
- Neocortex prompt renderers as the canonical mechanism. Rejected: it would make gesture prompting depend on cognition being configured, and pull a speech-pipeline concern into the cognition module.
- Curated subset of 15–25 tags. Rejected as a code-level choice: curation is a data flag, and descriptions let the LLM judge register.
- Structured output (tool use or JSON with an `enum` gesture field and an anchor word). Rejected:
  - `speech-ws` streams sentences to TTS as the LLM produces them, which is the library's first-audio-latency property. A JSON reply cannot be spoken until its text field is parsed incrementally, which needs a streaming JSON parser and a provider-specific tool-call stream through the `AgentProvider` abstraction.
  - Inline tags with vocabulary validation give the same guarantee: no invalid gesture reaches the client.
- No prompt instructions (keyword-only). Rejected (see D15).
**Rationale:** The model can only choose well if it knows what each gesture looks like and where to put it. The speech-api SPI is in-repo and app-agnostic, and it already exists for exactly this purpose; it simply had no production composer.
**Trade-offs:**
- About 300–400 prompt tokens with descriptions.
- Proactive speech is gesture-less until the neocortex peer change lands.
**Sources:** PromptSection (speech-api); CognitionAvatarAdapter.wrapAssembler L51–75, evaluateProactive L94–103; consumer-guide L668 (stale); application.properties system prompt; SpeechProducers `max_tokens: 80`
**Exploration:** quick, revised under review
**Depends on:** D2, D12, D16, D17
**Status:** revised (round 1, R1-11, R1-12; round 2, R2-02: composition mechanism corrected; R2-03: proactive path dependency)

## D8: Idle attention — server-owned, opt-in, cognition-driven when available

**Choice:**
- **Single owner: the server session.** No client silence timer.
- **With cognition configured.** Non-verbal attention bids require a neocortex decision variant (e.g. `InnerLifeTick.NonVerbal(tag)`), because drive and motivation evaluation happens inside neocortex. That variant is filed as a peer issue (D17). When it lands, `AvatarCognition.evaluateProactive` widens to return `ProactiveAct` (`Speak(text)` | `Gesture(tag)`), mapped from it.
  - Until then, the cognition configuration makes **no** non-verbal attention bids. Proactive speech continues as today.
  - `SilenceAttentionPolicy` never runs alongside cognition, even if enabled. Cognition presence wins and a warning is logged.
  - The SPI is not widened ahead of a producer. That would recreate the #450 D5 lapse, a declared channel with nothing behind it.
- **Without cognition:** `SilenceAttentionPolicy` in `speech-ws` is **opt-in** via `AvatarConfig` (`attention.enabled`, default `false`; thresholds configurable). The demo enables it. When enabled:
  - Step 1: after 30 s of silence, a subtle bid (`idle.attention` low-intensity clip).
  - Step 2: after 75 s, a stronger bid: a different clip class, e.g. `knock`.
  - Then nothing more until the next activity.
- **Activity that resets the clock:**
  - Any user message (`Start`, `Text`).
  - `PlaybackComplete(turnSeq)` from the client. This is when the user actually finished hearing the turn; server-side duration estimates are wrong under playback rate (0.6–1.4) and queue latency.
  - A throttled `UserActivity(kind)` message for typing and visibility changes.

  Cognition consumes both `PlaybackComplete` and `UserActivity` as engagement signals.
- The client drops non-speech `Gesture` messages while `document.hidden`.

**Alternatives:**
- Client 30 s/60 s timer (original choice). Rejected:
  - It collides with the server's 30 s proactive tick: a knock and proactive speech fire from two uncoordinated owners.
  - Its reset events were undefined.
  - The second step drew from the same pool, so it was escalation in name only.
- Client timer as fallback only when no cognition is configured. Rejected: it keeps two owners depending on configuration, and the client cannot see server-initiated turns.
- Server-estimated end of turn (`TurnComplete` + audio duration, round-1 text). Rejected: playback rate and queue latency make it wrong by up to about 40%.
- Default-on silence policy. Rejected: an avatar that knocks after 30 s is a persona choice, not a library default.
- Widen `AvatarCognition` now with an adapter that only ever returns `Speak`. Rejected: a dead variant with no producer.
**Rationale:** Bidding for attention is social cognition. It belongs with the component that has drives and a user model, or, without cognition, with the component that sees the whole conversation, and only when the application opts in.
**Trade-offs:**
- Two new client→server messages (`PlaybackComplete`, `UserActivity`).
- Cognition configurations get attention gestures only after the neocortex peer change.
**Sources:** SpeechWebSocket.startProactiveTick L160–187; AvatarConfig.proactiveTickIntervalSeconds (default 30); AvatarCognition (speech-api); CognitionAvatarAdapter.evaluateProactive L94–103 → neocortex ProactiveSpeechSupport; index.html `speed` slider (0.6–1.4)
**Exploration:** quick, revised under review
**Depends on:** D3 (`Gesture` message), D5, D10, D17
**Status:** revised (round 1, R1-14; round 2, R2-03: neocortex dependency; R2-10: `PlaybackComplete`, opt-in)

## D9: Consumer forms of a parsed utterance

**Choice:** Each `ParsedSegment` (D2) is rendered per consumer:

| Consumer | Form |
|---|---|
| TTS | clean text |
| Client display (`Segment.text`) | clean text |
| STT vocabulary (`onResponse` → `ConversationVocabulary`, corrector) | clean text |
| Cognition memory (`recordInteraction`) | clean text, passed explicitly rather than read from `history` |
| Conversation history (`ConversationTurn`) | **canonical markup**: only validated cues, re-rendered as `[gesture:tag]` before their anchor word; hallucinated, malformed or truncated tags removed |

**Alternatives:**
- Raw LLM text everywhere (implicit in the original D2). Rejected:
  - It pollutes the STT hint and the corrector (`gesturedoublefacepalm` enters the vocabulary).
  - It stores markup in cognition memory.
- Clean text in history. Rejected: the model's own prior turns are its strongest format exemplar. Removing tags from history teaches the model that its replies have no tags, and adherence drifts over a conversation.
- Raw tagged text in history. Rejected: it reinforces invalid or hallucinated tags.
**Rationale:** History is a few-shot context and should demonstrate the contract exactly. Every other consumer wants language, not performance markup.
**Trade-offs:**
- A few tokens per turn in history. Negligible with one-sentence replies.
- A risk of the model repeating the same gesture, addressed by the D7 prompt ("vary gestures; none when nothing fits").
**Sources:** SpeechSession L175, L246–247, L263–264, L371–372, L411–422; SpeechProducers L289–295; ConversationVocabulary.addFromText
**Exploration:** surfaced in review (R1-03)
**Depends on:** D2
**Status:** captured (round 1)

## D10: Turn framing and serialisation

**Choice:**
- **One active turn per session.** All turn work in a session runs on one per-session `TurnExecutor` (a single-thread, virtual-thread executor): user replies (`handleStop`, `handleText`) and proactive turns. This also makes `history` single-writer.
  - STT streaming (`handleAudio`, partial polling) is **not** on the executor, so audio capture is never blocked.
- **Proactive suppression.** The proactive tick (and `SilenceAttentionPolicy`) skips its evaluation while any of these is true:
  - a turn is queued or in flight;
  - the floor is **listening** (a `Start` without `Stop`, or `UserActivity(typing)` within the last 5 s);
  - a turn has completed without its `PlaybackComplete`.
- **User message during a proactive turn: queue.** The user turn runs immediately after the proactive turn's remaining synthesis. Superseding would need mid-turn TTS cancellation and client audio interruption, and audio interruption is out of scope (D13).
- **`turnSeq`.** Every server-originated turn has one: user reply (streaming or non-streaming) or proactive. Every `Segment` and audio-frame header carries it (D3).
- **`TurnComplete`.** After the last segment the server sends `TurnComplete(turnSeq, segmentCount, origin)`. This is emitted in a `finally` block, so error paths also close the turn (with `Error` before it). A turn with zero segments (LLM returned only markup or nothing) still sends `TurnComplete(…, 0, …)`, so the client leaves the thinking state.
- **TTS-failure contract.** If `synthesise` throws for one sentence:
  - The server still sends that `Segment` with its text, `hasAudio=false`, no visemes and no gesture cues. Cues need an audio clock.
  - It sends a non-fatal `Error` naming the segment, and continues with the next sentence.
  - The client shows the text and schedules nothing for it.
  - The user never loses a sentence's text. Before, `Response` carried it; now it is the segment.

**Alternatives:**
- No serialisation, with pairing fixed by frame headers alone. Rejected: headers make pairing correct, but interleaved turns would still interleave history writes, prompt assembly and the floor state.
- Supersede the proactive turn on user input. Rejected for now: it needs audio interruption (recorded gap, D13).
- A `final` flag on the last `Segment`. Rejected: on the streaming path the server does not know a sentence is last until the stream ends, after that sentence has been emitted.
- Reuse `Response` as the end-of-turn marker. Rejected: its position differs per path and it is removed (D3).
- A separate `TurnStart` message. Rejected for now: `Segment.index == 0` marks the start. The client enters thinking from its own send or stop (D13).
**Rationale:** D5, D8 and D13 all need a reliable end-of-turn boundary, and the protocol must stay correct when the proactive thread and the message thread both want to speak.
**Trade-offs:**
- A user reply can wait behind a proactive turn's remaining synthesis (bounded by one or two sentences of TTS).
- One more message per turn.
**Sources:** SpeechSession handleStop/handleText/handleProactiveSpeech; SpeechWebSocket streaming generator L84–113, proactive tick L160–187
**Exploration:** surfaced in review (R1-06), extended (R2-01)
**Status:** revised (round 2, R2-01: serialisation, suppression rules, TTS-failure contract, `turnSeq`)

## D11: Animation arbitration — layered base and gesture, base ducks under gestures

**Choice:** `BodyAnimationSystem` runs two layers.
- **Base layer.** The looping pool clip. The cycle timer and pool changes (speaking/idle/listening/thinking, see D13) only ever swap the base clip.
- **Gesture layer.** A one-shot clip. When a gesture starts, the base layer's weight ducks to 0 over the crossfade and the gesture fades in. When the gesture finishes, the base fades back in, using whatever base clip is current at that moment.

Arbitration rules:
- A pool change during a gesture swaps the base clip underneath it with no visible effect.
- A new gesture during a gesture replaces it, latest wins, with a crossfade. The D7 density rule makes collisions rare.
- A gesture during a base crossfade wins.
- The cycle timer never touches the gesture layer and is never stopped by gestures. This fixes the build-copy defect where the cycle did not restart after a gesture.

**Alternatives:**
- A single action slot where every `play()` crossfades away from the current action. Rejected: `setSpeaking` pre-empts in-flight gestures. A word-0 tag races `setSpeaking(true)`, and a sentence-end tag is cut off by `setSpeaking(false)`.
- Defer pool changes until the gesture finishes. Rejected in favour of layering: deferral needs a pending-state queue and delays the pool change visibly after long gestures. Ducking gives the same priority with no queue.
**Rationale:** three.js mixes actions by normalised weight. A ducked base plus a full-weight gesture gives strict gesture priority, and the base stays continuous.
**Trade-offs:** Two concurrent actions per frame (negligible). Weight management replaces `crossFadeTo` for the gesture path.
**Sources:** body-animations.mjs (build copy) `play`, `playGesture`, `setSpeaking`, `_playFromActivePool`, `finished` handler
**Exploration:** surfaced in review (R1-07)
**Depends on:** D13
**Status:** captured (round 1)

## D12: Performance vocabulary — the whole taxonomy in one resource behind an SPI

**Choice:**
- **One file:** `speech-demo/src/main/resources/META-INF/resources/animations/avatar-performance.json`. It is both web-served (the client `fetch`es it) and on the classpath (Java loads it). It holds **all** performance data, now:

  | Section | Contents |
  |---|---|
  | `clips` | every clip in `m2m-animations.glb`: `name`, `strokeMs` (D4), optional expression-curve override |
  | `taxonomy` | categories → subcategories → `[{clip, weight}]` (moved from JS `TAXONOMY`) |
  | `pools` | per floor state (D13): `idle`, `listening`, `thinking`, `speaking` → `[{subcategory, weight}]` (moved from `IDLE_POOL_REFS`/`SPEAKING_POOL_REFS`) |
  | `gestures` | `tag`, `description`, `target` (`{clip}` or `{subcategory}`), `promptable` |
  | `moods` | the TalkingHead mood set (`neutral, happy, angry, sad, fear, disgust, love, sleep`) with descriptions and `promptable` (D16) |

- **`body-animations.mjs` holds no data.** It loads this file and is playback logic only. `GESTURE_MAP`, `TAXONOMY`, `KEYWORD_GESTURES` and the pool constants are deleted.
- **SPI:** `speech-api` gains `PerformanceVocabulary` (`gestureTags()`, `isValidGesture(tag)`, `gesturePromptEntries()`, `moods()`, `isValidMood(m)`), so `speech-ws` stays app-agnostic. `speech-demo` supplies the JSON-backed implementation.
- **Derived from it:**
  - The segmenter validates against `PerformanceVocabulary` (D2).
  - `GesturePromptSection` renders the prompt entries (D7).
  - The client builds every runtime structure from the fetched file.
- **Build-time test** asserts that:
  - every `clips` entry exists in `m2m-animations.glb`, and every GLB clip has a `clips` entry;
  - every taxonomy and gesture reference resolves (clip or subcategory, transitively to clips);
  - every pool references existing subcategories;
  - the expression channel names used by clip curves exist in `avatar.glb` (D6).

  This catches the stale-clip class of bug (`thinker`, `shocked-recoil`, etc.) for subcategory targets as well.
**Alternatives:**
- JS `GESTURE_MAP` plus a Java validation set plus prompt text (implicit in the original D7). Rejected: three hand-synced copies, and the build copy already shows drift.
- Tags in JSON, taxonomy staying in JS with migration "over time" (round-1 text). Rejected: two sources, a build test that cannot validate subcategory targets, and no home for `strokeMs`. Self-contradictory against the "exactly one source" settlement.
- Vocabulary hard-coded in `speech-ws`. Rejected: `speech-ws` is a reusable library, and the vocabulary is the demo's asset.
**Rationale:** Performance data crosses the module boundary (demo assets → library validation → LLM prompt → client resolution). It needs exactly one source, and "one source" has to mean all of it.
**Trade-offs:** The client waits for an extra fetch before body animations are enabled. The file is renamed and broadened from `gestures.json` (round 1), and its SPI from `GestureVocabulary`.
**Sources:** body-animations.mjs GESTURE_MAP / TAXONOMY / pool refs (build copy); HANDOFF "Deleted" list; built index.html L199 (TalkingHead mood names); speech-api module charter (CLAUDE.md)
**Exploration:** surfaced in review (R1-11)
**Status:** revised (round 2, R2-07: whole taxonomy moved now; R2-04: moods added; R2-05: avatar name-set test)

## D13: Conversational floor states

**Choice:** The client keeps an explicit floor state machine. Each state selects a base-layer pool (D11) from `avatar-performance.json`.

| State | Entered on | Pool |
|---|---|---|
| idle | `TurnComplete` + all audio played (D5), or start | `idle` |
| listening | mic `start`, or `UserActivity(typing)` | `listening` (`idle-listening`, …) |
| thinking | user `stop` / `text` sent, until first segment plays | `thinking`: neutral attentive stances (`idle-subtle`, `hands-clasped-front`, `idle-listening`). Uncertainty clips (`confused`, `shrug`) are **excluded**. |
| speaking | first audio-bearing segment of a turn starts playing | `speaking` |

- **Proactive turns** go idle → speaking. The server suppresses proactive turns during listening, thinking and unacknowledged playback (D10), so a proactive turn should not arrive in those states. If one does (a race at the boundary), the client plays it and enters speaking. The server has already arbitrated.
- **Barge-in** (the user starts the mic during speaking) moves to listening. Audio keeps playing; it is not interrupted. Audio interruption is out of scope and recorded as a known gap.
- The current one-shot `playBodyGesture('thinking')` on send is replaced by the thinking state. It also targeted the deleted `thinker` clip.
- Dedicated thinking clips (e.g. a replacement hand-to-chin) are a content follow-up. Adding them is a data change to the `thinking` pool.
**Alternatives:**
- Speaking/idle only (implicit in the original D5). Rejected: the 1–3 s latency window from STT through cleanup, LLM and first-sentence TTS is where an avatar most needs a "working on it" signal. #450's design named "thinking while the LLM generates" as a goal.
- A thinking pool adjacent to `conversational.uncertainty` (round-1 text). Rejected: `confused` and `shrug` mean "I don't know" and would play before every answer.
**Rationale:** The client already observes every transition: its own `start`/`stop`/`text`, the first segment's playback, and `TurnComplete`. Recording the states makes pool selection one lookup instead of scattered calls.
**Trade-offs:** Listening and thinking share clips with idle until dedicated content exists.
**Sources:** 2026-11-11-mixamo-avatar-animation-design.md L10; built index.html L328 (`playBodyGesture('thinking')`); body-animations.mjs build copy (`idle-subtle`, `idle-listening`, `hands-clasped-front` present; `thinker` stale)
**Exploration:** surfaced in review (R1-15)
**Depends on:** D10, D11, D12
**Status:** revised (round 2, R2-09: neutral thinking pool; R2-01: proactive-turn rule)

## D14: Per-frame channel ownership and playback host

**Choice:** Channel ownership is explicit:

| Channel | Owner |
|---|---|
| Head, neck and eye bones | TalkingHead (already masked from body clips) |
| All other skeleton bones | `BodyAnimationSystem` mixer |
| Face morphs | one face compositor (D6) |
| Audio clock | the playback host |

The demo's separate `tickBody` rAF loop is removed. Two independent rAF callbacks make the final pose depend on callback order and render timing.

**Frame ordering uses public three.js render hooks.** This works whatever the spike finds.
- **Bones.** `scene.onBeforeRender` runs after `scene.updateMatrixWorld()` and before skeleton upload. It runs `mixer.update(dt)` there, guarded so it runs once per `renderer.info.render.frame`, then calls `armature.updateMatrixWorld(true)`.
- **Face.** `onBeforeRender` on the head mesh runs after TalkingHead has written its morphs and before morph influences are uploaded. The face compositor runs there.
- These are to be verified on `three@0.180.0` during implementation. TalkingHead internals are never needed.

**Playback host, gated by a verification spike:** adopt TalkingHead 1.8's `speakAudio` as the host if all of the following hold:
- (a) it accepts our viseme timeline and words/markers;
- (b) markers fire on the audio clock;
- (c) its per-frame body-pose system can be prevented from writing non-head bones while the external mixer owns them.

If all three hold, the custom audio queue and viseme rAF loop are deleted, and gesture cues become `markers` at `offsetMs / rate − stroke`. If any fails, keep the custom host. The custom host implements D3 (header-based pairing), D5 and D10, and the render hooks above provide ordering. The spike result is recorded here before implementation planning.
**Alternatives:**
- Leave ownership implicit, with three uncoordinated writers (status quo). Rejected: the result depends on update order, not on policy.
- Adopt `speakAudio` unconditionally. Not taken without verification: the v1.8 API (marker semantics, pose-system interaction) was not confirmed.
- Depend on a TalkingHead frame hook (round-1 point (d)). Rejected: three.js public hooks give deterministic ordering without coupling to TalkingHead internals.
**Rationale:** Settling ownership first is what makes D5, D6 and D11 implementable. Render hooks make the ownership table enforceable today, so the spike only decides the audio host. TalkingHead's own timeline would remove custom queue code and fits the IDEAS.md goal of keeping #457 contribution-friendly upstream, but only if it really yields the bones.
**Trade-offs:** A spike before planning. `scene.onBeforeRender` is a single slot: the demo must not let other code overwrite it (wrap and chain if needed).
**Sources:** built index.html L603–652 (TalkingHead 1.8, separate mixer, separate rAF); index.html L248–309 (custom queue and viseme loop); three.js `Object3D.onBeforeRender` / `Scene.onBeforeRender`; IDEAS.md (upstream contribution)
**Exploration:** surfaced in review (R1-16)
**Depends on:** D6, D11
**Status:** revised (round 2, R2-06: render-hook ordering replaces spike point (d))

## D15: Keyword fallback — dropped

**Choice:** No keyword fallback for gestures or mood. The base speaking pool provides continuous conversational motion when the LLM chooses no gesture. Choosing no gesture is now a valid decision under the D7 density rule ("none when no gesture fits"). The client-side `classifyResponse`, `KEYWORD_GESTURES` and `KEYWORD_MOODS` are deleted. The server logs tag adherence per turn: tags present, valid and dropped. A model that ignores the instructions is therefore visible in logs, and a server-side whole-word fallback can be revisited with data.
**Alternatives:**
- Client-side substring fallback on `Response` (HANDOFF scope item 3). Rejected:
  - It inherits the streaming-path lateness (D2).
  - Substring matching misfires: `"hi "` matches `"sushi "`, `"yes"` matches `"eyes"`, `"love"` matches `"I'd love to help"`.
- Server-side per-segment whole-word fallback through the cue path. Rejected for now: it overrides the LLM's deliberate choice of no gesture, which is the point of D7. It may be reintroduced, data-driven, if adherence logs show a model systematically emits no tags.
**Rationale:** With a promptable vocabulary on the reply path, the fallback fills a gap that no longer exists, and does so with low precision.
**Trade-offs:** Removes listed #457 scope (HANDOFF "What needs building" item 3), pending human confirmation (D17). With models that ignore instructions, gestures are rarer until the fallback is reconsidered.
**Sources:** body-animations.mjs KEYWORD_GESTURES / classifyResponse (build copy); built index.html KEYWORD_MOODS / detectMood
**Exploration:** surfaced in review (R1-17)
**Depends on:** D7
**Status:** captured (round 1), scope removal awaiting human confirmation

## D16: Mood — one owner per configuration, persistent baseline vs turn-scoped override

**Choice:** Displayed mood has exactly one owner, decided by configuration.
- **Cognition supplies affect.** `AvatarCognition` exposes the current affect (`currentMood(agentId, tenantId, subjectId)`, mapped to the TalkingHead mood set).
  - The server sends `Mood(state)` on change. Mood is checked after each proactive tick and after each `recordInteraction`.
  - The LLM is **not** prompted for mood. Any `[mood:…]` it emits is stripped, and no `MoodCue` is sent.
  - The LLM still sees cognition's mood through the neocortex prompt renderers, so its wording and the face agree.
  - If the neocortex adapter can read mood state through an existing accessor, this is in-repo. Otherwise the accessor is part of the neocortex peer issue (D17). Until then, the next rule applies, with a single owner either way.
- **No cognition affect available.** The LLM owns mood:
  - Moods are prompted from the vocabulary's `moods` section (D12) with descriptions, and validated against it.
  - Each `[mood:x]` becomes a **turn-scoped** `MoodCue(x, offsetMs)`. It applies at its offset and lasts until the turn's playback completes plus a hold (default 3 s). The face then returns to the session baseline (`neutral`).
  - A mood never persists across turns implicitly.
  - At most one mood cue per turn (prompt rule; extra cues dropped).
- **Client.** Displayed mood = the active turn-scoped override, if any, otherwise the baseline. The baseline comes from the last `Mood(state)` or is `neutral`. The client applies it through TalkingHead `setMood`, which is part of layer 1 (base) of the D6 face compositor.

**Alternatives:**
- An LLM `MoodCue` with no lifecycle (round-1 text). Rejected: one `[mood:sad]` would persist for the rest of the session, and it competes with cognition's mood model. That is two owners of one channel, the class of defect fixed for attention in D8.
- No mood at all without cognition. Rejected: TalkingHead mood is a cheap, visible affect channel, and with a turn scope and a vocabulary the LLM is a reasonable owner when nothing better exists.
- Reclassify LLM mood as a transient expression cue through the D6 compositor. Rejected: TalkingHead mood also drives idle face behaviour (eyes, blink cadence) beyond blendshape offsets. Expression curves cover gesture-local affect; mood is the turn or session baseline.
**Rationale:** A persistent state needs one owner and a lifecycle. Cognition is the natural owner when it computes affect. Otherwise the LLM owns it per turn, with explicit decay.
**Trade-offs:**
- A `Mood` message and a mood accessor on `AvatarCognition`.
- The turn-scoped hold is a tunable heuristic.
**Sources:** agency-core CircuitBreakerMoodMapper → neocortex MoodSignal; built index.html L199–221 (TalkingHead moods, detectMood); TalkingHead `setMood`
**Exploration:** surfaced in review (R2-04)
**Depends on:** D2, D3, D12, D17
**Status:** captured (round 2)

## D17: Cross-repo sequencing

**Choice:** #457 implementation is sequenced as follows:
1. **#458 lands via `work-end`** (D1).
2. **Connectors peer issue lands first.** Mark timing in `casehub-connectors-voice-spi`: `TextMark`/`MarkTiming`, `MarkTimingEnricher`, and the VITS guarded-exact implementation (D4). #457 **blocks** on it. It is small and well-bounded, and the alternative is an interim timing shim in the wrong layer.
3. **#457 ships everything else in-repo:**
   - segmenter, consumer forms and turn serialisation (D2, D9, D10);
   - protocol (D3);
   - client floor, layers and face compositor (D5, D11, D13, D6, D14);
   - performance vocabulary and SPI (D12);
   - `DefaultPromptAssembler` section composition and the gesture section (D7);
   - opt-in `SilenceAttentionPolicy` (D8);
   - LLM-owned turn-scoped mood (D16);
   - adherence logging (D15).
4. **Neocortex peer issue lands behind #457** (non-blocking). It adds:
   - caller-supplied prompt sections for proactive speech (D7);
   - a non-verbal proactive outcome (D8);
   - a mood accessor if one does not already exist (D16).

   The agency follow-up then widens `AvatarCognition` (`ProactiveAct`, `currentMood`) when each producer exists.

   **Interim behaviour (cognition configured):**
   - proactive speech without gestures;
   - no non-verbal attention bids;
   - LLM-owned turn-scoped mood.
**Alternatives:**
- Block #457 on neocortex too. Rejected: neocortex's changes need cognition-design work (inner-life decision variants). The interim behaviour is degraded but correct, not a workaround: absent capabilities, not fake ones.
- Interim connectors shim inside `speech-ws`. Rejected (D4).
- Do everything in one cross-repo branch set. Rejected: per-issue landing (D1 rationale).
**Rationale:** The dependency direction is fixed by the code. Mark timing is an SPI that #457 consumes. The proactive and mood changes need decisions made inside neocortex, which agency must not fake.
**Trade-offs:** #457 starts only after the connectors change lands. Cognition configurations reach full parity only after the neocortex follow-up.
**Sources:** casehub-connectors-voice-spi (SynthesisOptions/Result); CognitionAvatarAdapter → neocortex ProactiveSpeechSupport; round-1 human decision (`skip`)
**Exploration:** surfaced in review (R2-03)
**Status:** captured (round 2), awaiting human confirmation together with D15's scope removal

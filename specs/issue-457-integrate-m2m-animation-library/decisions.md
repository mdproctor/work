# Decisions — #457 Integrate M2M Animation Library

## Verification notes (decision review round 1)

Facts checked against code during the round-1 decision review. Later decisions rely on them.

- **Streaming path ordering.** `SpeechSession.handleStop` (L239–245) and `handleText` (L360–370) send `Response` only after `streamingGenerator.generate(...)` returns. By then every sentence has been synthesised and its `Phonemes` and audio sent. The non-streaming path (L261–271) and `handleProactiveSpeech` (L175–176) send `Response` before any audio. `Response` therefore has no consistent position relative to audio.
- **Word boundaries survive inside VITS.** `EspeakLibrary.textToPhonemes` joins espeak clauses with `' '`, and espeak IPA output separates words with spaces. `VitsConfig.tokenize` keeps every `phoneme_id_map` key except `^ $ _`. `VitsTextToSpeech.extractPhonemeTimings` emits a `PhonemeTiming(" ", start, end)` for each space token where `endMs > startMs`. Caveats:
  - Space tokens that round to a 0 ms duration are dropped.
  - espeak word count ≠ text word count when espeak expands numbers or abbreviations.
- **The TTS SPI is cross-repo.** `SynthesisOptions(voice, language, audioFormat, includePhonemes)`, `SynthesisResult(audioData, audioFormat, phonemes)` and `LipSyncEnricher` live in `casehub-connectors-voice-spi`.
- **Tag pollution in STT vocabulary is real.** `ConversationVocabulary.addFromText` strips characters outside `[a-z']`. `[gesture:doublefacepalm]` therefore becomes the term `gesturedoublefacepalm` and enters the Whisper hint and the corrector dictionary.
- **`PromptSection` is only composed when cognition is present.** `SocialPromptAssembler` composes sections; `DefaultPromptAssembler` does not. Without cognition, no `PromptSection` reaches the LLM.
- **Demo client baseline.** The demo client imports **TalkingHead 1.8** (not 1.7). It drives the body mixer from its own `requestAnimationFrame` loop (`tickBody`), separate from TalkingHead's loop. The #450-era build already has a client `case 'animation'` handler, but main's `AvatarMessage` has no `Animation` record. #450 D5 was therefore never completed server-side.
- **Not yet verified (#458 code).** `BodyAnimationSystem` was only reviewed from the build copy (`target/classes`, 16 Nov). Two known problems there:
  - `GESTURE_MAP` still references clips deleted in #458 (`thinker`, `shocked-recoil`, `hands-interlocked-chest`, `hit-chest`).
  - The `finished` handler does not restart the cycle timer.

  Neither has been checked against the #458 tip.

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
1. **Tags are parsed before sentence segmentation.** The segmenter runs over the streamed buffer. It holds one token of lookahead after each sentence boundary, so that a tag-only remainder can be bound before the sentence is emitted. The latency cost is one LLM delta.
2. **Forward binding.** A tag binds to the next spoken word. This matches the D7 prompt rule ("place the tag immediately before the word it accompanies").
3. **Trailing tags.** A tag with no following word in its sentence (`"Nice to meet you! [gesture:greeting]"`) binds to the **first word of that sentence**, which treats the whole sentence as the affiliate. It is never emitted as its own segment.
4. **No empty segments.** A segment whose clean text is empty is never sent to TTS. Its cues merge into the previous segment, or into the next one if there is no previous segment.
5. **Malformed or truncated markup is stripped.** Anything matching `\[(gesture|mood):[^\]]*\]?$` at end of stream, or an unclosed `[gesture:` prefix, is removed before TTS. Unknown tags are validated against the vocabulary (D12) and dropped.
6. **Mood markup** is parsed by the same component and becomes a `Mood` cue. The client's `detectMood(msg.text)` is removed.

The client's `parseGestureTags` is deleted from `index.html` and from `body-animations.mjs`. No non-server consumer exists. A future TalkingHead upstream contribution would get its own API designed for that context, not a leftover from this one.
**Alternatives:**
- Server strips before TTS while `Response` keeps the tagged text (original choice). Rejected: the premise was false. On the streaming path `Response` arrives after all audio has been sent, so client-parsed gestures fire late. Combined with D3's server-delivered cues, every gesture would fire twice. The chat bubble would also show raw markup.
- Client-only parsing. Rejected: TTS would speak the tags.
- Per-path parsing (separate code in each of the three paths). Rejected: the paths already diverge (different splitters, different `Response` ordering). That divergence is the bug class here.
**Rationale:** One parser, one segmentation, one cue representation. Every consumer gets a deliberately chosen form (D9). The server already owns sentence segmentation, so it should own the markup that segmentation has to respect.
**Trade-offs:** One-token lookahead per sentence boundary. Forward binding misreads tags the LLM placed after their affiliate (`"Hello! [gesture:greeting] Let me explain."` binds to "Let"). The D7 placement rule is the contract that makes forward binding correct.
**Sources:** SpeechSession L174–183, L239–247, L261–271, L360–372; SpeechWebSocket `findSentenceBoundary` L219–227; index.html L151–152; SSML `<mark>` semantics (Azure, Google, Polly)
**Exploration:** deep-analysis, revised under review with new evidence (streaming-path ordering)
**Status:** revised (round 1, R1-02, R1-13)

## D3: Protocol — segments carry text, visemes and cues; separate channel for non-speech gestures

**Choice:** Replace `AvatarMessage.Phonemes` and `AvatarMessage.Response` with turn-framed segment messages. Add a non-speech gesture message.

```java
record Segment(String turnId, int index, String text,
               List<VisemeFrame> visemes, List<Cue> cues) implements AvatarMessage {}
record TurnComplete(String turnId, int segmentCount, TurnOrigin origin) implements AvatarMessage {}   // D10
record Gesture(String tag, GestureOrigin origin) implements AvatarMessage {}                          // non-speech

sealed interface Cue {
    record GestureCue(String tag, long offsetMs) implements Cue {}
    record MoodCue(String mood, long offsetMs) implements Cue {}
}
```

- A `Segment` precedes its audio frame, as `Phonemes` does today. The client pairs every `Segment` with the next binary frame **whether or not `visemes` is empty**. Today's `msg.data.length > 0` gate drops cues for engines without phonemes, so it is removed.
- `cues` is a list. Multiple gestures per sentence are allowed, and the density rule (D7) limits them.
- `text` is clean display text. The client builds the chat bubble from segments as they arrive. `Response` is removed: its content is the concatenation of the segment texts, and its ordering was inconsistent across paths.
- `Gesture` completes #450 D5, whose server-side record was never added to `AvatarMessage`. It carries only a vocabulary tag; #450's raw `url`/`durationSec` form is superseded. It is used for gestures that are not tied to speech: cognition-driven non-verbal acts and attention bids (D8).

**Alternatives:**
- Optional `gesture` + `wordIndex`/`wordCount` on `Phonemes` (original choice). Rejected:
  - A scalar field drops every tag after the first.
  - Delivery depended on visemes being non-empty.
  - The message name no longer described its content.
- Separate `Animation` message per sentence. Rejected for speech-timed cues: it needs ordering coupling with the audio frame. It is kept, as `Gesture`, for cues that are not timed to speech.
- Batch gesture map in `Response`. Rejected: `Response` has inconsistent timing across paths (D2).
**Rationale:** Speech-timed cues belong to the segment they are timed against. Non-speech gestures need their own channel, which #450 already decided. The decision lapsed only because the server side never landed.
**Trade-offs:** Breaking protocol change: `Phonemes` and `Response` are gone. This is deliberate: every client and test that consumes them must be updated explicitly.
**Sources:** AvatarMessage.java (main); #450 decisions D1, D5; built index.html `case 'animation'`; index.html L154–160, L229–233; blog 2026-11-13 L76 (multiple tags per response)
**Exploration:** deep-analysis, revised under review with new evidence (#450 D5 lapse, viseme-gated delivery)
**Depends on:** D2, D10
**Status:** revised (round 1, R1-09, R1-10)

## D4: Gesture timing — the server owns speech time, the client owns animation time

**Choice:**
- **Speech time (server).** Each cue carries `offsetMs`, the onset of its anchor word within the segment's audio, in **buffer time** (unscaled). The value comes from the TTS SPI as SSML-style mark timing:
  - `SynthesisOptions` gains `List<TextMark(String id, int charOffset)> marks`.
  - `SynthesisResult` gains `List<MarkTiming(String id, long offsetMs)> marks`.
- **Engines resolve marks with the best evidence available.** This is a cross-repo change in `casehub-connectors-voice-spi`, filed against connectors:
  - **VITS/Piper: exact.** Each mark's text prefix is phonemised with espeak and its IPA words are counted. That count indexes the space-token boundaries in the model's duration output, which handles number and abbreviation expansion. If the space token was dropped (0 ms duration), fall back to the next tier.
  - **Phoneme-only engines: phoneme-proportional.** Engines with phoneme timings but no word boundaries (`LipSyncEnricher`/`EspeakPhonemeAligner`) use phoneme-proportional timing over the aligned phonemes.
  - **No phonemes: character-proportional** over the WAV duration.
  - A `MarkTimingEnricher` decorator, analogous to `LipSyncEnricher`, provides the two fallback tiers for any engine that returns no marks.
- **Animation time (client).** The client schedules each gesture as `trigger = max(0, offsetMs / rate − strokeMs(clip) − crossfade / 2)`:
  - `rate` is the playback rate. The viseme loop already uses buffer-time × rate.
  - `strokeMs` is per-clip metadata from the vocabulary (D12). The client computes it because it resolves subcategory picks, so only the client knows the concrete clip.
  - Triggers clamped to 0 are logged in debug mode, so clips with long preparation phases can be found.
- **Stroke metadata.** Initial `strokeMs` values come from an offline analysis script: peak wrist displacement or velocity relative to the clip's first frame. Values can be overridden by hand in the vocabulary file.

**Alternatives:**
- Word-index proportional: `wordIndex/wordCount × duration` on the client (original choice). Rejected for two reasons:
  - The client has no word information, because visemes merge word boundaries away. Any accuracy upgrade would have needed a protocol change, contradicting the original rationale.
  - The server already has exact word onsets for the default engine.
- Viseme-boundary mapping on the client. Rejected for the same reason: no word boundaries reach the client.
- Read space tokens directly in `speech-ws` from `synthesis.phonemes()`. Rejected: it leaks a VITS-specific detail (space phoneme = word boundary) into the engine-agnostic speech layer. Mark timing is the standard TTS abstraction (Azure `BookmarkReached`, Google timepoints, Polly speech marks).
- Sentence-start only. Rejected: too coarse.
**Rationale:** The original rationale misapplied the McNeill/Kendon synchrony rule, which concerns the gesture **stroke**:
- The stroke precedes or coincides with its lexical affiliate.
- The M2M clips include a preparation phase.
- A crossfade adds latency rather than absorbing error.
Both effects push the stroke late, so the error is biased, not symmetric. Correct synchrony needs speech time (server-side knowledge) and stroke time (clip knowledge), each owned by the party that has it. The original claim that "LLM tags cluster at phrase boundaries" had no evidence and is withdrawn.
**Trade-offs:**
- SPI change in a peer repo (connectors).
- Per-clip `strokeMs` metadata to maintain, mitigated by automatic derivation.
- Strokes that should precede segment start are clamped. The first segment of a turn cannot lead its own audio.
**Sources:** VitsConfig.tokenize L94–127; VitsTextToSpeech.extractPhonemeTimings L135–171; EspeakLibrary.textToPhonemes L81–124; casehub-connectors-voice-spi SynthesisOptions/SynthesisResult/LipSyncEnricher; index.html L257–274 (rate handling); McNeill 1992 / Kendon 2004 (stroke–affiliate synchrony)
**Exploration:** deep-analysis, revised under review with new evidence (VITS word boundaries, stroke vs onset)
**Depends on:** D3, D12
**Status:** revised (round 1, R1-04, R1-05)

## D5: Speaking-state activation — follows the turn, not queue occupancy

**Choice:** The client enters **speaking** when the first segment of a turn starts playing. It leaves speaking only when both of these are true:
1. `TurnComplete(turnId)` has been received (D10).
2. The play queue is empty and the last segment's audio has ended.

Mid-turn queue drains, caused by slow synthesis of sentence N+1, do not change state. A 10 s safety timeout after the last audio ends forces idle if `TurnComplete` never arrives (dropped connection). The leave transition also respects D11: an in-flight gesture finishes before the base layer changes pool.
**Alternatives:**
- Audio playback start/end of the queue (original choice). Rejected: `playNextQueued` sets `isPlaying = false` whenever the queue drains between sentences. With Kokoro, Audio8 or CosyVoice3, or with multi-sentence proactive speech, the pose flickers idle → speaking every sentence.
- `Response` arrival. Rejected: it arrives early on the non-streaming and proactive paths and late on the streaming path (D2), and `Response` is removed anyway (D3).
- Queue drain plus hysteresis only. Rejected: a fixed hysteresis guesses at synthesis latency. An explicit end-of-turn signal makes the guess unnecessary.
**Rationale:** Speaking is a property of the turn. Only the server knows when a turn has no more segments, so it must say so.
**Trade-offs:** Depends on the server always sending `TurnComplete`, including on error paths (enforced in a `finally` block, see D10).
**Sources:** index.html `playNextQueued` L248–249, L299–307; BodyAnimationSystem.setSpeaking
**Exploration:** quick, revised under review
**Depends on:** D10, D11, D13
**Status:** revised (round 1, R1-06)

## D6: Morph target conflicts — strip at load, re-apply as additive expression curves through one face owner

**Choice:**
- **At load**, `BodyAnimationSystem` removes all `*.morphTargetInfluences` tracks from every clip, exactly as it already removes head-bone tracks. GLTFLoader emits glTF `weights` channels as a whole-array track, so leaving them in writes all 72 influences: blink, eye look, mood and every `viseme_*`.
- **Expression curves.** Before discarding a morph track, the loader extracts a sparse **expression curve**: the per-target keyframes for an allow-list of expression channels (`brow*`, `mouthFrown*`, `mouthSmile*`, `mouthPress*`, `cheek*`, `noseSneer*`, `mouthPucker`). `viseme_*`, `eyeBlink*`, `eyeLook*` and `jaw*` are excluded. Authoring stays in the GLB, where #458 put it, so there is no hand-maintained second copy.
- **One face owner (D14)** composes the face each frame, in order, before render:
  1. Base: TalkingHead's blink, mood and eyes.
  2. Visemes: replace the `viseme_*` channels.
  3. Gesture expression curves: added to the expression channels and clamped to [0, 1].
  4. The gesture expression weight follows the gesture layer's weight (D11), so it fades with the gesture.
- The shrug frown therefore also shows **during speech**, which is when the gesture means most (`shrug` sits in `conversational.uncertainty`, which is in the speaking pool and is a tag target).

**Alternatives:**
- Conditional pass-through: body morphs only when not speaking (original choice). Rejected:
  - The whole-array track clobbers blink, mood and visemes.
  - It suppresses the frown exactly when the gesture is semantically relevant.
  - It produces a pop at speech boundaries.
- Strip all with no re-application. Rejected: it loses the authored expression.
- Hand-authored expression metadata in the vocabulary (reviewer's suggestion). Rejected in favour of automatic extraction: same runtime model, but a single source of authoring truth (the GLB).
- Additive blending of raw tracks in the mixer. Rejected: the mixer's normalised weighted accumulation is not additive across separate writers, and it would still write the viseme and blink channels.
**Rationale:** ARKit-style expression shapes compose additively with visemes; TalkingHead layers mood over speech the same way. The defect was having several writers, not the choice of blending policy.
**Trade-offs:**
- Requires the frame-ordered face owner from D14.
- The allow-list must be maintained when new expression channels appear.
- The extraction assumes morph tracks are keyed in clip time and keeps that alignment.
**Sources:** three.js GLTFLoader weights → `<mesh>.morphTargetInfluences` whole-array track (behaviour to confirm against the loaded clip during implementation); HANDOFF "Morph target injection" (72 targets); index.html viseme rAF loop L272–297
**Exploration:** quick, revised under review
**Depends on:** D11, D14
**Status:** revised (round 1, R1-08)

## D7: LLM gesture instructions — generated from the vocabulary, with descriptions, placement and density rules, on every LLM path

**Choice:**
- **Generated section.** The gesture instruction is a `PromptSection` (`GesturePromptSection`) generated from the vocabulary (D12). It lists each promptable tag with a one-line description, including allegories (`tightrope-walk` = careful).
- **Rules in the section:**
  - **Placement:** put the tag immediately before the word it accompanies.
  - **Density:** at most one tag per sentence, and none when no gesture fits.
  - **Register:** dramatic or theatrical tags are only for playful or role-play contexts. Their descriptions say so.
- **Every LLM path gets the section:**
  - `DefaultPromptAssembler` gains `PromptSection` composition. Today only `SocialPromptAssembler` composes sections, so without cognition the instruction would never reach the LLM.
  - The cognition-wrapped assembler gets it through the same sections.
  - `AvatarCognition.evaluateProactive` receives the same sections, so proactive speech gestures with the same vocabulary.
- **Full vocabulary is kept.** Promptability is a per-entry data flag (`promptable`) in the vocabulary file, not a code decision.
- **Validation and stripping** happen in the segmenter (D2 rule 5).
- **Demo budget.** `max_tokens: 80` against the 20-word cap leaves room for one or two tags (about 27 + about 20 tokens). Truncated tails are stripped (D2), not spoken.

**Alternatives:**
- Bare tag list appended to the system prompt, validated against `GESTURE_MAP` (original choice). Rejected:
  - Names alone mislead the model (`yes` plays thumbs-up, `bow` plays a head-nod, `death`/`hit` read literally).
  - `GESTURE_MAP` is JavaScript in `speech-demo` and cannot be the server's validation source.
  - The original choice gave no placement rule, which D4 needs.
- Curated subset of 15–25 tags. Rejected as a code-level choice: curation is now a data flag, and descriptions let the LLM judge register. This keeps the original rationale that the LLM judges appropriateness better than a static filter, now that it can see what each tag does.
- Structured output (tool use or JSON with an `enum` gesture field and an anchor word). Rejected:
  - `speech-ws` streams sentences to TTS as the LLM produces them, which is the library's first-audio-latency property. A JSON reply cannot be spoken until its text field is parsed incrementally, which needs a streaming JSON parser and a provider-specific tool-call stream through the `AgentProvider` abstraction.
  - Inline tags with vocabulary validation give the same guarantee: no invalid gesture reaches the client.
- No prompt instructions (keyword-only). Rejected (see D15).
**Rationale:** The model can only choose well if it knows what each gesture looks like and where to put it. All LLM paths must see the same contract, or proactive speech becomes a second, unvalidated dialect.
**Trade-offs:** About 300–400 prompt tokens with descriptions, and a `PromptSection` composition change in `DefaultPromptAssembler`.
**Sources:** PromptSection (speech-api); consumer-guide (SocialPromptAssembler composes sections); application.properties system prompt; SpeechProducers `max_tokens: 80`; GESTURE_MAP (build copy)
**Exploration:** quick, revised under review
**Depends on:** D2, D12
**Status:** revised (round 1, R1-11, R1-12)

## D8: Idle attention — server-owned, cognition-driven when present, escalating for real

**Choice:**
- **Single owner: the server session.** No client silence timer.
- **With cognition configured:** `AvatarCognition.evaluateProactive` is widened to return a `ProactiveAct` (`Speak(text)` | `Gesture(tag)`). This is a breaking SPI change, in-repo: speech-api plus `CognitionAvatarAdapter`. Attention bids come from the drive and user model on the existing proactive tick. The silence policy below does not run.
- **Without cognition:** a `SilenceAttentionPolicy` in `speech-ws` uses the session's activity clock.
  - Step 1: after 30 s of silence, a subtle bid (`idle.attention` low-intensity clip).
  - Step 2: after 75 s, a stronger bid: a different clip class, e.g. `knock`.
  - Then nothing more until the next activity.
- **Activity that resets the clock:**
  - Any user message (`Start`, `Text`).
  - `TurnComplete` plus that turn's audio duration (the server knows every segment's WAV length).
  - A new throttled client→server `UserActivity(kind)` message for typing and visibility changes. Cognition also consumes it as an engagement signal.
- The client drops non-speech `Gesture` messages while `document.hidden`.

**Alternatives:**
- Client 30 s/60 s timer (original choice). Rejected:
  - It collides with the server's 30 s proactive tick: a knock and proactive speech fire from two uncoordinated owners.
  - Its reset events were undefined.
  - The second step drew from the same pool, so it was escalation in name only.
- Client timer as fallback only when no cognition is configured (reviewer's suggestion). Rejected: it keeps two owners depending on configuration, and the client cannot see server-initiated turns. The server already has every activity signal except typing and visibility, and `UserActivity` adds those.
- Skip for now. Rejected: it is listed #457 scope and the design is now clear.
**Rationale:** Bidding for attention is social cognition. It belongs with the component that has drives and a user model, or, without cognition, with the component that sees the whole conversation.
**Trade-offs:**
- Breaking `AvatarCognition` SPI change.
- A new client→server message.
- Thresholds remain tunable configuration (`AvatarConfig`).
**Sources:** SpeechWebSocket.startProactiveTick L160–187; AvatarConfig.proactiveTickIntervalSeconds (default 30); AvatarCognition (speech-api); CognitionAvatarAdapter (agency-core)
**Exploration:** quick, revised under review
**Depends on:** D3 (`Gesture` message), D10
**Status:** revised (round 1, R1-14)

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

## D10: Turn framing — explicit TurnComplete on every server path

**Choice:**
- Every server-originated turn has a `turnId`: user reply (streaming or non-streaming) or proactive. Every `Segment` carries it.
- After the last segment the server sends `TurnComplete(turnId, segmentCount, origin)`. This is emitted in a `finally` block, so error paths also close the turn (with `Error` before it).
- A turn with zero segments (LLM returned only markup or nothing) still sends `TurnComplete(…, 0, …)`, so the client leaves the thinking state.
**Alternatives:**
- A `final` flag on the last `Segment`. Rejected: on the streaming path the server does not know a sentence is last until the stream ends, after that sentence has been emitted.
- Reuse `Response` as the end-of-turn marker. Rejected: its position differs per path and it is removed (D3).
- A separate `TurnStart` message. Rejected for now: `Segment.index == 0` marks the start. The client enters thinking from its own send or stop (D13).
**Rationale:** D5, D8 and D13 all need a reliable end-of-turn boundary. Only the server has it.
**Trade-offs:** One more message per turn.
**Sources:** SpeechSession handleStop/handleText/handleProactiveSpeech; SpeechWebSocket streaming generator L84–113
**Exploration:** surfaced in review (R1-06)
**Status:** captured (round 1)

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
- Defer pool changes until the gesture finishes (reviewer's suggestion). Rejected in favour of layering: deferral needs a pending-state queue and delays the pool change visibly after long gestures. Ducking gives the same priority with no queue.
**Rationale:** three.js mixes actions by normalised weight. A ducked base plus a full-weight gesture gives strict gesture priority, and the base stays continuous.
**Trade-offs:** Two concurrent actions per frame (negligible). Weight management replaces `crossFadeTo` for the gesture path.
**Sources:** body-animations.mjs (build copy) `play`, `playGesture`, `setSpeaking`, `_playFromActivePool`, `finished` handler
**Exploration:** surfaced in review (R1-07)
**Depends on:** D13
**Status:** captured (round 1)

## D12: Gesture vocabulary — a single resource behind an SPI

**Choice:**
- **One file:** `speech-demo/src/main/resources/META-INF/resources/animations/gestures.json`. It is both web-served (the client `fetch`es it) and on the classpath (Java loads it). Each entry has:
  - `tag`
  - `description` (for the prompt)
  - `target`: `{clip}` or `{subcategory}`
  - `promptable`
  - per-clip `strokeMs` (D4)
- Pool membership for the floor states (D13) also lives in this file.
- **SPI:** `speech-api` gains `GestureVocabulary` (`tags()`, `isValid(tag)`, `promptEntries()`), so `speech-ws` stays app-agnostic. `speech-demo` supplies the JSON-backed implementation.
- **Derived from it:**
  - The segmenter validates against `GestureVocabulary` (D2).
  - `GesturePromptSection` renders `promptEntries()` (D7).
  - The client's `GESTURE_MAP` constant is deleted. `body-animations.mjs` builds its map from the fetched file.
- A build-time test asserts that every clip referenced in `gestures.json` exists in `m2m-animations.glb`. This catches the stale-clip class of bug (`thinker`, `shocked-recoil`, etc.).
**Alternatives:**
- JS `GESTURE_MAP` plus a Java validation set plus prompt text (implicit in the original D7). Rejected: three hand-synced copies, and the build copy already shows drift.
- Vocabulary hard-coded in `speech-ws`. Rejected: `speech-ws` is a reusable library, and the vocabulary is the demo's asset.
**Rationale:** The vocabulary is data that crosses the module boundary (demo assets → library validation → LLM prompt → client resolution). It needs exactly one source.
**Trade-offs:** The client waits for an extra fetch before body animations are enabled. Taxonomy pool definitions should migrate into the same file over time.
**Sources:** body-animations.mjs GESTURE_MAP (build copy); HANDOFF "Deleted" list; speech-api module charter (CLAUDE.md)
**Exploration:** surfaced in review (R1-11)
**Status:** captured (round 1)

## D13: Conversational floor states

**Choice:** The client keeps an explicit floor state machine. Each state selects a base-layer pool (D11) from `gestures.json`.

| State | Entered on | Pool |
|---|---|---|
| idle | `TurnComplete` + drained queue (D5), or start | `IDLE_POOL` |
| listening | mic `start`, or `UserActivity(typing)` | listening pool (`idle-listening`, …) |
| thinking | user `stop` / `text` sent, until first segment plays | thinking pool (`conversational.uncertainty`-adjacent; clip choice is data) |
| speaking | first segment of a turn starts playing | `SPEAKING_POOL` |

- Proactive turns go idle → speaking directly.
- Barge-in (the user starts the mic during speaking) moves to listening. Audio keeps playing; it is not interrupted. Audio interruption is out of scope and recorded as a known gap.
- The current one-shot `playBodyGesture('thinking')` on send is replaced by the thinking state. It also targeted the deleted `thinker` clip.
**Alternatives:**
- Speaking/idle only (implicit in the original D5). Rejected: the 1–3 s latency window from STT through cleanup, LLM and first-sentence TTS is where an avatar most needs a "working on it" signal. #450's design named "thinking while the LLM generates" as a goal.
**Rationale:** The client already observes every transition: its own `start`/`stop`/`text`, the first segment's playback, and `TurnComplete`. Recording the states makes pool selection one lookup instead of scattered calls.
**Trade-offs:** Requires pools that are not authored yet; until then listening and thinking may map to existing idle clips.
**Sources:** 2026-11-11-mixamo-avatar-animation-design.md L10; built index.html L328 (`playBodyGesture('thinking')`)
**Exploration:** surfaced in review (R1-15)
**Depends on:** D10, D11
**Status:** captured (round 1)

## D14: Per-frame channel ownership and playback host

**Choice:** Channel ownership is explicit:

| Channel | Owner |
|---|---|
| Head, neck and eye bones | TalkingHead (already masked from body clips) |
| All other skeleton bones | `BodyAnimationSystem` mixer |
| Face morphs | one face compositor (D6), run once per frame inside TalkingHead's frame before render, never from a separate rAF loop |
| Audio clock | the playback host |

The demo's separate `tickBody` rAF loop is removed. Two independent rAF callbacks make the final pose depend on callback order and render timing.

**Playback host, gated by a verification spike:** adopt TalkingHead 1.8's `speakAudio` as the host if all of the following hold:
- (a) it accepts our viseme timeline and words/markers;
- (b) markers fire on the audio clock;
- (c) its per-frame body-pose system can be prevented from writing non-head bones while the external mixer owns them;
- (d) it exposes a hook to run the body mixer and face compositor inside its frame.

If all four hold, the custom audio queue and viseme rAF loop are deleted, and gesture cues become `markers` at `offsetMs / rate − stroke`. If any fails, keep the custom host and implement the ownership table above directly. The spike result is recorded here before implementation planning.
**Alternatives:**
- Leave ownership implicit, with three uncoordinated writers (status quo). Rejected: the result depends on update order, not on policy.
- Adopt `speakAudio` unconditionally (reviewer's suggestion). Not taken without verification: the review could not confirm the v1.8 API (marker semantics, pose-system interaction), and the demo is on 1.8, not 1.7.
**Rationale:** Settling ownership first is what makes D5, D6 and D11 implementable. TalkingHead's own timeline would remove custom queue code and fits the IDEAS.md goal of keeping #457 contribution-friendly upstream, but only if it really yields the bones.
**Trade-offs:** A spike before planning. Possible dependency on TalkingHead internals if a frame hook is not public API.
**Sources:** built index.html L603–652 (TalkingHead 1.8, separate mixer, separate rAF); index.html L248–309 (custom queue and viseme loop); IDEAS.md (upstream contribution)
**Exploration:** surfaced in review (R1-16)
**Depends on:** D6, D11
**Status:** captured (round 1), spike pending

## D15: Keyword fallback — dropped

**Choice:** No keyword fallback for gestures or mood. The base speaking pool provides continuous conversational motion when the LLM chooses no gesture. Choosing no gesture is now a valid decision under the D7 density rule ("none when no gesture fits"). The client-side `classifyResponse`, `KEYWORD_GESTURES` and `KEYWORD_MOODS` are deleted. The server logs tag adherence per turn: tags present, valid and dropped. A model that ignores the instructions is therefore visible in logs, and a server-side whole-word fallback can be revisited with data.
**Alternatives:**
- Client-side substring fallback on `Response` (HANDOFF scope item 3). Rejected:
  - It inherits the streaming-path lateness (D2).
  - Substring matching misfires: `"hi "` matches `"sushi "`, `"yes"` matches `"eyes"`, `"love"` matches `"I'd love to help"`.
- Server-side per-segment whole-word fallback through the cue path. Rejected for now: it overrides the LLM's deliberate choice of no gesture, which is the point of D7. It may be reintroduced, data-driven, if adherence logs show a model systematically emits no tags.
**Rationale:** With a promptable vocabulary on every LLM path, the fallback fills a gap that no longer exists, and does so with low precision.
**Trade-offs:** Removes listed #457 scope (HANDOFF "What needs building" item 3). With models that ignore instructions, gestures are rarer until the fallback is reconsidered.
**Sources:** body-animations.mjs KEYWORD_GESTURES / classifyResponse (build copy); built index.html KEYWORD_MOODS / detectMood
**Exploration:** surfaced in review (R1-17)
**Depends on:** D7
**Status:** captured (round 1)

# Decisions — #457 Integrate M2M Animation Library

## D1: Branch base for #457

**Choice:** Rebase on #458 branch
**Alternatives:**
- Merge #458 to main first — cleaner history but requires landing #458 now
- Cherry-pick files — avoids branch dependency but loses commit history
**Rationale:** #457 depends on all of #458's code (body-animations.mjs, m2m-animations.glb, updated index.html). Rebasing preserves full context. Work-end lands both together.
**Trade-offs:** Branch depends on #458 not being force-pushed or rebased.
**Sources:** Queue state showing #458 closed but not landed on main
**Exploration:** quick
**Status:** captured

## D2: Gesture tag parsing location — server-side per-sentence

**Choice:** Server parses and strips `[gesture:tag]` per sentence before TTS, sends tagged text in Response to client
**Alternatives:**
- Full server-side parsing with separate Animation messages — more protocol complexity
- Client-only with prompt engineering — fragile, TTS would vocalize tags
**Rationale:** The server splits text into sentences for TTS anyway. Tags must be stripped before TTS (otherwise TTS vocalizes "[gesture:greeting]"). The existing client-side tag parsing from #458 continues to work because Response still contains tagged text.
**Trade-offs:** Gesture tag parsing logic exists in both server (for stripping/timing) and client (for triggering). But the client parsing is already there from #458.
**Sources:** SpeechSession.java handleStop/handleText flow, body-animations.mjs parseGestureTags, research on SSML mark tags (Azure, Google, BML standard)
**Exploration:** deep-analysis

## D3: Gesture sync mechanism — extend Phonemes message with gesture + wordIndex

**Choice:** Add optional `gesture` (String) and `wordIndex`/`wordCount` (int) fields to the Phonemes WebSocket message
**Alternatives:**
- Separate Animation message per sentence — adds protocol complexity and ordering dependency
- Batch gesture map in Response message — fragile if sentence splitting differs client/server
**Rationale:** Phonemes message already arrives per-sentence just before its audio. Adding gesture info here naturally pairs gesture + audio in the client's existing playback queue. Zero new message types. Matches Azure bookmark pattern at sentence granularity.
**Trade-offs:** Phonemes message grows slightly. Gesture is conceptually separate from phonemes but pragmatically co-located.
**Sources:** Azure avatar SSML bookmark gestures, AIAvatarKit pipeline, SARGes gesture label scheme
**Exploration:** deep-analysis
**Depends on:** D2 (server-side parsing)

## D4: Gesture timing — word-index proportional

**Choice:** Server sends `wordIndex` and `wordCount`. Client calculates `triggerMs = (wordIndex / wordCount) * audioDurationMs`.
**Alternatives:**
- Sentence-start only — simpler but coarse, gesture always fires at sentence beginning
- Viseme-boundary mapping — most accurate but requires character-to-phoneme alignment
- Character-offset proportional — slightly better correlation with spoken duration
**Rationale:** First-principles analysis shows the 400ms crossfade duration absorbs most proportional timing errors. Natural co-speech gestures lead speech by 100-300ms (McNeill), so early firing is acceptable. LLM tags cluster at phrase boundaries where proportional is accurate. Architecture supports upgrading to viseme-lookup later without protocol changes.
**Trade-offs:** Proportional word-to-time mapping is imprecise for sentences mixing short and long words. Error can be up to ~400ms in extreme cases. Absorbed by crossfade.
**Sources:** McNeill "Hand and Mind" on gesture-speech timing, crossfade analysis of CROSSFADE_DURATION=0.4
**Exploration:** deep-analysis
**Depends on:** D3 (gesture in Phonemes message)

## D5: Speaking pool activation — audio playback start/end

**Choice:** Switch to speaking pool when first audio chunk starts playing in client, back to idle when last chunk finishes
**Alternatives:**
- Response message arrival — slightly early (Response arrives before audio)
- Server-sent speaking state messages — most precise server-side control but adds protocol
**Rationale:** Perfectly synced to what the user hears. The client's existing `playQueue` already has this boundary — first chunk `play()` and last chunk `onended`.
**Trade-offs:** Speaking state is entirely client-driven, server has no visibility into it. Fine for a demo.
**Sources:** index.html playNextQueued function, BodyAnimationSystem.setSpeaking
**Exploration:** quick

## D6: Morph target conflicts — conditional pass-through

**Choice:** Apply body-animation morph targets only when NOT actively speaking (no visemes playing). Speaking visemes take full priority.
**Alternatives:**
- Strip all morph target tracks from body animations — clean separation but loses shrug's frown
- Additive blending — more expressive but risks visual artifacts during speech
**Rationale:** Preserves the shrug frown expression during idle while preventing conflicts during speech. TalkingHead's viseme system is authoritative during speech; body animation morph targets (like mouthFrown) are authoritative during idle.
**Trade-offs:** Morph target switch at speech boundaries might cause a visual pop. Can be mitigated with a brief lerp.
**Sources:** body-animations.mjs HEAD_BONES mask, index.html viseme playback system
**Exploration:** quick

## D7: LLM system prompt — full gesture vocabulary

**Choice:** Include all ~40 tags from GESTURE_MAP in the LLM system prompt. Server validates against GESTURE_MAP and drops unknown tags.
**Alternatives:**
- Curated subset ~15-25 tags — fewer awkward choices but arbitrary filtering
- No prompt instructions — keyword fallback only, less intentional
**Rationale:** The LLM is better at judging conversational appropriateness than a static filter. The full vocabulary includes theatrical gestures (death, zombie) that would be jarring in normal conversation but appropriate in dramatic contexts. Server-side validation catches hallucinated tags (documented risk from medical-robot LLM study).
**Trade-offs:** Longer system prompt (~200 tokens for full vocabulary). Small risk of LLM choosing theatrical gestures inappropriately.
**Sources:** SARGes ethogram approach, medical robot LLM gesture study, GESTURE_MAP in body-animations.mjs
**Exploration:** quick

## D8: Idle attention behaviour — escalating timeouts

**Choice:** 30s silence → attention gesture (knock/interact), 60s → another attention gesture, then resume relaxed idle pool.
**Alternatives:**
- 30s timeout, single gesture — simpler, less lifelike
- Skip for now — defer to follow-up issue
**Rationale:** Escalating timeouts feel more natural — the avatar gently prompts, then gives up. Two levels before resuming idle avoids feeling pushy.
**Trade-offs:** More client-side timer state. Risk of feeling annoying if thresholds are too aggressive.
**Sources:** idle.attention subcategory in TAXONOMY (knock weight:8, interact weight:6)
**Exploration:** quick

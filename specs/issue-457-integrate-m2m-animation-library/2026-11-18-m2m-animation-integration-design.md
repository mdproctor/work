# M2M Animation Library Integration — Design Spec

**Issue:** casehubio/agency#457
**Branch:** issue-457-integrate-m2m-animation-library (rebased on issue-458-author-23-conversational-body-poses)
**Date:** 2026-11-18

## Overview

Wire the M2M animation library (68 clips authored in #458) into TalkingHead's speech pipeline so the avatar performs contextually appropriate body gestures during conversation. The `body-animations.mjs` module already has the full taxonomy, pool selection, gesture tag resolution, keyword matching, and crossfade playback. This issue connects that system to the live avatar during speech with proper audio-gesture synchronisation.

## Architecture

The integration spans three layers:

```
LLM → [gesture:tag] in response text
  ↓
Server (SpeechSession): strip tags per-sentence before TTS,
  extract gesture + wordIndex, send in Phonemes message
  ↓
Client (index.html): play gesture from playback queue
  with proportional timing delay
```

**Key principle:** The sentence boundary is the sync point. The server already splits LLM responses into sentences for TTS. Each sentence gets its own Phonemes + audio delivery. Gesture info rides alongside Phonemes — the client triggers the gesture when it starts playing that sentence from its audio queue.

## Component Changes

### 1. Server: SpeechSession gesture extraction

**File:** `speech-ws/src/main/java/io/casehub/agency/speech/ws/SpeechSession.java`

Add a utility method that processes a sentence before TTS:

```java
record GestureExtraction(String cleanText, @Nullable String gesture, int wordIndex, int wordCount) {}

static GestureExtraction extractGestures(String sentence) {
    // Parse [gesture:tag] from sentence
    // Strip tags, count word position of first tag
    // Return clean text + gesture info
}
```

Call `extractGestures()` in the sentence loop within `handleStop()` and `handleText()`:
- TTS receives `cleanText` (no tags)
- Gesture info is passed to `synthesiseAndSend()`

Also strip `[mood:tag]` markers before TTS (same vocalization problem).

Validate gesture names against a known set (the GESTURE_MAP keys). Drop unknown tags silently — the medical-robot LLM study showed small models sometimes hallucinate tag names outside the vocabulary.

### 2. Protocol: Extend Phonemes message

**File:** `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java`

Extend the `Phonemes` record:

```java
record Phonemes(
    List<VisemeFrame> data,
    @Nullable String gesture,
    int gestureWordIndex,
    int gestureWordCount
) implements AvatarMessage {}
```

**File:** `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/MessageCodec.java`

Update `encode()` to include `gesture`, `gestureWordIndex`, `gestureWordCount` fields when gesture is non-null.

### 3. Server: LLM system prompt

**File:** `speech-ws/src/main/java/io/casehub/agency/speech/ws/DefaultPromptAssembler.java`

Append gesture instructions to the system prompt:

```
You can express body language by placing [gesture:tag] markers in your responses.
Available gestures: nod, bow, no, naughty, shrug, wait, aha, point, thumbsup,
yes, reject, knock, thinking, confused, dizzy, clapping, angry, greeting,
victory, fistpump, dab, sad, joyful, facepalm, doublefacepalm, shocked,
overwhelmed, horror, oops, nervous, shiver, careful, sneaky, rewind, meditate,
pray, guitar, drumroll, sing, death, hit, zombie, drunk, backflip, powerup,
agreement, disagreement, emphasis, uncertainty, celebrate, surprise, distress,
dance, attention, happy, explaining.

Place markers at natural points in your speech. Example:
"Hello! [gesture:greeting] Welcome to the demo."
"Hmm, [gesture:thinking] that's an interesting question."

Use gestures sparingly — one or two per response is natural. Not every
response needs a gesture. You can also set mood with [mood:happy], [mood:sad],
[mood:angry], [mood:love], [mood:fear].
```

### 4. Client: Audio-synced gesture triggering

**File:** `speech-demo/src/main/resources/META-INF/resources/index.html`

Modify the `ws.onmessage` handler for `'phonemes'` to capture gesture info alongside viseme data:

```javascript
case 'phonemes':
  pendingVisemes = msg.data;
  pendingGesture = msg.gesture ? {
    name: msg.gesture,
    wordIndex: msg.gestureWordIndex || 0,
    wordCount: msg.gestureWordCount || 1
  } : null;
  break;
```

Modify `playAudio()` to capture `pendingGesture` synchronously (like it already does for `pendingVisemes`), and pass it into the `playQueue` entry:

```javascript
playQueue.push({ audio: audioBuf, visemes: myVisemes, gesture: myGesture });
```

In `playNextQueued()`, when starting playback of a queue item with a gesture:

```javascript
if (item.gesture && bodyAnim) {
  var delay = (item.gesture.wordIndex / item.gesture.wordCount)
              * (item.audio.duration * 1000);
  setTimeout(function() {
    bodyAnim.playGesture(item.gesture.name);
  }, delay);
}
```

Remove the existing gesture-from-Response logic in the `'response'` case — gestures now come via Phonemes/audio queue sync. Keep the keyword fallback (`classifyResponse`) as a safety net for sentences with no server-extracted gesture.

### 5. Client: Speaking pool switching

**File:** `speech-demo/src/main/resources/META-INF/resources/index.html`

In `playNextQueued()`:
- When starting the **first** queued item (transition from `!isPlaying`), call `bodyAnim.setSpeaking(true)`
- When the **last** queued item finishes (`playQueue.length === 0`), call `bodyAnim.setSpeaking(false)`

This is the natural boundary — the avatar is "speaking" exactly when audio is playing.

### 6. Client: Morph target conditional pass-through

**File:** `speech-demo/src/main/resources/META-INF/resources/animations/body-animations.mjs`

Extend `_maskHeadBones(clip)` to also strip morph target tracks:

```javascript
_maskHeadBones(clip) {
  clip.tracks = clip.tracks.filter(track => {
    const dot = track.name.indexOf(".");
    if (dot === -1) return true;
    const boneName = track.name.slice(0, dot);
    if (HEAD_BONES.has(boneName)) return false;
    // Store morph target tracks separately for conditional playback
    if (track.name.includes('.morphTargetInfluences')) {
      this._morphTracks.set(clip.name, this._morphTracks.get(clip.name) || []);
      this._morphTracks.get(clip.name).push(track);
      return false; // Strip from main clip
    }
    return true;
  });
}
```

Add a method `setVisemesActive(active)` that the client calls when viseme playback starts/stops. When visemes are NOT active and the current body animation has morph targets, apply them additively.

### 7. Client: Idle attention with escalating timeouts

**File:** `speech-demo/src/main/resources/META-INF/resources/animations/body-animations.mjs`

Add attention state to `BodyAnimationSystem`:

```javascript
startAttentionMonitor() {
  this._lastActivity = Date.now();
  this._attentionLevel = 0; // 0=none, 1=first prompt, 2=second prompt
  this._attentionTimer = setInterval(() => {
    if (this._speaking) { this._resetAttention(); return; }
    const silence = Date.now() - this._lastActivity;
    if (silence > 60000 && this._attentionLevel === 1) {
      this._attentionLevel = 2;
      this.playGesture('attention');
    } else if (silence > 30000 && this._attentionLevel === 0) {
      this._attentionLevel = 1;
      this.playGesture('attention');
    }
    // After level 2, resume normal idle — don't keep prompting
  }, 5000);
}

recordActivity() {
  this._lastActivity = Date.now();
  this._attentionLevel = 0;
}
```

Call `recordActivity()` from the client on user input (send, mic start, any interaction).

## Data Flow — Complete Speech Cycle

```
1. User types "What is machine learning?"
   → Client: bodyAnim.playGesture('thinking')
   → Client: bodyAnim.recordActivity()

2. Server: LLM responds:
   "Machine learning [gesture:explaining] is a branch of AI
    that learns from data. [gesture:nod] It's quite powerful."

3. Server splits into sentences:
   a. "Machine learning is a branch of AI that learns from data."
      gesture=explaining, wordIndex=1, wordCount=12
   b. "It's quite powerful."
      gesture=nod, wordIndex=0, wordCount=3

4. Server TTS each clean sentence:
   a. → Phonemes(visemes, "explaining", 1, 12) → audio bytes
   b. → Phonemes(visemes, "nod", 0, 3) → audio bytes

5. Client receives:
   - Response("Machine learning [gesture:explaining] is a branch...")
     → display clean text, detect mood
   - Phonemes a → capture pending gesture + visemes
   - Audio a → queue with gesture {explaining, 1, 12}
   - Phonemes b → capture pending gesture + visemes
   - Audio b → queue with gesture {nod, 0, 3}

6. Client playback:
   - Start queue → bodyAnim.setSpeaking(true)
   - Play audio a: delay = (1/12)*audioDuration → setTimeout → playGesture('explaining')
   - Play audio b: delay = (0/3)*audioDuration = 0ms → playGesture('nod') immediately
   - Queue empty → bodyAnim.setSpeaking(false) → idle pool resumes
```

## Testing

- **Unit test:** `GestureExtraction` parsing — tags stripped, wordIndex calculated, unknown tags dropped
- **Unit test:** `Phonemes` encoding with gesture fields
- **Manual test:** Send messages with gesture tags, verify gestures fire at approximately the right moment
- **Manual test:** Verify TTS does NOT vocalize `[gesture:X]` or `[mood:X]` markers
- **Manual test:** Speaking pool switches on first audio play, off on last audio end
- **Manual test:** Morph target gestures (shrug frown) show during idle, hidden during speech
- **Manual test:** Idle attention fires at 30s and 60s, resets on user input
- **Edge cases:** Response with no gesture tags (keyword fallback should still work), multiple gesture tags in one sentence (use first), empty response

## References

- [SpeechSession.java] — server speech pipeline, sentence splitting, TTS dispatch
- [AvatarMessage.java] — WebSocket protocol sealed hierarchy
- [body-animations.mjs] — BodyAnimationSystem, GESTURE_MAP, TAXONOMY, parseGestureTags
- [index.html (#458 branch)] — existing client integration, playQueue, viseme playback
- [Azure avatar SSML gestures](https://learn.microsoft.com/en-nz/azure/ai-services/speech-service/text-to-speech-avatar/avatar-gestures-with-ssml) — SSML bookmark gesture sync pattern
- [AIAvatarKit](https://github.com/uezo/aiavatarkit) — LLM → animation pipeline reference
- [SARGes](https://arxiv.org/pdf/2503.20202) — LLM ethogram-based gesture label assignment
- [TANDE](https://arxiv.org/pdf/2607.13357) — structured JSON gesture output from LLM
- McNeill "Hand and Mind" — gesture-speech timing: gestures lead speech by 100-300ms

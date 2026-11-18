# Client-Side Floor States and Animation Layering — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #457 — Avatar demo: integrate M2M animation library with idle cycle and LLM-tagged gestures
**Issue group:** #457

**Goal:** Wire the server's new turn-framed protocol (Segment, TurnComplete) into a client-side floor state machine that drives body animation pool switching and two-layer animation arbitration.

**Architecture:** `body-animations.mjs` becomes a data-driven module loaded from `avatar-performance.json`. It exposes a floor state machine (idle/listening/thinking/speaking) where each state selects a base-layer pool, and a gesture layer that ducks the base when active. `index.html` handles the new protocol messages, parses 8-byte binary audio headers, tracks active turns, wires floor state transitions, and registers the body mixer with TalkingHead's frame loop via the fork's extension points.

**Tech Stack:** ES modules (three.js r180), TalkingHead 1.8 fork (vendored), Quarkus WebSocket

## Global Constraints

- No build toolchain — ES modules via import map, inline `<script type="module">`
- No JS test framework — verification is manual browser testing against the running server
- Server still sends legacy messages (Response, Phonemes) alongside new protocol — client ignores them
- Mark timing (D4) is blocked by connectors — gesture cues have no `offsetMs` yet, play immediately on segment receipt
- Face compositing (D6) and mood ownership (D16) are out of scope
- `speakAudio` adoption (D14 playback host) is deferred — keep custom audio queue with header parsing

---

## Batch 1: body-animations.mjs — data-driven floor states with animation layering

### Task 1: Rewrite BodyAnimationSystem — data-driven, floor state machine, base+gesture layers

**Files:**
- Rewrite: `speech-demo/src/main/resources/META-INF/resources/animations/body-animations.mjs`

**Interfaces:**
- Consumes: `avatar-performance.json` (fetched by caller, passed as config object)
- Produces:
  - `class BodyAnimationSystem` — constructor takes `(avatarRoot, config)` where config is the parsed JSON
  - `static async create(avatarRoot, config)` — factory that loads GLB and returns ready instance
  - `setFloorState(state)` — `'idle'|'listening'|'thinking'|'speaking'` → switches base-layer pool
  - `triggerGesture(tag)` — resolves gesture tag to clip, plays as gesture layer with base ducking
  - `update(dt)` — drives the mixer (called by TalkingHead via registerMixer, or external loop)
  - `get mixer()` — exposes the THREE.AnimationMixer for registration with TalkingHead
  - `get floorState` — current state string
  - `dispose()` — cleanup
  - Exports: `BodyAnimationSystem` (class), `FloorState` (enum-like object)
  - Deleted exports: `parseGestureTags`, `classifyResponse`

- [ ] **Step 1: Write the new module**

Replace the entire contents of `body-animations.mjs`. The module loads its data from a config object (the parsed `avatar-performance.json`), not from hardcoded constants.

```javascript
import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";

const ANIMATION_GLB = "animations/m2m-animations.glb";
const CROSSFADE_DURATION = 0.4;
const GESTURE_DUCK_DURATION = 0.3;

const CYCLE_INTERVALS = {
  idle:      { min: 5000, max: 15000 },
  listening: { min: 6000, max: 12000 },
  thinking:  { min: 4000, max:  8000 },
  speaking:  { min: 4000, max: 10000 },
};

export const FloorState = Object.freeze({
  IDLE:      'idle',
  LISTENING: 'listening',
  THINKING:  'thinking',
  SPEAKING:  'speaking',
});

const HEAD_BONES = new Set([
  "Head", "Neck", "LeftEye", "RightEye",
  "HeadTop_End", "Neck1",
]);

function resolveSubcategory(taxonomy, path) {
  const [cat, sub] = path.split(".");
  const category = taxonomy[cat];
  return category ? category[sub] || null : null;
}

function pickWeighted(entries) {
  let total = 0;
  for (const e of entries) total += e.weight;
  let r = Math.random() * total;
  for (const e of entries) {
    r -= e.weight;
    if (r <= 0) return e;
  }
  return entries[0];
}

function pickFromPool(taxonomy, poolRefs) {
  const ref = pickWeighted(poolRefs);
  const clips = resolveSubcategory(taxonomy, ref.subcategory);
  if (!clips || clips.length === 0) return null;
  return pickWeighted(clips).clip;
}

export class BodyAnimationSystem {

  /**
   * @param {THREE.Object3D} avatarRoot
   * @param {Object} config — parsed avatar-performance.json
   * @param {Map<string, THREE.AnimationClip>} clips — pre-loaded clips
   */
  constructor(avatarRoot, config, clips) {
    this._config = config;
    this._clips = clips;
    this._mixer = new THREE.AnimationMixer(avatarRoot);

    // Build gesture lookup from config.gestures
    this._gestureMap = new Map();
    for (const g of config.gestures) {
      this._gestureMap.set(g.tag, g.target);
    }

    // State
    this._floorState = FloorState.IDLE;
    this._active = true;

    // Base layer — looping pool clip
    this._baseAction = null;
    this._baseWeight = 1.0;

    // Gesture layer — one-shot clip, ducks base
    this._gestureAction = null;
    this._gestureWeight = 0.0;
    this._gestureDucking = false;
    this._duckProgress = 0;

    // Cycle timer
    this._cycleTimer = null;

    // Finished listener for gesture completion
    this._onGestureFinished = null;
  }

  static async create(avatarRoot, config) {
    const gltf = await new GLTFLoader().loadAsync(ANIMATION_GLB);
    const clips = new Map();
    for (const clip of gltf.animations) {
      clip.tracks = clip.tracks.filter(track => {
        const dot = track.name.indexOf(".");
        if (dot === -1) return true;
        return !HEAD_BONES.has(track.name.slice(0, dot));
      });
      clips.set(clip.name, clip);
    }
    console.log(`[BodyAnim] Loaded ${clips.size} clips`);
    const system = new BodyAnimationSystem(avatarRoot, config, clips);
    system._playFromPool();
    system._startCycle();
    return system;
  }

  get mixer() { return this._mixer; }
  get floorState() { return this._floorState; }
  get isPlaying() { return this._baseAction !== null && this._baseAction.isRunning(); }

  update(dt) {
    if (!this._active) return;

    // Animate duck/unduck weights
    if (this._gestureDucking && this._duckProgress < 1) {
      this._duckProgress = Math.min(1, this._duckProgress + dt / GESTURE_DUCK_DURATION);
      this._baseWeight = 1 - this._duckProgress;
      this._gestureWeight = this._duckProgress;
      this._applyWeights();
    } else if (!this._gestureDucking && this._duckProgress > 0) {
      this._duckProgress = Math.max(0, this._duckProgress - dt / GESTURE_DUCK_DURATION);
      this._baseWeight = 1 - this._duckProgress;
      this._gestureWeight = this._duckProgress;
      this._applyWeights();
      if (this._duckProgress === 0 && this._gestureAction) {
        this._gestureAction.stop();
        this._gestureAction = null;
      }
    }

    this._mixer.update(dt);
  }

  setFloorState(state) {
    if (this._floorState === state) return;
    this._floorState = state;
    // Pool change swaps the base clip underneath any active gesture
    this._playFromPool();
    this._startCycle();
  }

  triggerGesture(tag) {
    const target = this._gestureMap.get(tag);
    if (!target) {
      console.warn(`[BodyAnim] Unknown gesture tag: ${tag}`);
      return false;
    }

    let clipName;
    if (target.clip) {
      clipName = target.clip;
    } else if (target.subcategory) {
      const clips = resolveSubcategory(this._config.taxonomy, target.subcategory);
      if (!clips || clips.length === 0) return false;
      clipName = pickWeighted(clips).clip;
    } else {
      return false;
    }

    return this._playGesture(clipName);
  }

  dispose() {
    this._stopCycleTimer();
    this._removeGestureListener();
    if (this._mixer) this._mixer.stopAllAction();
    this._baseAction = null;
    this._gestureAction = null;
    this._active = false;
    this._mixer = null;
    this._clips = null;
  }

  // ── Base layer ──────────────────────────────────────────────

  _playFromPool() {
    const poolRefs = this._config.pools[this._floorState];
    if (!poolRefs || poolRefs.length === 0) return;
    const name = pickFromPool(this._config.taxonomy, poolRefs);
    if (name) this._playBase(name);
  }

  _playBase(name) {
    const clip = this._clips.get(name);
    if (!clip) {
      console.warn(`[BodyAnim] Unknown base clip: ${name}`);
      return;
    }

    const action = this._mixer.clipAction(clip);
    action.setLoop(THREE.LoopRepeat, Infinity);

    if (this._baseAction && this._baseAction !== action) {
      action.reset();
      action.setEffectiveWeight(this._baseWeight);
      action.play();
      this._baseAction.crossFadeTo(action, CROSSFADE_DURATION, true);
    } else if (!this._baseAction) {
      action.reset();
      action.setEffectiveWeight(this._baseWeight);
      action.play();
    }
    this._baseAction = action;
  }

  // ── Gesture layer ───────────────────────────────────────────

  _playGesture(name) {
    const clip = this._clips.get(name);
    if (!clip) {
      console.warn(`[BodyAnim] Unknown gesture clip: ${name}`);
      return false;
    }

    // Clean up previous gesture
    this._removeGestureListener();
    if (this._gestureAction) {
      this._gestureAction.stop();
    }

    const action = this._mixer.clipAction(clip);
    action.setLoop(THREE.LoopOnce, 1);
    action.clampWhenFinished = true;
    action.reset();
    action.setEffectiveWeight(0);
    action.play();

    this._gestureAction = action;
    this._gestureDucking = true;
    this._duckProgress = 0;

    // On gesture finish, unduck
    this._onGestureFinished = (e) => {
      if (e.action === action) {
        this._gestureDucking = false;
        // duckProgress will animate back to 0 in update()
        this._removeGestureListener();
      }
    };
    this._mixer.addEventListener("finished", this._onGestureFinished);

    return true;
  }

  _removeGestureListener() {
    if (this._onGestureFinished && this._mixer) {
      this._mixer.removeEventListener("finished", this._onGestureFinished);
      this._onGestureFinished = null;
    }
  }

  _applyWeights() {
    if (this._baseAction) this._baseAction.setEffectiveWeight(this._baseWeight);
    if (this._gestureAction) this._gestureAction.setEffectiveWeight(this._gestureWeight);
  }

  // ── Cycle timer ─────────────────────────────────────────────

  _startCycle() {
    this._stopCycleTimer();
    const intervals = CYCLE_INTERVALS[this._floorState] || CYCLE_INTERVALS.idle;
    const delay = intervals.min + Math.random() * (intervals.max - intervals.min);
    this._cycleTimer = setTimeout(() => {
      if (this._active) {
        this._playFromPool();
        this._startCycle();
      }
    }, delay);
  }

  _stopCycleTimer() {
    clearTimeout(this._cycleTimer);
    this._cycleTimer = null;
  }
}
```

- [ ] **Step 2: Verify the module loads**

Run: `mvn --batch-mode install -DskipTests -pl speech-demo`

This checks the resource is packaged. Since this is JS, there's no compile-time check. The real verification is in Batch 2 when the page is loaded.

- [ ] **Step 3: Commit**

```bash
git add speech-demo/src/main/resources/META-INF/resources/animations/body-animations.mjs
git commit -m "feat(#457): rewrite BodyAnimationSystem — data-driven floor states with base+gesture layering (D11, D12, D13)

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 2: index.html — new protocol, turn tracking, TalkingHead integration

### Task 2: Rewrite index.html client — new protocol handling, floor states, mixer registration

**Files:**
- Rewrite: `speech-demo/src/main/resources/META-INF/resources/index.html`

**Interfaces:**
- Consumes:
  - `BodyAnimationSystem.create(avatarRoot, config)` → instance with `setFloorState()`, `triggerGesture()`, `mixer`
  - `FloorState` enum from body-animations.mjs
  - TalkingHead fork: `head.registerMixer(mixer, { boneClaim: [...] })`, `head.addEventListener('audiostart'|'audioend', ...)`
  - Server protocol (JSON): `segment`, `turnComplete`, `gesture`, `mood`, `playbackComplete`, `userActivity`
  - Server protocol (binary): 8-byte LE header `[u8 version, u8 reserved, u16 segmentIndex, u32 turnSeq]` + WAV
- Produces: Working demo page with floor-state-driven body animations and new protocol handling

The changes below target the `<script type="module">` block in index.html. The HTML structure and CSS are unchanged.

- [ ] **Step 1: Update imports and add turn-tracking state**

Replace the import line and state variables at the top of the script block.

Old:
```javascript
import { BodyAnimationSystem, parseGestureTags, classifyResponse } from "./animations/body-animations.mjs";
```
New:
```javascript
import { BodyAnimationSystem, FloorState } from "./animations/body-animations.mjs";
```

Add turn-tracking state after the existing variable declarations:

```javascript
// Turn tracking (D3, D5, D10)
let activeTurnSeq = -1;
let pendingSegments = new Map();    // key: "turnSeq:index" → { text, hasAudio, visemes, cues }
let turnSegmentCount = -1;          // set by TurnComplete
let turnAudioPlayed = new Set();    // segmentIndex values whose audio has ended
let turnComplete = false;           // TurnComplete received for activeTurnSeq
let turnSafetyTimer = null;         // 10s fallback if TurnComplete never arrives
const TURN_SAFETY_TIMEOUT = 10000;
```

- [ ] **Step 2: Delete client-side mood detection and tag parsing**

Remove the `MOOD_TAG_RE`, `KEYWORD_MOODS`, `detectMood`, and `playBodyGesture` declarations (lines ~199–225 in the current file). Also remove `var serverAnimationReceived = false;`.

- [ ] **Step 3: Add protocol helper functions**

Add these functions after the state variables:

```javascript
function sendWsJson(obj) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

function enterFloorState(state) {
  if (!bodyAnim) return;
  bodyAnim.setFloorState(state);
}

function parseBinaryHeader(buffer) {
  const view = new DataView(buffer);
  return {
    version: view.getUint8(0),
    segmentIndex: view.getUint16(2, true),
    turnSeq: view.getUint32(4, true),
  };
}

function resetTurnState() {
  pendingSegments.clear();
  turnSegmentCount = -1;
  turnAudioPlayed.clear();
  turnComplete = false;
  clearTimeout(turnSafetyTimer);
  turnSafetyTimer = null;
}

function checkTurnFinished() {
  if (!turnComplete) return;
  // All hasAudio segments must have been played
  for (const [key, seg] of pendingSegments) {
    if (seg.hasAudio && !turnAudioPlayed.has(seg.index)) return;
  }
  // Turn is fully played
  enterFloorState(FloorState.IDLE);
  sendWsJson({ type: 'playbackComplete', turnSeq: activeTurnSeq });
  resetTurnState();
}
```

- [ ] **Step 4: Rewrite the WebSocket message handler**

Replace the `ws.onmessage` handler. The new handler processes both legacy messages (transcript, timing, error, partial) and new protocol messages (segment, turnComplete, gesture, mood). Legacy response/phonemes/animation are ignored.

```javascript
ws.onmessage = (evt) => {
  if (typeof evt.data === 'string') {
    const msg = JSON.parse(evt.data);
    switch (msg.type) {
      case 'transcript':
        addMessage(msg.text, 'user');
        break;

      case 'segment': {
        const key = `${msg.turnSeq}:${msg.index}`;
        pendingSegments.set(key, {
          index: msg.index,
          text: msg.text,
          hasAudio: msg.hasAudio,
          visemes: msg.visemes || [],
          cues: msg.cues || [],
        });

        // Show text immediately
        if (msg.text) addMessage(msg.text, 'avatar');

        // Track active turn
        if (msg.turnSeq !== activeTurnSeq) {
          resetTurnState();
          activeTurnSeq = msg.turnSeq;
        }

        // Trigger gesture cues (D4 timing deferred — play immediately)
        for (const cue of (msg.cues || [])) {
          if (cue.tag && bodyAnim) {
            bodyAnim.triggerGesture(cue.tag);
          }
        }

        // No audio → mark as played immediately
        if (!msg.hasAudio) {
          turnAudioPlayed.add(msg.index);
          checkTurnFinished();
        }
        break;
      }

      case 'turnComplete': {
        turnComplete = true;
        turnSegmentCount = msg.segmentCount;

        // If no segments had audio, acknowledge immediately
        if (msg.segmentCount === 0) {
          sendWsJson({ type: 'playbackComplete', turnSeq: msg.turnSeq });
          enterFloorState(FloorState.IDLE);
          resetTurnState();
        } else {
          checkTurnFinished();
          // Safety timeout: if audio never finishes, force idle
          clearTimeout(turnSafetyTimer);
          turnSafetyTimer = setTimeout(() => {
            console.warn('[Turn] Safety timeout — forcing idle');
            enterFloorState(FloorState.IDLE);
            sendWsJson({ type: 'playbackComplete', turnSeq: activeTurnSeq });
            resetTurnState();
          }, TURN_SAFETY_TIMEOUT);
        }
        break;
      }

      case 'gesture':
        if (bodyAnim) bodyAnim.triggerGesture(msg.tag);
        break;

      case 'mood':
        if (head) head.setMood(msg.state);
        break;

      case 'timing':
        showTiming(msg);
        break;

      case 'error':
        addMessage('Error: ' + msg.message, 'system');
        break;

      case 'partial':
        status.textContent = 'Hearing: ' + msg.text;
        break;

      // Legacy messages — ignore (server sends alongside new protocol)
      case 'response':
      case 'phonemes':
      case 'animation':
        break;
    }
  } else {
    // Binary audio frame with 8-byte header (D3)
    handleAudioFrame(evt.data);
  }
};
```

- [ ] **Step 5: Rewrite audio playback with header parsing**

Replace the entire `playAudio`, `playNextQueued`, and queue management code with a header-aware version. The audio queue remains a simple FIFO — the header is used to pair the frame with its segment for future `speakAudio` migration, but for now we just strip it and play.

```javascript
var playQueue = [];
var isPlaying = false;

async function handleAudioFrame(blob) {
  try {
    const arrayBuf = await blob.arrayBuffer();
    const header = parseBinaryHeader(arrayBuf);
    const audioBytes = arrayBuf.slice(8);

    if (!audioCtx) audioCtx = new AudioContext();
    const audioBuf = await audioCtx.decodeAudioData(audioBytes);

    // Enter speaking on first audio of a turn
    if (header.turnSeq === activeTurnSeq && !isPlaying && playQueue.length === 0) {
      enterFloorState(FloorState.SPEAKING);
    }

    playQueue.push({ audio: audioBuf, turnSeq: header.turnSeq, segmentIndex: header.segmentIndex });
    if (!isPlaying) playNextQueued();
  } catch (e) {
    console.error('[Audio] Frame error:', e);
  }
}

function playNextQueued() {
  if (playQueue.length === 0) {
    isPlaying = false;
    status.textContent = 'Ready';
    return;
  }
  isPlaying = true;
  var item = playQueue.shift();

  if (!audioCtx) audioCtx = new AudioContext();
  var source = audioCtx.createBufferSource();
  source.buffer = item.audio;
  var rate = parseFloat(document.getElementById('speed').value);
  source.playbackRate.value = rate;
  source.connect(audioCtx.destination);
  source.start();
  status.textContent = 'Speaking... (' + playQueue.length + ' queued)';

  source.onended = function() {
    // Mark this segment's audio as played
    turnAudioPlayed.add(item.segmentIndex);
    checkTurnFinished();
    playNextQueued();
  };
}
```

- [ ] **Step 6: Wire floor state transitions on user actions**

Update `sendText()`:
```javascript
function sendText() {
  const text = msgInput.value.trim();
  if (!text || !ws || ws.readyState !== WebSocket.OPEN) return;
  const llmModel = document.getElementById('llmModel').value;
  const ttsModel = document.getElementById('ttsModel').value;
  ws.send(JSON.stringify({ type: 'text', text: text, llmModel: llmModel, ttsModel: ttsModel }));
  msgInput.value = '';
  status.textContent = 'Processing...';
  enterFloorState(FloorState.THINKING);
}
```

Update `startRecording()` — add after `recording = true;`:
```javascript
enterFloorState(FloorState.LISTENING);
```

Update `stopRecording()` — in the timeout callback, replace `if (bodyAnim) playBodyGesture('thinking');` with:
```javascript
enterFloorState(FloorState.THINKING);
```

Add typing activity tracking on the message input:
```javascript
let lastTypingActivity = 0;
msgInput.addEventListener('input', function() {
  const now = Date.now();
  if (now - lastTypingActivity > 2000) {
    sendWsJson({ type: 'userActivity', kind: 'TYPING' });
    lastTypingActivity = now;
  }
});
```

Add visibility change tracking:
```javascript
document.addEventListener('visibilitychange', function() {
  sendWsJson({ type: 'userActivity', kind: 'VISIBILITY_CHANGE' });
});
```

- [ ] **Step 7: Rewrite avatar loading — fetch config, registerMixer, claimBones**

Replace the body animation loading section inside the `toggleAvatar` click handler (the `try` block starting with `var avatarModel = head.armature;`):

```javascript
try {
  if (bodyAnimFrameId) { cancelAnimationFrame(bodyAnimFrameId); bodyAnimFrameId = null; }
  if (bodyAnim) { bodyAnim.dispose(); bodyAnim = null; }

  var avatarModel = head.armature;
  if (avatarModel) {
    // Fetch performance vocabulary
    const configResp = await fetch('animations/avatar-performance.json');
    const config = await configResp.json();

    bodyAnim = await BodyAnimationSystem.create(avatarModel, config);

    // Register mixer with TalkingHead — replaces separate rAF loop (D22)
    head.registerMixer(bodyAnim.mixer, {
      boneClaim: [
        'Spine', 'Spine1', 'Spine2', 'Hips',
        'LeftShoulder', 'LeftArm', 'LeftForeArm', 'LeftHand',
        'LeftHandThumb1', 'LeftHandThumb2', 'LeftHandThumb3',
        'LeftHandIndex1', 'LeftHandIndex2', 'LeftHandIndex3',
        'LeftHandMiddle1', 'LeftHandMiddle2', 'LeftHandMiddle3',
        'LeftHandRing1', 'LeftHandRing2', 'LeftHandRing3',
        'LeftHandPinky1', 'LeftHandPinky2', 'LeftHandPinky3',
        'RightShoulder', 'RightArm', 'RightForeArm', 'RightHand',
        'RightHandThumb1', 'RightHandThumb2', 'RightHandThumb3',
        'RightHandIndex1', 'RightHandIndex2', 'RightHandIndex3',
        'RightHandMiddle1', 'RightHandMiddle2', 'RightHandMiddle3',
        'RightHandRing1', 'RightHandRing2', 'RightHandRing3',
        'RightHandPinky1', 'RightHandPinky2', 'RightHandPinky3',
        'LeftUpLeg', 'LeftLeg', 'LeftFoot', 'LeftToeBase',
        'RightUpLeg', 'RightLeg', 'RightFoot', 'RightToeBase',
      ],
    });

    addMessage('Body animations loaded (' + bodyAnim.mixer._actions.length + ' clips, floor state: ' + bodyAnim.floorState + ').', 'system');
  }
} catch (e) {
  console.warn('Body animation load failed:', e);
  addMessage('Body animations unavailable: ' + e.message, 'system');
}
```

Note: The `bodyAnimFrameId`/`tickBody` rAF loop is NOT started. TalkingHead's registered mixer handles `update(dt)`. The `bodyAnimFrameId` variable and the entire `tickBody` function are deleted.

- [ ] **Step 8: Update animation picker to use triggerGesture**

Replace the playAnim click handler:
```javascript
document.getElementById('playAnim').addEventListener('click', function() {
  var clipName = document.getElementById('animPicker').value;
  if (clipName && bodyAnim) {
    bodyAnim.triggerGesture(clipName);
    addMessage('Playing: ' + clipName, 'system');
  }
});
```

- [ ] **Step 9: Build and verify**

Run: `mvn --batch-mode install -DskipTests`

Then start the server and test manually in the browser:

1. Open the demo, click "Show 3D Avatar"
2. Verify body animations load and idle cycle plays
3. Type a message → verify avatar enters thinking state (neutral attentive pose)
4. When response arrives → verify avatar enters speaking state (hand gestures, stances)
5. When speech ends → verify avatar returns to idle
6. Click Mic → verify avatar enters listening state
7. Release Mic → verify thinking → speaking → idle
8. Use animation picker → verify gestures play with base ducking
9. Send a message that should trigger a gesture tag → verify gesture plays during speech
10. Check console for `[BodyAnim]` and `[Turn]` logs — no errors

- [ ] **Step 10: Commit**

```bash
git add speech-demo/src/main/resources/META-INF/resources/index.html
git commit -m "feat(#457): client-side floor states and new protocol handling (D3, D5, D13)

Wire Segment/TurnComplete protocol into floor state machine. Parse
8-byte binary audio headers. Register body mixer with TalkingHead
fork. Send PlaybackComplete and UserActivity. Delete legacy client-side
tag parsing and mood detection.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## References

- [decisions.md D3] — Protocol: segments carry text, visemes, cues; audio frames are identified
- [decisions.md D5] — Speaking-state activation follows the turn
- [decisions.md D11] — Animation arbitration: layered base and gesture, base ducks under gestures
- [decisions.md D12] — Performance vocabulary: the whole taxonomy in one resource behind an SPI
- [decisions.md D13] — Conversational floor states: idle/listening/thinking/speaking
- [decisions.md D14] — Channel ownership and playback host (speakAudio adoption deferred)
- [decisions.md D22] — External mixer registration in TalkingHead fork
- [avatar-performance.json] — pools already define idle/listening/thinking/speaking
- [body-animations.mjs] — current module being rewritten
- [index.html] — current demo page being updated
- [vendor/talkinghead.mjs] — TalkingHead fork with registerMixer, claimBones, lifecycle events
- [AvatarMessage.java, MessageCodec.java] — server protocol types and JSON encoding
- [TurnExecutor.java] — server-side Segment/TurnComplete emission
- [GitHub #457] — focal issue
- [GitHub #491] — TalkingHead fork with extension points (landed)
- [GitHub #499] — tracks removal of legacy Response/Phonemes emission

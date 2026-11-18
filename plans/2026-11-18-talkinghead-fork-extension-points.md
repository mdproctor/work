# TalkingHead Fork Extension Points — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #491 — Fork TalkingHead with composable extension points
**Issue group:** #491 (blocks #457 client-side batches)

**Goal:** Add four composable extension points to a casehubio fork of met4citizen/TalkingHead, enabling external animation systems to coexist with TalkingHead's internal pose, morph, and audio systems. Vendor the result into agency for #457.

**Architecture:** Four non-breaking additions to TalkingHead's `animate()` frame loop: (1) bone ownership via canonical names with claim/release API, (2) ordered morph layer registry running after `updateMorphTargets`, (3) external mixer registration with coordinated frame update, (4) EventTarget lifecycle events. All changes are in a single file (`modules/talkinghead.mjs`) — TalkingHead is a monolithic ES module.

**Tech Stack:** JavaScript ES modules, three.js (TalkingHead's internal dependency), browser-based testing via test HTML pages

## Global Constraints

- All changes must be **non-breaking** — existing TalkingHead users see no behaviour change
- TalkingHead is a single-file ES module (`modules/talkinghead.mjs`, ~6000 lines)
- No build toolchain — the source file IS the distribution artifact
- Testing is browser-based (TalkingHead has no test framework)
- The fork targets upstream contribution — code style must match met4citizen's conventions

## TalkingHead Frame Order (reference)

```
animate(t):
  1. animQueue processing (morph writes, task dispatch)
  2. Eye contact / head-move anims queued
  3. updatePoseBase(animClock)       ← BONE CLAIM FILTER HERE
  4. mixer.update(dt)                 (FBX animation)
  5. updatePoseDelta()               ← BONE CLAIM FILTER HERE
  6. Neck volume-based rotation      ← BONE CLAIM CHECK HERE
  7. Hips balance                    ← BONE CLAIM CHECK HERE
  8. dynamicbones.update(dt)
  9. opt.update(dt)                   (existing user hook)
 10. REGISTERED MIXERS UPDATE HERE   ← NEW
 11. updateMorphTargets(dt)           (layer 0)
 12. REGISTERED MORPH LAYERS HERE    ← NEW
 13. Camera, controls, render
```

---

## Batch 1: Bone Ownership API

After this batch: external systems can claim bones by canonical name. TalkingHead's pose system skips claimed bones. No visible change for existing users.

### Task 1: Canonical bone vocabulary, claim/release API, and frame loop filtering

**Files:**
- Modify: `modules/talkinghead.mjs` (constructor, updatePoseBase, updatePoseDelta, animate, new methods)
- Create: `test/test-bone-ownership.html` (browser verification)

**Interfaces:**
- Produces: `claimBones(names)`, `releaseBones(names)`, `claimBoneGroup(group)`, `releaseBoneGroup(group)`, `claimedBones` (read-only Set). Consumed by Task 3 (mixer registration with boneClaim).

- [ ] **Step 1: Create test page for bone ownership**

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Test: Bone Ownership</title>
<script type="importmap">
{ "imports": {
    "three": "https://cdn.jsdelivr.net/npm/three@0.180.0/build/three.module.js/+esm",
    "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.180.0/examples/jsm/",
    "talkinghead": "../modules/talkinghead.mjs"
} }
</script>
</head>
<body>
<div id="container" style="width:600px;height:400px"></div>
<pre id="log"></pre>
<script type="module">
import { TalkingHead } from "talkinghead";

const log = document.getElementById("log");
function print(msg) { log.textContent += msg + "\n"; console.log(msg); }

const head = new TalkingHead(document.getElementById("container"), {
  ttsEndpoint: null, lipsyncModules: ["en"],
  cameraView: "upper", cameraRotateEnable: true
});
await head.showAvatar({ url: "../test/fixtures/avatar.glb", body: "F", avatarMood: "neutral", lipsyncLang: "en" });
print("Avatar loaded.");

// Test 1: claimBones exists and accepts canonical names
print("\nTest 1: claimBones API");
try {
  head.claimBones(["Spine", "Spine1", "Spine2", "Hips"]);
  print("  PASS: claimBones accepted names");
} catch (e) {
  print("  FAIL: " + e.message);
}

// Test 2: claimedBones returns the claimed set
print("\nTest 2: claimedBones read");
const claimed = head.claimedBones;
print("  claimed: " + [...claimed].join(", "));
print("  has Spine: " + claimed.has("Spine"));
print("  has Head: " + claimed.has("Head"));
print("  " + (claimed.has("Spine") && !claimed.has("Head") ? "PASS" : "FAIL"));

// Test 3: Spine is not written by TalkingHead after claim
print("\nTest 3: Claimed bone not overwritten");
const spine = head.armature.getObjectByName("Spine");
if (spine) {
  const testQ = new THREE.Quaternion(0.1, 0.2, 0.3, 0.9).normalize();
  spine.quaternion.copy(testQ);
  // Let TalkingHead run a frame
  await new Promise(r => requestAnimationFrame(r));
  await new Promise(r => requestAnimationFrame(r));
  const diff = spine.quaternion.angleTo(testQ);
  print("  Quaternion drift after 2 frames: " + diff.toFixed(6) + " rad");
  print("  " + (diff < 0.001 ? "PASS: TalkingHead did not overwrite Spine" : "FAIL: Spine was overwritten"));
} else {
  print("  SKIP: Spine bone not found");
}

// Test 4: releaseBones restores TalkingHead control
print("\nTest 4: releaseBones");
head.releaseBones(["Spine"]);
print("  claimed after release: " + [...head.claimedBones].join(", "));
print("  " + (!head.claimedBones.has("Spine") ? "PASS" : "FAIL"));

// Test 5: claimBoneGroup
print("\nTest 5: claimBoneGroup('body')");
head.claimBoneGroup("body");
const bodyBones = [...head.claimedBones];
print("  claimed: " + bodyBones.length + " bones");
print("  includes Spine: " + head.claimedBones.has("Spine"));
print("  includes Hips: " + head.claimedBones.has("Hips"));
print("  excludes Head: " + !head.claimedBones.has("Head"));
print("  excludes Neck: " + !head.claimedBones.has("Neck"));
print("  " + (head.claimedBones.has("Spine") && !head.claimedBones.has("Head") ? "PASS" : "FAIL"));

// Test 6: releaseBoneGroup
head.releaseBoneGroup("body");
print("\nTest 6: releaseBoneGroup('body')");
print("  claimed after release: " + head.claimedBones.size);
print("  " + (head.claimedBones.size === 0 ? "PASS" : "FAIL"));

print("\n--- Bone ownership tests complete ---");
</script>
</body>
</html>
```

- [ ] **Step 2: Run test to verify it fails**

Open `test/test-bone-ownership.html` in browser. Expected: errors — `claimBones` is not a function.

- [ ] **Step 3: Add bone vocabulary, claim state, and API methods to TalkingHead**

In `modules/talkinghead.mjs`, add to the constructor (after `Object.assign(this.opt, opt || {})`) :

```javascript
// Bone ownership (D20) — canonical names, rig-agnostic
this._claimedBones = new Set();
```

Add the bone group definitions and API methods to the class body:

```javascript
// Canonical bone groups — expand to individual bone names
static BONE_GROUPS = {
  'head': ['Head', 'Neck', 'LeftEye', 'RightEye'],
  'body': ['Hips', 'Spine', 'Spine1', 'Spine2',
           'LeftShoulder', 'LeftArm', 'LeftForeArm',
           'RightShoulder', 'RightArm', 'RightForeArm',
           'LeftUpLeg', 'LeftLeg', 'LeftFoot', 'LeftToeBase',
           'RightUpLeg', 'RightLeg', 'RightFoot', 'RightToeBase'],
  'hands': ['LeftHand', 'RightHand',
            'LeftHandThumb1', 'LeftHandThumb2', 'LeftHandThumb3',
            'LeftHandIndex1', 'LeftHandIndex2', 'LeftHandIndex3',
            'LeftHandMiddle1', 'LeftHandMiddle2', 'LeftHandMiddle3',
            'LeftHandRing1', 'LeftHandRing2', 'LeftHandRing3',
            'LeftHandPinky1', 'LeftHandPinky2', 'LeftHandPinky3',
            'RightHandThumb1', 'RightHandThumb2', 'RightHandThumb3',
            'RightHandIndex1', 'RightHandIndex2', 'RightHandIndex3',
            'RightHandMiddle1', 'RightHandMiddle2', 'RightHandMiddle3',
            'RightHandRing1', 'RightHandRing2', 'RightHandRing3',
            'RightHandPinky1', 'RightHandPinky2', 'RightHandPinky3'],
  'arms': ['LeftShoulder', 'LeftArm', 'LeftForeArm', 'LeftHand',
           'RightShoulder', 'RightArm', 'RightForeArm', 'RightHand'],
  'legs': ['LeftUpLeg', 'LeftLeg', 'LeftFoot', 'LeftToeBase',
           'RightUpLeg', 'RightLeg', 'RightFoot', 'RightToeBase']
};

get claimedBones() {
  return new Set(this._claimedBones);
}

claimBones(names) {
  for (const name of names) this._claimedBones.add(name);
}

releaseBones(names) {
  for (const name of names) this._claimedBones.delete(name);
}

claimBoneGroup(group) {
  const bones = TalkingHead.BONE_GROUPS[group];
  if (!bones) throw new Error(`Unknown bone group: ${group}`);
  this.claimBones(bones);
}

releaseBoneGroup(group) {
  const bones = TalkingHead.BONE_GROUPS[group];
  if (!bones) throw new Error(`Unknown bone group: ${group}`);
  this.releaseBones(bones);
}

_isBoneClaimed(key) {
  const boneName = key.includes('.') ? key.split('.')[0] : key;
  return this._claimedBones.has(boneName);
}
```

- [ ] **Step 4: Filter claimed bones in updatePoseBase**

In `updatePoseBase`, add a claim check at the top of the loop:

```javascript
// Inside updatePoseBase, at the start of the for loop body:
for( const [key,val] of Object.entries(this.poseTarget.props) ) {
  if ( this._isBoneClaimed(key) ) continue;  // ← ADD THIS LINE
  const o = this.poseAvatar.props[key];
  // ... rest of loop unchanged
```

- [ ] **Step 5: Filter claimed bones in updatePoseDelta**

In `updatePoseDelta`, add after the zero-delta check:

```javascript
for( const [key,d] of Object.entries(this.poseDelta.props) ) {
  if ( d.x === 0 && d.y === 0 && d.z === 0 ) continue;
  if ( this._isBoneClaimed(key) ) continue;  // ← ADD THIS LINE
  // ... rest of loop unchanged
```

- [ ] **Step 6: Guard direct bone writes in animate**

In `animate()`, guard the direct neck and hips writes:

```javascript
// Neck volume-based rotation — guard with claim check
if ( !this._isBoneClaimed('Neck') && this.objectNeck ) {
  // ... existing neck quaternion code
}

// Hips balance — guard with claim check
if ( !this._isBoneClaimed('Hips') && this.objectHips ) {
  // ... existing hips position code
}
```

- [ ] **Step 7: Run test page, verify all pass**

Open `test/test-bone-ownership.html`. All 6 tests should pass.

- [ ] **Step 8: Commit**

```bash
git add modules/talkinghead.mjs test/test-bone-ownership.html
git commit -m "feat: bone ownership API — claimBones, releaseBones, bone groups (D20)"
```

---

## Batch 2: Morph Layer Registry and External Mixer Registration

After this batch: external systems can register morph layers and animation mixers that compose with TalkingHead's internal systems within a single coordinated frame loop.

### Task 2: Morph layer registry

**Files:**
- Modify: `modules/talkinghead.mjs` (constructor, animate, new methods)
- Create: `test/test-morph-layers.html`

**Interfaces:**
- Produces: `registerMorphLayer(name, { order, blend, channels, update })`, `unregisterMorphLayer(name)`. Consumed by #457 body-animations.mjs (viseme layer, gesture expression layer).

- [ ] **Step 1: Create test page for morph layers**

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Test: Morph Layer Registry</title>
<script type="importmap">
{ "imports": {
    "three": "https://cdn.jsdelivr.net/npm/three@0.180.0/build/three.module.js/+esm",
    "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.180.0/examples/jsm/",
    "talkinghead": "../modules/talkinghead.mjs"
} }
</script>
</head>
<body>
<div id="container" style="width:600px;height:400px"></div>
<pre id="log"></pre>
<script type="module">
import { TalkingHead } from "talkinghead";

const log = document.getElementById("log");
function print(msg) { log.textContent += msg + "\n"; console.log(msg); }

const head = new TalkingHead(document.getElementById("container"), {
  ttsEndpoint: null, lipsyncModules: ["en"],
  cameraView: "upper", cameraRotateEnable: true
});
await head.showAvatar({ url: "../test/fixtures/avatar.glb", body: "F", avatarMood: "neutral", lipsyncLang: "en" });

// Test 1: registerMorphLayer exists
print("Test 1: registerMorphLayer API");
let layerCalled = false;
try {
  head.registerMorphLayer("test-layer", {
    order: 10,
    blend: "additive",
    channels: ["mouthFrownLeft", "mouthFrownRight"],
    update: (dt, getInfluence, setInfluence) => {
      layerCalled = true;
      setInfluence("mouthFrownLeft", 0.5);
    }
  });
  print("  PASS: registerMorphLayer accepted");
} catch (e) {
  print("  FAIL: " + e.message);
}

// Test 2: Layer is called during animate
print("\nTest 2: Layer called in frame loop");
await new Promise(r => requestAnimationFrame(r));
await new Promise(r => requestAnimationFrame(r));
print("  layerCalled: " + layerCalled);
print("  " + (layerCalled ? "PASS" : "FAIL"));

// Test 3: Additive blend adds to existing value
print("\nTest 3: Additive blend");
// Find the head mesh and check mouthFrownLeft
const scene = head.scene;
let headMesh = null;
scene.traverse(o => {
  if (o.morphTargetDictionary && o.morphTargetDictionary["mouthFrownLeft"] !== undefined) {
    headMesh = o;
  }
});
if (headMesh) {
  const idx = headMesh.morphTargetDictionary["mouthFrownLeft"];
  const val = headMesh.morphTargetInfluences[idx];
  print("  mouthFrownLeft value: " + val.toFixed(3));
  print("  " + (val >= 0.4 ? "PASS: additive layer contributed" : "FAIL: value too low"));
} else {
  print("  SKIP: headMesh not found");
}

// Test 4: Layer ordering
print("\nTest 4: Layer ordering");
const callOrder = [];
head.registerMorphLayer("layer-20", {
  order: 20, blend: "additive", channels: [],
  update: () => callOrder.push(20)
});
head.registerMorphLayer("layer-5", {
  order: 5, blend: "additive", channels: [],
  update: () => callOrder.push(5)
});
callOrder.length = 0;
await new Promise(r => requestAnimationFrame(r));
await new Promise(r => requestAnimationFrame(r));
print("  call order: " + callOrder.join(", "));
print("  " + (callOrder[0] === 5 && callOrder[1] === 10 && callOrder[2] === 20 ? "PASS" : "FAIL (expected 5, 10, 20)"));

// Test 5: unregisterMorphLayer
print("\nTest 5: unregisterMorphLayer");
head.unregisterMorphLayer("test-layer");
head.unregisterMorphLayer("layer-20");
head.unregisterMorphLayer("layer-5");
callOrder.length = 0;
layerCalled = false;
await new Promise(r => requestAnimationFrame(r));
await new Promise(r => requestAnimationFrame(r));
print("  layerCalled after unregister: " + layerCalled);
print("  " + (!layerCalled ? "PASS" : "FAIL"));

print("\n--- Morph layer tests complete ---");
</script>
</body>
</html>
```

- [ ] **Step 2: Run test to verify it fails**

Open in browser. Expected: `registerMorphLayer is not a function`.

- [ ] **Step 3: Add morph layer registry to TalkingHead**

In the constructor, after `this._claimedBones = new Set()`:

```javascript
// Morph layer registry (D21) — ordered layers with blend modes
this._morphLayers = []; // sorted by order
```

Add the API methods:

```javascript
registerMorphLayer(name, { order = 0, blend = 'additive', channels = [], update }) {
  if (typeof update !== 'function') throw new Error('registerMorphLayer: update must be a function');
  this._morphLayers.push({ name, order, blend, channels, update });
  this._morphLayers.sort((a, b) => a.order - b.order);
}

unregisterMorphLayer(name) {
  this._morphLayers = this._morphLayers.filter(l => l.name !== name);
}
```

- [ ] **Step 4: Wire morph layers into animate(), after updateMorphTargets**

In `animate()`, after the `this.updateMorphTargets(dt)` call, add:

```javascript
// Run registered morph layers (D21)
if ( this._morphLayers.length > 0 ) {
  this._runMorphLayers(dt);
}
```

Add the `_runMorphLayers` method:

```javascript
_runMorphLayers(dt) {
  // Build mesh lookup once per frame
  if (!this._morphMeshes) {
    this._morphMeshes = [];
    this.armature.traverse(o => {
      if (o.morphTargetDictionary) {
        this._morphMeshes.push(o);
      }
    });
  }

  for (const layer of this._morphLayers) {
    const getInfluence = (name) => {
      for (const mesh of this._morphMeshes) {
        const idx = mesh.morphTargetDictionary[name];
        if (idx !== undefined) return mesh.morphTargetInfluences[idx];
      }
      return 0;
    };

    const setInfluence = (name, value) => {
      for (const mesh of this._morphMeshes) {
        const idx = mesh.morphTargetDictionary[name];
        if (idx === undefined) continue;
        if (layer.blend === 'replace') {
          mesh.morphTargetInfluences[idx] = Math.min(1, Math.max(0, value));
        } else if (layer.blend === 'additive') {
          mesh.morphTargetInfluences[idx] = Math.min(1, mesh.morphTargetInfluences[idx] + value);
        } else if (layer.blend === 'max') {
          mesh.morphTargetInfluences[idx] = Math.min(1, Math.max(mesh.morphTargetInfluences[idx], value));
        }
      }
    };

    layer.update(dt, getInfluence, setInfluence);
  }
}
```

- [ ] **Step 5: Run test page, verify all pass**

- [ ] **Step 6: Commit**

```bash
git add modules/talkinghead.mjs test/test-morph-layers.html
git commit -m "feat: morph layer registry — registerMorphLayer with ordered blend modes (D21)"
```

### Task 3: External mixer registration

**Files:**
- Modify: `modules/talkinghead.mjs` (constructor, animate, new methods)
- Create: `test/test-mixer-registration.html`

**Interfaces:**
- Consumes: `claimBones` (Task 1) for `boneClaim` option
- Produces: `registerMixer(mixer, { priority, boneClaim })`, `unregisterMixer(mixer)`. Consumed by #457 BodyAnimationSystem.

- [ ] **Step 1: Create test page for mixer registration**

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Test: Mixer Registration</title>
<script type="importmap">
{ "imports": {
    "three": "https://cdn.jsdelivr.net/npm/three@0.180.0/build/three.module.js/+esm",
    "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.180.0/examples/jsm/",
    "talkinghead": "../modules/talkinghead.mjs"
} }
</script>
</head>
<body>
<div id="container" style="width:600px;height:400px"></div>
<pre id="log"></pre>
<script type="module">
import * as THREE from "three";
import { TalkingHead } from "talkinghead";

const log = document.getElementById("log");
function print(msg) { log.textContent += msg + "\n"; console.log(msg); }

const head = new TalkingHead(document.getElementById("container"), {
  ttsEndpoint: null, lipsyncModules: ["en"],
  cameraView: "upper", cameraRotateEnable: true
});
await head.showAvatar({ url: "../test/fixtures/avatar.glb", body: "F", avatarMood: "neutral", lipsyncLang: "en" });

// Test 1: registerMixer exists
print("Test 1: registerMixer API");
const testMixer = new THREE.AnimationMixer(head.armature);
let mixerUpdated = false;
const origUpdate = testMixer.update.bind(testMixer);
testMixer.update = (dt) => { mixerUpdated = true; origUpdate(dt); };

try {
  head.registerMixer(testMixer, {
    priority: 10,
    boneClaim: ["Spine", "Spine1", "Spine2", "Hips"]
  });
  print("  PASS: registerMixer accepted");
} catch (e) {
  print("  FAIL: " + e.message);
}

// Test 2: Bones are claimed
print("\nTest 2: boneClaim auto-claims bones");
print("  Spine claimed: " + head.claimedBones.has("Spine"));
print("  " + (head.claimedBones.has("Spine") ? "PASS" : "FAIL"));

// Test 3: Mixer is called in frame loop
print("\nTest 3: Mixer updated in frame loop");
await new Promise(r => requestAnimationFrame(r));
await new Promise(r => requestAnimationFrame(r));
print("  mixerUpdated: " + mixerUpdated);
print("  " + (mixerUpdated ? "PASS" : "FAIL"));

// Test 4: unregisterMixer releases bones
print("\nTest 4: unregisterMixer");
head.unregisterMixer(testMixer);
print("  Spine claimed after unregister: " + head.claimedBones.has("Spine"));
print("  " + (!head.claimedBones.has("Spine") ? "PASS" : "FAIL"));

print("\n--- Mixer registration tests complete ---");
</script>
</body>
</html>
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Add mixer registration to TalkingHead**

In the constructor:

```javascript
// External mixer registry (D22) — coordinated frame updates
this._registeredMixers = []; // sorted by priority
```

API methods:

```javascript
registerMixer(mixer, { priority = 0, boneClaim = [] } = {}) {
  if (boneClaim.length > 0) this.claimBones(boneClaim);
  this._registeredMixers.push({ mixer, priority, boneClaim });
  this._registeredMixers.sort((a, b) => a.priority - b.priority);
}

unregisterMixer(mixer) {
  const entry = this._registeredMixers.find(e => e.mixer === mixer);
  if (entry) {
    if (entry.boneClaim.length > 0) this.releaseBones(entry.boneClaim);
    this._registeredMixers = this._registeredMixers.filter(e => e.mixer !== mixer);
  }
}
```

- [ ] **Step 4: Wire registered mixers into animate(), between opt.update and updateMorphTargets**

In `animate()`, after `if ( this.opt.update ) { this.opt.update(dt); }` and before `this.updateMorphTargets(dt)`:

```javascript
// Update registered external mixers (D22)
for ( const entry of this._registeredMixers ) {
  entry.mixer.update( dt / 1000 );
}
```

- [ ] **Step 5: Run test page, verify all pass**

- [ ] **Step 6: Commit**

```bash
git add modules/talkinghead.mjs test/test-mixer-registration.html
git commit -m "feat: external mixer registration — registerMixer with boneClaim (D22)"
```

---

## Batch 3: Lifecycle Events, Vendoring, and Integration Verification

After this batch: TalkingHead dispatches lifecycle events. The vendored build is in agency and verified with the demo.

### Task 4: Lifecycle events via EventTarget

**Files:**
- Modify: `modules/talkinghead.mjs` (class declaration, state transitions)
- Create: `test/test-lifecycle-events.html`

**Interfaces:**
- Produces: `addEventListener('statechange', ...)`, `addEventListener('moodchange', ...)`, `addEventListener('audiostart', ...)`, `addEventListener('audioend', ...)`. Consumed by #457 client for floor state observation.

- [ ] **Step 1: Create test page for lifecycle events**

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Test: Lifecycle Events</title>
<script type="importmap">
{ "imports": {
    "three": "https://cdn.jsdelivr.net/npm/three@0.180.0/build/three.module.js/+esm",
    "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.180.0/examples/jsm/",
    "talkinghead": "../modules/talkinghead.mjs"
} }
</script>
</head>
<body>
<div id="container" style="width:600px;height:400px"></div>
<pre id="log"></pre>
<script type="module">
import { TalkingHead } from "talkinghead";

const log = document.getElementById("log");
function print(msg) { log.textContent += msg + "\n"; console.log(msg); }

const head = new TalkingHead(document.getElementById("container"), {
  ttsEndpoint: null, lipsyncModules: ["en"],
  cameraView: "upper", cameraRotateEnable: true
});
await head.showAvatar({ url: "../test/fixtures/avatar.glb", body: "F", avatarMood: "neutral", lipsyncLang: "en" });

// Test 1: addEventListener exists (EventTarget)
print("Test 1: EventTarget mixin");
print("  addEventListener: " + (typeof head.addEventListener === "function"));
print("  " + (typeof head.addEventListener === "function" ? "PASS" : "FAIL"));

// Test 2: moodchange event
print("\nTest 2: moodchange event");
let moodEvent = null;
head.addEventListener("moodchange", e => { moodEvent = e.detail; });
head.setMood("happy");
await new Promise(r => setTimeout(r, 100));
print("  event fired: " + (moodEvent !== null));
if (moodEvent) print("  mood: " + moodEvent.mood + ", previous: " + moodEvent.previous);
print("  " + (moodEvent && moodEvent.mood === "happy" ? "PASS" : "FAIL"));

// Test 3: statechange event
print("\nTest 3: statechange event");
let stateEvents = [];
head.addEventListener("statechange", e => { stateEvents.push(e.detail); });
// Speaking triggers a state change — use speakText as the simplest trigger
head.speakText("Hello");
await new Promise(r => setTimeout(r, 500));
print("  statechange events: " + stateEvents.length);
if (stateEvents.length > 0) print("  first: " + JSON.stringify(stateEvents[0]));
print("  " + (stateEvents.length > 0 ? "PASS" : "FAIL (may need TTS endpoint for speech)"));

print("\n--- Lifecycle event tests complete ---");
</script>
</body>
</html>
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Add EventTarget mixin to TalkingHead**

TalkingHead cannot extend `EventTarget` directly (it doesn't extend anything, and adding it would be a breaking change for `instanceof` checks). Instead, mix it in via delegation:

In the constructor:

```javascript
// Lifecycle events (D23) — EventTarget delegation
this._eventTarget = new EventTarget();
```

Add delegation methods:

```javascript
addEventListener(type, listener, options) {
  this._eventTarget.addEventListener(type, listener, options);
}

removeEventListener(type, listener, options) {
  this._eventTarget.removeEventListener(type, listener, options);
}

dispatchEvent(event) {
  return this._eventTarget.dispatchEvent(event);
}
```

- [ ] **Step 4: Dispatch statechange events**

Find every assignment to `this.stateName` in the source. Before each assignment, capture the previous value and dispatch after:

```javascript
// Wrap stateName assignments — replace each `this.stateName = 'xxx'` with:
const _prevState = this.stateName;
this.stateName = 'xxx';
if (_prevState !== this.stateName) {
  this.dispatchEvent(new CustomEvent('statechange', {
    detail: { from: _prevState, to: this.stateName }
  }));
}
```

The assignments to find: `this.stateName = 'idle'` and `this.stateName = 'speaking'` (the two states confirmed from the source analysis). Also check for `'listening'` and `'animating'` which may be set elsewhere.

- [ ] **Step 5: Dispatch moodchange events**

In `setMood(s)`:

```javascript
setMood(s) {
  const previous = this.moodName;
  // ... existing setMood body ...
  if (this.moodName !== previous) {
    this.dispatchEvent(new CustomEvent('moodchange', {
      detail: { mood: this.moodName, previous }
    }));
  }
}
```

- [ ] **Step 6: Dispatch audiostart/audioend events**

In `playAudio()` (or wherever audio playback starts):

```javascript
this.dispatchEvent(new CustomEvent('audiostart', { detail: {} }));
```

And when audio ends:

```javascript
this.dispatchEvent(new CustomEvent('audioend', { detail: {} }));
```

- [ ] **Step 7: Run test page, verify pass**

- [ ] **Step 8: Commit**

```bash
git add modules/talkinghead.mjs test/test-lifecycle-events.html
git commit -m "feat: lifecycle events via EventTarget — statechange, moodchange, audiostart/end (D23)"
```

### Task 5: Vendor into agency and verify with demo

**Files:**
- Create: `speech-demo/src/main/resources/META-INF/resources/vendor/talkinghead.mjs` (copy from fork)
- Modify: `speech-demo/src/main/resources/META-INF/resources/index.html` (import map)
- Test: manual browser test with demo

**Interfaces:**
- Consumes: TalkingHead fork with all four extension points (Tasks 1–4)
- Produces: vendored module in agency, verified against the existing demo

- [ ] **Step 1: Copy the built module from the fork into agency**

```bash
cp /path/to/casehubio-TalkingHead/modules/talkinghead.mjs \
   speech-demo/src/main/resources/META-INF/resources/vendor/talkinghead.mjs
```

- [ ] **Step 2: Update the import map in index.html**

Change line 11:
```
"talkinghead": "https://cdn.jsdelivr.net/gh/met4citizen/TalkingHead@1.8/modules/talkinghead.mjs"
```
to:
```
"talkinghead": "./vendor/talkinghead.mjs"
```

- [ ] **Step 3: Verify the demo still works**

```bash
mvn --batch-mode quarkus:dev -pl speech-demo
```

Open `http://localhost:8080`. Verify:
- Avatar loads and renders normally
- TalkingHead's idle animation (head, eyes) works as before
- Body animations load and play (animation picker)
- Speech works (text input → LLM → TTS → audio)
- No console errors from the vendored module

- [ ] **Step 4: Smoke-test the new APIs from browser console**

```javascript
// In browser console:
_head.claimBones(["Spine"]); // Should work
_head.claimedBones; // Should show Set(1) {"Spine"}
_head.releaseBones(["Spine"]);
_head.registerMorphLayer("test", { order: 1, blend: "additive", channels: [], update: () => {} });
_head.unregisterMorphLayer("test");
_head.addEventListener("moodchange", e => console.log("mood:", e.detail));
_head.setMood("happy"); // Should log mood event
```

- [ ] **Step 5: Commit**

```bash
git add speech-demo/src/main/resources/META-INF/resources/vendor/talkinghead.mjs
git add speech-demo/src/main/resources/META-INF/resources/index.html
git commit -m "feat(#491): vendor TalkingHead fork with extension points (D19)"
```

---

## References

- [decisions.md D18–D24] — TalkingHead fork decisions
- [2026-11-18-m2m-animation-integration-design.md] — parent design spec
- [TalkingHead source: animate()] — frame order analysis (updatePoseBase → mixer → updatePoseDelta → dynamicbones → opt.update → updateMorphTargets → render)
- [TalkingHead source: updatePoseBase()] — poseTarget.props iteration, poseAvatar.props writes
- [TalkingHead source: updatePoseDelta()] — poseDelta.props iteration with additive writes
- [TalkingHead source: updateMorphTargets()] — morph writes (visemes, blink, mood, eyes, body morphs)
- [TalkingHead source: speakAudio()] — markers/mtimes queued as 'function' tasks in animQueue
- [TalkingHead README] — public API surface, speakAudio signature, configuration options
- [GitHub #457] — parent integration issue
- [GitHub #491] — this fork issue

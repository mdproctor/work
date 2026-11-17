# Ideas — casehub-blocks

## 2026-11-17 — Contribute body animation system to TalkingHead upstream

**Priority:** medium
**Status:** active

Contribute the `BodyAnimationSystem` module, M2M CC0 animation library (68 clips), and `[gesture:tag]` protocol back to met4citizen/TalkingHead as a PR. Would add: systematic body gesture taxonomy with weighted pools, LLM gesture tag parsing, speaking/idle pool cycling with crossfade, and head-bone exclusion for composing with TalkingHead's facial system. The M2M clips are CC0 so no licensing issue; code would need MIT compatibility.

**Context:** Arose during #457 design. TalkingHead supports full-body avatars and Mixamo FBX animations but has no systematic gesture system. Our implementation fills that gap cleanly. Shape #457 code to be contribution-friendly (clean module boundaries, no CaseHub coupling).

**Promoted to:**

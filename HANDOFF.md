# HANDOFF — casehub-neocortex

## Active Branch

`issue-1136-speech-pipeline-integration` — scaffolded with empty plan commit, ready for implementation.

## Issue: casehubio/neocortex#1136

Three additions enabling richer avatar behaviour when cognition is configured. None blocking — agency#457 ships with degraded-but-correct interim for each.

### 1. Caller-supplied prompt sections for proactive speech

**Where:** `cognition/.../prompt/ProactiveSpeechSupport.java` (30 lines)

`evaluateProactive(channelContext)` builds its own prompt. The speech pipeline can't inject gesture instructions. Fix: accept `List<CognitionPromptRenderer>` that gets appended to the proactive prompt.

Thread sections through `InnerLifeOrchestrator.tick()` → `doTick()` into the LLM call. The system prompt is a constant `SYSTEM_PROMPT` string — additional sections need appending.

**Key files:**
- `cognition-api/.../CognitionPromptRenderer.java` — the SPI (render + blockTag + sectionTier)
- `cognition/.../prompt/ProactiveSpeechSupport.java` — entry point
- `cognition/.../innerlife/InnerLifeOrchestrator.java` — `tick()` and `doTick()`

### 2. Non-verbal proactive outcome (InnerLifeTick.NonVerbal)

**Where:** `cognition-api/.../innerlife/InnerLifeTick.java`

Currently sealed: `Silent` + `Initiated`. Add `NonVerbal(String tag)` for attention bids via gesture.

`InnerLifeOrchestrator.doTick()` decides Initiated/Silent based on LLM score + drive state. Non-verbal path needs drive-based decision logic (e.g. high affiliation drive + silence duration → knock gesture).

`ProactiveSpeechSupport.evaluateProactive()` switch needs the new case.

### 3. Delivery feedback for dropped proactive turns

**Where:** `cognition/.../innerlife/InnerLifeOrchestrator.java`

New method: `reportDropped(agentId, turnId, Instant userActivityAt)`. When TurnExecutor drops a stale proactive turn, this tells neocortex so inner-life state doesn't assume delivery.

`AgentState` tracks `consecutiveInitiationsWithoutResponse` — a dropped turn should decrement the initiation count or be treated as not-sent.

### Design reference

Full design in agency `specs/issue-457-integrate-m2m-animation-library/decisions.md` D7, D8, D10, D17.

## Queue

| # | Issue | Title | Scale | Complexity | Status |
|---|-------|-------|-------|------------|--------|
| 1 | #1119 | Memory hygiene parameter adaptation via consolidation phase | M | Med | queued |
| 2 | #1132 | Implement LivedDispositionSource | S | Low | queued |
| 3 | #1135 | Extend bridge to compose AMPD facets | S | Med | queued |
| 4 | #1136 | Speech pipeline integration | M | Med | **active** |

## This Session

- Reviewed eidos design for `DispositionProfileStore` — identified double-composition risk in `EffectiveCognitiveDefaults` and lossy projection of three-channel facet model
- Eidos revised to `LivedDispositionSource` read-only SPI with authored-as-parameter signature (eidos#245, landed)
- Filed neocortex#1132 (implement LivedDispositionSource) and #1135 (facet composition extension)
- Closed eidos#235 as superseded by #245
- Fixed stale references on #1135
- Cleaned queue: removed 11 stale done items (#882-#888, #1121, #1120, #1110, #1111)
- Scaffolded #1136

## Cross-Repo State

| Repo | Issue | Status |
|------|-------|--------|
| eidos | #245 | **Landed** — LivedDispositionSource SPI + findEffective() |
| eidos | #235 | **Closed** — superseded by #245 |
| eidos | #243 | Parked — convenience model assembler not needed |
| neocortex | #1132 | **Open** — implement LivedDispositionSource (unblocked) |
| neocortex | #1135 | **Open** — extend bridge for AMPD facets (depends on #1132) |
| neocortex | #1136 | **Open, active** — speech pipeline integration |
| agency | #457 | **Open** — speech pipeline (ships without #1136 changes) |

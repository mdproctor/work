# HANDOFF — casehub-neocortex

## Active Branch

`main` — no active feature branch. Queue has 6 items pending.

## Last Closed: casehubio/neocortex#1136

Speech pipeline integration — landed as `0d132ac7` on main. Three additions to cognition APIs enabling richer avatar behaviour:

1. **Caller-supplied prompt sections** — `InnerLifeOrchestrator.tick()` and `ProactiveSpeechSupport.evaluateProactive()` accept `List<CognitionPromptRenderer>`, rendered and appended to the proactive system prompt via `buildSystemPrompt(DriveProfile, sections)`.

2. **InnerLifeTick.NonVerbal** — new sealed variant `NonVerbal(String tag, double motivationScore)`. Drive-based non-verbal attention bids triggered by affiliation drive + silence duration, evaluated before the LLM call. Configured via `NonVerbalConfig` (silenceThreshold, affiliationDriveThreshold, defaultTag).

3. **reportDropped** — `InnerLifeOrchestrator.reportDropped(agentId, tenantId, userActivityAt)` adjusts initiation counters and updates `lastActivityTimestamp` when a proactive turn is dropped at dequeue.

`ProactiveSpeechSupport.evaluateProactive()` now returns `InnerLifeTick` instead of `@Nullable String`.

### Review findings fixed in-session

- Redundant `driveOrchestrator.currentDrives()` call in `buildSystemPrompt` — now takes `DriveProfile` directly
- `reportDropped` updates `lastActivityTimestamp` from `userActivityAt` to prevent stale NonVerbal bids
- FQN usage in `evaluateNonVerbal` cleaned up with imports
- Backward-compat 6-arg `InnerLifeConfig` constructor removed (no callers)

## Queue

| # | Issue | Title | Scale | Complexity | Status |
|---|-------|-------|-------|------------|--------|
| 1 | #1119 | Memory hygiene parameter adaptation via consolidation phase | M | Med | queued |
| 2 | #1132 | Implement LivedDispositionSource | S | Low | queued |
| 3 | #1135 | Extend bridge to compose AMPD facets | S | Med | queued |
| 4 | #1139 | Document NonVerbalConfig in pipeline defaults | XS | Low | queued |
| 5 | #1140 | Update CLAUDE.md for speech pipeline types | XS | Low | queued |
| 6 | #1141 | Fix DispositionSignalWriterTest broken by eidos API | XS | Low | queued |

## Notes for Next Session

- **Pre-existing test-compile break**: `DispositionSignalWriterTest` fails because `InMemorySignalStore` doesn't implement `activationWeights(String, String)` added to eidos `DispositionSignalStore` SPI. Filed as #1141. Blocks `test-compile` for the entire `cognition` module — fix this first if working in cognition.
- **agency#457** ships without #1136 changes (degraded-but-correct interim). The neocortex-side work is now landed — agency integration is agency's concern.
- **Unpublished commits**: 10 commits on main that haven't been pushed to remote.

## Cross-Repo State

| Repo | Issue | Status |
|------|-------|--------|
| eidos | #245 | **Landed** — LivedDispositionSource SPI + findEffective() |
| eidos | #235 | **Closed** — superseded by #245 |
| neocortex | #1132 | **Open** — implement LivedDispositionSource (unblocked) |
| neocortex | #1135 | **Open** — extend bridge for AMPD facets (depends on #1132) |
| neocortex | #1136 | **Closed** — landed as 0d132ac7 |
| neocortex | #1139 | **Open** — document NonVerbalConfig in pipeline defaults |
| neocortex | #1140 | **Open** — update CLAUDE.md for speech pipeline types |
| neocortex | #1141 | **Open** — fix DispositionSignalWriterTest |
| agency | #457 | **Open** — speech pipeline (ships without #1136 changes) |

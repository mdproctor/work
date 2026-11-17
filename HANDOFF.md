# HANDOFF — casehub-agency

## Active Work — Epic #475: Swarm coordination infrastructure cleanup

### Current state — 3 issues on stacked branches, ready for work-end

**Branches (stacked on main):**
- `issue-466-rename-swarm-degraded-event` — `SwarmDegradedEvent` → `DegradedEvent`, moved to `orchestration.degradation`
- `issue-472-separate-degradation-absorption` — Added `collapsed` flag to `DegradedEvent`, split `onDegradation` logic
- `issue-473-progress-broadcast-absorption` — `ProgressUpdated` broadcast routes through `resolveAbsorber`

**All tests pass:** 519 agency-core tests, zero failures.

**Blog:** `docs/blog/2026-11-17-mdp01-not-every-stumble-is-a-fall.md` — explains graduated response model.

### Queue — 3 remaining items

| # | Title | Scale | Complexity | Status |
|---|-------|-------|------------|--------|
| #404 | LatencyDegradationTrigger: external circuit breaker integration | S | Med | Next |
| #444 | Wire DecisionLoopEvent metrics through ExecutionEventListener | M | Med | Partially done (2 of 3 items on main) |
| #468 | Migrate existing topologies to CascadeHandler | M | Med | Open |

### Filed this session

| # | Title | Scale | Complexity |
|---|-------|-------|------------|
| #478 | Rename `ExecutionEventListener.onSwarmDegraded` to `onDegraded` | XS | Low |
| #479 | Add collapse emitter policy to `DegradationAwareSummariser` | S | Med |
| #480 | Propagate `collapsed` flag to `DecisionLoopEvent.Degraded` for telemetry | XS | Low |

### Pre-existing build issue

`agentic-yaml` module has a compilation error — `CognitionCompiler.java:58` constructor mismatch with neocortex `DriveConfig`. Not related to this work. `agency-core` builds and tests clean.

### Key design decision — Degradation vs collapse

`DegradedEvent` now has two independent signals:
- `fallbackActive` → agent switched to heuristic (loop still functions)
- `collapsed` → agent structurally absent, parent must absorb scope

No production emitter currently sends `collapsed=true` — the mechanism exists but is dormant. Issue #479 tracks the emitter policy.

### Code review findings addressed

- Renamed two misleading test names (used "collapse" but tested degradation)
- Thread safety: pre-existing characteristic — `ConcurrentHashMap.newKeySet()` per-operation atomic, two-set compound in `onDegradation` not transactional (unchanged from before)
- Other topologies (`CascadeHandler`, `CentralisedTopology`, etc.) don't need `collapsed` — they only track `degradedLoops` for cascade threshold

---

## Recently Landed — Orchestration hardening (2026-11-17)

| # | SHA | Summary |
|---|-----|---------|
| #477 | `e8b0ab41` | Validate `parentMap` entries reference known `agentRoles` in `HierarchicalTopology` |
| #474 | `a4651fd0` | Constructor-time acyclicity validation in `HierarchicalTopology.parentMap` |
| #465 | `87c45db1` | `TokenRecordable` interface replaces instanceof cascade in `CentralisedTopology` |
| #467 | `feb565e7` | `modelId` parameter on `DecisionLoopFactory` (was hardcoded `"unknown"`) |
| #464 | `752c9f6d` | Per-loop bridge bus emits `ActionDispatched` via `loopEventSink` |

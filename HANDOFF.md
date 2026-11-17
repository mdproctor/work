# HANDOFF — casehub-agency

## Epic #475: Swarm coordination infrastructure — topology cleanup and enhancement

### Session summary (2026-11-17)

Landed 4 issues, filed 3 follow-ups:

| # | SHA | Summary |
|---|-----|---------|
| #477 | `e8b0ab41` | Validate `parentMap` entries reference known `agentRoles` |
| #466 | `d23be111` | Rename `SwarmDegradedEvent` → `DegradedEvent`, move to `orchestration.degradation` |
| #472 | `0dacc5f1` | Separate `degradedLoops` (cascade threshold) from `collapsedLoops` (scope absorption) |
| #473 | `ec01eeda` | `ProgressUpdated` broadcast routes through `resolveAbsorber` |

**Blog:** `docs/blog/2026-11-17-mdp01-not-every-stumble-is-a-fall.md`

### Queue — 6 remaining items

| # | Title | Scale | Complexity | Notes |
|---|-------|-------|------------|-------|
| #404 | LatencyDegradationTrigger: external circuit breaker integration | S | Med | Independent |
| #444 | Wire DecisionLoopEvent metrics through ExecutionEventListener | M | Med | 2 of 3 items already on main |
| #468 | Migrate existing topologies to CascadeHandler | M | Med | Independent |
| #478 | Rename `ExecutionEventListener.onSwarmDegraded` to `onDegraded` | XS | Low | Consumer-facing API — verify engine doesn't override |
| #480 | Propagate `collapsed` flag to `DecisionLoopEvent.Degraded` | XS | Low | Telemetry gap |
| #479 | Add collapse emitter policy to `DegradationAwareSummariser` | S | Med | Mechanism exists but dormant — no emitter sends `collapsed=true` |

### Key design context for next session

**Degradation vs collapse:** `DegradedEvent` now has two independent signals:
- `fallbackActive` → agent switched to heuristic (loop still functions) → `degradedLoops`
- `collapsed` → agent structurally absent, parent absorbs scope → `collapsedLoops`

Backward-compat 3-arg constructor defaults `collapsed=false` — existing emitters unchanged.

**Thread safety:** `ConcurrentHashMap.newKeySet()` per-operation atomic, two-set compound in `onDegradation` not transactional (pre-existing characteristic, not introduced by this work).

### Pre-existing build issue

`agentic-yaml` module: `CognitionCompiler.java:58` constructor mismatch with neocortex `DriveConfig`. Unrelated. `agency-core` builds and tests clean (519 tests).

### Unpublished

17 commits on main not yet pushed to remote. Publish with `canonical_sync.py publish`.

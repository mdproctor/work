# HANDOFF — casehub-neocortex (slot 230)

## Session summary

Completed #1252 (per-type confidence decay) and #1209 (import standardisation). Closed #1243 (SE8 review residuals — all children done). Closed stale GitHub issues #1210, #1211. Queue was empty; repopulated with 4 new items.

## Queue (4 open items)

| # | Title | Scale | Complexity |
|---|-------|-------|------------|
| #1076 | PID-5 facet extension for situational attractor profiles | S | Med |
| #1065 | RelationalSchema roles carry no hierarchy | S | Med |
| #1091 | MindMapQuery property-based filtering | M | Med |
| #1102 | Valenced relationship and goal-outcome signals | M | Med |

Recommended order: start with #1076 (assessment, informs later work) or #1065 (self-contained, well-defined).

## Notes for next session

- **Java target is 21**, not 25 — do not use ScopedValue/JEP 506. #1162 is blocked on Java target upgrade.
- Pre-existing test failure: `EmotionMappingIntegrationTest.shameWithUrgencyHasElevatedArousalAboveBaseline` — from commit `2aa181532`.
- Workspace and project repos have diverged on main. Prior sessions resolved with `start_anyway_from_the_stale_base`.
- Two stale deferred items in queue (both resolved). No `remove-deferred` command exists.
- `casehubio/examples` push fails due to Vault Radar pre-push hook — not related to neocortex work.
- Both slots (229, 230) verified: all work landed to canonical repos. Old branches are all stamped closed.

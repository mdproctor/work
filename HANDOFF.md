# HANDOFF — casehub-neocortex

## Last Session

Completed #1208 (final item in original 50-item queue). Started #1209 — discovered the approach needs rethinking.

**#1208** — Standardised fully qualified imports in `InnerLifeOrchestratorTest`. 11 FQ class references replaced with proper imports. Committed, landed, published.

**#1209 — attempted, reverted.** The `optimize_imports` approach fails because many simple class names (`Set`, `Map`, `Duration`, `Instant`, `Uni`) are ambiguous on this classpath — multiple classes share the same simple name. IntelliJ's `optimize_imports` can't auto-resolve them, leaving broken imports. 32 of 53 files failed to compile. All changes reverted. Issue updated with findings and recommended approach (manual import insertion per file, not auto-resolve).

**Additional findings:**
- Full scan: 740 FQ usages across 61 files (not 274/47 as initially estimated)
- Scale updated from M/Low to L/Med on #1209
- 6 parallel IntelliJ agents overwhelmed the MCP server — caused disconnects. Sequential processing works but optimize_imports still can't resolve ambiguities.

**Blog:** `blog/2026-11-23-mdp03-the-eleven-that-hid-behind-one.md` — partial style fixes and false consistency.

**Filed:** #1209 — FQ import cleanup across cognition test files.

## Queue State

#1209 active (not started — approach needs rethinking). #1167 queued after. 5 deferred items cleaned up (3 closed, 2 re-queued/done).

## Pre-existing compilation issue

`AgentDescriptor` constructor mismatch in `SituationalPersonalityIntegrationTest` and `SituationalPersonalityParticipantTest` — predates this session, not caused by FQ work.

## Immediate Next Step

1. #1209 needs a Python script approach that reads each file, identifies FQ usages, computes correct imports, inserts them, then replaces FQ names — the hook blocking Python on Java files will need a workaround (temporary exemption or a dedicated tooling script)
2. Alternatively: use IntelliJ's "Add import" intention interactively per file, or a custom IntelliJ plugin action

## References

- Blog: `blog/2026-11-23-mdp03-the-eleven-that-hid-behind-one.md`
- Issue comment: https://github.com/casehubio/neocortex/issues/1209#issuecomment-7099035555

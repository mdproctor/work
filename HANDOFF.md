# HANDOFF — casehub-neocortex

## Last Session

Designed and implemented #1212 (direct CAPS settling pathway for dissonance). Evaluated whether a direct CAPS settling pathway produces richer behavioural effects than the indirect prompt→experience→graduation pathway — concluded yes based on constraint-satisfaction fidelity (Shultz & Lepper 1996), and implemented.

1. **DissonanceActivationSource SPI** (`cognition-api`) — `@FunctionalInterface` with `TensionActivation` record carrying `capsActivationsA`, `capsActivationsB`, and tension `strength`.
2. **DissonanceDetectionPhase** (`cognition`) — moved from `@Priority(25)` to `@Priority(17)`, implements `DissonanceActivationSource`, extended `TensionSnapshot` with CAPS activation maps, `parseCapsActivations()` for JSON→Map conversion.
3. **BehavioralSynthesisPhase** (`mindmap-intelligence`) — `mergeDissonanceActivations()` reads SPI, scales by tension strength, merges via `Math::max`. Both direct and indirect paths coexist.

**Blog:** `blog/2026-11-23-mdp02-the-settler-that-finally-heard-the-argument.md`

**Filed:**
- casehubio/neocortex#1216 (harden parseCapsActivations JSON parser — XS/Low)

**Tests:** 44 pass (20 dissonance + 24 behavioral synthesis).

## Queue State

- **#1216** (harden parseCapsActivations parser — XS/Low) — open, queued

## Immediate Next Step

#1216 (XS/Low) — replace the hand-rolled JSON parser in `DissonanceDetectionPhase.parseCapsActivations()` with a more robust implementation, or add edge-case tests to document the parser's limitations.

## References

- `specs/issue-1212-direct-caps-evaluation/2026-11-23-direct-caps-evaluation-design.md` — design spec
- `specs/issue-1212-direct-caps-evaluation/decisions.md` — 6 design decisions
- `plans/2026-11-23-direct-caps-evaluation.md` — implementation plan (3 tasks, 1 batch)
- `blog/2026-11-23-mdp02-the-settler-that-finally-heard-the-argument.md` — diary entry

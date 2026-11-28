# HANDOFF — casehub-agency (slot 223)

## Last Session

Completed #554 — TurnExecutor cancellation tests. Single commit on branch `issue-554-turn-executor-cancellation-tests`.

**What was done:**

- Added 3 cancellation tests to `TurnExecutorTest.java`:
  - Cancel mid-streaming (blocks in generator, cancels, verifies no TTS audio + `cancelled=true`)
  - Cancel during prompt assembly (blocks in assembler, cancels before LLM runs)
  - Concurrent cancel from another thread during active streaming
- Added `latchingTextSink()` helper + `turnCompleteLatch` field for thread-safe async test synchronisation
- Key insight: `executeUserTurn()` resets `turnCancelled = false` on entry, so "cancel before turn" is not possible — test correctly targets prompt assembly phase instead
- Key insight: `Future.get()` after `cancel(true)` returns `CancellationException` immediately without waiting for task completion — tests use latch-based waiting instead

**Build note:** `voice-spi` SNAPSHOT must be installed from slot's connectors (`mvn clean install -DskipTests` on `connectors/voice-spi`) before compiling `speech-ws`. IntelliJ reimport and Maven SNAPSHOT resolution can silently revert it.

## Queue

- **#554** — done, branch `issue-554-turn-executor-cancellation-tests`, awaiting landing
- **#523** — next: Remove Gson from speech-sherpa (S/Med)
- **#236** — Quarkus dev-mode speech-sherpa Java 21 (S/Med)

## Cross-Module

- **Pre-existing build issue:** `agency` module has a compilation error (`AgencyBeans.java:377`). Use `-pl` to skip it.
- **Enforcer skip:** speech-demo uber-jar needs `-Denforcer.skip=true`.

## References

- Blog: `docs/blog/2026-11-26-mdp03-dead-code-and-missing-signals.md`
- Consumer guide: `docs/guides/consumer-guide.md` (Pipeline Composition section)

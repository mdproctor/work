# Build Scripts

Helper scripts that enforce hard timeouts and exit clearly. Use these instead of
bare `mvn` commands — they prevent the silent background-task problem.

**Never specify `timeout` > default (120s) in Bash tool calls.** A large timeout
silently converts the command to a background task with output in an unreadable
temp file. If a command needs more than 120s, break it into smaller pieces using
these scripts.

---

## Scripts

```bash
# Test a single module (90s timeout — exits with clear error if exceeded)
scripts/mvn-test <module>
scripts/mvn-test <module> -Dtest=SpecificTestClass

# Install a module to local Maven repo so dependents can resolve it (60s timeout)
scripts/mvn-install <module>

# Compile a module's main + test sources without running tests (45s timeout)
scripts/mvn-compile <module>

# Test multiple modules sequentially, fail-fast on first failure
scripts/check-build runtime casehub-work-reports
```

## Standard Workflow

After changing module X:

```bash
scripts/mvn-test X                        # verify tests pass
scripts/mvn-install X                     # publish to local Maven repo
scripts/mvn-compile <dependent-of-X>      # verify dependent still compiles
```

---

## Expected Test Times

If a module takes significantly longer than these, something is wrong.

| Module | Expected |
|---|---|
| casehub-work-api | < 5s |
| casehub-work-core | < 10s |
| runtime | < 60s |
| casehub-work-reports | < 45s |
| casehub-work-notifications | < 30s |
| casehub-work-ai | < 30s |
| casehub-work-queues | < 30s |
| casehub-work-ledger | < 30s |

# Playbook Templates for Built-in Protocols

**Issue:** casehubio/agency#360
**Date:** 2026-10-27
**Status:** Draft

## Problem

Qhorus has four built-in channel protocols implemented as Java classes with hardcoded logic. Two of these — REQUEST_RESPONSE and TASK_COMPLETION — encode important communication patterns that consumers cannot easily inspect, modify, or extend. The protocol logic is opaque: you either use the built-in or rewrite from scratch.

Platform#661 delivered a `PlaybookProtocolEvaluator` that can execute protocol definitions expressed as state machines, backed by `OrcStateMachine` and `CoordinationStateStore`. This opens the door to expressing protocols as YAML playbook templates — readable, forkable, and extensible.

## Goal

Express REQUEST_RESPONSE and TASK_COMPLETION as playbook YAML templates that:
- Consumers can read to understand how the protocol works
- Consumers can fork and modify to create custom variants
- The system teaches by example — "here's how REQUEST_RESPONSE works as a state machine"

## Constraints

- **No adapter bridge yet.** The `ChannelProtocol` adapter that wires `PlaybookProtocolEvaluator` into qhorus's dispatch pipeline is qhorus#497 (open). These templates are not executable as channel protocols until that lands.
- **Commitment counting vs state machines.** The built-in protocols count active commitments against a threshold. A finite state machine cannot count unboundedly. Templates use a tiered approximation (IDLE/ACTIVE/OVERLOADED) with metadata documenting the exact counting rules.
- **No new Java code.** Templates are YAML files + documentation. No loader, no registration mechanism, no tests against `PlaybookProtocolEvaluator`.

## Design

### File Location

```
agency-core/src/main/resources/playbook-templates/
├── request-response.playbook.yaml
└── task-completion.playbook.yaml
```

Agency owns YAML surfaces for orchestration (agentic-yaml) and summarisation (summarisation-yaml). Protocol templates follow the same pattern — YAML definitions for reusable infrastructure.

### Template Format

Each template uses the established playbook multi-document YAML format with front-matter envelope (`playbook: "1.0"`, `schema`, `name`), followed by a body containing both metadata (exact semantics) and states (state machine approximation).

#### Structure

```yaml
---
playbook: "1.0"
schema: protocol
name: <protocol-name>
---
metadata:
  description: <human-readable description>
  config:
    <param-name>:
      type: int
      default: <value>
      override-key: <policyOverrides key>
  exact-rules:
    - <rule 1 — precise specification of when advisories fire>
    - <rule 2>

states:
  <STATE_1>:
    - on: { <EVENT>: <TARGET_STATE> }
  <STATE_2>:
    - advisory: { severity: <level>, message: <text>, action: <action> }
    - on: { <EVENT>: <TARGET_STATE> }
```

The `metadata` section is documentation — it captures the exact behavioral specification that the tiered state machine approximates. The `states` section is the executable state machine definition, compatible with `CompiledProtocol.from()` in the platform's `PlaybookProtocolEvaluator`.

The `schema: protocol` value is a new schema type — not yet registered in `playbook.schema.json`. Registration is deferred to qhorus#497 when the runtime integration lands. Until then, the templates use this value conventionally to signal their purpose.

### Template: request-response.playbook.yaml

**Source protocol:** `RequestResponseProtocol` (qhorus runtime-core)

**Exact behavior:** Checks `ProtocolContext.activeCommitments()` for open QUERY commitments. If incoming message is QUERY and open queries >= `maxOpenQueries` (default 3): WARNING. If incoming is neither QUERY nor RESPONSE and there are open queries: ADVISORY.

**State machine approximation:**

```yaml
---
playbook: "1.0"
schema: protocol
name: request-response
---
metadata:
  description: >
    Monitors the request-response cycle in a channel. Warns when too many
    queries are unanswered, indicating the responder may be overloaded or
    messages are being lost.
  config:
    maxOpenQueries:
      type: int
      default: 3
      override-key: request-response.max-open-queries
  exact-rules:
    - >
      When a QUERY arrives and the count of open (unanswered) QUERY
      commitments >= maxOpenQueries, emit WARNING with action INVESTIGATE
    - >
      When a non-QUERY, non-RESPONSE message arrives and there are any
      open QUERY commitments, emit ADVISORY with action LOG

states:
  IDLE:
    - on:
        QUERY: AWAITING_RESPONSE
        RESPONSE: IDLE

  AWAITING_RESPONSE:
    - advisory:
        severity: INFO
        message: "channel has open queries awaiting response"
        action: LOG
    - on:
        RESPONSE: IDLE
        QUERY: OVERLOADED

  OVERLOADED:
    - advisory:
        severity: WARNING
        message: "multiple unanswered queries — responder may be overloaded"
        action: INVESTIGATE
    - on:
        RESPONSE: AWAITING_RESPONSE
        QUERY: OVERLOADED
```

**Tier mapping:** IDLE = 0 open queries, AWAITING_RESPONSE = 1 open query, OVERLOADED = 2+ open queries. The Java implementation uses a configurable threshold (default 3); the state machine triggers at 2 for simplicity. The metadata documents the exact threshold behavior.

### Template: task-completion.playbook.yaml

**Source protocol:** `TaskCompletionProtocol` (qhorus runtime-core)

**Exact behavior:** Checks `ProtocolContext.activeCommitments()` for open COMMAND commitments. If incoming is COMMAND and open commands >= `maxOpenCommands` (default 3): WARNING. If sender is the obligor on an open command and incoming is not DONE/FAILURE/DECLINE: WARNING ("you have an open obligation").

**State machine approximation:**

```yaml
---
playbook: "1.0"
schema: protocol
name: task-completion
---
metadata:
  description: >
    Monitors the task assignment and completion cycle. Warns when too many
    commands are outstanding or when an obligor sends non-completion messages
    while they have open obligations.
  config:
    maxOpenCommands:
      type: int
      default: 3
      override-key: task-completion.max-open-commands
  exact-rules:
    - >
      When a COMMAND arrives and the count of open (incomplete) COMMAND
      commitments >= maxOpenCommands, emit WARNING with action INVESTIGATE
    - >
      When the sender is the obligor on an open COMMAND commitment and
      the incoming message is not DONE, FAILURE, or DECLINE, emit WARNING
      with action INVESTIGATE ("obligor has an open obligation")

states:
  IDLE:
    - on:
        COMMAND: TASK_ASSIGNED
        DONE: IDLE
        FAILURE: IDLE
        DECLINE: IDLE

  TASK_ASSIGNED:
    - advisory:
        severity: INFO
        message: "channel has open task assignments awaiting completion"
        action: LOG
    - on:
        DONE: IDLE
        FAILURE: IDLE
        DECLINE: IDLE
        COMMAND: OVERLOADED

  OVERLOADED:
    - advisory:
        severity: WARNING
        message: "multiple incomplete tasks — assignee may be overloaded"
        action: INVESTIGATE
    - on:
        DONE: TASK_ASSIGNED
        FAILURE: TASK_ASSIGNED
        DECLINE: TASK_ASSIGNED
        COMMAND: OVERLOADED
```

**Tier mapping:** IDLE = 0 open commands, TASK_ASSIGNED = 1 open command, OVERLOADED = 2+ open commands. The obligor check (sender has open obligation but sends non-completion message) is documented in `exact-rules` but cannot be represented in the state machine without guard expressions — the state machine tracks channel-level load, not per-sender obligations.

### Documentation

Add a "Protocol Templates" section to `docs/guides/consumer-guide.md`:

1. **What protocol templates are** — YAML playbook definitions expressing channel protocol behavior as state machines
2. **How to read a template** — metadata section (exact semantics) vs states section (executable approximation)
3. **How to create a custom protocol** — fork a template, modify states/transitions/advisories, rename
4. **Limitations** — templates are tiered approximations; exact counting and per-sender checks require the Java implementation; templates become executable when qhorus#497 lands

### What's Not In Scope

- No Java loader or registration code
- No tests against `PlaybookProtocolEvaluator` (no platform dependency added)
- No CONTRIBUTION_REQUIRED or ROUND_ROBIN templates
- No `ChannelProtocol` adapter bridge (that's qhorus#497)
- No extension of `PlaybookProtocolEvaluator` to support guard expressions

## Behavioral Gap Analysis

| Aspect | Java Implementation | State Machine Template |
|--------|-------------------|----------------------|
| Commitment counting | Exact count vs threshold | Tiered (0 / 1 / 2+) |
| Configurable threshold | Via `QhorusConfig` + `policyOverrides` | Documented in metadata, not enforced by states |
| Obligor check (TASK_COMPLETION) | Per-sender obligation tracking | Documented in metadata only |
| Advisory severity mapping | `Severity.WARNING` / `Severity.ADVISORY` | `AdvisorySeverity.WARNING` / `AdvisorySeverity.INFO` |
| State persistence | Stateless (re-derived from commitments) | Durable via `CoordinationStateStore` (when running) |

The metadata `exact-rules` section bridges this gap — consumers can see both what the state machine does and what the full Java implementation does.

## References

- `RequestResponseProtocol.java` (qhorus runtime-core) — source protocol implementation
- `TaskCompletionProtocol.java` (qhorus runtime-core) — source protocol implementation
- `PlaybookProtocolEvaluator.java` (platform coordination-core) — playbook evaluation engine
- `CompiledProtocol.java` (platform coordination-core) — state map compilation
- `PlaybookFrontMatter.java` (platform yaml-core) — front-matter record
- `playbook.schema.json` (platform yaml-core) — YAML schema
- casehubio/platform#661 — playbook-as-protocol bridge (closed)
- casehubio/qhorus#497 — playbook protocol lifecycle (open, blocks runtime integration)
- casehubio/casehub-ops#201 — Phase 2: playbook-backed channel protocols (parent epic)

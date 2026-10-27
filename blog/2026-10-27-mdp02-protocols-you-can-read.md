---
layout: post
title: "Protocols You Can Read"
date: 2026-10-27
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [protocols, playbook, yaml, counters, qhorus]
---

# Protocols You Can Read

Qhorus has four built-in channel protocols — REQUEST_RESPONSE, TASK_COMPLETION, CONTRIBUTION_REQUIRED, ROUND_ROBIN. They enforce communication discipline: warn when queries pile up unanswered, flag when an obligor ignores their open task, catch when one participant dominates the conversation. They work. The problem is that they're Java classes with hardcoded logic, and the only way to understand what they do is to read the source.

That's fine for platform developers. It's not fine for anyone else.

A consumer who wants to know "what does REQUEST_RESPONSE actually enforce?" has two options: read `RequestResponseProtocol.java` (assuming they can find it in qhorus-runtime-core), or trust the one-paragraph description in the consumer guide. Neither option lets them *modify* the behaviour. If the default threshold of 3 open queries doesn't fit their domain, they can't adjust it without writing a new `ChannelProtocol` implementation from scratch.

## Why Not a State Machine?

I started with the obvious approach — express the protocols as state machines. Three tiers: IDLE, ACTIVE, OVERLOADED. QUERY moves you right, RESPONSE moves you left, WARNING fires in the rightmost state. Readable, forkable, done.

Except the built-in protocols don't have states. They count active commitments on every evaluation. REQUEST_RESPONSE counts open queries and warns when that count crosses a threshold. A finite state machine can approximate this with tiers, but it can't reproduce it — the tier boundaries are fixed where the real threshold is configurable.

The playbook system already has the right primitives for this. Counters (`OrcCounter`) with increment, decrement, and reactive threshold listeners. Guards on state transitions. Pattern matching on event types. These compose — and they give exact behavioral equivalence, not an approximation.

## Protocols Are Just Playbooks

The design shift was realising that a protocol playbook isn't a special thing. It's a normal playbook — same YAML format, same compiler, same executor — that happens to be triggered by channel dispatch events and emits advisories. The front-matter metadata declares its role (`type: protocol`) and pre-initializes resources (counter defaults, policyOverride keys for per-channel customisation).

The request-response playbook reads events from the channel dispatch, matches on event type, and maintains a counter of open queries:

```yaml
- step: evaluate
  from: dispatch-events
  loop: continuous
  match: "${event.type}"
  cases:
    - pattern: "QUERY"
      counter-add: { name: "openQueries", delta: 1 }
      when ${counter-get('openQueries') >= config.maxOpenQueries}:
        - advisory: { severity: WARNING, action: INVESTIGATE }
    - pattern: "RESPONSE"
      counter-add: { name: "openQueries", delta: -1 }
```

No tiered approximation. The counter tracks the exact count. The guard fires when the count crosses the configurable threshold. The playbook does what the Java does.

Platform had built a separate `PlaybookProtocolEvaluator` with its own mini state machine engine for this — but the standard playbook executor already has everything it needs. Counters, guards, pattern matching, event loops. The separate engine is technical debt; we filed a platform issue to remove it.

## What This Opens Up

The playbooks aren't wired into channel dispatch yet — qhorus#497 will bridge that. When it lands, a consumer forks `request-response.playbook.yaml`, changes the threshold, adds a case for a domain-specific event type, and registers it. No Java.

The deeper point is that protocol logic stops being opaque framework code and becomes something you can read, test, and modify. The playbook format is the same one used everywhere else in the platform. A developer who can read one playbook can read a protocol.

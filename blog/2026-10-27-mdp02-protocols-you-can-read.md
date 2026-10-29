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
      block:
        - counter-add: { name: "openQueries", delta: 1 }
        - advisory: { severity: WARNING, message: "unanswered queries exceed threshold", action: INVESTIGATE }
          when: "${openQueries >= config.maxOpenQueries}"
    - pattern: "RESPONSE"
      counter-add: { name: "openQueries", delta: -1 }
```

No tiered approximation. The counter tracks the exact count. The guard fires when the count crosses the configurable threshold. The playbook does what the Java does.

## Two Families of Protocol

Counter-based protocols — REQUEST_RESPONSE and TASK_COMPLETION — track *quantities*. How many queries are open? How many commands are outstanding? The primitives are simple: `counter-add` to increment and decrement, a `when` guard to fire at a threshold.

![Two protocol families — counter-based and sender-tracking](images/protocol-families.svg)

CONTRIBUTION_REQUIRED and ROUND_ROBIN are different. They track *identity* — who sent the last message, and should this sender be talking right now? The quantity primitives aren't enough. You need `counter-set` to reset a counter to a specific value (not just increment), and `state-set` to remember the last sender across evaluations.

The contribution-required playbook skips EVENT-type messages (system noise), then tracks consecutive messages from the same sender:

```yaml
cases:
  - pattern: "EVENT"
    counter-add: { name: "consecutiveCount", delta: 0 }
  - default:
      - counter-add: { name: "consecutiveCount", delta: 1 }
        when: "${event.sender == state.lastSender}"
      - counter-set: { name: "consecutiveCount", value: 1 }
        when: "${event.sender != state.lastSender}"
      - state-set: { name: "lastSender", value: "${event.sender}" }
      - advisory: { severity: WARNING, action: LOG }
        when: "${consecutiveCount >= config.maxConsecutive}"
```

When alice sends three messages in a row without bob contributing, the counter hits the threshold and the advisory fires. When bob finally speaks, `counter-set` resets to 1 — not `counter-add: { delta: -N }`, which would require knowing the current value. The reset is absolute.

![Contribution-required event flow — consecutive sender tracking](images/contribution-required-flow.svg)

ROUND_ROBIN is the purest sender-tracking protocol. It doesn't count anything — it remembers who spoke last and checks whether the next speaker is who the rotation expects. The modular arithmetic (`(lastIdx + 1) % participants.size()`) lives in the expression engine, not in the playbook. The playbook just records state and fires when the expectation is violated.

The difference between these two families isn't just the primitives they use. It's what they're watching. Counter-based protocols monitor the *channel's* health — are obligations piling up? Sender-tracking protocols monitor *participant behaviour* — is someone hogging the conversation, or speaking out of turn? Same format, same executor, different lens on the same event stream.

## What This Opens Up

The playbooks aren't wired into channel dispatch yet — qhorus#497 will bridge that. When it lands, a consumer forks any of the four playbook templates, adjusts the thresholds or participant list, adds domain-specific event cases, and registers it. No Java.

The deeper point is that protocol logic stops being opaque framework code and becomes something you can read, test, and modify. The playbook format is the same one used everywhere else in the platform. A developer who can read one playbook can read a protocol.

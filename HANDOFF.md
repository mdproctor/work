# Handover — 2026-07-17

**Branch:** `main` (clean)

## Last Session

Completed topic-aware conversation model (#49). ConversationProjection now carries topic, messageId, and messageType from MessageView through the fold. ConversationRenderer supports topic-grouped rendering, COMMAND→STATUS→DONE obligation chains, and reaction decoration via batch-fetched ReactionGroups. ChannelAgentRequest carries topic for dispatch context. Design spec and explainer doc ("conversation projections vs global chat history") committed alongside implementation.

Also filed blocks#62 (integrate structured progress into conversation rendering — blocked by work#237 + engine#84), linked work#237 to engine#84, and added conversation-projection consumer requirements to work#237.

## Immediate Next Step

Pick from What's Next. Run `/work` to start.

## What's Left

- **engine#101 revision** — Update issue to reflect Gaps 2+3 done, close or narrow to remaining Gap 1. · XS · Low

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| #62 | Integrate structured progress into conversation rendering | M | Med | Blocked by work#237 + engine#84; #49 builds the rendering surface |
| #46 | Output contracts on TaskNode | M | Med | Depends on engine#694 (done) |
| #54 | Recursive LLM decomposition | M | Med | LLM returns CompoundTasks |
| #56 | SHOP-style forward reasoning with effects | S | Med | Apply PrimitiveTask effects during planning |
| #57 | Task tree construction DSL | S | Low | compound/primitive factory methods |
| #53 | Predecessor-specific plan composition analysis | S | Med | engine#718 done — unblocked |

## Cross-Module

**Engine issues from blocks sessions:**
- engine#696–698 — recovery, versioning, context isolation (all open)
- engine#101 — LLM supervisor mode: Gap 1 reframed by engine#700 (closed)
- engine#717 — ExperiencePlanStep.stepOutcome() should return RoutingOutcome

**Progress infrastructure (cross-cutting):**
- work#237 — structured progress (platform primitive, not just work)
- engine#84 — milestone/stage/goal alignment (linked to work#237)
- blocks#62 — conversation rendering consumer (blocked by above two)

## References

- Spec: `docs/specs/2026-07-17-topic-aware-conversation-model-design.md`
- Explainer: `docs/conversation-projections-vs-chat-logs.md`
- Plan: `plans/attic/issue-49-e2e-choreography-topics/`

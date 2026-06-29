# Handover — 2026-06-29

**Branch:** `main`

## Last Session

P4 channel agent dispatch landed (#3, closed). Five classes added to `io.casehub.blocks.channel`: ChannelAgentHandler SPI, ChannelAgentDispatcher, ChannelAgentRequest, AgentTask, AgentResultParseException. 40 tests total. DraftHouse subclasses the dispatcher for debate-specific error encoding.

Key design decision: dispatcher uses `Function<AgentTask, String>` and `Consumer<MessageDispatch>` instead of depending on qhorus runtime `MessageService` — keeps blocks at the API dependency level.

## Immediate Next Step

P5 (structured conversation protocol) is next per parent#310. This is the big one — needs design brainstorm first on naming (debate→conversation?), entry type generality, and agent role model.

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| — | P5: Structured conversation protocol | L | High | Needs brainstorm before implementation |
| — | P6: Oversight gate lifecycle | M | Med | From openclaw, guidance posted on openclaw#31 |

## References

- Extraction plan: [parent#310 comment](https://github.com/casehubio/parent/issues/310#issuecomment-4795440229)
- Repo setup: parent#321

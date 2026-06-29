# Handover — 2026-06-29

**Branch:** `main`

## Last Session

First real code landed — P1–P3 channel utility blocks extracted from DraftHouse (#2, closed). Four classes in `io.casehub.blocks.channel`: ChannelMessageMeta, ContextTracker, ContextSnapshot, BoundedProjectionDecorator. 34 tests. DraftHouse is the first consumer.

## Immediate Next Step

P4 (channel agent dispatch) is next per parent#310. The SPI interface + dispatcher pattern — S/Low complexity.

## What's Next

| # | Description | Scale | Complexity | Notes |
|---|-------------|-------|------------|-------|
| — | P4: Channel agent dispatch SPI + dispatcher | S | Low | Unblocks P5 |
| — | P5: Structured conversation protocol | L | High | The main value — what Claudony needs |
| — | P6: Oversight gate lifecycle | M | Med | From openclaw, unblocks openclaw#31 |

## References

- Extraction plan: [parent#310 comment](https://github.com/casehubio/parent/issues/310#issuecomment-4795440229)
- Repo setup: parent#321

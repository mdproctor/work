# HANDOFF — casehub-agency (slot 223)

## Last Session

Completed and landed 6 issues across avatar speech pipeline work. All on main.

**Landed:**

- **#551** — Colour-coded status bar with icons for speech pipeline states. 9 states mapped to colours/icons with CSS transitions. Fixed missing Speaking/Ready status emissions in AvatarEngine for 3D head path.

- **#553** — Integrated `CorrectionHooks.corrector()` as a named pipeline step in `TextFilterRegistry`. Removed dead `corrector` parameter from SpeechSession. CDI singleton sharing for vocabulary learning.

- **#537** — Excluded `casehub-agency` from `speech-deployment`'s `agentic-yaml-deployment` dependency. Fixes Jandex index miss for `CoordinationSignalProvider`.

- **#460** — Wired gesture vocabulary into LLM system prompt and UtteranceSegmenter. `GesturePromptSection` now attached to `DefaultPromptAssembler`. 51 promptable gestures + 6 moods now visible to the LLM with continuous natural embedding.

**Closed (already resolved):**

- **#524** — `@JsonInclude(NON_NULL)` audit — resolved by #521.
- **#448** — Platform agent-config — `SpeechProducers.java` already removed, `agent-config.yaml` already in place.

**Deferred:**

- **#546** — Client visual indicators for voice state transitions — blocked by #544 (interrupt handler).

## Remaining Avatar UI Issues

- **#461** — Contribute body animation system to TalkingHead (M/Med, upstream contribution)
- **#454** — Epic: Avatar speech pipeline — production-ready (XL/High)

## Cross-Module

- **Pre-existing build issue:** `agency` module has a compilation error (`AgencyBeans.java:377`). Use `-pl` to skip it.
- **Enforcer skip:** speech-demo uber-jar needs `-Denforcer.skip=true`.

## References

- Blog: `docs/blog/2026-11-26-mdp03-dead-code-and-missing-signals.md`
- Consumer guide: `docs/guides/consumer-guide.md` (Pipeline Composition section)
- Avatar config: `speech-demo/src/main/resources/META-INF/avatar.yaml`

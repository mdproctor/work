# HANDOFF — casehub-blocks

## Last Session

Implemented **casehubio/blocks#304** — CognitiveAttentionMediator: bridge neocortex attention signals to CognitionCore. Full TDD implementation across 5 commits:

1. `CognitiveAttentionMediator` — @ApplicationScoped CDI bean, per-principal ConcurrentLinkedQueue, merge semantics (dedup by sourceNodeId+category, latest urgencyP75, significance-sorted)
2. `AttentionRelevance` — package-private static utility with EnumSet<SignalCategory> constants mapping cognitive domains to relevant signal categories. Type-safe, no string keys.
3. `CognitionConfig` — added `attentionEnabled` (13th field)
4. `CognitionCore` — nullable mediator field, drain-and-discard in tick() FOUNDATION, `isEnabled()` helper for attention-aware section gating, `AttentionPromptSection` rendering, `lastBriefing()` accessor
5. CDI wiring — BlocksBeans, SocialAvatarCognition, BlocksAutoConfiguration all pass mediator

Docs updated: CLAUDE.md, consumer guide, contributor guide. #305 closed as premature (topN truncation doesn't exist — when it does, retrieval-modulated scoring is the right approach, not arbitrary cutoffs).

2209 tests pass. Full project builds.

## Follow-up Issues

| # | Repo | Title | Scale | Complexity | Notes |
|---|------|-------|-------|------------|-------|
| 306 | blocks | Runtime-customizable attention override policy | S | Med | Extract AttentionRelevance to SPI |
| 307 | blocks | CognitionSnapshot — capture lastBriefing | XS | Low | Attention invisible to observability |
| 308 | blocks | Context-budget-aware prompt rendering | L | High | Session lifecycle, window management, new vs continuing |
| 91 | examples | Replace CognitiveBudget.forSituation() with attention gating | S | Med | wacky-manor, depends on #304 + #306 |
| 89 | examples | Redefine briefing as voice card | — | — | Open, should reference #304 mediator |

## Design Decisions

- **D1:** Mediator in blocks-core alongside CognitionCore
- **D2:** attentionEnabled config flag with drain-and-discard when disabled
- **D3:** AttentionRelevance reverse mapping (domain → categories) with EnumSet constants — type-safe, O(1) bitwise, testable

## Cross-Repo Fixes (2026-09-27)

Fixed broken engine main build to unblock blocks CI:

| Commit | What | File |
|--------|------|------|
| `552f69f0f` | Add missing `Event<TickEvaluatedEvent>` param to `RuntimeBeans.evolutionTicker()` producer | `runtime/.../RuntimeBeans.java` |
| `552f69f0f` | Add `.orElseThrow()` for `Optional<CaseInstance>` return in test | `work-adapter/.../PlanItemCompletionApplierTest.java` |
| `552f69f0f` | Fix codegen extra-field handling, `getWhen→getCondition`, annotation formatting | Multiple files |
| `618df923f` | Add REPLAY to `ExecutionOriginTest` coverage assertion | `api/.../ExecutionOriginTest.java` |

Pushed to both `mdproctor/engine` and `casehubio/engine` on main.

## Cross-Repo Fixes (2026-10-14)

Fixed cascading CI failures across blocks and neocortex:

**blocks** (landed via quick-fix `7949bf3`):
- `DefaultIntegrityCheckerTest` — swapped `findSupersededCases(String, MemoryDomain)` → `findSupersededCases(MemoryDomain, String)` (4 sites)
- `CognitionCompiler` — added `subThoughtModulationStrength` + `secondaryPleasureModulationFactor` to `DriveConfig` constructor, `trendImprovingThreshold` + `trendDecliningThreshold` + `maxDimensionDelta` to `StrategyLearningConfig`
- `PatternWorkerFunctionHandler`, `EngineAdapterBeans`, `EngineAdapterAutoConfiguration`, test — `WorkerRuntimeFactory` import: `engine.internal.executor` → `engine.runtime.executor`
- `IntegrationTestBeans` — 15 imports: `engine.internal.worker` → `engine.runtime.worker`, `engine.internal.routing` → `engine.runtime.routing`, `actorstate` → `engine.actorstate`, `persistence.memory` → `engine.persistence.memory`, `engine.internal.context` → `engine.runtime.context`
- `BlocksAutoConfiguration` — removed stale `CognitiveAttentionMediator` param from `CognitiveProfileParticipant` constructor

**neocortex** (commit `98e37b9c`):
- Removed duplicate `casehub-neocortex-sqlite-support` dependency management entry in parent pom (blocked all CI builds)

## References

- Spec: `/Users/mdproctor/claude/casehub/slots/203/wsp-casehub-blocks/specs/issue-304-cognitive-attention-mediator/2026-09-27-cognitive-attention-mediator-design.md`
- Decisions: `/Users/mdproctor/claude/casehub/slots/203/wsp-casehub-blocks/specs/issue-304-cognitive-attention-mediator/decisions.md`
- Plan: `/Users/mdproctor/claude/casehub/slots/203/wsp-casehub-blocks/plans/2026-09-27-cognitive-attention-mediator.md`
- Neocortex spec §7: `/Users/mdproctor/claude/casehub/slots/203/neocortex/docs/specs/issue-381-progressive-attention-model/2026-09-25-progressive-attention-model-design.md`

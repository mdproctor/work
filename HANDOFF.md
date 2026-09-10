# Session Handover — 2026-09-10

**Branch:** `main`
**Epic:** #246 — Complete YAML Surface
**State:** .plan queue with 12 issues, idle on main

## What happened

Two issues landed this session:

**#242 — Type-safe expression compilation.** Per-site `Class<?>` compilation in 3 agentic-yaml registries (RoutingStrategyRegistry, TerminationConditionRegistry, JudgmentTriggerRegistry). Design went through a review that collapsed a 5-decision descriptor architecture into simple per-site compilation. Layer 2 descriptor model deferred to yaml-core.

**#247 — Social cognition configs.** 13 spec records with `@Nullable` fields + `CognitionCompiler` that merges partial YAML with `defaults()`. `CognitionDefinition` root type for `cognition.yaml`. Deployment processor discovers config at build time. 104 tests passing.

## Resume point

Run `work` — the .plan queue starts at #248 (Affordance world model). Each issue references `docs/yaml-coverage.md` with the section to update on close.

## Key decisions

- Per-site expression compilation (Layer 1): each expression site passes its known Class<?> to ExpressionEngine.compile(). Layer 2 descriptor model deferred.
- Spec records with @Nullable fields for partial YAML merge — established pattern for all config-shaped types
- Standalone cognition.yaml (not nested in pattern spec) — configs are application-scoped
- MVEL3 can't introspect Java records with sealed interface components — compile against Map<String, Object> instead (GE-20260910-27296d)

## References

| Artifact | Path |
|----------|------|
| Coverage matrix | `docs/yaml-coverage.md` |
| Epic | casehubio/blocks#246 |
| .plan | 12 issues queued, #248 next |
| #242 spec | `docs/specs/issue-242-typed-expr-compilation/` |
| #247 spec | `docs/specs/issue-247-social-cognition-configs/` |
| Diary entry | `docs/blog/2026-09-10-mdp01-when-the-reviewer-kills-your-architecture.md` |
| Garden entry | GE-20260910-27296d (MVEL3 record/sealed interface gotcha) |

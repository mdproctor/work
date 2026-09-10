# Session Handover — 2026-09-10

**Branch:** `main`
**Epic:** #246 — Complete YAML Surface
**State:** .plan queue with 13 issues, idle on main

## What happened

Implemented #242 (type-safe expression compilation). Wired per-site `Class<?>` context types into three agentic-yaml registries (RoutingStrategyRegistry, TerminationConditionRegistry, JudgmentTriggerRegistry) so YAML-declared expressions compile and evaluate correctly. Design went through a review cycle that collapsed a 5-decision descriptor architecture into simple per-site compilation — Layer 2 descriptor model deferred to yaml-core when a consumer materialises.

Garden entry GE-20260910-27296d captured: MVEL3 PojoAdapter fails for Java records with sealed interface components.

## Resume point

Run `work` — the .plan queue starts at #247 (social cognition configs). Each issue references `docs/yaml-coverage.md` with the section to update on close.

## Key decisions

- Per-site compilation (Layer 1): each expression site in the sealed spec hierarchy passes its known Class<?> to ExpressionEngine.compile(). No new types or modules.
- Layer 2 deferred: yaml-core descriptor model (TypeDescriptor, PropertyDescriptor, ExpressionCompiler SPI) builds when Tier 2 summarisation or dynamic pipeline loading needs YAML-declared context schemas
- Map projection for records: MVEL3 can't introspect Java records with sealed interface components — compile against Map<String, Object> and project fields explicitly
- Design decisions in specs/issue-242-typed-expr-compilation/decisions.md (promoted to project docs/specs/)

## References

| Artifact | Path |
|----------|------|
| Coverage matrix | `docs/yaml-coverage.md` |
| Epic | casehubio/blocks#246 |
| .plan | 13 issues queued, #247 next |
| Design spec | `docs/specs/issue-242-typed-expr-compilation/` |
| Diary entry | `docs/blog/2026-09-10-mdp01-when-the-reviewer-kills-your-architecture.md` |
| Garden entry | GE-20260910-27296d (MVEL3 record/sealed interface gotcha) |

# Session Handover — 2026-09-07

**Branch:** `issue-165-agentic-yaml-expansion`
**Issues:** #165
**State:** Design complete (8 decisions), ready for writing-plans

## What happened

Full YAML DSL parity audit and architecture design for blocks. Cross-repo research across engine, eidos, neocortex, work, and platform to understand the yaml-core ecosystem, schema generation approaches (victools reflection, not JavaParser), and the DSL-STYLE-GUIDE principle ("peer representations of the same models").

### Key findings from audit

- All repos use victools reflection on canonical Java classes — no JavaParser anywhere
- Engine has 8 SchemaModules reshaping output to match DSL ergonomics
- Work is at 100% YAML parity, engine ~85%, eidos done, neocortex audited, blocks not started
- blocks#165 is the last open Phase 2 item from parent#422

### Architecture decisions (D1-D8)

1. **D1** — victools on spec records (not canonical runtime types — those have functional interfaces)
2. **D2** — Spec records as shared data model. Java builders and YAML both converge to same runtime objects. Java DSL unchanged externally.
3. **D3** — MVEL3 LambdaCatalog for expression compilation (YAML→Java only, no roundtripping). jq also supported.
4. **D4** — Dual compilation: compile-time (deployment module) + runtime (dynamic loading). Both always available.
5. **D5** — Strategy registries (type:name resolution, SummariserRegistry pattern)
6. **D6** — Annotation-driven spec record generation. `@YamlSpec(name="round-robin")` on implementations → deployment step generates spec records, sealed hierarchies, registries. ~35 generated, ~5 hand-written for complex shapes.
7. **D7** — Drift detection. Every impl gets `@YamlSpec`. Generated baseline fields ⊆ hand-written spec fields → pass. Missing field → build fails.
8. **D8** — ShorthandModule for YAML ergonomics. `routing: round-robin` instead of `routing: { type: round-robin }` for zero-config strategies.

### External work done

- **platform#279** — Promote SealedHierarchyModule to casehub-schema-generator (DONE)
- **blocks#165** — Updated with full architecture, unblocked
- ShorthandModule also being promoted from neocortex to platform (in progress, not by this session)

## Resume point

Write the implementation plan (`writing-plans` skill). The design spec and all 8 decisions are committed. Pipeline state is PLANNING. The spec has been refined by a partial design review (killed by memory pressure, but refinements committed).

Key files to read:
- Spec: `specs/issue-165-agentic-yaml-expansion/2026-09-07-agentic-yaml-expansion-design.md`
- Decisions: `specs/issue-165-agentic-yaml-expansion/decisions.md` (D1-D8)
- Precedent: `docs/specs/issue-233-summarisation-yaml-surface/2026-09-05-summarisation-yaml-surface-design.md`
- Pattern mapping: `specs/main/2026-08-23-typescript-programming-model-design.md`

## References

| Artifact | Path |
|----------|------|
| Design spec | `specs/issue-165-agentic-yaml-expansion/2026-09-07-agentic-yaml-expansion-design.md` |
| Decisions | `specs/issue-165-agentic-yaml-expansion/decisions.md` |
| Pipeline state | `specs/issue-165-agentic-yaml-expansion/pipeline.state` |
| Platform issue | casehubio/platform#279 (done) |
| Parent epic | casehubio/parent#422 |

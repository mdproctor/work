# HANDOFF — 2026-08-25

## Last Session

Designed and began the codegen module retirement — the first of four items in the TypeScript programming model roadmap. Key design decisions: (1) extract `CaseDefinitionSpec` to match the YAML `spec:` block structurally, so Java types are directly Jackson-deserializable; (2) externalized Jackson Module for deserialization rules (no behavior annotations on domain types — captured as platform protocol PP-20260825-7ad4b1); (3) confirmed via `ide_find_references` across 11 repos that all spec-level getter usage (270+ call sites) is engine-internal — zero cross-repo impact.

Phase 1 complete: `CaseDefinitionSpec` created with 32 fields, `CaseDefinition` getters/setters delegate to spec, Builder routes through getters. 1270 api tests pass. Committed as `2778dd11`.

## Immediate Next Step

Phase 2 of CaseDefinitionSpec extraction — remove orphaned field declarations from CaseDefinition, then build the Jackson `CaseDefinitionModule` with externalized deserializers. Open IntelliJ workspace with engine+worker only (2 repos — 11-repo workspace caused persistent timeouts).

## Cross-Module

*Unchanged — `git show HEAD~1:HANDOFF.md`*

## References

| Doc | Path |
|-----|------|
| Design spec | `specs/issue-422-ts-programming-model/2026-08-24-schema-generator-design.md` |
| Decisions | `specs/issue-422-ts-programming-model/decisions.md` |
| Implementation plan | `plans/2026-08-24-schema-generator.md` |
| Protocol | `casehub/garden/docs/protocols/casehub/jackson-externalized-serialization.md` |

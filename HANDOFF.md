# HANDOFF — 2026-08-25

## Last Session

Built the model-canonical schema generator end-to-end: Java model types → JSON Schema → TypeScript interfaces. The generated schema replaces the hand-written `CaseDefinition.yaml` as the single source of truth. 12 engine commits, 1 worker commit.

**Schema generator (engine#975):** victools/jsonschema-generator with 8 custom modules (SpecNesting, UnevaluatedProperties, Worker, CaseCompletion, ExpressionEvaluator, Trigger, BindingTarget, EnumInlining) plus a 550-line SchemaPostProcessor. Wired into Maven build via exec-maven-plugin — runs at `process-classes`, writes `CaseDefinition.yaml` to both `target/generated-schema/` and `schema/src/main/resources/schema/`. SchemaDriftTest catches model changes without regeneration.

**YAML expansion (engine#976):** 13 new CaseDefinitionSpec fields (reflection, monitoring, adaptation, planningConstraints, recoveryPolicy, portfolioConfig, memoryRetrieval, maxAdaptations, goapActions, workerServiceAccountIds, defaultQuorum, humanTaskContextConstraints, humanTaskWorkloadConstraint). YAML mapper parsing for 4 fields that were missing. Per-worker GOAP shorthand (cost, effect, softDependency). Two example fixtures.

**Cross-repo renames (worker-api):** `Worker.capabilityNames` → `capabilities`, `Capability.inputSchema` → `inputProjection`, `Capability.outputSchema` → `outputProjection`. IntelliJ workspace refactoring across both repos — 99 files, zero false positives. Post-processor rename bridges removed.

**@JsonPropertyDescription:** Added to CaseDefinition (~35 fields), Binding (~18), Goal (4), Milestone (6). Descriptions flow through the chain: Java annotations → generated JSON Schema → TypeScript TSDoc comments.

**TypeScript types (parent#422):** `json-schema-to-typescript` produces 845 lines of type-safe TS interfaces from the generated schema. Committed to `schema/src/main/resources/ts/CaseDefinition.ts`. End-to-end chain proven.

## Immediate Next Step

Deferred items in `.plan` — pick based on priority:

1. **Retire codegen module** (L / High) — remove `engine/codegen`, delete `io.casehub.model.*` generated POJOs, refactor `CaseDefinitionYamlMapper` to deserialize directly to API model types. Significant — the mapper's 1900 lines use the generated POJOs as the Jackson deserialization target.

2. **Wire TS generation into Maven** (S / Low) — `frontend-maven-plugin` or exec-maven-plugin to run `npx json-schema-to-typescript` after schema generation. Currently manual.

3. **TS CDK builders** (M / Med) — Pages DSL pattern: TypeScript builder functions that emit validated YAML. Depends on the generated TS types being stable.

4. **npm package** (S / Low) — `@casehub/engine-sdk` publishing the generated TS types.

## Cross-Module

**Worker-api** is on branch `issue-422-ts-programming-model` in a worktree at slot 156. Must merge to main BEFORE engine — engine depends on the renamed jar.

**Merge order:**
1. Worker: merge + push → CI publishes renamed jar
2. Engine: merge + push → CI resolves new worker-api

## References

| Doc | Path |
|-----|------|
| Design spec | `specs/issue-422-ts-programming-model/2026-08-24-schema-generator-design.md` |
| Roadmap spec | blocks workspace: `specs/main/2026-08-23-typescript-programming-model-design.md` |
| Decisions | `specs/issue-422-ts-programming-model/decisions.md` |
| Implementation plan | `plans/2026-08-24-schema-generator.md` |
| Blog: schema work | `blog/2026-08-24-mdp01-two-models-of-the-same-system.md` |
| Blog: refactoring study | `blog/2026-08-24-mdp02-the-rename-that-proved-the-point.md` |

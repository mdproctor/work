# TypeScript Programming Model — Decisions

## D1: Three-Level TS Strategy

**Choice:** Three progressive levels of TypeScript support for CaseHub

- **Level 1 — GraphQL Client**: Type-safe TS bindings for CaseHub's GraphQL API. npm package, Node.js runtime. Table stakes for TS relevance.
- **Level 2 — TS CDK → YAML**: TS builder functions (Pages DSL pattern) that produce validated CaseHub YAML. Types generated from CaseDefinition.yaml JSON Schema. Full type safety, autocomplete, refactoring. Compilation target is the existing YAML document format.
- **Level 3 — Dual TSJ + YAML**: TSJ (TS→JVM bytecode) for direct Java object construction AND continued YAML expressiveness expansion. Both paths produce the same runtime objects. TS type definitions (.d.ts) generated from Java types. The relative weight of TSJ vs YAML evolves based on TSJ maturity and YAML coverage.

**Alternatives:**
- Node.js-only (no JVM) — limits TS developers to what YAML/HTTP can express; blocks-level patterns inaccessible
- TSJ-only (no YAML expansion) — all-in on a pre-alpha dependency; no fallback
- Full port to TS — prohibitive cost, maintaining two implementations

**Rationale:** Each level delivers standalone value. Level 1 is immediate. Level 2 leverages the existing YAML schema with the Pages DSL precedent. Level 3 provides the escape hatch for patterns that can't be declaratively expressed. YAML expansion as a parallel track reduces TSJ dependency.

**Trade-offs:** Level 3 depends on TSJ maturity (32 commits, no license, 1 contributor). The YAML expansion path is the safety net — if TSJ stalls, the declarative surface grows to compensate.

**Sources:** casehubio/casehub-desiredstate#108, Pages DSL (pages-ui/src/dsl/builders.ts), CaseDefinition.yaml JSON Schema (engine/schema)

**Exploration:** quick
**Status:** captured

## D2: YAML as primary expressiveness surface

**Choice:** Push YAML expressiveness as far as possible before relying on TSJ for code-level patterns

**Alternatives:**
- TSJ-first (make everything code-constructable, YAML as secondary) — couples roadmap to TSJ maturity
- Independent TS SDK producing JSON IR — loses "no serialization boundary" elegance, still needs schema design

**Rationale:** YAML already covers case definitions comprehensively (1345-line JSON Schema). The engine already loads and executes YAML. Expanding YAML to cover agentic patterns, routing strategies, and termination conditions is incremental. The TS CDK (Level 2) automatically gains these patterns. TSJ is reserved for truly code-only patterns (custom lambdas, SPI implementations).

**Trade-offs:** Some blocks patterns are inherently procedural (custom decomposition logic, social cognition orchestrators) and may never be fully expressible in YAML. These remain TSJ-only or Java-only.

**Sources:** CaseDefinition.yaml schema analysis, Pages DSL dual YAML/TS strategy

**Exploration:** quick
**Status:** captured

## D3: Full type safety — no exceptions

**Choice:** All TS surfaces are fully type-safe. No `any`, no type escape hatches, no untyped config objects.

**Alternatives:**
- Pragmatic typing (allow `any` at extension points) — faster to build, but undermines the type safety story
- Runtime validation only (loose types + zod/joi) — shifts errors from compile time to runtime

**Rationale:** Type safety is the core value proposition of TS over raw YAML. If the TS surface has `any` holes, LLMs will generate untyped code that passes the type checker but fails at runtime. The type system IS the validation layer.

**Trade-offs:** Harder to design extension points. Generic type parameters may be needed for SPIs. Generated types from Java must be complete — no partial generation.

**Sources:** User requirement (explicit)

**Exploration:** quick
**Status:** captured

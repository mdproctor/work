# Decisions — #242 Type-safe Expression Compilation

## D1: Descriptor model ownership — yaml-core

**Choice:** yaml-core owns the type descriptor model. Expression engines (MVEL3, JEXL, JQ) adapt to it via bridge modules. yaml-core stays zero-dependency.
**Alternatives:**
- MVEL3 owns the model — couples yaml-core to a specific engine
- Each module defines its own descriptors — duplicates the pattern
**Rationale:** yaml-core is the shared YAML infrastructure layer. Expression compilation is a cross-cutting concern used by both agentic-yaml and summarisation-yaml. Owning the descriptor model in yaml-core gives both modules a shared contract without introducing engine dependencies.
**Trade-offs:** Creates a descriptor hierarchy parallel to MVEL3's Type/Declaration. Bridge module handles the mapping.
**Sources:** platform/yaml-core/pom.xml (zero deps, J2CL-transpilable), MVEL3 Type.java, CompilerParameters.java
**Exploration:** quick
**Status:** captured

## D2: Type names — string-based

**Choice:** Property types in descriptors are string-based (`"double"`, `"string"`, `"io.casehub.blocks.agentic.RoutingCandidate"`), not `Class<?>` tokens. A `Map<String, Class<?>>` type resolver in the bridge module maps strings to classes at compilation time.
**Alternatives:**
- Class-token based (`Class<?>` directly) — simpler bridge but requires context type on classpath at descriptor construction time; can't describe types not yet available
**Rationale:** String-based names are the only way to support YAML-declared schemas where the domain type might not be on the classpath (Tier 2 summarisation pipelines, dynamic loading). The type resolver provides built-in primitive mappings, consumer-registered domain types, and optional `Class.forName()` fallback for qualified names.
**Trade-offs:** Requires string → Class resolution step. Type errors surface at compilation time rather than descriptor construction time.
**Sources:** MVEL3 Type.java (uses Class<?>), ThresholdClassifySummariser.java (Map.class context), issue #242 body (per-site typed compilation)
**Exploration:** quick
**Depends on:** D1 (yaml-core ownership means no platform-api dependency, therefore no Class<?>)
**Status:** captured

## D3: Descriptor shape — mirrors MVEL3's ContextType with string types

**Choice:** `TypeDescriptor` sealed interface with `Pojo`, `MapType`, `ListType` variants. Each carries a `ContextKind` (POJO/MAP/LIST) mirroring MVEL3's `ContextType` enum. `PropertyDescriptor` supports nested structures for deep property access validation.
**Alternatives:**
- Flat map of property names to type strings — no nested type support
- JSON Schema subset — heavier, over-engineered for this use case
**Rationale:** MVEL3 already distinguishes POJO/MAP/LIST contexts and generates different evaluator classes for each (PojoEvaluator, MapEvaluator, ArrayEvaluator). Mirroring this in yaml-core's model ensures a clean 1:1 mapping in the bridge. String type names keep it zero-dep while supporting the same structural information.
**Trade-offs:** Slightly more complex than a flat property map, but accurately models the context shapes MVEL3 needs.
**Sources:** MVEL3 ContextType.java (POJO/MAP/LIST/NONE), PojoEvaluator.java, MapEvaluator.java, ArrayEvaluator.java
**Exploration:** quick
**Depends on:** D1 (yaml-core ownership), D2 (string-based type names)
**Status:** captured

## D4: CompiledHandle — generic <C, R>

**Choice:** `CompiledHandle<C, R>` with `R eval(C context)`. Fully generic because MVEL3 code-generates to the actual generic types, not Object.
**Alternatives:**
- Erased handle `CompiledHandle<R>` with `R eval(Object context)` — simpler but incorrect; MVEL3 generates typed bytecode, not Object-based
**Rationale:** MVEL3 generates evaluator classes with the actual type parameters in the bytecode. The generic C is meaningful — the generated code accesses properties on the typed context directly, not via reflection. Erasing to Object would lose this and force unnecessary casting.
**Trade-offs:** The generic C can't be statically enforced at the yaml-core SPI level (descriptor uses strings, not Class<C>), but it is enforced at the bridge level when the class is resolved.
**Sources:** MVEL3 CompilerParameters.java (Declaration<T> contextDeclaration, Type<R> outType), PojoEvaluator.java (generated typed bytecode)
**Exploration:** quick
**Depends on:** D2 (string types resolved to Class<C> in bridge)
**Status:** captured

## D5: Bridge module — yaml-expression in platform

**Choice:** New `yaml-expression` module in platform, depends on yaml-core + platform-api. Contains `TypeResolver` (Map<String, Class<?>> with built-in primitives, consumer registration, Class.forName fallback) and `ExpressionEngineCompiler` (adapts ExpressionCompiler SPI to platform ExpressionEngine). Both agentic-yaml and summarisation-yaml depend on yaml-core for the SPI and yaml-expression for the runtime wiring.
**Alternatives:**
- Add platform-api as dependency to yaml-core — breaks zero-dep/J2CL constraint
- Each blocks module writes its own bridge — duplicates the type resolution logic
**Rationale:** Centralises the string → Class → MVEL3 Type/Declaration pipeline in one place. TypeResolver is shared infrastructure — every YAML module that compiles expressions needs primitive mappings and domain type registration.
**Trade-offs:** One more Maven module in platform. But it naturally parallels the existing platform-expression module (which provides ExpressionEngine implementations).
**Sources:** platform/yaml-core/pom.xml (zero deps constraint), platform-expression (existing ExpressionEngine implementations), agentic-yaml PatternCompiler.java, summarisation-yaml ThresholdClassifySummariser.java
**Exploration:** quick
**Depends on:** D1 (yaml-core owns SPI), D2 (string types need resolver)
**Status:** captured

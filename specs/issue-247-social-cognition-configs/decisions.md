# Decisions — #247 Social Cognition Configs

## D1: Partial YAML via spec records with @Nullable fields

**Choice:** Create spec records (e.g. `DriveConfigSpec`) with all fields `@Nullable`. A compiler merges non-null values with the runtime config's `defaults()` factory. YAML authors write only the fields they want to override.
**Alternatives:**
- Jackson @JsonCreator with default values — mutates the runtime records with Jackson annotations; couples data types to serialization framework
- Map-based overlay via reflection — no spec records needed but loses type safety and IDE schema support
**Rationale:** Same established pattern as PatternSpec → PatternCompiler. Spec records are purpose-built for YAML deserialization — they don't burden the runtime config types with serialization concerns. victools generates schema from the spec records, showing all available fields with types and descriptions.
**Trade-offs:** 13 spec records mirror 13 config records — boilerplate, but each is small (5-15 fields, all @Nullable). Drift between spec and config is caught by the schema drift test (#243) when it lands.
**Sources:** PatternSpec.java (established pattern), DriveConfig.java (representative config), issue #247 body
**Exploration:** quick
**Status:** captured

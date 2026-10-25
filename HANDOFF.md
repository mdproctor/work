# HANDOFF — slot 219 (taxonomy alignment)

## Last Session

Triaged the remaining 3 children of epic **casehubio/casehub-mosaic#283** (Unified agency capability taxonomy). All three were no-ops — agency#353's deliverables already satisfied their scope:

- **neocortex#673** — closed. Neocortex types are runtime SPIs (`*Orchestrator`, `*Synthesizer`), not YAML roots. No naming alignment needed. `COGNITION`/`REFLECTION`/`MEMORY_HYGIENE` already in `CapabilityType` enum.
- **engine#1258** — closed. `CHOREOGRAPHY` and `SAGA` already in `CapabilityType` enum under ORCHESTRATION, correctly marked `hasYamlRoot: false` (configured within CaseDefinition, not standalone YAML documents).
- Filed **Hortora/soredium#474** — work-end doesn't checkout workspace to main after close ceremony.

## Immediate Next Step

**casehubio/casehub-mosaic#284** — last child of the epic. M/Med. Derive TS types from agency's generated schema, build staleness CI test, fix name mismatches, remove orphaned types.

## Context for mosaic#284

Agency#353 delivered:
- `CapabilityType` enum (28 values) + `CapabilityCategory` enum (8 categories) in `agency-core/.../orchestration/execution/`
- `CapabilityCatalogGenerator` — generates `capability-catalog.json` from the enum. Each entry: name (kebab-case), category, `hasYamlRoot` flag
- Naming convention: `*Definition` for YAML document roots, `*Spec` for composable sub-shapes
- Mosaic already has a `catalog-staleness.test.ts` and consumes `capability-catalog.json` — #284 expands this to full type derivation
- Known mismatches to fix: `decomposition` ↔ `HTN`, `channel-execution` ↔ qhorus terminology

Epic mosaic#283 is marked CLOSED on GitHub but mosaic#284 is still OPEN — may need reopening.

## References

- Epic: casehubio/casehub-mosaic#283
- Agency catalog generator: `agency/agentic-yaml/.../AgencySchemaGenerator.java`, `CapabilityCatalogGenerator.java`
- Agency enums: `agency-core/.../orchestration/execution/CapabilityType.java`, `CapabilityCategory.java`
- Mosaic types: `casehub-mosaic/packages/mosaic-core/src/types/patterns/`
- Mosaic staleness test: `casehub-mosaic/packages/mosaic-core/src/types/patterns/catalog-staleness.test.ts`

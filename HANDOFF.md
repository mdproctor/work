# HANDOFF — casehub-neocortex

## Last Session

Implemented #1132 — `EidosLivedDispositionSource` surfaces evolved personality from neocortex to eidos `findEffective()`. Single `@ApplicationScoped` class in `cognitive-eidos/` implementing `LivedDispositionSource` SPI. Uses `PersonalityStateSource` via CDI `Instance<>` for graceful degradation. Called `LivedComposition.profile()` directly rather than routing through `livedDescriptor()` — the existing bridge method drops `ampdFacets` and `situationalProfiles` when rebuilding the disposition.

Code review: clean, no findings. Filed #1147 for the pre-existing `livedDescriptor()` field-drop bug. Updated #1060 spec follow-ups with #1132 (done), #1135, #1147.

## Immediate Next Step

Run `work continue` — queue active on #1135 (extend bridge to compose AMPD facets in findEffective()). This builds directly on #1132: same module, same bridge class, same composition utilities.

## Queue Status

| # | Issue | Scale | Status |
|---|-------|-------|--------|
| 1135 | Extend bridge for AMPD facets | S/Med | **Active** — next |
| 1143 | CBR persistence for learned retention | — | Open |
| 1144 | Per-tenant RetentionConfig adaptation | — | Open |
| 1147 | Fix livedDescriptor field-drop | XS/Low | Open (filed this session) |

## Cross-Repo State

| Repo | Issue | Status |
|------|-------|--------|
| eidos | #245 | **Landed** — LivedDispositionSource SPI + findEffective() |
| neocortex | #1132 | **Closed** — EidosLivedDispositionSource implemented and landed |
| neocortex | #1135 | **Open** — extend bridge for AMPD facets (queue active) |
| neocortex | #1147 | **Open** — livedDescriptor drops ampdFacets/situationalProfiles |

## Context for #1135

The next issue composes AMPD facets (not just disposition profile terms) in `findEffective()`. Key files already read this session:
- `EidosCognitiveProfileBridge.livedDescriptor()` — existing method, only composes profile terms
- `LivedComposition.compose()` — already does facet composition at `DescriptorView` level (homeOffset + promoted + displacement)
- `PersonalityStateSnapshot` — carries `facets` map with `FacetDisplacement(homeOffset, displacement, promoted)`
- The gap: `EidosLivedDispositionSource.resolveEffective()` returns composed profile terms but authored facet scores unchanged

## References

- Blog: `docs/blog/2026-11-18-mdp04-the-loop-that-closed-itself.md`
- Design spec (#1060): `docs/specs/issue-1060-personality-evolution-orchestrator-never-ticked/2026-11-17-unified-personality-evolution-design.md`

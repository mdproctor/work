# Decisions — #362 Coordinator Prompt Tuning for Swarm Coordination

## D1: Scope — all three prompt sites

**Choice:** Wire all three swarm prompt sites (team formation, collective reasoning, provisioning) into the optimisation framework in this issue.
**Alternatives:**
- Team formation only — highest impact but requires a second pass for the other two
- Team formation + reasoning — skip provisioning (fewer invocations, advisory-only)
**Rationale:** The scaffolding types (`SwarmPromptSignatures`, `SwarmOutcomeMetric`, `SwarmOutcomeMapper`) already exist for all three. The wiring is repetitive, not complex — doing all three avoids context-switching overhead of a second pass.
**Trade-offs:** Slightly larger PR. If any one site needs rework, all three are in the same change.
**Sources:** `SwarmPromptSignatures.java` (three constants), issue #362 body (deferred from #285)
**Exploration:** quick
**Status:** captured

## D2: Overall approach — direct wiring via CDI observer

**Choice:** Direct wiring following the proven routing prompt optimisation pattern. SystemPromptCustomiser on all three sites, CDI observer for outcome recording, evolution loop types.
**Alternatives:**
- Event-driven pipeline (EventStreamBus + SummarisationRunner) — adds unnecessary windowed aggregation complexity for a simple write
- Deferred batch-only (scan historical events) — requires persistence layer that doesn't exist for swarm events
**Rationale:** The routing prompt optimisation already proves this exact pattern works. Approach matches existing code structure and keeps the implementation straightforward.
**Trade-offs:** CDI observer coupling — outcome recording only works within a CDI container. Acceptable because swarm coordination already requires CDI.
**Sources:** `OptimisedFewShotSection.java`, `VariantAwareSystemPromptCustomiser.java`, `PromptOptimisationBatch.java`
**Exploration:** quick
**Status:** captured

## D3: Outcome recorder location — swarm/prompt/ package

**Choice:** New `SwarmOutcomeRecorder` `@ApplicationScoped` bean in the existing `orchestration.swarm.prompt` sub-package.
**Alternatives:**
- Extend SwarmCoordinatorBuilder — tighter coupling but guaranteed lifecycle
- In the evolution sub-package — groups batch-cycle plumbing together but separates from swarm prompt types
**Rationale:** Co-located with `SwarmOutcomeMetric`, `SwarmOutcomeMapper`, and `SwarmOptimisedFewShotSection`. Follows the principle of grouping by domain (swarm prompt optimisation) rather than by framework layer.
**Trade-offs:** Slight package growth in `swarm/prompt/`.
**Sources:** `SwarmOutcomeMetric.java`, `SwarmOutcomeMapper.java`
**Exploration:** quick
**Status:** captured

## D4: Few-shot section — generalise existing class

**Choice:** Generalise `SwarmOptimisedFewShotSection` to be parameterised by `PromptSignature`, creating one class that serves all three prompt sites.
**Alternatives:**
- Separate classes per site (SwarmTeamFormationFewShotSection, etc.) — more explicit but mostly copy-paste
**Rationale:** `SwarmOptimisedFewShotSection` already takes a `PromptSignature` at construction. The rendering logic is identical across sites — only the signature and example format differ. Parameterisation eliminates duplication.
**Trade-offs:** If a prompt site later needs radically different few-shot rendering, we'd need to subclass or split. Unlikely given the current uniform structure.
**Sources:** `SwarmOptimisedFewShotSection.java`
**Exploration:** quick
**Status:** captured

## D5: Variant slot selection — at coordinator build time

**Choice:** `SwarmCoordinatorBuilder` calls `VariantSelector.selectSlot()` once per coordinator build and passes the slot to all three prompt sites.
**Alternatives:**
- Per-execution in LlmSwarmCoordinator — more flexible but inconsistent with how other consumers use VariantSelector
- Per-site independent selection — allows independent A/B testing per site but makes outcome correlation across sites impossible
**Rationale:** Consistent by construction — all three prompt sites in a single swarm execution use the same variant slot. Matches how `variantSlot` is already set on `CollectiveReasoningAggregation`. Outcomes can be attributed to a single variant configuration.
**Trade-offs:** Can't independently A/B test team formation vs reasoning vs provisioning prompts. Acceptable for now — cross-site correlation is more valuable than per-site granularity when tuning a coordinated system.
**Depends on:** D1 (all three sites in scope)
**Sources:** `SwarmCoordinatorBuilder.java`, `VariantSelector.java`
**Exploration:** quick
**Status:** captured

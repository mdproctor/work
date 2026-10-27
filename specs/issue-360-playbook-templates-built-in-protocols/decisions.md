## D1: Behavioral fidelity — tiered state machine with metadata

**Choice:** Hybrid — tiered state machine approximation with a metadata section documenting the exact counting rules
**Alternatives:**
- Pure tiered approximation — simpler but loses the precise specification consumers need when forking
- Guard expressions — exact equivalence but requires extending PlaybookProtocolEvaluator with expression support (new platform work)
**Rationale:** The state machine is readable and modifiable; the metadata preserves the precise semantics (e.g., "warn when openQueries >= threshold, default 3") so consumers know exactly what the original protocol does when customizing
**Trade-offs:** Two representations of the same behavior that could theoretically drift, but since metadata is documentation-only the risk is low
**Sources:** RequestResponseProtocol.java, TaskCompletionProtocol.java (qhorus runtime-core), PlaybookProtocolEvaluator tests (platform coordination-core)
**Exploration:** quick
**Status:** captured

## D2: Template location — agency repo

**Choice:** agency-core/src/main/resources/playbook-templates/
**Alternatives:**
- qhorus — closer to the Java implementations but has no YAML surface
- platform — near PlaybookProtocolEvaluator and existing playbooks but these are protocol domain templates, not platform infrastructure
**Rationale:** Agency already owns YAML surfaces for orchestration (agentic-yaml) and summarisation (summarisation-yaml). Protocol templates are the same pattern — YAML definitions for reusable infrastructure
**Trade-offs:** Templates are one repo away from both the Java protocols they model (qhorus) and the runtime that will execute them (platform)
**Sources:** agentic-yaml module structure, summarisation-yaml module structure
**Exploration:** quick
**Status:** captured

## D3: Scope — YAML + docs only, no Java loader

**Choice:** Deliver template YAML files and consumer documentation. No Java registration code.
**Alternatives:**
- YAML + loader + docs — include a ProtocolTemplateLoader that calls PlaybookProtocolEvaluator.register(), useful for testing but speculative since the adapter bridge doesn't exist
**Rationale:** The ChannelProtocol adapter bridge (qhorus#497) doesn't exist yet. Building registration code now couples to an API that may change. The registration mechanism comes naturally when #497 lands.
**Trade-offs:** Templates can't be tested against PlaybookProtocolEvaluator in this repo without manual test setup
**Sources:** qhorus#497 (open), platform#661 (closed — built the evaluator but not the ChannelProtocol bridge)
**Exploration:** quick
**Status:** captured

## D4: Protocol scope — two templates only

**Choice:** REQUEST_RESPONSE and TASK_COMPLETION as specified in the issue
**Alternatives:**
- All four built-in protocols (add CONTRIBUTION_REQUIRED, ROUND_ROBIN) — marginal extra effort but outside issue scope
**Rationale:** Keeps scope tight, faster delivery. The other two are straightforward to add later.
**Trade-offs:** Two protocols remain Java-only until a follow-up issue
**Sources:** Issue #360 deliverables list
**Exploration:** quick
**Status:** captured

## D5: YAML format — playbook envelope

**Choice:** Use the established multi-document YAML format with front-matter (playbook version, schema, name) wrapping the protocol states body
**Alternatives:**
- Bare state map — simpler but doesn't follow established playbook conventions, would need wrapping later
**Rationale:** Consistent with existing playbook YAML in platform/playbooks/. Ready for PlaybookCompiler integration when qhorus#497 lands.
**Trade-offs:** Slightly more verbose than a bare map
**Depends on:** D1 (the states body format follows from the tiered state machine decision)
**Sources:** PlaybookFrontMatter record (platform yaml-core), playbook.schema.json, existing playbook examples in platform/playbooks/
**Exploration:** quick
**Status:** captured

---
layout: post
title: "The Taxonomy That Was Already There"
date: 2026-10-25
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [agentic, taxonomy, architecture, cross-repo]
---

# The Taxonomy That Was Already There

CaseHub has twenty-eight distinct capabilities spread across eight categories — orchestration patterns like supervisor and voting, deliberation protocols like negotiation and conversation, social systems like cognition and memory hygiene, perception pipelines, governance, optimisation. These grew organically across half a dozen repos over two years. Each repo invented its own conventions. Agency used `*Spec` for YAML-backed types. Summarisation used `*Definition`. Cognition used `*Definition` for the root but `*ConfigSpec` for its children. Mosaic's TypeScript reimagined the hierarchy without formal linkage to Java.

The taxonomy epic was supposed to fix this — unify naming, add a category enum, connect the dots across repos. And it did, but what surprised me was how much of the unification was already latent in the codebase.

## The Convention Hidden in the Inconsistency

When I looked at the actual usage patterns, the `*Spec` vs `*Definition` split wasn't random. Every standalone YAML document root — the thing a user authors as a top-level file — used `*Definition`. Every composable sub-shape nested inside those documents used `*Spec`. `PatternSpec` was the one exception: a YAML root wearing a sub-shape suffix. The inconsistency was skin deep.

The fix was to rename three types — `PatternSpec` to `PatternDefinition`, `NegotiationSpec` to `NegotiationDefinition`, `ConversationSpec` to `ConversationDefinition` — and codify the rule. Document roots are definitions. Composable fragments are specs. What looked like a naming war was a convention waiting to be named.

## Twenty-Eight Capabilities, One Enum

The `CapabilityType` enum is the real deliverable. Twenty-eight entries across eight `CapabilityCategory` values, each carrying a `hasYamlRoot` flag that distinguishes YAML-backed capabilities (the ones with their own compilers and document schemas) from runtime services (the ones configured inline or through code). A `CapabilityCatalogGenerator` produces a JSON artifact from the enum — one source of truth that both Java and TypeScript can consume.

The interesting distinction is the two tiers. Capabilities like `PatternDefinition` and `NegotiationDefinition` are formal — they have standalone YAML schemas, dedicated compilers, and their own compilation pipelines. Capabilities like `CHOREOGRAPHY` and `SAGA` are real user-facing features with YAML configuration, but they live inside `CaseDefinition` rather than as independent documents. `REFLECTION` and `MEMORY_HYGIENE` are pure runtime services with no YAML surface at all. The `hasYamlRoot` flag captures this spectrum without forcing false uniformity.

## The Downstream That Wasn't

The epic had four child issues: agency (the foundation), neocortex (naming alignment), engine (decide choreography/saga status), and mosaic (derive TypeScript types from the schema). I expected each downstream issue to require real changes. They didn't.

Neocortex's types — `ReflectionSynthesizer`, `ReflectionOrchestrator`, `MemoryHygieneOrchestrator` — are runtime service SPIs. They use service naming (`*Orchestrator`, `*Synthesizer`), not configuration naming. The `*Definition` convention doesn't apply to them. The issue assumed `CognitionDefinition` lived in neocortex; it actually lives in agency. All three capabilities were already registered in the enum. Nothing to do.

Engine's choreography and saga subsystems were already in `CapabilityType` under `ORCHESTRATION`, correctly marked `hasYamlRoot: false`. The question "should these become formal capabilities?" had been answered by including them in the enum. The question "should they have standalone YAML roots?" was answered by their architecture — they're planning strategies configured within case definitions, not independent documents.

Both issues closed as no-ops. The foundation work in agency had already done the thinking; the downstream repos just needed to confirm they were aligned. Speculative issues written before the foundation lands are often like this — the decisions they anticipated get made elsewhere.

## What Remains

One child issue is genuinely substantive: the mosaic alignment. TypeScript needs to derive its category types from the generated JSON schema rather than maintaining hand-written parallel definitions. A staleness CI test already exists and passes, but the type derivation itself — replacing loose `{ type: string }` definitions with types generated from the Java sealed hierarchies — is real work. That's the last piece before the taxonomy is mechanically enforced across the language boundary.

The broader pattern is worth noting. Cross-repo type alignment epics tend to look large from the outside — four repos, two languages, dozens of types. But when the foundation is well-designed, most of the alignment is already implicit. The taxonomy didn't impose order on chaos. It named the order that was already there.

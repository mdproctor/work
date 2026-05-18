# Work — Squash Plan v3
*Generated: 2026-05-08*  
*Backup: `backup/pre-squash-main-20260507`*  
*Mode: Flat compaction (no merge PRs)*  
*Awaiting YES to execute — do not squash until approved.*

---

## Phase 0 — filter-repo

| | |
|---|---|
| Stripped | `HANDOFF.md`, `blog/*` |
| Commits pruned | 37 |
| Commits remaining | 343 |

---

## Summary

| | |
|---|---|
| Already clean | 235 commits |
| Compaction groups | 38 |
| Commits to absorb | 70 |
| **Result** | **380 → ~273 commits — 70 absorbed** |

---

## Already Clean — 235 commits

| Capability | Commits | What was built |
|------------|---------|----------------|
| docs | 51 | docs: add comprehensive API reference, integr; docs: expand README opening to cover agent us... |
| feat | 32 | feat: initial Quarkus Tarkus project scaffold; feat: Uni<String> bridge API and TarkusFlow D... |
| fix | 18 | fix: add quarkus.http.test-port=0 to prevent ; fix: sweep... |
| core | 17 | WorkItemNote; SSE live event stream... |
| refactor | 11 | refactor: update all Java package declaration; refactor: rename TarkusConfig/Processor/Flow/... |
| issue-tracker | 10 | quarkus-workitems-issue-tracker; WebhookEvent types + IssueTrackerProvider + W... |
| examples | 9 | scaffold quarkus-tarkus-examples module; add shared response types... |
| queues | 9 | update spec; WorkItemFilter CRUD, JEXL/JQ/Lambda evaluator... |
| workitems-ai | 9 | add WorkerSkillProfile entity and V14 migrati; add WorkerSkillProfileResource REST API... |
| runtime | 8 | add rationale/planRef overloads to WorkItemSe; add WorkItemSpawnGroup entity + Flyway V18... |
| work-api | 6 | move SPI routing types to io.quarkiverse.work; add WorkEventType enum and WorkLifecycleEvent... |
| ledger | 5 | implement ActorTrustScore and EigenTrust nigh; migrate to quarkus-ledger as shared extension... |
| dashboard | 5 | Tamboui queue board POC + scenario step delay; Quarkus-embedded Tamboui dashboard via @Quark... |
| filter-registry | 5 | module scaffold + FilterAction SPI types; JexlConditionEvaluator + FilterRegistryEngine... |
| work-core | 5 | add WorkBroker; move routing strategies and NoOpWorkerRegistr... |
| api | 4 | add WorkEventType.SPAWNED and SpawnPort SPI; BusinessCalendar and HolidayCalendar SPIs... |
| chore | 3 | chore: close epics #77, #78, #81; chore: close Epic #80... |
| workitems | 3 | add WorkItemContextBuilder, convert WorkItemL; add JpaWorkloadProvider, wire WorkBroker into... |
| test | 2 | test: fill coverage gaps in Phase 1 and 2 bef; test: verify WorkItemEventBroadcaster @Defaul |
| flow | 2 | add fn() helper to TarkusFlow; rename tarkus() → workItem() in TarkusFlow DS |
| queues-examples | 2 | 4 real-world queue routing scenarios; document review pipeline |
| mongodb | 2 | add quarkus-workitems-persistence-mongodb mod; implement query() and count(AuditQuery) in Mo |
| ai | 2 | GET /workitems/{id}/resolution-suggestion; escalation summarisation |
| extension | 1 | implement Phase 1 core data model and service |
| rest | 1 | implement Phase 2 REST API |
| scheduler | 1 | implement Phase 3 lifecycle engine |
| events | 1 | implement Phase 4 CDI event emission on all W |
| integration | 1 | implement Phase 5 quarkus-tarkus-flow CDI bri |
| native | 1 | implement Phase 8 native image validation |
| scaffold | 1 | add quarkus-tarkus-ledger dep, remove spuriou |
| claude-md | 1 | add examples module, quarkus-ledger prereq, d |
| tarkus-flow | 1 | add README |
| flow-examples | 1 | quarkus-tarkus-flow-examples |
| vocabulary | 1 | support ORG/TEAM/PERSONAL scopes in POST /voc |
| #122 | 1 | semantic routing example + integration guide  |
| notifications | 1 | delegate HTTP channels to casehub-connectors |
| migration | 1 | V5001 rename priority values NORMAL->MEDIUM,  |
| testing | 1 | InMemoryIssueLinkStore |

---

## Compaction Groups — 38 groups, 70 commits absorbed

## docs: add documentation links to README
*Compaction group 1 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `d28129a` docs: add documentation links to README | ✅ KEEP | *(message adequate — unchanged)* |
| `9de59ac` docs: session wrap — CLAUDE.md gotchas, blog entry 2026-04-14 | 🔽 SQUASH ↑ | *(session handover — mixed content)* |
📝 *- CLAUDE.md: tarkus-flow and integration-tests promoted from future to built;*

> **Result:** 1 commit.

---

## feat(ledger): implement quarkus-tarkus-ledger optional accountability module
*Compaction group 2 — 2 commits → 1*
**Synthesised body** *(will be appended to the commit body on execution):*
> [Plan: docs: add ledger/audit/provenance design specification]

| Commit | Action | Curated result |
|--------|--------|----------------|
| `cac7ab9` feat(ledger): implement quarkus-tarkus-ledger optional accountability modul | ✅ KEEP | *(subject adequate; synthesised body above appended to commit)* |
| `ff47aec` docs: add ledger/audit/provenance design specification | 🔽 SQUASH ↑ | *(planning doc — see Synthesised body)* |
📝 *Comprehensive design covering 6 capabilities:*

> **Result:** 1 commit.

---

## docs+refactor: health check fixes — DESIGN.md sync, docs update, LedgerEventCapture cleanup
*Compaction group 3 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `0271b51` docs+refactor: health check fixes — DESIGN.md sync, docs update, LedgerEven | ✅ KEEP | *(message adequate — unchanged)* |
| `2000805` docs: session wrap — CLAUDE.md gotchas, blog entry, handover 2026-04-15 | 🔽 SQUASH ↑ | *(session handover — mixed content)* |
📝 *CLAUDE.md: two new testing gotchas (quarkus.http.test-port=0, @TestTransaction + REST),*

> **Result:** 1 commit.

---

## docs: rename plan quarkus-tarkus→quarkus-workitems
*Compaction group 4 — 2 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `cbcc135` docs: rename plan quarkus-tarkus→quarkus-workitems
*(message adequate — unchanged)*
> Absorbed: refactor: rename module directories tarkus→wo

> **Result:** 1 commit.

---

## refactor(pom): rename Maven coordinates io.quarkiverse.tarkus→workitems
*Compaction group 5 — 3 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `51f4da7` refactor(pom): rename Maven coordinates io.quarkiverse.tarkus→workitems
*(message adequate — unchanged)*
> Absorbed: refactor: move Java source directories tarkus; fix: correct integration-tests source directo

> **Result:** 1 commit.

---

## feat(labels): WorkItemLabel entity, Flyway V2, MANUAL/INFERRED persistence
*Compaction group 6 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `8b6f954` feat(labels): WorkItemLabel entity, Flyway V2, MANUAL/INFERRED persistence | ✅ KEEP | *(message adequate — unchanged)* |
| `4fc27e1` fix: anchor META-INF/ and io/ gitignore patterns to project root | 🔽 SQUASH ↑ | *(absorbed — fix)* |
📝 *Unanchored patterns were silently ignoring new source files anywhere*

> **Result:** 1 commit.

---

## feat(vocabulary): LabelVocabulary and LabelDefinition with scoped hierarchy
*Compaction group 7 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `07c90d5` feat(vocabulary): LabelVocabulary and LabelDefinition with scoped hierarchy | ✅ KEEP | *(message adequate — unchanged)* |
| `36addef` fix: anchor db/ gitignore pattern to project root | 🔽 SQUASH ↑ | *(absorbed — fix)* |
📝 *Bare db/ was silently ignoring new Flyway migration files anywhere*

> **Result:** 1 commit.

---

## feat(labels): label query by pattern, add/remove MANUAL label endpoints
*Compaction group 8 — 2 commits → 1*
**Synthesised body** *(will be appended to the commit body on execution):*
> [Plan: docs: implementation plan for core label model (sub-epic #51, issues #53-#55)]

| Commit | Action | Curated result |
|--------|--------|----------------|
| `c10185b` feat(labels): label query by pattern, add/remove MANUAL label endpoints | ✅ KEEP | *(subject adequate; synthesised body above appended to commit)* |
| `9bebef1` docs: implementation plan for core label model (sub-epic #51, issues #53-#5 | 🔽 SQUASH ↑ | *(planning doc — see Synthesised body)* |

> **Result:** 1 commit.

---

## feat(queues): quarkus-workitems-queues module scaffold
*Compaction group 9 — 2 commits → 1*
**Synthesised body** *(will be appended to the commit body on execution):*
> [Plan: docs: implementation plan for quarkus-workitems-queues module (sub-epic #52)]

| Commit | Action | Curated result |
|--------|--------|----------------|
| `cd759da` feat(queues): quarkus-workitems-queues module scaffold | ✅ KEEP | *(subject adequate; synthesised body above appended to commit)* |
| `1965ae8` docs: implementation plan for quarkus-workitems-queues module (sub-epic #52 | 🔽 SQUASH ↑ | *(planning doc — see Synthesised body)* |

> **Result:** 1 commit.

---

## feat(core): WorkItemTemplate — predefined WorkItem blueprints (#76)
*Compaction group 10 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `1f9e85a` feat(core): WorkItemTemplate — predefined WorkItem blueprints (#76) | ✅ KEEP | *(message adequate — unchanged)* |
| `a8a9192` chore: add epic structure — 5 active epics for retrospective issue grouping | 🔽 SQUASH ↑ | *(absorbed — chore)* |
📝 *Epics created via issue-workflow retrospective:*

> **Result:** 1 commit.

---

## feat(core): distributed schedule execution — @Version + REQUIRES_NEW prevents double-fire (#94)
*Compaction group 11 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `889d165` feat(core): distributed schedule execution — @Version + REQUIRES_NEW preven | ✅ KEEP | *(message adequate — unchanged)* |
| `e51398e` docs: session handover 2026-04-20 | 🔽 SQUASH ↑ | *(session handover — mixed content)* |
📝 *- WorkItemLink, AsyncAPI spec, Micrometer metrics delivered*

> **Result:** 1 commit.

---

## feat(workitems-ai): LowConfidenceFilterProducer — confidence-gated routing
*Compaction group 12 — 3 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `a9d5f05` feat(workitems-ai): LowConfidenceFilterProducer — confidence-gated routing
*(message adequate — unchanged)*
> Absorbed: docs: design spec — confidence-gated routing ; docs: implementation plan — confidence-gated 

> **Result:** 1 commit.

---

## feat(api): quarkus-workitems-api module — shared WorkerSelectionStrategy SPI
*Compaction group 13 — 3 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `e330ab0` feat(api): quarkus-workitems-api module — shared WorkerSelectionStrategy SP
*(message adequate — unchanged)*
> Absorbed: docs: design spec — WorkerSelectionStrategy (; docs: implementation plan — WorkerSelectionSt

> **Result:** 1 commit.

---

## docs: update DESIGN.md and CLAUDE.md for WorkerSelectionStrategy
*Compaction group 14 — 4 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `7e8d31a` docs: update DESIGN.md and CLAUDE.md for WorkerSelectionStrategy
*(message adequate — unchanged)*
> Absorbed: docs: update CLAUDE.md — Jandex, Hibernate re; chore: add .worktrees/ to .gitignore; chore: ensure .worktrees/ ignored (trailing s

> **Result:** 1 commit.

---

## feat(workitems): move filter actions to runtime with new Object workUnit signature
*Compaction group 15 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `81af87f` feat(workitems): move filter actions to runtime with new Object workUnit si | ✅ KEEP | *(message adequate — unchanged)* |
| `78340b8` chore: delete quarkus-workitems-api and quarkus-workitems-filter-registry | 🔽 SQUASH ↑ | *(absorbed — chore)* |
📝 *Both modules absorbed into quarkus-work-api and quarkus-work-core respectively.*
| `69cbdd1` docs: update CLAUDE.md for quarkus-work-api/work-core separation | 🔽 SQUASH ↑ | *(absorbed — docs)* |
📝 *Module table updated: quarkus-workitems-api and quarkus-workitems-filter-registry*

> **Result:** 1 commit.

---

## feat: quarkus-work-api + quarkus-work-core separation (#118)
*Compaction group 16 — 3 commits → 1*
**Synthesised body** *(will be appended to the commit body on execution):*
> [Plan: docs: design spec for quarkus-work / quarkus-workitems separation]
> [Plan: docs: implementation plan for quarkus-work separation]

| Commit | Action | Curated result |
|--------|--------|----------------|
| `e4ae875` feat: quarkus-work-api + quarkus-work-core separation (#118) | ✅ KEEP | *(subject adequate; synthesised body above appended to commit)* |
| `6d724f8` docs: design spec for quarkus-work / quarkus-workitems separation | 🔽 SQUASH ↑ | *(planning doc — see Synthesised body)* |
📝 *Defines quarkus-work-api (SPI contracts) and quarkus-work-core*
| `528a38d` docs: implementation plan for quarkus-work separation | 🔽 SQUASH ↑ | *(planning doc — see Synthesised body)* |
📝 *21 tasks covering quarkus-work-api scaffold, quarkus-work-core scaffold,*

> **Result:** 1 commit.

---

## feat: semantic skill matching — SkillProfile SPI stack + EmbeddingSkillMatcher (#121)
*Compaction group 17 — 3 commits → 1*
**Synthesised body** *(will be appended to the commit body on execution):*
> [Plan: docs: design spec for semantic skill matching (Epic #100)]
> [Plan: docs: implementation plan for semantic skill matching]

| Commit | Action | Curated result |
|--------|--------|----------------|
| `1f4c3f0` feat: semantic skill matching — SkillProfile SPI stack + EmbeddingSkillMatc | ✅ KEEP | *(subject adequate; synthesised body above appended to commit)* |
| `a12ef75` docs: design spec for semantic skill matching (Epic #100) | 🔽 SQUASH ↑ | *(planning doc — see Synthesised body)* |
📝 *SkillProfile + SkillProfileProvider + SkillMatcher SPIs in quarkus-work-api.*
| `d3124b9` docs: implementation plan for semantic skill matching | 🔽 SQUASH ↑ | *(planning doc — see Synthesised body)* |
📝 *16 tasks across 7 phases: SPIs in work-api, WorkerSkillProfile entity + REST,*

> **Result:** 1 commit.

---

## feat(ai): #119 CompositeSkillProfileProvider + #120 LeastLoaded fallback (#100)
*Compaction group 18 — 2 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `6a556cf` feat(ai): #119 CompositeSkillProfileProvider + #120 LeastLoaded fallback (#
*(message adequate — unchanged)*
> Absorbed: chore: update repo references to mdproctor/qu

> **Result:** 1 commit.

---

## fix(runtime): override merging + deterministic idempotent child lookup
*Compaction group 19 — 3 commits → 1*
**Synthesised body** *(will be appended to the commit body on execution):*
> Uses 'system:spawn:{groupId}' createdBy filter instead of timestamp

| Commit | Action | Curated result |
|--------|--------|----------------|
| `1c47b6f` fix(runtime): override merging + deterministic idempotent child lookup | ✅ KEEP | *(subject adequate; synthesised body above appended to commit)* |
| `81b9ff9` fix(runtime): scope cancelGroup cascade to specific spawn group children | 🔽 SQUASH ↑ | *(absorbed — fix)* |
📝 *Uses 'system:spawn:{groupId}' createdBy marker to cancel only children*
| `11b7c44` fix(runtime): scope GET /spawn-groups/{id} children to specific group | 🔽 SQUASH ↑ | *(absorbed — fix)* |
📝 *Uses 'system:spawn:{groupId}' createdBy filter instead of timestamp*

> **Result:** 1 commit.

---

## docs: update DESIGN.md — spawn SPI, filter engine relocation, callerRef, SpawnGroup
*Compaction group 20 — 5 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `53b7307` docs: update DESIGN.md — spawn SPI, filter engine relocation, callerRef, Sp | ✅ KEEP | *(message adequate — unchanged)* |
| `1b8e735` build: bump to 0.2-SNAPSHOT; casehub-parent BOM + GitHub Packages publishin | 🔽 SQUASH ↑ | *(absorbed — build)* |
📝 *- Version bump: 1.0.0-SNAPSHOT → 0.2-SNAPSHOT across all modules*
| `06dbfc0` ci: wire github-casehubio server credentials for GitHub Packages auth | 🔽 SQUASH ↑ | *(absorbed — ci)* |
📝 *setup-java only configured the 'github' server id; casehub-parent is*
| `4416470` ci: simplify GitHub Packages auth — rename repo id to 'github' | 🔽 SQUASH ↑ | *(absorbed — ci)* |
📝 *Rename repository id github-casehubio → github in pom.xml so it*
| `0c6fb4c` docs(claude): add ecosystem conventions — Quarkus version, GitHub Packages, | 🔽 SQUASH ↑ | *(absorbed — docs)* |

> **Result:** 1 commit.

---

## feat: scaffold quarkus-work-reports module; remove scaffold from runtime
*Compaction group 21 — 2 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `faa2128` feat: scaffold quarkus-work-reports module; remove scaffold from runtime
*(message adequate — unchanged)*
> Absorbed: fix: add quarkus-flyway dep, remove redundant

> **Result:** 1 commit.

---

## feat: Epic #104 — quarkus-work-reports SLA compliance reporting module
*Compaction group 22 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `44398a0` feat: Epic #104 — quarkus-work-reports SLA compliance reporting module | ✅ KEEP | *(message adequate — unchanged)* |
| `ad026d0` chore: add build discipline scripts and CLAUDE.md rules | 🔽 SQUASH ↑ | *(absorbed — chore)* |
📝 *scripts/mvn-test, mvn-install, mvn-compile, check-build — each with hard*

> **Result:** 1 commit.

---

## fix: PostgreSQL-compatible SQL types in all Flyway migrations; MODE=PostgreSQL in H2 test URLs
*Compaction group 23 — 2 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `763b348` fix: PostgreSQL-compatible SQL types in all Flyway migrations; MODE=Postgre
*(message adequate — unchanged)*
> Absorbed: fix: add MODE=PostgreSQL to remaining H2 test

> **Result:** 1 commit.

---

## feat: add WorkItemGroupLifecycleEvent to quarkus-work-api
*Compaction group 24 — 3 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `9ba30ac` feat: add WorkItemGroupLifecycleEvent to quarkus-work-api
*(message adequate — unchanged)*
> Absorbed: fix: align factory parameter name groupStatus; fix: update factory body reference status → g

> **Result:** 1 commit.

---

## refactor: rename to casehub-work — groupId io.casehub, package io.casehub.work
*Compaction group 25 — 7 commits → 1*
**Final message:** `refactor: rename to casehub-work — groupId io.casehub, package io.casehub.work; stale refs updated`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `1287368` refactor: rename to casehub-work — groupId io.casehub, package io.casehub.w | ✅ KEEP | *(see Final message above)* |
| `00fcbc2` docs: fix stale test counts after PostgreSQL dialect test enabled (68→73) | 🔽 SQUASH ↑ | *(stale ref; reflected in Final message)* |
| `45c7500` docs(claude): update CLAUDE.md — fix stale paths post-ecosystem rename | 🔽 SQUASH ↑ | *(stale ref; reflected in Final message)* |
📝 *- doc URLs: quarkus-ledger.md → casehub-ledger.md, quarkus-qhorus.md → casehub-qhorus.md*
| `3b2d3c7` docs: fix stale repo name references post-rename | 🔽 SQUASH ↑ | *(stale ref; reflected in Final message)* |
| `a90c768` docs: fix stale casehubio/quarkus-work repo refs in planning docs → casehub | 🔽 SQUASH ↑ | *(stale ref; reflected in Final message)* |
| `e55db32` docs: fix stale Quarkus WorkItems title → CaseHub Work in CLAUDE.md | 🔽 SQUASH ↑ | *(stale ref; reflected in Final message)* |
| `af69bad` fix: update casehub-parent and casehub-connectors groupId to io.casehub | 🔽 SQUASH ↑ | *(absorbed — fix)* |
📝 *- Root pom.xml BOM import: io.casehubio:casehub-parent → io.casehub:casehub-parent*

> **Result:** 1 commit.

---

## docs: remove Quarkiverse positioning — casehub org is home
*Compaction group 26 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `6db9a86` docs: remove Quarkiverse positioning — casehub org is home | ✅ KEEP | *(message adequate — unchanged)* |
| `4454e8e` refactor: move source directories from io/quarkiverse/work to io/casehub/wo | 🔽 SQUASH ↑ | *(absorbed — refactor)* |
📝 *Package declarations were already updated to io.casehub.work.* but source files*
| `905977e` docs: update CLAUDE.md project structure paths to io/casehub/work | 🔽 SQUASH ↑ | *(absorbed — docs)* |
📝 *Follows the source directory move in the previous commit.*

> **Result:** 1 commit.

---

## chore: add epic #147 (Project Refinement) to active epics table in CLAUDE.md
*Compaction group 27 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `b32b0f3` chore: add epic #147 (Project Refinement) to active epics table in CLAUDE.m | ✅ KEEP | *(message adequate — unchanged)* |
| `7062849` chore: rename stale 'Quarkus WorkItems' and 'quarkus-work-ledger' to CaseHu | 🔽 SQUASH ↑ | *(absorbed — chore)* |
📝 *Replaces all occurrences of 'Quarkus WorkItems' with 'CaseHub Work' and*

> **Result:** 1 commit.

---

## fix: correct ledger config prefix in work-examples application.properties
*Compaction group 28 — 4 commits → 1*
**Synthesised body** *(will be appended to the commit body on execution):*
> Properties were using casehub.work.ledger.* instead of casehub.ledger.*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `4992f2e` fix: correct ledger config prefix in work-examples application.properties | ✅ KEEP | *(subject adequate; synthesised body above appended to commit)* |
| `ecb8020` fix(examples): remove candidateUsers from vocabulary scenario WorkItem crea | 🔽 SQUASH ↑ | *(absorbed — fix)* |
📝 *candidateUsers=ACTOR_MANAGER triggered WorkItemAssignmentService auto-assignment,*
| `9cadf3f` fix(test): increase TUI pilot pause to reduce timing flakiness in full buil | 🔽 SQUASH ↑ | *(absorbed — fix)* |
📝 *press_r_resets_to_step0 uses fixed 500ms pauses that are insufficient*
| `1c047bc` ci: add workflow_dispatch trigger to publish workflow | 🔽 SQUASH ↑ | *(absorbed — ci)* |

> **Result:** 1 commit.

---

## feat(ci): add repository_dispatch trigger for upstream-published events
*Compaction group 29 — 2 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `e7fd2c4` feat(ci): add repository_dispatch trigger for upstream-published events
*(message adequate — unchanged)*
> Absorbed: ci: standardise publish workflow — consistent

> **Result:** 1 commit.

---

## fix: suppress BackPressureFailure in WorkItemEventBroadcaster when no SSE subscribers
*Compaction group 30 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `aa65336` fix: suppress BackPressureFailure in WorkItemEventBroadcaster when no SSE s | ✅ KEEP | *(message adequate — unchanged)* |
| `03e49d0` chore: add jandex-maven-plugin for CDI bean discovery | 🔽 SQUASH ↑ | *(absorbed — chore)* |
📝 *Library JARs require META-INF/jandex.idx for Quarkus to discover CDI*

> **Result:** 1 commit.

---

## docs: document Flyway migration version numbering convention
*Compaction group 31 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `3dc689c` docs: document Flyway migration version numbering convention | ✅ KEEP | *(message adequate — unchanged)* |
| `7f4450f` fix: renumber casehub-work-issue-tracker migration V3000 → V5000 | 🔽 SQUASH ↑ | *(absorbed — fix)* |
📝 *Resolves conflict with casehub-work-notifications which also uses V3000.*

> **Result:** 1 commit.

---

## refactor: extract ExpiryLifecycleService from expiry job classes
*Compaction group 32 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `0a2e09a` refactor: extract ExpiryLifecycleService from expiry job classes | ✅ KEEP | *(message adequate — unchanged)* |
| `732e506` chore: pin jandex-maven-plugin 3.1.2 in root pluginManagement | 🔽 SQUASH ↑ | *(absorbed — chore)* |
| `69f64fd` fix(test): capture Instant.now() before REST call in BusinessHoursIntegrati | 🔽 SQUASH ↑ | *(absorbed — fix)* |
📝 *Business hours calculation rounds up to next minute boundary. Calling*

> **Result:** 1 commit.

---

## docs: document casehub-ledger 0.2-SNAPSHOT configurable datasource
*Compaction group 33 — 2 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `854f266` docs: document casehub-ledger 0.2-SNAPSHOT configurable datasource
*(message adequate — unchanged)*
> Absorbed: ci: use GH_PAT for cross-repo repository_disp

> **Result:** 1 commit.

---

## docs: correct MongoDB persistence module status — implemented, not future
*Compaction group 34 — 2 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `12cfeee` docs: correct MongoDB persistence module status — implemented, not future
*(message adequate — unchanged)*
> Absorbed: fix(test): eliminate flakiness in WorkItemGro

> **Result:** 1 commit.

---

## feat: sync WorkItem fields to GitHub Issue labels and state on lifecycle transitions (#157)
*Compaction group 35 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `a16ea4d` feat: sync WorkItem fields to GitHub Issue labels and state on lifecycle tr | ✅ KEEP | *(message adequate — unchanged)* |
| `774ac41` docs: session handover 2026-05-01 — distributed SSE and GitHub sync | 🔽 SQUASH ↑ | *(session handover — mixed content)* |
| `ed039f7` fix(test): widen BusinessHoursIntegrationTest claim deadline bound to 4 day | 🔽 SQUASH ↑ | *(absorbed — fix)* |
📝 *2 business hours from Friday after-hours resolves to Monday morning (~3.6*

> **Result:** 1 commit.

---

## refactor(test): extract BusinessHoursAssert helper for robust deadline assertions (#158)
*Compaction group 36 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `befe8f9` refactor(test): extract BusinessHoursAssert helper for robust deadline asse | ✅ KEEP | *(message adequate — unchanged)* |
| `21beb9e` fix(test): eliminate OCC race in completedEventFiresExactlyOnceAtThreshold | 🔽 SQUASH ↑ | *(absorbed — fix)* |
📝 *Root cause: onThresholdReached defaults to CANCEL in MultiInstanceSpawnService.*

> **Result:** 1 commit.

---

## fix: implement new LedgerEntryRepository methods from casehub-ledger 0.2-SNAPSHOT
*Compaction group 37 — 2 commits → 1*

*All absorbed commits are pure noise — no body content, flags, or message changes needed.*

✅ KEEP `7fd12bc` fix: implement new LedgerEntryRepository methods from casehub-ledger 0.2-SN
*(message adequate — unchanged)*
> Absorbed: docs: update CLAUDE.md — fork/PR workflow, SN

> **Result:** 1 commit.

---

## docs: update ARCHITECTURE.md, CLAUDE.md, DESIGN.md for IssueLinkStore (#161)
*Compaction group 38 — 11 commits → 1*
⚠️ **Net no-op pair:** migrate+restore — combined tree effect zero.

| Commit | Action | Curated result |
|--------|--------|----------------|
| `c68162c` docs: update ARCHITECTURE.md, CLAUDE.md, DESIGN.md for IssueLinkStore (#161 | ✅ KEEP | *(message adequate — unchanged)* |
| `ba3550d` docs: update CLAUDE.md — AFTER_SUCCESS scope expanded, Instance<T> Mockito  | 🔽 SQUASH ↑ | *(absorbed — docs)* |
| `9d0c437` chore: move ADRs to docs/adr/ — MADR/Java convention | 🔽 SQUASH ↑ | *(absorbed — chore)* |
| `ae8c0bc` chore: consolidate specs to docs/specs/ — canonical location | 🔽 SQUASH ↑ | *(absorbed — chore)* |
| `6315990` chore: flatten docs/architecture/ — single file moved to docs/  no-issue: s | 🔽 SQUASH ↑ | *(absorbed — chore)* |
| `a2b3edb` chore: migrate CLAUDE.md and methodology artifacts to workspace | 🔽 SQUASH ↑ | *(absorbed — chore)* |
| `11cc778` chore: restore CLAUDE.md to project repo (workspace symlinks to this) | 🔽 SQUASH ↑ | *(absorbed — chore)* |
| `965dba2` chore: ignore wksp symlink | 🔽 SQUASH ↑ | *(absorbed — chore)* |
| `4133f66` chore: use local paths for PLATFORM.md and deep-dive docs instead of GitHub | 🔽 SQUASH ↑ | *(absorbed — chore)* |
| `19e2e06` chore: platform docs — use local Read, fall back to WebFetch if not cloned | 🔽 SQUASH ↑ | *(absorbed — chore)* |
| `07ca841` chore: add Project Artifacts section to CLAUDE.md | 🔽 SQUASH ↑ | *(absorbed — chore)* |

> **Result:** 1 commit.

---

## AFTER — post-squash simulation

```
  380  commits on backup/pre-squash-main-20260507
   -37  pruned by filter-repo
   -70  absorbed by squash
  ──────────────────────────────────────
   ~273  commits — no content lost
```

Sample (most recent 10 KEEP commits — post-squash simulation):
```
  c68162c  docs: update ARCHITECTURE.md, CLAUDE.md, DESIGN.md for IssueLinkStore (#161)
  6b6f96d  refactor(issue-tracker): IssueLinkService injects IssueLinkStore (#161)
  a66f646  refactor(issue-tracker): WebhookEventHandler injects IssueLinkStore + WorkItemStore (
  b871b13  feat(testing): InMemoryIssueLinkStore — in-memory IssueLinkStore for tests (#161)
  725474d  feat(issue-tracker): IssueLinkStore SPI + JpaIssueLinkStore (#161)
  324f106  docs: IssueLinkStore SPI implementation plan (#161)
  4f74ff5  docs: IssueLinkStore SPI design spec (#161)
  5c02cc5  feat(issue-tracker): JiraWebhookResource — POST /workitems/jira-webhook (#156)
  cad2060  feat(issue-tracker): Jira webhook parser + config + fixtures (#156)
  209cdf1  feat(issue-tracker): GitHubWebhookResource — POST /workitems/github-webhook (#156)
```

*(After squash executes, verify with: `git log --oneline b51f64dfdb9a822e45f79b0e4e0bb1b2f9e4e322..squash/wip-main-*`)*

---

## Quality check (opt-in at review gate)

After squash executes, you can request a quality check: subject length vs diff size,
missing rationale bodies, non-conventional subjects. Run only if desired — not automatic.

---

## Approval

Reply **YES** to execute, or specify groups to change.
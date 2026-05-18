# Work — Squash Plan
*Generated: 2026-05-07*  
*Working branch: `squash/wip-main-20260507-043704`*  
*Reference: [engine-reconstruction-plan.md](https://github.com/mdproctor/casehub/blob/main/docs/superpowers/specs/engine-reconstruction-plan.md)*

---

## Phase 0 — filter-repo (complete)

| | |
|---|---|
| Stripped | `HANDOFF.md` (36 commits), `blog/` — 17 files |
| Commits pruned (became empty) | 37 |
| Commits remaining for compaction | 342 |

---

## Summary

| | |
|---|---|
| Already clean (no action) | 253 commits |
| Compaction groups | 42 |
| Commits to absorb | 46 |
| **Result** | **379 → 296 commits — 46 absorbed, no content lost** |

---

## Already Clean — 253 commits (no action needed)

*To see all: `git log --oneline 60b4d6ba7a7462dff23ec49d09298c528e57d31f..squash/wip-main-20260507-043704` for the compacted list.*

Representative: rest, scheduler, events, integration, native, ledger, examples, scaffold, runtime, claude-md, flow-examples, flow, pom, queues, labels...

---

## Compaction Groups — 42 groups, 85 commits absorbed

## feat(extension): implement Phase 1 core data model and service layer
*Compaction group 1 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `9ecd88c` feat(extension): implement Phase 1 core data model and service layer | ✅ KEEP | *(message adequate — unchanged)* |
| `b51f64d` feat: initial Quarkus Tarkus project scaffold | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)*
📝 *body: New standalone Quarkiverse extension providing human-scale WorkItem* |

> **Result:** 1 commit.

---

## docs: expand README opening to cover agent use cases
*Compaction group 2 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `0a905b5` docs: expand README opening to cover agent use cases | ✅ KEEP | *(message adequate — unchanged)* |
| `1c957c1` docs: session wrap — CLAUDE.md gotchas, blog entry 2026-04-14 | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)*
📝 *body: - CLAUDE.md: tarkus-flow and integration-tests promoted from future to built;* |
| `50a623b` docs: session handover 2026-04-14 | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |

> **Result:** 1 commit.

---

## docs: examples module design spec
*Compaction group 3 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `85b753f` docs: examples module design spec | ✅ KEEP | *(message adequate — unchanged)* |
| `693c1ef` docs: session wrap — CLAUDE.md gotchas, blog entry, handover 2026-04-15 | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)*
📝 *body: CLAUDE.md: two new testing gotchas (quarkus.http.test-port=0, @TestTransaction + REST),* |

> **Result:** 1 commit.

---

## docs: expand ledger module capabilities in README
*Compaction group 4 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `14d90f8` docs: expand ledger module capabilities in README | ✅ KEEP | *(message adequate — unchanged)* |
| `2c46b77` chore(examples): close #47 — all scenarios complete | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)*
📝 *body: Full quarkus-tarkus-examples module implemented and tested. Closes #47.* |

> **Result:** 1 commit.

---

## docs(tarkus-flow): add README — integration guide, class overview, DSL usage
*Compaction group 5 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `dda87cd` docs(tarkus-flow): add README — integration guide, class overview, DSL usag | ✅ KEEP | *(message adequate — unchanged)* |
| `4bc9296` docs: add blog entry 2026-04-15-mdp02 — examples that prove it | 🔽 SQUASH ↑ | *(stripped by filter-repo Phase 0 — commit only touched blog/ files, became empty and was pruned; shown here for completeness)* | |
| `aa14944` docs: session handover 2026-04-15 | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |

> **Result:** 1 commit.

---

## refactor: rename module directories tarkus→workitems
*Compaction group 6 — 2 commits → 1*
**Final message:** `refactor: rename module directories tarkus→workitems; stale refs updated`

| Commit | Action | Curated result |
|--------|--------|----------------|
| `032dc18` refactor: rename module directories tarkus→workitems | ✅ KEEP | *(see Final message above)* |
| `4efc514` chore: rename GitHub repo mdproctor/quarkus-tarkus → mdproctor/quarkus-work | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |

> **Result:** 1 commit.

---

## fix: code review — labels in evaluator contexts, cascade correctness, sort, FKs, update test
*Compaction group 7 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `3c1a280` fix: code review — labels in evaluator contexts, cascade correctness, sort, | ✅ KEEP | *(message adequate — unchanged)* |
| `f0e4069` test(queues): E2E verification — all modules BUILD SUCCESS | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)*
📝 *body: Full test suite across all modules passing. quarkus-workitems-queues coverage:* |

> **Result:** 1 commit.

---

## feat(vocabulary): support ORG/TEAM/PERSONAL scopes in POST /vocabulary/{scope}
*Compaction group 8 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `8a688a7` feat(vocabulary): support ORG/TEAM/PERSONAL scopes in POST /vocabulary/{sco | ✅ KEEP | *(message adequate — unchanged)* |
| `1a7e026` docs: session handover 2026-04-18 | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |

> **Result:** 1 commit.

---

## feat(core): WorkItem relation graph — PART_OF + pluggable relation types (#82)
*Compaction group 9 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `99fa334` feat(core): WorkItem relation graph — PART_OF + pluggable relation types (# | ✅ KEEP | *(message adequate — unchanged)* |
| `ed2fc78` chore: add epic structure — 5 active epics for retrospective issue grouping | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)*
📝 *body: Epics created via issue-workflow retrospective:* |

> **Result:** 1 commit.

---

## feat(core): WorkItemFormSchema entity + CRUD API — JSON Schema for payload and resolution
*Compaction group 10 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `fc6f346` feat(core): WorkItemFormSchema entity + CRUD API — JSON Schema for payload  | ✅ KEEP | *(message adequate — unchanged)* |
| `187b458` docs: session handover 2026-04-20 | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)*
📝 *body: - WorkItemLink, AsyncAPI spec, Micrometer metrics delivered* |

> **Result:** 1 commit.

---

## feat: scaffold quarkus-work-api module
*Compaction group 11 — 9 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `0d8e8a2` feat: scaffold quarkus-work-api module | ✅ KEEP | *(message adequate — unchanged)* |
| `f60e493` docs: update CLAUDE.md — Jandex, Hibernate reflection, quarkus-junit5 gotch | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |
| `551e4c4` docs: add project blog entry 2026-04-21 — the filter that grew into a contr | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |
| `f4dc2e0` docs: session handover 2026-04-21 | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |
| `3382da2` docs: update handover — quarkus-work foundational module as next priority | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |
| `6633d7e` docs: design spec for quarkus-work / quarkus-workitems separation | 🔽 SQUASH ↑ | *(absorbed — pre-implementation planning doc; message adequate)*
📝 *body: Defines quarkus-work-api (SPI contracts) and quarkus-work-core* |
| `c51e8e3` docs: implementation plan for quarkus-work separation | 🔽 SQUASH ↑ | *(absorbed — pre-implementation planning doc; message adequate)*
📝 *body: 21 tasks covering quarkus-work-api scaffold, quarkus-work-core scaffold,* |
| `2d5d5b7` chore: add .worktrees/ to .gitignore | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |
| `58d7bcd` chore: ensure .worktrees/ ignored (trailing slash pattern) | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |

> **Result:** 1 commit.

---

## docs: update DESIGN.md for quarkus-work separation
*Compaction group 12 — 4 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `14bbc17` docs: update DESIGN.md for quarkus-work separation | ✅ KEEP | *(message adequate — unchanged)* |
| `e11858a` chore: delete quarkus-workitems-api and quarkus-workitems-filter-registry | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)*
📝 *body: Both modules absorbed into quarkus-work-api and quarkus-work-core respectively.* |
| `9d227f1` docs: session handover 2026-04-22 — quarkus-work separation complete | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)*
📝 *body: Branch feature/work-separation: quarkus-work-api + quarkus-work-core extracted.* |
| `3e33644` docs: update CLAUDE.md for quarkus-work-api/work-core separation | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)*
📝 *body: Module table updated: quarkus-workitems-api and quarkus-workitems-filter-registry* |

> **Result:** 1 commit.

---

## feat(work-api): add SkillProfile record
*Compaction group 13 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `4030180` feat(work-api): add SkillProfile record | ✅ KEEP | *(message adequate — unchanged)* |
| `e01fc4b` docs: implementation plan for semantic skill matching | 🔽 SQUASH ↑ | *(absorbed — pre-implementation planning doc; message adequate)*
📝 *body: 16 tasks across 7 phases: SPIs in work-api, WorkerSkillProfile entity + REST,* |

> **Result:** 1 commit.

---

## feat(#122): semantic routing example + integration guide + API reference
*Compaction group 14 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `c8ca3d2` feat(#122): semantic routing example + integration guide + API reference | ✅ KEEP | *(message adequate — unchanged)* |
| `57acf0b` docs: add project blog entry 2026-04-22 — the substrate and what grows on i | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |
| `883b8f7` docs: session handover 2026-04-22 — quarkus-work substrate + semantic skill | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |

> **Result:** 1 commit.

---

## feat: full examples coverage — 9 new scenarios, docs/examples-guide.md
*Compaction group 15 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `99902d1` feat: full examples coverage — 9 new scenarios, docs/examples-guide.md | ✅ KEEP | *(message adequate — unchanged)* |
| `0335e3f` docs: session handover 2026-04-23 — Epic #122 complete, ledger drift repair | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |

> **Result:** 1 commit.

---

## refactor: rename quarkus-workitems → quarkus-work throughout
*Compaction group 16 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `098acfe` refactor: rename quarkus-workitems → quarkus-work throughout | ✅ KEEP | *(message adequate — unchanged)* |
| `53ce6ba` docs: session handover 2026-04-23 — Epic #100 complete, 17 issues closed | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |
| `e8219b7` chore: update repo references to mdproctor/quarkus-work | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |

> **Result:** 1 commit.

---

## docs: bring CLAUDE.md up to date
*Compaction group 17 — 7 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `2a9d7ff` docs: bring CLAUDE.md up to date | ✅ KEEP | *(message adequate — unchanged)* |
| `5d030c8` docs: add project blog entry 2026-04-24 — the primitive and the orchestrato | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |
| `6ab5a77` docs: session handover 2026-04-24 — subprocess spawning complete, layering  | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |
| `fa44d5a` build: bump to 0.2-SNAPSHOT; casehub-parent BOM + GitHub Packages publishin | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)*
📝 *body: - Version bump: 1.0.0-SNAPSHOT → 0.2-SNAPSHOT across all modules* |
| `b9ab478` ci: wire github-casehubio server credentials for GitHub Packages auth | 🔽 SQUASH ↑ | *(absorbed — CI noise; message adequate)*
📝 *body: setup-java only configured the 'github' server id; casehub-parent is* |
| `0974618` ci: simplify GitHub Packages auth — rename repo id to 'github' | 🔽 SQUASH ↑ | *(absorbed — CI noise; message adequate)*
📝 *body: Rename repository id github-casehubio → github in pom.xml so it* |
| `5bc25f7` docs(claude): add ecosystem conventions — Quarkus version, GitHub Packages, | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md update; message adequate)* |

> **Result:** 1 commit.

---

## docs: fix casehub-engine local path in CLAUDE.md (~/claude not ~/dev)
*Compaction group 18 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `a5bbb5f` docs: fix casehub-engine local path in CLAUDE.md (~/claude not ~/dev) | ✅ KEEP | *(message adequate — unchanged)* |
| `d509cdc` docs: add project blog entry 2026-04-27 — SLAs, signals, and a connector li | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |

> **Result:** 1 commit.

---

## docs: SLA compliance reporting design spec (Epic #104)
*Compaction group 19 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `134667a` docs: SLA compliance reporting design spec (Epic #104) | ✅ KEEP | *(message adequate — unchanged)* |
| `68a7b36` docs: session handover 2026-04-27 — business-hours, notifications, casehub- | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |

> **Result:** 1 commit.

---

## feat: scaffold quarkus-work-reports module; remove scaffold from runtime
*Compaction group 20 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `9a64fed` feat: scaffold quarkus-work-reports module; remove scaffold from runtime | ✅ KEEP | *(message adequate — unchanged)* |
| `4b5b51f` chore: begin Epic #104 SLA reporting — issues created | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |

> **Result:** 1 commit.

---

## fix: PostgresDialectValidationTest — working against real PostgreSQL via Testcontainers
*Compaction group 21 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `bcf99dc` fix: PostgresDialectValidationTest — working against real PostgreSQL via Te | ✅ KEEP | *(message adequate — unchanged)* |
| `99c5b36` chore: add build discipline scripts and CLAUDE.md rules | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)*
📝 *body: scripts/mvn-test, mvn-install, mvn-compile, check-build — each with hard* |

> **Result:** 1 commit.

---

## fix: PostgreSQL-compatible SQL types in all Flyway migrations; MODE=PostgreSQL in H2 test URLs
*Compaction group 22 — 4 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `09b4e3a` fix: PostgreSQL-compatible SQL types in all Flyway migrations; MODE=Postgre | ✅ KEEP | *(message adequate — unchanged)* |
| `51c6def` docs: fix stale test counts after PostgreSQL dialect test enabled (68→73) | 🔽 SQUASH ↑ | *(absorbed — stale ref fixup; message adequate)* |
| `266f891` docs: add project blog entry 2026-04-28 — optional reports and PostgreSQL a | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |
| `9772a19` docs: session handover 2026-04-28 — SLA reports, Postgres augmentation, bui | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |

> **Result:** 1 commit.

---

## docs: clarify group policy boundary — M-of-N and cascade cancel owned by quarkus-work
*Compaction group 23 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `bd0b9bb` docs: clarify group policy boundary — M-of-N and cascade cancel owned by qu | ✅ KEEP | *(message adequate — unchanged)* |
| `cc3d5c0` docs: update session handover — migration fixes, MODE=PostgreSQL, casehub-p | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |

> **Result:** 1 commit.

---

## fix: three audit findings in quarkus-work-ledger (casehubio/quarkus-ledger#72)
*Compaction group 24 — 4 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `e44842e` fix: three audit findings in quarkus-work-ledger (casehubio/quarkus-ledger# | ✅ KEEP | *(message adequate — unchanged)* |
| `9c0a0f0` docs: add project blog entry 2026-04-29 — M-of-N parallel WorkItems and a r | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |
| `089992c` docs: session handover 2026-04-29 — Epic #106 multi-instance WorkItems comp | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |
| `b7fbf6d` docs: session handover 2026-04-29 — #93 broadcaster SPI decision | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |

> **Result:** 1 commit.

---

## fix: migrate TrustScoreJobTest to TrustGateService, fix flaky rerun test, align imports with ledger-api module (#72, #70)
*Compaction group 25 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `a9623cc` fix: migrate TrustScoreJobTest to TrustGateService, fix flaky rerun test, a | ✅ KEEP | *(message adequate — unchanged)* |
| `d886c15` docs: add project blog entry 2026-04-29 — ledger audit findings and wrong m | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |
| `a4cc56e` docs: session handover 2026-04-29 — ledger audit fixes, garden entries | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |

> **Result:** 1 commit.

---

## docs: update casehub POC path to casehub-poc
*Compaction group 26 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `3417f53` docs: update casehub POC path to casehub-poc | ✅ KEEP | *(message adequate — unchanged)* |
| `d832f32` ci: retrigger after quarkus-ledger findScore() publish | 🔽 SQUASH ↑ | *(absorbed — CI noise; message adequate)* |

> **Result:** 1 commit.

---

## fix: update casehub-parent and casehub-connectors groupId to io.casehub
*Compaction group 27 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `1249929` fix: update casehub-parent and casehub-connectors groupId to io.casehub | ✅ KEEP | *(message adequate — unchanged)* |
| `59003ca` docs(claude): update CLAUDE.md — fix stale paths post-ecosystem rename | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md update; message adequate)*
📝 *body: - doc URLs: quarkus-ledger.md → casehub-ledger.md, quarkus-qhorus.md → casehub-qhorus.md* |

> **Result:** 1 commit.

---

## docs: purge remaining own-identity Quarkiverse references — casehub is home
*Compaction group 28 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `245bb29` docs: purge remaining own-identity Quarkiverse references — casehub is home | ✅ KEEP | *(message adequate — unchanged)* |
| `4487105` docs: update CLAUDE.md project structure paths to io/casehub/work | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)*
📝 *body: Follows the source directory move in the previous commit.* |
| `710af79` docs: fix stale repo name references post-rename | 🔽 SQUASH ↑ | *(absorbed — stale ref fixup; message adequate)* |

> **Result:** 1 commit.

---

## fix: resolve all tier-4 health check findings
*Compaction group 29 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `609dc75` fix: resolve all tier-4 health check findings | ✅ KEEP | *(message adequate — unchanged)* |
| `b853130` docs: fix stale casehubio/quarkus-work repo refs in planning docs → casehub | 🔽 SQUASH ↑ | *(absorbed — stale ref fixup; message adequate)* |

> **Result:** 1 commit.

---

## fix: correct ledger config prefix in work-examples application.properties
*Compaction group 30 — 5 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `5726850` fix: correct ledger config prefix in work-examples application.properties | ✅ KEEP | *(message adequate — unchanged)* |
| `4688629` docs: fix stale Quarkus WorkItems title → CaseHub Work in CLAUDE.md | 🔽 SQUASH ↑ | *(absorbed — stale ref fixup; message adequate)* |
| `576b88c` docs: session handover 2026-04-30 — CaseHub identity cleanup, health check, | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |
| `798b4fe` chore: retroactive issue linkage for naming consistency sweep | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)*
📝 *body: Refs casehubio/parent#8 — platform-wide naming sweep commits:* |
| `0402912` chore: rename stale 'Quarkus WorkItems' and 'quarkus-work-ledger' to CaseHu | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)*
📝 *body: Replaces all occurrences of 'Quarkus WorkItems' with 'CaseHub Work' and* |

> **Result:** 1 commit.

---

## feat(ci): add repository_dispatch trigger for upstream-published events
*Compaction group 31 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `da3fae2` feat(ci): add repository_dispatch trigger for upstream-published events | ✅ KEEP | *(message adequate — unchanged)* |
| `5ad833d` fix(test): increase TUI pilot pause to reduce timing flakiness in full buil | 🔽 SQUASH ↑ | *(absorbed — test hardening; message adequate)*
📝 *body: press_r_resets_to_step0 uses fixed 500ms pauses that are insufficient* |
| `2cb8ad2` ci: add workflow_dispatch trigger to publish workflow | 🔽 SQUASH ↑ | *(absorbed — CI noise; message adequate)* |

> **Result:** 1 commit.

---

## fix: eliminate intermittent failures in NotificationDeliveryTest
*Compaction group 32 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `95bf119` fix: eliminate intermittent failures in NotificationDeliveryTest | ✅ KEEP | *(message adequate — unchanged)* |
| `2519f1b` ci: standardise publish workflow — consistent build/test/publish/dispatch c | 🔽 SQUASH ↑ | *(absorbed — CI noise; message adequate)* |

> **Result:** 1 commit.

---

## docs: add two gotchas to CLAUDE.md — persistAndFlush flush-all, BroadcastProcessor no-subscriber
*Compaction group 33 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `d5465fe` docs: add two gotchas to CLAUDE.md — persistAndFlush flush-all, BroadcastPr | ✅ KEEP | *(message adequate — unchanged)* |
| `413f5d3` chore: add jandex-maven-plugin for CDI bean discovery | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)*
📝 *body: Library JARs require META-INF/jandex.idx for Quarkus to discover CDI* |

> **Result:** 1 commit.

---

## docs: document Flyway migration version numbering convention
*Compaction group 34 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `cb36258` docs: document Flyway migration version numbering convention | ✅ KEEP | *(message adequate — unchanged)* |
| `ec1f15a` docs: add blog entry 2026-05-01 — three intermittent test failures and Hibe | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |
| `feb33ca` docs: session handover 2026-05-01 — intermittent test failures fixed | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |

> **Result:** 1 commit.

---

## feat: add PostgreSQL LISTEN/NOTIFY broadcaster for distributed SSE (#93)
*Compaction group 35 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `edc56ab` feat: add PostgreSQL LISTEN/NOTIFY broadcaster for distributed SSE (#93) | ✅ KEEP | *(message adequate — unchanged)* |
| `e91096f` chore: pin jandex-maven-plugin 3.1.2 in root pluginManagement | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |
| `2ed5b0a` fix(test): capture Instant.now() before REST call in BusinessHoursIntegrati | 🔽 SQUASH ↑ | *(absorbed — test hardening; message adequate)*
📝 *body: Business hours calculation rounds up to next minute boundary. Calling* |

> **Result:** 1 commit.

---

## feat: add PostgreSQL LISTEN/NOTIFY broadcaster for distributed queue SSE (#155)
*Compaction group 36 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `7365134` feat: add PostgreSQL LISTEN/NOTIFY broadcaster for distributed queue SSE (# | ✅ KEEP | *(message adequate — unchanged)* |
| `a7ec29c` ci: use GH_PAT for cross-repo repository_dispatch | 🔽 SQUASH ↑ | *(absorbed — CI noise; message adequate)* |

> **Result:** 1 commit.

---

## feat: sync WorkItem fields to GitHub Issue labels and state on lifecycle transitions (#157)
*Compaction group 37 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `454da81` feat: sync WorkItem fields to GitHub Issue labels and state on lifecycle tr | ✅ KEEP | *(message adequate — unchanged)* |
| `e314dfc` fix(test): eliminate flakiness in WorkItemGroupLifecycleEventTest | 🔽 SQUASH ↑ | *(absorbed — test hardening; message adequate)* |

> **Result:** 1 commit.

---

## refactor(test): extract BusinessHoursAssert helper for robust deadline assertions (#158)
*Compaction group 38 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `c9ea3e0` refactor(test): extract BusinessHoursAssert helper for robust deadline asse | ✅ KEEP | *(message adequate — unchanged)* |
| `ad3bbcf` docs: session handover 2026-05-01 — distributed SSE and GitHub sync | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |
| `0f62b90` fix(test): widen BusinessHoursIntegrationTest claim deadline bound to 4 day | 🔽 SQUASH ↑ | *(absorbed — test hardening; message adequate)*
📝 *body: 2 business hours from Friday after-hours resolves to Monday morning (~3.6* |

> **Result:** 1 commit.

---

## fix: implement new LedgerEntryRepository methods from casehub-ledger 0.2-SNAPSHOT
*Compaction group 39 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `6b309d3` fix: implement new LedgerEntryRepository methods from casehub-ledger 0.2-SN | ✅ KEEP | *(message adequate — unchanged)* |
| `1b59d9a` fix(test): eliminate OCC race in completedEventFiresExactlyOnceAtThreshold | 🔽 SQUASH ↑ | *(absorbed — test hardening; message adequate)*
📝 *body: Root cause: onThresholdReached defaults to CANCEL in MultiInstanceSpawnService.* |

> **Result:** 1 commit.

---

## refactor: change onThresholdReached default from CANCEL to KEEP (null)
*Compaction group 40 — 3 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `213810a` refactor: change onThresholdReached default from CANCEL to KEEP (null) | ✅ KEEP | *(message adequate — unchanged)* |
| `9e76847` docs: session handover 2026-05-02 — CI fixes and normative layer doc | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |
| `a51b6f7` docs: update CLAUDE.md — fork/PR workflow, SNAPSHOT drift gotcha, onThresho | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |

> **Result:** 1 commit.

---

## docs: Phase 2 inbound webhooks design spec (#156)
*Compaction group 41 — 2 commits → 1*

| Commit | Action | Curated result |
|--------|--------|----------------|
| `68dd48f` docs: Phase 2 inbound webhooks design spec (#156) | ✅ KEEP | *(message adequate — unchanged)* |
| `ec3c64e` docs: session handover 2026-05-02 — CI fixes and onThresholdReached default | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |

> **Result:** 1 commit.

---

## docs: update ARCHITECTURE.md, CLAUDE.md, DESIGN.md for IssueLinkStore (#161)
*Compaction group 42 — 13 commits → 1*

⚠️ **Net no-op pair:** This group absorbs both `chore: migrate CLAUDE.md and methodology artifacts to workspace` and `chore: restore CLAUDE.md to project repo`. These two commits cancel each other out — CLAUDE.md was moved to the workspace then immediately restored. Combined effect on CLAUDE.md tree is zero; only other files in those commits contribute any lasting change.

| Commit | Action | Curated result |
|--------|--------|----------------|
| `23b743b` docs: update ARCHITECTURE.md, CLAUDE.md, DESIGN.md for IssueLinkStore (#161 | ✅ KEEP | *(message adequate — unchanged)* |
| `ebb0cbd` docs: update CLAUDE.md — AFTER_SUCCESS scope expanded, Instance<T> Mockito  | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |
| `f2b39aa` docs: add blog entry 2026-05-05 — speech acts, priority alignment, IssueLin | 🔽 SQUASH ↑ | *(absorbed — docs follow-on; message adequate)* |
| `da3825a` docs: session handover 2026-05-05 — GitHub/Jira webhooks Phase 2, IssueLink | 🔽 SQUASH ↑ | *(absorbed — session handover survived filter-repo; mixed content)* |
| `f7d34c3` chore: move ADRs to docs/adr/ — MADR/Java convention | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |
| `0ac4935` chore: consolidate specs to docs/specs/ — canonical location | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |
| `9b69148` chore: flatten docs/architecture/ — single file moved to docs/  no-issue: s | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |
| `84e4c0e` chore: migrate CLAUDE.md and methodology artifacts to workspace | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |
| `5cb7c6a` chore: restore CLAUDE.md to project repo (workspace symlinks to this) | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |
| `36772a9` chore: ignore wksp symlink | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |
| `4348415` chore: use local paths for PLATFORM.md and deep-dive docs instead of GitHub | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |
| `b2c87ec` chore: platform docs — use local Read, fall back to WebFetch if not cloned | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |
| `94ac23a` chore: add Project Artifacts section to CLAUDE.md | 🔽 SQUASH ↑ | *(absorbed — chore cleanup; message adequate)* |

> **Result:** 1 commit.

---

## AFTER — what `git log --oneline` will show

```
  379  commits on main (original)
   -37  pruned by filter-repo (HANDOFF.md + blog/ became empty)
   -46  absorbed by squash
  ─────────────────────────────────────────────
   296  commits — no content lost
```

Sample (most recent 10 of 295 — post-squash simulation):
```
  23b743b  docs: update ARCHITECTURE.md, CLAUDE.md, DESIGN.md for IssueLinkStore (#161)
  2c93742  refactor(issue-tracker): IssueLinkService injects IssueLinkStore (#161)
  b262b04  refactor(issue-tracker): WebhookEventHandler injects IssueLinkStore + WorkItemStore (
  f8c73f1  feat(testing): InMemoryIssueLinkStore — in-memory IssueLinkStore for tests (#161)
  bb2d5b3  feat(issue-tracker): IssueLinkStore SPI + JpaIssueLinkStore (#161)
  bbbb28e  docs: IssueLinkStore SPI implementation plan (#161)
  6cbbdf5  docs: IssueLinkStore SPI design spec (#161)
  556313c  feat(issue-tracker): JiraWebhookResource — POST /workitems/jira-webhook (#156)
  c9c2016  feat(issue-tracker): Jira webhook parser + config + fixtures (#156)
  dc6fd48  feat(issue-tracker): GitHubWebhookResource — POST /workitems/github-webhook (#156)
```

---

## Interval tree verification

5 sample points — all at diff=0. Content verified intact.

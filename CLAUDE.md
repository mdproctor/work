# work Workspace

**Name:** CaseHub Work
**Project repo:** /Users/mdproctor/claude/casehub/work
**Workspace type:** public


## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/work` before any other work.

## Artifacts

| Artifact | Skill | Path | Destination | Notes |
|---|---|---|---|---|
| specs | brainstorming | `specs/` | project | promoted to `docs/specs/` at epic close |
| plans | writing-plans | `plans/` | workspace | permanent |
| handover | handover | `HANDOFF.md` | workspace | single file, overwritten each session |
| idea-log | idea-log | `IDEAS.md` | workspace | single file |
| design-snapshot | design-snapshot | `snapshots/` | workspace | INDEX.md; auto-pruned, max 10 |
| epic journal | java-update-design / update-primary-doc | `design/JOURNAL.md` | workspace | created by `epic` |
| adr | adr | `adr/` | project | promoted to `docs/adr/` at epic close |
| blog | write-blog | `~/.claude/blog-routing.yaml` | — | no workspace staging — routed automatically |

## Git Discipline

Two git repositories are active in every session:
- **Workspace** (`/Users/mdproctor/claude/public/casehub/work`) — methodology artifacts: handover, specs, plans, ADRs (blog is not staged here — goes directly to destination in blog-routing.yaml)
- **Project repo** (`/Users/mdproctor/claude/casehub/work`) — source code

Before any git operation, run `git rev-parse --show-toplevel` to confirm which repo is currently active. Do not assume — the session may have opened in either. cd to the correct repo before staging:
- Source code commits → project repo
- Methodology artifacts → workspace


## Rules

- All methodology artifacts go here, not in the project repo
- Promotion to project repo is always explicit — never automatic
- Workspace branches mirror project branches — switch both together

---

# CaseHub Work — Claude Code Project Guide

## Platform Context

This repo is one component of the casehubio multi-repo platform. **Before implementing anything — any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol.**

> **Platform docs:** Local paths use `../parent/docs/` as root. If a path doesn't exist, the parent repo isn't cloned locally — fetch from `https://raw.githubusercontent.com/casehubio/parent/main/docs/<path>` instead.

The protocol asks: Does this already exist elsewhere? Is this the right repo for it? Does this create a consolidation opportunity? Is this consistent with how the platform handles the same concern in other repos?

**Platform architecture (fetch before any implementation decision):**
```
../parent/docs/PLATFORM.md
```

**This repo's deep-dive:**
```
../parent/docs/repos/casehub-work.md
```

**Other repo deep-dives** (fetch the relevant ones when your implementation touches their domain):
- casehub-ledger: `../parent/docs/repos/casehub-ledger.md`
- casehub-qhorus: `../parent/docs/repos/casehub-qhorus.md`
- casehub-engine: `../parent/docs/repos/casehub-engine.md`
- claudony: `../parent/docs/repos/claudony.md`
- casehub-connectors: `../parent/docs/repos/casehub-connectors.md`

---

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2, GraalVM 25 (native image target)

---

## What This Project Is

CaseHub Work is a **CaseHub platform module** providing **human-scale WorkItem lifecycle management**. It gives any Quarkus application a human task inbox with expiry, delegation, escalation, priority, and audit trail — usable independently or with optional integrations for Quarkus-Flow, CaseHub, and Qhorus. It is hosted under the CaseHub organisation (`casehubio/work`), not submitted to Quarkiverse.

**The core concept — WorkItem (not Task):**
A `WorkItem` is a unit of work requiring human attention or judgment. It is deliberately NOT called `Task` because:
- The CNCF Serverless Workflow SDK (used by Quarkus-Flow) has its own `Task` class (`io.serverlessworkflow.api.types.Task`) — a machine-executed workflow step
- CaseHub has its own `Task` class — a CMMN-style case work unit
Using `WorkItem` avoids naming conflicts and accurately describes what WorkItems manages: work that waits for a person.

**See the full glossary:** `docs/ARCHITECTURE.md` § Glossary

---

## Naming

| Element | Value |
|---|---|
| GitHub repo | `casehubio/work` |
| groupId | `io.casehub` |
| Parent artifactId | `casehub-work-parent` |
| Runtime artifactId | `casehub-work` |
| Deployment artifactId | `casehub-work-deployment` |
| Root Java package | `io.casehub.work` |
| Runtime subpackage | `io.casehub.work.runtime` |
| Deployment subpackage | `io.casehub.work.deployment` |
| Config prefix | `casehub.work` |
| Feature name | `workitems` |
| Version | `0.2-SNAPSHOT` (published to GitHub Packages under casehubio org) |

---

## Ecosystem Context

WorkItems is part of the Quarkus Native AI Agent Ecosystem:

```
CaseHub (case orchestration)   Quarkus-Flow (workflow execution)   Qhorus (agent mesh)
         │                              │                               │
         └──────────────────────────────┼───────────────────────────────┘
                                        │
                              CaseHub Work (WorkItem inbox)
                                        │
                              casehub-work-casehub   (optional adapter)
                              casehub-work-flow      (optional adapter)
                              casehub-work-qhorus    (optional adapter)
```

WorkItems has **no dependency on CaseHub, Quarkus-Flow, or Qhorus** — it is the independent human task layer. The integration modules (future) depend on WorkItems, not vice versa.

**Related projects (read only, for context):**
- `~/claude/casehub/qhorus` — agent communication mesh (Qhorus integration target)
- `~/claude/casehub/engine` — real CaseHub engine (CMMN + blackboard; **not** `~/claude/casehub-poc` which is the retiring POC)
- `~/dev/quarkus-flow` — workflow engine (Quarkus-Flow integration target; uses CNCF Serverless Workflow SDK)
- `~/claude/casehub/claudony` — integration layer; will surface WorkItems inbox in its dashboard

---

## Project Structure

Use `ide_find_class` / `ide_find_symbol` to locate specific classes. The table below shows module ownership and structural constraints that the IDE can't tell you.

| Module | Purpose | Key constraints |
|---|---|---|
| `casehub-work-api/` | Pure-Java SPI — no JPA, no REST | All SPIs, events, value objects. casehub-engine depends on this directly. |
| `casehub-work-core/` | Jandex library — no JPA, no REST | WorkBroker, built-in strategies, claim SLA policies; pure CDI. No filter classes — filter engine moved to `runtime/filter/` in #133. |
| `runtime/` | Extension runtime | WorkItem entity, JPA stores, filter engine, multi-instance coordinator, REST endpoints at `/workitems` |
| `deployment/` | Extension build-time | `WorkItemsProcessor` @BuildStep only |
| `testing/` | Test utilities (`casehub-work-testing`) | In-memory stores; no datasource required. `InMemoryIssueLinkStore` requires `casehub-work-issue-tracker` on classpath. |
| `docs/` | Architecture, design, specs | `ARCHITECTURE.md` (SPI contracts), `DESIGN.md` (roadmap + Flyway history), `GOTCHAS.md`, `FLYWAY.md` |
| `scripts/` | Build helpers | See `scripts/README.md` for usage and expected test times |

**Integration modules (built):**
- `work-flow/` — Quarkus-Flow CDI bridge (`HumanTaskFlowBridge`, `PendingWorkItemRegistry`, `WorkItemFlowEventListener`)
- `casehub-work-ledger/` — optional accountability module (command/event ledger, hash chain, attestation, EigenTrust)
- `casehub-work-queues/` — optional label-based queue module; label filter chains, queue views, JEXL/JQ expression evaluation
- `casehub-work-ai/` — AI-native features; confidence gating via `LowConfidenceFilterProducer`; `SemanticWorkerSelectionStrategy` (@Alternative @Priority(1)) for embedding-based worker scoring; depends on `casehub-work-core`
- `casehub-work-notifications/` — optional outbound notification module; CDI observer dispatches to HTTP webhook, Slack, and Teams channels after lifecycle events. Flyway V3000.
- `casehub-work-reports/` — optional SLA compliance reporting; `/reports/sla-breaches`, `/actors/{id}`, `/throughput`, `/queue-health`; zero cost when absent; 73 tests
- `casehub-work-postgres-broadcaster/` — optional distributed SSE; PostgreSQL LISTEN/NOTIFY for WorkItem events (`casehub_work_events`); no Flyway migrations; 22 tests
- `casehub-work-queues-postgres-broadcaster/` — optional distributed SSE for queue events (`casehub_work_queue_events`); no Flyway migrations; 13 tests; depends on `casehub-work-queues` + `quarkus-reactive-pg-client`
- `casehub-work-issue-tracker/` — optional issue-tracker link module; `IssueTrackerProvider` SPI; GitHub and Jira webhook handlers; Flyway V5000; 93 tests
- `casehub-work-examples/` — runnable scenario demos; each runs via `POST /examples/{name}/run`
- `integration-tests/` — `@QuarkusIntegrationTest` suite and native image validation (25 tests)

**Future integration modules (not yet scaffolded):**
- CaseHub adapter — lives in casehub-engine repo, not here (see `docs/architecture/LAYERING.md`)
- `casehub-work-qhorus/` — Qhorus MCP tools (`request_approval`, `check_approval`, `wait_for_approval`) (blocked: Qhorus not yet complete)
- `casehub-work-persistence-mongodb/` — MongoDB-backed `WorkItemStore`
- `casehub-work-persistence-redis/` — Redis-backed `WorkItemStore`

---

## Build and Test

```bash
# Build all modules
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install

# Test a specific module (prefer scripts/ for timeouts)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -Dtest=ClassName -pl runtime

# Black-box integration tests (JVM mode)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn verify -pl integration-tests

# Native image integration tests (requires GraalVM 25)
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home mvn verify -Pnative -pl integration-tests
```

**Use `mvn` not `./mvnw`** — maven wrapper not configured on this machine.

---

## Build Discipline

**Never run `mvn install` or `mvn test` without `-pl <module>`.** The full project has 20+ modules; a full build times out in any AI tool context window. Always target the specific module you changed.

**Use `scripts/` helper scripts** — they enforce hard timeouts. See `scripts/README.md` for script reference and expected test times per module.

**Before writing any Java code:** read `docs/GOTCHAS.md` — categorised gotchas for extension build, Hibernate/JPA, CDI/transactions, testing, domain behaviour, and cross-repo issues.

**When adding Flyway migrations:** read `docs/FLYWAY.md` — version range ownership, V-number reservation procedure for concurrent epic branches, and casehub-ledger prerequisites.

---

## Java and GraalVM on This Machine

```bash
# Java 26 (Oracle, system default) — use for dev and tests
JAVA_HOME=$(/usr/libexec/java_home -v 26)

# GraalVM 25 — use for native image builds only
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home
```

---

## Design Document

`docs/specs/2026-04-14-tarkus-design.md` is the primary design specification.
`docs/ARCHITECTURE.md` is the architectural reference — module graph, domain model, SPI contracts.
`docs/DESIGN.md` is the implementation tracker — build roadmap, Flyway migration history, test totals.

---

## Project Artifacts

Paths that are project content (not workspace noise). Skills use this to avoid
filtering or dropping commits that touch these paths.

| Path | What it is |
|------|------------|
| `CLAUDE.md` | Project conventions (build, test, naming) |
| `docs/adr/` | Architecture decision records |
| `docs/DESIGN.md` | Design document |

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/work

**Active epics:**

| # | Epic | Status | Notes |
|---|---|---|---|
| #92 | Distributed WorkItems — clustering + federation | in progress | #93 ✅ WorkItem SSE done; #155 ✅ queue SSE done; broader federation deferred |
| #79 | External System Integrations | blocked | CaseHub/Qhorus not stable |
| #39 | ProvenanceLink (PROV-O causal graph) | blocked | Awaiting #79 |

**Completed epics:** #77, #78, #80, #81 (Collaboration, Queue Intelligence, Storage, Platform), #93 (Distributed SSE), #98 (Form Schema), #99 (Audit Query API), #100 (AI-Native Features), #101 (Business-Hours Deadlines), #102 (Workload-Aware Routing), #103 (Notifications), #104 (SLA Compliance Reporting), #105 (Subprocess Spawning), #106 (Multi-Instance Tasks), #147 (Project Refinement), #170 (Schema-Validated Output)

**Automatic behaviours (Claude follows these at all times in this project):**
- **Before implementation begins** — check if an active issue exists. If not, run issue-workflow Phase 1 before writing any code. Create a child issue under the matching epic above.
- **Before any commit** — run issue-workflow Phase 3 to confirm issue linkage.
- **All commits should reference an issue** — `Refs #N` (ongoing) or `Closes #N` (done). Also reference the parent epic: `Refs #77` etc.
- **Code review fix commits** — when committing fixes found during a code review, create or reuse an issue for that review work **before** committing. Use `Refs #N` on the relevant epic even if it is already closed.
- **New feature requests** — assess which epic it belongs to before creating the issue. If none fits, propose a new epic first.

## Ecosystem Conventions

All casehubio projects align on these conventions:

**Quarkus version:** All projects use `3.32.2`. When bumping, bump all projects together.

**GitHub Packages — dependency resolution:** Add to `pom.xml` `<repositories>`:
```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```
CI must use `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

**Cross-project SNAPSHOT versions:** `casehub-ledger` and `casehub-work` modules are `0.2-SNAPSHOT` resolved from GitHub Packages. Declare in `pom.xml` properties and `<dependencyManagement>` — no hardcoded versions in submodule poms.

**SNAPSHOT API drift:** CI pulls the latest `casehub-ledger:0.2-SNAPSHOT` from GitHub Packages; local builds use the cached jar. When `casehub-ledger` adds new abstract methods to `LedgerEntryRepository`, CI breaks but local passes silently. Before concluding a build is stable, refresh the local cache: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn install -DskipTests -f ~/claude/casehub/ledger/pom.xml` and re-run the affected module tests.

**Git workflow — fork model:**
```
origin   → personal fork   (git remote get-url origin)
upstream → casehubio       (git remote get-url upstream)
```
Before starting any branch: `git fetch upstream && git rebase upstream/main` to sync local main with casehubio. At work-end: rebase the branch onto local main, push to `origin main`. PRs to `upstream` are created separately, on demand — never automatically at work-end.


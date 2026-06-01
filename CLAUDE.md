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
| epic journal | java-update-design | `design/JOURNAL.md` | workspace | written on epic branches |
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

CaseHub Work is a **CaseHub platform module** providing **human-scale WorkItem lifecycle management**. It gives any Quarkus application a human task inbox with expiry, delegation, escalation, priority, and audit trail — usable independently or with an optional Quarkus-Flow integration. CaseHub and Qhorus adapters are planned but not yet built. It is hosted under the CaseHub organisation (`casehubio/work`), not submitted to Quarkiverse.

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

WorkItems is the independent human task layer below CaseHub, Quarkus-Flow, and Qhorus — no dependency on any of them. Integration modules depend on WorkItems, not vice versa.

**Related projects (read only, for context):**
- `~/claude/casehub/qhorus` — agent communication mesh (Qhorus integration target)
- `~/claude/casehub/engine` — real CaseHub engine (CMMN + blackboard; **not** `~/claude/casehub-poc` which is the retiring POC)
- `~/dev/quarkus-flow` — workflow engine (Quarkus-Flow integration target; uses CNCF Serverless Workflow SDK)
- `~/claude/casehub/claudony` — integration layer; will surface WorkItems inbox in its dashboard

---

## Project Structure

Module ownership and structural constraints: `docs/MODULES.md` — read before any multi-module work.

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
| `docs/MODULES.md` | Module ownership and structural constraints |
| `docs/ECOSYSTEM.md` | Cross-project conventions (packaging, versioning, fork workflow) |

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/work

**Active epics:**

| # | Epic | Status | Notes |
|---|---|---|---|
| #92 | Distributed WorkItems — clustering + federation | in progress | #93 ✅ WorkItem SSE done; #155 ✅ queue SSE done; broader federation deferred |
| #79 | External System Integrations | blocked | CaseHub/Qhorus not stable |
| #39 | ProvenanceLink (PROV-O causal graph) | blocked | Awaiting #79 |

**Automatic behaviours (Claude follows these at all times in this project):**
- **Before implementation begins** — check if an active issue exists. If not, run issue-workflow Phase 1 before writing any code. Create a child issue under the matching epic above.
- **Before any commit** — run issue-workflow Phase 3 to confirm issue linkage.
- **All commits should reference an issue** — `Refs #N` (ongoing) or `Closes #N` (done). Also reference the parent epic: `Refs #77` etc.
- **Code review fix commits** — when committing fixes found during a code review, create or reuse an issue for that review work **before** committing. Use `Refs #N` on the relevant epic even if it is already closed.
- **New feature requests** — assess which epic it belongs to before creating the issue. If none fits, propose a new epic first.

## Ecosystem Conventions

Cross-project conventions (GitHub Packages setup, SNAPSHOT versioning, fork workflow): `docs/ECOSYSTEM.md`.


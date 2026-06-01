# Module Map

Use `ide_find_class` / `ide_find_symbol` to locate specific classes. This file documents module ownership and structural constraints that the IDE can't tell you.

## Core Modules

| Module | Purpose | Key constraints |
|---|---|---|
| `casehub-work-api/` | Pure-Java SPI — no JPA, no REST | All SPIs, events, value objects. casehub-engine depends on this directly. |
| `casehub-work-core/` | Jandex library — no JPA, no REST | WorkBroker, built-in strategies, claim SLA policies; pure CDI. No filter classes — filter engine moved to `runtime/filter/` in #133. |
| `runtime/` | Extension runtime | WorkItem entity, JPA stores, filter engine, multi-instance coordinator, REST endpoints at `/workitems` |
| `deployment/` | Extension build-time | `WorkItemsProcessor` @BuildStep only |
| `testing/` | Test utilities (`casehub-work-testing`) | In-memory stores; no datasource required. `InMemoryIssueLinkStore` requires `casehub-work-issue-tracker` on classpath. |
| `docs/` | Architecture, design, specs | `ARCHITECTURE.md` (SPI contracts), `DESIGN.md` (roadmap + Flyway history), `GOTCHAS.md`, `FLYWAY.md` |
| `scripts/` | Build helpers | See `scripts/README.md` for usage and expected test times |

## Integration Modules (built)

| Module | Purpose |
|---|---|
| `work-flow/` | Quarkus-Flow CDI bridge (`HumanTaskFlowBridge`, `PendingWorkItemRegistry`, `WorkItemFlowEventListener`) |
| `casehub-work-ledger/` | Optional accountability module (command/event ledger, hash chain, attestation, EigenTrust) |
| `casehub-work-queues/` | Optional label-based queue module; label filter chains, queue views, JEXL/JQ expression evaluation |
| `casehub-work-ai/` | AI-native features; confidence gating via `LowConfidenceFilterProducer`; `SemanticWorkerSelectionStrategy` (@Alternative @Priority(1)) for embedding-based worker scoring |
| `casehub-work-notifications/` | Optional outbound notification module; CDI observer dispatches to HTTP webhook, Slack, and Teams channels. Flyway V3000. |
| `casehub-work-reports/` | Optional SLA compliance reporting; `/reports/sla-breaches`, `/actors/{id}`, `/throughput`, `/queue-health` |
| `casehub-work-postgres-broadcaster/` | Optional distributed SSE; PostgreSQL LISTEN/NOTIFY for WorkItem events (`casehub_work_events`) |
| `casehub-work-queues-postgres-broadcaster/` | Optional distributed SSE for queue events (`casehub_work_queue_events`); depends on `casehub-work-queues` + `quarkus-reactive-pg-client` |
| `casehub-work-issue-tracker/` | Optional issue-tracker link module; `IssueTrackerProvider` SPI; GitHub and Jira webhook handlers. Flyway V5000. |
| `casehub-work-examples/` | Runnable scenario demos; each runs via `POST /examples/{name}/run` |
| `integration-tests/` | `@QuarkusIntegrationTest` suite and native image validation |

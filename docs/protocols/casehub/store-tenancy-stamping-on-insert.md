---
id: PP-20260609-bdac7e
title: "Store put() stamps tenancyId on insert, preserves on update"
type: rule
scope: repo
applies_to: "All tenant-scoped store put() implementations and callers in async/background paths"
severity: critical
violation_hint: "Entity saved with null tenancyId, or tenancyId silently changed on update, or store called from async path without TenantContextRunner"
created: 2026-06-09
updated: 2026-06-10
---

On insert (`entity.getTenancyId() == null`), stamp from `CurrentPrincipal.tenancyId()`. On update, preserve the existing value — never overwrite. TenancyId is immutable after entity creation. This prevents both orphaned entities (no tenant) and silent tenant migration (entity moves between tenants on update).

**Async and background paths:** `@ObservesAsync` handlers, `@Scheduled` jobs, and any code running outside a CDI request scope have no `CurrentPrincipal`. Before calling store `put()` in these paths, establish tenant context via `TenantContextRunner.runInTenantContext(tenancyId, () -> ...)` — the runner activates a request scope with a `CurrentPrincipal` that returns the supplied tenancyId. Extract tenancyId from the event source (e.g. `WorkItem.getTenancyId()`) or job data, never hardcode it. See [async-event-tenant-context-propagation](async-event-tenant-context-propagation.md).

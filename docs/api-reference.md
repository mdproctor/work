# CaseHub Work — REST API Reference

All responses are `application/json`. All request bodies require `Content-Type: application/json` unless otherwise noted.

**Modules:** Endpoints are spread across optional modules. An endpoint is available only if its module is on the classpath.

| Module | Base paths | Description |
|---|---|---|
| `casehub-work` (runtime) | `/workitems`, `/workitem-templates`, `/workitem-schedules`, `/spawn-groups`, `/audit`, `/vocabulary`, `/filter-rules`, `/q/asyncapi` | Core WorkItem lifecycle, templates, schedules, spawning, audit, vocabulary, filter rules |
| `casehub-work-queues` | `/queues`, `/filters`, `/workitems/{id}/pickup`, `/workitems/{id}/relinquishable` | Queue views, filters, soft pickup |
| `casehub-work-ledger` | `/workitems/{id}/ledger`, `/workitems/actors/{actorId}/trust` | Immutable audit ledger, trust scores |
| `casehub-work-ai` | `/workitems/{id}/resolution-suggestion`, `/worker-skill-profiles`, `/workitems/{id}/escalation-summaries` | AI suggestions, skill profiles, escalation summaries |
| `casehub-work-notifications` | `/workitem-notification-rules` | Webhook and Slack notification rules |
| `casehub-work-reports` | `/workitems/reports/*` | SLA, actor, throughput, and queue health reports |
| `casehub-work-issue-tracker` | `/workitems/{id}/issues`, `/workitems/github-webhook`, `/workitems/jira-webhook` | External issue tracker integration |

---

## Shared Types

### WorkItemResponse

Returned by most lifecycle endpoints.

| Field | Type | Description |
|---|---|---|
| `id` | UUID | |
| `title` | string | |
| `description` | string | |
| `types` | string[] | Hierarchical type paths |
| `formKey` | string | UI form reference |
| `status` | WorkItemStatus | See [WorkItemStatus](#workitemstatus) |
| `priority` | WorkItemPriority | `LOW`, `MEDIUM`, `HIGH`, `URGENT` |
| `assigneeId` | string | |
| `owner` | string | |
| `candidateGroups` | string | Comma-separated |
| `candidateUsers` | string | Comma-separated |
| `requiredCapabilities` | string | Comma-separated |
| `createdBy` | string | |
| `delegationDeclineTarget` | DeclineTarget | `POOL` or `DELEGATOR` (null when not delegated) |
| `delegationChain` | string | |
| `priorStatus` | WorkItemStatus | Status before current transition |
| `payload` | string | JSON context |
| `resolution` | string | |
| `claimDeadline` | instant | |
| `expiresAt` | instant | |
| `followUpDate` | instant | |
| `createdAt` | instant | |
| `updatedAt` | instant | |
| `assignedAt` | instant | |
| `startedAt` | instant | |
| `completedAt` | instant | |
| `suspendedAt` | instant | |
| `labels` | WorkItemLabelResponse[] | Each: `path` (string), `persistence` (`MANUAL`/`INFERRED`), `appliedBy` (string) |
| `confidenceScore` | double (nullable) | AI confidence score (0.0–1.0) |
| `callerRef` | string (nullable) | Opaque caller-supplied routing key |
| `version` | long | JPA optimistic lock version |
| `templateId` | UUID (nullable) | Template this item was instantiated from |
| `templateVersion` | long (nullable) | Version of the template at instantiation |
| `outcome` | string (nullable) | Named outcome at completion |
| `permittedOutcomes` | Outcome[] (nullable) | Each: `name`, `displayName`, `condition` |
| `inputDataSchema` | string (nullable) | JSON Schema for payload validation |
| `outputDataSchema` | string (nullable) | JSON Schema for resolution validation |
| `excludedUsers` | string (nullable) | Comma-separated excluded user IDs |
| `scope` | string (nullable) | Hierarchical scope path |
| `percentComplete` | integer (nullable) | Progress percentage (0–100), updated via `PUT /workitems/{id}/progress` |
| `statusNote` | string (nullable) | Free-text status note from actor |
| `candidateScores` | string (nullable) | JSON map of actor ID → routing score (e.g. `{"alice":0.85,"bob":0.72}`) |
| `routingExperiences` | string (nullable) | JSON array of past similar cases from CBR routing |

### WorkItemStatus

| Status | Terminal | Description |
|---|---|---|
| `PENDING` | no | Awaiting claim |
| `ASSIGNED` | no | Claimed, not yet started |
| `IN_PROGRESS` | no | Work underway |
| `DELEGATED` | no | Delegated, awaiting accept/decline |
| `SUSPENDED` | no | Paused |
| `COMPLETED` | yes | Finished successfully |
| `REJECTED` | yes | Finished with rejection |
| `FAULTED` | yes | System or infrastructure failure (distinct from REJECTED — not a deliberate decision) |
| `CANCELLED` | yes | Cancelled |
| `OBSOLETE` | yes | Superseded by context change (distinct from CANCELLED — not a deliberate stop) |
| `ESCALATED` | yes | All SLA breach policy branches exhausted |
| `EXPIRED` | yes | Exceeded expiry deadline |

---

## WorkItem Lifecycle

### POST /workitems

Creates a new WorkItem in `PENDING` status. If no `expiresAt` is supplied, expiry is set to `now + casehub.work.default-expiry-hours`. If no `claimDeadline` is supplied and `casehub.work.default-claim-hours > 0`, the claim deadline is set accordingly. Business-hours variants (`claimDeadlineBusinessHours`, `expiresAtBusinessHours`) use the BusinessHoursCalculator SPI.

**Request body:** `CreateWorkItemRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `title` | string | yes | |
| `description` | string | no | |
| `types` | string[] | no | Hierarchical type paths (e.g. `["legal"]`, `["finance/audit"]`) |
| `formKey` | string | no | UI form reference |
| `priority` | WorkItemPriority | no | `LOW` / `MEDIUM` / `HIGH` / `URGENT`, defaults to `MEDIUM` |
| `assigneeId` | string | no | Direct assignee; null for candidate-based routing |
| `candidateGroups` | string | no | Comma-separated group names eligible to claim |
| `candidateUsers` | string | no | Comma-separated user IDs invited to claim |
| `requiredCapabilities` | string | no | Comma-separated capability tags for routing |
| `createdBy` | string | no | System or agent that created the WorkItem |
| `payload` | string | no | JSON context (stored as TEXT, not parsed) |
| `claimDeadline` | ISO-8601 instant | no | Must be claimed by; overrides config default |
| `expiresAt` | ISO-8601 instant | no | Must be completed by; overrides config default |
| `followUpDate` | ISO-8601 instant | no | Reminder date; surfaces in inbox when `followUp=true` |
| `labels` | WorkItemLabelRequest[] | no | Labels to apply at creation (each: `path`, `persistence`, `appliedBy`) |
| `confidenceScore` | double | no | AI confidence score (0.0–1.0) |
| `callerRef` | string | no | Opaque caller-supplied routing key |
| `claimDeadlineBusinessHours` | integer | no | Claim deadline in business hours (uses BusinessHoursCalculator SPI) |
| `expiresAtBusinessHours` | integer | no | Expiry in business hours |
| `excludedUsers` | string | no | Comma-separated user IDs excluded from claiming |
| `scope` | string | no | Hierarchical scope path (e.g. `casehubio/devtown/pr-review`) |

**Response:** `201 Created` (with `Location` header)
**Body:** `WorkItemResponse`

**Error:** `400` — validation failure

```bash
curl -X POST http://localhost:8080/workitems \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Review contract for Acme Corp",
    "types": ["legal"],
    "priority": "HIGH",
    "candidateGroups": "legal-team",
    "createdBy": "contract-service",
    "payload": "{\"contractId\": \"CTR-9988\"}"
  }'
```

---

### GET /workitems

Lists all WorkItems. Intended for admin use.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `label` | string | no | Filter by label pattern |
| `outcome` | string | no | Filter by outcome |

**Response:** `200 OK`
**Body:** `WorkItemResponse[]`

---

### GET /workitems/inbox/summary

Aggregate inbox counts for dashboard widgets.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `assignee` | string | no | |
| `candidateGroup` | string[] | no | Multi-value |
| `candidateUser` | string | no | |
| `status` | WorkItemStatus | no | |
| `priority` | WorkItemPriority | no | |
| `type` | string | no | Hierarchical type filter (ancestor matching) |

**Response:** `200 OK`
**Body:** `WorkItemSummaryBuilder.Summary`

| Field | Type | Description |
|---|---|---|
| `total` | long | |
| `byStatus` | map\<string, long\> | |
| `byPriority` | map\<string, long\> | |
| `overdue` | long | |
| `claimDeadlineBreached` | long | |
| `oldestCreatedAt` | instant (nullable) | `min(createdAt)` across non-terminal items; null when none exist |

---

### GET /workitems/inbox

Returns WorkItems visible to the requesting user or group with multi-instance stats. Uses OR logic across `assignee`/`candidateGroups`/`candidateUsers`; all other filters applied with AND.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `assignee` | string | no | |
| `candidateGroup` | string[] | no | Multi-value |
| `candidateUser` | string | no | |
| `status` | WorkItemStatus | no | |
| `priority` | WorkItemPriority | no | |
| `type` | string | no | Hierarchical type filter (ancestor matching) |
| `followUp` | boolean | no | Filter to items with `followUpDate` in the past |
| `outcome` | string | no | |

**Response:** `200 OK`
**Body:** `WorkItemRootResponse[]`

| Field | Type | Description |
|---|---|---|
| `item` | WorkItemResponse | |
| `childCount` | int | |
| `completedCount` | integer (nullable) | |
| `requiredCount` | integer (nullable) | |
| `groupStatus` | string (nullable) | |

```bash
curl "http://localhost:8080/workitems/inbox?assignee=alice&priority=HIGH"
```

---

### GET /workitems/{id}

Returns a single WorkItem with its complete audit trail.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `WorkItemWithAuditResponse` — all `WorkItemResponse` fields plus `auditTrail` (`AuditEntryResponse[]` — each: `id` UUID, `event` string, `actor` string, `detail` string, `occurredAt` instant)

**Error:** `404`

---

### PUT /workitems/{id}/claim

Claims the WorkItem. Transitions `PENDING → ASSIGNED`.

**Path parameter:** `id` — UUID
**Query parameter:** `claimant` — string

**Response:** `200 OK`
**Body:** `WorkItemResponse`

**Error:** `409` — not PENDING

```bash
curl -X PUT "http://localhost:8080/workitems/{id}/claim?claimant=alice"
```

---

### PUT /workitems/{id}/start

Begins work. Transitions `ASSIGNED → IN_PROGRESS`. Records `startedAt`.

**Path parameter:** `id` — UUID
**Query parameter:** `actor` — string

**Response:** `200 OK`
**Body:** `WorkItemResponse`

---

### PUT /workitems/{id}/complete

Completes the WorkItem. Transitions `IN_PROGRESS → COMPLETED`. Records `completedAt`.

**Path parameter:** `id` — UUID
**Query parameter:** `actor` — string

**Request body:** `CompleteRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `resolution` | string | no | |
| `outcome` | string | no | Named outcome; validated against `permittedOutcomes` if template-defined |

**Response:** `200 OK`
**Body:** `WorkItemResponse`

**Error:** `400` — outcome validation fails

```bash
curl -X PUT "http://localhost:8080/workitems/{id}/complete?actor=alice" \
  -H 'Content-Type: application/json' \
  -d '{"resolution": "Approved with conditions", "outcome": "APPROVED"}'
```

---

### PUT /workitems/{id}/reject

Rejects the WorkItem. Transitions `ASSIGNED`/`IN_PROGRESS → REJECTED`.

**Path parameter:** `id` — UUID
**Query parameter:** `actor` — string

**Request body:** `RejectRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `reason` | string | no | |
| `outcome` | string | no | |

**Response:** `200 OK`
**Body:** `WorkItemResponse`

---

### PUT /workitems/{id}/delegate

Delegates to another actor. Transitions `ASSIGNED`/`IN_PROGRESS → DELEGATED`.

**Path parameter:** `id` — UUID
**Query parameter:** `actor` — string

**Request body:** `DelegateRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `to` | string | yes | Target actor |
| `declineTarget` | DeclineTarget | no | `POOL` or `DELEGATOR`; controls where WorkItem goes if declined |

**Response:** `200 OK`
**Body:** `WorkItemResponse`

---

### PUT /workitems/{id}/accept-delegation

Accepts a pending delegation. Transitions `DELEGATED → ASSIGNED`.

**Path parameter:** `id` — UUID
**Query parameter:** `claimant` — string

**Response:** `200 OK`
**Body:** `WorkItemResponse`

**Error:** `409` — not DELEGATED

---

### PUT /workitems/{id}/decline-delegation

Declines a delegation. Routes based on `declineTarget`: `POOL → PENDING`, `DELEGATOR → ASSIGNED` (back to original).

**Path parameter:** `id` — UUID
**Query parameter:** `actor` — string

**Response:** `200 OK`
**Body:** `WorkItemResponse`

**Error:** `409` — not DELEGATED

---

### PUT /workitems/{id}/release

Releases a claimed WorkItem back to the pool. Transitions `ASSIGNED → PENDING`.

**Path parameter:** `id` — UUID
**Query parameter:** `actor` — string

**Response:** `200 OK`
**Body:** `WorkItemResponse`

---

### PUT /workitems/{id}/suspend

Suspends a WorkItem. Transitions `ASSIGNED`/`IN_PROGRESS → SUSPENDED`. Records `priorStatus` for restore on resume.

**Path parameter:** `id` — UUID
**Query parameter:** `actor` — string

**Request body:** `SuspendRequest` — `reason` (string, optional)

**Response:** `200 OK`
**Body:** `WorkItemResponse`

---

### PUT /workitems/{id}/resume

Resumes a suspended WorkItem. Transitions `SUSPENDED →` `priorStatus` (restores the status before suspension — either `ASSIGNED` or `IN_PROGRESS`).

**Path parameter:** `id` — UUID
**Query parameter:** `actor` — string

**Response:** `200 OK`
**Body:** `WorkItemResponse`

---

### PUT /workitems/{id}/cancel

Cancels a WorkItem. Transitions any non-terminal status → `CANCELLED`.

**Path parameter:** `id` — UUID
**Query parameter:** `actor` — string

**Request body:** `CancelRequest` — `reason` (string, optional)

**Response:** `200 OK`
**Body:** `WorkItemResponse`

---

### PUT /workitems/{id}/extend

Extends a WorkItem's expiry deadline.

**Path parameter:** `id` — UUID
**Query parameter:** `actor` — string

**Request body:** `ExtendRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `newExpiresAt` | ISO-8601 instant | yes | |

**Response:** `200 OK`
**Body:** `WorkItemResponse`

**Error:** `400` — `newExpiresAt` is in the past

---

### PUT /workitems/{id}/fault

Marks a WorkItem as FAULTED — system or infrastructure failure. Callable from any non-terminal state.

**Path parameter:** `id` — UUID

**Request body:** `FaultRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `actor` | string | yes | System actor or agent ID |
| `errorDetail` | string | no | Error description (stored as resolution) |

**Response:** `200 OK`
**Body:** `WorkItemResponse`

**Error:** `400` — WorkItem is already terminal

---

### PUT /workitems/{id}/obsolete

Marks a WorkItem as OBSOLETE — superseded by context change. Callable from any non-terminal state. Intended for engine/orchestrator use; actors should call cancel instead.

**Path parameter:** `id` — UUID

**Request body:** `ObsoleteRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `actor` | string | yes | Triggering system or identity |
| `reason` | string | no | Reason for obsolescence (stored as resolution) |

**Response:** `200 OK`
**Body:** `WorkItemResponse`

**Error:** `400` — WorkItem is already terminal

---

### PUT /workitems/{id}/progress

Reports progress on an IN_PROGRESS WorkItem. Updates percentComplete and statusNote without changing status.

**Path parameter:** `id` — UUID
**Header:** `X-Actor-Id` — string (actor reporting progress)

**Request body:** `ProgressRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `percentComplete` | integer (0–100) | no | Progress percentage |
| `statusNote` | string | no | Free-text status note |

**Response:** `200 OK`
**Body:** `WorkItemResponse`

**Error:** `400` — WorkItem is not IN_PROGRESS

---

### POST /workitems/{id}/clone

Creates a new `PENDING` WorkItem copying operational fields. Title defaults to `"{original title} (copy)"`. `MANUAL` labels are copied; `INFERRED` labels are not.

**Path parameter:** `id` — UUID

**Request body:** `CloneRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `title` | string | no | Override title |
| `createdBy` | string | yes | |

**Response:** `201 Created`
**Body:** `WorkItemResponse`

**Error:** `404`

---

## Notes

### POST /workitems/{id}/notes

Add an internal operational note to a WorkItem.

**Path parameter:** `id` — UUID

**Request body:** `NoteRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `content` | string | yes | |
| `author` | string | yes | |

**Response:** `201 Created`
**Body:** `NoteResponse` — `id` (UUID), `workItemId` (UUID), `content` (string), `author` (string), `createdAt` (instant), `editedAt` (instant, null initially)

---

### GET /workitems/{id}/notes

List all notes for a WorkItem, oldest first.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `NoteResponse[]`

---

### PUT /workitems/{id}/notes/{noteId}

Edit an existing note. Sets `editedAt` to now.

**Path parameters:** `id` — UUID, `noteId` — UUID

**Request body:** `NoteEditRequest` — `content` (string, required)

**Response:** `200 OK`
**Body:** `NoteResponse`

**Error:** `404`

---

### DELETE /workitems/{id}/notes/{noteId}

Delete a note.

**Path parameters:** `id` — UUID, `noteId` — UUID

**Response:** `204 No Content`

**Error:** `404`

---

## Links

### POST /workitems/{id}/links

Add a structured reference to an external resource (runbook, ticket, wiki page).

**Path parameter:** `id` — UUID

**Request body:** `AddLinkRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `url` | string | yes | URL of the external resource |
| `title` | string | no | |
| `relationType` | string | yes | e.g. `runbook`, `customer-ticket`, `wiki` |
| `linkedBy` | string | no | |

**Response:** `201 Created`
**Body:** `LinkResponse` — `id` (UUID), `workItemId` (UUID), `url`, `title`, `relationType`, `linkedBy`, `createdAt` (instant)

---

### GET /workitems/{id}/links

List all links, optionally filtered by relation type.

**Path parameter:** `id` — UUID
**Query parameter:** `type` — string (optional, filter by `relationType`)

**Response:** `200 OK`
**Body:** `LinkResponse[]`

---

### DELETE /workitems/{id}/links/{linkId}

**Path parameters:** `id` — UUID, `linkId` — UUID

**Response:** `204 No Content`

**Error:** `404`

---

## Relations

### POST /workitems/{id}/relations

Add a directed relation between two WorkItems. For `PART_OF`: cycles are rejected.

**Path parameter:** `id` — UUID (source)

**Request body:** `AddRelationRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `targetId` | string (UUID) | yes | |
| `relationType` | string | yes | `PART_OF`, `BLOCKS`, `DEPENDS_ON`, or custom |
| `createdBy` | string | no | |

**Response:** `201 Created`
**Body:** `RelationResponse` — `id`, `sourceId`, `targetId`, `relationType`, `createdBy`, `createdAt`

**Error:** `400` — cycle detected, `409` — duplicate

---

### GET /workitems/{id}/relations

List outgoing relations.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `RelationResponse[]`

---

### GET /workitems/{id}/relations/incoming

List incoming relations.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `RelationResponse[]`

---

### DELETE /workitems/{id}/relations/{relationId}

**Path parameters:** `id` — UUID, `relationId` — UUID

**Response:** `204 No Content`

**Error:** `404`

---

### GET /workitems/{id}/children

List direct children (WorkItems with `PART_OF` relation pointing here). Does not recurse.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `WorkItemResponse[]`

---

### GET /workitems/{id}/parent

Get the parent WorkItem.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `WorkItemResponse`

**Error:** `404` — no parent

---

## Labels and Vocabulary

### POST /workitems/{id}/labels

Add a label.

**Path parameter:** `id` — UUID

**Request body:** `AddLabelRequest` — `path` (string, required), `appliedBy` (string, optional)

**Response:** `200 OK`
**Body:** `WorkItemResponse`

---

### DELETE /workitems/{id}/labels

Remove a label.

**Path parameter:** `id` — UUID
**Query parameter:** `path` — string (required)

**Response:** `200 OK`
**Body:** `WorkItemResponse`

---

### GET /vocabulary

List all label definitions.

**Response:** `200 OK`
**Body:** array — each: `id` (UUID), `path` (string), `vocabularyId` (UUID), `scope` (string), `description` (string), `createdBy` (string), `createdAt` (instant)

---

### POST /vocabulary

Register a label definition.

**Request body:** `AddDefinitionRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `path` | string | yes | No wildcards |
| `description` | string | no | |
| `addedBy` | string | no | |
| `scope` | string | no | Hierarchical path; null/blank = root/global |

**Response:** `201 Created`
**Body:** `id` (UUID), `path` (string), `scope` (string)

---

## Templates

### POST /workitem-templates

Create a WorkItem template.

**Request body:** `CreateTemplateRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes | Unique template name |
| `description` | string | no | |
| `typePaths` | string | no | JSON array of type paths applied at instantiation |
| `priority` | string | no | `LOW`/`MEDIUM`/`HIGH`/`URGENT` |
| `candidateGroups` | string | no | Comma-separated |
| `candidateUsers` | string | no | Comma-separated |
| `requiredCapabilities` | string | no | Comma-separated |
| `defaultExpiryHours` | integer | no | Calendar-hour expiry |
| `defaultClaimHours` | integer | no | Calendar-hour claim deadline |
| `defaultExpiryBusinessHours` | integer | no | Business-hour expiry |
| `defaultClaimBusinessHours` | integer | no | Business-hour claim deadline |
| `defaultPayload` | string | no | JSON |
| `labelPaths` | string | no | JSON array of label paths |
| `instanceCount` | integer | no | Multi-instance count |
| `requiredCount` | integer | no | M-of-N completion threshold |
| `parentRole` | string | no | `COORDINATOR` or `PARTICIPANT` |
| `assignmentStrategy` | string | no | CDI bean name |
| `onThresholdReached` | string | no | `KEEP`, `SUSPEND`, or `CANCEL` |
| `allowSameAssignee` | boolean | no | |
| `outcomes` | Outcome[] | no | Each: `name`, `displayName`, `condition` |
| `inputDataSchema` | JSON | no | JSON Schema (draft-07) for payload |
| `outputDataSchema` | JSON | no | JSON Schema (draft-07) for resolution |
| `excludedUsers` | string | no | Comma-separated |
| `excludedGroups` | string | no | Comma-separated |
| `scope` | string | no | |
| `createdBy` | string | yes | |

**Response:** `201 Created` — includes `version` (long, starts at 1)

**Error:** `400`, `409` (name conflict)

---

### GET /workitem-templates

List all templates.

**Response:** `200 OK`

---

### GET /workitem-templates/{id}

**Path parameter:** `id` — UUID

**Response:** `200 OK`

**Error:** `404`

---

### DELETE /workitem-templates/{id}

Does NOT delete WorkItems previously instantiated from it.

**Path parameter:** `id` — UUID

**Response:** `204 No Content`

**Error:** `404`

---

### PUT /workitem-templates/{id}

Full replacement update. All mutable fields overwritten; null clears. `createdBy` is immutable.

**Path parameter:** `id` — UUID

**Request body:** `UpdateTemplateRequest` (same fields as create, minus `createdBy`)

**Response:** `200 OK`

**Error:** `400`, `404`, `409`

---

### PATCH /workitem-templates/{id}

Partial update via JSON Merge Patch (RFC 7396). Fields present in patch are applied (null clears); absent fields unchanged. `createdBy` not patchable.

**Content-Type:** `application/merge-patch+json`

**Path parameter:** `id` — UUID

**Response:** `200 OK`

**Error:** `400`, `404`, `409`

---

### POST /workitem-templates/{id}/instantiate

Create a WorkItem from a template. WorkItem inherits all template defaults.

**Path parameter:** `id` — UUID

**Request body:** `InstantiateRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `title` | string | no | Override |
| `assigneeId` | string | no | |
| `createdBy` | string | yes | |

**Response:** `201 Created`
**Body:** `WorkItemResponse`

**Error:** `400`, `404`

---

## Schedules

### POST /workitem-schedules

Create a recurring schedule that instantiates a template on a cron expression.

**Request body:** `CreateScheduleRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes | |
| `templateId` | string (UUID) | yes | |
| `cronExpression` | string | yes | Quartz 6-field cron |
| `createdBy` | string | no | |

**Response:** `201 Created`
**Body:** `ScheduleResponse` — `id`, `name`, `templateId`, `cronExpression`, `active`, `createdBy`, `createdAt`, `lastFiredAt`, `nextFireAt`

---

### GET /workitem-schedules

List all schedules.

**Response:** `200 OK`
**Body:** `ScheduleResponse[]`

---

### GET /workitem-schedules/{id}

**Path parameter:** `id` — UUID

**Response:** `200 OK`

**Error:** `404`

---

### DELETE /workitem-schedules/{id}

**Path parameter:** `id` — UUID

**Response:** `204 No Content`

**Error:** `404`

---

### PUT /workitem-schedules/{id}/active

Enable or disable a schedule. Re-enabling recomputes `nextFireAt` so missed periods are not all fired at once.

**Path parameter:** `id` — UUID

**Request body:** `SetActiveRequest` — `active` (boolean, required)

**Response:** `200 OK`
**Body:** `ScheduleResponse`

**Error:** `400`, `404`

---

## Spawning and Multi-Instance

### POST /workitems/{id}/spawn

Spawn a group of child WorkItems from templates. Idempotent — second call with same `idempotencyKey` returns existing group (200).

**Path parameter:** `id` — UUID (parent)

**Request body:** `SpawnBodyRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `idempotencyKey` | string | yes | |
| `children` | SpawnChildRequest[] | yes | Each: `templateId` (UUID, required), `callerRef` (string, optional), `overrides` (map, optional) |

**Response:** `201 Created` (new) or `200 OK` (idempotent replay)
**Body:** `groupId` (string), `children` (array of `workItemId`, `callerRef`)

**Error:** `400`, `404`, `409`, `422`

---

### GET /workitems/{id}/spawn-groups

List spawn groups for a parent.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** array — each: `id`, `parentId`, `idempotencyKey`, `createdAt`

---

### DELETE /workitems/{id}/spawn-groups/{groupId}

Cancel a spawn group.

**Path parameters:** `id` — UUID, `groupId` — UUID
**Query parameter:** `cancelChildren` — boolean (if true, cancels PENDING children)

**Response:** `204 No Content`

**Error:** `404`

---

### GET /spawn-groups/{groupId}

Fetch a spawn group by ID. Includes `PART_OF` children.

**Path parameter:** `groupId` — UUID

**Response:** `200 OK`
**Body:** `id`, `parentId`, `idempotencyKey`, `createdAt`, `children` (array of `workItemId`, `createdAt`)

**Error:** `404`

---

### GET /workitems/{id}/instances

Get multi-instance children with M-of-N group summary. 404 for non-multi-instance parents.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `InstancesResponse`

| Field | Type | Description |
|---|---|---|
| `parentId` | UUID | |
| `groupId` | UUID | |
| `instanceCount` | int | |
| `requiredCount` | int | M-of-N threshold |
| `completedCount` | int | |
| `rejectedCount` | int | |
| `groupStatus` | string | `IN_PROGRESS`, `COMPLETED`, or `REJECTED` |
| `instances` | WorkItemResponse[] | |

**Error:** `404`

---

## Bulk Operations

### POST /workitems/bulk

Execute a bulk operation across multiple WorkItems. Partial success allowed. Max batch size: 100.

**Request body:** `BulkRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `operation` | string | yes | `claim` or `cancel` |
| `workItemIds` | string[] | yes | Max 100 |
| `actorId` | string | no | |
| `reason` | string | no | Used for `cancel` |

**Response:** `200 OK` (even with partial failures)
**Body:** `BulkItemResult[]` — each: `id` (string), `status` (`ok`/`error`), `error` (string, nullable)

**Error:** `400` — batch > 100

---

## Filter Rules

### POST /filter-rules

Create a dynamic filter rule. Filter rules run when WorkItems are created or updated, evaluating a condition expression and applying actions.

**Request body:** `CreateFilterRuleRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | yes | |
| `description` | string | no | |
| `enabled` | boolean | no | Default `true` |
| `condition` | string | yes | JEXL or JQ expression |
| `events` | string[] | no | Default `["ADD","UPDATE","REMOVE"]` |
| `actionsJson` | string | no | JSON-encoded actions array (default `"[]"`) |

**Response:** `201 Created`
**Body:** `id`, `name`, `description`, `enabled`, `condition`, `events`, `actionsJson`, `createdAt`

---

### GET /filter-rules

List all dynamic filter rules.

**Response:** `200 OK`

---

### GET /filter-rules/{id}

**Path parameter:** `id` — UUID

**Response:** `200 OK`

**Error:** `404`

---

### DELETE /filter-rules/{id}

**Path parameter:** `id` — UUID

**Response:** `204 No Content`

**Error:** `404`

---

### GET /filter-rules/permanent

List all permanent (CDI-produced) filter rules defined in code.

**Response:** `200 OK`
**Body:** array — each: `name`, `description`, `enabled`, `events`, `condition`

---

### PUT /filter-rules/permanent/enabled

Toggle a permanent rule's enabled state at runtime. Override is in-memory — resets on restart.

**Query parameter:** `name` — string (required)

**Request body:** `{"enabled": true|false}`

**Response:** `200 OK`

**Error:** `400`, `404`

---

## Queues

*Module: `casehub-work-queues`*

### GET /queues

List all queue views.

**Response:** `200 OK`
**Body:** array — each: `id` (UUID), `name` (string), `labelPattern` (string), `scope` (string)

---

### POST /queues

Create a new queue view.

**Request body:** `CreateQueueRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | no | |
| `labelPattern` | string | yes | Glob/regex matched against WorkItem labels |
| `scope` | string | no | Path-based scope |
| `additionalConditions` | string | no | JEXL expression |
| `sortField` | string | no | Default `createdAt` |
| `sortDirection` | string | no | `ASC` or `DESC`, default `ASC` |

**Response:** `201 Created`

---

### GET /queues/{id}

Query the live content of a queue view.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `WorkItemResponse[]`

**Error:** `404`

---

### DELETE /queues/{id}

**Path parameter:** `id` — UUID

**Response:** `204 No Content`

**Error:** `404`

---

### GET /queues/{id}/summary

Pre-computed aggregates for a queue view's current membership. Same response shape as `GET /workitems/inbox/summary`.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `WorkItemSummaryBuilder.Summary`

| Field | Type | Description |
|---|---|---|
| `total` | long | |
| `byStatus` | map\<string, long\> | |
| `byPriority` | map\<string, long\> | |
| `overdue` | long | |
| `claimDeadlineBreached` | long | |
| `oldestCreatedAt` | instant (nullable) | `min(createdAt)` across non-terminal items; null when none exist |

**Error:** `404` — queue not found

---

### GET /queues/{id}/events

SSE stream of queue membership events. Hot stream — only events after connect are delivered.

**Path parameter:** `id` — UUID
**Produces:** `text/event-stream`

**Response:** `200 OK` (streaming)
**Body:** `WorkItemQueueEvent` per event — `workItemId` (UUID), `queueViewId` (UUID), `queueName` (string), `eventType` (`ADDED`/`REMOVED`/`CHANGED`), `tenancyId` (string)

---

### GET /queues/{id}/trend

Historical queue membership trend data for sparkline visualisation.

**Path parameter:** `id` — UUID

**Query parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `period` | Duration string | `24h` | How far back (`1h`, `24h`, `7d`, `30d`). Accepts ISO-8601 (`PT24H`, `P7D`) and shorthand. |

**Response:** `200 OK`
**Body:** `QueueTrendResponse`

| Field | Type | Description |
|---|---|---|
| `queueViewId` | UUID | |
| `queueName` | string | |
| `period` | string | ISO-8601 duration |
| `dataPoints` | DataPoint[] | Ordered by `snapshotAt` ascending |

DataPoint: `snapshotAt` (instant), `memberCount` (long)

**Error:** `404` — queue not found

---

### PUT /workitems/{id}/pickup

Queue pickup — claim or soft-takeover. `PENDING` = standard claim. `ASSIGNED` with relinquishable flag = soft pickup with ownership transfer.

**Path parameter:** `id` — UUID
**Query parameter:** `claimant` — string

**Response:** `200 OK`
**Body:** `WorkItemResponse`

**Error:** `404`, `409` (ASSIGNED and not relinquishable)

---

### PUT /workitems/{id}/relinquishable

Set or clear the relinquishable flag.

**Path parameter:** `id` — UUID

**Request body:** `RelinquishableRequest` — `relinquishable` (boolean)

**Response:** `200 OK`
**Body:** `workItemId` (UUID), `relinquishable` (boolean)

**Error:** `404`

---

### GET /filters

List all queue filters.

**Response:** `200 OK`
**Body:** array — each: `id` (UUID), `name`, `scope`, `conditionLanguage`, `active` (boolean)

---

### POST /filters

Create a queue filter.

**Request body:** `CreateFilterRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `name` | string | no | |
| `scope` | string | no | Path-based scope |
| `conditionLanguage` | string | no | `jexl` or `jq` |
| `conditionExpression` | string | no | |
| `actions` | FilterAction[] | no | |

**Response:** `201 Created`

---

### PUT /filters/{id}

Update a queue filter. Null fields left unchanged.

**Path parameter:** `id` — UUID

**Response:** `200 OK`

**Error:** `404`

---

### DELETE /filters/{id}

**Path parameter:** `id` — UUID

**Response:** `204 No Content`

**Error:** `404`

---

### POST /filters/evaluate

Ad-hoc filter evaluation against a synthetic WorkItem without persisting.

**Request body:** `AdHocEvalRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `conditionLanguage` | string | yes | `jexl` or `jq` |
| `conditionExpression` | string | yes | |
| `workItem` | object | yes | `title`, `status`, `priority`, `assigneeId`, `types` |

**Response:** `200 OK`
**Body:** `matches` (boolean)

---

## Audit

### GET /audit

Cross-WorkItem audit history query. All filters optional and combinable. Paginated.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `actorId` | string | no | Exact match on actor |
| `from` | ISO-8601 | no | Inclusive lower bound on `occurredAt` |
| `to` | ISO-8601 | no | Inclusive upper bound |
| `event` | string | no | Exact match on event type (e.g. `COMPLETED`) |
| `type` | string | no | Hierarchical type filter |
| `page` | int | no | Zero-based (default `0`) |
| `size` | int | no | Page size (default `20`, max `100`) |

**Response:** `200 OK`
**Body:** `entries` (array of `id`, `workItemId`, `event`, `actor`, `detail`, `occurredAt`), `page`, `size`, `total`

```bash
curl "http://localhost:8080/audit?actorId=alice&event=COMPLETED&from=2025-01-01T00:00:00Z&size=50"
```

---

## Ledger

*Module: `casehub-work-ledger`*

### GET /workitems/{id}/ledger

Retrieve all ledger entries for a WorkItem with attestations, ordered by sequence.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `LedgerEntryResponse[]`

| Field | Type | Description |
|---|---|---|
| `id` | UUID | |
| `subjectId` | UUID | WorkItem ID |
| `sequenceNumber` | int | |
| `entryType` | LedgerEntryType | |
| `commandType` | string (nullable) | e.g. `CompleteWorkItem` |
| `eventType` | string (nullable) | e.g. `WorkItemCompleted` |
| `actorId` | string | |
| `actorType` | ActorType | `HUMAN`, `AGENT`, `SYSTEM` |
| `actorRole` | string (nullable) | |
| `causedByEntryId` | UUID (nullable) | Causal link |
| `digest` | string (nullable) | SHA-256 leaf hash |
| `occurredAt` | instant | |
| `attestations` | LedgerAttestationResponse[] | |

Attestation fields: `id`, `ledgerEntryId`, `subjectId`, `attestorId`, `attestorType`, `verdict` (`SOUND`/`ENDORSED`/`FLAGGED`/`CHALLENGED`), `evidence`, `confidence` (0.0–1.0), `occurredAt`

**Error:** `404`

---

### PUT /workitems/{id}/ledger/provenance

Set source entity provenance on the creation entry (seq 1). Called by integrating systems after WorkItem creation.

**Path parameter:** `id` — UUID

**Request body:** `ProvenanceRequest` — `sourceEntityId`, `sourceEntityType`, `sourceEntitySystem` (all string)

**Response:** `200 OK`

**Error:** `404`, `409` (already set)

---

### POST /workitems/{id}/ledger/{entryId}/attestations

Post a peer attestation on a specific ledger entry.

**Path parameters:** `id` — UUID, `entryId` — UUID

**Request body:** `LedgerAttestationRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `attestorId` | string | yes | |
| `attestorType` | ActorType | yes | `HUMAN`, `AGENT`, `SYSTEM` |
| `verdict` | AttestationVerdict | yes | `SOUND`, `ENDORSED`, `FLAGGED`, `CHALLENGED` |
| `evidence` | string | no | |
| `confidence` | double | yes | 0.0–1.0 |

**Response:** `201 Created`

**Error:** `404`, `409` (attestations disabled)

---

### GET /workitems/actors/{actorId}/trust

Get computed Bayesian Beta trust score for an actor.

**Path parameter:** `actorId` — string

**Response:** `200 OK`
**Body:** `ActorTrustScoreResponse` — `actorId`, `actorType`, `trustScore` (0.0–1.0, neutral prior 0.5), `decisionCount`, `overturnedCount`, `attestationPositive`, `attestationNegative`, `lastComputedAt`

**Error:** `404` (scoring disabled or no score computed)

---

## AI

*Module: `casehub-work-ai`*

### GET /workitems/{id}/resolution-suggestion

AI resolution suggestion based on similar past WorkItems. Always returns 200. `suggestion` is null when no suggestion produced; check `modelAvailable` to distinguish "no model" from "model returned nothing".

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `ResolutionSuggestionResponse` — `workItemId` (UUID), `suggestion` (string, nullable), `basedOn` (int), `modelAvailable` (boolean)

**Error:** `404`

---

### POST /worker-skill-profiles

Upsert a worker skill profile for semantic skill matching.

**Request body:** `ProfileRequest` — `workerId` (string, required), `narrative` (string, optional)

**Response:** `201 Created`

---

### GET /worker-skill-profiles

List all worker skill profiles.

**Response:** `200 OK`

---

### GET /worker-skill-profiles/{workerId}

**Path parameter:** `workerId` — string

**Response:** `200 OK`

**Error:** `404`

---

### DELETE /worker-skill-profiles/{workerId}

**Path parameter:** `workerId` — string

**Response:** `204 No Content`

**Error:** `404`

---

### GET /workitems/{id}/escalation-summaries

LLM-generated escalation summaries for a WorkItem, most recent first.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `EscalationSummary[]` — each: `id` (UUID), `tenancyId`, `workItemId` (UUID), `eventType` (`EXPIRED`/`CLAIM_EXPIRED`), `summary` (string, nullable), `generatedAt` (instant)

---

## Notifications

*Module: `casehub-work-notifications`*

### POST /workitem-notification-rules

Create a notification rule.

**Request body:** `CreateRuleRequest`

| Field | Type | Required | Description |
|---|---|---|---|
| `channelType` | string | yes | `webhook` or `slack` |
| `targetUrl` | string | yes | |
| `eventTypes` | string | yes | Comma-separated event types |
| `types` | string | no | Optional type filter |
| `secret` | string | no | Webhook HMAC secret |
| `enabled` | boolean | no | Default `true` |

**Response:** `201 Created`
**Body:** `id`, `channelType`, `targetUrl`, `eventTypes`, `types`, `enabled`, `createdAt` (secret intentionally omitted)

---

### GET /workitem-notification-rules

List rules, optionally filtered by channel type.

**Query parameter:** `channelType` — string (optional)

**Response:** `200 OK` (secret omitted)

---

### GET /workitem-notification-rules/{id}

**Path parameter:** `id` — UUID

**Response:** `200 OK` (secret omitted)

**Error:** `404`

---

### PUT /workitem-notification-rules/{id}

**Path parameter:** `id` — UUID

**Request body:** `CreateRuleRequest` (all fields optional for update)

**Response:** `200 OK`

**Error:** `404`

---

### DELETE /workitem-notification-rules/{id}

**Path parameter:** `id` — UUID

**Response:** `204 No Content`

**Error:** `404`

---

## Reports

*Module: `casehub-work-reports`*

### GET /workitems/reports/sla-breaches

SLA breach report — WorkItems that exceeded their expiry deadline.

**Query parameters:** `from` (ISO-8601), `to` (ISO-8601), `type` (string), `priority` (string) — all optional

**Response:** `200 OK`
**Body:** `SlaBreachReport` — `items` (array of `workItemId`, `type`, `priority`, `expiresAt`, `completedAt`, `status`, `breachDurationMinutes`), `summary` (`totalBreached`, `avgBreachDurationMinutes`, `byType` map)

---

### GET /workitems/reports/actors/{actorId}

Actor performance report.

**Path parameter:** `actorId` — string
**Query parameters:** `from` (ISO-8601), `to` (ISO-8601), `type` (string) — all optional

**Response:** `200 OK`
**Body:** `ActorReport` — `actorId`, `totalAssigned`, `totalCompleted`, `totalRejected`, `avgCompletionMinutes` (nullable), `byType` map

---

### GET /workitems/reports/throughput

Throughput report — created vs completed counts over time buckets.

**Query parameters:** `from` (ISO-8601, required), `to` (ISO-8601, required), `groupBy` (string — `day`/`week`/`month`, default `day`)

**Response:** `200 OK`
**Body:** `ThroughputReport` — `from`, `to`, `groupBy`, `buckets` (array of `period`, `created`, `completed`)

---

### GET /workitems/reports/queue-health

Queue health snapshot.

**Query parameters:** `type` (string), `priority` (string) — all optional

**Response:** `200 OK`
**Body:** `QueueHealthReport` — `timestamp`, `overdueCount`, `pendingCount`, `avgPendingAgeSeconds`, `oldestUnclaimedCreatedAt`, `criticalOverdueCount`

---

## Issue Tracker Integration

*Module: `casehub-work-issue-tracker`*

### POST /workitems/{id}/issues

Link an existing issue. Fetches current title/URL/status from remote tracker. Idempotent.

**Path parameter:** `id` — UUID

**Request body:** `LinkIssueRequest` — `trackerType` (string, required — `github`/`jira`), `externalRef` (string, required — e.g. `owner/repo#42`), `linkedBy` (string)

**Response:** `201 Created`
**Body:** `id`, `workItemId`, `trackerType`, `externalRef`, `title`, `url`, `status`, `linkedAt`, `linkedBy`

**Error:** `400`, `404`, `500`

---

### POST /workitems/{id}/issues/create

Create a new issue in remote tracker and link it. WorkItem UUID appended to issue body.

**Path parameter:** `id` — UUID

**Request body:** `CreateIssueRequest` — `trackerType` (string, required), `title` (string, required), `body` (string — markdown), `linkedBy` (string)

**Response:** `201 Created`

**Error:** `400`, `500`

---

### GET /workitems/{id}/issues

List linked issues.

**Path parameter:** `id` — UUID

**Response:** `200 OK`

---

### DELETE /workitems/{id}/issues/{linkId}

Remove a link. Does NOT close the issue in the remote tracker.

**Path parameters:** `id` — UUID, `linkId` — UUID

**Response:** `204 No Content`

**Error:** `404`

---

### PUT /workitems/{id}/issues/sync

Refresh linked issues from remote trackers. Failed fetches skipped.

**Path parameter:** `id` — UUID

**Response:** `200 OK`
**Body:** `synced` (int), `workItemId` (UUID)

---

### POST /workitems/github-webhook/{tenancyId}

Receive GitHub webhook events. Verifies `X-Hub-Signature-256` HMAC. Returns 200 for all valid requests.

**Path parameter:** `tenancyId` — string
**Headers:** `X-Hub-Signature-256`

**Response:** `200 OK`

**Error:** `400`, `401`

---

### POST /workitems/jira-webhook/{tenancyId}

Receive Jira webhook events. Verification via shared secret query parameter.

**Path parameter:** `tenancyId` — string
**Query parameter:** `secret` — string

**Response:** `200 OK`

**Error:** `400`, `401`

---

## SSE Events

### GET /workitems/events

Server-Sent Events stream of all WorkItem lifecycle events. Hot stream — only events after connect.

**Produces:** `text/event-stream`

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `workItemId` | UUID | no | Filter to single WorkItem |
| `type` | string | no | Event type suffix (e.g. `created`, `completed`) |

**Body:** `WorkItemLifecycleEvent` per event

| Field | Type | Description |
|---|---|---|
| `type` | string | CloudEvents type (e.g. `io.casehub.work.workitem.created`) |
| `source` | string | URI (e.g. `/workitems/{id}`) |
| `subject` | string | WorkItem UUID |
| `workItemId` | UUID | |
| `status` | WorkItemStatus | |
| `occurredAt` | instant | |
| `actor` | string | |
| `detail` | string (nullable) | |
| `rationale` | string (nullable) | |
| `planRef` | string (nullable) | |
| `outcome` | string (nullable) | |

```bash
curl -N "http://localhost:8080/workitems/events?type=completed"
```

---

### GET /workitems/{id}/events

SSE stream for a specific WorkItem.

**Path parameter:** `id` — UUID
**Produces:** `text/event-stream`

Same event schema as above.

---

## AsyncAPI

### GET /q/asyncapi

Serve the AsyncAPI 3.0 specification YAML.

**Produces:** `application/yaml`

**Response:** `200 OK`
**Body:** YAML

**Error:** `503` — resource not found on classpath

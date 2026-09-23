# io.casehub.work.api.WorkItemCreateRequest

**Package:** `io.casehub.work.api`

**Kind:** `class`

## Fields

### `assigneeId` (`java.lang.String`)

### `auditDetail` (`java.lang.String`)

Optional detail appended to the CREATED audit entry. Used to record group expansion notes.

### `callerRef` (`java.lang.String`)

### `candidateGroups` (`java.lang.String`)

### `candidateScores` (`java.lang.String`)

### `candidateUsers` (`java.lang.String`)

### `claimDeadline` (`java.time.Instant`)

### `claimDeadlineBusinessHours` (`java.lang.Integer`)

### `confidenceScore` (`java.lang.Double`)

### `createdBy` (`java.lang.String`)

### `description` (`java.lang.String`)

### `excludedUsers` (`java.lang.String`)

### `expiresAt` (`java.time.Instant`)

### `expiresAtBusinessHours` (`java.lang.Integer`)

### `followUpDate` (`java.time.Instant`)

### `formKey` (`java.lang.String`)

### `inputDataSchema` (`java.lang.String`)

### `labels` (`java.util.List<io.casehub.work.api.WorkItemLabelRequest>`)

### `outputDataSchema` (`java.lang.String`)

### `payload` (`java.lang.String`)

### `payloadTypeName` (`java.lang.String`)

### `permittedOutcomes` (`java.util.List<io.casehub.work.api.Outcome>`)

### `priority` (`io.casehub.work.api.WorkItemPriority`)

### `requiredCapabilities` (`java.lang.String`)

### `resolutionTypeName` (`java.lang.String`)

### `routingExperiences` (`java.lang.String`)

### `scope` (`java.lang.String`)

Hierarchical scope path e.g. `"casehubio/devtown/pr-review"`; null means root scope.

### `templateId` (`java.util.UUID`)

### `templateVersion` (`java.lang.Long`)

Version of the template used at instantiation; null for non-template WorkItems.

### `tenancyId` (`java.lang.String`)

Tenant identifier for multi-tenant SPI callers.

### `title` (`java.lang.String`)

### `types` (`java.util.List<java.lang.String>`)

## Constructors

### `private WorkItemCreateRequest(io.casehub.work.api.WorkItemCreateRequest.Builder b)`

#### Parameters

- `b` (`io.casehub.work.api.WorkItemCreateRequest.Builder`)

## Methods

### `public static io.casehub.work.api.WorkItemCreateRequest.Builder builder()`

### `public boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public int hashCode()`

### `public io.casehub.work.api.WorkItemCreateRequest.Builder toBuilder()`

### `public java.lang.String toString()`

Intentionally omits payload, schemas, callerRef, and credentials — log-safety.

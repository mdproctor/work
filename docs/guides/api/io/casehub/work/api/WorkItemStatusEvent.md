# io.casehub.work.api.WorkItemStatusEvent

**Package:** `io.casehub.work.api`

**Kind:** `record`

Lightweight status-change event carrying only api-module types.
This is the SPI-visible subset of the runtime's WorkItemLifecycleEvent.

## Fields

### `actor` (`java.lang.String`)

### `assigneeId` (`java.lang.String`)

### `callerRef` (`java.lang.String`)

### `candidateGroups` (`java.lang.String`)

### `detail` (`java.lang.String`)

### `eventType` (`io.casehub.work.api.WorkEventType`)

### `occurredAt` (`java.time.Instant`)

### `outcome` (`java.lang.String`)

### `status` (`io.casehub.work.api.WorkItemStatus`)

### `tenancyId` (`java.lang.String`)

### `workItemId` (`java.util.UUID`)

## Record Components

### `actor` (`java.lang.String`)

### `assigneeId` (`java.lang.String`)

### `callerRef` (`java.lang.String`)

### `candidateGroups` (`java.lang.String`)

### `detail` (`java.lang.String`)

### `eventType` (`io.casehub.work.api.WorkEventType`)

### `occurredAt` (`java.time.Instant`)

### `outcome` (`java.lang.String`)

### `status` (`io.casehub.work.api.WorkItemStatus`)

### `tenancyId` (`java.lang.String`)

### `workItemId` (`java.util.UUID`)

## Constructors

### `public WorkItemStatusEvent(io.casehub.work.api.WorkEventType eventType, java.util.UUID workItemId, io.casehub.work.api.WorkItemStatus status, java.lang.String actor, java.lang.String detail, java.lang.String callerRef, java.lang.String assigneeId, java.lang.String candidateGroups, java.lang.String outcome, java.lang.String tenancyId, java.time.Instant occurredAt)`

#### Parameters

- `eventType` (`io.casehub.work.api.WorkEventType`)
- `workItemId` (`java.util.UUID`)
- `status` (`io.casehub.work.api.WorkItemStatus`)
- `actor` (`java.lang.String`)
- `detail` (`java.lang.String`)
- `callerRef` (`java.lang.String`)
- `assigneeId` (`java.lang.String`)
- `candidateGroups` (`java.lang.String`)
- `outcome` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `occurredAt` (`java.time.Instant`)

## Methods

### `public java.lang.String actor()`

### `public java.lang.String assigneeId()`

### `public java.lang.String callerRef()`

### `public java.lang.String candidateGroups()`

### `public java.lang.String detail()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public io.casehub.work.api.WorkEventType eventType()`

### `public final int hashCode()`

### `public java.time.Instant occurredAt()`

### `public java.lang.String outcome()`

### `public io.casehub.work.api.WorkItemStatus status()`

### `public java.lang.String tenancyId()`

### `public final java.lang.String toString()`

### `public java.util.UUID workItemId()`

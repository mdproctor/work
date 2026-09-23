# io.casehub.work.api.WorkItemRef

**Package:** `io.casehub.work.api`

**Kind:** `record`

## Fields

### `assigneeId` (`java.lang.String`)

### `callerRef` (`java.lang.String`)

### `candidateGroups` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `outcome` (`java.lang.String`)

### `payload` (`java.lang.String`)

### `payloadTypeName` (`java.lang.String`)

### `resolution` (`java.lang.String`)

### `resolutionTypeName` (`java.lang.String`)

### `status` (`io.casehub.work.api.WorkItemStatus`)

### `tenancyId` (`java.lang.String`)

## Record Components

### `assigneeId` (`java.lang.String`)

### `callerRef` (`java.lang.String`)

### `candidateGroups` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `outcome` (`java.lang.String`)

### `payload` (`java.lang.String`)

### `payloadTypeName` (`java.lang.String`)

### `resolution` (`java.lang.String`)

### `resolutionTypeName` (`java.lang.String`)

### `status` (`io.casehub.work.api.WorkItemStatus`)

### `tenancyId` (`java.lang.String`)

## Constructors

### `public WorkItemRef(java.util.UUID id, io.casehub.work.api.WorkItemStatus status, java.lang.String callerRef, java.lang.String assigneeId, java.lang.String resolution, java.lang.String candidateGroups, java.lang.String outcome, java.lang.String tenancyId, java.lang.String payload, java.lang.String payloadTypeName, java.lang.String resolutionTypeName)`

#### Parameters

- `id` (`java.util.UUID`)
- `status` (`io.casehub.work.api.WorkItemStatus`)
- `callerRef` (`java.lang.String`)
- `assigneeId` (`java.lang.String`)
- `resolution` (`java.lang.String`)
- `candidateGroups` (`java.lang.String`)
- `outcome` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `payload` (`java.lang.String`)
- `payloadTypeName` (`java.lang.String`)
- `resolutionTypeName` (`java.lang.String`)

## Methods

### `public java.lang.String assigneeId()`

### `public java.lang.String callerRef()`

### `public java.lang.String candidateGroups()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.String outcome()`

### `public java.lang.String payload()`

### `public java.lang.String payloadTypeName()`

### `public java.lang.String resolution()`

### `public java.lang.String resolutionTypeName()`

### `public io.casehub.work.api.WorkItemStatus status()`

### `public java.lang.String tenancyId()`

### `public final java.lang.String toString()`

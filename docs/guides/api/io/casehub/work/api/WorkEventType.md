# io.casehub.work.api.WorkEventType

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Canonical lifecycle event vocabulary shared across all work-management systems.
WorkItems, CaseHub tasks, and future work-unit types all map to these values.

## Enum Constants

### `ASSIGNED` (`io.casehub.work.api.WorkEventType`)

### `CANCELLED` (`io.casehub.work.api.WorkEventType`)

### `CLAIM_EXPIRED` (`io.casehub.work.api.WorkEventType`)

Claim deadline passed without the work being claimed.

### `COMPLETED` (`io.casehub.work.api.WorkEventType`)

### `CREATED` (`io.casehub.work.api.WorkEventType`)

### `DEADLINE_EXTENDED` (`io.casehub.work.api.WorkEventType`)

expiresAt deadline was extended forward by an actor or breach policy.

### `DELEGATED` (`io.casehub.work.api.WorkEventType`)

### `DELEGATION_ACCEPTED` (`io.casehub.work.api.WorkEventType`)

Delegatee accepted a targeted delegation.

### `DELEGATION_DECLINED` (`io.casehub.work.api.WorkEventType`)

Delegatee declined a targeted delegation.

### `ESCALATED` (`io.casehub.work.api.WorkEventType`)

### `EXPIRED` (`io.casehub.work.api.WorkEventType`)

### `FAULTED` (`io.casehub.work.api.WorkEventType`)

System or infrastructure failure.

### `LABEL_ADDED` (`io.casehub.work.api.WorkEventType`)

A label was added to the WorkItem.

### `LABEL_REMOVED` (`io.casehub.work.api.WorkEventType`)

A label was removed from the WorkItem.

### `MANUALLY_ESCALATED` (`io.casehub.work.api.WorkEventType`)

Actor explicitly escalated the WorkItem to a different candidate group.

### `OBSOLETE` (`io.casehub.work.api.WorkEventType`)

WorkItem superseded by context change.

### `PROGRESS_UPDATE` (`io.casehub.work.api.WorkEventType`)

Actor reported progress (percentComplete, statusNote).

### `REJECTED` (`io.casehub.work.api.WorkEventType`)

### `RELEASED` (`io.casehub.work.api.WorkEventType`)

### `RESUMED` (`io.casehub.work.api.WorkEventType`)

### `SIGNAL_RECEIVED` (`io.casehub.work.api.WorkEventType`)

An external signal was received and routed to this work unit.

### `SLA_EXTENDED` (`io.casehub.work.api.WorkEventType`)

SLA breach policy extended the deadline.

### `SLA_REASSIGNED` (`io.casehub.work.api.WorkEventType`)

WorkItem re-routed to new candidate groups by SLA breach policy.

### `SPAWNED` (`io.casehub.work.api.WorkEventType`)

Child WorkItems were spawned from this work unit.

### `STARTED` (`io.casehub.work.api.WorkEventType`)

### `SUSPENDED` (`io.casehub.work.api.WorkEventType`)

## Constructors

### `private WorkEventType()`

## Methods

### `public static io.casehub.work.api.WorkEventType valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.WorkEventType[] values()`

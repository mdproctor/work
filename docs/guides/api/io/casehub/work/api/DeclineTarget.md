# io.casehub.work.api.DeclineTarget

**Package:** `io.casehub.work.api`

**Kind:** `enum`

Determines where a delegated `io.casehub.work.api.WorkItem` returns
when the delegatee declines.

<p>Configure the scope-level default via `.KEY` through
`io.casehub.platform.api.preferences.PreferenceProvider`.
Override per WorkItem at delegation time via
`DelegateRequest.declineTarget()` (in the REST module).

## Fields

### `KEY` (`PreferenceKey<io.casehub.work.api.DeclineTarget>`)

Preference key for the scope-level default.
Qualified name: `casehub.work.delegation.decline-target`.
Default: `.POOL`.

## Enum Constants

### `DELEGATOR` (`io.casehub.work.api.DeclineTarget`)

Item returns to the actor who delegated it (last entry in delegationChain).

### `POOL` (`io.casehub.work.api.DeclineTarget`)

Item returns to the general pool with candidateGroups unchanged. Default.

## Constructors

### `private DeclineTarget()`

## Methods

### `public java.lang.String toSerializedValue()`

### `public static io.casehub.work.api.DeclineTarget valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.work.api.DeclineTarget[] values()`

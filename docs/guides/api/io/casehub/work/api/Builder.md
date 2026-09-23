# io.casehub.work.api.WorkItemCreateRequest.Builder

**Package:** `io.casehub.work.api`

**Kind:** `class`

## Fields

### `assigneeId` (`java.lang.String`)

### `auditDetail` (`java.lang.String`)

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

### `templateId` (`java.util.UUID`)

### `templateVersion` (`java.lang.Long`)

### `tenancyId` (`java.lang.String`)

### `title` (`java.lang.String`)

### `types` (`java.util.List<java.lang.String>`)

## Constructors

### `private Builder()`

### `private Builder(io.casehub.work.api.WorkItemCreateRequest src)`

#### Parameters

- `src` (`io.casehub.work.api.WorkItemCreateRequest`)

## Methods

### `public io.casehub.work.api.WorkItemCreateRequest.Builder assigneeId(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder auditDetail(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest build()`

### `public io.casehub.work.api.WorkItemCreateRequest.Builder callerRef(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder candidateGroups(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder candidateScores(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder candidateUsers(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder claimDeadline(java.time.Instant v)`

#### Parameters

- `v` (`java.time.Instant`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder claimDeadlineBusinessHours(java.lang.Integer v)`

#### Parameters

- `v` (`java.lang.Integer`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder confidenceScore(java.lang.Double v)`

#### Parameters

- `v` (`java.lang.Double`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder createdBy(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder description(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder excludedUsers(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder expiresAt(java.time.Instant v)`

#### Parameters

- `v` (`java.time.Instant`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder expiresAtBusinessHours(java.lang.Integer v)`

#### Parameters

- `v` (`java.lang.Integer`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder followUpDate(java.time.Instant v)`

#### Parameters

- `v` (`java.time.Instant`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder formKey(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder inputDataSchema(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder labels(java.util.List<io.casehub.work.api.WorkItemLabelRequest> v)`

#### Parameters

- `v` (`java.util.List<io.casehub.work.api.WorkItemLabelRequest>`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder outputDataSchema(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder payload(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder payloadTypeName(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder permittedOutcomes(java.util.List<io.casehub.work.api.Outcome> v)`

#### Parameters

- `v` (`java.util.List<io.casehub.work.api.Outcome>`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder priority(io.casehub.work.api.WorkItemPriority v)`

#### Parameters

- `v` (`io.casehub.work.api.WorkItemPriority`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder requiredCapabilities(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder resolutionTypeName(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder routingExperiences(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder scope(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder templateId(java.util.UUID v)`

#### Parameters

- `v` (`java.util.UUID`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder templateVersion(java.lang.Long v)`

#### Parameters

- `v` (`java.lang.Long`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder tenancyId(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder title(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.work.api.WorkItemCreateRequest.Builder types(java.util.List<java.lang.String> v)`

#### Parameters

- `v` (`java.util.List<java.lang.String>`)

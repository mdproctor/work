package io.casehub.work.runtime.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.casehub.work.api.DeclineTarget;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;

public record WorkItemResponse(
        UUID id,
        String title,
        String description,
        String category,
        String formKey,
        WorkItemStatus status,
        WorkItemPriority priority,
        String assigneeId,
        String owner,
        String candidateGroups,
        String candidateUsers,
        String requiredCapabilities,
        String createdBy,
        DeclineTarget delegationDeclineTarget,
        String delegationChain,
        WorkItemStatus priorStatus,
        String payload,
        String resolution,
        Instant claimDeadline,
        Instant expiresAt,
        Instant followUpDate,
        Instant createdAt,
        Instant updatedAt,
        Instant assignedAt,
        Instant startedAt,
        Instant completedAt,
        Instant suspendedAt,
        List<WorkItemLabelResponse> labels,
        /**
         * Confidence score from the AI agent that created this WorkItem (0.0–1.0).
         * Null when created by a human or when no confidence metadata was provided.
         */
        Double confidenceScore,
        /**
         * Opaque caller-supplied routing key set at spawn time.
         * Null for WorkItems not created via spawn.
         */
        String callerRef,
        /**
         * JPA optimistic locking version. Included in the response so clients can detect
         * concurrent modifications — if the version you received differs from what another
         * client received, a modification occurred between your reads.
         */
        Long version,
        /**
         * UUID of the WorkItemTemplate this item was instantiated from.
         * Null for items created directly. Use GET /workitem-templates/{templateId}
         * to resolve outcome display names.
         */
        UUID templateId,
        /**
         * The named outcome recorded at completion. Null until COMPLETED.
         * Validated against permittedOutcomes at completion time.
         */
        String outcome,
        /**
         * Permitted outcome names snapshotted from the template at instantiation.
         * Null means no constraint — any outcome (or none) is accepted.
         */
        List<String> permittedOutcomes,
        /**
         * JSON Schema for payload; snapshotted from template at instantiation.
         * Null if no template or no schema.
         */
        String inputDataSchema,
        /**
         * JSON Schema for resolution; snapshotted from template at instantiation.
         * Null if unconstrained.
         */
        String outputDataSchema,
        /** Comma-separated user IDs excluded from this WorkItem; null = unconstrained. */
        String excludedUsers,
        /** Hierarchical scope path e.g. {@code "casehubio/devtown/pr-review"}; null = root scope. */
        String scope) {
}

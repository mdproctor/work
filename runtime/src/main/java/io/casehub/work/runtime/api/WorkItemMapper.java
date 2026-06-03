package io.casehub.work.runtime.api;

import java.util.List;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.OutcomeCodecs;
import io.casehub.work.runtime.model.WorkItemLabel;

public final class WorkItemMapper {

    private WorkItemMapper() {
    }

    public static WorkItemResponse toResponse(final WorkItem wi) {
        return new WorkItemResponse(
                wi.id, wi.title, wi.description, wi.category, wi.formKey,
                wi.status, wi.priority, wi.assigneeId, wi.owner,
                wi.candidateGroups, wi.candidateUsers, wi.requiredCapabilities,
                wi.createdBy, wi.delegationDeclineTarget, wi.delegationChain,
                wi.priorStatus, wi.payload, wi.resolution,
                wi.claimDeadline, wi.expiresAt, wi.followUpDate,
                wi.createdAt, wi.updatedAt, wi.assignedAt, wi.startedAt,
                wi.completedAt, wi.suspendedAt,
                wi.labels == null ? List.of() : wi.labels.stream().map(WorkItemMapper::toLabelResponse).toList(),
                wi.confidenceScore, wi.callerRef, wi.version,
                wi.templateId, wi.outcome,
                OutcomeCodecs.decodePermittedOutcomes(wi.permittedOutcomes),
                wi.inputDataSchema,
                wi.outputDataSchema,
                wi.excludedUsers,
                wi.scope);
    }

    public static AuditEntryResponse toAuditResponse(final AuditEntry e) {
        return new AuditEntryResponse(e.id, e.event, e.actor, e.detail, e.occurredAt);
    }

    public static WorkItemWithAuditResponse toWithAudit(final WorkItem wi, final List<AuditEntry> trail) {
        final List<AuditEntryResponse> auditResponses = trail.stream()
                .map(WorkItemMapper::toAuditResponse)
                .toList();
        final List<WorkItemLabelResponse> labelResponses = wi.labels == null ? List.of()
                : wi.labels.stream().map(WorkItemMapper::toLabelResponse).toList();
        return new WorkItemWithAuditResponse(
                wi.id, wi.title, wi.description, wi.category, wi.formKey,
                wi.status, wi.priority, wi.assigneeId, wi.owner,
                wi.candidateGroups, wi.candidateUsers, wi.requiredCapabilities,
                wi.createdBy, wi.delegationDeclineTarget, wi.delegationChain,
                wi.priorStatus, wi.payload, wi.resolution,
                wi.claimDeadline, wi.expiresAt, wi.followUpDate,
                wi.createdAt, wi.updatedAt, wi.assignedAt, wi.startedAt,
                wi.completedAt, wi.suspendedAt,
                labelResponses, auditResponses, wi.confidenceScore, wi.callerRef, wi.version,
                wi.templateId, wi.outcome,
                OutcomeCodecs.decodePermittedOutcomes(wi.permittedOutcomes),
                wi.inputDataSchema,
                wi.outputDataSchema,
                wi.excludedUsers,
                wi.scope);
    }

    public static WorkItemCreateRequest toServiceRequest(final CreateWorkItemRequest req) {
        return WorkItemCreateRequest.builder()
                .title(req.title())
                .description(req.description())
                .category(req.category())
                .formKey(req.formKey())
                .priority(req.priority())
                .assigneeId(req.assigneeId())
                .candidateGroups(req.candidateGroups())
                .candidateUsers(req.candidateUsers())
                .requiredCapabilities(req.requiredCapabilities())
                .createdBy(req.createdBy())
                .payload(req.payload())
                .claimDeadline(req.claimDeadline())
                .expiresAt(req.expiresAt())
                .followUpDate(req.followUpDate())
                .labels(req.labels())
                .confidenceScore(req.confidenceScore())
                .callerRef(req.callerRef())
                .claimDeadlineBusinessHours(req.claimDeadlineBusinessHours())
                .expiresAtBusinessHours(req.expiresAtBusinessHours())
                .excludedUsers(req.excludedUsers())
                .scope(req.scope())
                // templateId, permittedOutcomes, inputDataSchema, outputDataSchema intentionally
                // omitted — populated from a WorkItemTemplate at instantiation time, not on direct REST creation
                .build();
    }

    static WorkItemLabelResponse toLabelResponse(final WorkItemLabel label) {
        return new WorkItemLabelResponse(label.path, label.persistence, label.appliedBy);
    }
}

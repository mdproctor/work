package io.casehub.work.runtime.api;

import java.time.Instant;
import java.util.List;

import io.casehub.work.runtime.model.WorkItemLabelRequest;
import io.casehub.work.runtime.model.WorkItemPriority;

public record CreateWorkItemRequest(
        String title,
        String description,
        String category,
        String formKey,
        WorkItemPriority priority,
        String assigneeId,
        String candidateGroups,
        String candidateUsers,
        String requiredCapabilities,
        String createdBy,
        String payload,
        Instant claimDeadline,
        Instant expiresAt,
        Instant followUpDate,
        List<WorkItemLabelRequest> labels,
        Double confidenceScore,
        String callerRef,
        Integer claimDeadlineBusinessHours,
        Integer expiresAtBusinessHours,
        /** Comma-separated user IDs excluded from claiming this WorkItem; null = no exclusion. */
        String excludedUsers,
        /** Hierarchical scope path e.g. {@code "casehubio/devtown/pr-review"}; null = root scope. */
        String scope) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String title;
        private String description;
        private String category;
        private String formKey;
        private WorkItemPriority priority;
        private String assigneeId;
        private String candidateGroups;
        private String candidateUsers;
        private String requiredCapabilities;
        private String createdBy;
        private String payload;
        private Instant claimDeadline;
        private Instant expiresAt;
        private Instant followUpDate;
        private List<WorkItemLabelRequest> labels;
        private Double confidenceScore;
        private String callerRef;
        private Integer claimDeadlineBusinessHours;
        private Integer expiresAtBusinessHours;
        private String excludedUsers;
        private String scope;

        public Builder title(final String v)                          { this.title = v; return this; }
        public Builder description(final String v)                    { this.description = v; return this; }
        public Builder category(final String v)                       { this.category = v; return this; }
        public Builder formKey(final String v)                        { this.formKey = v; return this; }
        public Builder priority(final WorkItemPriority v)             { this.priority = v; return this; }
        public Builder assigneeId(final String v)                     { this.assigneeId = v; return this; }
        public Builder candidateGroups(final String v)                { this.candidateGroups = v; return this; }
        public Builder candidateUsers(final String v)                 { this.candidateUsers = v; return this; }
        public Builder requiredCapabilities(final String v)           { this.requiredCapabilities = v; return this; }
        public Builder createdBy(final String v)                      { this.createdBy = v; return this; }
        public Builder payload(final String v)                        { this.payload = v; return this; }
        public Builder claimDeadline(final Instant v)                 { this.claimDeadline = v; return this; }
        public Builder expiresAt(final Instant v)                     { this.expiresAt = v; return this; }
        public Builder followUpDate(final Instant v)                  { this.followUpDate = v; return this; }
        public Builder labels(final List<WorkItemLabelRequest> v)    { this.labels = v; return this; }
        public Builder confidenceScore(final Double v)                { this.confidenceScore = v; return this; }
        public Builder callerRef(final String v)                      { this.callerRef = v; return this; }
        public Builder claimDeadlineBusinessHours(final Integer v)    { this.claimDeadlineBusinessHours = v; return this; }
        public Builder expiresAtBusinessHours(final Integer v)        { this.expiresAtBusinessHours = v; return this; }
        public Builder excludedUsers(final String v)                  { this.excludedUsers = v; return this; }
        public Builder scope(final String v)                          { this.scope = v; return this; }

        public CreateWorkItemRequest build() {
            return new CreateWorkItemRequest(title, description, category, formKey,
                    priority, assigneeId, candidateGroups, candidateUsers,
                    requiredCapabilities, createdBy, payload, claimDeadline,
                    expiresAt, followUpDate, labels, confidenceScore, callerRef,
                    claimDeadlineBusinessHours, expiresAtBusinessHours, excludedUsers, scope);
        }
    }
}

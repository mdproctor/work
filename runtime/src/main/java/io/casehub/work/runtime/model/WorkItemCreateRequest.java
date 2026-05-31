package io.casehub.work.runtime.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


public final class WorkItemCreateRequest {

    public final String title;
    public final String description;
    public final String category;
    public final String formKey;
    public final WorkItemPriority priority;
    public final String assigneeId;
    public final String candidateGroups;
    public final String candidateUsers;
    public final String requiredCapabilities;
    public final String createdBy;
    public final String payload;
    public final Instant claimDeadline;
    public final Instant expiresAt;
    public final Instant followUpDate;
    public final List<WorkItemLabelRequest> labels;
    public final Double confidenceScore;
    public final String callerRef;
    public final Integer claimDeadlineBusinessHours;
    public final Integer expiresAtBusinessHours;
    public final UUID templateId;
    public final List<String> permittedOutcomes;
    public final String inputDataSchema;
    public final String outputDataSchema;
    public final String excludedUsers;

    /** Hierarchical scope path e.g. {@code "casehubio/devtown/pr-review"}; null means root scope. */
    public final String scope;

    /** Optional detail appended to the CREATED audit entry. Used to record group expansion notes. */
    public final String auditDetail;

    private WorkItemCreateRequest(final Builder b) {
        this.title                      = b.title;
        this.description                = b.description;
        this.category                   = b.category;
        this.formKey                    = b.formKey;
        this.priority                   = b.priority;
        this.assigneeId                 = b.assigneeId;
        this.candidateGroups            = b.candidateGroups;
        this.candidateUsers             = b.candidateUsers;
        this.requiredCapabilities       = b.requiredCapabilities;
        this.createdBy                  = b.createdBy;
        this.payload                    = b.payload;
        this.claimDeadline              = b.claimDeadline;
        this.expiresAt                  = b.expiresAt;
        this.followUpDate               = b.followUpDate;
        this.labels                     = b.labels;
        this.confidenceScore            = b.confidenceScore;
        this.callerRef                  = b.callerRef;
        this.claimDeadlineBusinessHours = b.claimDeadlineBusinessHours;
        this.expiresAtBusinessHours     = b.expiresAtBusinessHours;
        this.templateId                 = b.templateId;
        this.permittedOutcomes          = b.permittedOutcomes;
        this.inputDataSchema            = b.inputDataSchema;
        this.outputDataSchema           = b.outputDataSchema;
        this.excludedUsers              = b.excludedUsers;
        this.scope                      = b.scope;
        this.auditDetail                = b.auditDetail;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkItemCreateRequest r)) return false;
        return Objects.equals(title, r.title)
                && Objects.equals(description, r.description)
                && Objects.equals(category, r.category)
                && Objects.equals(formKey, r.formKey)
                && Objects.equals(priority, r.priority)
                && Objects.equals(assigneeId, r.assigneeId)
                && Objects.equals(candidateGroups, r.candidateGroups)
                && Objects.equals(candidateUsers, r.candidateUsers)
                && Objects.equals(requiredCapabilities, r.requiredCapabilities)
                && Objects.equals(createdBy, r.createdBy)
                && Objects.equals(payload, r.payload)
                && Objects.equals(claimDeadline, r.claimDeadline)
                && Objects.equals(expiresAt, r.expiresAt)
                && Objects.equals(followUpDate, r.followUpDate)
                && Objects.equals(labels, r.labels)
                && Objects.equals(confidenceScore, r.confidenceScore)
                && Objects.equals(callerRef, r.callerRef)
                && Objects.equals(claimDeadlineBusinessHours, r.claimDeadlineBusinessHours)
                && Objects.equals(expiresAtBusinessHours, r.expiresAtBusinessHours)
                && Objects.equals(templateId, r.templateId)
                && Objects.equals(permittedOutcomes, r.permittedOutcomes)
                && Objects.equals(inputDataSchema, r.inputDataSchema)
                && Objects.equals(outputDataSchema, r.outputDataSchema)
                && Objects.equals(excludedUsers, r.excludedUsers)
                && Objects.equals(scope, r.scope)
                && Objects.equals(auditDetail, r.auditDetail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, description, category, formKey, priority,
                assigneeId, candidateGroups, candidateUsers, requiredCapabilities,
                createdBy, payload, claimDeadline, expiresAt, followUpDate, labels,
                confidenceScore, callerRef, claimDeadlineBusinessHours, expiresAtBusinessHours,
                templateId, permittedOutcomes, inputDataSchema, outputDataSchema, excludedUsers, scope);
    }

    /** Intentionally omits payload, schemas, callerRef, and credentials — log-safety. */
    @Override
    public String toString() {
        return "WorkItemCreateRequest{title='" + title + "', category='" + category
                + "', priority=" + priority + ", assigneeId='" + assigneeId
                + "', candidateGroups='" + candidateGroups + "'}";
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
        private UUID templateId;
        private List<String> permittedOutcomes;
        private String inputDataSchema;
        private String outputDataSchema;
        private String excludedUsers;
        private String scope;
        private String auditDetail;

        private Builder() {}

        private Builder(final WorkItemCreateRequest src) {
            this.title                      = src.title;
            this.description                = src.description;
            this.category                   = src.category;
            this.formKey                    = src.formKey;
            this.priority                   = src.priority;
            this.assigneeId                 = src.assigneeId;
            this.candidateGroups            = src.candidateGroups;
            this.candidateUsers             = src.candidateUsers;
            this.requiredCapabilities       = src.requiredCapabilities;
            this.createdBy                  = src.createdBy;
            this.payload                    = src.payload;
            this.claimDeadline              = src.claimDeadline;
            this.expiresAt                  = src.expiresAt;
            this.followUpDate               = src.followUpDate;
            this.labels                     = src.labels;
            this.confidenceScore            = src.confidenceScore;
            this.callerRef                  = src.callerRef;
            this.claimDeadlineBusinessHours = src.claimDeadlineBusinessHours;
            this.expiresAtBusinessHours     = src.expiresAtBusinessHours;
            this.templateId                 = src.templateId;
            this.permittedOutcomes          = src.permittedOutcomes;
            this.inputDataSchema            = src.inputDataSchema;
            this.outputDataSchema           = src.outputDataSchema;
            this.excludedUsers              = src.excludedUsers;
            this.scope                      = src.scope;
            this.auditDetail                = src.auditDetail;
        }

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
        public Builder templateId(final UUID v)                       { this.templateId = v; return this; }
        public Builder permittedOutcomes(final List<String> v)        { this.permittedOutcomes = v; return this; }
        public Builder inputDataSchema(final String v)                { this.inputDataSchema = v; return this; }
        public Builder outputDataSchema(final String v)               { this.outputDataSchema = v; return this; }
        public Builder excludedUsers(final String v)                  { this.excludedUsers = v; return this; }
        public Builder scope(final String v)                          { this.scope = v; return this; }
        public Builder auditDetail(final String v)                    { this.auditDetail = v; return this; }

        public WorkItemCreateRequest build() {
            if (title == null || title.isBlank())
                throw new IllegalArgumentException("title is required");
            return new WorkItemCreateRequest(this);
        }
    }
}

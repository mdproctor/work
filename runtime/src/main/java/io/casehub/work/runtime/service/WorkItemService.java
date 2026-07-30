package io.casehub.work.runtime.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.platform.api.routing.StrategyResolver;
import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.spi.BusinessCalendar;
import io.casehub.work.api.ClaimSlaContext;
import io.casehub.work.api.spi.ClaimSlaPolicy;
import io.casehub.work.api.DeclineTarget;
import io.casehub.work.api.spi.ExclusionPolicy;
import io.casehub.work.api.PolicyDecision;
import io.casehub.work.core.strategy.CapabilityValidator;
import io.casehub.work.runtime.config.WorkItemsConfig;
import io.casehub.work.runtime.event.WorkItemLifecycleEmitter;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.api.LabelPersistence;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;
import io.casehub.work.runtime.repository.WorkItemStore;

@ApplicationScoped
public class WorkItemService {

    private final WorkItemStore workItemStore;
    private final AuditEntryStore auditStore;
    private final WorkItemsConfig config;
    private final WorkItemAssignmentService assignmentService;
    private final ClaimSlaPolicy claimSlaPolicy;
    private final ExclusionPolicy exclusionPolicy;
    private final BlockedAttemptAuditService blockedAuditService;
    private final CapabilityValidator capabilityValidator;
    private final WorkItemTimerService timerService;
    // claimSlaPolicy is resolved at construction time via StrategyResolver

    @Inject
    WorkItemSpawnGroupStore spawnGroupStore;

    @Inject
    EntityManager em;

    @Inject
    FormSchemaValidationService schemaValidator;

    @Inject
    OutcomeValidator outcomeValidator;

    @Inject
    WorkItemLifecycleEmitter lifecycleEmitter;

    @Inject
    jakarta.enterprise.inject.Instance<BusinessCalendar> businessCalendar;

    @Inject
    PreferenceProvider preferenceProvider;

    @Inject
    public WorkItemService(final WorkItemStore workItemStore,
            final AuditEntryStore auditStore,
            final WorkItemsConfig config,
            final WorkItemAssignmentService assignmentService,
            final StrategyResolver strategyResolver,
            final ExclusionPolicy exclusionPolicy,
            final BlockedAttemptAuditService blockedAuditService,
            final CapabilityValidator capabilityValidator,
            final WorkItemTimerService timerService) {
        this.workItemStore = workItemStore;
        this.auditStore = auditStore;
        this.config = config;
        this.assignmentService = assignmentService;
        this.claimSlaPolicy = strategyResolver.resolve(ClaimSlaPolicy.class, config.sla().claimPolicy());
        this.exclusionPolicy = exclusionPolicy;
        this.blockedAuditService = blockedAuditService;
        this.capabilityValidator = capabilityValidator;
        this.timerService = timerService;
    }

    @Transactional
    public WorkItem create(final WorkItemCreateRequest request) {
        capabilityValidator.validate(CapabilityParser.parse(request.requiredCapabilities));
        final WorkItem item = new WorkItem();
        item.status = WorkItemStatus.PENDING;
        item.title = request.title;
        item.description = request.description;
        if (request.types != null) {
            for (final String typePath : request.types) {
                Path.parse(typePath);
                item.types.add(new io.casehub.work.runtime.model.WorkItemType(typePath));
            }
        }
        item.formKey = request.formKey;
        item.priority = request.priority != null ? request.priority : WorkItemPriority.MEDIUM;
        item.assigneeId = request.assigneeId;
        item.candidateGroups = request.candidateGroups;
        item.candidateUsers = request.candidateUsers;
        item.requiredCapabilities = request.requiredCapabilities;
        item.createdBy = request.createdBy;
        item.payload = request.payload;
        item.confidenceScore = request.confidenceScore;
        item.callerRef = request.callerRef;
        item.followUpDate = request.followUpDate;
        item.templateId = request.templateId;
        item.templateVersion = request.templateVersion;
        item.permittedOutcomes = WorkItemTemplateService.encodeOutcomes(request.permittedOutcomes);
        item.inputDataSchema = request.inputDataSchema;
        item.outputDataSchema = request.outputDataSchema;
        item.excludedUsers = request.excludedUsers;
        item.scope = request.scope;
        item.tenancyId = request.tenancyId;
        item.payloadTypeName = request.payloadTypeName;
        item.resolutionTypeName = request.resolutionTypeName;
        item.candidateScores = request.candidateScores;
        item.routingExperiences = request.routingExperiences;

        final Instant now = Instant.now();
        item.createdAt = now;
        item.updatedAt = now;

        // expiresAt: absolute > business hours > config default (wall-clock)
        if (request.expiresAt != null) {
            item.expiresAt = request.expiresAt;
        } else if (request.expiresAtBusinessHours != null) {
            item.expiresAt = resolveBusinessHours(now, request.expiresAtBusinessHours);
        } else {
            item.expiresAt = now.plus(config.defaultExpiryHours(), ChronoUnit.HOURS);
        }

        // claimDeadline: absolute > business hours > config default (wall-clock)
        if (request.claimDeadline != null) {
            item.claimDeadline = request.claimDeadline;
        } else if (request.claimDeadlineBusinessHours != null) {
            item.claimDeadline = resolveBusinessHours(now, request.claimDeadlineBusinessHours);
        } else if (config.defaultClaimHours() > 0) {
            item.claimDeadline = now.plus(config.defaultClaimHours(), ChronoUnit.HOURS);
        }

        // Claim SLA tracking — item enters pool at creation time
        item.accumulatedUnclaimedSeconds = 0L;
        item.lastReturnedToPoolAt = now;

        // Labels: only MANUAL labels accepted at creation time
        if (request.labels != null) {
            for (var labelReq : request.labels) {
                if (labelReq.persistence() == LabelPersistence.INFERRED) {
                    throw new IllegalArgumentException(
                            "INFERRED labels cannot be submitted at creation time — they are managed by the filter engine");
                }
                item.labels.add(new WorkItemLabel(labelReq.path(), labelReq.persistence(), labelReq.appliedBy()));
            }
        }

        if (item.inputDataSchema != null) {
            final List<String> violations = schemaValidator.validate(item.inputDataSchema, item.payload);
            if (!violations.isEmpty()) {
                throw new IllegalArgumentException("payload violates inputDataSchema: " + violations);
            }
        }

        // Pre-generate ID so CREATE_DENIED audit can reference it even if the WorkItem is never persisted.
        // @PrePersist guards with if (id == null) so this is safe.
        item.id = UUID.randomUUID();

        if (request.assigneeId != null) {
            final PolicyDecision createDecision = exclusionPolicy.check(request.assigneeId, item.excludedUsers);
            if (createDecision.denied()) {
                blockedAuditService.record(item.id, "CREATE_DENIED", request.createdBy, createDecision.reason());
                throw new IllegalArgumentException(createDecision.reason());
            }
        }
        assignmentService.assign(item, AssignmentTrigger.CREATED);
        final WorkItem saved = workItemStore.put(item);
        if (saved.expiresAt != null) {
            timerService.scheduleExpiry(saved.id, saved.tenancyId, saved.expiresAt);
        }
        if (saved.claimDeadline != null) {
            timerService.scheduleClaimDeadline(saved.id, saved.tenancyId, saved.claimDeadline);
        }
        audit(saved.id, "CREATED", request.createdBy, request.auditDetail);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("CREATED", saved, request.createdBy, null));
        return saved;
    }

    @Transactional
    public WorkItem claim(final UUID id, final String claimantId) {
        if (claimantId == null || claimantId.isBlank()) {
            throw new IllegalArgumentException("claimantId is required");
        }
        final WorkItem item = requireWorkItem(id);
        // Multi-instance claim guard — read allowSameAssignee then detach: the spawn group
        // has @Version and would otherwise participate in persistAndFlush(), racing with
        // the async MultiInstanceCoordinator updating the same version column.
        if (item.parentId != null) {
            final WorkItemSpawnGroup group = spawnGroupStore.findMultiInstanceByParentId(item.parentId).orElse(null);
            if (group != null) {
                em.detach(group);
                if (!group.allowSameAssignee) {
                    final long alreadyHeld = workItemStore.countByParentAndAssignee(item.parentId, claimantId, id);
                    if (alreadyHeld > 0) {
                        throw new IllegalStateException(
                                "Claimant '" + claimantId + "' already hold another instance in this group");
                    }
                }
            }
        }
        if (item.status != WorkItemStatus.PENDING) {
            throw new IllegalStateException("Cannot claim WorkItem in status: " + item.status);
        }
        final PolicyDecision claimDecision = exclusionPolicy.check(claimantId, item.excludedUsers);
        if (claimDecision.denied()) {
            blockedAuditService.record(item.id, "CLAIM_DENIED", claimantId, claimDecision.reason());
            throw new IllegalStateException(claimDecision.reason());
        }
        final Instant now = Instant.now();
        // Accumulate time spent in the unclaimed pool for this phase
        if (item.lastReturnedToPoolAt != null) {
            item.accumulatedUnclaimedSeconds += Duration.between(item.lastReturnedToPoolAt, now).toSeconds();
            item.lastReturnedToPoolAt = null;
        }
        item.status = WorkItemStatus.ASSIGNED;
        item.assigneeId = claimantId;
        item.assignedAt = now;
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "ASSIGNED", claimantId, null);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("ASSIGNED", saved, claimantId, null));
        return saved;
    }

    @Transactional
    public WorkItem start(final UUID id, final String actorId) {
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.ASSIGNED) {
            throw new IllegalStateException("Cannot start WorkItem in status: " + item.status);
        }
        item.status = WorkItemStatus.IN_PROGRESS;
        item.startedAt = Instant.now();
        final WorkItem saved = workItemStore.put(item);
        audit(saved.id, "STARTED", actorId, null);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("STARTED", saved, actorId, null));
        return saved;
    }

    /**
     * Complete a WorkItem from a system actor, accepting any non-terminal status.
     * Used by the multi-instance coordinator when group policy triggers parent completion.
     * Schema validation (inputDataSchema/outputDataSchema) is intentionally bypassed for system completions.
     */
    @Transactional
    public WorkItem completeFromSystem(final UUID id, final String actorId, final String resolution) {
        final WorkItem item = requireWorkItem(id);
        if (item.status.isTerminal())
            return item;
        item.status = WorkItemStatus.COMPLETED;
        item.completedAt = Instant.now();
        item.resolution = resolution;
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "COMPLETED", actorId, null);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("COMPLETED", saved, actorId, resolution));
        return saved;
    }

    /**
     * Reject a WorkItem from a system actor, accepting any non-terminal status.
     * Used by the multi-instance coordinator when group policy triggers parent rejection.
     */
    @Transactional
    public WorkItem rejectFromSystem(final UUID id, final String actorId, final String reason) {
        final WorkItem item = requireWorkItem(id);
        if (item.status.isTerminal())
            return item;
        item.status = WorkItemStatus.REJECTED;
        item.completedAt = Instant.now();
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "REJECTED", actorId, reason);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("REJECTED", saved, actorId, reason));
        return saved;
    }

    @Transactional
    public WorkItem complete(final UUID id, final String actorId, final String resolution,
            final String outcome) {
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot complete WorkItem in status: " + item.status);
        }
        outcomeValidator.validate(item, outcome, resolution, null, actorId);
        if (item.outputDataSchema != null) {
            final List<String> violations = schemaValidator.validate(item.outputDataSchema, resolution);
            if (!violations.isEmpty()) {
                throw new IllegalArgumentException("resolution violates outputDataSchema: " + violations);
            }
        }
        item.status = WorkItemStatus.COMPLETED;
        item.completedAt = Instant.now();
        item.resolution = resolution;
        item.outcome = outcome;
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "COMPLETED", actorId, null);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("COMPLETED", saved, actorId, resolution));
        return saved;
    }

    @Transactional
    public WorkItem reject(final UUID id, final String actorId, final String reason, final String outcome) {
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.ASSIGNED && item.status != WorkItemStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot reject WorkItem in status: " + item.status);
        }
        outcomeValidator.validate(item, outcome, null, reason, actorId);
        item.status = WorkItemStatus.REJECTED;
        item.completedAt = Instant.now();
        item.outcome = outcome;
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "REJECTED", actorId, reason);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("REJECTED", saved, actorId, reason));
        return saved;
    }

    /**
     * Complete a WorkItem with an explicit rationale and policy reference for ledger capture.
     *
     * @param id the WorkItem UUID
     * @param actorId who completed it
     * @param resolution the resolution payload
     * @param outcome the named outcome (validated against permittedOutcomes if set)
     * @param rationale the actor's stated basis for the decision (GDPR Art. 22 compliance)
     * @param planRef the policy/procedure version that governed this decision
     */
    @Transactional
    public WorkItem complete(final UUID id, final String actorId, final String resolution,
            final String outcome, final String rationale, final String planRef) {
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot complete WorkItem in status: " + item.status);
        }
        outcomeValidator.validate(item, outcome, resolution, null, actorId);
        if (item.outputDataSchema != null) {
            final List<String> violations = schemaValidator.validate(item.outputDataSchema, resolution);
            if (!violations.isEmpty()) {
                throw new IllegalArgumentException("resolution violates outputDataSchema: " + violations);
            }
        }
        item.status = WorkItemStatus.COMPLETED;
        item.completedAt = Instant.now();
        item.resolution = resolution;
        item.outcome = outcome;
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "COMPLETED", actorId, null);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of(
                "COMPLETED", saved, actorId, resolution, rationale, planRef));
        return saved;
    }

    /**
     * Reject a WorkItem with an explicit rationale and named outcome for ledger capture.
     *
     * @param id the WorkItem UUID
     * @param actorId who rejected it
     * @param reason the rejection reason (stored as event detail)
     * @param rationale the actor's formal stated basis (stored as ledger rationale)
     * @param outcome the named outcome (validated against permittedOutcomes if set)
     */
    @Transactional
    public WorkItem reject(final UUID id, final String actorId, final String reason,
            final String outcome, final String rationale) {
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.ASSIGNED && item.status != WorkItemStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot reject WorkItem in status: " + item.status);
        }
        outcomeValidator.validate(item, outcome, null, reason, actorId);
        item.status = WorkItemStatus.REJECTED;
        item.completedAt = Instant.now();
        item.outcome = outcome;
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "REJECTED", actorId, reason);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of(
                "REJECTED", saved, actorId, reason, rationale, null));
        return saved;
    }

    @Transactional
    public WorkItem delegate(final UUID id, final String actorId, final String toAssigneeId,
            final DeclineTarget declineTarget) {
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.ASSIGNED && item.status != WorkItemStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot delegate WorkItem in status: " + item.status);
        }
        final PolicyDecision delegateDecision = exclusionPolicy.check(toAssigneeId, item.excludedUsers);
        if (delegateDecision.denied()) {
            blockedAuditService.record(item.id, "DELEGATE_DENIED", actorId,
                    "target:" + toAssigneeId + "; reason:" + delegateDecision.reason());
            throw new IllegalArgumentException(delegateDecision.reason());
        }
        if (item.owner == null) {
            item.owner = actorId;
        }
        item.delegationChain = item.delegationChain == null
                ? actorId
                : item.delegationChain + "," + actorId;
        // Fire strategy while item is still in its current state so countActive
        // sees the correct load before reassignment.
        assignmentService.assign(item, AssignmentTrigger.DELEGATED);
        // If strategy did not select a candidate, fall back to explicit 'to' param.
        if (item.assigneeId == null || item.assigneeId.equals(actorId)) {
            item.assigneeId = toAssigneeId;
        }
        // DELEGATED unconditionally — overrides any ASSIGNED set by strategy.
        // DELEGATED is directly addressed; pool SLA tracking does not apply.
        item.status = WorkItemStatus.DELEGATED;
        item.claimDeadline = null;
        item.lastReturnedToPoolAt = null;
        item.delegationDeclineTarget = declineTarget;
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "DELEGATED", actorId, "to:" + saved.assigneeId);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("DELEGATED", saved, actorId, "to:" + toAssigneeId));
        return saved;
    }

    @Transactional
    public WorkItem acceptDelegation(final UUID id, final String claimantId) {
        if (claimantId == null || claimantId.isBlank()) {
            throw new IllegalArgumentException("claimantId is required");
        }
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.DELEGATED) {
            throw new IllegalStateException(
                    "Cannot accept delegation for WorkItem in status: " + item.status);
        }
        if (!claimantId.equals(item.assigneeId)) {
            throw new IllegalStateException(
                    "Actor '" + claimantId + "' is not the designated delegatee for WorkItem " + id);
        }
        item.status = WorkItemStatus.ASSIGNED;
        item.assignedAt = Instant.now();
        item.delegationDeclineTarget = null;
        final WorkItem saved = workItemStore.put(item);
        audit(saved.id, "DELEGATION_ACCEPTED", claimantId, null);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("DELEGATION_ACCEPTED", saved, claimantId, null));
        return saved;
    }

    @Transactional
    public WorkItem declineDelegation(final UUID id, final String actorId) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.DELEGATED) {
            throw new IllegalStateException(
                    "Cannot decline delegation for WorkItem in status: " + item.status);
        }
        if (!actorId.equals(item.assigneeId)) {
            throw new IllegalStateException(
                    "Actor '" + actorId + "' is not the designated delegatee for WorkItem " + id);
        }
        final DeclineTarget target = resolveDeclineTarget(item);
        item.delegationDeclineTarget = null;

        if (target == DeclineTarget.DELEGATOR && item.delegationChain != null) {
            final String[] chain = item.delegationChain.split(",");
            final String prevActor = chain[chain.length - 1].trim();
            // Restore to previous actor — no exclusion check: prevActor was a verified holder.
            item.assigneeId = prevActor;
            item.status = WorkItemStatus.ASSIGNED;
            item.assignedAt = Instant.now();
        } else {
            // POOL path
            item.assigneeId = null;
            item.status = WorkItemStatus.PENDING;
            final Instant now = Instant.now();
            item.lastReturnedToPoolAt = now;
            item.claimDeadline = claimSlaPolicy.computePoolDeadline(buildClaimSlaContext(item, now));
            assignmentService.assign(item, AssignmentTrigger.DELEGATION_DECLINED);
        }

        final WorkItem saved = workItemStore.put(item);
        if (saved.claimDeadline != null) {
            timerService.scheduleClaimDeadline(saved.id, saved.tenancyId, saved.claimDeadline);
        }
        audit(saved.id, "DELEGATION_DECLINED", actorId, null);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("DELEGATION_DECLINED", saved, actorId, null));
        return saved;
    }

    private DeclineTarget resolveDeclineTarget(final WorkItem item) {
        if (item.delegationDeclineTarget != null) {
            return item.delegationDeclineTarget;
        }
        final Path scopePath = item.scope != null ? Path.parse(item.scope) : Path.root();
        final Preferences prefs = preferenceProvider.resolve(new SettingsScope(item.tenancyId, scopePath, Instant.now()));
        return prefs.getOrDefault(DeclineTarget.KEY);
    }

    public Optional<WorkItem> findById(final UUID id) {
        return workItemStore.get(id);
    }

    /**
     * Scan WorkItems matching the given query criteria.
     *
     * <p>
     * Tenant isolation is enforced by the store: if {@link WorkItemQuery#tenancyId()}
     * is set, it is used as the filter; otherwise the current principal's tenant applies.
     *
     * @param query the query criteria; must not be {@code null}
     * @return list of matching work items; may be empty, never null
     */
    public List<WorkItem> scan(final WorkItemQuery query) {
        return workItemStore.scan(query);
    }

    @Transactional
    public WorkItem release(final UUID id, final String actorId) {
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.ASSIGNED) {
            throw new IllegalStateException("Cannot release WorkItem in status: " + item.status);
        }
        final Instant now = Instant.now();
        item.status = WorkItemStatus.PENDING;
        item.assigneeId = null;
        item.lastReturnedToPoolAt = now;
        item.claimDeadline = claimSlaPolicy.computePoolDeadline(buildClaimSlaContext(item, now));
        assignmentService.assign(item, AssignmentTrigger.RELEASED);
        final WorkItem saved = workItemStore.put(item);
        if (saved.claimDeadline != null) {
            timerService.scheduleClaimDeadline(saved.id, saved.tenancyId, saved.claimDeadline);
        }
        audit(saved.id, "RELEASED", actorId, null);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("RELEASED", saved, actorId, null));
        return saved;
    }

    @Transactional
    public WorkItem suspend(final UUID id, final String actorId, final String reason) {
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.ASSIGNED && item.status != WorkItemStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot suspend WorkItem in status: " + item.status);
        }
        item.priorStatus = item.status;
        item.status = WorkItemStatus.SUSPENDED;
        item.suspendedAt = Instant.now();
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "SUSPENDED", actorId, reason);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("SUSPENDED", saved, actorId, reason));
        return saved;
    }

    @Transactional
    public WorkItem resume(final UUID id, final String actorId) {
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.SUSPENDED) {
            throw new IllegalStateException("Cannot resume WorkItem in status: " + item.status);
        }
        item.status = item.priorStatus;
        item.priorStatus = null;
        item.suspendedAt = null;
        final WorkItem saved = workItemStore.put(item);
        if (saved.expiresAt != null) {
            timerService.scheduleExpiry(saved.id, saved.tenancyId, saved.expiresAt);
        }
        if (saved.claimDeadline != null) {
            timerService.scheduleClaimDeadline(saved.id, saved.tenancyId, saved.claimDeadline);
        }
        audit(saved.id, "RESUMED", actorId, null);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("RESUMED", saved, actorId, null));
        return saved;
    }

    @Transactional
    public WorkItem cancel(final UUID id, final String actorId, final String reason) {
        final WorkItem item = requireWorkItem(id);
        if (item.status.isTerminal()) {
            throw new IllegalStateException("Cannot cancel WorkItem in status: " + item.status);
        }
        item.status = WorkItemStatus.CANCELLED;
        item.completedAt = Instant.now();
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "CANCELLED", actorId, reason);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("CANCELLED", saved, actorId, reason));
        return saved;
    }

    @Transactional
    public WorkItem fault(final UUID id, final String systemActorId, final String errorDetail) {
        final WorkItem item = requireWorkItem(id);
        if (item.status.isTerminal()) {
            throw new IllegalStateException("Cannot fault WorkItem in status: " + item.status);
        }
        item.status = WorkItemStatus.FAULTED;
        item.completedAt = Instant.now();
        item.resolution = errorDetail;
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "FAULTED", systemActorId, errorDetail);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("FAULTED", saved, systemActorId, errorDetail));
        return saved;
    }

    @Transactional
    public WorkItem faultFromSystem(final UUID id, final String actorId, final String errorDetail) {
        final WorkItem item = requireWorkItem(id);
        if (item.status.isTerminal())
            return item;
        item.status = WorkItemStatus.FAULTED;
        item.completedAt = Instant.now();
        item.resolution = errorDetail;
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "FAULTED", actorId, errorDetail);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("FAULTED", saved, actorId, errorDetail));
        return saved;
    }

    @Transactional
    public WorkItem obsolete(final UUID id, final String triggeredBy, final String reason) {
        final WorkItem item = requireWorkItem(id);
        if (item.status.isTerminal()) {
            throw new IllegalStateException("Cannot obsolete WorkItem in status: " + item.status);
        }
        item.status = WorkItemStatus.OBSOLETE;
        item.completedAt = Instant.now();
        item.resolution = reason;
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "OBSOLETE", triggeredBy, reason);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("OBSOLETE", saved, triggeredBy, reason));
        return saved;
    }

    @Transactional
    public WorkItem obsoleteFromSystem(final UUID id, final String triggeredBy, final String reason) {
        final WorkItem item = requireWorkItem(id);
        if (item.status.isTerminal())
            return item;
        item.status = WorkItemStatus.OBSOLETE;
        item.completedAt = Instant.now();
        item.resolution = reason;
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "OBSOLETE", triggeredBy, reason);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("OBSOLETE", saved, triggeredBy, reason));
        return saved;
    }

    @Transactional
    public WorkItem escalate(final UUID id, final String actor,
                             final String targetGroup, final String reason) {
        final WorkItem item = requireWorkItem(id);
        if (item.status.isTerminal()) {
            throw new IllegalStateException("Cannot escalate WorkItem in status: " + item.status);
        }
        item.candidateGroups = targetGroup;
        item.assigneeId = null;
        item.status = WorkItemStatus.PENDING;
        final Instant now = Instant.now();
        item.lastReturnedToPoolAt = now;
        item.claimDeadline = claimSlaPolicy.computePoolDeadline(buildClaimSlaContext(item, now));
        assignmentService.assign(item, AssignmentTrigger.SLA_ESCALATED);
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelClaimDeadline(saved.id);
        if (saved.claimDeadline != null) {
            timerService.scheduleClaimDeadline(saved.id, saved.tenancyId, saved.claimDeadline);
        }
        if (saved.expiresAt != null) {
            timerService.rescheduleExpiry(saved.id, saved.expiresAt);
        }
        audit(saved.id, "MANUALLY_ESCALATED", actor, reason);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("MANUALLY_ESCALATED", saved, actor, reason));
        return saved;
    }

    @Transactional
    public WorkItem cancelFromSystem(final UUID id, final String actorId, final String reason) {
        final WorkItem item = requireWorkItem(id);
        if (item.status.isTerminal())
            return item;
        item.status = WorkItemStatus.CANCELLED;
        item.completedAt = Instant.now();
        final WorkItem saved = workItemStore.put(item);
        timerService.cancelExpiry(saved.id);
        timerService.cancelClaimDeadline(saved.id);
        audit(saved.id, "CANCELLED", actorId, reason);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("CANCELLED", saved, actorId, reason));
        return saved;
    }

    @Transactional
    public WorkItem progress(final UUID id, final String actorId, final Integer percentComplete,
            final String statusNote) {
        final WorkItem item = requireWorkItem(id);
        if (item.status != WorkItemStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot report progress for WorkItem in status: " + item.status);
        }
        item.percentComplete = percentComplete;
        item.statusNote = statusNote;
        item.updatedAt = Instant.now();
        final WorkItem saved = workItemStore.put(item);
        audit(saved.id, "PROGRESS_UPDATE", actorId, statusNote);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("PROGRESS_UPDATE", saved, actorId, statusNote));
        return saved;
    }

    @Transactional
    public WorkItem extend(final UUID id, final Instant newExpiresAt, final String actorId) {
        if (newExpiresAt == null) {
            throw new IllegalArgumentException("newExpiresAt is required");
        }
        final WorkItem item = requireWorkItem(id);
        if (item.status.isTerminal()) {
            throw new IllegalStateException("Cannot extend WorkItem in status: " + item.status);
        }
        if (item.expiresAt != null && !newExpiresAt.isAfter(item.expiresAt)) {
            throw new IllegalArgumentException(
                    "newExpiresAt must be after current expiresAt (" + item.expiresAt + ")");
        }
        item.expiresAt = newExpiresAt;
        item.updatedAt = Instant.now();
        final WorkItem saved = workItemStore.put(item);
        timerService.rescheduleExpiry(saved.id, newExpiresAt);
        audit(saved.id, "DEADLINE_EXTENDED", actorId, null);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("DEADLINE_EXTENDED", saved, actorId, null));
        return saved;
    }

    @Transactional
    public WorkItem addLabel(final UUID workItemId, final String path, final String appliedBy) {
        final WorkItem item = workItemStore.get(workItemId)
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));
        item.labels.add(new WorkItemLabel(path, LabelPersistence.MANUAL, appliedBy));
        final WorkItem saved = workItemStore.put(item);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("LABEL_ADDED", saved, appliedBy, null));
        return saved;
    }

    @Transactional
    public WorkItem removeLabel(final UUID workItemId, final String path) {
        final WorkItem item = workItemStore.get(workItemId)
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));
        final boolean removed = item.labels.removeIf(
                l -> l.path.equals(path) && l.persistence == LabelPersistence.MANUAL);
        if (!removed) {
            throw new LabelNotFoundException(workItemId, path);
        }
        final WorkItem saved = workItemStore.put(item);
        lifecycleEmitter.emit(WorkItemLifecycleEvent.of("LABEL_REMOVED", saved, "system", path));
        return saved;
    }

    /**
     * Clone a WorkItem — creates a new PENDING WorkItem copying operational fields from the source.
     *
     * <p>
     * <strong>Copied:</strong> title (optionally overridden), description, types, formKey, priority,
     * candidateGroups, candidateUsers, requiredCapabilities, payload, MANUAL labels.
     *
     * <p>
     * <strong>Not copied:</strong> id, status (always PENDING), assigneeId, owner, delegationState,
     * delegationChain, priorStatus, resolution, all timestamps, INFERRED labels (the filter engine
     * re-applies them on the first lifecycle event).
     *
     * @param sourceId the WorkItem to clone
     * @param titleOverride if non-null and non-blank, used as the clone's title; otherwise appends " (copy)"
     * @param createdBy the actor creating the clone
     * @return the newly created PENDING WorkItem
     * @throws WorkItemNotFoundException if the source WorkItem does not exist
     */
    @Transactional
    public WorkItem clone(final UUID sourceId, final String titleOverride, final String createdBy) {
        final WorkItem source = workItemStore.get(sourceId)
                .orElseThrow(() -> new WorkItemNotFoundException(sourceId));

        final String title = (titleOverride != null && !titleOverride.isBlank())
                ? titleOverride
                : source.title + " (copy)";

        final java.util.List<WorkItemLabel> manualLabels = source.labels == null
                ? java.util.List.of()
                : source.labels.stream()
                        .filter(l -> l.persistence == LabelPersistence.MANUAL)
                        .toList();

        final WorkItemCreateRequest req = WorkItemCreateRequest.builder()
                .title(title)
                .description(source.description)
                .types(source.types.stream().map(t -> t.path).toList())
                .formKey(source.formKey)
                .priority(source.priority)
                .candidateGroups(source.candidateGroups)
                .candidateUsers(source.candidateUsers)
                .requiredCapabilities(source.requiredCapabilities)
                .createdBy(createdBy)
                .payload(source.payload)
                .excludedUsers(source.excludedUsers)
                .build();

        WorkItem clone = create(req);

        for (final WorkItemLabel label : manualLabels) {
            clone = addLabel(clone.id, label.path, label.appliedBy);
        }

        return clone;
    }

    /**
     * Finds a WorkItem by its callerRef.
     *
     * @param callerRef the callerRef to match
     * @return an Optional containing the WorkItem if found
     */
    public Optional<WorkItem> findByCallerRef(final String callerRef) {
        return workItemStore.findByCallerRef(callerRef);
    }

    /**
     * Find an active WorkItem by caller reference, delegating to the store.
     *
     * @param callerRef the caller reference to look up; must not be {@code null}
     * @return an {@link Optional} containing the matching active WorkItem, or empty if not found
     */
    public Optional<WorkItem> findActiveByCallerRef(final String callerRef) {
        return workItemStore.findActiveByCallerRef(callerRef);
    }

    private ClaimSlaContext buildClaimSlaContext(final WorkItem item, final Instant now) {
        final Duration totalPoolSla = config.defaultClaimHours() > 0
                ? Duration.ofHours(config.defaultClaimHours())
                : Duration.ofHours(24);
        final Duration accumulated = Duration.ofSeconds(item.accumulatedUnclaimedSeconds);
        final Instant submitted = item.createdAt != null ? item.createdAt : now;
        return new ClaimSlaContext(submitted, totalPoolSla, accumulated, now);
    }

    private WorkItem requireWorkItem(final UUID id) {
        return workItemStore.get(id)
                .orElseThrow(() -> new WorkItemNotFoundException(id));
    }

    private void audit(final UUID workItemId, final String event, final String actor, final String detail) {
        final AuditEntry entry = new AuditEntry();
        entry.workItemId = workItemId;
        entry.event = event;
        entry.actor = actor;
        entry.detail = detail;
        entry.occurredAt = Instant.now();
        auditStore.append(entry);
    }

    /**
     * Resolve a business-hours count to an absolute {@link Instant} using the configured
     * {@link BusinessCalendar}. Falls back to wall-clock hours when no BusinessCalendar CDI bean
     * is available.
     */
    private Instant resolveBusinessHours(final Instant from, final int businessHours) {
        if (businessCalendar != null && !businessCalendar.isUnsatisfied()) {
            final java.time.ZoneId zone = java.time.ZoneId.of(config.businessHours().timezone());
            return businessCalendar.get().addBusinessDuration(from, Duration.ofHours(businessHours), zone);
        }
        // Fallback: treat as wall-clock hours when no calendar configured
        return from.plus(businessHours, ChronoUnit.HOURS);
    }
}

package io.casehub.work.runtime.multiinstance;

import io.casehub.platform.api.routing.StrategyResolver;
import io.casehub.work.api.GroupStatus;
import io.casehub.work.api.MultiInstanceConfig;
import io.casehub.work.api.MultiInstanceContext;
import io.casehub.work.api.ParentRole;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.spi.InstanceAssignmentStrategy;
import io.casehub.work.runtime.model.OutcomeCodecs;
import io.casehub.work.runtime.model.WorkItemRelation;
import io.casehub.work.api.WorkItemRelationType;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a multi-instance group: a parent WorkItem + N child instances + a
 * {@link WorkItemSpawnGroup} that owns the M-of-N completion policy.
 *
 * <p>
 * All three artefacts are created inside a single transaction (the caller's or
 * one started here via {@code @Transactional}). The assignment strategy named
 * on the template is resolved via {@link StrategyResolver} by id; when absent
 * or blank, the default {@code "pool"} strategy is used.
 */
@ApplicationScoped
public class MultiInstanceSpawnService {

    @Inject
    WorkItemService workItemService;

    @Inject
    StrategyResolver strategyResolver;
    @Inject
    io.casehub.work.api.spi.WorkItemStore workItemStore;


    @Transactional
    public io.casehub.work.api.WorkItem createGroup(final WorkItemCreateRequest mergedRequest,
                                                    final WorkItemTemplate template, final String expandedExcludedUsers) {
        final io.casehub.work.api.WorkItem parent = workItemService.create(mergedRequest);

        final WorkItemSpawnGroup group = new WorkItemSpawnGroup();
        group.parentId           = parent.id();
        group.idempotencyKey     = "multi-instance:" + parent.id();
        group.instanceCount      = template.instanceCount;
        group.requiredCount      = template.requiredCount;
        group.onThresholdReached = template.onThresholdReached;
        group.allowSameAssignee  = Boolean.TRUE.equals(template.allowSameAssignee);
        group.parentRole         = template.parentRole != null ? template.parentRole : ParentRole.COORDINATOR.name();
        group.groupStatus        = GroupStatus.IN_PROGRESS;
        group.tenancyId          = parent.tenancyId();
        group.persist();

        final List<io.casehub.work.api.WorkItem> children = new ArrayList<>();
        for (int i = 0; i < template.instanceCount; i++) {
            final WorkItemCreateRequest childReq = buildChildRequest(template, expandedExcludedUsers,
                                                                     mergedRequest.createdBy, i, group, mergedRequest.scope, mergedRequest.tenancyId,
                                                                     mergedRequest.permittedOutcomes);
            io.casehub.work.api.WorkItem child = workItemService.create(childReq);
            child = child.toBuilder().parentId(parent.id()).build();
            workItemStore.put(child);

            final WorkItemRelation rel = new WorkItemRelation();
            rel.sourceId     = child.id();
            rel.targetId     = parent.id();
            rel.relationType = WorkItemRelationType.PART_OF;
            rel.createdBy    = "system:multi-instance:" + group.id;
            rel.tenancyId    = parent.tenancyId();
            rel.persist();

            children.add(child);
        }

        final InstanceAssignmentStrategy strategy = resolveStrategy(template.assignmentStrategy);
        final MultiInstanceConfig config = new MultiInstanceConfig(
                template.instanceCount,
                template.requiredCount != null ? template.requiredCount : template.instanceCount,
                null,
                template.assignmentStrategy,
                null,
                Boolean.TRUE.equals(template.allowSameAssignee),
                null);
        strategy.assign((List) children, new MultiInstanceContext(parent, config));
        for (int i = 0; i < children.size(); i++) {
            workItemStore.put(children.get(i));
        }

        return parent;
    }

    @Transactional
    public io.casehub.work.api.WorkItem createGroupFromRequest(final WorkItemCreateRequest parentRequest,
                                                               final MultiInstanceConfig config) {
        if (parentRequest.callerRef != null) {
            workItemService.findActiveByCallerRef(parentRequest.callerRef)
                           .ifPresent(existing -> {
                               throw new IllegalStateException(
                                       "Active WorkItem already exists for callerRef: " + parentRequest.callerRef);
                           });
        }

        final io.casehub.work.api.WorkItem parent = workItemService.create(parentRequest);

        final WorkItemSpawnGroup group = new WorkItemSpawnGroup();
        group.parentId           = parent.id();
        group.idempotencyKey     = parentRequest.callerRef != null
                                   ? parentRequest.callerRef : "multi-instance:" + parent.id();
        group.instanceCount      = config.instanceCount();
        group.requiredCount      = config.requiredCount();
        group.onThresholdReached = config.effectiveOnThresholdReached().name();
        group.allowSameAssignee  = config.allowSameAssignee();
        group.parentRole         = config.effectiveParentRole().name();
        group.groupStatus        = GroupStatus.IN_PROGRESS;
        group.tenancyId          = parent.tenancyId();
        group.persist();

        final List<io.casehub.work.api.WorkItem> children = new ArrayList<>();
        for (int i = 0; i < config.instanceCount(); i++) {
            final WorkItemCreateRequest childReq = parentRequest.toBuilder()
                                                                .callerRef(null)
                                                                .createdBy("system:multi-instance:" + group.id)
                                                                .title(parentRequest.title + " [" + (i + 1) + "/" + config.instanceCount() + "]")
                                                                .auditDetail("Multi-instance child " + (i + 1) + " of " + config.instanceCount())
                                                                .build();
            io.casehub.work.api.WorkItem child = workItemService.create(childReq);
            child = child.toBuilder().parentId(parent.id()).build();
            workItemStore.put(child);

            final WorkItemRelation rel = new WorkItemRelation();
            rel.sourceId     = child.id();
            rel.targetId     = parent.id();
            rel.relationType = WorkItemRelationType.PART_OF;
            rel.createdBy    = "system:multi-instance:" + group.id;
            rel.tenancyId    = parent.tenancyId();
            rel.persist();

            children.add(child);
        }

        final InstanceAssignmentStrategy strategy = resolveStrategy(
                config.effectiveAssignmentStrategyName());
        strategy.assign((List) children, new MultiInstanceContext(parent, config));
        for (int i = 0; i < children.size(); i++) {
            workItemStore.put(children.get(i));
        }

        return parent;
    }


    private WorkItemCreateRequest buildChildRequest(final WorkItemTemplate template,
            final String expandedExcludedUsers, final String createdBy,
            final int index, final WorkItemSpawnGroup group,
            final String scope, final String tenancyId,
            final java.util.List<io.casehub.work.api.Outcome> permittedOutcomes) {
        return WorkItemCreateRequest.builder()
                .title(template.name + " [" + (index + 1) + "/" + template.instanceCount + "]")
                .description(template.description)
                .types(WorkItemTemplateService.parseTypes(template).stream().map(t -> t.path).toList())
                .priority(template.priority)
                .candidateGroups(template.candidateGroups)
                .candidateUsers(template.candidateUsers)
                .requiredCapabilities(template.requiredCapabilities)
                .createdBy("system:multi-instance:" + group.id)
                .payload(template.defaultPayload)
                .claimDeadlineBusinessHours(template.defaultClaimBusinessHours)
                .expiresAtBusinessHours(template.defaultExpiryBusinessHours)
                .templateId(template.id)
                .permittedOutcomes(permittedOutcomes != null
                        ? permittedOutcomes
                        : OutcomeCodecs.decodeOutcomes(template.outcomes))
                .inputDataSchema(template.inputDataSchema)
                .outputDataSchema(template.outputDataSchema)
                .excludedUsers(expandedExcludedUsers)
                .scope(scope != null ? scope : template.scope)
                .tenancyId(tenancyId)
                .build();
    }

    private InstanceAssignmentStrategy resolveStrategy(final String name) {
        if (name == null || name.isBlank()) {
            return strategyResolver.resolve(InstanceAssignmentStrategy.class, "pool");
        }
        return strategyResolver.resolve(InstanceAssignmentStrategy.class, name);
    }
}

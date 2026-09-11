package io.casehub.work.runtime.service;

import io.casehub.platform.api.routing.StrategyResolver;
import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.spi.ExclusionPolicy;
import io.casehub.work.api.spi.WorkerRegistry;
import io.casehub.work.api.spi.WorkerSelectionStrategy;
import io.casehub.work.api.spi.WorkloadProvider;
import io.casehub.work.core.strategy.WorkBroker;


import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Orchestrates worker selection for WorkItems on creation, release, and delegation.
 *
 * <p>
 * Flow:
 * <ol>
 * <li>Resolve active strategy via {@link StrategyResolver} using the configured id</li>
 * <li>Build resolved candidate list from {@code candidateUsers} + {@code WorkerRegistry}</li>
 * <li>Populate {@code activeWorkItemCount} for each candidate via {@link WorkloadProvider}</li>
 * <li>Delegate trigger gating, capability filtering, and strategy dispatch to {@link WorkBroker}</li>
 * <li>Apply non-null fields of {@link AssignmentDecision} to the WorkItem</li>
 * </ol>
 *
 * <p>
 * Mutates the WorkItem in memory only. The caller's {@code @Transactional} boundary
 * flushes the changes to the database.
 */
public class WorkItemAssignmentService {

    private final StrategyResolver strategyResolver;
    private final String routingStrategy;
    private final WorkerRegistry workerRegistry;
    private final WorkloadProvider workloadProvider;
    private final WorkBroker workBroker = new WorkBroker();
    private final ExclusionPolicy exclusionPolicy;

    public WorkItemAssignmentService(
            final StrategyResolver strategyResolver,
            final String routingStrategy,
            final WorkerRegistry workerRegistry,
            final WorkloadProvider workloadProvider,
            final ExclusionPolicy exclusionPolicy) {
        this.strategyResolver = strategyResolver;
        this.routingStrategy = routingStrategy;
        this.workerRegistry = workerRegistry;
        this.workloadProvider = workloadProvider;
        this.exclusionPolicy = exclusionPolicy;
    }

    public io.casehub.work.api.WorkItem assign(final io.casehub.work.api.WorkItem workItem, final AssignmentTrigger trigger) {
        final WorkerSelectionStrategy strategy   = activeStrategy();
        final List<WorkerCandidate>   candidates = resolveCandidates(workItem);
        final SelectionContext context = new SelectionContext(
                workItem.types() != null ? List.copyOf(workItem.types()) : java.util.List.of(),
                workItem.priority() != null ? workItem.priority().name() : null,
                CapabilityParser.parseLenient(workItem.requiredCapabilities()),
                workItem.candidateGroups(),
                workItem.candidateUsers(),
                workItem.title(),
                workItem.description(),
                workItem.excludedUsers(),
                null,
                null);

        final AssignmentDecision decision = workBroker.apply(context, trigger, candidates, strategy);
        return applyDecision(workItem, decision);
    }

    private WorkerSelectionStrategy activeStrategy() {
        return strategyResolver.resolve(WorkerSelectionStrategy.class, routingStrategy);
    }

    private List<WorkerCandidate> resolveCandidates(final io.casehub.work.api.WorkItem workItem) {
        final List<WorkerCandidate> candidates = new ArrayList<>();

        if (workItem.candidateUsers() != null && !workItem.candidateUsers().isBlank()) {
            Arrays.stream(workItem.candidateUsers().split(","))
                  .map(String::trim)
                  .filter(id -> !id.isEmpty())
                  .forEach(id -> candidates.add(
                          WorkerCandidate.of(id).withActiveWorkItemCount(
                                  workloadProvider.getActiveWorkCount(id))));
        }

        if (workItem.candidateGroups() != null && !workItem.candidateGroups().isBlank()) {
            Arrays.stream(workItem.candidateGroups().split(","))
                  .map(String::trim)
                  .filter(g -> !g.isEmpty())
                  .flatMap(g -> workerRegistry.resolveGroup(g).stream())
                  .filter(c -> candidates.stream().noneMatch(e -> e.id().equals(c.id())))
                  .map(c -> c.activeWorkItemCount() > 0
                            ? c
                            : c.withActiveWorkItemCount(workloadProvider.getActiveWorkCount(c.id())))
                  .forEach(candidates::add);
        }

        if (workItem.excludedUsers() != null) {
            candidates.removeIf(c -> exclusionPolicy.check(c.id(), workItem.excludedUsers()).denied());
        }

        return candidates;
    }

    private io.casehub.work.api.WorkItem applyDecision(final io.casehub.work.api.WorkItem workItem, final AssignmentDecision decision) {
        var builder = workItem.toBuilder();
        if (decision.assigneeId() != null) {
            builder.assigneeId(decision.assigneeId());
            builder.status(WorkItemStatus.ASSIGNED);
            builder.assignedAt(Instant.now());
        }
        if (decision.candidateGroups() != null) {
            builder.candidateGroups(decision.candidateGroups());
        }
        if (decision.candidateUsers() != null) {
            builder.candidateUsers(decision.candidateUsers());
        }
        return builder.build();
    }
}

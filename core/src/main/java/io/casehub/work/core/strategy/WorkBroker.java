package io.casehub.work.core.strategy;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.AssignmentTrigger;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.WorkerSelectionStrategy;

/**
 * Generic work assignment broker.
 *
 * <p>Applies trigger gating, capability filtering, and strategy dispatch.
 * Does not know about any specific work-unit type — all domain-specific
 * logic (candidate resolution, workload counting, decision application)
 * belongs in the caller (e.g. {@code WorkItemAssignmentService}).
 *
 * <p>CaseHub replaces its {@code TaskBroker} by delegating to this bean.
 */
@ApplicationScoped
public class WorkBroker {

    /**
     * Apply the strategy to a pre-resolved candidate list.
     *
     * @param context the work unit's routing context
     * @param trigger which lifecycle event triggered this call
     * @param candidates resolved candidates with pre-populated activeWorkItemCount
     * @param strategy the active selection strategy
     * @return the assignment decision; never null
     */
    public AssignmentDecision apply(
            final SelectionContext context,
            final AssignmentTrigger trigger,
            final List<WorkerCandidate> candidates,
            final WorkerSelectionStrategy strategy) {

        if (!strategy.triggers().contains(trigger)) {
            return AssignmentDecision.noChange();
        }
        final List<WorkerCandidate> filtered = filterByCapabilities(context, candidates);
        return strategy.select(context, filtered);
    }

    /**
     * Filters candidates to those possessing all required capabilities.
     *
     * <p>Capability matching is exact and case-sensitive — enforced by the
     * {@link io.casehub.work.api.Capability} constructor. Engine case definitions and
     * worker registrations must use identical {@link io.casehub.work.api.Capability} values.
     */
    private List<WorkerCandidate> filterByCapabilities(
            final SelectionContext context, final List<WorkerCandidate> candidates) {
        if (context.requiredCapabilities().isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(c -> c.capabilities().containsAll(context.requiredCapabilities()))
                .toList();
    }
}

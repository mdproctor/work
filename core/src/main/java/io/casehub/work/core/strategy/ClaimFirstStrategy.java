package io.casehub.work.core.strategy;

import java.util.List;

import io.casehub.work.api.AssignmentDecision;
import io.casehub.work.api.SelectionContext;
import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.spi.WorkerSelectionStrategy;

/**
 * No-op worker selection strategy — leaves all WorkItems in the open pool.
 * Whoever claims first wins. Activated by:
 * {@code casehub.work.routing.strategy=claim-first}.
 *
 * <p>{@code @Alternative @Priority(0)} allows higher-priority strategies (e.g.
 * {@code SemanticWorkerSelectionStrategy} at priority 1) to override this default
 * without CDI ambiguity when multiple {@link WorkerSelectionStrategy} implementations
 * are on the classpath.
 */
public class ClaimFirstStrategy implements WorkerSelectionStrategy {

    @Override
    public String id() { return "claim-first"; }

    @Override
    public AssignmentDecision select(final SelectionContext context,
            final List<WorkerCandidate> candidates) {
        return AssignmentDecision.noChange();
    }
}

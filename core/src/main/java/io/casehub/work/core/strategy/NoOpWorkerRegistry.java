package io.casehub.work.core.strategy;

import java.util.List;

import io.casehub.work.api.WorkerCandidate;
import io.casehub.work.api.spi.WorkerRegistry;

/**
 * Default WorkerRegistry — returns empty list for all groups.
 * Groups remain claim-first until the application registers a real resolver.
 */
public class NoOpWorkerRegistry implements WorkerRegistry {

    @Override
    public List<WorkerCandidate> resolveGroup(final String groupName) {
        return List.of();
    }
}

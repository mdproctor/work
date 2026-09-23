package io.casehub.work.progress.rest.core;

import io.casehub.work.progress.runtime.event.ProgressEventBroadcaster;
import io.casehub.work.progress.runtime.service.ProgressService;
import io.casehub.work.progress.runtime.service.SubtreeRollbackService;
import io.casehub.work.progress.spi.ProgressEventStore;
import io.casehub.work.progress.spi.ProgressInstanceStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ProgressCoreProducers {

    @Produces
    @ApplicationScoped
    public ProgressCore progressCore(ProgressService progressService,
            ProgressEventStore eventStore, ProgressInstanceStore instanceStore,
            ProgressEventBroadcaster broadcaster,
            SubtreeRollbackService subtreeRollbackService) {
        return new ProgressCore(progressService, eventStore, instanceStore,
                broadcaster, subtreeRollbackService);
    }
}

package io.casehub.work.progress.rest.core;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Flow;

import io.casehub.work.progress.ProgressCreateRequest;
import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressSnapshot;
import io.casehub.work.progress.ProgressUpdatedEvent;
import io.casehub.work.progress.SubtreeRollbackResult;
import io.casehub.work.progress.rest.CreateProgressRequest;
import io.casehub.work.progress.rest.UpdateStateRequest;
import io.casehub.work.progress.rest.UpdateStepDataRequest;
import io.casehub.work.progress.runtime.event.ProgressEventBroadcaster;
import io.casehub.work.progress.runtime.service.ProgressService;
import io.casehub.work.progress.runtime.service.SubtreeRollbackService;
import io.casehub.work.progress.spi.ProgressEventStore;
import io.casehub.work.progress.spi.ProgressInstanceStore;
import io.smallrye.mutiny.Multi;

public class ProgressCore {

    private final ProgressService progressService;
    private final ProgressEventStore eventStore;
    private final ProgressInstanceStore instanceStore;
    private final ProgressEventBroadcaster broadcaster;
    private final SubtreeRollbackService subtreeRollbackService;

    public ProgressCore(ProgressService progressService, ProgressEventStore eventStore,
            ProgressInstanceStore instanceStore, ProgressEventBroadcaster broadcaster,
            SubtreeRollbackService subtreeRollbackService) {
        this.progressService = progressService;
        this.eventStore = eventStore;
        this.instanceStore = instanceStore;
        this.broadcaster = broadcaster;
        this.subtreeRollbackService = subtreeRollbackService;
    }

    public ProgressInstance create(CreateProgressRequest request) {
        ProgressCreateRequest domainReq = new ProgressCreateRequest(
                request.tenancyId(), request.scopeType(), request.scopeId(),
                request.shapeType(), request.state(),
                request.parentProgressId(), request.rollupStrategyId(),
                request.definition(), request.rollbackPolicy(),
                request.visualisationMode(), null);
        return progressService.create(domainReq);
    }

    public ProgressInstance updateState(UUID id, UpdateStateRequest body) {
        return progressService.updateState(id, body.state());
    }

    public ProgressInstance complete(UUID id) {
        return progressService.complete(id);
    }

    public ProgressInstance fail(UUID id) {
        return progressService.fail(id);
    }

    public ProgressInstance reactivate(UUID id) {
        return progressService.reactivate(id);
    }

    public ProgressInstance attachChild(UUID parentId, CreateProgressRequest request) {
        ProgressCreateRequest domainReq = new ProgressCreateRequest(
                request.tenancyId(), request.scopeType(), request.scopeId(),
                request.shapeType(), request.state(),
                null, request.rollupStrategyId(), request.definition(),
                request.rollbackPolicy(), request.visualisationMode(), null);
        return progressService.attachChild(parentId, domainReq);
    }

    public Optional<ProgressInstance> getById(UUID id) {
        return progressService.findById(id);
    }

    public Optional<TreeResponse> getTree(UUID id) {
        return progressService.findById(id)
                .map(root -> {
                    List<ProgressInstance> descendants = instanceStore.findDescendantsOf(id);
                    return new TreeResponse(root, descendants);
                });
    }

    public List<ProgressInstance> findByScope(String scopeType, String scopeId) {
        return progressService.findByScope(scopeType, scopeId);
    }

    public List<ProgressUpdatedEvent> getEvents(UUID id, String since) {
        if (since != null) {
            return eventStore.findByProgressIdSince(id, Instant.parse(since));
        }
        return eventStore.findByProgressId(id);
    }

    public ProgressInstance rollback(UUID id, UUID toEventId) {
        if (toEventId != null) {
            return progressService.rollbackToEvent(id, toEventId);
        }
        return progressService.rollback(id);
    }

    public SubtreeRollbackResult rollbackSubtree(UUID id, String timestamp, UUID toEventId) {
        if (timestamp != null && toEventId != null) {
            throw new IllegalArgumentException("timestamp and toEvent are mutually exclusive");
        }
        if (toEventId != null) {
            return subtreeRollbackService.rollbackSubtreeToEvent(id, toEventId);
        }
        if (timestamp != null) {
            return subtreeRollbackService.rollbackSubtree(id, Instant.parse(timestamp));
        }
        throw new IllegalArgumentException("timestamp or toEvent required");
    }

    public List<ProgressSnapshot> getSnapshots(UUID id, Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 1000) : 100;
        return progressService.getSnapshots(id, effectiveLimit);
    }

    public Flow.Publisher<ProgressUpdatedEvent> streamEvents(UUID id, String tenancyId) {
        return broadcaster.stream(tenancyId)
                .filter(event -> id.equals(event.rootProgressId())
                        || id.equals(event.progressId()));
    }

    public ProgressInstance startStep(UUID id, String stepName) {
        return progressService.startStep(id, stepName);
    }

    public ProgressInstance completeStep(UUID id, String stepName) {
        return progressService.completeStep(id, stepName);
    }

    public ProgressInstance skipStep(UUID id, String stepName) {
        return progressService.skipStep(id, stepName);
    }

    public ProgressInstance failStep(UUID id, String stepName) {
        return progressService.failStep(id, stepName);
    }

    public ProgressInstance updateStepState(UUID id, String stepName, UpdateStepDataRequest body) {
        return progressService.updateStepState(id, stepName, body.data());
    }
}

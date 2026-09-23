package io.casehub.work.rest.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.api.GroupStatus;
import io.casehub.work.api.WorkItem;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.rest.service.ViewMapper;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;

public class WorkItemInstancesCore {

    private final WorkItemStore workItemStore;
    private final WorkItemSpawnGroupStore spawnGroupStore;

    public WorkItemInstancesCore(WorkItemStore workItemStore,
            WorkItemSpawnGroupStore spawnGroupStore) {
        this.workItemStore = workItemStore;
        this.spawnGroupStore = spawnGroupStore;
    }

    public Optional<InstancesResponse> getInstances(UUID parentId) {
        if (workItemStore.get(parentId).isEmpty()) {
            return Optional.empty();
        }

        var group = spawnGroupStore.findMultiInstanceByParentId(parentId).orElse(null);
        if (group == null) {
            return Optional.empty();
        }

        List<WorkItem> children = workItemStore.findByParentId(parentId);
        GroupStatus status = group.groupStatus != null ? group.groupStatus : GroupStatus.IN_PROGRESS;

        return Optional.of(new InstancesResponse(
                parentId,
                group.id,
                group.instanceCount,
                group.requiredCount,
                group.completedCount,
                group.rejectedCount,
                status.name(),
                children.stream().map(ViewMapper::toView).toList()));
    }
}

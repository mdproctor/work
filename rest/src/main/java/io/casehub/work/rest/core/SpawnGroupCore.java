package io.casehub.work.rest.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.api.WorkItemRelationType;
import io.casehub.work.runtime.repository.WorkItemRelationStore;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;

public class SpawnGroupCore {

    private final WorkItemSpawnGroupStore spawnGroupStore;
    private final WorkItemRelationStore relationStore;

    public SpawnGroupCore(WorkItemSpawnGroupStore spawnGroupStore, WorkItemRelationStore relationStore) {
        this.spawnGroupStore = spawnGroupStore;
        this.relationStore = relationStore;
    }

    public Optional<Map<String, Object>> getGroup(UUID groupId) {
        return spawnGroupStore.get(groupId).map(group -> {
            String createdByMarker = "system:spawn:" + groupId;
            List<Map<String, Object>> children = relationStore
                    .findByTargetAndType(group.parentId, WorkItemRelationType.PART_OF)
                    .stream()
                    .filter(r -> createdByMarker.equals(r.createdBy))
                    .map(r -> Map.<String, Object>of(
                            "workItemId", r.sourceId.toString(),
                            "createdAt", r.createdAt.toString()))
                    .toList();
            return Map.<String, Object>of(
                    "id", group.id.toString(),
                    "parentId", group.parentId.toString(),
                    "idempotencyKey", group.idempotencyKey,
                    "createdAt", group.createdAt.toString(),
                    "children", children);
        });
    }
}

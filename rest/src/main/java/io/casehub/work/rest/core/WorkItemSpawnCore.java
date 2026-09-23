package io.casehub.work.rest.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.transaction.Transactional;

import io.casehub.work.api.ChildSpec;
import io.casehub.work.api.SpawnRequest;
import io.casehub.work.api.SpawnResult;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;
import io.casehub.work.runtime.service.WorkItemSpawnService;

public class WorkItemSpawnCore {

    private final WorkItemSpawnService spawnService;
    private final WorkItemSpawnGroupStore spawnGroupStore;

    public WorkItemSpawnCore(WorkItemSpawnService spawnService,
            WorkItemSpawnGroupStore spawnGroupStore) {
        this.spawnService = spawnService;
        this.spawnGroupStore = spawnGroupStore;
    }

    @Transactional
    public SpawnResultResponse spawn(UUID parentId, SpawnBodyRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("request body is required");
        }
        if (body.children() == null || body.children().isEmpty()) {
            throw new IllegalArgumentException("children must not be empty");
        }
        if (body.idempotencyKey() == null || body.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        for (SpawnChildRequest child : body.children()) {
            if (child.templateId() == null) {
                throw new IllegalArgumentException("templateId is required");
            }
        }

        List<ChildSpec> specs = body.children().stream()
                .map(c -> new ChildSpec(UUID.fromString(c.templateId()), c.callerRef(), c.overrides()))
                .toList();
        SpawnRequest request = new SpawnRequest(parentId, body.idempotencyKey(), specs);

        SpawnResult result = spawnService.spawn(request);
        Map<String, Object> responseBody = Map.of(
                "groupId", result.groupId().toString(),
                "children", result.children().stream()
                        .map(c -> Map.of(
                                "workItemId", c.workItemId().toString(),
                                "callerRef", c.callerRef() != null ? c.callerRef() : ""))
                        .toList());
        return new SpawnResultResponse(responseBody, result.created());
    }

    public List<Map<String, Object>> listSpawnGroups(UUID parentId) {
        return spawnGroupStore.findByParentId(parentId).stream()
                .map(g -> Map.<String, Object>of(
                        "id", g.id.toString(),
                        "parentId", g.parentId.toString(),
                        "idempotencyKey", g.idempotencyKey,
                        "createdAt", g.createdAt.toString()))
                .toList();
    }

    @Transactional
    public boolean cancelGroup(UUID parentId, UUID groupId, boolean cancelChildren) {
        var group = spawnGroupStore.get(groupId).orElse(null);
        if (group == null || !group.parentId.equals(parentId)) {
            return false;
        }
        spawnService.cancelGroup(groupId, cancelChildren);
        return true;
    }
}

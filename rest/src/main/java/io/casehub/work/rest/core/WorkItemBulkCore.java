package io.casehub.work.rest.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.transaction.Transactional;

import io.casehub.work.api.spi.WorkItemOperations;

public class WorkItemBulkCore {

    static final int MAX_BATCH_SIZE = 100;

    private final WorkItemOperations workItemService;

    public WorkItemBulkCore(WorkItemOperations workItemService) {
        this.workItemService = workItemService;
    }

    @Transactional
    public List<BulkItemResult> bulk(BulkRequest request) {
        if (request == null || request.operation() == null || request.operation().isBlank()) {
            throw new IllegalArgumentException("operation is required");
        }
        if (request.workItemIds() == null || request.workItemIds().isEmpty()) {
            throw new IllegalArgumentException("workItemIds must not be empty");
        }
        if (request.workItemIds().size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batch size exceeds maximum of " + MAX_BATCH_SIZE);
        }

        String op = request.operation().toLowerCase();
        if (!List.of("claim", "cancel").contains(op)) {
            throw new IllegalArgumentException("unknown operation '" + request.operation() + "'; supported: claim, cancel");
        }

        String actor = request.actorId() != null ? request.actorId() : "unknown";
        List<BulkItemResult> results = new ArrayList<>();

        for (String idStr : request.workItemIds()) {
            results.add(apply(op, idStr, actor, request.reason()));
        }

        return results;
    }

    private BulkItemResult apply(String op, String idStr, String actor, String reason) {
        try {
            UUID id = UUID.fromString(idStr);
            switch (op) {
                case "claim" -> workItemService.claim(id, actor);
                case "cancel" -> workItemService.cancel(id, actor, reason);
                default -> throw new IllegalArgumentException("Unknown operation: " + op);
            }
            return BulkItemResult.ok(idStr);
        } catch (Exception e) {
            return BulkItemResult.error(idStr, e.getMessage());
        }
    }
}

package io.casehub.work.rest.core;

import java.util.List;

public record BulkRequest(
        String operation,
        List<String> workItemIds,
        String actorId,
        String reason) {
}

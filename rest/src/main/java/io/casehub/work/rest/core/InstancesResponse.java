package io.casehub.work.rest.core;

import java.util.List;
import java.util.UUID;

import io.casehub.work.api.view.WorkItemView;

public record InstancesResponse(
        UUID parentId,
        UUID groupId,
        int instanceCount,
        int requiredCount,
        int completedCount,
        int rejectedCount,
        String groupStatus,
        List<WorkItemView> instances) {
}

package io.casehub.work.runtime.api;

import io.casehub.work.api.LabelPersistence;

public record WorkItemLabelResponse(
        String path,
        LabelPersistence persistence,
        String appliedBy) {
}

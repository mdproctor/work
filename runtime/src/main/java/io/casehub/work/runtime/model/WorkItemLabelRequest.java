package io.casehub.work.runtime.model;

import io.casehub.work.api.LabelPersistence;

/**
 * Value object carrying label data for WorkItem creation.
 * Pure Java — no JPA annotations, no HTTP concerns.
 */
public record WorkItemLabelRequest(String path, LabelPersistence persistence, String appliedBy) {
}

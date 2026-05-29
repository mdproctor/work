package io.casehub.work.api;

import java.util.Set;

/**
 * Minimal WorkItem context passed to {@link WorkerSelectionStrategy#select}.
 *
 * <p>Decouples strategies from the WorkItem JPA entity.
 *
 * @param category WorkItem category (may be null)
 * @param priority WorkItemPriority name e.g. "HIGH" (may be null)
 * @param requiredCapabilities capabilities the assignee must possess (empty set = no requirement);
 *     matched against worker capability tags using exact case-sensitive equality
 * @param candidateGroups comma-separated group names (may be null)
 * @param candidateUsers comma-separated user IDs (may be null)
 * @param title work item title — used by semantic matchers (may be null)
 * @param description work item description — used by semantic matchers (may be null)
 * @param excludedUsers comma-separated user IDs excluded from this WorkItem (may be null)
 */
public record SelectionContext(
        String category,
        String priority,
        Set<Capability> requiredCapabilities,
        String candidateGroups,
        String candidateUsers,
        String title,
        String description,
        String excludedUsers) {
}

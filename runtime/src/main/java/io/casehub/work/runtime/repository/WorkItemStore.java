package io.casehub.work.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemRootView;

/**
 * KV-native store SPI for {@link WorkItem} persistence.
 *
 * <p>
 * Replaces the SQL-shaped {@code WorkItemRepository} with a store interface
 * that separates primary-key operations ({@link #put}, {@link #get}) from
 * query operations ({@link #scan}) using the {@link WorkItemQuery} value object.
 * Backends translate {@code WorkItemQuery} to their native query language.
 *
 * <p>
 * <strong>CDI backend activation (three-tier priority ladder):</strong><br>
 * {@code @DefaultBean} (mock/in-memory) &lt; {@code @ApplicationScoped} (JPA/SQL primary) &lt;
 * {@code @Alternative @Priority(1)} (NoSQL secondary, beats JPA when co-deployed).
 * Adding a backend module to the classpath activates it automatically — no consumer changes.
 * See the platform
 * <a href="https://github.com/casehubio/garden/blob/main/docs/protocols/universal/persistence-backend-cdi-priority.md">persistence-backend-cdi-priority</a>
 * protocol.
 *
 * @see WorkItemQuery
 */
public interface WorkItemStore {

    /**
     * Persist or update a WorkItem and return the saved instance.
     * Replaces the former {@code save()} method — aligned with KV store terminology.
     *
     * @param workItem the work item to persist; must not be {@code null}
     * @return the persisted work item
     */
    WorkItem put(WorkItem workItem);

    /**
     * Retrieve a WorkItem by its primary key.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the work item, or empty if not found
     */
    Optional<WorkItem> get(UUID id);

    /**
     * Scan WorkItems matching the given query criteria.
     *
     * <p>
     * Assignment fields in the query are combined with OR logic; all other fields
     * are combined with AND logic. A {@code null} field imposes no constraint.
     *
     * <p>
     * Use {@link WorkItemQuery} static factories for common patterns:
     * {@link WorkItemQuery#inbox inbox}, {@link WorkItemQuery#expired expired},
     * {@link WorkItemQuery#claimExpired claimExpired},
     * {@link WorkItemQuery#byLabelPattern byLabelPattern}.
     *
     * @param query the query criteria; must not be {@code null}
     * @return list of matching work items; may be empty, never null
     */
    List<WorkItem> scan(WorkItemQuery query);

    /**
     * Return all WorkItems — for admin and monitoring use only.
     * Equivalent to {@code scan(WorkItemQuery.all())}.
     *
     * @return unordered list of all persisted work items
     */
    default List<WorkItem> scanAll() {
        return scan(WorkItemQuery.all());
    }

    /**
     * Count instances in a multi-instance group assigned to the given claimant,
     * excluding the WorkItem being claimed.
     * Returns 0 by default — override in JPA store.
     *
     * @param parentId the UUID of the parent WorkItem whose group is being checked
     * @param assigneeId the claimant to check for existing held instances
     * @param excludeId the WorkItem being claimed (excluded from the count)
     * @return the number of other instances in the group already held by the claimant
     */
    default long countByParentAndAssignee(UUID parentId, String assigneeId, UUID excludeId) {
        return 0L;
    }

    /**
     * Find a WorkItem by its caller reference.
     *
     * <p>
     * {@code callerRef} is the opaque string set by the caller when the WorkItem was created
     * (e.g. {@code "case:{caseId}/pi:{planItemId}"}). At most one WorkItem should exist per
     * callerRef; if multiple exist the first result is returned.
     *
     * <p>
     * The default implementation performs a linear scan via {@link #scanAll()} — override in
     * JPA/SQL stores for an indexed query.
     *
     * @param callerRef the caller reference to look up; must not be {@code null}
     * @return an {@link Optional} containing the matching WorkItem, or empty if not found
     */
    default Optional<WorkItem> findByCallerRef(String callerRef) {
        return scanAll().stream()
                .filter(wi -> callerRef.equals(wi.callerRef))
                .findFirst();
    }

    /**
     * Return root WorkItems (parentId IS NULL) visible to the caller, enriched with aggregate stats.
     *
     * <p>Visibility is an OR across all provided dimensions:
     * <ul>
     *   <li>{@code assignee} — matches {@code assigneeId = assignee}
     *   <li>{@code candidateUser} — matches {@code candidateUsers CONTAINS candidateUser}
     *   <li>{@code candidateGroups} — matches any group in the comma-separated {@code candidateGroups} field
     * </ul>
     *
     * <p>Any null parameter is skipped; if all are null/empty, returns an empty list.
     *
     * @param assignee the worker currently assigned; may be null
     * @param candidateUser a user eligible to claim the WorkItem; may be null
     * @param candidateGroups the groups to check visibility for; may be null or empty
     * @return list of root WorkItems enriched with child stats; never null
     */
    default List<WorkItemRootView> scanRoots(String assignee, String candidateUser, List<String> candidateGroups) {
        return java.util.Collections.emptyList();
    }
}

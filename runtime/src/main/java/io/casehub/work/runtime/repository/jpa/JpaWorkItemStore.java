package io.casehub.work.runtime.repository.jpa;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.api.GroupStatus;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemRootView;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;
import io.casehub.work.runtime.repository.WorkItemStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemStore}.
 *
 * <p>
 * The {@link #scan} method builds a dynamic JPQL query from the non-null fields of
 * the supplied {@link WorkItemQuery}, replacing the five separate query methods of
 * the former {@code JpaWorkItemRepository}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemStore extends TenantAwareStore implements WorkItemStore {

    @Inject
    WorkItemSpawnGroupStore spawnGroupStore;

    @Override
    public WorkItem put(final WorkItem workItem) {
        return withTenantQuery(() -> {
            if (workItem.tenancyId == null) {
                workItem.tenancyId = currentPrincipal.tenancyId();
            }
            workItem.persistAndFlush();
            return workItem;
        });
    }

    @Override
    public Optional<WorkItem> get(final UUID id) {
        return withTenantQuery(() ->
                WorkItem.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public Optional<WorkItem> findByCallerRef(final String callerRef) {
        return withTenantQuery(() ->
                WorkItem.find("callerRef = ?1 AND tenancyId = ?2", callerRef, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public List<WorkItem> scan(final WorkItemQuery query) {
        return withTenantQuery(() -> {
            final Map<String, Object> params = new HashMap<>();
            final StringBuilder jpql = new StringBuilder();

            // ── Tenant isolation — always first ──────────────────────────────────
            jpql.append("tenancyId = :tenancyId");
            params.put("tenancyId", currentPrincipal.tenancyId());

        // ── Assignment — OR logic ────────────────────────────────────────────
        final boolean hasAssigneeId = query.assigneeId() != null;
        final boolean hasCandidateGroups = query.candidateGroups() != null && !query.candidateGroups().isEmpty();
        final boolean hasCandidateUserId = query.candidateUserId() != null;
        final boolean hasAssignment = hasAssigneeId || hasCandidateGroups || hasCandidateUserId;

        if (hasAssignment) {
            jpql.append(" AND (1=0");
            if (hasAssigneeId) {
                jpql.append(" OR assigneeId = :assigneeId OR candidateUsers LIKE :assigneeIdLike");
                params.put("assigneeId", query.assigneeId());
                params.put("assigneeIdLike", "%" + query.assigneeId() + "%");
            }
            if (hasCandidateGroups) {
                for (int i = 0; i < query.candidateGroups().size(); i++) {
                    final String key = "group" + i;
                    jpql.append(" OR candidateGroups LIKE :").append(key);
                    params.put(key, "%" + query.candidateGroups().get(i) + "%");
                }
            }
            if (hasCandidateUserId && !hasAssigneeId) {
                // candidateUserId provided without assigneeId — match via candidateUsers LIKE
                jpql.append(" OR candidateUsers LIKE :candidateUserIdLike");
                params.put("candidateUserIdLike", "%" + query.candidateUserId() + "%");
            }
            jpql.append(")");
        }

        // ── Filters — AND logic ──────────────────────────────────────────────
        if (query.status() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("status = :status");
            params.put("status", query.status());
        }

        if (query.statusIn() != null && !query.statusIn().isEmpty()) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("status IN (:statusIn)");
            params.put("statusIn", query.statusIn());
        }

        if (query.priority() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("priority = :priority");
            params.put("priority", query.priority());
        }

        if (query.category() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("category = :category");
            params.put("category", query.category());
        }

        if (query.outcome() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("outcome = :outcome");
            params.put("outcome", query.outcome());
        }

        if (query.followUpBefore() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("followUpDate <= :followUpBefore");
            params.put("followUpBefore", query.followUpBefore());
        }

        if (query.expiresAtOrBefore() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("expiresAt <= :expiresAtOrBefore");
            params.put("expiresAtOrBefore", query.expiresAtOrBefore());
        }

        if (query.claimDeadlineOrBefore() != null) {
            if (jpql.length() > 0) {
                jpql.append(" AND ");
            }
            jpql.append("claimDeadline <= :claimDeadlineOrBefore");
            params.put("claimDeadlineOrBefore", query.claimDeadlineOrBefore());
        }

            // ── Label pattern — requires JOIN ────────────────────────────────────
            if (query.labelPattern() != null) {
                return scanByLabelPattern(query.labelPattern());
            }

            return WorkItem.find(jpql.toString(), params).list();
        });
    }

    @Override
    public long countByParentAndAssignee(final UUID parentId, final String assigneeId, final UUID excludeId) {
        return withTenantQuery(() -> {
            // Only count non-terminal instances — terminal children no longer block new claims
            return WorkItem.count(
                    "parentId = ?1 AND assigneeId = ?2 AND id != ?3 AND status NOT IN (?4) AND tenancyId = ?5",
                    parentId, assigneeId, excludeId,
                    List.of(io.casehub.work.runtime.model.WorkItemStatus.COMPLETED,
                            io.casehub.work.runtime.model.WorkItemStatus.REJECTED,
                            io.casehub.work.runtime.model.WorkItemStatus.CANCELLED,
                            io.casehub.work.runtime.model.WorkItemStatus.ESCALATED),
                    currentPrincipal.tenancyId());
        });
    }

    @Override
    public List<WorkItemRootView> scanRoots(
            final String assignee, final String candidateUser, final List<String> userGroups) {
        return withTenantQuery(() -> {
            // Build visibility predicate using named params (same pattern as scan()).
            // Tenant isolation is always the first predicate.
            final StringBuilder pred = new StringBuilder();
            final Map<String, Object> params = new HashMap<>();

            pred.append("tenancyId = :tenancyId");
            params.put("tenancyId", currentPrincipal.tenancyId());

        // Each non-null dimension is an independent OR predicate, grouped in parens.
        final StringBuilder visibilityPred = new StringBuilder();
        if (assignee != null && !assignee.isBlank()) {
            visibilityPred.append("assigneeId = :assigneeId");
            params.put("assigneeId", assignee);
        }
        if (candidateUser != null && !candidateUser.isBlank()) {
            if (!visibilityPred.isEmpty()) visibilityPred.append(" OR ");
            visibilityPred.append("candidateUsers LIKE :candidateUserLike");
            params.put("candidateUserLike", "%" + candidateUser + "%");
        }
        if (userGroups != null) {
            int gi = 0;
            for (final String group : userGroups) {
                final String key = "grp" + gi++;
                if (!visibilityPred.isEmpty()) {
                    visibilityPred.append(" OR ");
                }
                visibilityPred.append("candidateGroups LIKE :").append(key);
                params.put(key, "%" + group + "%");
            }
        }
        if (visibilityPred.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        pred.append(" AND (").append(visibilityPred).append(")");

            // Find directly visible items
            final List<WorkItem> directlyVisible = WorkItem.find(pred.toString(), params).list();

            // Collect roots (items with parentId IS NULL) including ancestors of visible children
            final LinkedHashSet<UUID> rootIds = new LinkedHashSet<>();
            final LinkedHashMap<UUID, WorkItem> rootItems = new LinkedHashMap<>();
            final String tenancyId = currentPrincipal.tenancyId();

            for (final WorkItem item : directlyVisible) {
                if (item.parentId == null) {
                    rootIds.add(item.id);
                    rootItems.put(item.id, item);
                } else {
                    // Tenant-scoped parent lookup (replaces static WorkItem.findById)
                    final WorkItem parent = WorkItem.<WorkItem> find(
                            "id = ?1 AND tenancyId = ?2", item.parentId, tenancyId).firstResult();
                    if (parent != null && parent.parentId == null) {
                        rootIds.add(parent.id);
                        rootItems.put(parent.id, parent);
                    }
                }
            }

            return rootIds.stream().map(id -> {
                final WorkItem root = rootItems.get(id);
                final WorkItemSpawnGroup group = spawnGroupStore.findMultiInstanceByParentId(id).orElse(null);
                final int childCount = (int) WorkItem.count("parentId = ?1 AND tenancyId = ?2", id, tenancyId);
                if (group != null) {
                    final GroupStatus status = group.policyTriggered
                            ? (group.completedCount >= group.requiredCount
                                    ? GroupStatus.COMPLETED
                                    : GroupStatus.REJECTED)
                            : GroupStatus.IN_PROGRESS;
                    return new WorkItemRootView(root, childCount, group.completedCount, group.requiredCount, status);
                }
                return new WorkItemRootView(root, childCount, null, null, null);
            }).toList();
        });
    }

    /**
     * Label pattern scan using the existing JPQL JOIN approach from {@code JpaWorkItemRepository}.
     * Tenant-scoped: results are restricted to the current tenant.
     *
     * @param pattern the label pattern; must not be null
     * @return matching work items
     */
    private List<WorkItem> scanByLabelPattern(final String pattern) {
        final String tenancyId = currentPrincipal.tenancyId();
        if (pattern.endsWith("/**")) {
            final String prefix = pattern.substring(0, pattern.length() - 3) + "/";
            return WorkItem.<WorkItem> find(
                    "SELECT DISTINCT wi FROM WorkItem wi JOIN wi.labels l WHERE wi.tenancyId = ?1 AND l.path LIKE ?2",
                    tenancyId, prefix + "%").list();
        }
        if (pattern.endsWith("/*")) {
            final String prefix = pattern.substring(0, pattern.length() - 2) + "/";
            return WorkItem.<WorkItem> find(
                    "SELECT DISTINCT wi FROM WorkItem wi JOIN wi.labels l " +
                            "WHERE wi.tenancyId = ?1 AND l.path LIKE ?2 AND l.path NOT LIKE ?3",
                    tenancyId, prefix + "%", prefix + "%/%").list();
        }
        return WorkItem.<WorkItem> find(
                "SELECT DISTINCT wi FROM WorkItem wi JOIN wi.labels l WHERE wi.tenancyId = ?1 AND l.path = ?2",
                tenancyId, pattern).list();
    }

    @Override
    public List<WorkItem> findByParentIdExcludingStatuses(final UUID parentId,
            final List<io.casehub.work.runtime.model.WorkItemStatus> excludeStatuses) {
        return withTenantQuery(() ->
                WorkItem.<WorkItem> find(
                        "parentId = ?1 AND tenancyId = ?2 AND status NOT IN (?3)",
                        parentId, currentPrincipal.tenancyId(), excludeStatuses).list());
    }

    @Override
    public List<WorkItem> findByParentIdWithStatuses(final UUID parentId,
            final List<io.casehub.work.runtime.model.WorkItemStatus> statuses) {
        return withTenantQuery(() ->
                WorkItem.<WorkItem> find(
                        "parentId = ?1 AND tenancyId = ?2 AND status IN (?3)",
                        parentId, currentPrincipal.tenancyId(), statuses).list());
    }

    @Override
    public List<WorkItem> findByParentId(final UUID parentId) {
        return withTenantQuery(() ->
                WorkItem.<WorkItem> find(
                        "parentId = ?1 AND tenancyId = ?2",
                        parentId, currentPrincipal.tenancyId()).list());
    }
}

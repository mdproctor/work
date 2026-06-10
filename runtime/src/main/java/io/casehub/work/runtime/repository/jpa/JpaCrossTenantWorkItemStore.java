package io.casehub.work.runtime.repository.jpa;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.CrossTenantWorkItemStore;

/**
 * Cross-tenant JPA implementation of {@link CrossTenantWorkItemStore}.
 *
 * <p>Extends {@link TenantAwareStore} and uses {@link #withCrossTenantQuery} to
 * execute {@code SET LOCAL ROLE casehub_crosstenancy} — bypassing RLS policies.
 * Only injected into system-level services via the {@code @CrossTenant} qualifier.
 */
@ApplicationScoped
public class JpaCrossTenantWorkItemStore extends TenantAwareStore implements CrossTenantWorkItemStore {

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<WorkItem> findActiveWithDeadlines() {
        return withCrossTenantQuery(() ->
            WorkItem.find(
                "status NOT IN (?1) AND (expiresAt IS NOT NULL OR claimDeadline IS NOT NULL)",
                List.of(WorkItemStatus.COMPLETED, WorkItemStatus.REJECTED,
                        WorkItemStatus.CANCELLED, WorkItemStatus.EXPIRED,
                        WorkItemStatus.ESCALATED))
                .list());
    }
}

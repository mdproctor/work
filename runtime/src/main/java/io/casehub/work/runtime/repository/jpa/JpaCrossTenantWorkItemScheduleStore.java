package io.casehub.work.runtime.repository.jpa;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.CrossTenantWorkItemScheduleStore;

/**
 * Cross-tenant JPA implementation of {@link CrossTenantWorkItemScheduleStore}.
 *
 * <p>Extends {@link TenantAwareStore} and uses {@link #withCrossTenantQuery} to
 * execute {@code SET LOCAL ROLE casehub_crosstenancy} — bypassing RLS policies.
 * Only injected into system-level services via the {@code @CrossTenant} qualifier.
 */
@ApplicationScoped
public class JpaCrossTenantWorkItemScheduleStore extends TenantAwareStore implements CrossTenantWorkItemScheduleStore {

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<WorkItemSchedule> findActive() {
        return withCrossTenantQuery(() -> WorkItemSchedule.find("active = true").list());
    }
}

package io.casehub.work.queues.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.queues.model.WorkItemFilter;
import io.casehub.work.queues.repository.WorkItemFilterStore;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemFilterStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemFilterStore extends TenantAwareStore implements WorkItemFilterStore {

    @Override
    public WorkItemFilter put(final WorkItemFilter filter) {
        return withTenantQuery(() -> {
            if (filter.tenancyId == null) {
                filter.tenancyId = currentPrincipal.tenancyId();
            }
            filter.persistAndFlush();
            return filter;
        });
    }

    @Override
    public Optional<WorkItemFilter> get(final UUID id) {
        return withTenantQuery(() ->
            WorkItemFilter.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                    .firstResultOptional()
        );
    }

    @Override
    public List<WorkItemFilter> findActive() {
        return withTenantQuery(() ->
            WorkItemFilter.list("active = true AND conditionLanguage != 'lambda' AND tenancyId = ?1",
                    currentPrincipal.tenancyId())
        );
    }

    @Override
    public List<WorkItemFilter> scanAll() {
        return withTenantQuery(() ->
            WorkItemFilter.list("tenancyId", currentPrincipal.tenancyId())
        );
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            long deleted = WorkItemFilter.delete("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }
}

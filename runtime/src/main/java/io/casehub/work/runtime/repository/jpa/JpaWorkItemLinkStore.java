package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.model.WorkItemLink;
import io.casehub.work.runtime.repository.WorkItemLinkStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemLinkStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemLinkStore extends TenantAwareStore implements WorkItemLinkStore {

    @Override
    public WorkItemLink put(final WorkItemLink link) {
        return withTenantQuery(() -> {
            if (link.tenancyId == null) {
                link.tenancyId = currentPrincipal.tenancyId();
            }
            link.persistAndFlush();
            return link;
        });
    }

    @Override
    public Optional<WorkItemLink> get(final UUID id) {
        return withTenantQuery(() ->
                WorkItemLink.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public List<WorkItemLink> findByWorkItemId(final UUID workItemId) {
        return withTenantQuery(() ->
                WorkItemLink.list("workItemId = ?1 AND tenancyId = ?2 ORDER BY createdAt ASC",
                        workItemId, currentPrincipal.tenancyId()));
    }

    @Override
    public List<WorkItemLink> findByWorkItemIdAndType(final UUID workItemId, final String type) {
        return withTenantQuery(() ->
                WorkItemLink.list(
                        "workItemId = ?1 AND relationType = ?2 AND tenancyId = ?3 ORDER BY createdAt ASC",
                        workItemId, type, currentPrincipal.tenancyId()));
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final long deleted = WorkItemLink.delete("id = ?1 AND tenancyId = ?2",
                    id, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }
}

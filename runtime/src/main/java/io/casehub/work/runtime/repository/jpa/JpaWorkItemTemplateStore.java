package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemTemplateStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemTemplateStore extends TenantAwareStore implements WorkItemTemplateStore {

    @Override
    public WorkItemTemplate put(final WorkItemTemplate template) {
        return withTenantQuery(() -> {
            if (template.tenancyId == null) {
                template.tenancyId = currentPrincipal.tenancyId();
            }
            template.persistAndFlush();
            return template;
        });
    }

    @Override
    public Optional<WorkItemTemplate> get(final UUID id) {
        return withTenantQuery(() ->
                WorkItemTemplate.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public Optional<WorkItemTemplate> getByName(final String name) {
        return withTenantQuery(() ->
                WorkItemTemplate.find("name = ?1 AND tenancyId = ?2", name, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public List<WorkItemTemplate> scanAll() {
        return withTenantQuery(() ->
                WorkItemTemplate.find("tenancyId = ?1 ORDER BY name ASC", currentPrincipal.tenancyId())
                        .list());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final long deleted = WorkItemTemplate.delete("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }
}

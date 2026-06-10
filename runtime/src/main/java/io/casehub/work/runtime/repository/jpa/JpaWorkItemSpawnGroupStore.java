package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemSpawnGroupStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemSpawnGroupStore extends TenantAwareStore implements WorkItemSpawnGroupStore {

    @Override
    public WorkItemSpawnGroup put(final WorkItemSpawnGroup group) {
        return withTenantQuery(() -> {
            if (group.tenancyId == null) {
                group.tenancyId = currentPrincipal.tenancyId();
            }
            group.persistAndFlush();
            return group;
        });
    }

    @Override
    public Optional<WorkItemSpawnGroup> get(final UUID id) {
        return withTenantQuery(() ->
                WorkItemSpawnGroup.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public List<WorkItemSpawnGroup> findByParentId(final UUID parentId) {
        return withTenantQuery(() ->
                WorkItemSpawnGroup.list(
                        "parentId = ?1 AND tenancyId = ?2 ORDER BY createdAt DESC",
                        parentId, currentPrincipal.tenancyId()));
    }

    @Override
    public Optional<WorkItemSpawnGroup> findByParentAndKey(final UUID parentId, final String groupKey) {
        return withTenantQuery(() ->
                WorkItemSpawnGroup.find(
                        "parentId = ?1 AND idempotencyKey = ?2 AND tenancyId = ?3",
                        parentId, groupKey, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public Optional<WorkItemSpawnGroup> findMultiInstanceByParentId(final UUID parentId) {
        return withTenantQuery(() ->
                WorkItemSpawnGroup.find(
                        "parentId = ?1 AND requiredCount IS NOT NULL AND tenancyId = ?2",
                        parentId, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final long deleted = WorkItemSpawnGroup.delete("id = ?1 AND tenancyId = ?2",
                    id, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }
}

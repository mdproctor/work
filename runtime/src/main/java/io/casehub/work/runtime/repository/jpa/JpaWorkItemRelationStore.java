package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.model.WorkItemRelation;
import io.casehub.work.runtime.repository.WorkItemRelationStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemRelationStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemRelationStore extends TenantAwareStore implements WorkItemRelationStore {

    @Override
    public WorkItemRelation put(final WorkItemRelation relation) {
        return withTenantQuery(() -> {
            if (relation.tenancyId == null) {
                relation.tenancyId = currentPrincipal.tenancyId();
            }
            relation.persistAndFlush();
            return relation;
        });
    }

    @Override
    public Optional<WorkItemRelation> get(final UUID id) {
        return withTenantQuery(() ->
                WorkItemRelation.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public List<WorkItemRelation> findBySourceId(final UUID sourceId) {
        return withTenantQuery(() ->
                WorkItemRelation.list("sourceId = ?1 AND tenancyId = ?2 ORDER BY createdAt ASC",
                        sourceId, currentPrincipal.tenancyId()));
    }

    @Override
    public List<WorkItemRelation> findByTargetId(final UUID targetId) {
        return withTenantQuery(() ->
                WorkItemRelation.list("targetId = ?1 AND tenancyId = ?2 ORDER BY createdAt ASC",
                        targetId, currentPrincipal.tenancyId()));
    }

    @Override
    public List<WorkItemRelation> findBySourceAndType(final UUID sourceId, final String type) {
        return withTenantQuery(() ->
                WorkItemRelation.list(
                        "sourceId = ?1 AND relationType = ?2 AND tenancyId = ?3 ORDER BY createdAt ASC",
                        sourceId, type, currentPrincipal.tenancyId()));
    }

    @Override
    public List<WorkItemRelation> findByTargetAndType(final UUID targetId, final String type) {
        return withTenantQuery(() ->
                WorkItemRelation.list(
                        "targetId = ?1 AND relationType = ?2 AND tenancyId = ?3 ORDER BY createdAt ASC",
                        targetId, type, currentPrincipal.tenancyId()));
    }

    @Override
    public Optional<WorkItemRelation> findExisting(final UUID sourceId, final UUID targetId,
            final String relationType) {
        return withTenantQuery(() ->
                WorkItemRelation.find(
                        "sourceId = ?1 AND targetId = ?2 AND relationType = ?3 AND tenancyId = ?4",
                        sourceId, targetId, relationType, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final long deleted = WorkItemRelation.delete("id = ?1 AND tenancyId = ?2",
                    id, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }
}

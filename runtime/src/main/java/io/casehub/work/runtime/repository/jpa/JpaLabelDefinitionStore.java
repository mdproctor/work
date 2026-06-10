package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;
import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.repository.LabelDefinitionStore;

/**
 * Default JPA/Panache implementation of {@link LabelDefinitionStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaLabelDefinitionStore extends TenantAwareStore implements LabelDefinitionStore {

    @Override
    public LabelDefinition put(final LabelDefinition definition) {
        return withTenantQuery(() -> {
            if (definition.tenancyId == null) {
                definition.tenancyId = currentPrincipal.tenancyId();
            }
            definition.persistAndFlush();
            return definition;
        });
    }

    @Override
    public Optional<LabelDefinition> get(final UUID id) {
        return withTenantQuery(() ->
                LabelDefinition.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public List<LabelDefinition> findByVocabularyId(final UUID vocabularyId) {
        return withTenantQuery(() ->
                LabelDefinition.find("vocabularyId = ?1 AND tenancyId = ?2", vocabularyId, currentPrincipal.tenancyId())
                        .list());
    }

    @Override
    public List<LabelDefinition> findByPath(final Path path) {
        return withTenantQuery(() ->
                LabelDefinition.find("path = ?1 AND tenancyId = ?2", path, currentPrincipal.tenancyId())
                        .list());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final long deleted = LabelDefinition.delete("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }
}

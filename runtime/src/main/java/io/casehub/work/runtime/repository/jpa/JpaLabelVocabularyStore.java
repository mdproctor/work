package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.repository.LabelVocabularyStore;

/**
 * Default JPA/Panache implementation of {@link LabelVocabularyStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaLabelVocabularyStore extends TenantAwareStore implements LabelVocabularyStore {

    @Override
    public LabelVocabulary put(final LabelVocabulary vocabulary) {
        return withTenantQuery(() -> {
            if (vocabulary.tenancyId == null) {
                vocabulary.tenancyId = currentPrincipal.tenancyId();
            }
            vocabulary.persistAndFlush();
            return vocabulary;
        });
    }

    @Override
    public Optional<LabelVocabulary> get(final UUID id) {
        return withTenantQuery(() ->
                LabelVocabulary.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public List<LabelVocabulary> scanAll() {
        return withTenantQuery(() ->
                LabelVocabulary.find("tenancyId = ?1", currentPrincipal.tenancyId())
                        .list());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final long deleted = LabelVocabulary.delete("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }
}

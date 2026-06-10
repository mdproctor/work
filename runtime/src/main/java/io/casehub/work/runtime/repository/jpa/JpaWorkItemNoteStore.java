package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.model.WorkItemNote;
import io.casehub.work.runtime.repository.WorkItemNoteStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemNoteStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #append} and {@link #update} methods stamp {@code tenancyId} from the principal
 * when the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemNoteStore extends TenantAwareStore implements WorkItemNoteStore {

    @Override
    public WorkItemNote append(final WorkItemNote note) {
        return withTenantQuery(() -> {
            if (note.tenancyId == null) {
                note.tenancyId = currentPrincipal.tenancyId();
            }
            note.persistAndFlush();
            return note;
        });
    }

    @Override
    public Optional<WorkItemNote> findById(final UUID noteId) {
        return withTenantQuery(() ->
                WorkItemNote.find("id = ?1 AND tenancyId = ?2", noteId, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public List<WorkItemNote> findByWorkItemId(final UUID workItemId) {
        return withTenantQuery(() ->
                WorkItemNote.list("workItemId = ?1 AND tenancyId = ?2 ORDER BY createdAt ASC",
                        workItemId, currentPrincipal.tenancyId()));
    }

    @Override
    public WorkItemNote update(final WorkItemNote note) {
        return withTenantQuery(() -> {
            if (note.tenancyId == null) {
                note.tenancyId = currentPrincipal.tenancyId();
            }
            note.persistAndFlush();
            return note;
        });
    }

    @Override
    public boolean delete(final UUID noteId) {
        return withTenantQuery(() -> {
            // Tenant-scoped delete — only delete if it belongs to current tenant
            long deleted = WorkItemNote.delete("id = ?1 AND tenancyId = ?2", noteId, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }
}

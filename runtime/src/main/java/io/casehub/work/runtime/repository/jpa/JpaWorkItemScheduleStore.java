package io.casehub.work.runtime.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.WorkItemScheduleStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemScheduleStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaWorkItemScheduleStore extends TenantAwareStore implements WorkItemScheduleStore {

    @Override
    public WorkItemSchedule put(final WorkItemSchedule schedule) {
        return withTenantQuery(() -> {
            if (schedule.tenancyId == null) {
                schedule.tenancyId = currentPrincipal.tenancyId();
            }
            schedule.persistAndFlush();
            return schedule;
        });
    }

    @Override
    public Optional<WorkItemSchedule> get(final UUID id) {
        return withTenantQuery(() ->
                WorkItemSchedule.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public List<WorkItemSchedule> scanAll() {
        return withTenantQuery(() ->
                WorkItemSchedule.find("tenancyId = ?1 ORDER BY name ASC", currentPrincipal.tenancyId())
                        .list());
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final long deleted = WorkItemSchedule.delete("id = ?1 AND tenancyId = ?2",
                    id, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }

    @Override
    public List<WorkItemSchedule> findDue(final Instant now) {
        return withTenantQuery(() ->
                WorkItemSchedule.list(
                        "active = true AND nextFireAt IS NOT NULL AND nextFireAt <= ?1 AND tenancyId = ?2 ORDER BY nextFireAt ASC",
                        now, currentPrincipal.tenancyId()));
    }
}

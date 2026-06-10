package io.casehub.work.notifications.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.notifications.model.WorkItemNotificationRule;
import io.casehub.work.notifications.repository.NotificationRuleStore;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;

/**
 * Default JPA/Panache implementation of {@link NotificationRuleStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaNotificationRuleStore extends TenantAwareStore implements NotificationRuleStore {

    @Override
    public WorkItemNotificationRule put(final WorkItemNotificationRule rule) {
        return withTenantQuery(() -> {
            if (rule.tenancyId == null) {
                rule.tenancyId = currentPrincipal.tenancyId();
            }
            rule.persistAndFlush();
            return rule;
        });
    }

    @Override
    public Optional<WorkItemNotificationRule> get(final UUID id) {
        return withTenantQuery(() ->
            WorkItemNotificationRule.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                    .firstResultOptional()
        );
    }

    @Override
    public List<WorkItemNotificationRule> findEnabledForEventType(final String eventType) {
        return withTenantQuery(() ->
            WorkItemNotificationRule.list(
                    "enabled = true AND eventTypes LIKE ?1 AND tenancyId = ?2 ORDER BY createdAt ASC",
                    "%" + eventType + "%", currentPrincipal.tenancyId())
        );
    }

    @Override
    public List<WorkItemNotificationRule> findAllEnabled() {
        return withTenantQuery(() ->
            WorkItemNotificationRule.list("enabled = true AND tenancyId = ?1 ORDER BY createdAt ASC",
                    currentPrincipal.tenancyId())
        );
    }

    @Override
    public List<WorkItemNotificationRule> scanAll() {
        return withTenantQuery(() ->
            WorkItemNotificationRule.list("tenancyId", currentPrincipal.tenancyId())
        );
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final long deleted = WorkItemNotificationRule.delete("id = ?1 AND tenancyId = ?2",
                    id, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }
}

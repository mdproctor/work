package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.filter.FilterRule;
import io.casehub.work.runtime.repository.FilterRuleStore;

/**
 * Default JPA/Panache implementation of {@link FilterRuleStore}.
 *
 * <p>
 * Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaFilterRuleStore extends TenantAwareStore implements FilterRuleStore {

    @Override
    public FilterRule put(final FilterRule rule) {
        return withTenantQuery(() -> {
            if (rule.tenancyId == null) {
                rule.tenancyId = currentPrincipal.tenancyId();
            }
            rule.persistAndFlush();
            return rule;
        });
    }

    @Override
    public Optional<FilterRule> get(final UUID id) {
        return withTenantQuery(() ->
                FilterRule.find("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId())
                        .firstResultOptional());
    }

    @Override
    public List<FilterRule> allEnabled() {
        return withTenantQuery(() ->
                FilterRule.list("enabled = true AND tenancyId = ?1 ORDER BY createdAt ASC", currentPrincipal.tenancyId()));
    }

    @Override
    public List<FilterRule> scanAll() {
        return withTenantQuery(() ->
                FilterRule.list("tenancyId = ?1 ORDER BY createdAt ASC", currentPrincipal.tenancyId()));
    }

    @Override
    public boolean delete(final UUID id) {
        return withTenantQuery(() -> {
            final long deleted = FilterRule.delete("id = ?1 AND tenancyId = ?2", id, currentPrincipal.tenancyId());
            return deleted > 0;
        });
    }
}

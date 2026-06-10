package io.casehub.work.queues.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.queues.model.FilterChain;
import io.casehub.work.queues.repository.FilterChainStore;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;

/**
 * Default JPA/Panache implementation of {@link FilterChainStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaFilterChainStore extends TenantAwareStore implements FilterChainStore {

    @Override
    public FilterChain put(final FilterChain chain) {
        return withTenantQuery(() -> {
            if (chain.tenancyId == null) {
                chain.tenancyId = currentPrincipal.tenancyId();
            }
            chain.persistAndFlush();
            return chain;
        });
    }

    @Override
    public Optional<FilterChain> findByFilterId(final UUID filterId) {
        return withTenantQuery(() ->
            FilterChain.find("filterId = ?1 AND tenancyId = ?2",
                    filterId, currentPrincipal.tenancyId())
                    .firstResultOptional()
        );
    }

    @Override
    public FilterChain findOrCreateForFilter(final UUID filterId) {
        return withTenantQuery(() ->
            findByFilterId(filterId).orElseGet(() -> {
                final FilterChain fc = new FilterChain();
                fc.filterId = filterId;
                fc.tenancyId = currentPrincipal.tenancyId();
                fc.persistAndFlush();
                return fc;
            })
        );
    }
}

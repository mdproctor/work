package io.casehub.work.runtime.repository.jpa;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import io.casehub.work.runtime.model.RoutingCursor;
import io.casehub.work.runtime.repository.CrossTenantRoutingCursorStore;

/**
 * Cross-tenant JPA implementation of {@link CrossTenantRoutingCursorStore}.
 *
 * <p>Extends {@link TenantAwareStore} and uses {@link #withCrossTenantQuery} to
 * execute {@code SET LOCAL ROLE casehub_crosstenancy} — bypassing RLS policies.
 * Only injected into system-level services via the {@code @CrossTenant} qualifier.
 */
@ApplicationScoped
public class JpaCrossTenantRoutingCursorStore extends TenantAwareStore implements CrossTenantRoutingCursorStore {

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public long cleanupStale(Instant cutoff) {
        return withCrossTenantQuery(() -> RoutingCursor.delete("lastAccessed < ?1", cutoff));
    }
}

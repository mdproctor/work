package io.casehub.work.runtime.repository.jpa;

import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.casehub.platform.api.identity.CurrentPrincipal;

/**
 * Blocking Hibernate ORM equivalent of the engine's {@code TenantAwareRepository}.
 *
 * <p>All JPA data access classes extend this base class. Provides {@link #withTenantQuery}
 * for tenant-scoped operations and {@link #withCrossTenantQuery} for cross-tenant operations
 * that bypass RLS via the {@code casehub_crosstenancy} PostgreSQL role.
 *
 * <p>Both methods are gated by {@code casehub.work.rls.enabled} (default {@code false}).
 * When disabled (H2 tests), the SET LOCAL calls are skipped — H2 does not support
 * PostgreSQL GUC variables. When enabled (PostgreSQL), SET LOCAL is executed within the
 * caller's transaction.
 */
public abstract class TenantAwareStore {

    @Inject
    protected EntityManager em;

    @Inject
    protected CurrentPrincipal currentPrincipal;

    @ConfigProperty(name = "casehub.work.rls.enabled", defaultValue = "false")
    boolean rlsEnabled;

    protected <T> T withTenantQuery(Supplier<T> work) {
        if (rlsEnabled) {
            assertTransactionActive();
            String tid = currentPrincipal.tenancyId();
            if (tid == null || tid.contains("'") || tid.contains("\\")) {
                throw new IllegalStateException("Invalid tenancyId in CurrentPrincipal: " + tid);
            }
            em.createNativeQuery(
                    "SET LOCAL \"casehub.tenancy_id\" = '" + tid + "'")
                    .executeUpdate();
        }
        return work.get();
    }

    protected void withTenantRun(Runnable work) {
        withTenantQuery(() -> {
            work.run();
            return null;
        });
    }

    protected <T> T withCrossTenantQuery(Supplier<T> work) {
        if (rlsEnabled) {
            assertTransactionActive();
            em.createNativeQuery("SET LOCAL ROLE casehub_crosstenancy")
                    .executeUpdate();
        }
        return work.get();
    }

    private void assertTransactionActive() {
        if (!em.isJoinedToTransaction()) {
            throw new IllegalStateException(
                    "SET LOCAL requires an active transaction — "
                            + "store methods must run within a @Transactional boundary");
        }
    }
}

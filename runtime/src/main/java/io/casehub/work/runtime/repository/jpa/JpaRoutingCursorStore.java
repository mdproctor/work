package io.casehub.work.runtime.repository.jpa;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.core.strategy.RoutingCursorStore;
import io.casehub.work.runtime.model.RoutingCursor;
import io.casehub.work.runtime.model.RoutingCursorId;

/**
 * JPA-backed {@link RoutingCursorStore}.
 *
 * <p>
 * All cursors are tenant-scoped via {@link CurrentPrincipal#tenancyId()}.
 * The composite primary key ({@code poolHash + tenancyId}) is used for find-or-create.
 *
 * <p>
 * {@code acquireNext()} is non-transactional so it can retry by calling
 * {@link #doAcquire(String, int)} — a separate CDI-intercepted method — through
 * the CDI proxy ({@code self}). Each {@code doAcquire()} call starts a fresh
 * {@code REQUIRES_NEW} transaction. On failure, the outer method retries once; if
 * the retry also fails, returns index 0 as a predictable fallback.
 */
@ApplicationScoped
public class JpaRoutingCursorStore extends TenantAwareStore implements RoutingCursorStore {

    private static final Logger LOG = Logger.getLogger(JpaRoutingCursorStore.class);

    @Inject
    JpaRoutingCursorStore self;

    @Override
    public int acquireNext(final String poolHash, final int poolSize) {
        try {
            return self.doAcquire(poolHash, poolSize);
        } catch (final PersistenceException e) {
            LOG.debugf("Cursor conflict for pool %s — retrying once", poolHash);
            try {
                return self.doAcquire(poolHash, poolSize);
            } catch (final PersistenceException e2) {
                LOG.warnf("Cursor conflict for pool %s — fallback to index 0", poolHash);
                return 0;
            }
        }
    }

    /**
     * Single atomic attempt: find-or-create cursor, advance index, persist.
     * Runs in its own {@code REQUIRES_NEW} transaction; callers handle retries.
     * Tenant-scoped: uses composite key (poolHash + tenancyId) for lookup.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int doAcquire(final String poolHash, final int poolSize) {
        return withTenantQuery(() -> {
            final String tenancyId = currentPrincipal.tenancyId();
            final RoutingCursorId id = new RoutingCursorId(poolHash, tenancyId);

            RoutingCursor cursor = RoutingCursor.findById(id);
            if (cursor == null) {
                cursor = new RoutingCursor(poolHash);
                cursor.tenancyId = tenancyId;
                cursor.persist();
                RoutingCursor.flush();
            }
            final int next = (cursor.lastIndex + 1) % poolSize;
            cursor.lastIndex = next;
            cursor.lastAccessed = Instant.now();
            return next;
        });
    }
}

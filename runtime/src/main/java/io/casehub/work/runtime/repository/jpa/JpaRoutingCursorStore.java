package io.casehub.work.runtime.repository.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.casehub.work.core.strategy.RoutingCursorStore;
import io.casehub.work.runtime.model.RoutingCursor;

/**
 * JPA-backed {@link RoutingCursorStore}.
 *
 * <p>
 * {@code acquireNext()} is non-transactional so it can retry by calling
 * {@link #doAcquire(String, int)} — a separate CDI-intercepted method — through
 * the CDI proxy ({@code self}). Each {@code doAcquire()} call starts a fresh
 * {@code REQUIRES_NEW} transaction. On failure, the outer method retries once; if
 * the retry also fails, returns index 0 as a predictable fallback.
 */
@ApplicationScoped
public class JpaRoutingCursorStore implements RoutingCursorStore {

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
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int doAcquire(final String poolHash, final int poolSize) {
        RoutingCursor cursor = RoutingCursor.findById(poolHash);
        if (cursor == null) {
            cursor = new RoutingCursor(poolHash);
            cursor.persist();
            RoutingCursor.flush();
        }
        final int next = (cursor.lastIndex + 1) % poolSize;
        cursor.lastIndex = next;
        return next;
    }
}

package io.casehub.work.runtime.repository.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.casehub.work.core.strategy.RoutingCursorStore;
import io.casehub.work.runtime.model.RoutingCursor;

/**
 * JPA-backed {@link RoutingCursorStore}.
 *
 * <p>
 * Each call runs in its own {@code REQUIRES_NEW} transaction, isolating the cursor
 * update from the outer assignment transaction. On OCC or INSERT conflict, retries
 * once; if the retry also fails, returns index 0 as a predictable fallback.
 */
@ApplicationScoped
public class JpaRoutingCursorStore implements RoutingCursorStore {

    private static final Logger LOG = Logger.getLogger(JpaRoutingCursorStore.class);

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int acquireNext(final String poolHash, final int poolSize) {
        return tryAcquire(poolHash, poolSize, true);
    }

    private int tryAcquire(final String poolHash, final int poolSize, final boolean allowRetry) {
        try {
            RoutingCursor cursor = RoutingCursor.findById(poolHash);
            if (cursor == null) {
                cursor = new RoutingCursor(poolHash);
                cursor.persist();
                RoutingCursor.flush();
            }
            final int next = (cursor.lastIndex + 1) % poolSize;
            cursor.lastIndex = next;
            return next;
        } catch (final jakarta.persistence.PersistenceException e) {
            if (allowRetry) {
                LOG.debugf("Cursor conflict for pool %s — retrying once", poolHash);
                return tryAcquire(poolHash, poolSize, false);
            }
            LOG.warnf("Cursor conflict for pool %s — fallback to index 0", poolHash);
            return 0;
        }
    }
}

package io.casehub.work.core.strategy;

/**
 * SPI for persisting round-robin cursor state per candidate pool.
 *
 * <p>
 * Each unique candidate pool is identified by a {@code poolHash} (SHA-256 of sorted
 * candidate IDs). The store atomically advances the cursor and returns the next index.
 *
 * <p>
 * Implementations must be atomic under concurrent access — the default JPA implementation
 * uses {@code @Transactional(REQUIRES_NEW)} with {@code @Version}-based OCC.
 */
public interface RoutingCursorStore {

    /**
     * Atomically advance the cursor for the given pool and return the next index.
     *
     * @param poolHash SHA-256 of the sorted candidate ID list (identifies the pool row)
     * @param poolSize number of candidates — used for modulo wrap
     * @return index into the candidate list for this assignment (0-based)
     */
    int acquireNext(String poolHash, int poolSize);
}

package io.casehub.work.testing;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.work.core.strategy.RoutingCursorStore;

/**
 * In-memory {@link RoutingCursorStore} for use in tests.
 *
 * <p>
 * Thread-safe, no datasource or Flyway required. Activated automatically when on
 * the test classpath via {@code @Alternative @Priority(1)}, overriding
 * {@code JpaRoutingCursorStore}.
 *
 * <p>
 * Call {@link #reset()} in {@code @BeforeEach} to clear cursor state between tests.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryRoutingCursorStore implements RoutingCursorStore {

    private final ConcurrentHashMap<String, AtomicInteger> cursors = new ConcurrentHashMap<>();

    @Override
    public int acquireNext(final String poolHash, final int poolSize) {
        final AtomicInteger cursor = cursors.computeIfAbsent(poolHash, k -> new AtomicInteger(-1));
        while (true) {
            final int current = cursor.get();
            final int next = (current + 1) % poolSize;
            if (cursor.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    /** Reset all cursors — call in {@code @BeforeEach} for test isolation. */
    public void reset() {
        cursors.clear();
    }
}

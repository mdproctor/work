package io.casehub.work.core.strategy;

/**
 * Default RoutingCursorStore — always returns index 0.
 *
 * <p>
 * Active when casehub-work-runtime is not on the classpath (e.g. casehub-engine tests
 * that depend on casehub-work-core but not the full runtime). JpaRoutingCursorStore in
 * the runtime module displaces this via CDI's @DefaultBean yielding semantics.
 */
public class NoOpRoutingCursorStore implements RoutingCursorStore {

    @Override
    public int acquireNext(final String poolHash, final int poolSize) {
        return 0;
    }
}

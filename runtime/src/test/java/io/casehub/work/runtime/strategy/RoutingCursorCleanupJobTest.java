package io.casehub.work.runtime.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.work.runtime.model.RoutingCursor;
import io.casehub.work.runtime.model.RoutingCursorId;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies {@link RoutingCursorCleanupJob} deletes stale cursor rows and preserves fresh ones.
 *
 * <p>Data is persisted in a separate committed transaction (via {@link #inTx}) because the
 * cleanup job's cross-tenant store uses {@code @Transactional(REQUIRES_NEW)} — it cannot
 * see uncommitted data from the test's transaction.
 */
@QuarkusTest
class RoutingCursorCleanupJobTest {

    @Inject
    RoutingCursorCleanupJob cleanupJob;

    @Test
    void cleanup_deletesRowsOlderThanCutoff() {
        final String staleHash = "stale-" + UUID.randomUUID();
        inTx(() -> {
            final RoutingCursor stale = new RoutingCursor(staleHash);
            stale.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
            stale.lastAccessed = Instant.now().minus(35, ChronoUnit.DAYS);
            stale.persist();
        });

        cleanupJob.cleanup();

        final RoutingCursor result = inTx(() -> RoutingCursor.findById(
                new RoutingCursorId(staleHash, TenancyConstants.DEFAULT_TENANT_ID)));
        assertThat(result).isNull();
    }

    @Test
    void cleanup_preservesRecentRows() {
        final String freshHash = "fresh-" + UUID.randomUUID();
        inTx(() -> {
            final RoutingCursor fresh = new RoutingCursor(freshHash);
            fresh.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
            fresh.lastAccessed = Instant.now().minus(1, ChronoUnit.DAYS);
            fresh.persist();
        });

        cleanupJob.cleanup();

        final RoutingCursor result = inTx(() -> RoutingCursor.findById(
                new RoutingCursorId(freshHash, TenancyConstants.DEFAULT_TENANT_ID)));
        assertThat(result).isNotNull();
    }

    @Transactional
    <T> T inTx(Supplier<T> s) {
        return s.get();
    }

    @Transactional
    void inTx(Runnable r) {
        r.run();
    }
}

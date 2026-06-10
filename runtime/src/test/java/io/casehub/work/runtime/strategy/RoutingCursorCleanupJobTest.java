package io.casehub.work.runtime.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.work.runtime.model.RoutingCursor;
import io.casehub.work.runtime.model.RoutingCursorId;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies {@link RoutingCursorCleanupJob} deletes stale cursor rows and preserves fresh ones.
 */
@QuarkusTest
class RoutingCursorCleanupJobTest {

    @Inject
    RoutingCursorCleanupJob cleanupJob;

    @Test
    @Transactional
    void cleanup_deletesRowsOlderThanCutoff() {
        final String staleHash = "stale-" + UUID.randomUUID();
        final RoutingCursor stale = new RoutingCursor(staleHash);
        stale.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        stale.lastAccessed = Instant.now().minus(35, ChronoUnit.DAYS);
        stale.persist();
        RoutingCursor.flush();

        cleanupJob.cleanup();
        RoutingCursor.getEntityManager().clear();

        assertThat((RoutingCursor) RoutingCursor.findById(
                new RoutingCursorId(staleHash, TenancyConstants.DEFAULT_TENANT_ID))).isNull();
    }

    @Test
    @Transactional
    void cleanup_preservesRecentRows() {
        final String freshHash = "fresh-" + UUID.randomUUID();
        final RoutingCursor fresh = new RoutingCursor(freshHash);
        fresh.tenancyId = TenancyConstants.DEFAULT_TENANT_ID;
        fresh.lastAccessed = Instant.now().minus(1, ChronoUnit.DAYS);
        fresh.persist();
        RoutingCursor.flush();

        cleanupJob.cleanup();
        RoutingCursor.getEntityManager().clear();

        assertThat((RoutingCursor) RoutingCursor.findById(
                new RoutingCursorId(freshHash, TenancyConstants.DEFAULT_TENANT_ID))).isNotNull();
    }
}

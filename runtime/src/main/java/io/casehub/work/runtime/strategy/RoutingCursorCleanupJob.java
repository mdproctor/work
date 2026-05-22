package io.casehub.work.runtime.strategy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.casehub.work.runtime.config.WorkItemsConfig;
import io.casehub.work.runtime.model.RoutingCursor;
import io.quarkus.scheduler.Scheduled;

/**
 * Periodically deletes stale {@link RoutingCursor} rows whose {@code lastAccessed}
 * timestamp is older than the configured TTL.
 *
 * <p>
 * Candidate pools accumulate over time as assignment candidate lists change. Rows
 * whose pools are no longer in use are never accessed again — this job reclaims them.
 *
 * <p>
 * The job is disabled by setting {@code casehub.work.routing.cursor.cleanup-cron=disabled}.
 */
@ApplicationScoped
public class RoutingCursorCleanupJob {

    private static final Logger LOG = Logger.getLogger(RoutingCursorCleanupJob.class);

    @Inject
    WorkItemsConfig config;

    /**
     * Runs on the configured cron schedule. Deletes cursor rows not accessed within
     * the configured TTL.
     */
    @Scheduled(cron = "{casehub.work.routing.cursor.cleanup-cron}")
    @Transactional
    public void scheduledCleanup() {
        cleanup();
    }

    /**
     * Deletes cursor rows older than the configured TTL. Called by the scheduler
     * and directly by tests.
     *
     * <p>
     * {@code @Transactional} is required for the direct-call path used in tests.
     * When invoked from {@link #scheduledCleanup()}, the annotation is a no-op
     * (internal {@code this} call bypasses CDI proxy); the enclosing scheduler
     * transaction covers the bulk delete.
     */
    @Transactional
    public void cleanup() {
        final Instant cutoff = Instant.now().minus(config.routing().cursor().ttlDays(), ChronoUnit.DAYS);
        final long deleted = RoutingCursor.delete("lastAccessed < ?1", cutoff);
        if (deleted > 0) {
            LOG.infof("Deleted %d stale routing_cursor rows (last_accessed < %s)", deleted, cutoff);
        }
    }
}

package io.casehub.work.runtime.service;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.repository.AuditEntryStore;

/**
 * Records blocked lifecycle attempt audit entries in independent transactions.
 *
 * <p>Called at enforcement points in {@link WorkItemService} before throwing a rejection
 * exception. The {@link TxType#REQUIRES_NEW} annotation ensures the audit entry commits
 * even when the caller's transaction rolls back — which is always the case when a
 * lifecycle operation is rejected.
 *
 * <p>The {@code record} method MUST return normally — it does not throw. If it threw,
 * its own REQUIRES_NEW transaction would also roll back, defeating the purpose.
 */
@ApplicationScoped
public class BlockedAttemptAuditService {

    private static final Logger LOG = Logger.getLogger(BlockedAttemptAuditService.class.getName());

    private final AuditEntryStore auditStore;

    @Inject
    public BlockedAttemptAuditService(final AuditEntryStore auditStore) {
        this.auditStore = auditStore;
    }

    /**
     * Writes a blocked-attempt audit entry in its own committed transaction.
     *
     * <p>This method ALWAYS returns normally — it never throws. If the audit write fails
     * (e.g. transient datasource error), the failure is logged and swallowed so that
     * the caller can throw its own rejection exception (409/400). A failed audit write
     * must not convert a policy rejection into an unexpected 500 for the client.
     *
     * <p>The {@link TxType#REQUIRES_NEW} annotation ensures the entry commits independently
     * of the caller's transaction, which always rolls back on a rejected operation.
     *
     * @param workItemId the WorkItem the attempt was made against
     * @param event      short event identifier, e.g. {@code "CLAIM_DENIED"}
     * @param actor      identity of the user who made the attempt
     * @param detail     human-readable detail from the policy, e.g. the denial reason
     */
    @Transactional(TxType.REQUIRES_NEW)
    public void record(final UUID workItemId, final String event,
                       final String actor, final String detail) {
        try {
            final AuditEntry entry = new AuditEntry();
            entry.workItemId = workItemId;
            entry.event = event;
            entry.actor = actor;
            entry.detail = detail;
            auditStore.append(entry);
        } catch (final Exception e) {
            // Swallow intentionally — REQUIRES_NEW rollback isolates the failure.
            // The caller throws its own rejection exception regardless.
            LOG.log(Level.WARNING, "Failed to record blocked attempt audit entry for workItem "
                    + workItemId + ", event=" + event, e);
        }
    }
}

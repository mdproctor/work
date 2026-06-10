package io.casehub.work.ledger.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.ledger.runtime.model.LedgerEntry;
import io.casehub.work.ledger.model.WorkItemLedgerEntry;
import io.casehub.work.ledger.repository.WorkItemLedgerEntryRepository;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;

/**
 * Hibernate ORM / EntityManager implementation of {@link WorkItemLedgerEntryRepository}.
 *
 * <p>
 * Uses EntityManager directly — {@link LedgerEntry} and its subclasses are plain JPA
 * entities (not Panache), so all queries go through JPQL or named queries.
 *
 * <p>All queries are scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The WorkItem-specific methods resolve the tenant automatically; the inherited interface
 * methods receive {@code tenancyId} explicitly from the caller.
 */
@ApplicationScoped
public class JpaWorkItemLedgerEntryRepository extends TenantAwareStore implements WorkItemLedgerEntryRepository {

    // ── WorkItem-specific methods (tenant resolved from CurrentPrincipal) ─────

    /** {@inheritDoc} */
    @Override
    public List<WorkItemLedgerEntry> findByWorkItemId(final UUID workItemId) {
        return withTenantQuery(() ->
            em.createQuery(
                    "SELECT e FROM WorkItemLedgerEntry e WHERE e.subjectId = :subjectId AND e.tenancyId = :tenancyId ORDER BY e.sequenceNumber ASC",
                    WorkItemLedgerEntry.class)
                    .setParameter("subjectId", workItemId)
                    .setParameter("tenancyId", currentPrincipal.tenancyId())
                    .getResultList()
        );
    }

    /** {@inheritDoc} */
    @Override
    public Optional<WorkItemLedgerEntry> findLatestByWorkItemId(final UUID workItemId) {
        return withTenantQuery(() ->
            em.createQuery(
                    "SELECT e FROM WorkItemLedgerEntry e WHERE e.subjectId = :subjectId AND e.tenancyId = :tenancyId ORDER BY e.sequenceNumber DESC",
                    WorkItemLedgerEntry.class)
                    .setParameter("subjectId", workItemId)
                    .setParameter("tenancyId", currentPrincipal.tenancyId())
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
        );
    }

    /** {@inheritDoc} */
    @Override
    public Optional<WorkItemLedgerEntry> findEarliestByWorkItemId(final UUID workItemId) {
        return withTenantQuery(() ->
            em.createQuery(
                    "SELECT e FROM WorkItemLedgerEntry e WHERE e.subjectId = :subjectId AND e.tenancyId = :tenancyId ORDER BY e.sequenceNumber ASC",
                    WorkItemLedgerEntry.class)
                    .setParameter("subjectId", workItemId)
                    .setParameter("tenancyId", currentPrincipal.tenancyId())
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
        );
    }

    // ── Inherited LedgerEntryRepository methods ──────────────────────────────

    /** {@inheritDoc} */
    @Override
    public LedgerEntry save(final LedgerEntry entry, final String tenancyId) {
        if (entry.tenancyId == null) {
            entry.tenancyId = tenancyId;
        }
        em.persist(entry);
        return entry;
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findBySubjectId(final UUID subjectId, final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId AND e.tenancyId = :tenancyId ORDER BY e.sequenceNumber ASC",
                LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findBySubjectIdAndTimeRange(final UUID subjectId, final Instant from, final Instant to, final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId AND e.tenancyId = :tenancyId AND e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<LedgerEntry> findLatestBySubjectId(final UUID subjectId, final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.subjectId = :subjectId AND e.tenancyId = :tenancyId ORDER BY e.sequenceNumber DESC",
                LedgerEntry.class)
                .setParameter("subjectId", subjectId)
                .setParameter("tenancyId", tenancyId)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<LedgerEntry> findEntryById(final UUID id, final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.id = :id AND e.tenancyId = :tenancyId",
                LedgerEntry.class)
                .setParameter("id", id)
                .setParameter("tenancyId", tenancyId)
                .getResultStream()
                .findFirst();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(final UUID ledgerEntryId, final String tenancyId) {
        return em.createNamedQuery("LedgerAttestation.findByEntryId", LedgerAttestation.class)
                .setParameter("entryId", ledgerEntryId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public LedgerAttestation saveAttestation(final LedgerAttestation attestation, final String tenancyId) {
        em.persist(attestation);
        return attestation;
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findByActorId(final String actorId, final Instant from, final Instant to, final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.actorId = :actorId AND e.tenancyId = :tenancyId AND e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("actorId", actorId)
                .setParameter("tenancyId", tenancyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findByActorRole(final String actorRole, final Instant from, final Instant to, final String tenancyId) {
        return em.createQuery(
                "SELECT e FROM LedgerEntry e WHERE e.actorRole = :actorRole AND e.tenancyId = :tenancyId AND e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC",
                LedgerEntry.class)
                .setParameter("actorRole", actorRole)
                .setParameter("tenancyId", tenancyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerEntry> findCausedBy(final UUID entryId, final String tenancyId) {
        // ObservabilitySupplement.causedByEntryId stored in supplement JSON — not yet queryable via JPQL
        return List.of();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(
            final UUID entryId, final String capabilityTag, final String tenancyId) {
        return em.createNamedQuery("LedgerAttestation.findByEntryIdAndCapabilityTag", LedgerAttestation.class)
                .setParameter("entryId", entryId)
                .setParameter("capabilityTag", capabilityTag)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdGlobal(final UUID entryId, final String tenancyId) {
        return em.createNamedQuery("LedgerAttestation.findGlobalByEntryId", LedgerAttestation.class)
                .setParameter("entryId", entryId)
                .getResultList();
    }

    /** {@inheritDoc} */
    @Override
    public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(
            final String attestorId, final String capabilityTag, final String tenancyId) {
        return em.createNamedQuery("LedgerAttestation.findByAttestorIdAndCapabilityTag", LedgerAttestation.class)
                .setParameter("attestorId", attestorId)
                .setParameter("capabilityTag", capabilityTag)
                .getResultList();
    }
}

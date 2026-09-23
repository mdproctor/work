package io.casehub.work.ledger.api.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.transaction.Transactional;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.model.supplement.JpaProvenanceSupplement;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.ledger.api.LedgerMapper;
import io.casehub.work.ledger.api.dto.LedgerAttestationRequest;
import io.casehub.work.ledger.api.dto.LedgerEntryResponse;
import io.casehub.work.ledger.api.dto.ProvenanceRequest;
import io.casehub.work.ledger.model.WorkItemLedgerEntry;
import io.casehub.work.ledger.repository.WorkItemLedgerEntryRepository;
import io.casehub.work.runtime.service.WorkItemNotFoundException;

public class LedgerCore {

    private final WorkItemLedgerEntryRepository ledgerRepo;
    private final WorkItemStore workItemStore;
    private final CurrentPrincipal currentPrincipal;
    private final LedgerConfig config;

    public LedgerCore(WorkItemLedgerEntryRepository ledgerRepo, WorkItemStore workItemStore,
            CurrentPrincipal currentPrincipal, LedgerConfig config) {
        this.ledgerRepo = ledgerRepo;
        this.workItemStore = workItemStore;
        this.currentPrincipal = currentPrincipal;
        this.config = config;
    }

    @Transactional
    public List<LedgerEntryResponse> getLedger(UUID workItemId) {
        workItemStore.get(workItemId)
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));

        List<WorkItemLedgerEntry> entries = ledgerRepo.findByWorkItemId(workItemId);
        entries.forEach(WorkItemLedgerEntry::syncSupplementsFromJpa);
        return entries.stream()
                .map(e -> LedgerMapper.toResponse(e, ledgerRepo.findAttestationsByEntryId(e.id, currentPrincipal.tenancyId())))
                .toList();
    }

    @Transactional
    public ProvenanceOutcome setProvenance(UUID workItemId, ProvenanceRequest request) {
        workItemStore.get(workItemId)
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));

        List<WorkItemLedgerEntry> entries = ledgerRepo.findByWorkItemId(workItemId);
        WorkItemLedgerEntry creationEntry = entries.stream()
                .filter(e -> e.sequenceNumber == 1)
                .findFirst()
                .orElse(null);

        if (creationEntry == null) {
            return ProvenanceOutcome.NOT_FOUND;
        }

        if (creationEntry.provenance().isPresent()) {
            return ProvenanceOutcome.CONFLICT;
        }

        var provenance = new JpaProvenanceSupplement();
        provenance.sourceEntityId = request.sourceEntityId();
        provenance.sourceEntityType = request.sourceEntityType();
        provenance.sourceEntitySystem = request.sourceEntitySystem();
        creationEntry.attach(provenance);
        ledgerRepo.save(creationEntry, currentPrincipal.tenancyId());

        return ProvenanceOutcome.OK;
    }

    @Transactional
    public AttestationOutcome postAttestation(UUID workItemId, UUID entryId,
            LedgerAttestationRequest request) {
        if (!config.attestations().enabled()) {
            return AttestationOutcome.DISABLED;
        }

        Optional<WorkItemLedgerEntry> entryOpt = ledgerRepo.findEntryById(entryId, currentPrincipal.tenancyId())
                .filter(e -> e instanceof WorkItemLedgerEntry)
                .map(e -> (WorkItemLedgerEntry) e)
                .filter(e -> workItemId.equals(e.subjectId));

        if (entryOpt.isEmpty()) {
            return AttestationOutcome.NOT_FOUND;
        }

        LedgerAttestation attestation = new io.casehub.ledger.runtime.model.LedgerAttestation();
        attestation.ledgerEntryId = entryId;
        attestation.subjectId = workItemId;
        attestation.attestorId = request.attestorId();
        attestation.attestorType = request.attestorType();
        attestation.verdict = request.verdict();
        attestation.evidence = request.evidence();
        attestation.confidence = request.confidence();

        ledgerRepo.saveAttestation(attestation, currentPrincipal.tenancyId());

        return AttestationOutcome.CREATED;
    }

    public enum ProvenanceOutcome { OK, NOT_FOUND, CONFLICT }
    public enum AttestationOutcome { CREATED, NOT_FOUND, DISABLED }
}

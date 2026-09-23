package io.casehub.work.ledger.api.core;

import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.ledger.repository.WorkItemLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class LedgerCoreProducers {

    @Produces
    @ApplicationScoped
    public ActorTrustCore actorTrustCore(ActorTrustScoreRepository trustScoreRepository,
            LedgerConfig config) {
        return new ActorTrustCore(trustScoreRepository, config);
    }

    @Produces
    @ApplicationScoped
    public LedgerCore ledgerCore(WorkItemLedgerEntryRepository ledgerRepo,
            WorkItemStore workItemStore, CurrentPrincipal currentPrincipal,
            LedgerConfig config) {
        return new LedgerCore(ledgerRepo, workItemStore, currentPrincipal, config);
    }
}

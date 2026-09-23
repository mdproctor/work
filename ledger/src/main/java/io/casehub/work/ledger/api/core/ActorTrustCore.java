package io.casehub.work.ledger.api.core;

import java.util.Optional;

import io.casehub.ledger.runtime.config.LedgerConfig;
import io.casehub.ledger.runtime.repository.ActorTrustScoreRepository;
import io.casehub.work.ledger.api.dto.ActorTrustScoreResponse;

public class ActorTrustCore {

    private final ActorTrustScoreRepository trustScoreRepository;
    private final LedgerConfig config;

    public ActorTrustCore(ActorTrustScoreRepository trustScoreRepository, LedgerConfig config) {
        this.trustScoreRepository = trustScoreRepository;
        this.config = config;
    }

    public Optional<ActorTrustScoreResponse> getActorTrust(String actorId) {
        if (!config.trustScore().enabled()) {
            return Optional.empty();
        }
        return trustScoreRepository.findByActorId(actorId)
                .map(s -> new ActorTrustScoreResponse(s.actorId, s.actorType, s.trustScore,
                        s.decisionCount, s.overturnedCount, s.attestationPositive,
                        s.attestationNegative, s.lastComputedAt));
    }
}

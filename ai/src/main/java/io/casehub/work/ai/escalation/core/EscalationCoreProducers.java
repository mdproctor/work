package io.casehub.work.ai.escalation.core;

import io.casehub.work.ai.repository.EscalationSummaryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class EscalationCoreProducers {

    @Produces
    @ApplicationScoped
    public EscalationSummaryCore escalationSummaryCore(EscalationSummaryStore summaryStore) {
        return new EscalationSummaryCore(summaryStore);
    }
}

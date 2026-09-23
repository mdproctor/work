package io.casehub.work.ai.escalation.core;

import io.casehub.work.ai.escalation.EscalationSummary;
import io.casehub.work.ai.repository.EscalationSummaryStore;
import java.util.List;
import java.util.UUID;

public class EscalationSummaryCore {
    private final EscalationSummaryStore summaryStore;

    public EscalationSummaryCore(EscalationSummaryStore summaryStore) {
        this.summaryStore = summaryStore;
    }

    public List<EscalationSummary> list(UUID workItemId) {
        return summaryStore.findByWorkItemId(workItemId);
    }
}

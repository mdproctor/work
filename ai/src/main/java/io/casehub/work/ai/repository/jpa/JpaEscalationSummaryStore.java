package io.casehub.work.ai.repository.jpa;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.ai.escalation.EscalationSummary;
import io.casehub.work.ai.repository.EscalationSummaryStore;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;

/**
 * Default JPA/Panache implementation of {@link EscalationSummaryStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaEscalationSummaryStore extends TenantAwareStore implements EscalationSummaryStore {

    @Override
    public EscalationSummary put(final EscalationSummary summary) {
        return withTenantQuery(() -> {
            if (summary.tenancyId == null) {
                summary.tenancyId = currentPrincipal.tenancyId();
            }
            summary.persistAndFlush();
            return summary;
        });
    }

    @Override
    public List<EscalationSummary> findByWorkItemId(final UUID workItemId) {
        return withTenantQuery(() ->
            EscalationSummary.list(
                    "workItemId = ?1 AND tenancyId = ?2 ORDER BY generatedAt DESC",
                    workItemId, currentPrincipal.tenancyId())
        );
    }
}

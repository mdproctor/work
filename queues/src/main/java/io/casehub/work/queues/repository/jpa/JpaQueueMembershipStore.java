package io.casehub.work.queues.repository.jpa;

import java.util.List;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.queues.model.WorkItemQueueMembership;
import io.casehub.work.queues.repository.QueueMembershipStore;
import io.casehub.work.runtime.repository.jpa.TenantAwareStore;

/**
 * Default JPA/Panache implementation of {@link QueueMembershipStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaQueueMembershipStore extends TenantAwareStore implements QueueMembershipStore {

    @Override
    public WorkItemQueueMembership put(final WorkItemQueueMembership membership) {
        return withTenantQuery(() -> {
            if (membership.tenancyId == null) {
                membership.tenancyId = currentPrincipal.tenancyId();
            }
            membership.persistAndFlush();
            return membership;
        });
    }

    @Override
    public List<WorkItemQueueMembership> findByWorkItemId(final UUID workItemId) {
        return withTenantQuery(() ->
            WorkItemQueueMembership.list(
                    "workItemId = ?1 AND tenancyId = ?2 ORDER BY queueName ASC",
                    workItemId, currentPrincipal.tenancyId())
        );
    }

    @Override
    public void deleteByWorkItemId(final UUID workItemId) {
        withTenantRun(() ->
            WorkItemQueueMembership.delete("workItemId = ?1 AND tenancyId = ?2",
                    workItemId, currentPrincipal.tenancyId())
        );
    }
}

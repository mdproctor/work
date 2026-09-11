package io.casehub.work.engine;

import io.casehub.platform.api.actor.ActorStateContributor;
import io.casehub.work.api.spi.TenantContextExecutor;
import io.casehub.work.api.spi.WorkItemCreator;
import io.casehub.work.api.spi.WorkItemStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class EngineAdapterBeans {

    @Produces
    public ActionGateWorkItemHandler actionGateWorkItemHandler(final WorkItemCreator workItemCreator) {
        return new ActionGateWorkItemHandler(workItemCreator);
    }

    @Produces
    public InboundWorkItemSchedulerImpl inboundWorkItemScheduler(
            final WorkItemCreator workItemCreator,
            final TenantContextExecutor tenantContextExecutor) {
        return new InboundWorkItemSchedulerImpl(workItemCreator, tenantContextExecutor);
    }

    @Produces
    public WorkActorStateContributor workActorStateContributor(final WorkItemStore workItemStore) {
        return new WorkActorStateContributor(workItemStore);
    }
}

package io.casehub.work.federation.rest.core;

import io.casehub.work.federation.FederationReceiver;
import io.casehub.work.federation.subscription.FederationSubscriptionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class FederationRestCoreProducers {

    @Produces
    @ApplicationScoped
    public FederationSubscriptionCore federationSubscriptionCore(
            FederationSubscriptionService subscriptionService) {
        return new FederationSubscriptionCore(subscriptionService);
    }

    @Produces
    @ApplicationScoped
    public FederationEventCore federationEventCore(FederationReceiver receiver,
            FederationSubscriptionService subscriptionService) {
        return new FederationEventCore(receiver, subscriptionService);
    }
}

package io.casehub.work.federation.rest.core;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.federation.subscription.FederationSubscriptionService;
import io.casehub.work.federation.subscription.SubscriptionFilter;

public class FederationSubscriptionCore {

    private final FederationSubscriptionService subscriptionService;

    public FederationSubscriptionCore(FederationSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    public Map<String, Object> register(SubscriptionRequest request) {
        byte[] hmacSecret = Base64.getDecoder().decode(request.hmacSecret());
        var filter = new SubscriptionFilter(
                request.filter().candidateGroups(),
                request.filter().candidateUsers(),
                request.tenancyId());

        var entity = subscriptionService.register(
                request.peerId(), request.callbackUrl(), request.baseUrl(),
                request.tenancyId(), filter,
                request.capabilitiesJson(), hmacSecret);

        return Map.of("id", entity.id, "status", entity.status);
    }

    public boolean deregister(UUID id) {
        return subscriptionService.deregister(id);
    }

    public Optional<Map<String, Object>> reactivate(UUID id) {
        return subscriptionService.reactivate(id);
    }
}

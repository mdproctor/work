package io.casehub.work.federation.rest.core;

import io.casehub.work.federation.FederationReceiver;
import io.casehub.work.federation.subscription.FederationSubscriptionService;

public class FederationEventCore {

    private final FederationReceiver receiver;
    private final FederationSubscriptionService subscriptionService;

    public FederationEventCore(FederationReceiver receiver,
            FederationSubscriptionService subscriptionService) {
        this.receiver = receiver;
        this.subscriptionService = subscriptionService;
    }

    public EventOutcome receiveEvent(String cloudEventJson, String signature, String peerId) {
        if (peerId == null || peerId.isEmpty()) {
            return EventOutcome.badRequest("Missing X-Federation-Peer-Id header");
        }
        if (signature == null || signature.isEmpty()) {
            return EventOutcome.unauthorized("Missing X-Federation-Signature header");
        }

        var subscriptions = subscriptionService.findActiveByPeerId(peerId);
        if (subscriptions.isEmpty()) {
            return EventOutcome.forbidden("No active subscription for peer: " + peerId);
        }

        byte[] hmacSecret = subscriptions.getFirst().hmacSecretEncrypted;
        try {
            receiver.onEvent(cloudEventJson, signature, hmacSecret);
            return EventOutcome.accepted();
        } catch (IllegalArgumentException e) {
            return EventOutcome.badRequest(e.getMessage());
        }
    }

    public record EventOutcome(int status, String message) {
        public static EventOutcome accepted() { return new EventOutcome(202, null); }
        public static EventOutcome badRequest(String msg) { return new EventOutcome(400, msg); }
        public static EventOutcome unauthorized(String msg) { return new EventOutcome(401, msg); }
        public static EventOutcome forbidden(String msg) { return new EventOutcome(403, msg); }
    }
}

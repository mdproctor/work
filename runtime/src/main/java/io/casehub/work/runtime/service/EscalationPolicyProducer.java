package io.casehub.work.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.casehub.work.api.EscalationPolicy;
import io.casehub.work.runtime.config.WorkItemsConfig;

/**
 * @deprecated Produces {@link io.casehub.work.api.EscalationPolicy} beans which are no longer injected.
 * The config keys {@code casehub.work.escalation-policy} and {@code casehub.work.claim-escalation-policy}
 * have no effect — migrate to {@link io.casehub.work.api.SlaBreachPolicy}. Removal tracked in work#215.
 */
@Deprecated
@ApplicationScoped
@SuppressWarnings("deprecation")
public class EscalationPolicyProducer {

    @Inject
    WorkItemsConfig config;

    @Inject
    NotifyEscalationPolicy notifyPolicy;

    @Inject
    AutoRejectEscalationPolicy autoRejectPolicy;

    @Inject
    ReassignEscalationPolicy reassignPolicy;

    @Produces
    @ApplicationScoped
    @ExpiryEscalation
    public EscalationPolicy expiryPolicy() {
        return switch (config.escalationPolicy()) {
            case "notify" -> notifyPolicy;
            case "auto-reject" -> autoRejectPolicy;
            case "reassign" -> reassignPolicy;
            default -> throw new IllegalArgumentException(
                    "Unknown escalation-policy: " + config.escalationPolicy()
                            + ". Valid values: notify, auto-reject, reassign");
        };
    }

    @Produces
    @ApplicationScoped
    @ClaimEscalation
    public EscalationPolicy claimPolicy() {
        return switch (config.claimEscalationPolicy()) {
            case "notify" -> notifyPolicy;
            case "reassign" -> reassignPolicy;
            default -> throw new IllegalArgumentException(
                    "Unknown claim-escalation-policy: " + config.claimEscalationPolicy()
                            + ". Valid values: notify, reassign");
        };
    }
}

package io.casehub.work.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;

import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.SlaBreachPolicy;

/**
 * Default {@link SlaBreachPolicy} — fails every breach with a diagnostic reason.
 * Applications override by declaring an {@code @ApplicationScoped SlaBreachPolicy} bean.
 */
@DefaultBean
@ApplicationScoped
class NoOpSlaBreachPolicy implements SlaBreachPolicy {

    @Override
    public BreachDecision onBreach(final SlaBreachContext context) {
        return new BreachDecision.Fail("no-sla-breach-policy-configured");
    }
}

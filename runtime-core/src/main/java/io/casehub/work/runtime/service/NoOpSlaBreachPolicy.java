package io.casehub.work.runtime.service;

import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.SlaBreachContext;
import io.casehub.work.api.spi.SlaBreachPolicy;

/**
 * Default {@link SlaBreachPolicy} — fails every breach with a diagnostic reason.
 * Applications override by declaring an {@code @ApplicationScoped SlaBreachPolicy} bean.
 */
public class NoOpSlaBreachPolicy implements SlaBreachPolicy {

    @Override
    public String id() { return "no-op"; }

    @Override
    public BreachDecision onBreach(final SlaBreachContext context) {
        return new BreachDecision.Fail("no-sla-breach-policy-configured");
    }
}

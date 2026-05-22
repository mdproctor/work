package io.casehub.work.api;

/**
 * SPI for escalation behaviour when work stalls past a deadline.
 *
 * @deprecated Replaced by {@link SlaBreachPolicy}, which returns a {@link BreachDecision}
 *             instead of void — allowing the runtime to control execution. Migrate
 *             implementations to {@link SlaBreachPolicy#onBreach(SlaBreachContext)}.
 *             Will be removed once all known consumers have migrated.
 */
@Deprecated
public interface EscalationPolicy {

    /**
     * React to a stalled work unit.
     *
     * @param event the lifecycle event that triggered escalation; never null.
     *        Use {@code event.eventType()} to determine the escalation reason,
     *        {@code event.source()} to access the concrete work unit.
     */
    void escalate(WorkLifecycleEvent event);
}

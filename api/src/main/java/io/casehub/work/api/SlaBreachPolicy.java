package io.casehub.work.api;

/**
 * SPI: decides what happens when a WorkItem breaches its SLA deadline.
 *
 * <p>The casehub-work runtime calls {@link #onBreach} when a WorkItem's
 * {@code expiresAt} or {@code claimDeadline} passes, then executes the
 * returned {@link BreachDecision}. The policy is pure — it makes a decision
 * and returns; all state mutations and CDI events are handled by the runtime.
 *
 * <p>Stateless two-tier escalation pattern — check candidateGroups to detect tier:
 * <pre>
 * public BreachDecision onBreach(SlaBreachContext ctx) {
 *     String escalationGroup = prefs(ctx).get(ESCALATION_GROUP);
 *     if (ctx.task().candidateGroups().contains(escalationGroup)) {
 *         return new Fail(prefs(ctx).get(BREACH_TERMINAL_REASON));
 *     }
 *     Duration deadline = Duration.ofHours(prefs(ctx).get(ESCALATION_HOURS));
 *     return EscalateTo.to(escalationGroup).withDeadline(deadline);
 * }
 * </pre>
 * The policy is called again when the escalated WorkItem expires — no serialization
 * of decision trees required.
 *
 * <p>Default implementation: {@code NoOpSlaBreachPolicy} returns
 * {@code Fail("no-sla-breach-policy-configured")}.
 *
 * <p>Replaces {@code EscalationPolicy}.
 */
public interface SlaBreachPolicy {

    /**
     * Decide what to do when {@code context.task()} has breached its SLA deadline.
     *
     * @param context breach context including task identity, scope, and resolved preferences
     * @return the decision to execute; never null
     */
    BreachDecision onBreach(SlaBreachContext context);
}

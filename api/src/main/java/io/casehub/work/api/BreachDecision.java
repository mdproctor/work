package io.casehub.work.api;

import java.time.Duration;
import java.util.Set;

/**
 * Decision returned by {@link SlaBreachPolicy#onBreach} describing what the
 * casehub-work runtime should do when a WorkItem breaches its SLA deadline.
 *
 * <p>Use {@link #thenOnBreach} to chain a fallback for when the primary decision
 * cannot be executed (e.g. {@link EscalateTo} with an empty group set):
 * <pre>
 * EscalateTo.to("senior-reviewers")
 *     .withDeadline(Duration.ofHours(4))
 *     .thenOnBreach(new Fail("no-escalation-target-configured"))
 * </pre>
 */
public sealed interface BreachDecision
        permits BreachDecision.Fail, BreachDecision.EscalateTo,
                BreachDecision.Extend, BreachDecision.Chained {

    /** Terminates the WorkItem with EXPIRED status and records {@code reason} as the resolution. */
    record Fail(String reason) implements BreachDecision {}

    /**
     * Reassigns the WorkItem to {@code groups} and resets the relevant deadline.
     *
     * <p>{@code deadline} applies only to {@link BreachType#COMPLETION_EXPIRED} breaches;
     * for {@link BreachType#CLAIM_EXPIRED}, the new claim deadline is always computed
     * via {@link ClaimSlaPolicy} regardless of this field.
     *
     * <p>An empty {@code groups} set is treated as "cannot execute" — the runtime
     * will fall through to the {@link Chained} fallback, or throw if not chained.
     */
    record EscalateTo(Set<String> groups, Duration deadline) implements BreachDecision {

        /**
         * Creates an {@code EscalateTo} with no deadline override.
         * The runtime uses {@code config.defaultExpiryHours()} for the new completion window.
         */
        public static EscalateTo to(final String... groups) {
            return new EscalateTo(Set.of(groups), null);
        }

        /** Returns a copy of this decision with the given deadline override. */
        public EscalateTo withDeadline(final Duration d) {
            return new EscalateTo(this.groups, d);
        }
    }

    /**
     * Extends the active deadline by {@code by} without changing WorkItem status.
     * For {@link BreachType#COMPLETION_EXPIRED}, pushes {@code expiresAt} forward.
     * For {@link BreachType#CLAIM_EXPIRED}, pushes {@code claimDeadline} forward.
     */
    record Extend(Duration by) implements BreachDecision {}

    /**
     * Tries {@code primary}; if it cannot be executed, tries {@code fallback}.
     * Build with {@link #thenOnBreach} rather than constructing directly.
     */
    record Chained(BreachDecision primary, BreachDecision fallback) implements BreachDecision {}

    /**
     * Chains a fallback decision: if this decision cannot be executed,
     * the runtime will execute {@code fallback} instead.
     */
    default BreachDecision thenOnBreach(final BreachDecision fallback) {
        return new Chained(this, fallback);
    }
}

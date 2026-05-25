package io.casehub.work.runtime.event;

import io.casehub.work.api.BreachDecision;
import io.casehub.work.api.SlaBreachContext;

/**
 * CDI event fired by {@link io.casehub.work.runtime.service.ExpiryLifecycleService}
 * after executing a {@link BreachDecision}.
 *
 * <p>{@code decision} is the <em>leaf</em> that actually executed — never a
 * {@link BreachDecision.Chained} wrapper. Observers can pattern-match directly:
 * <pre>
 * void onBreach(@Observes SlaBreachEvent e) {
 *     switch (e.decision()) {
 *         case Fail f    -> notify("task expired: " + f.reason());
 *         case EscalateTo et -> notify("task escalated to " + et.groups());
 *         case Extend ex -> {}  // silent extension, no notification
 *         case Chained c -> {}  // never fired — leaf is always resolved
 *     }
 * }
 * </pre>
 */
public record SlaBreachEvent(SlaBreachContext context, BreachDecision decision) {
}

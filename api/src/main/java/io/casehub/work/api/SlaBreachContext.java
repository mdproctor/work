package io.casehub.work.api;

import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.Preferences;

/**
 * Context passed to {@link SlaBreachPolicy#onBreach} describing the breach event.
 *
 * <p>{@code scope} is {@link Path#root()} when the WorkItem has no assigned scope —
 * in this case {@code preferences} reflects org-level defaults only. If the policy
 * implementation knows a richer scope (e.g. by parsing {@code task().callerRef()}),
 * it may resolve preferences independently and ignore this field. See engine#330.
 *
 * <p>Both {@code scope} and {@code preferences} may be null in unit test contexts where
 * scope is irrelevant to the behavior under test; production code always provides
 * {@code Path.root()} as the minimum scope and a resolved {@code Preferences} instance.
 */
public record SlaBreachContext(
        BreachType breachType,
        BreachedTask task,
        Path scope,
        Preferences preferences) {
}

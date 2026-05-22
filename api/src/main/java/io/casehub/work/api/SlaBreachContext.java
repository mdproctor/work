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
 * <p>In unit tests, construct with {@code Path.root()} and {@code MapPreferences.empty()}
 * — both are zero-dependency and require no mocking.
 */
public record SlaBreachContext(
        BreachType breachType,
        BreachedTask task,
        Path scope,
        Preferences preferences) {
}

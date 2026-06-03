package io.casehub.work.api;

import java.util.Locale;

import io.casehub.platform.api.preferences.PreferenceKey;
import io.casehub.platform.api.preferences.SingleValuePreference;

/**
 * Determines where a delegated {@link io.casehub.work.runtime.model.WorkItem} returns
 * when the delegatee declines.
 *
 * <p>Configure the scope-level default via {@link #KEY} through
 * {@link io.casehub.platform.api.preferences.PreferenceProvider}.
 * Override per WorkItem at delegation time via
 * {@link io.casehub.work.runtime.api.DelegateRequest#declineTarget()}.
 */
public enum DeclineTarget implements SingleValuePreference {

    /** Item returns to the general pool with candidateGroups unchanged. Default. */
    POOL,

    /** Item returns to the actor who delegated it (last entry in delegationChain). */
    DELEGATOR;

    /**
     * Preference key for the scope-level default.
     * Qualified name: {@code casehub.work.delegation.decline-target}.
     * Default: {@link #POOL}.
     */
    public static final PreferenceKey<DeclineTarget> KEY =
            new PreferenceKey<>("casehub.work.delegation", "decline-target", POOL,
                    s -> DeclineTarget.valueOf(s.toUpperCase(Locale.ROOT)));
}

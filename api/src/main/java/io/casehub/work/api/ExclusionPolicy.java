package io.casehub.work.api;

/**
 * SPI for evaluating whether a user is excluded from acting on a WorkItem.
 *
 * <p>
 * The default implementation ({@code CommaSeparatedExclusionPolicy}) checks
 * whether {@code userId} appears in a comma-separated {@code excludedUsers} string.
 * Custom implementations can plug in LDAP group membership, role-based rules,
 * or time-window logic (see casehubio/work#185).
 *
 * <p>
 * Implementations must return {@link PolicyDecision#ALLOW} (not {@code null}) when
 * the user is not excluded. The reason on a denied decision flows directly into
 * audit entries and exception messages — make it human-readable and specific.
 */
public interface ExclusionPolicy {

    /**
     * Evaluates whether {@code userId} is excluded.
     *
     * @param userId the identity to check; must not be null
     * @param excludedUsers the policy data (e.g. comma-separated IDs); null or blank means no exclusion
     * @return {@link PolicyDecision#ALLOW} if permitted; a denied {@link PolicyDecision} carrying
     *         a human-readable reason if excluded
     */
    PolicyDecision check(String userId, String excludedUsers);
}

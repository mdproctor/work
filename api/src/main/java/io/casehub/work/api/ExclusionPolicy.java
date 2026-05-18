package io.casehub.work.api;

/**
 * SPI for evaluating whether a user is excluded from acting on a WorkItem.
 *
 * <p>
 * The default implementation ({@code CommaSeparatedExclusionPolicy}) checks
 * whether {@code userId} appears in a comma-separated {@code excludedUsers} string.
 * Custom implementations can plug in LDAP group membership, role-based rules,
 * or time-window logic (see casehubio/work#185).
 */
public interface ExclusionPolicy {

    /**
     * Returns {@code true} if {@code userId} is excluded.
     *
     * @param userId the identity to check; must not be null
     * @param excludedUsers comma-separated user IDs; null or blank means no exclusion
     * @return {@code true} if the user is excluded, {@code false} otherwise
     */
    boolean isExcluded(String userId, String excludedUsers);
}

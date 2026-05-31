package io.casehub.work.api;

/**
 * SPI for evaluating whether a user is excluded from acting on a WorkItem.
 *
 * <p>
 * The default implementation ({@link CommaSeparatedExclusionPolicy}) checks whether
 * {@code userId} appears in a comma-separated {@code excludedUsers} string. Custom
 * implementations can plug in time-window logic, role-based rules, or any other
 * conflict-of-interest policy.
 *
 * <p><b>Activation:</b> declare {@code @Alternative @Priority(1) @ApplicationScoped}
 * on your implementation — CDI replaces {@link CommaSeparatedExclusionPolicy} globally.
 *
 * <p><b>{@code excludedUsers} format:</b> the SPI owns the encoding — it is opaque to
 * the platform. The default uses plain comma-separated actor IDs; custom implementations
 * may encode richer metadata (e.g. {@code userId:YYYY-MM-DD} for expiring exclusions).
 * Implementations that replace the default <em>must</em> handle or reject the plain CSV
 * format already stored in existing WorkItems.
 *
 * <p><b>Group-level exclusion:</b> handled separately by
 * {@code TemplateExpander + GroupMembershipProvider} — group members are resolved to
 * actor IDs at WorkItem creation time and stored in {@code excludedUsers}.
 * {@code ExclusionPolicy.check()} operates on individual actor IDs at claim/delegate time.
 *
 * <p><b>Service-tier enforcement:</b> denials are audited via
 * {@code BlockedAttemptAuditService} in a {@code REQUIRES_NEW} transaction — the
 * {@code reason} string from the denied {@link PolicyDecision} flows into the audit
 * entry detail field.
 *
 * <p>
 * Implementations must return {@link PolicyDecision#ALLOW} (not {@code null}) when
 * the user is not excluded. The reason on a denied decision flows directly into
 * audit entries and exception messages — make it human-readable and specific.
 *
 * @see CommaSeparatedExclusionPolicy
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

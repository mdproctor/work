package io.casehub.work.api;

/**
 * Result of an {@link ExclusionPolicy#check} evaluation.
 *
 * <p>Carries whether the check denied the operation and, if so, the reason
 * supplied by the policy implementation. The reason is policy-specific and
 * intended for audit entries and exception messages — the caller should not
 * parse it programmatically.
 *
 * <p>Use {@link #ALLOW} for the common non-denied case to avoid allocation.
 */
public record PolicyDecision(boolean denied, String reason) {

    public PolicyDecision {
        if (denied && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A denied PolicyDecision must carry a non-blank reason");
        }
    }

    /** Shared instance for the non-denied case — reason is {@code null}. */
    public static final PolicyDecision ALLOW = new PolicyDecision(false, null);

    /** Returns a denied decision carrying the supplied reason. */
    public static PolicyDecision deny(final String reason) {
        return new PolicyDecision(true, reason);
    }

    /** Convenience inverse of {@link #denied()}. */
    public boolean allowed() {
        return !denied;
    }
}

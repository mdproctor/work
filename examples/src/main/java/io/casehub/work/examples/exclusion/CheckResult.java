package io.casehub.work.examples.exclusion;

/**
 * Result of a single {@link ExpiringExclusionPolicy#check(String, String)} call in the demo.
 *
 * @param actor the user identity checked
 * @param exclusionData the {@code excludedUsers} value passed to {@code check()}; {@code null} means no exclusion configured
 * @param denied {@code true} if the check returned a denied decision
 * @param reason denial reason; {@code null} when allowed
 */
public record CheckResult(String actor, String exclusionData, boolean denied, String reason) {
}

package io.casehub.work.examples.exclusion;

import java.util.List;

/**
 * Response returned by the exclusion policy demo endpoint.
 *
 * @param scenario identifier for this demonstration
 * @param results one {@link CheckResult} per check performed, in order
 */
public record ExclusionPolicyDemoResponse(String scenario, List<CheckResult> results) {
}

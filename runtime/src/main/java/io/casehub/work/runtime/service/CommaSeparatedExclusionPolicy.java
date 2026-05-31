package io.casehub.work.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.api.ExclusionPolicy;
import io.casehub.work.api.PolicyDecision;
import io.quarkus.arc.DefaultBean;

/**
 * Default {@link ExclusionPolicy} — checks whether {@code userId} appears in a
 * comma-separated {@code excludedUsers} string. Case-sensitive, whitespace-trimmed
 * per token. Activated via {@code @DefaultBean} so CDI {@code @Alternative}
 * implementations take precedence when present.
 *
 * <p>To replace this with custom logic, declare
 * {@code @Alternative @Priority(1) @ApplicationScoped} on your implementation.
 */
@ApplicationScoped
@DefaultBean
public class CommaSeparatedExclusionPolicy implements ExclusionPolicy {

    @Override
    public PolicyDecision check(final String userId, final String excludedUsers) {
        if (excludedUsers == null || excludedUsers.isBlank()) {
            return PolicyDecision.ALLOW;
        }
        for (final String id : excludedUsers.split(",")) {
            if (id.trim().equals(userId)) {
                return PolicyDecision.deny("user '" + userId + "' in comma-separated exclusion list");
            }
        }
        return PolicyDecision.ALLOW;
    }
}

package io.casehub.work.runtime.service;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.quarkus.arc.DefaultBean;

/**
 * Default {@link GroupMembershipProvider} — returns an empty set for every group.
 * Activated via {@code @DefaultBean}; replace with {@code @Alternative @Priority(1)}
 * to connect a real directory (LDAP, Keycloak, SCIM, etc.).
 *
 * <p>When this provider is active, {@code excludedGroups} on templates resolve to no actor IDs.
 * {@link TemplateExpander} logs a WARN on every instantiation of a template that has
 * {@code excludedGroups} set — this is intentional: it signals to operators that groups
 * are configured but no real provider is wired. Wire an {@code @Alternative @Priority(1)}
 * implementation to resolve actual group members.
 */
@ApplicationScoped
@DefaultBean
public class NoOpGroupMembershipProvider implements GroupMembershipProvider {

    @Override
    public Set<GroupMember> membersOf(final String groupName) {
        return Set.of();
    }
}

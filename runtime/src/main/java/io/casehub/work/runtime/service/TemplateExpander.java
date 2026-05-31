package io.casehub.work.runtime.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.casehub.work.runtime.model.WorkItemTemplate;

/**
 * Expands {@link WorkItemTemplate#excludedGroups} to actor IDs at WorkItem creation time.
 * Merged result is stored in {@code excludedUsers} — no {@code excluded_groups} column on {@code work_item}.
 */
@ApplicationScoped
public class TemplateExpander {

    private static final Logger LOG = Logger.getLogger(TemplateExpander.class);

    @Inject
    GroupMembershipProvider groupMembershipProvider;

    /**
     * Returns the merged excluded-users string for the given template.
     * Calls {@link GroupMembershipProvider#membersOf} for each group in
     * {@link WorkItemTemplate#excludedGroups} and merges with {@link WorkItemTemplate#excludedUsers}.
     * Logs a warning when groups are configured but no new actor IDs are resolved.
     */
    public String expandExcludedUsers(final WorkItemTemplate template) {
        final String merged = mergeGroupsIntoExcludedUsers(
                template.excludedGroups, template.excludedUsers, groupMembershipProvider);
        if (template.excludedGroups != null && !template.excludedGroups.isBlank()) {
            final int before = countIds(template.excludedUsers);
            final int after = countIds(merged);
            if (after == before) {
                LOG.warnf("excludedGroups='%s' on template '%s' resolved to no additional actors — "
                        + "group may be empty or GroupMembershipProvider not configured",
                        template.excludedGroups, template.id);
            }
        }
        return merged;
    }

    /**
     * Pure static helper — testable without CDI. Resolves each group in {@code excludedGroups}
     * via {@code provider} and merges actor IDs with {@code excludedUsers}.
     */
    static String mergeGroupsIntoExcludedUsers(final String excludedGroups,
                                                final String excludedUsers,
                                                final GroupMembershipProvider provider) {
        if (excludedGroups == null || excludedGroups.isBlank()) {
            return excludedUsers;
        }
        final Set<String> ids = new LinkedHashSet<>();
        if (excludedUsers != null) {
            Arrays.stream(excludedUsers.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(ids::add);
        }
        for (final String group : excludedGroups.split(",")) {
            final String g = group.trim();
            if (!g.isEmpty()) {
                provider.membersOf(g).forEach(m -> ids.add(m.actorId()));
            }
        }
        return ids.isEmpty() ? null : String.join(",", ids);
    }

    private static int countIds(final String csv) {
        if (csv == null || csv.isBlank()) return 0;
        return (int) Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).count();
    }
}

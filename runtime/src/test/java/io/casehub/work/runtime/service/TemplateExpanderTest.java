package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.platform.api.identity.GroupMember;
import org.junit.jupiter.api.Test;

import java.util.Set;

class TemplateExpanderTest {

    // No-op provider: every group is unknown
    private static final io.casehub.platform.api.identity.GroupMembershipProvider EMPTY =
            groupName -> Set.of();

    // Provider that knows "legal-team" → {alice, bob}
    private static final io.casehub.platform.api.identity.GroupMembershipProvider LEGAL_TEAM =
            groupName -> "legal-team".equals(groupName)
                    ? Set.of(new GroupMember("alice", "Alice"), new GroupMember("bob", "Bob"))
                    : Set.of();

    @Test
    void merge_nullGroups_returnsExcludedUsersUnchanged() {
        assertThat(TemplateExpander.mergeGroupsIntoExcludedUsers(null, "alice,bob", EMPTY))
                .isEqualTo("alice,bob");
    }

    @Test
    void merge_blankGroups_returnsExcludedUsersUnchanged() {
        assertThat(TemplateExpander.mergeGroupsIntoExcludedUsers("  ", "alice", EMPTY))
                .isEqualTo("alice");
    }

    @Test
    void merge_nullGroupsAndNullUsers_returnsNull() {
        assertThat(TemplateExpander.mergeGroupsIntoExcludedUsers(null, null, EMPTY)).isNull();
    }

    @Test
    void merge_groupResolvesToMembers_membersAddedToUsers() {
        String result = TemplateExpander.mergeGroupsIntoExcludedUsers("legal-team", null, LEGAL_TEAM);
        assertThat(result).contains("alice");
        assertThat(result).contains("bob");
    }

    @Test
    void merge_existingUsersPreserved_groupMembersAdded() {
        String result = TemplateExpander.mergeGroupsIntoExcludedUsers("legal-team", "carol", LEGAL_TEAM);
        assertThat(result).contains("carol");
        assertThat(result).contains("alice");
        assertThat(result).contains("bob");
    }

    @Test
    void merge_groupMemberAlreadyInExcludedUsers_noDuplicate() {
        // alice is both in excludedUsers and in legal-team
        String result = TemplateExpander.mergeGroupsIntoExcludedUsers("legal-team", "alice", LEGAL_TEAM);
        long aliceCount = java.util.Arrays.stream(result.split(","))
                .filter("alice"::equals).count();
        assertThat(aliceCount).isEqualTo(1);
    }

    @Test
    void merge_unknownGroup_returnsExcludedUsersUnchanged() {
        String result = TemplateExpander.mergeGroupsIntoExcludedUsers("unknown-group", "alice", EMPTY);
        assertThat(result).isEqualTo("alice");
    }

    @Test
    void merge_unknownGroupAndNullUsers_returnsNull() {
        assertThat(TemplateExpander.mergeGroupsIntoExcludedUsers("unknown-group", null, EMPTY)).isNull();
    }

    @Test
    void merge_multipleGroupsCsv_allExpanded() {
        io.casehub.platform.api.identity.GroupMembershipProvider provider = groupName ->
                switch (groupName) {
                    case "team-a" -> Set.of(new GroupMember("alice", "Alice"));
                    case "team-b" -> Set.of(new GroupMember("bob", "Bob"));
                    default -> Set.of();
                };
        String result = TemplateExpander.mergeGroupsIntoExcludedUsers("team-a,team-b", null, provider);
        assertThat(result).contains("alice");
        assertThat(result).contains("bob");
    }
}

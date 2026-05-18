package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Contract test for ExclusionPolicy SPI. Refs #171.
 * Using an anonymous implementation proves the interface contract exists independently of any concrete class.
 */
class ExclusionPolicyTest {

    // Anonymous implementation — compiler error here means ExclusionPolicy doesn't exist yet
    private final ExclusionPolicy policy = (userId, excludedUsers) -> {
        if (excludedUsers == null || excludedUsers.isBlank()) return false;
        for (final String id : excludedUsers.split(",")) {
            if (id.trim().equals(userId)) return true;
        }
        return false;
    };

    @Test
    void isExcluded_returnsTrue_whenUserIdInList() {
        assertThat(policy.isExcluded("alice", "alice,bob")).isTrue();
    }

    @Test
    void isExcluded_returnsFalse_whenUserIdNotInList() {
        assertThat(policy.isExcluded("carol", "alice,bob")).isFalse();
    }

    @Test
    void isExcluded_returnsFalse_whenExcludedUsersNull() {
        assertThat(policy.isExcluded("alice", null)).isFalse();
    }

    @Test
    void isExcluded_returnsFalse_whenExcludedUsersBlank() {
        assertThat(policy.isExcluded("alice", "")).isFalse();
    }

    @Test
    void isExcluded_trims_whitespace() {
        assertThat(policy.isExcluded("alice", " alice , bob ")).isTrue();
    }
}

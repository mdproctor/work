package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Contract test for ExclusionPolicy SPI. Refs #171, #186.
 * Using an anonymous implementation proves the interface contract exists independently of any concrete class.
 */
class ExclusionPolicyTest {

    // Anonymous implementation — compiler error here means ExclusionPolicy.check() doesn't exist yet
    private final ExclusionPolicy policy = (userId, excludedUsers) -> {
        if (excludedUsers == null || excludedUsers.isBlank()) return PolicyDecision.ALLOW;
        for (final String id : excludedUsers.split(",")) {
            if (id.trim().equals(userId)) return PolicyDecision.deny("user '" + userId + "' in list");
        }
        return PolicyDecision.ALLOW;
    };

    @Test
    void check_returnsDenied_whenUserIdInList() {
        assertThat(policy.check("alice", "alice,bob").denied()).isTrue();
    }

    @Test
    void check_returnsAllow_whenUserIdNotInList() {
        assertThat(policy.check("carol", "alice,bob").allowed()).isTrue();
    }

    @Test
    void check_returnsAllow_whenExcludedUsersNull() {
        assertThat(policy.check("alice", null).allowed()).isTrue();
    }

    @Test
    void check_returnsAllow_whenExcludedUsersBlank() {
        assertThat(policy.check("alice", "").allowed()).isTrue();
    }

    @Test
    void check_trims_whitespace() {
        assertThat(policy.check("alice", " alice , bob ").denied()).isTrue();
    }

    @Test
    void check_denied_carriesReasonContainingUserId() {
        final PolicyDecision decision = policy.check("alice", "alice,bob");
        assertThat(decision.reason()).contains("alice");
    }
}

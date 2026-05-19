package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.work.api.PolicyDecision;
import org.junit.jupiter.api.Test;

class CommaSeparatedExclusionPolicyTest {

    private final CommaSeparatedExclusionPolicy policy = new CommaSeparatedExclusionPolicy();

    @Test
    void check_returnsAllow_whenNotInList() {
        assertThat(policy.check("carol", "alice,bob").allowed()).isTrue();
    }

    @Test
    void check_returnsDenied_whenInList() {
        assertThat(policy.check("alice", "alice,bob").denied()).isTrue();
    }

    @Test
    void check_returnsDenied_withReasonContainingUserId() {
        final PolicyDecision decision = policy.check("alice", "alice,bob");
        assertThat(decision.reason()).contains("alice");
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
    void check_allow_hasNullReason() {
        assertThat(policy.check("carol", "alice,bob").reason()).isNull();
    }
}

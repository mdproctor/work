package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PolicyDecisionTest {

    @Test
    void allow_isNotDenied() {
        assertThat(PolicyDecision.ALLOW.denied()).isFalse();
    }

    @Test
    void allow_isAllowed() {
        assertThat(PolicyDecision.ALLOW.allowed()).isTrue();
    }

    @Test
    void allow_hasNullReason() {
        assertThat(PolicyDecision.ALLOW.reason()).isNull();
    }

    @Test
    void deny_isDenied() {
        assertThat(PolicyDecision.deny("reason").denied()).isTrue();
    }

    @Test
    void deny_isNotAllowed() {
        assertThat(PolicyDecision.deny("reason").allowed()).isFalse();
    }

    @Test
    void deny_carriesReason() {
        assertThat(PolicyDecision.deny("user 'alice' in exclusion list").reason())
                .isEqualTo("user 'alice' in exclusion list");
    }
}

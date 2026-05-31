package io.casehub.work.examples.exclusion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.PolicyDecision;

class ExpiringExclusionPolicyTest {

    // Pin clock to 2026-06-01 — all date comparisons are relative to this
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.now(FIXED_CLOCK); // 2026-06-01

    private final ExpiringExclusionPolicy policy = new ExpiringExclusionPolicy(FIXED_CLOCK);

    @Test
    void check_futureDate_denied() {
        final PolicyDecision d = policy.check("alice", "alice:2099-01-01");
        assertThat(d.denied()).isTrue();
        assertThat(d.reason()).contains("alice").contains("2099-01-01").contains("cooling-off");
    }

    @Test
    void check_pastDate_allowed() {
        assertThat(policy.check("alice", "alice:2020-01-01").allowed()).isTrue();
    }

    @Test
    void check_exactBoundary_todayEqualsExpiry_allowed() {
        // today == expiry: not strictly before, so ALLOW
        final String todayStr = TODAY.toString(); // "2026-06-01"
        assertThat(policy.check("alice", "alice:" + todayStr).allowed()).isTrue();
    }

    @Test
    void check_nonListedUser_allowed() {
        assertThat(policy.check("bob", "alice:2099-01-01").allowed()).isTrue();
    }

    @Test
    void check_nullExclusion_allowed() {
        assertThat(policy.check("alice", null).allowed()).isTrue();
    }

    @Test
    void check_blankExclusion_allowed() {
        assertThat(policy.check("alice", "").allowed()).isTrue();
    }

    @Test
    void check_plainId_denied_permanently() {
        final PolicyDecision d = policy.check("alice", "alice");
        assertThat(d.denied()).isTrue();
        assertThat(d.reason()).contains("alice").doesNotContain("until");
    }

    @Test
    void check_invalidDateToken_denied_permanently() {
        final PolicyDecision d = policy.check("alice", "alice:not-a-date");
        assertThat(d.denied()).isTrue();
        assertThat(d.reason()).contains("invalid expiry format");
    }

    @Test
    void check_multipleTokens_mostRestrictiveWins() {
        // expired token followed by active token: DENY because active token is present
        final PolicyDecision d = policy.check("alice", "alice:2020-01-01,alice:2099-01-01");
        assertThat(d.denied()).isTrue();
        assertThat(d.reason()).contains("2099-01-01");
    }

    @Test
    void check_plainId_asStoredByTemplateExpander_deniedPermanently() {
        // TemplateExpander resolves groups and stores plain CSV; ExpiringExclusionPolicy
        // treats no-colon tokens as permanently excluded — correct semantic for existing data
        final PolicyDecision d = policy.check("alice", "alice,bob");
        assertThat(d.denied()).isTrue();
        assertThat(d.reason()).contains("alice").doesNotContain("until");
    }

    @Test
    void check_whitespaceAroundTokens_handled() {
        assertThat(policy.check("alice", " alice:2099-01-01 ").denied()).isTrue();
    }
}

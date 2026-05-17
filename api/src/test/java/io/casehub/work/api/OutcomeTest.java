package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutcomeTest {

    @Test
    void outcome_nameAndDisplayName() {
        final Outcome outcome = new Outcome("approved", "Approved");
        assertThat(outcome.name()).isEqualTo("approved");
        assertThat(outcome.displayName()).isEqualTo("Approved");
    }

    @Test
    void outcome_nameOnly_nullDisplayName() {
        final Outcome outcome = new Outcome("rejected", null);
        assertThat(outcome.name()).isEqualTo("rejected");
        assertThat(outcome.displayName()).isNull();
    }
}

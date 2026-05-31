package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoOpGroupMembershipProviderTest {

    private final NoOpGroupMembershipProvider provider = new NoOpGroupMembershipProvider();

    @Test
    void membersOf_anyGroup_returnsEmptySet() {
        assertThat(provider.membersOf("legal-team")).isEmpty();
        assertThat(provider.membersOf("anything")).isEmpty();
        assertThat(provider.membersOf("")).isEmpty();
    }
}

package io.casehub.work.core.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.Capability;

class PermissiveCapabilityRegistryTest {

    private final PermissiveCapabilityRegistry registry = new PermissiveCapabilityRegistry();

    @Test
    void capabilities_returnsEmptySet() {
        assertThat(registry.capabilities()).isEmpty();
    }

    @Test
    void isKnown_returnsFalse_forAnyCapability() {
        assertThat(registry.isKnown(Capability.of("legal-review"))).isFalse();
    }
}

package io.casehub.work.core.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.Capability;
import io.casehub.work.api.WorkCapabilities;

class WorkCapabilitiesRegistryTest {

    private final WorkCapabilitiesRegistry registry = new WorkCapabilitiesRegistry();

    @Test
    void capabilities_containsAllWorkCapabilitiesConstants() {
        assertThat(registry.capabilities())
                .contains(
                        WorkCapabilities.LEGAL_REVIEW,
                        WorkCapabilities.CONTRACT_ANALYSIS,
                        WorkCapabilities.CONTRACT_REVIEW,
                        WorkCapabilities.NDA,
                        WorkCapabilities.IP_LICENSING,
                        WorkCapabilities.COMPLIANCE,
                        WorkCapabilities.GDPR);
    }

    @Test
    void isKnown_trueForKnownConstant() {
        assertThat(registry.isKnown(WorkCapabilities.LEGAL_REVIEW)).isTrue();
    }

    @Test
    void isKnown_falseForUnknownCapability() {
        assertThat(registry.isKnown(Capability.of("unknown-cap"))).isFalse();
    }
}

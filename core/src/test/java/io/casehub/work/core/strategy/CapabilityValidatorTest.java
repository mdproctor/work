package io.casehub.work.core.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.Capability;
import io.casehub.work.api.CapabilityRegistry;
import io.casehub.work.api.UnknownCapabilityException;
import io.casehub.work.api.ValidationMode;
import io.casehub.work.api.WorkCapabilities;

class CapabilityValidatorTest {

    private final CapabilityRegistry registryWithLegal =
            () -> Set.of(WorkCapabilities.LEGAL_REVIEW);

    private CapabilityValidator validatorWith(ValidationMode mode, CapabilityRegistry registry) {
        return new CapabilityValidator(mode, registry);
    }

    @Test
    void permissive_alwaysPasses_evenForUnknownCapability() {
        final CapabilityValidator v = validatorWith(ValidationMode.PERMISSIVE, registryWithLegal);
        assertThatNoException().isThrownBy(
                () -> v.validate(Set.of(Capability.of("completely-unknown"))));
    }

    @Test
    void strict_passes_whenAllCapabilitiesKnown() {
        final CapabilityValidator v = validatorWith(ValidationMode.STRICT, registryWithLegal);
        assertThatNoException().isThrownBy(
                () -> v.validate(Set.of(WorkCapabilities.LEGAL_REVIEW)));
    }

    @Test
    void strict_throws_whenAnyCapabilityUnknown() {
        final CapabilityValidator v = validatorWith(ValidationMode.STRICT, registryWithLegal);
        final Capability unknown = Capability.of("audit-sign");
        assertThatThrownBy(() -> v.validate(Set.of(WorkCapabilities.LEGAL_REVIEW, unknown)))
                .isInstanceOf(UnknownCapabilityException.class)
                .satisfies(ex -> assertThat(((UnknownCapabilityException) ex).unknownIds())
                        .containsExactly("audit-sign"));
    }

    @Test
    void strict_throwsWithAllUnknownValues_whenMultipleUnknown() {
        final CapabilityValidator v = validatorWith(ValidationMode.STRICT, registryWithLegal);
        assertThatThrownBy(() -> v.validate(
                Set.of(Capability.of("audit-sign"), Capability.of("risk-assess"))))
                .isInstanceOf(UnknownCapabilityException.class)
                .satisfies(ex -> assertThat(((UnknownCapabilityException) ex).unknownIds())
                        .containsExactlyInAnyOrder("audit-sign", "risk-assess"));
    }

    @Test
    void warn_doesNotThrow_forUnknownCapability() {
        final CapabilityValidator v = validatorWith(ValidationMode.WARN, registryWithLegal);
        assertThatNoException().isThrownBy(
                () -> v.validate(Set.of(Capability.of("audit-sign"))));
    }

    @Test
    void emptySet_alwaysPasses_inAnyMode() {
        for (ValidationMode mode : ValidationMode.values()) {
            assertThatNoException().isThrownBy(
                    () -> validatorWith(mode, registryWithLegal).validate(Set.of()));
        }
    }
}

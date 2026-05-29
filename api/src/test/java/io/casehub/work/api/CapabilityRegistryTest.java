package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class CapabilityRegistryTest {

    @Test
    void isKnown_delegatesToCapabilities_whenNotOverridden() {
        // Subclass overrides only capabilities() — isKnown() must reflect it.
        // Guard against GE-20260511-a5f47d: static-field-backed isKnown() that bypasses capabilities().
        final Capability legal = Capability.of("legal-review");
        final Capability audit = Capability.of("audit-sign");

        final CapabilityRegistry registry = () -> Set.of(legal);

        assertThat(registry.isKnown(legal)).isTrue();
        assertThat(registry.isKnown(audit)).isFalse();
    }

    @Test
    void isKnown_canBeOverriddenForEfficiency() {
        // Implementors may override isKnown() for DB-backed lookups — must still work correctly.
        final Capability known = Capability.of("legal-review");
        final CapabilityRegistry registry = new CapabilityRegistry() {
            @Override public Set<Capability> capabilities() { return Set.of(known); }
            @Override public boolean isKnown(Capability tag) { return tag.equals(known); }
        };

        assertThat(registry.isKnown(known)).isTrue();
        assertThat(registry.isKnown(Capability.of("other"))).isFalse();
    }
}

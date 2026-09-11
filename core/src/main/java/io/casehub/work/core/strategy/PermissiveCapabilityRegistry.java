package io.casehub.work.core.strategy;

import java.util.Set;

import io.casehub.work.api.Capability;
import io.casehub.work.api.spi.CapabilityRegistry;

/**
 * Default CapabilityRegistry — no enforcement.
 * Displaced automatically by any application-scoped {@link CapabilityRegistry} in the deploying app.
 */
public class PermissiveCapabilityRegistry implements CapabilityRegistry {

    @Override
    public Set<Capability> capabilities() {
        return Set.of();
    }
}

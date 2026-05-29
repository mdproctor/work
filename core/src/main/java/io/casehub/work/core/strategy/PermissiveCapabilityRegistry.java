package io.casehub.work.core.strategy;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;

import io.casehub.work.api.Capability;
import io.casehub.work.api.CapabilityRegistry;

/**
 * Default CapabilityRegistry — no enforcement.
 * Displaced automatically by any application-scoped {@link CapabilityRegistry} in the deploying app.
 */
@DefaultBean
@ApplicationScoped
public class PermissiveCapabilityRegistry implements CapabilityRegistry {

    @Override
    public Set<Capability> capabilities() {
        return Set.of();
    }
}

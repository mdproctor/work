package io.casehub.work.api;

import java.util.Set;

/**
 * SPI for known capability vocabulary.
 *
 * <p>The default implementation ({@link io.casehub.work.core.strategy.PermissiveCapabilityRegistry})
 * returns an empty set — no enforcement. Deploy an {@code @ApplicationScoped @Alternative @Priority(1)}
 * implementation to govern capability vocabulary.
 *
 * <p>Validation mode (STRICT / WARN / PERMISSIVE) is configured via
 * {@code casehub.work.capability-validation} — it is a deployment concern, not a registry concern.
 */
public interface CapabilityRegistry {

    /** Known capability vocabulary. Empty set means unmanaged (no enforcement). */
    Set<Capability> capabilities();

    /**
     * Returns true if {@code tag} is a known capability.
     *
     * <p>Matching is exact and case-sensitive. The {@link Capability} constructor enforces
     * lowercase kebab-case, so format violations are rejected before reaching this method.
     *
     * <p>Override when direct lookup is more efficient than loading {@link #capabilities()}
     * (e.g. a database-backed registry with {@code SELECT EXISTS}). Never back this method
     * with a static field — that silently bypasses subclass capability sets (GE-20260511-a5f47d).
     */
    default boolean isKnown(Capability tag) {
        return capabilities().contains(tag);
    }
}

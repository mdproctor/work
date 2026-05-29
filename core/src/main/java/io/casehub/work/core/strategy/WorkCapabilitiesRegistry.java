package io.casehub.work.core.strategy;

import java.util.Set;

import io.casehub.work.api.Capability;
import io.casehub.work.api.CapabilityRegistry;
import io.casehub.work.api.WorkCapabilities;

/**
 * Optional CapabilityRegistry backed by {@link WorkCapabilities} constants.
 *
 * <p>Not a CDI bean — instantiated by teams who want platform-vocabulary enforcement:
 * <pre>{@code
 * @ApplicationScoped @Alternative @Priority(1)
 * public class MyRegistry extends WorkCapabilitiesRegistry { }
 * }</pre>
 *
 * Or extend and override {@link #capabilities()} to add domain-specific capabilities.
 */
public class WorkCapabilitiesRegistry implements CapabilityRegistry {

    private static final Set<Capability> KNOWN = Set.of(
            WorkCapabilities.LEGAL_REVIEW,
            WorkCapabilities.CONTRACT_ANALYSIS,
            WorkCapabilities.CONTRACT_REVIEW,
            WorkCapabilities.NDA,
            WorkCapabilities.IP_LICENSING,
            WorkCapabilities.COMPLIANCE,
            WorkCapabilities.GDPR);

    @Override
    public Set<Capability> capabilities() {
        return KNOWN;
    }
}

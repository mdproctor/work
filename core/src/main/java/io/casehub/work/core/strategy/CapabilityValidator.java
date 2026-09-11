package io.casehub.work.core.strategy;

import io.casehub.work.api.Capability;
import io.casehub.work.api.UnknownCapabilityException;
import io.casehub.work.api.ValidationMode;
import io.casehub.work.api.spi.CapabilityRegistry;

import java.util.List;
import java.util.Set;

public class CapabilityValidator {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(CapabilityValidator.class.getName());

    private final ValidationMode     validationMode;
    private final CapabilityRegistry registry;

    public CapabilityValidator(ValidationMode validationMode, CapabilityRegistry registry) {
        this.validationMode = validationMode;
        this.registry       = registry;
    }

    public void validate(Set<Capability> capabilities) {
        if (validationMode == ValidationMode.PERMISSIVE || capabilities.isEmpty()) {
            return;
        }
        final List<Capability> unknown = capabilities.stream()
                                                     .filter(c -> !registry.isKnown(c))
                                                     .toList();
        if (unknown.isEmpty()) {
            return;
        }
        if (validationMode == ValidationMode.STRICT) {
            throw new UnknownCapabilityException(unknown);
        } else {
            LOG.warning("WorkItem references unregistered capabilities: " +
                        unknown.stream().map(Capability::id).toList());
        }
    }
}

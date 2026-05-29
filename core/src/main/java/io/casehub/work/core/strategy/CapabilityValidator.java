package io.casehub.work.core.strategy;

import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.casehub.work.api.Capability;
import io.casehub.work.api.CapabilityRegistry;
import io.casehub.work.api.UnknownCapabilityException;
import io.casehub.work.api.ValidationMode;

@ApplicationScoped
public class CapabilityValidator {

    private static final Logger LOG = Logger.getLogger(CapabilityValidator.class);

    /**
     * Injected via MicroProfile Config — the value is sourced from
     * {@code casehub.work.capability-validation} which is declared in
     * {@code WorkItemsConfig} (a {@code @ConfigMapping}) so SmallRye tracks it.
     * The {@code @ConfigProperty} injection here reads the same key; the mapping
     * ensures strict-mode validation doesn't reject it as unmapped.
     */
    @Inject
    @ConfigProperty(name = "casehub.work.capability-validation",
                    defaultValue = "PERMISSIVE")
    private ValidationMode validationMode;

    @Inject
    private CapabilityRegistry registry;

    public CapabilityValidator() {
        // CDI no-arg constructor — fields injected above
    }

    /** For unit tests — bypasses CDI and config. */
    public CapabilityValidator(ValidationMode validationMode, CapabilityRegistry registry) {
        this.validationMode = validationMode;
        this.registry = registry;
    }

    /**
     * Validates that all capabilities in the set are known to the registry.
     *
     * <p>Precondition: {@code capabilities} is non-null — use
     * {@code CapabilityParser.parse()} or {@code CapabilityParser.parseLenient()} to produce
     * the argument.
     *
     * <p>No-op in PERMISSIVE mode or when the set is empty.
     */
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
            LOG.warnf("WorkItem references unregistered capabilities: %s",
                    unknown.stream().map(Capability::id).toList());
        }
    }
}

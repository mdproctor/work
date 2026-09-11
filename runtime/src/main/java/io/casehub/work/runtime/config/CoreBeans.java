package io.casehub.work.runtime.config;

import io.casehub.work.api.ValidationMode;
import io.casehub.work.api.spi.CapabilityRegistry;
import io.casehub.work.api.spi.WorkerRegistry;
import io.casehub.work.core.policy.ContinuationPolicy;
import io.casehub.work.core.policy.FreshClockPolicy;
import io.casehub.work.core.policy.PhaseClockPolicy;
import io.casehub.work.core.policy.SingleBudgetPolicy;
import io.casehub.work.core.strategy.CapabilityValidator;
import io.casehub.work.core.strategy.ClaimFirstStrategy;
import io.casehub.work.core.strategy.LeastLoadedStrategy;
import io.casehub.work.core.strategy.NoOpRoutingCursorStore;
import io.casehub.work.core.strategy.NoOpWorkerRegistry;
import io.casehub.work.core.strategy.PermissiveCapabilityRegistry;
import io.casehub.work.core.strategy.RoutingCursorStore;
import io.casehub.work.core.strategy.RoundRobinStrategy;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CoreBeans {

    @Produces
    @Unremovable
    @ApplicationScoped
    public ClaimFirstStrategy claimFirstStrategy() {
        return new ClaimFirstStrategy();
    }

    @Produces
    @Unremovable
    @ApplicationScoped
    public LeastLoadedStrategy leastLoadedStrategy() {
        return new LeastLoadedStrategy();
    }

    @Produces
    @Unremovable
    @ApplicationScoped
    public RoundRobinStrategy roundRobinStrategy(RoutingCursorStore cursorStore) {
        return new RoundRobinStrategy(cursorStore);
    }

    @Produces
    @ApplicationScoped
    public CapabilityValidator capabilityValidator(
            @ConfigProperty(name = "casehub.work.capability-validation",
                            defaultValue = "PERMISSIVE") ValidationMode validationMode,
            CapabilityRegistry registry) {
        return new CapabilityValidator(validationMode, registry);
    }

    @Produces
    @DefaultBean
    public NoOpWorkerRegistry noOpWorkerRegistry() {
        return new NoOpWorkerRegistry();
    }

    @Produces
    @DefaultBean
    public NoOpRoutingCursorStore noOpRoutingCursorStore() {
        return new NoOpRoutingCursorStore();
    }

    @Produces
    @DefaultBean
    public PermissiveCapabilityRegistry permissiveCapabilityRegistry() {
        return new PermissiveCapabilityRegistry();
    }

    @Produces
    @Unremovable
    @ApplicationScoped
    public ContinuationPolicy continuationPolicy() {
        return new ContinuationPolicy();
    }

    @Produces
    @Unremovable
    @ApplicationScoped
    public FreshClockPolicy freshClockPolicy() {
        return new FreshClockPolicy();
    }

    @Produces
    @Unremovable
    @ApplicationScoped
    public PhaseClockPolicy phaseClockPolicy() {
        return new PhaseClockPolicy();
    }

    @Produces
    @Unremovable
    @ApplicationScoped
    public SingleBudgetPolicy singleBudgetPolicy() {
        return new SingleBudgetPolicy();
    }
}

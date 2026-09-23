package io.casehub.work.runtime.rest.core;

import io.casehub.work.runtime.service.SlaDefaultsYamlLoader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class RuntimeRestCoreProducers {

    @Produces
    @ApplicationScoped
    public SlaAdminCore slaAdminCore(SlaDefaultsYamlLoader loader) {
        return new SlaAdminCore(loader);
    }
}

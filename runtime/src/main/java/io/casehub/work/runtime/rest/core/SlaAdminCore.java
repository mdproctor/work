package io.casehub.work.runtime.rest.core;

import io.casehub.work.runtime.service.SlaDefaultsYamlLoader;
import java.util.Map;

public class SlaAdminCore {
    private final SlaDefaultsYamlLoader loader;

    public SlaAdminCore(SlaDefaultsYamlLoader loader) {
        this.loader = loader;
    }

    public Map<String, Boolean> reload() {
        loader.reload();
        return Map.of("reloaded", true);
    }
}

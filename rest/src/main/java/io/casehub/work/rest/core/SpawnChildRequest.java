package io.casehub.work.rest.core;

import java.util.Map;

public record SpawnChildRequest(String templateId, String callerRef, Map<String, Object> overrides) {
}

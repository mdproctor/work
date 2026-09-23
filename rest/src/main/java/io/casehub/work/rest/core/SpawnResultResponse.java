package io.casehub.work.rest.core;

import java.util.Map;

public record SpawnResultResponse(Map<String, Object> body, boolean created) {
}

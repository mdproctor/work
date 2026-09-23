package io.casehub.work.rest.core;

import java.util.List;

public record SpawnBodyRequest(String idempotencyKey, List<SpawnChildRequest> children) {
}

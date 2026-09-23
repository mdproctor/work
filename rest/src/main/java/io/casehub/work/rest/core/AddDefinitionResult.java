package io.casehub.work.rest.core;

import java.util.UUID;

public record AddDefinitionResult(UUID id, String path, String scope) {
}

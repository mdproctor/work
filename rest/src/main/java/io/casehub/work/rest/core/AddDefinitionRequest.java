package io.casehub.work.rest.core;

public record AddDefinitionRequest(String path, String description, String addedBy, String scope) {
}

package io.casehub.work.rest.core;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.casehub.work.api.Outcome;

public record UpdateTemplateRequest(
        String name,
        String description,
        String typePaths,
        String priority,
        String candidateGroups,
        String candidateUsers,
        String requiredCapabilities,
        Integer defaultExpiryHours,
        Integer defaultClaimHours,
        Integer defaultExpiryBusinessHours,
        Integer defaultClaimBusinessHours,
        String defaultPayload,
        String labelPaths,
        Integer instanceCount,
        Integer requiredCount,
        String parentRole,
        String assignmentStrategy,
        String onThresholdReached,
        Boolean allowSameAssignee,
        List<Outcome> outcomes,
        JsonNode inputDataSchema,
        JsonNode outputDataSchema,
        String excludedUsers,
        String excludedGroups,
        String scope) {
}

package io.casehub.work.rest.core;

public record CreateScheduleRequest(
        String name, String templateId, String cronExpression, String createdBy) {
}

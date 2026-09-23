package io.casehub.work.issuetracker.api.core;

public record LinkIssueRequest(String trackerType, String externalRef, String linkedBy) {
}

package io.casehub.work.issuetracker.api.core;

public record CreateIssueRequest(String trackerType, String title, String body, String linkedBy) {
}

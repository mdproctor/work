package io.casehub.work.issuetracker.api.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.transaction.Transactional;

import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.service.IssueLinkService;

public class IssueLinkCore {

    private final IssueLinkService linkService;

    public IssueLinkCore(IssueLinkService linkService) {
        this.linkService = linkService;
    }

    @Transactional
    public Map<String, Object> linkIssue(UUID workItemId, LinkIssueRequest request) {
        if (request == null || request.trackerType() == null || request.externalRef() == null) {
            throw new IllegalArgumentException("trackerType and externalRef are required");
        }
        WorkItemIssueLink link = linkService.linkExistingIssue(
                workItemId,
                request.trackerType(),
                request.externalRef(),
                request.linkedBy() != null ? request.linkedBy() : "unknown");
        return toResponse(link);
    }

    @Transactional
    public Map<String, Object> createAndLink(UUID workItemId, CreateIssueRequest request) {
        if (request == null || request.trackerType() == null || request.title() == null) {
            throw new IllegalArgumentException("trackerType and title are required");
        }
        WorkItemIssueLink link = linkService.createAndLink(
                workItemId,
                request.trackerType(),
                request.title(),
                request.body() != null ? request.body() : "",
                request.linkedBy() != null ? request.linkedBy() : "unknown");
        return toResponse(link);
    }

    public List<Map<String, Object>> listLinks(UUID workItemId) {
        return linkService.listLinks(workItemId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public boolean removeLink(UUID linkId, UUID workItemId) {
        return linkService.removeLink(linkId, workItemId);
    }

    @Transactional
    public Map<String, Object> syncLinks(UUID workItemId) {
        int synced = linkService.syncLinks(workItemId);
        return Map.of("synced", synced, "workItemId", workItemId);
    }

    private Map<String, Object> toResponse(WorkItemIssueLink link) {
        return Map.of(
                "id", link.id,
                "workItemId", link.workItemId,
                "trackerType", link.trackerType,
                "externalRef", link.externalRef,
                "title", link.title != null ? link.title : "",
                "url", link.url != null ? link.url : "",
                "status", link.status,
                "linkedAt", link.linkedAt,
                "linkedBy", link.linkedBy);
    }
}

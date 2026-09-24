package io.casehub.work.api.spi;

import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PaginatedResponse;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.PlatformStream;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemRootView;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemSummary;
import io.casehub.work.api.view.WorkItemPage;
import io.casehub.work.api.view.WorkItemView;
import io.casehub.work.api.view.WorkItemWithAuditView;
import io.smallrye.mutiny.Multi;

import java.util.List;

@McpDomain(value = "work/items", app = "work", summary = "Work item CRUD — create, query, clone, filter, assign")
public interface WorkItemApi {

    @PlatformQuery("List work items with optional filtering")
    @PaginatedResponse
    WorkItemPage listAll(WorkItemStatus status, WorkItemPriority priority,
                         String label, String outcome,
                         @ContextParam("tenancyId") String tenancyId,
                         Integer offset, Integer limit);

    @PlatformQuery("Get a work item by ID with audit trail")
    WorkItemWithAuditView getById(@PathParam UUID workItemId,
                                  @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Create a new work item")
    @RestStatus(201)
    WorkItemView create(WorkItemCreateRequest request,
                        @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Clone an existing work item")
    @RestStatus(201)
    WorkItemView clone(@PathParam UUID workItemId, String title, String createdBy,
                       @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Aggregated inbox summary counts")
    WorkItemSummary inboxSummary(String assignee, List<String> candidateGroups,
                                 String candidateUser, WorkItemStatus status,
                                 WorkItemPriority priority, String type,
                                 @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Inbox view with root items and post-filtering")
    List<WorkItemRootView> inbox(String assignee, List<String> candidateGroups,
                                String candidateUser, WorkItemStatus status,
                                WorkItemPriority priority, String type,
                                Boolean followUp, String outcome,
                                @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Add a label to a work item")
    WorkItemView addLabel(@PathParam UUID workItemId, String path, String appliedBy,
                          @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Remove a label from a work item")
    WorkItemView removeLabel(@PathParam UUID workItemId, String path,
                             @ContextParam("tenancyId") String tenancyId);

    @PlatformStream("Global work item event stream")
    Multi<WorkItemLifecycleEvent> streamEvents(UUID workItemId, String type,
                                               @ContextParam("tenancyId") String tenancyId);

    @PlatformStream("Events for a specific work item")
    Multi<WorkItemLifecycleEvent> streamWorkItemEvents(@PathParam UUID workItemId,
                                                       @ContextParam("tenancyId") String tenancyId);
}

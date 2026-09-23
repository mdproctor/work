package io.casehub.work.api.spi;

import java.util.List;
import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.view.AddRelationRequest;
import io.casehub.work.api.view.WorkItemRelationView;
import io.casehub.work.api.view.WorkItemView;

@McpDomain(value = "work/relations", app = "work")
public interface WorkItemRelationApi {

    @PlatformMutation("Add a relation with cycle detection")
    @RestStatus(201)
    WorkItemRelationView addRelation(@PathParam UUID workItemId, AddRelationRequest body,
                                     @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List outgoing relations")
    List<WorkItemRelationView> listOutgoing(@PathParam UUID workItemId,
                                            @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List incoming relations")
    List<WorkItemRelationView> listIncoming(@PathParam UUID workItemId,
                                            @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Delete a relation")
    void deleteRelation(@PathParam UUID workItemId, @PathParam UUID relationId,
                        @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get child work items")
    List<WorkItemView> children(@PathParam UUID workItemId,
                                @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("Get parent work item")
    WorkItemView parent(@PathParam UUID workItemId,
                        @ContextParam("tenancyId") String tenancyId);
}

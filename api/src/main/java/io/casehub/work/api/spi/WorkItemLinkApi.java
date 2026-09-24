package io.casehub.work.api.spi;

import java.util.List;
import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.view.AddLinkRequest;
import io.casehub.work.api.view.WorkItemLinkView;

@McpDomain(value = "work/links", app = "work", summary = "Work item linking — parent/child and cross-references")
public interface WorkItemLinkApi {

    @PlatformMutation("Add an external link to a work item")
    @RestStatus(201)
    WorkItemLinkView addLink(@PathParam UUID workItemId, AddLinkRequest body,
                             @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List links for a work item")
    List<WorkItemLinkView> listLinks(@PathParam UUID workItemId,
                                     @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Delete a link")
    void deleteLink(@PathParam UUID workItemId, @PathParam UUID linkId,
                    @ContextParam("tenancyId") String tenancyId);
}

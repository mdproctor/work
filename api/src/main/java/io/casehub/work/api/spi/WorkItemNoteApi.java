package io.casehub.work.api.spi;

import java.util.List;
import java.util.UUID;

import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestStatus;
import io.casehub.work.api.view.AddNoteRequest;
import io.casehub.work.api.view.WorkItemNoteView;

@McpDomain(value = "work/notes", app = "work", summary = "Work item notes and comments")
public interface WorkItemNoteApi {

    @PlatformMutation("Add a note to a work item")
    @RestStatus(201)
    WorkItemNoteView addNote(@PathParam UUID workItemId, AddNoteRequest body,
                             @ContextParam("tenancyId") String tenancyId);

    @PlatformQuery("List notes for a work item")
    List<WorkItemNoteView> listNotes(@PathParam UUID workItemId,
                                     @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Edit an existing note")
    WorkItemNoteView editNote(@PathParam UUID workItemId, @PathParam UUID noteId,
                              AddNoteRequest body,
                              @ContextParam("tenancyId") String tenancyId);

    @PlatformMutation("Delete a note")
    void deleteNote(@PathParam UUID workItemId, @PathParam UUID noteId,
                    @ContextParam("tenancyId") String tenancyId);
}

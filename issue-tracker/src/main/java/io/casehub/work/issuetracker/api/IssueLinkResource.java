package io.casehub.work.issuetracker.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.issuetracker.api.core.CreateIssueRequest;
import io.casehub.work.issuetracker.api.core.IssueLinkCore;
import io.casehub.work.issuetracker.api.core.LinkIssueRequest;
import io.casehub.work.issuetracker.spi.IssueTrackerException;

@Path("/workitems/{id}/issues")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IssueLinkResource {

    @Inject
    IssueLinkCore core;

    @POST
    public Response linkIssue(
            @PathParam("id") final UUID workItemId,
            final LinkIssueRequest request) {
        try {
            return Response.status(Response.Status.CREATED).entity(core.linkIssue(workItemId, request)).build();
        } catch (IssueTrackerException e) {
            if (e.isNotFound()) {
                return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", e.getMessage())).build();
            }
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/create")
    public Response createAndLink(
            @PathParam("id") final UUID workItemId,
            final CreateIssueRequest request) {
        try {
            return Response.status(Response.Status.CREATED).entity(core.createAndLink(workItemId, request)).build();
        } catch (IssueTrackerException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    public List<Map<String, Object>> listLinks(@PathParam("id") final UUID workItemId) {
        return core.listLinks(workItemId);
    }

    @DELETE
    @Path("/{linkId}")
    public Response removeLink(
            @PathParam("id") final UUID workItemId,
            @PathParam("linkId") final UUID linkId) {
        return core.removeLink(linkId, workItemId)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "Link not found")).build();
    }

    @PUT
    @Path("/sync")
    public Response syncLinks(@PathParam("id") final UUID workItemId) {
        return Response.ok(core.syncLinks(workItemId)).build();
    }
}

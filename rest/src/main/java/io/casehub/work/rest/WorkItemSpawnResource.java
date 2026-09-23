package io.casehub.work.rest;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.rest.core.SpawnBodyRequest;
import io.casehub.work.rest.core.SpawnResultResponse;
import io.casehub.work.rest.core.WorkItemSpawnCore;
import io.casehub.work.runtime.service.WorkItemNotFoundException;

@Path("/workitems")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkItemSpawnResource {

    @Inject
    WorkItemSpawnCore core;

    @POST
    @Path("/{id}/spawn")
    public Response spawn(@PathParam("id") final UUID parentId, final SpawnBodyRequest body) {
        try {
            SpawnResultResponse result = core.spawn(parentId, body);
            if (result.created()) {
                return Response.created(URI.create("/spawn-groups/" + result.body().get("groupId")))
                        .entity(result.body()).build();
            } else {
                return Response.ok(result.body()).build();
            }
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        } catch (WorkItemNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}/spawn-groups")
    public List<Map<String, Object>> listSpawnGroups(@PathParam("id") final UUID parentId) {
        return core.listSpawnGroups(parentId);
    }

    @DELETE
    @Path("/{id}/spawn-groups/{groupId}")
    public Response cancelGroup(
            @PathParam("id") final UUID parentId,
            @PathParam("groupId") final UUID groupId,
            @QueryParam("cancelChildren") final boolean cancelChildren) {
        try {
            return core.cancelGroup(parentId, groupId, cancelChildren)
                    ? Response.noContent().build()
                    : Response.status(Response.Status.NOT_FOUND)
                            .entity(Map.of("error", "Spawn group not found")).build();
        } catch (WorkItemNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }
}

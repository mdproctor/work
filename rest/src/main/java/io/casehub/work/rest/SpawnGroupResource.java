package io.casehub.work.rest;

import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.rest.core.SpawnGroupCore;

@Path("/spawn-groups")
@Produces(MediaType.APPLICATION_JSON)
public class SpawnGroupResource {

    @Inject
    SpawnGroupCore core;

    @GET
    @Path("/{groupId}")
    public Response getGroup(@PathParam("groupId") final UUID groupId) {
        return core.getGroup(groupId)
                .map(group -> Response.ok(group).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Spawn group not found")).build());
    }
}

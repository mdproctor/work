package io.casehub.work.rest;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.rest.core.WorkItemInstancesCore;

@Path("/workitems")
@Produces(MediaType.APPLICATION_JSON)
public class WorkItemInstancesResource {

    @Inject
    WorkItemInstancesCore core;

    @GET
    @Path("/{id}/instances")
    public Response getInstances(@PathParam("id") final UUID parentId) {
        return core.getInstances(parentId)
                .map(r -> Response.ok(r).build())
                .orElseGet(() -> Response.status(404).build());
    }
}

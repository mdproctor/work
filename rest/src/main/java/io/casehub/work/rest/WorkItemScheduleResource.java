package io.casehub.work.rest;

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

import io.casehub.work.rest.core.CreateScheduleRequest;
import io.casehub.work.rest.core.SetActiveRequest;
import io.casehub.work.rest.core.WorkItemScheduleCore;

@Path("/workitem-schedules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkItemScheduleResource {

    @Inject
    WorkItemScheduleCore core;

    @POST
    public Response create(final CreateScheduleRequest request) {
        try {
            return Response.status(Response.Status.CREATED).entity(core.create(request)).build();
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    @GET
    public List<Map<String, Object>> list() {
        return core.list();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") final UUID id) {
        return core.get(id)
                .map(s -> Response.ok(s).build())
                .orElseGet(this::notFound);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") final UUID id) {
        return core.delete(id) ? Response.noContent().build() : notFound();
    }

    @PUT
    @Path("/{id}/active")
    public Response setActive(@PathParam("id") final UUID id, final SetActiveRequest request) {
        try {
            return core.setActive(id, request)
                    .map(s -> Response.ok(s).build())
                    .orElseGet(this::notFound);
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        }
    }

    private Response bad(String msg) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", msg)).build();
    }

    private Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "Schedule not found")).build();
    }
}

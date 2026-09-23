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

import io.casehub.work.rest.core.CreateLabelRuleRequest;
import io.casehub.work.rest.core.LabelRuleCore;

@Path("/label-rules")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LabelRuleResource {

    @Inject
    LabelRuleCore core;

    @GET
    public List<Map<String, Object>> list() {
        return core.list();
    }

    @POST
    public Response create(final CreateLabelRuleRequest req) {
        try {
            return Response.status(201).entity(core.create(req)).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") final UUID id, final CreateLabelRuleRequest req) {
        return core.update(id, req)
                .map(r -> Response.ok(r).build())
                .orElseGet(() -> Response.status(404).entity(Map.of("error", "Not found")).build());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") final UUID id) {
        return core.delete(id)
                ? Response.noContent().build()
                : Response.status(404).entity(Map.of("error", "Not found")).build();
    }

    @POST
    @Path("/evaluate")
    public Response evaluate(final Map<String, Object> req) {
        try {
            return Response.ok(core.evaluate(req)).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }
}

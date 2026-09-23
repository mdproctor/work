package io.casehub.work.rest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;

import io.casehub.work.rest.core.CreateTemplateRequest;
import io.casehub.work.rest.core.InstantiateRequest;
import io.casehub.work.rest.core.UpdateTemplateRequest;
import io.casehub.work.rest.core.WorkItemTemplateCore;

@Path("/workitem-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkItemTemplateResource {

    @Inject
    WorkItemTemplateCore core;

    @POST
    public Response createTemplate(final CreateTemplateRequest request) {
        try {
            return Response.status(Response.Status.CREATED).entity(core.createTemplate(request)).build();
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    public List<Map<String, Object>> listTemplates() {
        return core.listTemplates();
    }

    @GET
    @Path("/{id}")
    public Response getTemplate(@PathParam("id") final UUID id) {
        return core.getTemplate(id)
                .map(t -> Response.ok(t).build())
                .orElseGet(this::notFound);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteTemplate(@PathParam("id") final UUID id) {
        return core.deleteTemplate(id) ? Response.noContent().build() : notFound();
    }

    @PUT
    @Path("/{id}")
    public Response updateTemplate(@PathParam("id") final UUID id, final UpdateTemplateRequest request) {
        try {
            return core.updateTemplate(id, request)
                    .map(t -> Response.ok(t).build())
                    .orElseGet(this::notFound);
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{id}")
    @Consumes("application/merge-patch+json")
    public Response patchTemplate(@PathParam("id") final UUID id, final JsonNode patch) {
        try {
            return core.patchTemplate(id, patch)
                    .map(t -> Response.ok(t).build())
                    .orElseGet(this::notFound);
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/{id}/instantiate")
    public Response instantiate(@PathParam("id") final UUID id, final InstantiateRequest request) {
        try {
            return Response.status(Response.Status.CREATED).entity(core.instantiate(id, request)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(e.getMessage().startsWith("Template not found")
                    ? Response.Status.NOT_FOUND : Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }

    private Response bad(String msg) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", msg)).build();
    }

    private Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "Template not found")).build();
    }
}

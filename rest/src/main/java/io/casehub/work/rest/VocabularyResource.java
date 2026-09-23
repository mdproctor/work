package io.casehub.work.rest;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.rest.core.AddDefinitionRequest;
import io.casehub.work.rest.core.AddDefinitionResult;
import io.casehub.work.rest.core.VocabularyCore;

@Path("/vocabulary")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VocabularyResource {

    @Inject
    VocabularyCore core;

    @GET
    public List<Map<String, Object>> listAll() {
        return core.listAll();
    }

    @POST
    public Response addDefinition(final AddDefinitionRequest request) {
        try {
            AddDefinitionResult result = core.addDefinition(request);
            return Response.status(Response.Status.CREATED)
                    .entity(Map.of("id", result.id(), "path", result.path(), "scope", result.scope()))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }
}

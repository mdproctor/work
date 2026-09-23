package io.casehub.work.ai.suggestion;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.ai.suggestion.core.ResolutionSuggestionCore;

@Path("/workitems")
@Produces(MediaType.APPLICATION_JSON)
public class ResolutionSuggestionResource {

    @Inject
    ResolutionSuggestionCore core;

    @GET
    @Path("/{id}/resolution-suggestion")
    public Response suggest(@PathParam("id") final UUID id) {
        return core.suggest(id)
                .map(r -> Response.ok(r).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"WorkItem not found: " + id + "\"}")
                        .build());
    }
}

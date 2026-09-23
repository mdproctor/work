package io.casehub.work.ledger.api;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import io.casehub.work.ledger.api.core.ActorTrustCore;

@Path("/workitems/actors")
@Produces(APPLICATION_JSON)
public class ActorTrustResource {

    @Inject
    ActorTrustCore core;

    @GET
    @Path("/{actorId}/trust")
    public Response getActorTrust(@PathParam("actorId") final String actorId) {
        return core.getActorTrust(actorId)
                .map(score -> Response.ok(score).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "No trust score computed for: " + actorId))
                        .build());
    }
}

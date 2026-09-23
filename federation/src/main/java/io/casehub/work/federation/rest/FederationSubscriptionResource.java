package io.casehub.work.federation.rest;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.federation.rest.core.FederationSubscriptionCore;
import io.casehub.work.federation.rest.core.SubscriptionRequest;

@Path("/federation/subscriptions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FederationSubscriptionResource {

    @Inject
    FederationSubscriptionCore core;

    @POST
    public Response register(SubscriptionRequest request) {
        return Response.status(Response.Status.CREATED).entity(core.register(request)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deregister(@PathParam("id") UUID id) {
        return core.deregister(id) ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }

    @PUT
    @Path("/{id}/reactivate")
    public Response reactivate(@PathParam("id") UUID id) {
        return core.reactivate(id)
                .map(r -> Response.ok(r).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }
}

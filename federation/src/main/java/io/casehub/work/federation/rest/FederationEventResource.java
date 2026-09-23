package io.casehub.work.federation.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import io.casehub.work.federation.rest.core.FederationEventCore;
import io.casehub.work.federation.rest.core.FederationEventCore.EventOutcome;

@Path("/federation/events")
public class FederationEventResource {

    @Inject
    FederationEventCore core;

    @POST
    @Consumes("application/cloudevents+json")
    public Response receiveEvent(String cloudEventJson,
                                 @HeaderParam("X-Federation-Signature") String signature,
                                 @HeaderParam("X-Federation-Peer-Id") String peerId) {
        EventOutcome outcome = core.receiveEvent(cloudEventJson, signature, peerId);
        if (outcome.message() != null) {
            return Response.status(outcome.status()).entity(outcome.message()).build();
        }
        return Response.status(outcome.status()).build();
    }
}

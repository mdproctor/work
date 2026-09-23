package io.casehub.work.ledger.api;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import io.casehub.work.ledger.api.core.LedgerCore;
import io.casehub.work.ledger.api.core.LedgerCore.AttestationOutcome;
import io.casehub.work.ledger.api.core.LedgerCore.ProvenanceOutcome;
import io.casehub.work.ledger.api.dto.LedgerAttestationRequest;
import io.casehub.work.ledger.api.dto.LedgerEntryResponse;
import io.casehub.work.ledger.api.dto.ProvenanceRequest;

@Path("/workitems/{id}")
@Produces(APPLICATION_JSON)
public class LedgerResource {

    @Inject
    LedgerCore core;

    @GET
    @Path("/ledger")
    public List<LedgerEntryResponse> getLedger(@PathParam("id") final UUID workItemId) {
        return core.getLedger(workItemId);
    }

    @PUT
    @Path("/ledger/provenance")
    @Consumes(APPLICATION_JSON)
    public Response setProvenance(@PathParam("id") final UUID workItemId,
            final ProvenanceRequest request) {
        return switch (core.setProvenance(workItemId, request)) {
            case OK -> Response.ok().build();
            case NOT_FOUND -> Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"No ledger entry found for this WorkItem\"}").build();
            case CONFLICT -> Response.status(Response.Status.CONFLICT)
                    .entity("{\"error\":\"Provenance already set for this WorkItem\"}").build();
        };
    }

    @POST
    @Path("/ledger/{entryId}/attestations")
    @Consumes(APPLICATION_JSON)
    public Response postAttestation(@PathParam("id") final UUID workItemId,
            @PathParam("entryId") final UUID entryId,
            final LedgerAttestationRequest request) {
        return switch (core.postAttestation(workItemId, entryId, request)) {
            case CREATED -> Response.status(Response.Status.CREATED).build();
            case NOT_FOUND -> Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Ledger entry not found for this WorkItem\"}").build();
            case DISABLED -> Response.status(Response.Status.CONFLICT)
                    .entity("{\"error\":\"Attestations are disabled\"}").build();
        };
    }
}

package io.casehub.work.rest;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.rest.core.BulkItemResult;
import io.casehub.work.rest.core.BulkRequest;
import io.casehub.work.rest.core.WorkItemBulkCore;

@Path("/workitems/bulk")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkItemBulkResource {

    @Inject
    WorkItemBulkCore core;

    @POST
    public Response bulk(final BulkRequest request) {
        try {
            List<BulkItemResult> results = core.bulk(request);
            return Response.ok(results).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }
    }
}

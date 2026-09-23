package io.casehub.work.ai.escalation;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.casehub.work.ai.escalation.core.EscalationSummaryCore;

/**
 * REST endpoint for reading escalation summaries.
 *
 * <pre>
 * GET /workitems/{id}/escalation-summaries
 * </pre>
 *
 * <p>
 * Returns all LLM-generated escalation summaries for a WorkItem, most recent first.
 * An empty list is returned if no escalations have occurred or none were generated yet.
 */
@Path("/workitems")
@Produces(MediaType.APPLICATION_JSON)
public class EscalationSummaryResource {

    @Inject
    EscalationSummaryCore core;

    /**
     * List all escalation summaries for a WorkItem, ordered by generatedAt descending.
     *
     * @param id the WorkItem UUID
     * @return list of escalation summaries; empty if none exist
     */
    @GET
    @Path("/{id}/escalation-summaries")
    public List<EscalationSummary> list(@PathParam("id") final UUID id) {
        return core.list(id);
    }
}

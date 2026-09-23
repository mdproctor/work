package io.casehub.work.reports.api;

import io.casehub.work.reports.api.core.ReportCore;
import io.casehub.work.reports.service.ActorReport;
import io.casehub.work.reports.service.QueueHealthReport;
import io.casehub.work.reports.service.SlaBreachReport;
import io.casehub.work.reports.service.ThroughputReport;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/workitems/reports")
@Produces(MediaType.APPLICATION_JSON)
public class ReportResource {

    @Inject
    ReportCore core;

    @GET
    @Path("/sla-breaches")
    public SlaBreachReport slaBreaches(
            @QueryParam("from") final String from,
            @QueryParam("to") final String to,
            @QueryParam("type") final String type,
            @QueryParam("priority") final String priority) {return core.slaBreaches(from, to, type, priority);}

    @GET
    @Path("/actors/{actorId}")
    public ActorReport actorPerformance(
            @PathParam("actorId") final String actorId,
            @QueryParam("from") final String from,
            @QueryParam("to") final String to,
            @QueryParam("type") final String type) {return core.actorPerformance(actorId, from, to, type);}

    @GET
    @Path("/throughput")
    public ThroughputReport throughput(
            @QueryParam("from") final String from,
            @QueryParam("to") final String to,
            @QueryParam("groupBy") @DefaultValue("day") final String groupBy) {
        try {
            return core.throughput(from, to, groupBy);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        }
    }

    @GET
    @Path("/queue-health")
    public QueueHealthReport queueHealth(
            @QueryParam("type") final String type,
            @QueryParam("priority") final String priority) {return core.queueHealth(type, priority);}

}

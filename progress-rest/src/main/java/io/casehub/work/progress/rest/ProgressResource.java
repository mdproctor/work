package io.casehub.work.progress.rest;

import io.casehub.work.progress.ProgressInstance;
import io.casehub.work.progress.ProgressSnapshot;
import io.casehub.work.progress.ProgressUpdatedEvent;
import io.casehub.work.progress.rest.core.ProgressCore;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;
import java.util.UUID;

@Path("/progress")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProgressResource {

    @Inject
    ProgressCore core;

    @POST
    public Response create(CreateProgressRequest request) {
        return Response.status(Response.Status.CREATED).entity(core.create(request)).build();
    }

    @PUT
    @Path("/{id}/state")
    public Response updateState(@PathParam("id") UUID id, UpdateStateRequest body) {
        return Response.ok(core.updateState(id, body)).build();
    }

    @POST
    @Path("/{id}/complete")
    public Response complete(@PathParam("id") UUID id) {
        return Response.ok(core.complete(id)).build();
    }

    @POST
    @Path("/{id}/fail")
    public Response fail(@PathParam("id") UUID id) {
        return Response.ok(core.fail(id)).build();
    }

    @POST
    @Path("/{id}/reactivate")
    public Response reactivate(@PathParam("id") UUID id) {
        return Response.ok(core.reactivate(id)).build();
    }

    @POST
    @Path("/{id}/children")
    public Response attachChild(@PathParam("id") UUID parentId, CreateProgressRequest request) {
        return Response.status(Response.Status.CREATED).entity(core.attachChild(parentId, request)).build();
    }

    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") UUID id) {
        return core.getById(id)
                .map(inst -> Response.ok(inst).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/{id}/tree")
    public Response getTree(@PathParam("id") UUID id) {
        return core.getTree(id)
                .map(tree -> Response.ok(tree).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    public List<ProgressInstance> findByScope(
            @QueryParam("scopeType") String scopeType,
            @QueryParam("scopeId") String scopeId) {
        return core.findByScope(scopeType, scopeId);
    }

    @GET
    @Path("/{id}/events")
    public List<ProgressUpdatedEvent> getEvents(
            @PathParam("id") UUID id,
            @QueryParam("since") String since) {
        return core.getEvents(id, since);
    }

    @POST
    @Path("/{id}/rollback")
    public Response rollback(@PathParam("id") UUID id, @QueryParam("toEvent") UUID toEventId) {
        return Response.ok(core.rollback(id, toEventId)).build();
    }

    @POST
    @Path("/{id}/rollback/subtree")
    public Response rollbackSubtree(
            @PathParam("id") UUID id,
            @QueryParam("timestamp") String timestamp,
            @QueryParam("toEvent") UUID toEventId) {
        try {
            return Response.ok(core.rollbackSubtree(id, timestamp, toEventId)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/{id}/snapshots")
    public List<ProgressSnapshot> getSnapshots(
            @PathParam("id") UUID id,
            @QueryParam("limit") Integer limit) {
        return core.getSnapshots(id, limit);
    }

    @GET
    @Path("/{id}/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public java.util.concurrent.Flow.Publisher<ProgressUpdatedEvent> streamEvents(
            @PathParam("id") UUID id,
            @QueryParam("tenancyId") String tenancyId) {
        return core.streamEvents(id, tenancyId);
    }

    @POST
    @Path("/{id}/steps/{stepName}/start")
    public Response startStep(@PathParam("id") UUID id, @PathParam("stepName") String stepName) {
        return Response.ok(core.startStep(id, stepName)).build();
    }

    @POST
    @Path("/{id}/steps/{stepName}/complete")
    public Response completeStep(@PathParam("id") UUID id, @PathParam("stepName") String stepName) {
        return Response.ok(core.completeStep(id, stepName)).build();
    }

    @POST
    @Path("/{id}/steps/{stepName}/skip")
    public Response skipStep(@PathParam("id") UUID id, @PathParam("stepName") String stepName) {
        return Response.ok(core.skipStep(id, stepName)).build();
    }

    @POST
    @Path("/{id}/steps/{stepName}/fail")
    public Response failStep(@PathParam("id") UUID id, @PathParam("stepName") String stepName) {
        return Response.ok(core.failStep(id, stepName)).build();
    }

    @PUT
    @Path("/{id}/steps/{stepName}/state")
    public Response updateStepState(@PathParam("id") UUID id,
                                    @PathParam("stepName") String stepName,
                                    UpdateStepDataRequest body) {
        return Response.ok(core.updateStepState(id, stepName, body)).build();
    }
}

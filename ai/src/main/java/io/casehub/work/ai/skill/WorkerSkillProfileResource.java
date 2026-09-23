package io.casehub.work.ai.skill;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.ai.skill.core.ProfileRequest;
import io.casehub.work.ai.skill.core.WorkerSkillProfileCore;

@Path("/worker-skill-profiles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkerSkillProfileResource {

    @Inject
    WorkerSkillProfileCore core;

    @POST
    public Response upsert(final ProfileRequest request) {
        try {
            core.upsert(request);
            return Response.status(201).build();
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), 400);
        }
    }

    @GET
    public List<WorkerSkillProfile> listAll() {
        return core.listAll();
    }

    @GET
    @Path("/{workerId}")
    public WorkerSkillProfile get(@PathParam("workerId") final String workerId) {
        return core.get(workerId)
                .orElseThrow(() -> new WebApplicationException(404));
    }

    @DELETE
    @Path("/{workerId}")
    public Response delete(@PathParam("workerId") final String workerId) {
        return core.delete(workerId) ? Response.noContent().build() : Response.status(404).build();
    }
}

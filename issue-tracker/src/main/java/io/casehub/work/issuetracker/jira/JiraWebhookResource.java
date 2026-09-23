package io.casehub.work.issuetracker.jira;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.issuetracker.github.core.WebhookOutcome;
import io.casehub.work.issuetracker.jira.core.JiraWebhookCore;

@Path("/workitems/jira-webhook/{tenancyId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JiraWebhookResource {

    @Inject
    JiraWebhookCore core;

    @POST
    public Response receive(
            @PathParam("tenancyId") final String tenancyId,
            @QueryParam("secret") final String secret,
            final String body) {
        WebhookOutcome outcome = core.receive(tenancyId, secret, body);
        return switch (outcome) {
            case OK -> Response.ok().build();
            case BAD_REQUEST -> Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "tenancyId path parameter is required")).build();
            case UNAUTHORIZED -> Response.status(Response.Status.UNAUTHORIZED).build();
        };
    }
}

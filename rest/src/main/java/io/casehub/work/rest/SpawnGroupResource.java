package io.casehub.work.rest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.work.api.WorkItemRelationType;
import io.casehub.work.runtime.repository.WorkItemRelationStore;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;

/**
 * REST endpoint for direct spawn group lookup by ID.
 *
 * <p>
 * The URI {@code /spawn-groups/{groupId}} is the canonical resource for a
 * {@link WorkItemSpawnGroup}. Its {@code Location} header is returned by
 * {@code POST /workitems/{id}/spawn} on creation.
 */
@Path("/spawn-groups")
@Produces(MediaType.APPLICATION_JSON)
public class SpawnGroupResource {

    @Inject
    WorkItemSpawnGroupStore spawnGroupStore;

    @Inject
    WorkItemRelationStore relationStore;

    /**
     * Fetch a spawn group by ID, including its PART_OF children.
     *
     * @param groupId the spawn group UUID
     * @return 200 OK with group details and child list; 404 if not found
     */
    @GET
    @Path("/{groupId}")
    public Response getGroup(@PathParam("groupId") final UUID groupId) {
        final var group = spawnGroupStore.get(groupId).orElse(null);
        if (group == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Spawn group not found")).build();
        }
        final String createdByMarker = "system:spawn:" + groupId;
        final List<Map<String, Object>> children = relationStore
                .findByTargetAndType(group.parentId, WorkItemRelationType.PART_OF)
                .stream()
                .filter(r -> createdByMarker.equals(r.createdBy))
                .map(r -> Map.<String, Object> of(
                        "workItemId", r.sourceId.toString(),
                        "createdAt", r.createdAt.toString()))
                .toList();
        return Response.ok(Map.of(
                "id", group.id.toString(),
                "parentId", group.parentId.toString(),
                "idempotencyKey", group.idempotencyKey,
                "createdAt", group.createdAt.toString(),
                "children", children)).build();
    }
}

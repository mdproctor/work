package io.casehub.work.runtime.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.work.api.Outcome;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import io.casehub.work.runtime.service.WorkItemTemplateValidationService;

/**
 * REST resource for managing and instantiating {@link WorkItemTemplate} records.
 *
 * <pre>
 * POST   /workitem-templates                         — create a template
 * GET    /workitem-templates                         — list all templates
 * GET    /workitem-templates/{id}                    — get a single template
 * DELETE /workitem-templates/{id}                    — delete a template
 * PUT    /workitem-templates/{id}                    — update (replace) a template
 * POST   /workitem-templates/{id}/instantiate        — create a WorkItem from the template
 * </pre>
 */
@Path("/workitem-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkItemTemplateResource {

    @Inject
    WorkItemTemplateService templateService;

    /**
     * Request body for creating a new template.
     *
     * @param name display name (required); used as default WorkItem title
     * @param description optional description of what this template is for
     * @param category default WorkItem category
     * @param priority default WorkItem priority
     * @param candidateGroups default comma-separated candidate groups
     * @param candidateUsers default comma-separated candidate users
     * @param requiredCapabilities default comma-separated capability tags
     * @param defaultExpiryHours default completion deadline in hours; null → system default
     * @param defaultClaimHours default claim deadline in hours; null → system default
     * @param defaultExpiryBusinessHours default completion deadline in business hours
     * @param defaultClaimBusinessHours default claim deadline in business hours
     * @param defaultPayload default JSON payload
     * @param labelPaths JSON array of label paths applied at instantiation
     * @param instanceCount number of parallel instances for multi-instance mode; null for standard
     * @param requiredCount minimum instances that must complete; only meaningful when instanceCount is set
     * @param parentRole COORDINATOR (default) or PARTICIPANT; only meaningful when instanceCount is set
     * @param assignmentStrategy CDI bean name of InstanceAssignmentStrategy; null defaults to "pool"
     * @param onThresholdReached action on remaining children when M-of-N threshold met: KEEP (default, no side effects), SUSPEND (pause active children), CANCEL (opt-in only, cancels all remaining). Null or omitted means KEEP.
     * @param allowSameAssignee when true, same person can claim multiple instances in group
     * @param outcomes optional list of named outcome definitions; null means no outcome constraint
     * @param inputDataSchema JSON Schema (draft-07) for payload validation at instantiation; null = no constraint.
     *        Accepted as a raw JSON object in the request body and serialised to a JSON string for storage.
     * @param outputDataSchema JSON Schema (draft-07) for resolution validation at completion; null = no constraint.
     *        Accepted as a raw JSON object in the request body and serialised to a JSON string for storage.
     * @param createdBy who created this template (required)
     */
    public record CreateTemplateRequest(
            String name,
            String description,
            String category,
            String priority,
            String candidateGroups,
            String candidateUsers,
            String requiredCapabilities,
            Integer defaultExpiryHours,
            Integer defaultClaimHours,
            Integer defaultExpiryBusinessHours,
            Integer defaultClaimBusinessHours,
            String defaultPayload,
            String labelPaths,
            Integer instanceCount,
            Integer requiredCount,
            String parentRole,
            String assignmentStrategy,
            String onThresholdReached,
            Boolean allowSameAssignee,
            List<Outcome> outcomes,
            /**
             * JSON Schema (draft-07) for payload validation at instantiation; null = no constraint.
             * Deserialised from a raw JSON object in the request body and serialised to a JSON string for storage.
             */
            JsonNode inputDataSchema,
            /**
             * JSON Schema (draft-07) for resolution validation at completion; null = no constraint.
             * Deserialised from a raw JSON object in the request body and serialised to a JSON string for storage.
             */
            JsonNode outputDataSchema,
            /** Comma-separated user IDs excluded from claiming instances; null = no exclusion. */
            String excludedUsers,
            /** Comma-separated group names whose members are excluded at instantiation time; null = no exclusion. */
            String excludedGroups,
            /** Hierarchical scope path e.g. {@code "casehubio/devtown/pr-review"}; null = root scope. */
            String scope,
            String createdBy) {
    }

    /**
     * Request body for instantiating a template.
     *
     * @param title optional title override; defaults to template name
     * @param assigneeId optional direct assignee; bypasses candidateGroups routing
     * @param createdBy who or what is triggering the instantiation (required)
     */
    public record InstantiateRequest(String title, String assigneeId, String createdBy) {
    }

    /**
     * Request body for updating an existing WorkItemTemplate (full replacement).
     *
     * <p>
     * All fields except {@code name} are nullable — null clears the field on the stored template.
     * {@code createdBy} is intentionally absent — authorship is immutable after creation.
     *
     * @param name                       required; must be unique across all templates
     * @param description                optional free-text description
     * @param category                   optional process classification
     * @param priority                   optional default priority (LOW/MEDIUM/HIGH/URGENT)
     * @param candidateGroups            optional comma-separated group IDs
     * @param candidateUsers             optional comma-separated user IDs
     * @param requiredCapabilities       optional comma-separated capability tags
     * @param defaultExpiryHours         optional completion deadline in calendar hours
     * @param defaultClaimHours          optional claim deadline in calendar hours
     * @param defaultExpiryBusinessHours optional completion deadline in business hours
     * @param defaultClaimBusinessHours  optional claim deadline in business hours
     * @param defaultPayload             optional default JSON payload for instantiated WorkItems
     * @param labelPaths                 optional comma-separated label paths to auto-apply
     * @param instanceCount              optional total instance count for multi-instance templates
     * @param requiredCount              optional M-of-N threshold for multi-instance completion
     * @param parentRole                 optional role label for the coordinator WorkItem
     * @param assignmentStrategy         optional instance assignment strategy name
     * @param onThresholdReached         optional action when M-of-N threshold met (KEEP/CANCEL)
     * @param allowSameAssignee          optional whether the same person may claim multiple instances
     * @param outcomes                   optional named outcomes constraining how instances are resolved
     * @param inputDataSchema            optional JSON Schema (draft-07) for payload validation
     * @param outputDataSchema           optional JSON Schema (draft-07) for resolution validation
     * @param excludedUsers              optional comma-separated user IDs excluded from claiming
     * @param excludedGroups             optional comma-separated group names excluded from claiming (expanded at instantiation)
     */
    public record UpdateTemplateRequest(
            String name,
            String description,
            String category,
            String priority,
            String candidateGroups,
            String candidateUsers,
            String requiredCapabilities,
            Integer defaultExpiryHours,
            Integer defaultClaimHours,
            Integer defaultExpiryBusinessHours,
            Integer defaultClaimBusinessHours,
            String defaultPayload,
            String labelPaths,
            Integer instanceCount,
            Integer requiredCount,
            String parentRole,
            String assignmentStrategy,
            String onThresholdReached,
            Boolean allowSameAssignee,
            List<Outcome> outcomes,
            JsonNode inputDataSchema,
            JsonNode outputDataSchema,
            String excludedUsers,
            String excludedGroups,
            String scope) {
    }

    /**
     * Create a new WorkItemTemplate.
     *
     * @param request the template definition; {@code name} and {@code createdBy} are required
     * @return 201 Created with the new template, 400 if required fields are missing
     */
    @POST
    @Transactional
    public Response createTemplate(final CreateTemplateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "name is required")).build();
        }
        if (request.createdBy() == null || request.createdBy().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "createdBy is required")).build();
        }
        if (templateService.findByName(request.name()).isPresent()) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "template with name '" + request.name() + "' already exists")).build();
        }
        if (request.inputDataSchema() != null && !request.inputDataSchema().isObject()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "inputDataSchema must be a JSON object (Schema), not a "
                            + request.inputDataSchema().getNodeType().name().toLowerCase())).build();
        }
        if (request.outputDataSchema() != null && !request.outputDataSchema().isObject()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "outputDataSchema must be a JSON object (Schema), not a "
                            + request.outputDataSchema().getNodeType().name().toLowerCase())).build();
        }

        final WorkItemTemplate t = new WorkItemTemplate();
        t.name = request.name();
        t.description = request.description();
        t.category = request.category();
        t.priority = request.priority() != null
                ? io.casehub.work.runtime.model.WorkItemPriority.valueOf(request.priority())
                : null;
        t.candidateGroups = request.candidateGroups();
        t.candidateUsers = request.candidateUsers();
        t.requiredCapabilities = request.requiredCapabilities();
        t.defaultExpiryHours = request.defaultExpiryHours();
        t.defaultClaimHours = request.defaultClaimHours();
        t.defaultExpiryBusinessHours = request.defaultExpiryBusinessHours();
        t.defaultClaimBusinessHours = request.defaultClaimBusinessHours();
        t.defaultPayload = request.defaultPayload();
        t.labelPaths = request.labelPaths();
        t.instanceCount = request.instanceCount();
        t.requiredCount = request.requiredCount();
        t.parentRole = request.parentRole();
        t.assignmentStrategy = request.assignmentStrategy();
        t.onThresholdReached = request.onThresholdReached();
        t.allowSameAssignee = request.allowSameAssignee();
        t.outcomes = WorkItemTemplateService.encodeOutcomes(request.outcomes());
        t.inputDataSchema = request.inputDataSchema() != null ? request.inputDataSchema().toString() : null;
        t.outputDataSchema = request.outputDataSchema() != null ? request.outputDataSchema().toString() : null;
        t.excludedUsers = request.excludedUsers();
        t.excludedGroups = request.excludedGroups();
        t.scope = request.scope();
        t.createdBy = request.createdBy();
        try {
            WorkItemTemplateValidationService.validate(t);
        } catch (final IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }
        t.persist();

        return Response.status(Response.Status.CREATED).entity(toResponse(t)).build();
    }

    /**
     * List all WorkItemTemplates, ordered by name.
     *
     * @return 200 OK with list of templates; may be empty
     */
    @GET
    public List<Map<String, Object>> listTemplates() {
        return WorkItemTemplate.listAllByName().stream().map(this::toResponse).toList();
    }

    /**
     * Get a single WorkItemTemplate by ID.
     *
     * @param id the template UUID
     * @return 200 OK with the template, 404 if not found
     */
    @GET
    @Path("/{id}")
    public Response getTemplate(@PathParam("id") final UUID id) {
        return templateService.findById(id)
                .map(t -> Response.ok(toResponse(t)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Template not found")).build());
    }

    /**
     * Delete a WorkItemTemplate.
     *
     * <p>
     * Does NOT delete WorkItems previously instantiated from this template.
     *
     * @param id the template UUID
     * @return 204 No Content on success, 404 if not found
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteTemplate(@PathParam("id") final UUID id) {
        return WorkItemTemplate.deleteById(id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Template not found")).build();
    }

    /**
     * Update an existing WorkItemTemplate (full replacement).
     *
     * <p>
     * All mutable fields are overwritten with the request values. Null clears the field.
     * WorkItems previously instantiated from this template are unaffected — they snapshot
     * the template state at instantiation time.
     *
     * @param id      the template UUID
     * @param request the new template state; {@code name} is required
     * @return 200 OK with the updated template, 400 if validation fails,
     *         404 if not found, 409 if name conflicts with another template
     */
    @PUT
    @Path("/{id}")
    @Transactional
    public Response updateTemplate(@PathParam("id") final UUID id, final UpdateTemplateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "name is required")).build();
        }
        if (request.inputDataSchema() != null && !request.inputDataSchema().isObject()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "inputDataSchema must be a JSON object (Schema), not a "
                            + request.inputDataSchema().getNodeType().name().toLowerCase())).build();
        }
        if (request.outputDataSchema() != null && !request.outputDataSchema().isObject()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "outputDataSchema must be a JSON object (Schema), not a "
                            + request.outputDataSchema().getNodeType().name().toLowerCase())).build();
        }

        final WorkItemTemplate t = templateService.findById(id).orElse(null);
        if (t == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Template not found")).build();
        }

        if (!request.name().equals(t.name)) {
            if (templateService.findByName(request.name()).isPresent()) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("error", "template with name '" + request.name() + "' already exists")).build();
            }
        }

        t.name = request.name();
        t.description = request.description();
        t.category = request.category();
        t.priority = request.priority() != null
                ? io.casehub.work.runtime.model.WorkItemPriority.valueOf(request.priority())
                : null;
        t.candidateGroups = request.candidateGroups();
        t.candidateUsers = request.candidateUsers();
        t.requiredCapabilities = request.requiredCapabilities();
        t.defaultExpiryHours = request.defaultExpiryHours();
        t.defaultClaimHours = request.defaultClaimHours();
        t.defaultExpiryBusinessHours = request.defaultExpiryBusinessHours();
        t.defaultClaimBusinessHours = request.defaultClaimBusinessHours();
        t.defaultPayload = request.defaultPayload();
        t.labelPaths = request.labelPaths();
        t.instanceCount = request.instanceCount();
        t.requiredCount = request.requiredCount();
        t.parentRole = request.parentRole();
        t.assignmentStrategy = request.assignmentStrategy();
        t.onThresholdReached = request.onThresholdReached();
        t.allowSameAssignee = request.allowSameAssignee();
        t.outcomes = WorkItemTemplateService.encodeOutcomes(request.outcomes());
        t.inputDataSchema = request.inputDataSchema() != null ? request.inputDataSchema().toString() : null;
        t.outputDataSchema = request.outputDataSchema() != null ? request.outputDataSchema().toString() : null;
        t.excludedUsers = request.excludedUsers();
        t.excludedGroups = request.excludedGroups();
        t.scope = request.scope();
        try {
            WorkItemTemplateValidationService.validate(t);
        } catch (final IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage())).build();
        }

        return Response.ok(toResponse(t)).build();
    }

    /**
     * Create a WorkItem from a template.
     *
     * <p>
     * The WorkItem inherits all template defaults. The caller may override the title
     * and assignee; all other fields are fixed by the template.
     *
     * @param id the template UUID
     * @param request optional overrides ({@code title}, {@code assigneeId}) and {@code createdBy}
     * @return 201 Created with the new WorkItem, 400 if createdBy missing, 404 if template not found
     */
    @POST
    @Path("/{id}/instantiate")
    @Transactional
    public Response instantiate(@PathParam("id") final UUID id, final InstantiateRequest request) {
        if (request == null || request.createdBy() == null || request.createdBy().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "createdBy is required")).build();
        }
        return templateService.findById(id)
                .map(t -> {
                    try {
                        final var wi = templateService.instantiate(
                                t, request.title(), request.assigneeId(), request.createdBy());
                        return Response.status(Response.Status.CREATED)
                                .entity(WorkItemMapper.toResponse(wi)).build();
                    } catch (IllegalArgumentException e) {
                        return Response.status(Response.Status.BAD_REQUEST)
                                .entity(Map.of("error", e.getMessage())).build();
                    }
                })
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Template not found")).build());
    }

    private Map<String, Object> toResponse(final WorkItemTemplate t) {
        final java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", t.id);
        m.put("name", t.name);
        m.put("description", t.description);
        m.put("category", t.category);
        m.put("priority", t.priority != null ? t.priority.name() : null);
        m.put("candidateGroups", t.candidateGroups);
        m.put("candidateUsers", t.candidateUsers);
        m.put("requiredCapabilities", t.requiredCapabilities);
        m.put("defaultExpiryHours", t.defaultExpiryHours);
        m.put("defaultClaimHours", t.defaultClaimHours);
        m.put("defaultExpiryBusinessHours", t.defaultExpiryBusinessHours);
        m.put("defaultClaimBusinessHours", t.defaultClaimBusinessHours);
        m.put("defaultPayload", t.defaultPayload);
        m.put("labelPaths", t.labelPaths);
        m.put("instanceCount", t.instanceCount);
        m.put("requiredCount", t.requiredCount);
        m.put("parentRole", t.parentRole);
        m.put("assignmentStrategy", t.assignmentStrategy);
        m.put("onThresholdReached", t.onThresholdReached);
        m.put("allowSameAssignee", t.allowSameAssignee);
        m.put("outcomes", WorkItemTemplateService.decodeOutcomes(t.outcomes));
        m.put("inputDataSchema", t.inputDataSchema);
        m.put("outputDataSchema", t.outputDataSchema);
        m.put("excludedUsers", t.excludedUsers);
        m.put("excludedGroups", t.excludedGroups);
        m.put("scope", t.scope);
        m.put("createdBy", t.createdBy);
        m.put("createdAt", t.createdAt);
        return m;
    }
}

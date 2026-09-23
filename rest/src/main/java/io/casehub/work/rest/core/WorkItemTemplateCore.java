package io.casehub.work.rest.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;

import io.casehub.work.api.Outcome;
import io.casehub.work.api.WorkItemCreateRequest;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.view.WorkItemView;
import io.casehub.work.rest.service.ViewMapper;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import io.casehub.work.runtime.service.WorkItemTemplateValidationService;

public class WorkItemTemplateCore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WorkItemTemplateService templateService;
    private final WorkItemTemplateStore templateStore;

    public WorkItemTemplateCore(WorkItemTemplateService templateService,
            WorkItemTemplateStore templateStore) {
        this.templateService = templateService;
        this.templateStore = templateStore;
    }

    @Transactional
    public Map<String, Object> createTemplate(CreateTemplateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (request.createdBy() == null || request.createdBy().isBlank()) {
            throw new IllegalArgumentException("createdBy is required");
        }
        if (templateService.findByName(request.name()).isPresent()) {
            throw new IllegalStateException("template with name '" + request.name() + "' already exists");
        }
        validateSchema(request.inputDataSchema(), "inputDataSchema");
        validateSchema(request.outputDataSchema(), "outputDataSchema");

        WorkItemTemplate t = new WorkItemTemplate();
        t.name = request.name();
        t.description = request.description();
        t.typePaths = request.typePaths();
        t.priority = request.priority() != null ? WorkItemPriority.valueOf(request.priority()) : null;
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
        WorkItemTemplateValidationService.validate(t);
        templateStore.put(t);

        return toResponse(t);
    }

    public List<Map<String, Object>> listTemplates() {
        return templateStore.scanAll().stream().map(this::toResponse).toList();
    }

    public Optional<Map<String, Object>> getTemplate(UUID id) {
        return templateService.findById(id).map(this::toResponse);
    }

    @Transactional
    public boolean deleteTemplate(UUID id) {
        return templateStore.delete(id);
    }

    @Transactional
    public Optional<Map<String, Object>> updateTemplate(UUID id, UpdateTemplateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        validateSchema(request.inputDataSchema(), "inputDataSchema");
        validateSchema(request.outputDataSchema(), "outputDataSchema");

        WorkItemTemplate t = templateService.findById(id).orElse(null);
        if (t == null) {
            return Optional.empty();
        }

        if (!request.name().equals(t.name)) {
            if (templateService.findByName(request.name()).isPresent()) {
                throw new IllegalStateException("template with name '" + request.name() + "' already exists");
            }
        }

        t.name = request.name();
        t.description = request.description();
        t.typePaths = request.typePaths();
        t.priority = request.priority() != null ? WorkItemPriority.valueOf(request.priority()) : null;
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
        WorkItemTemplateValidationService.validate(t);
        t.version++;

        return Optional.of(toResponse(t));
    }

    @Transactional
    public Optional<Map<String, Object>> patchTemplate(UUID id, JsonNode patch) {
        if (patch == null || !patch.isObject()) {
            throw new IllegalArgumentException("patch body must be a JSON object");
        }

        WorkItemTemplate t = templateService.findById(id).orElse(null);
        if (t == null) {
            return Optional.empty();
        }

        if (patch.has("name")) {
            JsonNode nameNode = patch.get("name");
            if (nameNode.isNull()) {
                throw new IllegalArgumentException("name is required when provided in a PATCH");
            }
            String newName = nameNode.asText();
            if (!newName.equals(t.name) && templateService.findByName(newName).isPresent()) {
                throw new IllegalStateException("template with name '" + newName + "' already exists");
            }
            t.name = newName;
        }

        if (patch.has("description"))          t.description = textOrNull(patch, "description");
        if (patch.has("typePaths"))            t.typePaths = textOrNull(patch, "typePaths");
        if (patch.has("candidateGroups"))      t.candidateGroups = textOrNull(patch, "candidateGroups");
        if (patch.has("candidateUsers"))       t.candidateUsers = textOrNull(patch, "candidateUsers");
        if (patch.has("requiredCapabilities")) t.requiredCapabilities = textOrNull(patch, "requiredCapabilities");
        if (patch.has("defaultPayload"))       t.defaultPayload = textOrNull(patch, "defaultPayload");
        if (patch.has("labelPaths"))           t.labelPaths = textOrNull(patch, "labelPaths");
        if (patch.has("parentRole"))           t.parentRole = textOrNull(patch, "parentRole");
        if (patch.has("assignmentStrategy"))   t.assignmentStrategy = textOrNull(patch, "assignmentStrategy");
        if (patch.has("onThresholdReached"))   t.onThresholdReached = textOrNull(patch, "onThresholdReached");
        if (patch.has("excludedUsers"))        t.excludedUsers = textOrNull(patch, "excludedUsers");
        if (patch.has("excludedGroups"))       t.excludedGroups = textOrNull(patch, "excludedGroups");
        if (patch.has("scope"))                t.scope = textOrNull(patch, "scope");

        if (patch.has("defaultExpiryHours"))
            t.defaultExpiryHours = intOrNull(patch, "defaultExpiryHours");
        if (patch.has("defaultClaimHours"))
            t.defaultClaimHours = intOrNull(patch, "defaultClaimHours");
        if (patch.has("defaultExpiryBusinessHours"))
            t.defaultExpiryBusinessHours = intOrNull(patch, "defaultExpiryBusinessHours");
        if (patch.has("defaultClaimBusinessHours"))
            t.defaultClaimBusinessHours = intOrNull(patch, "defaultClaimBusinessHours");
        if (patch.has("instanceCount"))
            t.instanceCount = intOrNull(patch, "instanceCount");
        if (patch.has("requiredCount"))
            t.requiredCount = intOrNull(patch, "requiredCount");

        if (patch.has("allowSameAssignee"))
            t.allowSameAssignee = patch.get("allowSameAssignee").isNull()
                    ? null : patch.get("allowSameAssignee").booleanValue();

        if (patch.has("priority")) {
            JsonNode priorityNode = patch.get("priority");
            if (priorityNode.isNull()) {
                t.priority = null;
            } else {
                t.priority = WorkItemPriority.valueOf(priorityNode.asText());
            }
        }

        if (patch.has("outcomes")) {
            JsonNode outcomesNode = patch.get("outcomes");
            if (outcomesNode.isNull()) {
                t.outcomes = null;
            } else {
                List<Outcome> outcomes = MAPPER.convertValue(
                        outcomesNode, new TypeReference<List<Outcome>>() {});
                t.outcomes = WorkItemTemplateService.encodeOutcomes(outcomes);
            }
        }

        if (patch.has("inputDataSchema")) {
            JsonNode schemaNode = patch.get("inputDataSchema");
            if (schemaNode.isNull()) {
                t.inputDataSchema = null;
            } else {
                validateSchemaNode(schemaNode, "inputDataSchema");
                t.inputDataSchema = schemaNode.toString();
            }
        }

        if (patch.has("outputDataSchema")) {
            JsonNode schemaNode = patch.get("outputDataSchema");
            if (schemaNode.isNull()) {
                t.outputDataSchema = null;
            } else {
                validateSchemaNode(schemaNode, "outputDataSchema");
                t.outputDataSchema = schemaNode.toString();
            }
        }

        WorkItemTemplateValidationService.validate(t);
        t.version++;

        return Optional.of(toResponse(t));
    }

    @Transactional
    public WorkItemView instantiate(UUID id, InstantiateRequest request) {
        if (request == null || request.createdBy() == null || request.createdBy().isBlank()) {
            throw new IllegalArgumentException("createdBy is required");
        }
        var createRequest = WorkItemCreateRequest.builder()
                .templateId(id)
                .title(request.title())
                .assigneeId(request.assigneeId())
                .createdBy(request.createdBy())
                .build();
        var wi = templateService.createFromTemplate(createRequest);
        return ViewMapper.toView(wi);
    }

    private Map<String, Object> toResponse(WorkItemTemplate t) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id);
        m.put("version", t.version);
        m.put("name", t.name);
        m.put("description", t.description);
        m.put("typePaths", t.typePaths);
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
        m.put("outcomes", t.outcomes == null ? null : WorkItemTemplateService.decodeOutcomes(t.outcomes));
        m.put("inputDataSchema", t.inputDataSchema);
        m.put("outputDataSchema", t.outputDataSchema);
        m.put("excludedUsers", t.excludedUsers);
        m.put("excludedGroups", t.excludedGroups);
        m.put("scope", t.scope);
        m.put("createdBy", t.createdBy);
        m.put("createdAt", t.createdAt);
        return m;
    }

    private static void validateSchema(JsonNode schema, String fieldName) {
        if (schema != null && !schema.isObject()) {
            throw new IllegalArgumentException(fieldName + " must be a JSON object (Schema), not a "
                    + schema.getNodeType().name().toLowerCase());
        }
    }

    private static void validateSchemaNode(JsonNode schema, String fieldName) {
        if (!schema.isObject()) {
            throw new IllegalArgumentException(fieldName + " must be a JSON object, not a "
                    + schema.getNodeType().name().toLowerCase());
        }
    }

    private static String textOrNull(JsonNode patch, String field) {
        JsonNode node = patch.get(field);
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private static Integer intOrNull(JsonNode patch, String field) {
        JsonNode node = patch.get(field);
        return (node == null || node.isNull()) ? null : node.intValue();
    }
}

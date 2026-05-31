package io.casehub.work.runtime.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.logging.Logger;
import io.casehub.work.api.Outcome;
import io.casehub.work.api.LabelPersistence;
import io.casehub.work.runtime.model.OutcomeCodecs;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.multiinstance.MultiInstanceSpawnService;

/**
 * Service for creating and instantiating {@link WorkItemTemplate} records.
 *
 * <p>
 * The unit-testable static methods ({@link #toCreateRequest} and {@link #parseLabels})
 * contain the pure mapping logic with no CDI or JPA dependencies. The CDI methods
 * ({@link #instantiate}) delegate to these statics for easy testing.
 */
@ApplicationScoped
public class WorkItemTemplateService {

    private static final Logger LOG = Logger.getLogger(WorkItemTemplateService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    WorkItemService workItemService;

    @Inject
    Instance<MultiInstanceSpawnService> multiInstanceSpawnService;

    @Inject
    TemplateExpander templateExpander;

    /**
     * Instantiate a {@link WorkItemTemplate} into a new PENDING {@link WorkItem}.
     *
     * <p>
     * The WorkItem is created with the template's defaults. The caller may supply:
     * <ul>
     * <li>{@code titleOverride} — if null, {@link WorkItemTemplate#name} is used as the title</li>
     * <li>{@code assigneeIdOverride} — if non-null, the WorkItem is pre-assigned</li>
     * <li>{@code createdBy} — who or what triggered the instantiation</li>
     * </ul>
     *
     * <p>
     * After the WorkItem is created, any labels from {@link WorkItemTemplate#labelPaths}
     * are applied as MANUAL labels. This happens inside the same transaction.
     *
     * @param template the template to instantiate; must not be null
     * @param titleOverride optional title; defaults to template name
     * @param assigneeIdOverride optional direct assignee; overrides candidateGroups routing
     * @param createdBy the actor (user or system) triggering instantiation
     * @return the newly created PENDING WorkItem with all template defaults applied
     */
    @Transactional
    public WorkItem instantiate(
            final WorkItemTemplate template,
            final String titleOverride,
            final String assigneeIdOverride,
            final String createdBy) {
        return instantiate(template, titleOverride, assigneeIdOverride, createdBy, null);
    }

    /**
     * Instantiate a {@link WorkItemTemplate} into a new PENDING {@link WorkItem}.
     *
     * <p>
     * When invoked via the 4-arg overload, this method runs within the caller's
     * transaction — the 4-arg delegates via a direct Java call ({@code this.instantiate(...)})
     * which bypasses the CDI proxy and does not apply this method's own
     * {@code @Transactional}. The annotation is active only for direct external callers
     * of this 5-arg overload.
     *
     * @param template the template to instantiate; must not be null
     * @param titleOverride optional title; defaults to template name
     * @param assigneeIdOverride optional direct assignee; overrides candidateGroups routing
     * @param createdBy the actor (user or system) triggering instantiation
     * @param callerRef opaque routing key for engine adapters; null for human-initiated creation.
     *                  For multi-instance templates, stored on the parent WorkItem so lifecycle
     *                  events carry the routing signal to engine adapters.
     * @return the newly created PENDING WorkItem with all template defaults applied
     */
    @Transactional
    public WorkItem instantiate(
            final WorkItemTemplate template,
            final String titleOverride,
            final String assigneeIdOverride,
            final String createdBy,
            final String callerRef) {
        return instantiate(template, titleOverride, assigneeIdOverride, createdBy, callerRef, null);
    }

    /**
     * Instantiate a {@link WorkItemTemplate} into a new PENDING {@link WorkItem},
     * optionally overriding the payload with engine-provided context data.
     *
     * <p>
     * For multi-instance templates, {@code payloadOverride} is not forwarded to
     * {@code MultiInstanceSpawnService} in this release — the template's
     * {@link WorkItemTemplate#defaultPayload} is used for each child instance.
     *
     * @param template the template to instantiate; must not be null
     * @param titleOverride optional title; defaults to template name
     * @param assigneeIdOverride optional direct assignee; overrides candidateGroups routing
     * @param createdBy the actor (user or system) triggering instantiation
     * @param callerRef opaque routing key for engine adapters; null for human-initiated creation
     * @param payloadOverride if non-null and non-blank, used as the WorkItem payload instead of
     *                        {@link WorkItemTemplate#defaultPayload}; enables engine adapters to
     *                        inject case context ({@code inputMapping} output) into the task
     * @return the newly created PENDING WorkItem with all template defaults applied
     */
    @Transactional
    public WorkItem instantiate(
            final WorkItemTemplate template,
            final String titleOverride,
            final String assigneeIdOverride,
            final String createdBy,
            final String callerRef,
            final String payloadOverride) {

        // Expand excludedGroups → actor IDs before any creation path
        final String expandedExcludedUsers = templateExpander.expandExcludedUsers(template);

        if (template.instanceCount != null) {
            if (payloadOverride != null && !payloadOverride.isBlank()) {
                // payloadOverride is not forwarded to multi-instance spawning in this release
                LOG.warnf("payloadOverride ignored for multi-instance template '%s' (casehubio/work#175)", template.id);
            }
            return multiInstanceSpawnService.get()
                    .createGroup(template, expandedExcludedUsers, titleOverride, createdBy, callerRef);
        }

        WorkItemCreateRequest request =
            toCreateRequest(template, titleOverride, assigneeIdOverride, createdBy, callerRef, payloadOverride);
        // Only rebuild when expansion actually added new actor IDs; no-op when all group members
        // were already in excludedUsers or when groups resolved to empty (NoOp provider).
        if (expandedExcludedUsers != null && !expandedExcludedUsers.equals(request.excludedUsers)) {
            final String auditDetail = buildExpansionAuditDetail(template, expandedExcludedUsers);
            request = request.toBuilder()
                    .excludedUsers(expandedExcludedUsers)
                    .auditDetail(auditDetail)
                    .build();
        }
        WorkItem workItem = workItemService.create(request);

        // Apply template labels as MANUAL — the filter engine may add INFERRED on top
        final List<WorkItemLabel> labels = parseLabels(template);
        for (final WorkItemLabel label : labels) {
            workItem = workItemService.addLabel(workItem.id, label.path, label.appliedBy);
        }

        return workItem;
    }

    /**
     * Convert a template and optional overrides into a {@link WorkItemCreateRequest}.
     *
     * <p>
     * Static for unit testability — no CDI or JPA dependency.
     *
     * @param template the template providing defaults
     * @param titleOverride if non-null and non-blank, used as the title; otherwise template name
     * @param assigneeIdOverride if non-null, set as the direct assignee
     * @param createdBy the actor triggering the instantiation
     * @return the create request ready for {@link WorkItemService#create}
     */
    public static WorkItemCreateRequest toCreateRequest(
            final WorkItemTemplate template,
            final String titleOverride,
            final String assigneeIdOverride,
            final String createdBy) {
        return toCreateRequest(template, titleOverride, assigneeIdOverride, createdBy, null, null);
    }

    /**
     * Convert a template and optional overrides into a {@link WorkItemCreateRequest}.
     *
     * <p>
     * Static for unit testability — no CDI or JPA dependency.
     *
     * @param template the template providing defaults
     * @param titleOverride if non-null and non-blank, used as the title; otherwise template name
     * @param assigneeIdOverride if non-null, set as the direct assignee
     * @param createdBy the actor triggering the instantiation
     * @param callerRef opaque routing key set by engine adapters (null for human-initiated creation)
     * @return the create request ready for {@link WorkItemService#create}
     */
    public static WorkItemCreateRequest toCreateRequest(
            final WorkItemTemplate template,
            final String titleOverride,
            final String assigneeIdOverride,
            final String createdBy,
            final String callerRef) {
        return toCreateRequest(template, titleOverride, assigneeIdOverride, createdBy, callerRef, null);
    }

    /**
     * Convert a template and optional overrides into a {@link WorkItemCreateRequest}.
     *
     * <p>
     * Static for unit testability — no CDI or JPA dependency.
     *
     * @param template the template providing defaults
     * @param titleOverride if non-null and non-blank, used as the title; otherwise template name
     * @param assigneeIdOverride if non-null, set as the direct assignee
     * @param createdBy the actor triggering the instantiation
     * @param callerRef opaque routing key set by engine adapters (null for human-initiated creation)
     * @param payloadOverride if non-null and non-blank, used as the payload instead of
     *                        {@link WorkItemTemplate#defaultPayload}; null falls back to template default
     * @return the create request ready for {@link WorkItemService#create}
     */
    public static WorkItemCreateRequest toCreateRequest(
            final WorkItemTemplate template,
            final String titleOverride,
            final String assigneeIdOverride,
            final String createdBy,
            final String callerRef,
            final String payloadOverride) {

        final String title = (titleOverride != null && !titleOverride.isBlank())
                ? titleOverride
                : template.name;

        final String payload = mergePayload(template.defaultPayload, payloadOverride);

        return WorkItemCreateRequest.builder()
                .title(title)
                .description(template.description)
                .category(template.category)
                .priority(template.priority)
                .assigneeId(assigneeIdOverride)
                .candidateGroups(template.candidateGroups)
                .candidateUsers(template.candidateUsers)
                .requiredCapabilities(template.requiredCapabilities)
                .createdBy(createdBy)
                .payload(payload)
                .callerRef(callerRef)
                .claimDeadlineBusinessHours(template.defaultClaimBusinessHours)
                .expiresAtBusinessHours(template.defaultExpiryBusinessHours)
                .templateId(template.id)
                .permittedOutcomes(parseOutcomeNames(template.outcomes))
                .inputDataSchema(template.inputDataSchema)
                .outputDataSchema(template.outputDataSchema)
                .excludedUsers(template.excludedUsers)
                .scope(template.scope)
                .build();
    }

    /**
     * Deep-merges two JSON payloads. When both are JSON objects, overlay keys win on conflict
     * and nested objects are merged recursively. When either is null/blank, the other is returned.
     * When either value is not a JSON object (array, scalar, etc.), the overlay wins entirely.
     * Malformed JSON in either argument falls back to returning the overlay unchanged.
     */
    static String mergePayload(final String base, final String overlay) {
        final boolean baseBlank = base == null || base.isBlank();
        final boolean overlayBlank = overlay == null || overlay.isBlank();
        if (baseBlank && overlayBlank) return null;
        if (baseBlank) return overlay;
        if (overlayBlank) return base;
        try {
            final JsonNode baseNode = MAPPER.readTree(base);
            final JsonNode overlayNode = MAPPER.readTree(overlay);
            // Non-object types (arrays, scalars) — overlay wins; no merge possible
            if (!baseNode.isObject() || !overlayNode.isObject()) return overlay;
            return MAPPER.writeValueAsString(deepMerge((ObjectNode) baseNode.deepCopy(), overlayNode));
        } catch (final Exception e) {
            // Malformed JSON — fall back to overlay to preserve the caller's intent
            return overlay;
        }
    }

    private static ObjectNode deepMerge(final ObjectNode base, final JsonNode overlay) {
        overlay.fields().forEachRemaining(entry -> {
            final JsonNode baseValue = base.get(entry.getKey());
            if (baseValue != null && baseValue.isObject() && entry.getValue().isObject()) {
                base.set(entry.getKey(), deepMerge((ObjectNode) baseValue.deepCopy(), entry.getValue()));
            } else {
                base.set(entry.getKey(), entry.getValue());
            }
        });
        return base;
    }

    private static String buildExpansionAuditDetail(final WorkItemTemplate template,
                                                     final String expandedExcludedUsers) {
        if (template.excludedGroups == null || template.excludedGroups.isBlank()) return null;
        final long before = template.excludedUsers == null ? 0L
                : java.util.Arrays.stream(template.excludedUsers.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).count();
        final long after = expandedExcludedUsers == null ? 0L
                : java.util.Arrays.stream(expandedExcludedUsers.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).count();
        final long added = after - before;
        if (added <= 0) return null;
        return "excludedGroups=[\"" + template.excludedGroups.trim() + "\"] resolved to " + added + " actor(s)";
    }

    /** @see OutcomeCodecs#parseOutcomeNames */
    public static List<String> parseOutcomeNames(final String outcomesJson) {
        return OutcomeCodecs.parseOutcomeNames(outcomesJson);
    }

    /** @see OutcomeCodecs#encodeOutcomes */
    public static String encodeOutcomes(final List<Outcome> outcomes) {
        return OutcomeCodecs.encodeOutcomes(outcomes);
    }

    /** @see OutcomeCodecs#encodePermittedOutcomes */
    public static String encodePermittedOutcomes(final List<String> names) {
        return OutcomeCodecs.encodePermittedOutcomes(names);
    }

    /** @see OutcomeCodecs#decodePermittedOutcomes */
    public static List<String> decodePermittedOutcomes(final String permittedOutcomesJson) {
        return OutcomeCodecs.decodePermittedOutcomes(permittedOutcomesJson);
    }

    /** @see OutcomeCodecs#decodeOutcomes */
    public static List<Outcome> decodeOutcomes(final String outcomesJson) {
        return OutcomeCodecs.decodeOutcomes(outcomesJson);
    }

    /**
     * Parse the template's {@link WorkItemTemplate#labelPaths} JSON array into
     * {@link WorkItemLabel} instances ready to be applied at instantiation.
     *
     * <p>
     * Returns an empty list if {@code labelPaths} is null, blank, or invalid JSON.
     * Labels are created with {@link LabelPersistence#MANUAL} and {@code appliedBy = "template"}.
     *
     * <p>
     * Static for unit testability — no CDI or JPA dependency.
     *
     * @param template the template whose labels are to be parsed
     * @return list of {@link WorkItemLabel} ready for application; may be empty
     */
    public static List<WorkItemLabel> parseLabels(final WorkItemTemplate template) {
        if (template.labelPaths == null || template.labelPaths.isBlank()) {
            return List.of();
        }
        try {
            final List<String> paths = MAPPER.readValue(template.labelPaths, new TypeReference<>() {
            });
            final List<WorkItemLabel> result = new ArrayList<>();
            for (final String path : paths) {
                if (path != null && !path.isBlank()) {
                    result.add(new WorkItemLabel(path, LabelPersistence.MANUAL, "template"));
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Find a template by ID.
     *
     * @param templateId the UUID
     * @return the template, or empty if not found
     */
    @Transactional
    public Optional<WorkItemTemplate> findById(final UUID templateId) {
        return Optional.ofNullable(WorkItemTemplate.findById(templateId));
    }

    /**
     * Find a template by exact name.
     *
     * @param name the template name to look up
     * @return the matching template, or empty if none found
     * @throws IllegalStateException if more than one template shares this name — a
     *         configuration error; the operator must deduplicate before the ref is usable.
     *         A DB-level UNIQUE constraint is the correct long-term enforcement
     *         (tracked as casehubio/work#174).
     */
    @Transactional
    public Optional<WorkItemTemplate> findByName(final String name) {
        return WorkItemTemplate.find("name", name).firstResultOptional();
    }

    /**
     * Resolve a template by UUID string or name.
     *
     * <p>If {@code templateRef} parses as a UUID, resolution is by ID. Otherwise resolution
     * is by name via {@link #findByName}.
     *
     * @param templateRef a UUID string or a template name
     * @return the matching template, or empty if not found
     * @throws IllegalStateException if name resolution finds multiple matches
     */
    public Optional<WorkItemTemplate> findByRef(final String templateRef) {
        final UUID id;
        try {
            id = UUID.fromString(templateRef);
        } catch (IllegalArgumentException e) {
            return findByName(templateRef);
        }
        return findById(id);
    }
}

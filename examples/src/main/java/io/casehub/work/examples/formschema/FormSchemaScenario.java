package io.casehub.work.examples.formschema;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.logging.Logger;

import io.casehub.work.examples.StepLog;
import io.casehub.work.runtime.api.AuditEntryResponse;
import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.work.runtime.service.WorkItemTemplateService;

/**
 * Scenario 7 — Output Schema Validation: register a template with an outputDataSchema,
 * instantiate WorkItems from it, and verify that valid resolutions succeed while
 * invalid ones are rejected.
 *
 * <p>
 * A legal team registers a WorkItemTemplate with a JSON Schema on the resolution field
 * (outputDataSchema). A correctly structured resolution is accepted; an invalid one
 * is rejected with an {@code IllegalArgumentException}.
 *
 * <p>
 * Steps: create template → instantiate → claim → start → complete with valid resolution →
 * instantiate second → claim → start → attempt invalid resolution → verify rejection.
 *
 * <p>
 * Actors: {@code legal-admin} (template creator), {@code legal-reviewer} (WorkItem completer).
 *
 * <p>
 * Endpoint: {@code POST /examples/formschema/run}
 */
@Path("/examples/formschema")
@Produces(MediaType.APPLICATION_JSON)
public class FormSchemaScenario {

    private static final Logger LOG = Logger.getLogger(FormSchemaScenario.class);

    private static final String SCENARIO_ID = "form-schema";
    private static final String ACTOR_ADMIN = "legal-admin";
    private static final String ACTOR_REVIEWER = "legal-reviewer";
    private static final String CATEGORY = "contract-review";
    private static final String TEMPLATE_NAME = "Contract Review Form";

    private static final String OUTPUT_DATA_SCHEMA = """
            {
              "type": "object",
              "required": ["decision"],
              "properties": {
                "decision": { "type": "string" }
              },
              "additionalProperties": false
            }
            """;

    @Inject
    WorkItemService workItemService;

    @Inject
    WorkItemTemplateService templateService;

    @Inject
    AuditEntryStore auditStore;

    /**
     * Run the output schema validation scenario end to end and return the outcome.
     *
     * @return scenario response confirming schema enforcement and rejection of invalid resolutions
     */
    @POST
    @Path("/run")
    @Transactional
    public FormSchemaResponse run() {
        final List<StepLog> steps = new ArrayList<>();
        final int total = 8;

        // Step 1: create a WorkItemTemplate with outputDataSchema
        final String description1 = "legal-admin creates a WorkItemTemplate with outputDataSchema requiring 'decision' field";
        LOG.infof("[SCENARIO] Step %d/%d: %s", 1, total, description1);
        final WorkItemTemplate template = new WorkItemTemplate();
        template.name = TEMPLATE_NAME;
        template.description = "Contract review requiring a structured resolution";
        template.category = CATEGORY;
        template.candidateGroups = ACTOR_REVIEWER;
        template.outputDataSchema = OUTPUT_DATA_SCHEMA;
        template.createdBy = ACTOR_ADMIN;
        template.persist();
        steps.add(new StepLog(1, description1, null));

        // Step 2: instantiate the template → first WorkItem
        final String description2 = "Instantiate template → first WorkItem for valid-resolution path";
        LOG.infof("[SCENARIO] Step %d/%d: %s", 2, total, description2);
        final WorkItem wi1 = templateService.instantiate(template, "Review Services Agreement: TechCorp Ltd",
                null, ACTOR_ADMIN);
        steps.add(new StepLog(2, description2, wi1.id));

        // Step 3: legal-reviewer claims and starts the first WorkItem
        final String description3 = "legal-reviewer claims and starts the first WorkItem";
        LOG.infof("[SCENARIO] Step %d/%d: %s", 3, total, description3);
        workItemService.claim(wi1.id, ACTOR_REVIEWER);
        workItemService.start(wi1.id, ACTOR_REVIEWER);
        steps.add(new StepLog(3, description3, wi1.id));

        // Step 4: complete with a valid resolution — succeeds
        final String description4 = "legal-reviewer completes with valid resolution {\"decision\":\"approved\"} — succeeds";
        LOG.infof("[SCENARIO] Step %d/%d: %s", 4, total, description4);
        workItemService.complete(wi1.id, ACTOR_REVIEWER, "{\"decision\": \"approved\"}", null);
        steps.add(new StepLog(4, description4, wi1.id));

        // Step 5: instantiate the template again → second WorkItem for the invalid-resolution path
        final String description5 = "Instantiate template → second WorkItem for invalid-resolution path";
        LOG.infof("[SCENARIO] Step %d/%d: %s", 5, total, description5);
        final WorkItem wi2 = templateService.instantiate(template, "Review SaaS Agreement: Acme Corp",
                null, ACTOR_ADMIN);
        steps.add(new StepLog(5, description5, wi2.id));

        // Step 6: claim and start the second WorkItem
        final String description6 = "legal-reviewer claims and starts the second WorkItem";
        LOG.infof("[SCENARIO] Step %d/%d: %s", 6, total, description6);
        workItemService.claim(wi2.id, ACTOR_REVIEWER);
        workItemService.start(wi2.id, ACTOR_REVIEWER);
        steps.add(new StepLog(6, description6, wi2.id));

        // Step 7: attempt to complete with an invalid resolution — expect rejection
        final String description7 = "Attempt to complete with invalid resolution {\"wrong_field\":\"value\"} — expect rejection";
        LOG.infof("[SCENARIO] Step %d/%d: %s", 7, total, description7);
        boolean invalidRejected = false;
        try {
            workItemService.complete(wi2.id, ACTOR_REVIEWER, "{\"wrong_field\": \"value\"}", null);
        } catch (final IllegalArgumentException e) {
            invalidRejected = true;
            LOG.infof("[SCENARIO] Step %d/%d: invalid resolution correctly rejected — %s", 7, total, e.getMessage());
        }
        if (!invalidRejected) {
            throw new IllegalStateException("Invalid resolution was not rejected — outputDataSchema enforcement failed");
        }
        steps.add(new StepLog(7, description7 + " — rejected=" + invalidRejected, wi2.id));

        // Step 8: collect audit trail for the successfully completed WorkItem
        final String description8 = "Collect audit trail for the successfully completed WorkItem";
        LOG.infof("[SCENARIO] Step %d/%d: %s", 8, total, description8);
        final List<AuditEntry> auditEntries = auditStore.findByWorkItemId(wi1.id);
        final List<AuditEntryResponse> auditTrail = auditEntries.stream()
                .map(a -> new AuditEntryResponse(a.id, a.event, a.actor, a.detail, a.occurredAt))
                .toList();
        steps.add(new StepLog(8, description8 + " — " + auditEntries.size() + " entries", null));

        return new FormSchemaResponse(
                SCENARIO_ID,
                steps,
                template.id,
                template.name,
                wi1.id,
                invalidRejected,
                auditTrail);
    }
}

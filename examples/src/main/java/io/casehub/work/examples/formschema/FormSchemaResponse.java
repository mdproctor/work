package io.casehub.work.examples.formschema;

import java.util.List;
import java.util.UUID;

import io.casehub.work.examples.StepLog;
import io.casehub.work.runtime.api.AuditEntryResponse;

/**
 * Response returned by the output schema validation scenario.
 *
 * @param scenario identifier of the scenario
 * @param steps chronological log of each step taken
 * @param templateId UUID of the {@code WorkItemTemplate} created during the scenario
 * @param templateName display name of the template
 * @param workItemId UUID of the first WorkItem (completed with valid resolution)
 * @param invalidRejected {@code true} if the invalid resolution was correctly rejected
 * @param auditTrail all audit entries for the first WorkItem
 */
public record FormSchemaResponse(
        String scenario,
        List<StepLog> steps,
        UUID templateId,
        String templateName,
        UUID workItemId,
        boolean invalidRejected,
        List<AuditEntryResponse> auditTrail) {
}

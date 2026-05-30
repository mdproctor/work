package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.LabelPersistence;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemTemplate;

/**
 * Pure unit tests for WorkItemTemplate → WorkItemCreateRequest mapping.
 * No Quarkus, no DB — exercises the template-to-request conversion logic only.
 */
class WorkItemTemplateServiceTest {

    // ── toCreateRequest: template defaults ────────────────────────────────────

    @Test
    void toCreateRequest_usesTemplateNameAsTitle_whenNoOverride() {
        final WorkItemTemplate t = template("Contract Review");
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.title).isEqualTo("Contract Review");
    }

    @Test
    void toCreateRequest_usesOverrideTitle_whenProvided() {
        final WorkItemTemplate t = template("Default Title");
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, "Specific Contract #44", null, "system");
        assertThat(req.title).isEqualTo("Specific Contract #44");
    }

    @Test
    void toCreateRequest_copiesCategory() {
        final WorkItemTemplate t = template("T");
        t.category = "legal";
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.category).isEqualTo("legal");
    }

    @Test
    void toCreateRequest_copiesPriority() {
        final WorkItemTemplate t = template("T");
        t.priority = WorkItemPriority.HIGH;
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.priority).isEqualTo(WorkItemPriority.HIGH);
    }

    @Test
    void toCreateRequest_copiesCandidateGroups() {
        final WorkItemTemplate t = template("T");
        t.candidateGroups = "legal-team,compliance-team";
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.candidateGroups).isEqualTo("legal-team,compliance-team");
    }

    @Test
    void toCreateRequest_copiesDefaultPayload() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = "{\"type\":\"nda\"}";
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.payload).isEqualTo("{\"type\":\"nda\"}");
    }

    @Test
    void toCreateRequest_copiesScope() {
        final WorkItemTemplate t = template("T");
        t.scope = "casehubio/devtown/pr-review";
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.scope).isEqualTo("casehubio/devtown/pr-review");
    }

    @Test
    void toCreateRequest_scopeIsNullWhenTemplateHasNoScope() {
        final WorkItemTemplate t = template("T");
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.scope).isNull();
    }

    @Test
    void toCreateRequest_setsCreatedBy() {
        final WorkItemTemplate t = template("T");
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "finance-bot");
        assertThat(req.createdBy).isEqualTo("finance-bot");
    }

    @Test
    void toCreateRequest_setsAssigneeOverride_whenProvided() {
        final WorkItemTemplate t = template("T");
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, "alice", "system");
        assertThat(req.assigneeId).isEqualTo("alice");
    }

    @Test
    void toCreateRequest_nullFields_whenTemplateHasNoDefaults() {
        final WorkItemTemplate t = template("Minimal");
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.category).isNull();
        assertThat(req.priority).isNull();
        assertThat(req.candidateGroups).isNull();
        assertThat(req.payload).isNull();
        assertThat(req.assigneeId).isNull();
    }

    @Test
    void toCreateRequest_setsCallerRef_whenProvided() {
        final WorkItemTemplate t = template("IRB Review");
        final String callerRef = "case:550e8400-e29b-41d4-a716-446655440000/pi:irb-gate";
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system", callerRef);
        assertThat(req.callerRef).isEqualTo(callerRef);
    }

    @Test
    void toCreateRequest_callerRefNull_whenNotProvided() {
        final WorkItemTemplate t = template("IRB Review");
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.callerRef).isNull();
    }

    // ── parseLabels ───────────────────────────────────────────────────────────

    @Test
    void parseLabels_returnsEmptyList_whenNull() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = null;
        assertThat(WorkItemTemplateService.parseLabels(t)).isEmpty();
    }

    @Test
    void parseLabels_parsesJsonArray() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = "[\"intake/triage\",\"priority/high\"]";
        final var labels = WorkItemTemplateService.parseLabels(t);
        assertThat(labels).hasSize(2);
        assertThat(labels).extracting(l -> l.path)
                .containsExactly("intake/triage", "priority/high");
        assertThat(labels).extracting(l -> l.persistence)
                .containsOnly(LabelPersistence.MANUAL);
    }

    @Test
    void parseLabels_handlesEmptyArray() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = "[]";
        assertThat(WorkItemTemplateService.parseLabels(t)).isEmpty();
    }

    @Test
    void parseLabels_setsAppliedByToTemplate() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = "[\"security/incident\"]";
        final var labels = WorkItemTemplateService.parseLabels(t);
        assertThat(labels.get(0).appliedBy).isEqualTo("template");
    }

    // ── toCreateRequest: payloadOverride ─────────────────────────────────────

    @Test
    void toCreateRequest_payloadOverride_nonNull_usesOverride() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = "{\"type\":\"default\"}";
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system", null, "{\"type\":\"override\"}");
        assertThat(req.payload).isEqualTo("{\"type\":\"override\"}");
    }

    @Test
    void toCreateRequest_payloadOverride_null_usesTemplateDefault() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = "{\"type\":\"default\"}";
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system", null, null);
        assertThat(req.payload).isEqualTo("{\"type\":\"default\"}");
    }

    @Test
    void toCreateRequest_payloadOverride_blank_usesTemplateDefault() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = "{\"type\":\"default\"}";
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system", null, "   ");
        assertThat(req.payload).isEqualTo("{\"type\":\"default\"}");
    }

    @Test
    void toCreateRequest_payloadOverride_noTemplateDefault_usesOverride() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = null;
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system", null, "{\"case\":\"42\"}");
        assertThat(req.payload).isEqualTo("{\"case\":\"42\"}");
    }

    @Test
    void toCreateRequest_payloadOverride_neitherSet_payloadIsNull() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = null;
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system", null, null);
        assertThat(req.payload).isNull();
    }

    @Test
    void toCreateRequest_mergePayload_disjointKeys_bothPreserved() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = "{\"type\":\"default\"}";
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system", null, "{\"case\":\"42\"}");
        assertThat(req.payload).contains("\"type\":\"default\"");
        assertThat(req.payload).contains("\"case\":\"42\"");
    }

    @Test
    void toCreateRequest_mergePayload_overlayKeyWinsConflict_baseUniqueKeysPreserved() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = "{\"type\":\"base\",\"extra\":\"kept\"}";
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system", null, "{\"type\":\"override\"}");
        assertThat(req.payload).contains("\"type\":\"override\"");
        assertThat(req.payload).contains("\"extra\":\"kept\"");
    }

    @Test
    void toCreateRequest_mergePayload_nestedObjects_deepMerged() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = "{\"data\":{\"x\":1}}";
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system", null, "{\"data\":{\"y\":2}}");
        assertThat(req.payload).contains("\"x\":1");
        assertThat(req.payload).contains("\"y\":2");
    }

    @Test
    void toCreateRequest_mergePayload_nonObjectOverlay_overlayWins() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = "{\"type\":\"base\"}";
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system", null, "\"just a string\"");
        assertThat(req.payload).isEqualTo("\"just a string\"");
    }

    @Test
    void toCreateRequest_mergePayload_arrayOverlay_overlayWins() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = "{\"type\":\"base\"}";
        final WorkItemCreateRequest req =
            WorkItemTemplateService.toCreateRequest(t, null, null, "system", null, "[1,2,3]");
        assertThat(req.payload).isEqualTo("[1,2,3]");
    }

    @Test
    void mergePayload_bothNull_returnsNull() {
        assertThat(WorkItemTemplateService.mergePayload(null, null)).isNull();
    }

    @Test
    void mergePayload_malformedBase_returnsOverlay() {
        assertThat(WorkItemTemplateService.mergePayload("not-json{{{", "{\"key\":\"value\"}"))
                .isEqualTo("{\"key\":\"value\"}");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private WorkItemTemplate template(final String name) {
        final WorkItemTemplate t = new WorkItemTemplate();
        t.name = name;
        t.createdBy = "admin";
        return t;
    }
}

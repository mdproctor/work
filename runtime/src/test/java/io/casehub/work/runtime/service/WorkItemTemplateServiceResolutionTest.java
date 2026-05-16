package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link WorkItemTemplateService#findByName} and
 * {@link WorkItemTemplateService#findByRef}. Requires a running CDI container and
 * Panache (H2 in test). Refs casehubio/engine#255.
 */
@QuarkusTest
class WorkItemTemplateServiceResolutionTest {

    @Inject
    WorkItemTemplateService templateService;

    @BeforeEach
    @Transactional
    void clearTemplates() {
        WorkItemTemplate.deleteAll();
    }

    // ── findByName ────────────────────────────────────────────────────────────

    @Test
    void findByName_uniqueMatch_returnsTemplate() {
        persist("IRB Ethics Review");

        final Optional<WorkItemTemplate> result = templateService.findByName("IRB Ethics Review");

        assertThat(result).isPresent();
        assertThat(result.get().name).isEqualTo("IRB Ethics Review");
    }

    @Test
    void findByName_noMatch_returnsEmpty() {
        final Optional<WorkItemTemplate> result = templateService.findByName("Does Not Exist");

        assertThat(result).isEmpty();
    }

    @Test
    void findByName_multipleMatches_throwsIllegalStateException() {
        persist("Duplicate Template");
        persist("Duplicate Template");

        assertThatThrownBy(() -> templateService.findByName("Duplicate Template"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Ambiguous template name")
            .hasMessageContaining("Duplicate Template")
            .hasMessageContaining("2");
    }

    // ── findByRef ─────────────────────────────────────────────────────────────

    @Test
    void findByRef_validUuid_resolvesById() {
        final WorkItemTemplate saved = persist("Loan Approval");

        final Optional<WorkItemTemplate> result = templateService.findByRef(saved.id.toString());

        assertThat(result).isPresent();
        assertThat(result.get().id).isEqualTo(saved.id);
    }

    @Test
    void findByRef_validName_resolvesByName() {
        persist("Security Incident Triage");

        final Optional<WorkItemTemplate> result = templateService.findByRef("Security Incident Triage");

        assertThat(result).isPresent();
        assertThat(result.get().name).isEqualTo("Security Incident Triage");
    }

    @Test
    void findByRef_unknownUuid_returnsEmpty() {
        final Optional<WorkItemTemplate> result =
            templateService.findByRef(UUID.randomUUID().toString());

        assertThat(result).isEmpty();
    }

    @Test
    void findByRef_unknownName_returnsEmpty() {
        final Optional<WorkItemTemplate> result = templateService.findByRef("Unknown Template");

        assertThat(result).isEmpty();
    }

    @Test
    void findByRef_ambiguousName_throwsIllegalStateException() {
        persist("AML Review");
        persist("AML Review");

        assertThatThrownBy(() -> templateService.findByRef("AML Review"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Ambiguous template name");
    }

    // ── instantiate: payloadOverride ─────────────────────────────────────────

    @Test
    void instantiate_payloadOverride_usedInsteadOfTemplateDefault() {
        final WorkItemTemplate t = persist("Clinical Trial Consent");
        t.defaultPayload = "{\"type\":\"default\"}";

        final var workItem = templateService.instantiate(
            t, null, null, "casehub-engine", "case:x/pi:y", "{\"trialId\":\"T-99\"}");

        assertThat(workItem).isNotNull();
        assertThat(workItem.payload).isEqualTo("{\"trialId\":\"T-99\"}");
    }

    @Test
    void instantiate_nullPayloadOverride_usesTemplateDefault() {
        final WorkItemTemplate t = persist("AML Check");
        t.defaultPayload = "{\"type\":\"aml\"}";

        final var workItem = templateService.instantiate(
            t, null, null, "casehub-engine", "case:x/pi:y", null);

        assertThat(workItem).isNotNull();
        assertThat(workItem.payload).isEqualTo("{\"type\":\"aml\"}");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @Transactional
    WorkItemTemplate persist(final String name) {
        final WorkItemTemplate t = new WorkItemTemplate();
        t.name = name;
        t.createdBy = "test";
        WorkItemTemplate.persist(t);
        return t;
    }
}

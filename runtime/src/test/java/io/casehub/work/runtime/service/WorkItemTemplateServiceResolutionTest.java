package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.casehub.platform.api.identity.GroupMember;
import io.casehub.platform.api.identity.GroupMembershipProvider;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

    /**
     * Configurable @Alternative GroupMembershipProvider for tests.
     * Takes priority over NoOpGroupMembershipProvider @DefaultBean.
     */
    @Alternative
    @Priority(1)
    @ApplicationScoped
    public static class TestGroupMembershipProvider implements GroupMembershipProvider {
        private static final Map<String, Set<GroupMember>> GROUPS = new java.util.concurrent.ConcurrentHashMap<>();

        public static void configure(String group, Set<GroupMember> members) {
            GROUPS.put(group, members);
        }

        public static void reset() {
            GROUPS.clear();
        }

        @Override
        public Set<GroupMember> membersOf(String groupName) {
            return GROUPS.getOrDefault(groupName, Set.of());
        }
    }

    @BeforeEach
    @Transactional
    void clearTemplates() {
        WorkItemTemplate.deleteAll();
        TestGroupMembershipProvider.reset();
    }

    @AfterEach
    void resetGroups() {
        TestGroupMembershipProvider.reset();
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

    // ── instantiate: payloadOverride ─────────────────────────────────────────

    @Test
    void instantiate_payloadOverride_deepMergedWithTemplateDefault() {
        final WorkItemTemplate t = persist("Clinical Trial Consent");
        t.defaultPayload = "{\"type\":\"default\"}";

        final var workItem = templateService.instantiate(
            t, null, null, "casehub-engine", "case:x/pi:y", "{\"trialId\":\"T-99\"}");

        assertThat(workItem).isNotNull();
        // Deep merge: disjoint keys from both are preserved; override wins on conflict
        assertThat(workItem.payload).contains("\"type\":\"default\"");
        assertThat(workItem.payload).contains("\"trialId\":\"T-99\"");
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

    // ── excludedGroups expansion ─────────────────────────────────────────────

    @Test
    void instantiate_excludedGroups_expandedIntoExcludedUsers() {
        TestGroupMembershipProvider.configure("legal-team",
                Set.of(new GroupMember("alice", "Alice")));

        final WorkItemTemplate t = persist("Contract Review");
        t.excludedGroups = "legal-team";

        final WorkItem workItem = templateService.instantiate(t, null, null, "system");

        assertThat(workItem.excludedUsers).contains("alice");
    }

    @Test
    void instantiate_excludedGroups_mergedWithExcludedUsers() {
        TestGroupMembershipProvider.configure("finance-team",
                Set.of(new GroupMember("bob", "Bob")));

        final WorkItemTemplate t = persist("Budget Approval");
        t.excludedUsers = "carol";
        t.excludedGroups = "finance-team";

        final WorkItem workItem = templateService.instantiate(t, null, null, "system");

        assertThat(workItem.excludedUsers).contains("carol");
        assertThat(workItem.excludedUsers).contains("bob");
    }

    @Test
    void instantiate_excludedGroups_unknownGroupProducesNullExcludedUsers() {
        // TestGroupMembershipProvider returns empty for unconfigured groups
        final WorkItemTemplate t = persist("Unopened Task");
        t.excludedGroups = "unknown-group";
        t.excludedUsers = null;

        final WorkItem workItem = templateService.instantiate(t, null, null, "system");

        assertThat(workItem.excludedUsers).isNull();
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

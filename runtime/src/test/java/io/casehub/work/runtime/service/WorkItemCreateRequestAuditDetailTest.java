package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.work.runtime.model.WorkItemCreateRequest;
import org.junit.jupiter.api.Test;

class WorkItemCreateRequestAuditDetailTest {

    @Test
    void builder_setsAuditDetail() {
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder()
                .title("T")
                .createdBy("system")
                .auditDetail("excludedGroups=[\"legal-team\"] resolved to 1 actor(s)")
                .build();
        assertThat(req.auditDetail).isEqualTo("excludedGroups=[\"legal-team\"] resolved to 1 actor(s)");
    }

    @Test
    void toBuilder_copiesAuditDetail() {
        final WorkItemCreateRequest original = WorkItemCreateRequest.builder()
                .title("T").createdBy("system").auditDetail("note").build();
        final WorkItemCreateRequest copy = original.toBuilder().build();
        assertThat(copy.auditDetail).isEqualTo("note");
    }

    @Test
    void auditDetail_defaultsToNull() {
        final WorkItemCreateRequest req = WorkItemCreateRequest.builder()
                .title("T").createdBy("system").build();
        assertThat(req.auditDetail).isNull();
    }
}

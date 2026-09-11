package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.casehub.work.api.WorkItem;
import io.casehub.work.runtime.model.WorkItemEntity;
import io.casehub.work.runtime.repository.WorkItemEntityMapper;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemRelation;
import io.casehub.work.api.WorkItemRelationType;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemRelationStore;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaWorkItemRelationStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that queries never leak data across tenant boundaries.
 */
@QuarkusTest
@TestTransaction
class JpaWorkItemRelationStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    WorkItemRelationStore store;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    /** Create a minimal WorkItem in the current tenant. */
    private WorkItem createWorkItem() {
        WorkItemEntity wi = new WorkItemEntity();
        wi.title = "test-" + UUID.randomUUID();
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.MEDIUM;
        wi.createdBy = "test";
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        wi.expiresAt = Instant.now().plusSeconds(3600);
        return workItemStore.put(WorkItemEntityMapper.toDomain(wi));
    }

    private WorkItemRelation newRelation(UUID sourceId, UUID targetId, String type) {
        WorkItemRelation r = new WorkItemRelation();
        r.sourceId = sourceId;
        r.targetId = targetId;
        r.relationType = type;
        r.createdBy = "test";
        r.createdAt = Instant.now();
        return r;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);
        WorkItem source = createWorkItem();
        WorkItem target = createWorkItem();

        WorkItemRelation r = newRelation(source.id(), target.id(), WorkItemRelationType.PART_OF);
        assertThat(r.tenancyId).isNull();

        store.put(r);

        assertThat(r.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void get_returnsEmpty_forAnotherTenantRelation() {
        principal.setTenancyId(TENANT_A);
        WorkItem         source = createWorkItem();
        WorkItem         target = createWorkItem();
        WorkItemRelation r      = newRelation(source.id(), target.id(), "BLOCKS");
        store.put(r);
        UUID id = r.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    @Test
    void findBySourceId_returnOnlyCurrentTenantRelations() {
        // Tenant A: create source and target, then create a relation
        principal.setTenancyId(TENANT_A);
        WorkItem sourceA = createWorkItem();
        WorkItem targetA = createWorkItem();
        store.put(newRelation(sourceA.id(), targetA.id(), "BLOCKS"));

        // Tenant B: create separate source and target
        principal.setTenancyId(TENANT_B);
        WorkItem sourceB = createWorkItem();
        WorkItem targetB = createWorkItem();
        store.put(newRelation(sourceB.id(), targetB.id(), "BLOCKS"));

        // As tenant B, only see B's relation
        List<WorkItemRelation> resultB = store.findBySourceId(sourceB.id());
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).tenancyId).isEqualTo(TENANT_B);

        // As tenant A, should not see B's relation for A's sourceId
        principal.setTenancyId(TENANT_A);
        List<WorkItemRelation> resultA = store.findBySourceId(sourceA.id());
        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void findByTargetAndType_returnOnlyCurrentTenantRelations() {
        principal.setTenancyId(TENANT_A);
        WorkItem source = createWorkItem();
        WorkItem target = createWorkItem();
        store.put(newRelation(source.id(), target.id(), WorkItemRelationType.PART_OF));

        principal.setTenancyId(TENANT_B);
        assertThat(store.findByTargetAndType(target.id(), WorkItemRelationType.PART_OF)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.findByTargetAndType(target.id(), WorkItemRelationType.PART_OF)).hasSize(1);
    }

    @Test
    void findExisting_tenantIsolated() {
        principal.setTenancyId(TENANT_A);
        WorkItem source = createWorkItem();
        WorkItem target = createWorkItem();
        store.put(newRelation(source.id(), target.id(), "BLOCKS"));

        principal.setTenancyId(TENANT_B);
        assertThat(store.findExisting(source.id(), target.id(), "BLOCKS")).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.findExisting(source.id(), target.id(), "BLOCKS")).isPresent();
    }

    @Test
    void delete_cannotDeleteAnotherTenantRelation() {
        principal.setTenancyId(TENANT_A);
        WorkItem         source = createWorkItem();
        WorkItem         target = createWorkItem();
        WorkItemRelation r      = newRelation(source.id(), target.id(), "BLOCKS");
        store.put(r);
        UUID id = r.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.delete(id)).isFalse();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
        assertThat(store.delete(id)).isTrue();
        assertThat(store.get(id)).isEmpty();
    }
}

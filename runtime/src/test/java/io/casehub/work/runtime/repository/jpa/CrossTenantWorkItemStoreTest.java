package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.CrossTenant;
import io.casehub.work.runtime.repository.CrossTenantWorkItemStore;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests that {@link CrossTenantWorkItemStore} sees items from all tenants,
 * while the tenant-scoped {@link WorkItemStore} respects tenant isolation.
 *
 * <p>Data is persisted in committed transactions (via {@link #inTx}) because the
 * cross-tenant store uses {@code @Transactional(REQUIRES_NEW)} — it cannot see
 * uncommitted data from the test's transaction.
 */
@QuarkusTest
class CrossTenantWorkItemStoreTest {

    @Inject
    WorkItemStore tenantStore;

    @Inject
    @CrossTenant
    CrossTenantWorkItemStore crossTenantStore;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void setUp() {
        principal.reset();
    }

    @Test
    void findActiveWithDeadlines_returns_items_from_all_tenants() {
        principal.setTenancyId("tenant-a");
        WorkItem itemA = inTx(() -> tenantStore.put(createItemWithExpiresAt("tenant-a")));

        principal.setTenancyId("tenant-b");
        WorkItem itemB = inTx(() -> tenantStore.put(createItemWithClaimDeadline("tenant-b")));

        var all = crossTenantStore.findActiveWithDeadlines();
        var created = all.stream()
            .filter(wi -> wi.id.equals(itemA.id) || wi.id.equals(itemB.id))
            .toList();
        assertThat(created).hasSize(2);
        assertThat(created.stream().map(wi -> wi.tenancyId).distinct().toList())
            .containsExactlyInAnyOrder("tenant-a", "tenant-b");
    }

    @Test
    void findActiveWithDeadlines_excludes_terminal_statuses() {
        principal.setTenancyId("tenant-a");

        WorkItem active = createItemWithExpiresAt("tenant-a");
        active.status = WorkItemStatus.IN_PROGRESS;
        inTx(() -> tenantStore.put(active));

        WorkItem completed = createItemWithExpiresAt("tenant-a");
        completed.status = WorkItemStatus.COMPLETED;
        inTx(() -> tenantStore.put(completed));

        WorkItem expired = createItemWithExpiresAt("tenant-a");
        expired.status = WorkItemStatus.EXPIRED;
        inTx(() -> tenantStore.put(expired));

        var results = crossTenantStore.findActiveWithDeadlines();
        assertThat(results.stream().anyMatch(wi -> wi.id.equals(active.id))).isTrue();
        assertThat(results.stream().noneMatch(wi -> wi.id.equals(completed.id))).isTrue();
        assertThat(results.stream().noneMatch(wi -> wi.id.equals(expired.id))).isTrue();
    }

    @Test
    void findActiveWithDeadlines_excludes_items_without_deadlines() {
        principal.setTenancyId("tenant-a");

        WorkItem withDeadline = createItemWithExpiresAt("tenant-a");
        inTx(() -> tenantStore.put(withDeadline));

        WorkItem withoutDeadline = new WorkItem();
        withoutDeadline.id = UUID.randomUUID();
        withoutDeadline.tenancyId = "tenant-a";
        withoutDeadline.title = "No deadline";
        withoutDeadline.status = WorkItemStatus.PENDING;
        withoutDeadline.priority = WorkItemPriority.MEDIUM;
        withoutDeadline.expiresAt = null;
        withoutDeadline.claimDeadline = null;
        inTx(() -> tenantStore.put(withoutDeadline));

        var results = crossTenantStore.findActiveWithDeadlines();
        assertThat(results.stream().anyMatch(wi -> wi.id.equals(withDeadline.id))).isTrue();
        assertThat(results.stream().noneMatch(wi -> wi.id.equals(withoutDeadline.id))).isTrue();
    }

    @Test
    @Transactional
    void tenantScopedStore_never_returns_cross_tenant_data() {
        principal.setTenancyId("tenant-a");
        WorkItem itemA = createItemWithExpiresAt("tenant-a");
        tenantStore.put(itemA);

        principal.setTenancyId("tenant-b");
        WorkItem itemB = createItemWithExpiresAt("tenant-b");
        tenantStore.put(itemB);

        principal.setCrossTenantAdmin(true);
        principal.setTenancyId("tenant-a");
        var results = tenantStore.scan(WorkItemQuery.all());
        assertThat(results).hasSize(1);
        assertThat(results).allSatisfy(wi -> assertThat(wi.tenancyId).isEqualTo("tenant-a"));

        principal.setTenancyId("tenant-b");
        results = tenantStore.scan(WorkItemQuery.all());
        assertThat(results).hasSize(1);
        assertThat(results).allSatisfy(wi -> assertThat(wi.tenancyId).isEqualTo("tenant-b"));
    }

    @Transactional
    <T> T inTx(Supplier<T> s) {
        return s.get();
    }

    @Transactional
    void inTx(Runnable r) {
        r.run();
    }

    private WorkItem createItemWithExpiresAt(String tenancyId) {
        WorkItem item = new WorkItem();
        item.id = UUID.randomUUID();
        item.tenancyId = tenancyId;
        item.title = "Item with expiresAt";
        item.status = WorkItemStatus.PENDING;
        item.priority = WorkItemPriority.MEDIUM;
        item.expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
        return item;
    }

    private WorkItem createItemWithClaimDeadline(String tenancyId) {
        WorkItem item = new WorkItem();
        item.id = UUID.randomUUID();
        item.tenancyId = tenancyId;
        item.title = "Item with claimDeadline";
        item.status = WorkItemStatus.ASSIGNED;
        item.priority = WorkItemPriority.MEDIUM;
        item.claimDeadline = Instant.now().plus(1, ChronoUnit.HOURS);
        return item;
    }
}

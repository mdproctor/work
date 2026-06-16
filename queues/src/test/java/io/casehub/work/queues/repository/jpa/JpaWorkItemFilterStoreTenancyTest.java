package io.casehub.work.queues.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.path.Path;
import io.casehub.work.queues.model.WorkItemFilter;
import io.casehub.work.queues.repository.WorkItemFilterStore;
import io.casehub.work.queues.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaWorkItemFilterStore}.
 */
@QuarkusTest
@TestTransaction
class JpaWorkItemFilterStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    WorkItemFilterStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    private WorkItemFilter newFilter(String name) {
        WorkItemFilter f = new WorkItemFilter();
        f.name = name;
        f.scope = Path.root();
        f.conditionLanguage = "jexl";
        f.conditionExpression = "true";
        f.active = true;
        return f;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);
        WorkItemFilter f = newFilter("stamp-test");
        assertThat(f.tenancyId).isNull();
        store.put(f);
        assertThat(f.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void get_returnsEmpty_forAnotherTenantItem() {
        principal.setTenancyId(TENANT_A);
        WorkItemFilter f = newFilter("get-isolation");
        store.put(f);
        UUID id = f.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    @Test
    void findActive_returnsOnlyCurrentTenantFilters() {
        principal.setTenancyId(TENANT_A);
        WorkItemFilter fA = newFilter("active-a");
        store.put(fA);

        principal.setTenancyId(TENANT_B);
        WorkItemFilter fB = newFilter("active-b");
        store.put(fB);

        principal.setTenancyId(TENANT_A);
        assertThat(store.findActive()).extracting(f -> f.id).contains(fA.id).doesNotContain(fB.id);

        principal.setTenancyId(TENANT_B);
        assertThat(store.findActive()).extracting(f -> f.id).contains(fB.id).doesNotContain(fA.id);
    }

    @Test
    void scanAll_returnsOnlyCurrentTenantFilters() {
        principal.setTenancyId(TENANT_A);
        WorkItemFilter fA = newFilter("scanall-a");
        store.put(fA);

        principal.setTenancyId(TENANT_B);
        WorkItemFilter fB = newFilter("scanall-b");
        store.put(fB);

        principal.setTenancyId(TENANT_A);
        assertThat(store.scanAll()).extracting(f -> f.id).contains(fA.id).doesNotContain(fB.id);

        principal.setTenancyId(TENANT_B);
        assertThat(store.scanAll()).extracting(f -> f.id).contains(fB.id).doesNotContain(fA.id);
    }

    @Test
    void delete_doesNotDeleteAnotherTenantItem() {
        principal.setTenancyId(TENANT_A);
        WorkItemFilter f = newFilter("delete-isolation");
        store.put(f);
        UUID id = f.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.delete(id)).isFalse();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
        assertThat(store.delete(id)).isTrue();
    }
}

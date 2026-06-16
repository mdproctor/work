package io.casehub.work.queues.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.path.Path;
import io.casehub.work.queues.model.FilterChain;
import io.casehub.work.queues.model.WorkItemFilter;
import io.casehub.work.queues.repository.FilterChainStore;
import io.casehub.work.queues.repository.WorkItemFilterStore;
import io.casehub.work.queues.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaFilterChainStore}.
 */
@QuarkusTest
@TestTransaction
class JpaFilterChainStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    FilterChainStore store;

    @Inject
    WorkItemFilterStore filterStore;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    /** Create a parent WorkItemFilter so FK constraints are satisfied. */
    private UUID createParentFilter(String name) {
        WorkItemFilter f = new WorkItemFilter();
        f.name = name;
        f.scope = Path.root();
        f.conditionLanguage = "jexl";
        f.conditionExpression = "true";
        f.active = true;
        filterStore.put(f);
        return f.id;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);
        UUID filterId = createParentFilter("chain-put-test");

        FilterChain fc = new FilterChain();
        fc.filterId = filterId;
        assertThat(fc.tenancyId).isNull();
        store.put(fc);
        assertThat(fc.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void findByFilterId_returnsEmpty_forAnotherTenant() {
        principal.setTenancyId(TENANT_A);
        UUID filterId = createParentFilter("chain-find-test");

        FilterChain fc = new FilterChain();
        fc.filterId = filterId;
        store.put(fc);

        principal.setTenancyId(TENANT_B);
        assertThat(store.findByFilterId(filterId)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.findByFilterId(filterId)).isPresent();
    }

    @Test
    void findOrCreateForFilter_createsScopedToCurrentTenant() {
        principal.setTenancyId(TENANT_A);
        UUID filterId = createParentFilter("chain-findorcreate-test");

        FilterChain fcA = store.findOrCreateForFilter(filterId);
        assertThat(fcA).isNotNull();
        assertThat(fcA.tenancyId).isEqualTo(TENANT_A);

        // Same filter, different tenant — should create a separate chain
        principal.setTenancyId(TENANT_B);
        FilterChain fcB = store.findOrCreateForFilter(filterId);
        assertThat(fcB).isNotNull();
        assertThat(fcB.tenancyId).isEqualTo(TENANT_B);

        // The two chains should be distinct entities
        assertThat(fcA.id).isNotEqualTo(fcB.id);
    }
}

package io.casehub.work.queues.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.path.Path;
import io.casehub.work.queues.model.QueueView;
import io.casehub.work.queues.repository.QueueViewStore;
import io.casehub.work.queues.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaQueueViewStore}.
 */
@QuarkusTest
@TestTransaction
class JpaQueueViewStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    QueueViewStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    private QueueView newQueueView(String name) {
        QueueView qv = new QueueView();
        qv.name = name;
        qv.labelPattern = "test/" + name + "/**";
        qv.scope = Path.root();
        return qv;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);
        QueueView qv = newQueueView("stamp-test");
        assertThat(qv.tenancyId).isNull();
        store.put(qv);
        assertThat(qv.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void put_preservesTenancyId_whenAlreadySet() {
        principal.setTenancyId(TENANT_B);
        QueueView qv = newQueueView("preserve-test");
        qv.tenancyId = TENANT_A;
        store.put(qv);
        assertThat(qv.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void get_returnsEmpty_forAnotherTenantItem() {
        principal.setTenancyId(TENANT_A);
        QueueView qv = newQueueView("get-isolation");
        store.put(qv);
        UUID id = qv.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    @Test
    void scanAll_returnsOnlyCurrentTenantItems() {
        principal.setTenancyId(TENANT_A);
        QueueView qvA = newQueueView("scanall-a");
        store.put(qvA);

        principal.setTenancyId(TENANT_B);
        QueueView qvB = newQueueView("scanall-b");
        store.put(qvB);

        principal.setTenancyId(TENANT_A);
        assertThat(store.scanAll()).extracting(q -> q.id).contains(qvA.id).doesNotContain(qvB.id);

        principal.setTenancyId(TENANT_B);
        assertThat(store.scanAll()).extracting(q -> q.id).contains(qvB.id).doesNotContain(qvA.id);
    }

    @Test
    void delete_doesNotDeleteAnotherTenantItem() {
        principal.setTenancyId(TENANT_A);
        QueueView qv = newQueueView("delete-isolation");
        store.put(qv);
        UUID id = qv.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.delete(id)).isFalse();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
        assertThat(store.delete(id)).isTrue();
    }
}

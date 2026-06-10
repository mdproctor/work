package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;

class TenantAwareStoreTest {

    private EntityManager em;
    private CurrentPrincipal principal;
    private Query nativeQuery;
    private TestStore store;

    static class TestStore extends TenantAwareStore {
        <T> T tenantQuery(Supplier<T> work) {
            return withTenantQuery(work);
        }

        <T> T crossTenantQuery(Supplier<T> work) {
            return withCrossTenantQuery(work);
        }
    }

    @BeforeEach
    void setUp() {
        em = mock(EntityManager.class);
        principal = mock(CurrentPrincipal.class);
        nativeQuery = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.executeUpdate()).thenReturn(0);
        when(em.isJoinedToTransaction()).thenReturn(true);
        when(principal.tenancyId()).thenReturn(TenancyConstants.DEFAULT_TENANT_ID);

        store = new TestStore();
        store.em = em;
        store.currentPrincipal = principal;
    }

    @Test
    void withTenantQuery_rlsEnabled_executesSetLocal() {
        store.rlsEnabled = true;

        String result = store.tenantQuery(() -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(em).createNativeQuery(
                "SET LOCAL \"casehub.tenancy_id\" = '" + TenancyConstants.DEFAULT_TENANT_ID + "'");
        verify(nativeQuery).executeUpdate();
    }

    @Test
    void withTenantQuery_rlsDisabled_skipsSetLocal() {
        store.rlsEnabled = false;

        String result = store.tenantQuery(() -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(em, never()).createNativeQuery(anyString());
    }

    @Test
    void withTenantQuery_nullTenancyId_throws() {
        store.rlsEnabled = true;
        when(principal.tenancyId()).thenReturn(null);

        assertThatThrownBy(() -> store.tenantQuery(() -> "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid tenancyId");
    }

    @Test
    void withTenantQuery_singleQuoteInTenancyId_throws() {
        store.rlsEnabled = true;
        when(principal.tenancyId()).thenReturn("tenant'; DROP TABLE work_item; --");

        assertThatThrownBy(() -> store.tenantQuery(() -> "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid tenancyId");
    }

    @Test
    void withTenantQuery_backslashInTenancyId_throws() {
        store.rlsEnabled = true;
        when(principal.tenancyId()).thenReturn("tenant\\escape");

        assertThatThrownBy(() -> store.tenantQuery(() -> "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid tenancyId");
    }

    @Test
    void withTenantQuery_noTransaction_throws() {
        store.rlsEnabled = true;
        when(em.isJoinedToTransaction()).thenReturn(false);

        assertThatThrownBy(() -> store.tenantQuery(() -> "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction");
    }

    @Test
    void withCrossTenantQuery_rlsEnabled_executesSetLocalRole() {
        store.rlsEnabled = true;

        String result = store.crossTenantQuery(() -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(em).createNativeQuery("SET LOCAL ROLE casehub_crosstenancy");
        verify(nativeQuery).executeUpdate();
    }

    @Test
    void withCrossTenantQuery_rlsDisabled_skipsSetLocalRole() {
        store.rlsEnabled = false;

        String result = store.crossTenantQuery(() -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(em, never()).createNativeQuery(anyString());
    }

    @Test
    void withCrossTenantQuery_noTransaction_throws() {
        store.rlsEnabled = true;
        when(em.isJoinedToTransaction()).thenReturn(false);

        assertThatThrownBy(() -> store.crossTenantQuery(() -> "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction");
    }
}

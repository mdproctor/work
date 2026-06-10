package io.casehub.work.runtime.rls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifies PostgreSQL Row-Level Security enforcement using a real PostgreSQL instance.
 *
 * <p>Not a {@code @QuarkusTest} — uses raw JDBC to test RLS at the database level.
 * This avoids Quarkus augmentation overhead and tests the actual PostgreSQL behaviour
 * that the applicator configures.
 *
 * <p>Two connections:
 * <ul>
 *   <li>{@code adminConn} — superuser, bypasses RLS. Used for data setup.</li>
 *   <li>{@code appConn} — restricted {@code app_user}, RLS enforced.</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RlsEnforcementTest {

    static PostgreSQLContainer<?> postgres;
    static Connection adminConn;
    static Connection appConn;

    static final String TENANT_A = "tenant-a-" + UUID.randomUUID();
    static final String TENANT_B = "tenant-b-" + UUID.randomUUID();

    @BeforeAll
    static void setUp() throws Exception {
        postgres = new PostgreSQLContainer<>("postgres:16")
                .withInitScript("rls-init.sql");
        postgres.start();

        adminConn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        adminConn.setAutoCommit(false);

        try (Statement stmt = adminConn.createStatement()) {
            stmt.execute("CREATE TABLE work_item ("
                    + "id UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                    + "title VARCHAR(255), "
                    + "tenancy_id VARCHAR(255) NOT NULL)");

            stmt.execute("ALTER TABLE work_item ENABLE ROW LEVEL SECURITY");
            stmt.execute("ALTER TABLE work_item FORCE ROW LEVEL SECURITY");
            stmt.execute("CREATE POLICY tenant_isolation ON work_item "
                    + "USING (tenancy_id = current_setting('casehub.tenancy_id', true))");

            stmt.execute("CREATE ROLE casehub_crosstenancy BYPASSRLS");
            stmt.execute("GRANT casehub_crosstenancy TO current_user");
            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON work_item TO casehub_crosstenancy");
            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON work_item TO app_user");
        }
        adminConn.commit();

        appConn = DriverManager.getConnection(
                postgres.getJdbcUrl(), "app_user", "app_password");
        appConn.setAutoCommit(false);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (appConn != null) appConn.close();
        if (adminConn != null) adminConn.close();
        if (postgres != null) postgres.stop();
    }

    @Test
    @Order(1)
    void tenantIsolation_tenantACannotSeeTenantBData() throws Exception {
        try (Statement stmt = adminConn.createStatement()) {
            stmt.execute("SET LOCAL ROLE casehub_crosstenancy");
            stmt.execute("INSERT INTO work_item (title, tenancy_id) VALUES ('A-item', '" + TENANT_A + "')");
            stmt.execute("INSERT INTO work_item (title, tenancy_id) VALUES ('B-item', '" + TENANT_B + "')");
        }
        adminConn.commit();

        try (Statement stmt = appConn.createStatement()) {
            stmt.execute("SET LOCAL \"casehub.tenancy_id\" = '" + TENANT_A + "'");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM work_item")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT title FROM work_item")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("A-item");
            }
        }
        appConn.commit();
    }

    @Test
    @Order(2)
    void bypassRlsRole_seesAllTenants() throws Exception {
        try (Statement stmt = appConn.createStatement()) {
            stmt.execute("SET LOCAL ROLE casehub_crosstenancy");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM work_item")) {
                rs.next();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(2);
            }
        }
        appConn.commit();
    }

    @Test
    @Order(3)
    void resetRole_returnToTenantScoped() throws Exception {
        try (Statement stmt = appConn.createStatement()) {
            stmt.execute("SET LOCAL ROLE casehub_crosstenancy");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM work_item")) {
                rs.next();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(2);
            }
        }
        appConn.commit();

        try (Statement stmt = appConn.createStatement()) {
            stmt.execute("SET LOCAL \"casehub.tenancy_id\" = '" + TENANT_B + "'");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM work_item")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT title FROM work_item")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("B-item");
            }
        }
        appConn.commit();
    }

    @Test
    @Order(4)
    void failClosed_noSessionVariable_zeroRows() throws Exception {
        try (Statement stmt = appConn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM work_item")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
        }
        appConn.commit();
    }

    @Test
    @Order(5)
    void transactionIsolation_setLocalRevertsAfterCommit() throws Exception {
        try (Statement stmt = appConn.createStatement()) {
            stmt.execute("SET LOCAL \"casehub.tenancy_id\" = '" + TENANT_A + "'");
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM work_item")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
        appConn.commit();

        try (Statement stmt = appConn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM work_item")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
        }
        appConn.commit();
    }

    @Test
    @Order(6)
    void writeRejection_insertWithWrongTenant() throws Exception {
        try (Statement stmt = appConn.createStatement()) {
            stmt.execute("SET LOCAL \"casehub.tenancy_id\" = '" + TENANT_A + "'");
            assertThatThrownBy(() -> stmt.execute(
                    "INSERT INTO work_item (title, tenancy_id) VALUES ('wrong', '" + TENANT_B + "')"))
                    .isInstanceOf(SQLException.class);
        }
        appConn.rollback();
    }
}

package io.casehub.work.runtime.rls;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;

/**
 * Applies PostgreSQL Row-Level Security policies to casehub-work runtime tables at startup.
 *
 * <p>Gated by {@code casehub.work.rls.enabled} (default {@code false}). When enabled:
 * <ol>
 *   <li>Creates the {@code casehub_crosstenancy} role with {@code BYPASSRLS} if not exists</li>
 *   <li>Grants DML on runtime tables to the cross-tenant role</li>
 *   <li>Enables RLS + FORCE RLS + creates tenant_isolation policy on each table</li>
 * </ol>
 *
 * <p>Runs at {@code @Priority(100)} — after Hibernate schema creation.
 * Module-specific applicators run at {@code @Priority(101)}.
 */
@ApplicationScoped
public class WorkRlsPolicyApplicator {

    private static final Logger LOG = Logger.getLogger(WorkRlsPolicyApplicator.class);

    static final List<String> TABLES = List.of(
            "work_item", "work_item_template", "audit_entry", "work_item_note",
            "work_item_link", "work_item_spawn_group", "work_item_schedule",
            "work_item_relation", "routing_cursor", "label_definition",
            "label_vocabulary", "filter_rule");

    @Inject
    AgroalDataSource dataSource;

    @ConfigProperty(name = "casehub.work.rls.enabled", defaultValue = "false")
    boolean rlsEnabled;

    void onStart(@Observes @Priority(100) StartupEvent ev) {
        if (!rlsEnabled) {
            LOG.debug("RLS disabled (casehub.work.rls.enabled=false) — skipping policy application");
            return;
        }
        LOG.info("Applying PostgreSQL Row-Level Security policies to casehub-work runtime tables");
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            createBypassRole(stmt);
            for (String table : TABLES) {
                applyRls(stmt, table);
            }
            LOG.infof("RLS applied to %d runtime tables", TABLES.size());
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to apply RLS policies", e);
        }
    }

    private void createBypassRole(Statement stmt) throws SQLException {
        stmt.execute(
                "DO $$ BEGIN "
                        + "  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'casehub_crosstenancy') THEN"
                        + "    EXECUTE 'CREATE ROLE casehub_crosstenancy BYPASSRLS'; "
                        + "  END IF; "
                        + "END $$");
        stmt.execute("GRANT casehub_crosstenancy TO current_user");
        for (String table : TABLES) {
            stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + table + " TO casehub_crosstenancy");
        }
        stmt.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO casehub_crosstenancy");
    }

    private void applyRls(Statement stmt, String table) throws SQLException {
        stmt.execute("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
        stmt.execute("ALTER TABLE " + table + " FORCE ROW LEVEL SECURITY");
        stmt.execute(
                "DO $$ BEGIN "
                        + "  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename = '"
                        + table + "' AND policyname = 'tenant_isolation') THEN "
                        + "    EXECUTE 'CREATE POLICY tenant_isolation ON "
                        + table + " USING (tenancy_id = current_setting(''casehub.tenancy_id'', true))'; "
                        + "  END IF; "
                        + "END $$");
    }
}

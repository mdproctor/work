package io.casehub.work.notifications.rls;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class NotificationsRlsPolicyApplicator {

    private static final Logger LOG = Logger.getLogger(NotificationsRlsPolicyApplicator.class);

    static final List<String> TABLES = List.of("work_item_notification_rule");

    @Inject
    AgroalDataSource dataSource;

    @ConfigProperty(name = "casehub.work.rls.enabled", defaultValue = "false")
    boolean rlsEnabled;

    void onStart(@Observes @Priority(101) StartupEvent ev) {
        if (!rlsEnabled) return;
        LOG.infof("Applying RLS policies to %s tables", "notifications");
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            for (String table : TABLES) {
                stmt.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + table + " TO casehub_crosstenancy");
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
            LOG.infof("RLS applied to %d %s tables", TABLES.size(), "notifications");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to apply RLS policies to notifications tables", e);
        }
    }
}

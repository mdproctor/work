package io.casehub.work.runtime.rls;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import io.agroal.api.AgroalDataSource;

class WorkRlsPolicyApplicatorTest {

    @Test
    void onStart_whenDisabled_doesNothing() throws SQLException {
        AgroalDataSource ds = mock(AgroalDataSource.class);
        WorkRlsPolicyApplicator applicator = new WorkRlsPolicyApplicator();
        applicator.dataSource = ds;
        applicator.rlsEnabled = false;

        applicator.onStart(null);

        verify(ds, never()).getConnection();
    }

    @Test
    void onStart_whenEnabled_appliesRlsToAllTables() throws SQLException {
        AgroalDataSource ds = mock(AgroalDataSource.class);
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);

        WorkRlsPolicyApplicator applicator = new WorkRlsPolicyApplicator();
        applicator.dataSource = ds;
        applicator.rlsEnabled = true;

        applicator.onStart(null);

        verify(ds).getConnection();
        // Role creation (2 SQL) + sequence grant (1) + DML grants (12) + 3 DDL per table × 12 = 51 minimum
        verify(stmt, atLeast(40)).execute(anyString());
    }
}

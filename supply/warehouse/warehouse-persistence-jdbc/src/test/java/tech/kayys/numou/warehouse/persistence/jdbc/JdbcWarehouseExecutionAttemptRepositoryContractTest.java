package tech.kayys.numou.warehouse.persistence.jdbc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcWarehouseExecutionAttemptRepositoryContractTest {
    @Test
    void requiresDataSource() {
        assertThrows(NullPointerException.class, () -> new JdbcWarehouseExecutionAttemptRepository(null));
    }

    @Test
    void rejectsInvalidRecoveryLimitBeforeDatabaseAccess() {
        JdbcWarehouseExecutionAttemptRepository repository =
                new JdbcWarehouseExecutionAttemptRepository(new NoConnectionDataSource());
        assertThrows(IllegalArgumentException.class, () -> repository.findRecoverable(0));
        assertThrows(IllegalArgumentException.class, () -> repository.findRecoverable(1001));
    }

    private static final class NoConnectionDataSource implements javax.sql.DataSource {
        @Override public java.sql.Connection getConnection() throws java.sql.SQLException { throw new java.sql.SQLException("not expected"); }
        @Override public java.sql.Connection getConnection(String u, String p) throws java.sql.SQLException { throw new java.sql.SQLException("not expected"); }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> iface) throws java.sql.SQLException { throw new java.sql.SQLException("not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}

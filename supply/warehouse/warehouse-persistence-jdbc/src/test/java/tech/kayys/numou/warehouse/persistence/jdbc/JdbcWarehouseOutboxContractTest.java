package tech.kayys.numou.warehouse.persistence.jdbc;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.time.Duration;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class JdbcWarehouseOutboxContractTest {
    private final JdbcWarehouseOutbox outbox = new JdbcWarehouseOutbox(new NoConnectionDataSource());

    @Test void rejectsInvalidBatchBeforeOpeningConnection() {
        assertThrows(IllegalArgumentException.class, () -> outbox.claim("worker", 0, Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> outbox.claim("worker", 501, Duration.ofSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> outbox.claim(" ", 1, Duration.ofSeconds(10)));
    }

    @Test void rejectsInvalidLeaseBeforeOpeningConnection() {
        assertThrows(IllegalArgumentException.class, () -> outbox.claim("worker", 1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> outbox.claim("worker", 1, Duration.ofHours(2)));
    }

    private static final class NoConnectionDataSource implements DataSource {
        @Override public Connection getConnection() throws SQLException { throw new SQLException("Unexpected database access"); }
        @Override public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public Logger getParentLogger() { return Logger.getGlobal(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not a wrapper"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}

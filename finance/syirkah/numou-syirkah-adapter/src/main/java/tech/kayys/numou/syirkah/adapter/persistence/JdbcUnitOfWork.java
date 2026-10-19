package tech.kayys.numou.syirkah.adapter.persistence;

import tech.kayys.numou.syirkah.application.port.TransactionPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * JDBC {@link TransactionPort}: binds one connection (autocommit off) to the current thread so the repository,
 * approvals store and outbox writer of a unit of work commit or roll back together. Repositories called outside
 * a unit of work open their own short transaction. Nested calls join the outer transaction.
 */
public class JdbcUnitOfWork implements TransactionPort {

    private final DataSource dataSource;
    private final ThreadLocal<Connection> current = new ThreadLocal<>();

    public JdbcUnitOfWork(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    DataSource dataSource() {
        return dataSource;
    }

    /** The connection of the running unit of work, or {@code null} when none is active on this thread. */
    Connection current() {
        return current.get();
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        if (current.get() != null) {
            return work.get();
        }
        try (Connection c = dataSource.getConnection()) {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            current.set(c);
            try {
                T result = work.get();
                c.commit();
                return result;
            } catch (RuntimeException | Error e) {
                rollbackQuietly(c, e);
                throw e;
            } finally {
                current.remove();
                try {
                    c.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                    // connection is closed right after; pool resets state on return
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("transaction failed: " + e.getMessage(), e);
        }
    }

    private static void rollbackQuietly(Connection c, Throwable cause) {
        try {
            c.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }
}

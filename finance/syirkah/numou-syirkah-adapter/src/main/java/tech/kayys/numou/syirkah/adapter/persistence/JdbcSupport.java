package tech.kayys.numou.numouadapter.persistence;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Shared plumbing: run work on the unit-of-work connection (or a short own
 * transaction) under a tenant.
 */
final class JdbcSupport {

    private JdbcSupport() {
    }

    @FunctionalInterface
    interface SqlWork<T> {
        T apply(Connection c) throws SQLException;
    }

    static <T> T inConnection(JdbcUnitOfWork uow, TenantId tenant, SqlWork<T> work) {
        java.util.function.Function<Connection, T> run = c -> {
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                    ps.setString(1, tenant.value().toString());
                    ps.executeQuery().close();
                }
                return work.apply(c);
            } catch (SQLException e) {
                throw new IllegalStateException("syirkah persistence failure: " + e.getMessage(), e);
            }
        };
        Connection bound = uow.current();
        if (bound != null) {
            return run.apply(bound);
        }
        return uow.inTransaction(() -> run.apply(uow.current()));
    }

    static OffsetDateTime ts(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    static Instant instant(java.sql.ResultSet rs, int col) throws SQLException {
        OffsetDateTime t = rs.getObject(col, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }
}

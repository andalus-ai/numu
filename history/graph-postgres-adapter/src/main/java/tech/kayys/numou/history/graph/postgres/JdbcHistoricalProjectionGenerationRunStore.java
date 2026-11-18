package tech.kayys.numou.history.graph.postgres;

import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore.Run;
import tech.kayys.numou.history.graph.spi.HistoricalProjectionGenerationRunStore.Status;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL saga journal for graph-generation rebuild and activation operations. */
public final class JdbcHistoricalProjectionGenerationRunStore implements HistoricalProjectionGenerationRunStore {
    private final DataSource source;

    public JdbcHistoricalProjectionGenerationRunStore(DataSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Run create(UUID tenantId, UUID operationId, UUID generationId, UUID rebuildRunId,
                      UUID expectedActiveGenerationId, Instant now) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(rebuildRunId, "rebuildRunId");
        Objects.requireNonNull(now, "now");
        return inTransaction(tenantId, c -> {
            String insert = """
                    INSERT INTO historical_projection_generation_run
                        (scope_key, operation_id, tenant_id, generation_id, rebuild_run_id,
                         expected_active_generation_id, status, failure_count, last_error, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'PREPARING', 0, '', ?, ?)
                    ON CONFLICT (scope_key, operation_id) DO NOTHING
                    """;
            try (PreparedStatement s = c.prepareStatement(insert)) {
                s.setString(1, scopeKey(tenantId));
                s.setObject(2, operationId);
                s.setObject(3, tenantId);
                s.setObject(4, generationId);
                s.setObject(5, rebuildRunId);
                s.setObject(6, expectedActiveGenerationId);
                s.setTimestamp(7, Timestamp.from(now));
                s.setTimestamp(8, Timestamp.from(now));
                s.executeUpdate();
            }
            // The operation ID is the idempotency key. Its first persisted binding wins.
            return select(c, tenantId, operationId)
                    .orElseThrow(() -> new SQLException("generation operation insert was not visible"));
        });
    }

    @Override
    public Optional<Run> find(UUID tenantId, UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        return inTransaction(tenantId, c -> select(c, tenantId, operationId));
    }

    @Override
    public boolean transition(UUID tenantId, UUID operationId, Status expected, Status next, Instant now) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(now, "now");
        if (!allowed(expected, next)) throw new IllegalArgumentException("invalid generation operation transition");
        String sql = """
                UPDATE historical_projection_generation_run
                   SET status=?, updated_at=?, activated_at=CASE WHEN ?='ACTIVE' THEN ? ELSE activated_at END
                 WHERE scope_key=? AND operation_id=? AND tenant_id IS NOT DISTINCT FROM ? AND status=?
                """;
        return inTransaction(tenantId, c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setString(1, next.name());
                s.setTimestamp(2, Timestamp.from(now));
                s.setString(3, next.name());
                s.setTimestamp(4, Timestamp.from(now));
                s.setString(5, scopeKey(tenantId));
                s.setObject(6, operationId);
                s.setObject(7, tenantId);
                s.setString(8, expected.name());
                return s.executeUpdate() == 1;
            }
        });
    }

    @Override
    public void recordFailure(UUID tenantId, UUID operationId, String detail, Instant now) {
        String message = detail == null ? "" : detail.substring(0, Math.min(1000, detail.length()));
        String sql = """
                UPDATE historical_projection_generation_run
                   SET failure_count=failure_count+1, last_error=?, updated_at=?
                 WHERE scope_key=? AND operation_id=? AND tenant_id IS NOT DISTINCT FROM ?
                """;
        inTransaction(tenantId, c -> {
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setString(1, message);
                s.setTimestamp(2, Timestamp.from(now));
                s.setString(3, scopeKey(tenantId));
                s.setObject(4, operationId);
                s.setObject(5, tenantId);
                if (s.executeUpdate() != 1) throw new SQLException("generation operation does not exist");
            }
            return null;
        });
    }

    private Optional<Run> select(Connection c, UUID tenantId, UUID operationId) throws SQLException {
        String sql = """
                SELECT operation_id, tenant_id, generation_id, rebuild_run_id, expected_active_generation_id,
                       status, failure_count, last_error, created_at, updated_at, activated_at
                  FROM historical_projection_generation_run
                 WHERE scope_key=? AND operation_id=? AND tenant_id IS NOT DISTINCT FROM ?
                """;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, scopeKey(tenantId));
            s.setObject(2, operationId);
            s.setObject(3, tenantId);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return Optional.empty();
                return Optional.of(new Run(r.getObject("operation_id", UUID.class),
                        r.getObject("tenant_id", UUID.class), r.getObject("generation_id", UUID.class),
                        r.getObject("rebuild_run_id", UUID.class),
                        r.getObject("expected_active_generation_id", UUID.class),
                        Status.valueOf(r.getString("status")), r.getInt("failure_count"),
                        r.getString("last_error"), instant(r, "created_at"), instant(r, "updated_at"),
                        nullableInstant(r, "activated_at")));
            }
        }
    }

    private <T> T inTransaction(UUID tenantId, SqlWork<T> work) {
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (tenantId != null) {
                    try (PreparedStatement s = c.prepareStatement("SELECT set_config('numou.tenant_id', ?, true)")) {
                        s.setString(1, tenantId.toString());
                        s.execute();
                    }
                }
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (Exception failure) {
                try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Unable to persist historical projection generation operation", failure);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to access historical projection generation operation store", failure);
        }
    }

    private static boolean allowed(Status from, Status to) {
        return switch (from) {
            case PREPARING -> to == Status.REPLAYING;
            case REPLAYING -> to == Status.VALIDATING;
            case VALIDATING -> to == Status.READY;
            case READY -> to == Status.ACTIVATING;
            case ACTIVATING -> to == Status.ACTIVE;
            case ACTIVE -> false;
        };
    }

    private static String scopeKey(UUID tenantId) {
        return tenantId == null ? "public" : tenantId.toString();
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        return r.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet r, String column) throws SQLException {
        OffsetDateTime value = r.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    @FunctionalInterface private interface SqlWork<T> { T apply(Connection connection) throws Exception; }
}

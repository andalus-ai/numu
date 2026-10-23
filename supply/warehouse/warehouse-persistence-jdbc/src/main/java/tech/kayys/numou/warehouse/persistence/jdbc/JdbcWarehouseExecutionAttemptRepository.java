package tech.kayys.numou.warehouse.persistence.jdbc;

import tech.kayys.numou.warehouse.application.WarehouseExecutionAttempt;
import tech.kayys.numou.warehouse.application.WarehouseExecutionAttemptRepository;
import tech.kayys.numou.warehouse.application.WarehouseExecutionAttemptStatus;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL-compatible JDBC adapter for the warehouse recovery journal.
 * The caller owns DataSource lifecycle and schema migration execution.
 */
public final class JdbcWarehouseExecutionAttemptRepository implements WarehouseExecutionAttemptRepository {
    private static final String COLUMNS = "tenant_id, command_key, fingerprint, work_order_id, line_id, operation, status, attempt_count, last_error, created_at, updated_at, version";
    private final DataSource dataSource;

    public JdbcWarehouseExecutionAttemptRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public WarehouseExecutionAttempt createIfAbsent(WarehouseExecutionAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        String sql = "INSERT INTO warehouse_execution_attempt (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (tenant_id, command_key) DO NOTHING";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, attempt);
            statement.executeUpdate();
            return find(connection, attempt.tenantId(), attempt.commandKey())
                    .orElseThrow(() -> new SQLException("Journal row missing after insert"));
        } catch (SQLException e) {
            throw persistence("Could not create warehouse execution attempt", e);
        }
    }

    @Override
    public Optional<WarehouseExecutionAttempt> find(String tenantId, String commandKey) {
        requireKey(tenantId, commandKey);
        try (Connection connection = dataSource.getConnection()) {
            return find(connection, tenantId.trim(), commandKey.trim());
        } catch (SQLException e) {
            throw persistence("Could not read warehouse execution attempt", e);
        }
    }

    @Override
    public WarehouseExecutionAttempt transition(String tenantId, String commandKey,
                                                long expectedVersion, WarehouseExecutionAttempt candidate) {
        requireKey(tenantId, commandKey);
        Objects.requireNonNull(candidate, "candidate");
        if (!tenantId.trim().equals(candidate.tenantId()) || !commandKey.trim().equals(candidate.commandKey())
                || candidate.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("Candidate identity or version does not match transition");
        }
        String sql = "UPDATE warehouse_execution_attempt SET fingerprint=?, work_order_id=?, line_id=?, operation=?, status=?, attempt_count=?, last_error=?, created_at=?, updated_at=?, version=? WHERE tenant_id=? AND command_key=? AND version=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, candidate.fingerprint());
            statement.setString(2, candidate.workOrderId());
            statement.setString(3, candidate.lineId());
            statement.setString(4, candidate.operation());
            statement.setString(5, candidate.status().name());
            statement.setInt(6, candidate.attemptCount());
            statement.setString(7, candidate.lastError());
            statement.setTimestamp(8, Timestamp.from(candidate.createdAt()));
            statement.setTimestamp(9, Timestamp.from(candidate.updatedAt()));
            statement.setLong(10, candidate.version());
            statement.setString(11, tenantId.trim());
            statement.setString(12, commandKey.trim());
            statement.setLong(13, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new OptimisticLockException("Warehouse execution attempt missing or concurrently changed");
            }
            return candidate;
        } catch (SQLException e) {
            throw persistence("Could not transition warehouse execution attempt", e);
        }
    }

    @Override
    public List<WarehouseExecutionAttempt> findRecoverable(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        String sql = "SELECT " + COLUMNS + " FROM warehouse_execution_attempt WHERE status IN ('PREPARED','INVENTORY_POSTED','NEEDS_RECONCILIATION') ORDER BY updated_at, created_at LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<WarehouseExecutionAttempt> result = new ArrayList<>();
                while (rows.next()) result.add(read(rows));
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw persistence("Could not list recoverable warehouse executions", e);
        }
    }

    private Optional<WarehouseExecutionAttempt> find(Connection connection, String tenantId, String commandKey) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM warehouse_execution_attempt WHERE tenant_id=? AND command_key=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantId);
            statement.setString(2, commandKey);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        }
    }

    private static void bind(PreparedStatement s, WarehouseExecutionAttempt a) throws SQLException {
        s.setString(1, a.tenantId()); s.setString(2, a.commandKey()); s.setString(3, a.fingerprint());
        s.setString(4, a.workOrderId()); s.setString(5, a.lineId()); s.setString(6, a.operation());
        s.setString(7, a.status().name()); s.setInt(8, a.attemptCount()); s.setString(9, a.lastError());
        s.setTimestamp(10, Timestamp.from(a.createdAt())); s.setTimestamp(11, Timestamp.from(a.updatedAt()));
        s.setLong(12, a.version());
    }

    private static WarehouseExecutionAttempt read(ResultSet r) throws SQLException {
        return new WarehouseExecutionAttempt(r.getString("tenant_id"), r.getString("command_key"),
                r.getString("fingerprint"), r.getString("work_order_id"), r.getString("line_id"),
                r.getString("operation"), WarehouseExecutionAttemptStatus.valueOf(r.getString("status")),
                r.getInt("attempt_count"), r.getString("last_error"),
                r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant(),
                r.getLong("version"));
    }

    private static void requireKey(String tenantId, String commandKey) {
        if (tenantId == null || tenantId.isBlank() || commandKey == null || commandKey.isBlank())
            throw new IllegalArgumentException("tenantId and commandKey are required");
    }

    private static IllegalStateException persistence(String message, SQLException cause) {
        return new IllegalStateException(message, cause);
    }

    public static final class OptimisticLockException extends IllegalStateException {
        public OptimisticLockException(String message) { super(message); }
    }
}

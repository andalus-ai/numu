package tech.kayys.numou.warehouse.persistence.jdbc;

import tech.kayys.numou.warehouse.application.WarehouseExecutionAttempt;
import tech.kayys.numou.warehouse.application.WarehouseExecutionAttemptRepository;
import tech.kayys.numou.warehouse.application.WarehouseExecutionAttemptStatus;
import tech.kayys.numou.warehouse.application.WarehouseExecutionClaim;
import tech.kayys.numou.warehouse.application.WarehouseInventoryRecoveryPayload;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL-compatible JDBC adapter for the warehouse recovery journal.
 * The caller owns DataSource lifecycle and schema migration execution.
 */
public final class JdbcWarehouseExecutionAttemptRepository implements WarehouseExecutionAttemptRepository {
    private static final String COLUMNS = "tenant_id, command_key, fingerprint, work_order_id, line_id, operation, status, attempt_count, last_error, created_at, updated_at, version, payload_item_id, payload_warehouse_id, payload_reservation_id, payload_quantity, payload_unit_cost, payload_currency, payload_reference";
    private final DataSource dataSource;

    public JdbcWarehouseExecutionAttemptRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public WarehouseExecutionAttempt createIfAbsent(WarehouseExecutionAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        String sql = "INSERT INTO warehouse_execution_attempt (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (tenant_id, command_key) DO NOTHING";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, attempt);
            statement.executeUpdate();
            WarehouseExecutionAttempt stored = find(connection, attempt.tenantId(), attempt.commandKey())
                    .orElseThrow(() -> new SQLException("Journal row missing after insert"));
            assertCompatibleReplay(attempt, stored);
            return stored;
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
        String sql = "UPDATE warehouse_execution_attempt SET fingerprint=?, work_order_id=?, line_id=?, operation=?, status=?, attempt_count=?, last_error=?, created_at=?, updated_at=?, version=?, payload_item_id=?, payload_warehouse_id=?, payload_reservation_id=?, payload_quantity=?, payload_unit_cost=?, payload_currency=?, payload_reference=? WHERE tenant_id=? AND command_key=? AND version=?";
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
            bindPayload(statement, 11, candidate.recoveryPayload());
            statement.setString(18, tenantId.trim());
            statement.setString(19, commandKey.trim());
            statement.setLong(20, expectedVersion);
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
        String sql = "SELECT " + COLUMNS + " FROM warehouse_execution_attempt WHERE status IN ('PREPARED','INVENTORY_POSTED','NEEDS_RECONCILIATION') AND (next_eligible_at IS NULL OR next_eligible_at <= CURRENT_TIMESTAMP) ORDER BY updated_at, created_at LIMIT ?";
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

    @Override
    public WarehouseExecutionAttempt scheduleRetry(String tenantId, String commandKey,
                                                    long expectedVersion, Instant nextEligibleAt) {
        requireKey(tenantId, commandKey);
        Objects.requireNonNull(nextEligibleAt, "nextEligibleAt");
        String sql = "UPDATE warehouse_execution_attempt SET next_eligible_at=?, updated_at=CURRENT_TIMESTAMP, version=version+1 " +
                "WHERE tenant_id=? AND command_key=? AND version=? AND status='NEEDS_RECONCILIATION' " +
                "AND (claim_until IS NULL OR claim_until < CURRENT_TIMESTAMP)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(nextEligibleAt.truncatedTo(ChronoUnit.MICROS)));
            statement.setString(2, tenantId.trim());
            statement.setString(3, commandKey.trim());
            statement.setLong(4, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new OptimisticLockException("Retry schedule rejected: missing, stale, claimed, or non-reconciliation attempt");
            }
            return find(tenantId, commandKey).orElseThrow(
                    () -> new IllegalStateException("Warehouse attempt disappeared after retry scheduling"));
        } catch (SQLException e) {
            throw persistence("Could not schedule warehouse recovery retry", e);
        }
    }

    @Override
    public WarehouseExecutionAttempt scheduleRetry(WarehouseExecutionClaim claim, Instant nextEligibleAt) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(nextEligibleAt, "nextEligibleAt");
        WarehouseExecutionAttempt attempt = claim.attempt();
        String sql = "UPDATE warehouse_execution_attempt SET next_eligible_at=?, updated_at=CURRENT_TIMESTAMP, " +
                "version=version+1, claim_owner=NULL, claim_until=NULL " +
                "WHERE tenant_id=? AND command_key=? AND version=? AND status='NEEDS_RECONCILIATION' " +
                "AND claim_owner=? AND claim_until=? AND claim_until > CURRENT_TIMESTAMP";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(nextEligibleAt.truncatedTo(ChronoUnit.MICROS)));
            statement.setString(2, attempt.tenantId());
            statement.setString(3, attempt.commandKey());
            statement.setLong(4, attempt.version());
            statement.setString(5, claim.workerId());
            statement.setTimestamp(6, Timestamp.from(claim.leaseUntil()));
            if (statement.executeUpdate() != 1) {
                throw new OptimisticLockException("Retry scheduling rejected: claim stale, expired, or attempt not reconcilable");
            }
            return find(attempt.tenantId(), attempt.commandKey()).orElseThrow(
                    () -> new IllegalStateException("Warehouse attempt disappeared after retry scheduling"));
        } catch (SQLException e) {
            throw persistence("Could not atomically schedule warehouse recovery retry", e);
        }
    }

    @Override
    public WarehouseExecutionAttempt deadLetter(WarehouseExecutionClaim claim, String reason) {
        Objects.requireNonNull(claim, "claim");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        WarehouseExecutionAttempt attempt = claim.attempt();
        String sql = "UPDATE warehouse_execution_attempt SET status='DEAD_LETTER', last_error=?, " +
                "updated_at=CURRENT_TIMESTAMP, version=version+1, claim_owner=NULL, claim_until=NULL " +
                "WHERE tenant_id=? AND command_key=? AND version=? AND status='NEEDS_RECONCILIATION' " +
                "AND claim_owner=? AND claim_until=? AND claim_until > CURRENT_TIMESTAMP";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reason.trim());
            statement.setString(2, attempt.tenantId());
            statement.setString(3, attempt.commandKey());
            statement.setLong(4, attempt.version());
            statement.setString(5, claim.workerId());
            statement.setTimestamp(6, Timestamp.from(claim.leaseUntil()));
            if (statement.executeUpdate() != 1) {
                throw new OptimisticLockException("Dead-letter transition rejected: claim stale, expired, or attempt not reconcilable");
            }
            return find(attempt.tenantId(), attempt.commandKey()).orElseThrow(
                    () -> new IllegalStateException("Warehouse attempt disappeared after dead-letter transition"));
        } catch (SQLException e) {
            throw persistence("Could not atomically dead-letter warehouse recovery attempt", e);
        }
    }

    @Override
    public List<WarehouseExecutionAttempt> findDeadLetters(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
        String sql = "SELECT " + COLUMNS + " FROM warehouse_execution_attempt WHERE status='DEAD_LETTER' " +
                "ORDER BY updated_at, created_at LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<WarehouseExecutionAttempt> result = new ArrayList<>();
                while (rows.next()) result.add(read(rows));
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw persistence("Could not list dead-letter warehouse executions", e);
        }
    }

    @Override
    public List<WarehouseExecutionClaim> claimRecoverable(String workerId, int limit, Duration lease) {
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId required");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        validateLease(lease);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant leaseUntil = now.plus(lease).truncatedTo(ChronoUnit.MICROS);
        String sql = "WITH candidates AS (SELECT tenant_id, command_key FROM warehouse_execution_attempt " +
                "WHERE status IN ('PREPARED','INVENTORY_POSTED','NEEDS_RECONCILIATION') " +
                "AND (next_eligible_at IS NULL OR next_eligible_at <= CURRENT_TIMESTAMP) " +
                "AND (claim_until IS NULL OR claim_until < CURRENT_TIMESTAMP) ORDER BY updated_at, created_at " +
                "LIMIT ? FOR UPDATE SKIP LOCKED) " +
                "UPDATE warehouse_execution_attempt e SET claim_owner=?, claim_until=? " +
                "FROM candidates c WHERE e.tenant_id=c.tenant_id AND e.command_key=c.command_key " +
                "RETURNING " + COLUMNS.replace("tenant_id", "e.tenant_id").replace("command_key", "e.command_key")
                        .replace("fingerprint", "e.fingerprint").replace("work_order_id", "e.work_order_id")
                        .replace("line_id", "e.line_id").replace("operation", "e.operation")
                        .replace("status", "e.status").replace("attempt_count", "e.attempt_count")
                        .replace("last_error", "e.last_error").replace("created_at", "e.created_at")
                        .replace("updated_at", "e.updated_at").replace("version", "e.version");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, limit);
                statement.setString(2, workerId.trim());
                statement.setTimestamp(3, Timestamp.from(leaseUntil));
                List<WarehouseExecutionClaim> claims = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) claims.add(new WarehouseExecutionClaim(read(rows), workerId.trim(), leaseUntil));
                }
                connection.commit();
                return List.copyOf(claims);
            } catch (SQLException | RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) {
            throw persistence("Could not claim recoverable warehouse executions", e);
        }
    }

    @Override
    public WarehouseExecutionClaim renewClaim(WarehouseExecutionClaim claim, Duration lease) {
        Objects.requireNonNull(claim, "claim");
        validateLease(lease);
        WarehouseExecutionAttempt attempt = claim.attempt();
        Instant nextLeaseUntil = Instant.now().truncatedTo(ChronoUnit.MICROS)
                .plus(lease).truncatedTo(ChronoUnit.MICROS);
        String sql = "UPDATE warehouse_execution_attempt SET claim_until=? " +
                "WHERE tenant_id=? AND command_key=? AND claim_owner=? AND claim_until=? " +
                "AND claim_until > CURRENT_TIMESTAMP AND status IN ('PREPARED','INVENTORY_POSTED','NEEDS_RECONCILIATION')";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(nextLeaseUntil));
            statement.setString(2, attempt.tenantId());
            statement.setString(3, attempt.commandKey());
            statement.setString(4, claim.workerId());
            statement.setTimestamp(5, Timestamp.from(claim.leaseUntil()));
            if (statement.executeUpdate() != 1) {
                throw new OptimisticLockException("Recovery lease is stale, expired, or no longer owned");
            }
            return new WarehouseExecutionClaim(attempt, claim.workerId(), nextLeaseUntil);
        } catch (SQLException e) {
            throw persistence("Could not renew warehouse recovery claim", e);
        }
    }

    @Override
    public void releaseClaim(WarehouseExecutionClaim claim) {
        Objects.requireNonNull(claim, "claim");
        WarehouseExecutionAttempt attempt = claim.attempt();
        String sql = "UPDATE warehouse_execution_attempt SET claim_owner=NULL, claim_until=NULL " +
                "WHERE tenant_id=? AND command_key=? AND claim_owner=? AND claim_until=? " +
                "AND claim_until > CURRENT_TIMESTAMP";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, attempt.tenantId());
            statement.setString(2, attempt.commandKey());
            statement.setString(3, claim.workerId());
            statement.setTimestamp(4, Timestamp.from(claim.leaseUntil()));
            if (statement.executeUpdate() != 1) {
                throw new OptimisticLockException("Recovery lease is stale, expired, or no longer owned");
            }
        } catch (SQLException e) {
            throw persistence("Could not release warehouse recovery claim", e);
        }
    }

    @Deprecated
    @Override
    public void releaseClaim(String tenantId, String commandKey, String workerId) {
        throw new UnsupportedOperationException(
                "Worker ID alone cannot fence a stale lease; release the exact WarehouseExecutionClaim");
    }

    private static void validateLease(Duration lease) {
        if (lease == null || lease.isZero() || lease.isNegative()
                || lease.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("lease must be positive and no longer than one hour");
        }
    }

    /**
     * An idempotency key identifies one immutable command payload. A retry may observe
     * a later status/version, but it must not silently alias a different business command.
     */
    private static void assertCompatibleReplay(WarehouseExecutionAttempt proposed,
                                               WarehouseExecutionAttempt stored) {
        if (!proposed.fingerprint().equals(stored.fingerprint())
                || !proposed.workOrderId().equals(stored.workOrderId())
                || !proposed.lineId().equals(stored.lineId())
                || !proposed.operation().equals(stored.operation())
                || !Objects.equals(proposed.recoveryPayload(), stored.recoveryPayload())) {
            throw new IdempotencyConflictException(
                    "Tenant-scoped command key is already bound to a different warehouse command payload");
        }
    }

    /** Signals unsafe reuse of a tenant-scoped idempotency key for another command. */
    public static final class IdempotencyConflictException extends IllegalStateException {
        public IdempotencyConflictException(String message) { super(message); }
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
        bindPayload(s, 13, a.recoveryPayload());
    }

    private static void bindPayload(PreparedStatement s, int start, WarehouseInventoryRecoveryPayload p) throws SQLException {
        s.setString(start, p == null ? null : p.itemId());
        s.setString(start + 1, p == null ? null : p.warehouseId());
        s.setString(start + 2, p == null ? null : p.reservationId());
        s.setString(start + 3, p == null ? null : p.quantity());
        s.setString(start + 4, p == null ? null : p.unitCost());
        s.setString(start + 5, p == null ? null : p.currency());
        s.setString(start + 6, p == null ? null : p.reference());
    }

    private static WarehouseExecutionAttempt read(ResultSet r) throws SQLException {
        return new WarehouseExecutionAttempt(r.getString("tenant_id"), r.getString("command_key"),
                r.getString("fingerprint"), r.getString("work_order_id"), r.getString("line_id"),
                r.getString("operation"), WarehouseExecutionAttemptStatus.valueOf(r.getString("status")),
                r.getInt("attempt_count"), r.getString("last_error"),
                r.getTimestamp("created_at").toInstant(), r.getTimestamp("updated_at").toInstant(),
                r.getLong("version"), readPayload(r));
    }

    private static WarehouseInventoryRecoveryPayload readPayload(ResultSet r) throws SQLException {
        String itemId = r.getString("payload_item_id");
        if (itemId == null) return null;
        return new WarehouseInventoryRecoveryPayload(r.getString("operation"), itemId,
                r.getString("payload_warehouse_id"), r.getString("payload_reservation_id"),
                r.getString("payload_quantity"), r.getString("payload_unit_cost"),
                r.getString("payload_currency"), r.getString("payload_reference"));
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

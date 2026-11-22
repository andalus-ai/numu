package tech.kayys.numou.history.source.postgres;

import tech.kayys.numou.history.source.domain.SourceIngestionLease;
import tech.kayys.numou.history.source.spi.SourceIngestionDispatchRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL work queue with SKIP LOCKED claims and worker/token lease fencing. */
public final class JdbcSourceIngestionDispatchRepository implements SourceIngestionDispatchRepository {
    private final DataSource dataSource;

    public JdbcSourceIngestionDispatchRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<SourceIngestionLease> claimNext(
            UUID tenantId, String workerRef, Instant now, Instant leaseUntil) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        String worker = required(workerRef, "workerRef", 256);
        if (!leaseUntil.isAfter(now)) throw new IllegalArgumentException("leaseUntil must be after now");
        return execute(tenantId, connection -> {
            String select = "select ingestion_id,attempt_count from historical_source_ingestion_dispatch "
                    + "where tenant_id=? and ((status='PENDING' and available_at<=?) "
                    + "or (status='LEASED' and lease_until<=?)) "
                    + "order by available_at,created_at,ingestion_id limit 1 for update skip locked";
            try (PreparedStatement statement = connection.prepareStatement(select)) {
                statement.setObject(1, tenantId);
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setTimestamp(3, Timestamp.from(now));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return Optional.empty();
                    UUID ingestionId = result.getObject(1, UUID.class);
                    int attempt = result.getInt(2) + 1;
                    UUID token = UUID.randomUUID();
                    String update = "update historical_source_ingestion_dispatch set status='LEASED',"
                            + "attempt_count=?,worker_ref=?,lease_token=?,lease_until=?,completed_at=null,"
                            + "last_error=null,updated_at=? where tenant_id=? and ingestion_id=?";
                    try (PreparedStatement updateStatement = connection.prepareStatement(update)) {
                        updateStatement.setInt(1, attempt);
                        updateStatement.setString(2, worker);
                        updateStatement.setObject(3, token);
                        updateStatement.setTimestamp(4, Timestamp.from(leaseUntil));
                        updateStatement.setTimestamp(5, Timestamp.from(now));
                        updateStatement.setObject(6, tenantId);
                        updateStatement.setObject(7, ingestionId);
                        updateStatement.executeUpdate();
                    }
                    appendEvent(connection, tenantId, ingestionId, "CLAIMED", attempt, worker, token, now, null);
                    return Optional.of(new SourceIngestionLease(ingestionId, worker, token, attempt, now, leaseUntil));
                }
            }
        });
    }

    @Override
    public boolean complete(UUID tenantId, UUID ingestionId, String workerRef, UUID leaseToken, Instant completedAt) {
        return transition(tenantId, ingestionId, workerRef, leaseToken, completedAt,
                "COMPLETED", null, completedAt, null);
    }

    @Override
    public boolean scheduleRetry(UUID tenantId, UUID ingestionId, String workerRef, UUID leaseToken,
                                 Instant availableAt, Instant failedAt, String error) {
        Objects.requireNonNull(availableAt, "availableAt");
        if (availableAt.isBefore(failedAt)) throw new IllegalArgumentException("availableAt must not precede failedAt");
        return transition(tenantId, ingestionId, workerRef, leaseToken, failedAt,
                "RETRY_SCHEDULED", availableAt, failedAt, error);
    }

    @Override
    public boolean deadLetter(UUID tenantId, UUID ingestionId, String workerRef, UUID leaseToken,
                              Instant failedAt, String error) {
        return transition(tenantId, ingestionId, workerRef, leaseToken, failedAt,
                "DEAD_LETTERED", null, failedAt, error);
    }

    private boolean transition(UUID tenantId, UUID ingestionId, String workerRef, UUID token,
                               Instant at, String eventType, Instant availableAt, Instant occurredAt,
                               String error) {
        Objects.requireNonNull(ingestionId, "ingestionId");
        Objects.requireNonNull(token, "leaseToken");
        Objects.requireNonNull(at, "at");
        String worker = required(workerRef, "workerRef", 256);
        String status = switch (eventType) {
            case "COMPLETED" -> "COMPLETED";
            case "RETRY_SCHEDULED" -> "PENDING";
            case "DEAD_LETTERED" -> "DEAD_LETTERED";
            default -> throw new IllegalArgumentException("Unsupported dispatch transition");
        };
        return execute(tenantId, connection -> {
            String lock = "select attempt_count from historical_source_ingestion_dispatch "
                    + "where tenant_id=? and ingestion_id=? and status='LEASED' and worker_ref=? "
                    + "and lease_token=? and lease_until>? for update";
            int attempt;
            try (PreparedStatement statement = connection.prepareStatement(lock)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, ingestionId);
                statement.setString(3, worker);
                statement.setObject(4, token);
                statement.setTimestamp(5, Timestamp.from(at));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return false;
                    attempt = result.getInt(1);
                }
            }
            String update = "update historical_source_ingestion_dispatch set status=?,"
                    + "available_at=coalesce(?,available_at),worker_ref=null,lease_token=null,lease_until=null,"
                    + "completed_at=?,last_error=?,updated_at=? where tenant_id=? and ingestion_id=?";
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setString(1, status);
                statement.setTimestamp(2, availableAt == null ? null : Timestamp.from(availableAt));
                statement.setTimestamp(3, "COMPLETED".equals(status) ? Timestamp.from(at) : null);
                statement.setString(4, error == null ? null : truncate(error, 2000));
                statement.setTimestamp(5, Timestamp.from(at));
                statement.setObject(6, tenantId);
                statement.setObject(7, ingestionId);
                statement.executeUpdate();
            }
            appendEvent(connection, tenantId, ingestionId, eventType, attempt, worker, token, occurredAt,
                    error == null ? null : truncate(error, 2000));
            return true;
        });
    }

    private static void appendEvent(Connection connection, UUID tenantId, UUID ingestionId,
                                    String eventType, int attempt, String workerRef, UUID token,
                                    Instant occurredAt, String error) throws SQLException {
        String sql = "insert into historical_source_ingestion_dispatch_event "
                + "(tenant_id,event_id,ingestion_id,event_type,attempt,worker_ref,lease_token,occurred_at,error) "
                + "values (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, ingestionId);
            statement.setString(4, eventType);
            statement.setInt(5, attempt);
            statement.setString(6, workerRef);
            statement.setObject(7, token);
            statement.setTimestamp(8, Timestamp.from(occurredAt));
            statement.setString(9, error);
            statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> { T run(Connection connection) throws SQLException; }

    private <T> T execute(UUID tenantId, SqlWork<T> work) {
        Objects.requireNonNull(tenantId, "tenantId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement setting = connection.prepareStatement("select set_config('numou.tenant_id', ?, true)")) {
                setting.setString(1, tenantId.toString());
                setting.execute();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Source ingestion dispatch persistence failed", failure);
        }
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return normalized;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}

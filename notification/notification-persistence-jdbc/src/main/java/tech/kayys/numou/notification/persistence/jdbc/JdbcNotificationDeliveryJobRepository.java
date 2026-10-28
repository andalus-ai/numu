package tech.kayys.numou.notification.persistence.jdbc;

import tech.kayys.numou.notification.application.port.NotificationDeliveryJobRepository;
import tech.kayys.numou.notification.domain.NotificationDeliveryJob;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL queue adapter with atomic claims and lease-token-fenced transitions. */
public final class JdbcNotificationDeliveryJobRepository implements NotificationDeliveryJobRepository {
    private static final String COLUMNS = "id, tenant_id, recipient_id, notification_id, device_id, provider, "
            + "idempotency_key, status, attempt_count, created_at, next_attempt_at, lease_token, lease_until, "
            + "delivered_at, last_error_code";
    private final DataSource dataSource;

    public JdbcNotificationDeliveryJobRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public NotificationDeliveryJob enqueue(NotificationDeliveryJob job) {
        Objects.requireNonNull(job, "job is required");
        if (job.status() != NotificationDeliveryJob.Status.PENDING || job.attemptCount() != 0)
            throw new IllegalArgumentException("only new pending jobs may be enqueued");
        String insert = "INSERT INTO notification_delivery_job (id, tenant_id, recipient_id, notification_id, "
                + "device_id, provider, idempotency_key, status, attempt_count, created_at, next_attempt_at, "
                + "lease_token, lease_until, delivered_at, last_error_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (tenant_id, idempotency_key) DO NOTHING";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                bind(statement, job);
                statement.executeUpdate();
            }
            String select = "SELECT " + COLUMNS + " FROM notification_delivery_job WHERE tenant_id = ? AND idempotency_key = ?";
            try (PreparedStatement statement = connection.prepareStatement(select)) {
                statement.setObject(1, job.tenantId());
                statement.setString(2, job.idempotencyKey());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("delivery job insert/read-back invariant failed");
                    NotificationDeliveryJob canonical = map(rows);
                    if (!sameRequest(canonical, job)) {
                        throw new IllegalStateException("delivery idempotency key was reused for a different request");
                    }
                    return canonical;
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("enqueue notification delivery", e);
        }
    }

    @Override
    public List<NotificationDeliveryJob> claimDue(
            Instant now, Instant leaseExpiry, int limit, UUID leaseToken) {
        Objects.requireNonNull(now, "now is required");
        Objects.requireNonNull(leaseExpiry, "leaseExpiry is required");
        Objects.requireNonNull(leaseToken, "leaseToken is required");
        if (!leaseExpiry.isAfter(now)) throw new IllegalArgumentException("leaseExpiry must be after now");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        String sql = "WITH candidates AS (SELECT id FROM notification_delivery_job "
                + "WHERE ((status IN ('PENDING', 'RETRY_WAIT') AND next_attempt_at <= ?) "
                + "OR (status = 'IN_FLIGHT' AND lease_until <= ?)) "
                + "ORDER BY next_attempt_at, created_at, id FOR UPDATE SKIP LOCKED LIMIT ?) "
                + "UPDATE notification_delivery_job job SET status = 'IN_FLIGHT', attempt_count = job.attempt_count + 1, "
                + "lease_token = ?, lease_until = ?, delivered_at = NULL "
                + "FROM candidates WHERE job.id = candidates.id RETURNING "
                + "job.id, job.tenant_id, job.recipient_id, job.notification_id, job.device_id, job.provider, "
                + "job.idempotency_key, job.status, job.attempt_count, job.created_at, job.next_attempt_at, "
                + "job.lease_token, job.lease_until, job.delivered_at, job.last_error_code";
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(now));
                statement.setTimestamp(2, Timestamp.from(now));
                statement.setInt(3, limit);
                statement.setObject(4, leaseToken);
                statement.setTimestamp(5, Timestamp.from(leaseExpiry));
                List<NotificationDeliveryJob> claimed = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) claimed.add(map(rows));
                }
                connection.commit();
                return List.copyOf(claimed);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw persistenceFailure("claim notification deliveries", e);
        }
    }

    @Override
    public Optional<NotificationDeliveryJob> markDelivered(
            UUID tenantId, UUID jobId, UUID leaseToken, Instant now) {
        requireScope(tenantId, jobId, leaseToken);
        Objects.requireNonNull(now, "now is required");
        String sql = "UPDATE notification_delivery_job SET status = 'DELIVERED', delivered_at = ?, "
                + "lease_token = NULL, lease_until = NULL, last_error_code = '' "
                + "WHERE tenant_id = ? AND id = ? AND status = 'IN_FLIGHT' AND lease_token = ? "
                + "RETURNING " + COLUMNS;
        return transition("mark notification delivery delivered", sql, statement -> {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setObject(2, tenantId);
            statement.setObject(3, jobId);
            statement.setObject(4, leaseToken);
        });
    }

    @Override
    public Optional<NotificationDeliveryJob> scheduleRetry(
            UUID tenantId, UUID jobId, UUID leaseToken, Instant retryAt, String safeErrorCode) {
        requireScope(tenantId, jobId, leaseToken);
        Objects.requireNonNull(retryAt, "retryAt is required");
        String error = safeErrorCode == null ? "" : safeErrorCode.trim();
        if (error.length() > NotificationDeliveryJob.MAX_ERROR_CODE_LENGTH)
            throw new IllegalArgumentException("safeErrorCode is too long");
        String sql = "UPDATE notification_delivery_job SET status = 'RETRY_WAIT', next_attempt_at = ?, "
                + "lease_token = NULL, lease_until = NULL, last_error_code = ? "
                + "WHERE tenant_id = ? AND id = ? AND status = 'IN_FLIGHT' AND lease_token = ? "
                + "RETURNING " + COLUMNS;
        return transition("schedule notification delivery retry", sql, statement -> {
            statement.setTimestamp(1, Timestamp.from(retryAt));
            statement.setString(2, error);
            statement.setObject(3, tenantId);
            statement.setObject(4, jobId);
            statement.setObject(5, leaseToken);
        });
    }

    @Override
    public Optional<NotificationDeliveryJob> markDead(
            UUID tenantId, UUID jobId, UUID leaseToken, String safeErrorCode) {
        requireScope(tenantId, jobId, leaseToken);
        String error = safeErrorCode == null ? "" : safeErrorCode.trim();
        if (error.length() > NotificationDeliveryJob.MAX_ERROR_CODE_LENGTH)
            throw new IllegalArgumentException("safeErrorCode is too long");
        String sql = "UPDATE notification_delivery_job SET status = 'DEAD', lease_token = NULL, "
                + "lease_until = NULL, last_error_code = ? "
                + "WHERE tenant_id = ? AND id = ? AND status = 'IN_FLIGHT' AND lease_token = ? "
                + "RETURNING " + COLUMNS;
        return transition("mark notification delivery dead", sql, statement -> {
            statement.setString(1, error);
            statement.setObject(2, tenantId);
            statement.setObject(3, jobId);
            statement.setObject(4, leaseToken);
        });
    }

    private Optional<NotificationDeliveryJob> transition(
            String operation, String sql, Binder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure(operation, e);
        }
    }

    private static void bind(PreparedStatement statement, NotificationDeliveryJob job) throws SQLException {
        statement.setObject(1, job.id());
        statement.setObject(2, job.tenantId());
        statement.setObject(3, job.recipientId());
        statement.setObject(4, job.notificationId());
        statement.setObject(5, job.deviceId());
        statement.setString(6, job.provider());
        statement.setString(7, job.idempotencyKey());
        statement.setString(8, job.status().name());
        statement.setInt(9, job.attemptCount());
        statement.setTimestamp(10, Timestamp.from(job.createdAt()));
        statement.setTimestamp(11, Timestamp.from(job.nextAttemptAt()));
        statement.setObject(12, job.leaseToken());
        if (job.leaseUntil() == null) statement.setNull(13, Types.TIMESTAMP_WITH_TIMEZONE);
        else statement.setTimestamp(13, Timestamp.from(job.leaseUntil()));
        if (job.deliveredAt() == null) statement.setNull(14, Types.TIMESTAMP_WITH_TIMEZONE);
        else statement.setTimestamp(14, Timestamp.from(job.deliveredAt()));
        statement.setString(15, job.lastErrorCode());
    }

    private static NotificationDeliveryJob map(ResultSet row) throws SQLException {
        Timestamp leaseUntil = row.getTimestamp("lease_until");
        Timestamp deliveredAt = row.getTimestamp("delivered_at");
        return new NotificationDeliveryJob(
                row.getObject("id", UUID.class), row.getObject("tenant_id", UUID.class),
                row.getObject("recipient_id", UUID.class), row.getObject("notification_id", UUID.class),
                row.getObject("device_id", UUID.class), row.getString("provider"),
                row.getString("idempotency_key"), NotificationDeliveryJob.Status.valueOf(row.getString("status")),
                row.getInt("attempt_count"), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("next_attempt_at").toInstant(), row.getObject("lease_token", UUID.class),
                leaseUntil == null ? null : leaseUntil.toInstant(),
                deliveredAt == null ? null : deliveredAt.toInstant(), row.getString("last_error_code"));
    }

    private static boolean sameRequest(NotificationDeliveryJob a, NotificationDeliveryJob b) {
        return a.tenantId().equals(b.tenantId()) && a.recipientId().equals(b.recipientId())
                && a.notificationId().equals(b.notificationId()) && a.deviceId().equals(b.deviceId())
                && a.provider().equals(b.provider());
    }

    private static void requireScope(UUID tenantId, UUID jobId, UUID leaseToken) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(jobId, "jobId is required");
        Objects.requireNonNull(leaseToken, "leaseToken is required");
    }

    private static IllegalStateException persistenceFailure(String operation, SQLException cause) {
        return new IllegalStateException("Failed to " + operation, cause);
    }

    @FunctionalInterface
    private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
}

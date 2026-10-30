package tech.kayys.numou.queuemanagement.postgres;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.numou.queuemanagement.spi.QueueOutboxDeliveryPort;
import tech.kayys.numou.queuemanagement.spi.QueueOutboxMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Short-transaction PostgreSQL lease operations; transport publishing stays outside DB transactions. */
@ApplicationScoped
public class QueuePostgresOutboxDeliveryAdapter implements QueueOutboxDeliveryPort {
    private static final int MAX_ERROR_LENGTH = 2000;
    private final AgroalDataSource dataSource;

    @Inject
    public QueuePostgresOutboxDeliveryAdapter(AgroalDataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    @Transactional
    public List<QueueOutboxMessage> claim(UUID tenantId, String workerId, int limit, Duration leaseDuration) {
        Objects.requireNonNull(tenantId, "tenantId");
        requireText(workerId, "workerId");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        long leaseSeconds = wholeSeconds(leaseDuration, "leaseDuration", false);
        String sql = """
                WITH candidates AS (
                    SELECT event_id FROM queue_outbox
                    WHERE tenant_id = ? AND published_at IS NULL AND dead_lettered_at IS NULL
                      AND available_at <= CURRENT_TIMESTAMP
                      AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)
                    ORDER BY available_at, created_at, event_id
                    LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE queue_outbox o
                   SET lease_owner = ?, lease_token = gen_random_uuid(),
                       lease_until = CURRENT_TIMESTAMP + make_interval(secs => ?),
                       attempt_count = o.attempt_count + 1,
                       last_attempt_at = CURRENT_TIMESTAMP
                  FROM candidates c
                 WHERE o.tenant_id = ? AND o.event_id = c.event_id
                RETURNING o.event_id, o.tenant_id, o.event_type, o.schema_version,
                          o.aggregate_type, o.aggregate_id, o.correlation_id, o.occurred_at,
                          o.payload::text AS payload, o.attempt_count, o.lease_owner,
                          o.lease_token, o.lease_until
                """;
        try (Connection connection = dataSource.getConnection()) {
            setTenant(connection, tenantId);
            List<QueueOutboxMessage> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId);
                statement.setInt(2, limit);
                statement.setString(3, workerId.trim());
                statement.setLong(4, leaseSeconds);
                statement.setObject(5, tenantId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) result.add(new QueueOutboxMessage(
                            rs.getObject("event_id", UUID.class), rs.getObject("tenant_id", UUID.class),
                            rs.getString("event_type"), rs.getInt("schema_version"),
                            rs.getString("aggregate_type"), rs.getString("aggregate_id"),
                            rs.getString("correlation_id"), rs.getTimestamp("occurred_at").toInstant(),
                            rs.getString("payload"), rs.getInt("attempt_count"), rs.getString("lease_owner"),
                            rs.getObject("lease_token", UUID.class), rs.getTimestamp("lease_until").toInstant()));
                }
            }
            return List.copyOf(result);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to claim queue outbox events", e);
        }
    }

    @Override
    @Transactional
    public boolean acknowledge(UUID tenantId, UUID eventId, UUID leaseToken) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseToken, "leaseToken");
        String sql = """
                UPDATE queue_outbox
                   SET published_at = CURRENT_TIMESTAMP, lease_owner = NULL,
                       lease_token = NULL, lease_until = NULL, last_error = NULL
                 WHERE tenant_id = ? AND event_id = ? AND lease_token = ?
                   AND published_at IS NULL AND dead_lettered_at IS NULL
                   AND lease_until > CURRENT_TIMESTAMP
                """;
        try (Connection connection = dataSource.getConnection()) {
            setTenant(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, eventId);
                statement.setObject(3, leaseToken);
                return statement.executeUpdate() == 1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to acknowledge queue outbox event", e);
        }
    }

    @Override
    @Transactional
    public RetryOutcome retry(UUID tenantId, UUID eventId, UUID leaseToken, Duration delay,
                              int maxAttempts, String error) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(leaseToken, "leaseToken");
        long delaySeconds = wholeSeconds(delay, "delay", true);
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        String safeError = error == null ? "Unspecified publisher failure"
                : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        String sql = """
                UPDATE queue_outbox
                   SET dead_lettered_at = CASE WHEN attempt_count >= ? THEN CURRENT_TIMESTAMP ELSE NULL END,
                       available_at = CASE WHEN attempt_count >= ? THEN available_at
                                           ELSE CURRENT_TIMESTAMP + make_interval(secs => ?) END,
                       last_error = ?, lease_owner = NULL, lease_token = NULL, lease_until = NULL
                 WHERE tenant_id = ? AND event_id = ? AND lease_token = ?
                   AND published_at IS NULL AND dead_lettered_at IS NULL
                   AND lease_until > CURRENT_TIMESTAMP
                RETURNING dead_lettered_at
                """;
        try (Connection connection = dataSource.getConnection()) {
            setTenant(connection, tenantId);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, maxAttempts);
                statement.setInt(2, maxAttempts);
                statement.setLong(3, delaySeconds);
                statement.setString(4, safeError);
                statement.setObject(5, tenantId);
                statement.setObject(6, eventId);
                statement.setObject(7, leaseToken);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) return RetryOutcome.LEASE_LOST;
                    return rs.getTimestamp(1) == null ? RetryOutcome.RESCHEDULED : RetryOutcome.DEAD_LETTERED;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to reschedule queue outbox event", e);
        }
    }

    private static void setTenant(Connection connection, UUID tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('numou.tenant_id', ?, true)")) {
            statement.setString(1, tenantId.toString());
            statement.execute();
        }
    }

    private static long wholeSeconds(Duration duration, String name, boolean allowZero) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || (!allowZero && duration.isZero()) || duration.getNano() != 0) {
            throw new IllegalArgumentException(name + (allowZero ? " must be non-negative whole seconds"
                    : " must be positive whole seconds"));
        }
        return duration.getSeconds();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}

package tech.kayys.numou.notification.persistence.jdbc;

import tech.kayys.numou.notification.application.port.NotificationRepository;
import tech.kayys.numou.notification.domain.Notification;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC adapter. Every read/mutation is tenant- and recipient-scoped;
 * uniqueness is enforced by the database to make concurrent retries safe.
 */
public final class JdbcNotificationRepository implements NotificationRepository {
    private static final String COLUMNS =
            "id, tenant_id, recipient_id, notification_type, title, body, source_reference, " +
            "idempotency_key, created_at, read_at";
    private final DataSource dataSource;

    public JdbcNotificationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public Notification createIfAbsent(Notification notification) {
        Objects.requireNonNull(notification, "notification is required");
        String insert = """
                INSERT INTO notification_inbox
                    (id, tenant_id, recipient_id, notification_type, title, body,
                     source_reference, idempotency_key, created_at, read_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, recipient_id, idempotency_key) DO NOTHING
                """;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setObject(1, notification.id());
                statement.setObject(2, notification.tenantId());
                statement.setObject(3, notification.recipientId());
                statement.setString(4, notification.type());
                statement.setString(5, notification.title());
                statement.setString(6, notification.body());
                statement.setString(7, notification.sourceReference());
                statement.setString(8, notification.idempotencyKey());
                statement.setTimestamp(9, Timestamp.from(notification.createdAt()));
                if (notification.readAt() == null) statement.setNull(10, Types.TIMESTAMP_WITH_TIMEZONE);
                else statement.setTimestamp(10, Timestamp.from(notification.readAt()));
                statement.executeUpdate();
            }
            return findByIdempotencyKey(connection, notification.tenantId(),
                    notification.recipientId(), notification.idempotencyKey())
                    .orElseThrow(() -> new SQLException("notification insert/read-back invariant failed"));
        } catch (SQLException e) {
            throw persistenceFailure("create notification", e);
        }
    }

    @Override
    public List<Notification> findInbox(UUID tenantId, UUID recipientId, int limit, Instant beforeCreatedAt) {
        requireScope(tenantId, recipientId);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        String sql = "SELECT " + COLUMNS + " FROM notification_inbox " +
                "WHERE tenant_id = ? AND recipient_id = ? " +
                (beforeCreatedAt == null ? "" : "AND created_at < ? ") +
                "ORDER BY created_at DESC, id DESC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, recipientId);
            int index = 3;
            if (beforeCreatedAt != null) statement.setTimestamp(index++, Timestamp.from(beforeCreatedAt));
            statement.setInt(index, limit);
            try (ResultSet rows = statement.executeQuery()) {
                List<Notification> result = new ArrayList<>();
                while (rows.next()) result.add(map(rows));
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw persistenceFailure("list notification inbox", e);
        }
    }

    @Override
    public Optional<Notification> findById(UUID tenantId, UUID recipientId, UUID notificationId) {
        requireScope(tenantId, recipientId);
        String sql = "SELECT " + COLUMNS + " FROM notification_inbox WHERE tenant_id = ? AND recipient_id = ? AND id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, recipientId);
            statement.setObject(3, notificationId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("find notification", e);
        }
    }

    @Override
    public Optional<Notification> markRead(UUID tenantId, UUID recipientId, UUID notificationId, Instant readAt) {
        requireScope(tenantId, recipientId);
        Objects.requireNonNull(notificationId, "notificationId is required");
        Objects.requireNonNull(readAt, "readAt is required");
        String sql = "UPDATE notification_inbox SET read_at = COALESCE(read_at, ?) " +
                "WHERE tenant_id = ? AND recipient_id = ? AND id = ? RETURNING " + COLUMNS;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(readAt));
            statement.setObject(2, tenantId);
            statement.setObject(3, recipientId);
            statement.setObject(4, notificationId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("mark notification read", e);
        }
    }

    private Optional<Notification> findByIdempotencyKey(
            Connection connection, UUID tenantId, UUID recipientId, String key) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM notification_inbox " +
                "WHERE tenant_id = ? AND recipient_id = ? AND idempotency_key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, recipientId);
            statement.setString(3, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    private static Notification map(ResultSet row) throws SQLException {
        Timestamp readAt = row.getTimestamp("read_at");
        return new Notification(
                row.getObject("id", UUID.class),
                row.getObject("tenant_id", UUID.class),
                row.getObject("recipient_id", UUID.class),
                row.getString("notification_type"),
                row.getString("title"),
                row.getString("body"),
                row.getString("source_reference"),
                row.getString("idempotency_key"),
                row.getTimestamp("created_at").toInstant(),
                readAt == null ? null : readAt.toInstant());
    }

    private static void requireScope(UUID tenantId, UUID recipientId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(recipientId, "recipientId is required");
    }

    private static IllegalStateException persistenceFailure(String operation, SQLException cause) {
        return new IllegalStateException("Failed to " + operation, cause);
    }
}
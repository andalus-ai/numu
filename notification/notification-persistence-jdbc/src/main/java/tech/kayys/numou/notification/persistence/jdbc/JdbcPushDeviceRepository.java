package tech.kayys.numou.notification.persistence.jdbc;

import tech.kayys.numou.notification.application.port.PushDeviceRepository;
import tech.kayys.numou.notification.domain.PushDevice;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL adapter for push-device registrations. Provider tokens are never
 * included in error messages. A fingerprint enforces global token ownership
 * without putting the raw token into an index.
 */
public final class JdbcPushDeviceRepository implements PushDeviceRepository {
    private static final String COLUMNS =
            "id, tenant_id, recipient_id, provider_token, platform, app_instance, " +
            "created_at, updated_at, disabled_at";
    private final DataSource dataSource;

    public JdbcPushDeviceRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
    }

    @Override
    public PushDevice registerOrRefresh(PushDevice device) {
        Objects.requireNonNull(device, "device is required");
        String sql = """
                INSERT INTO notification_push_device
                    (id, tenant_id, recipient_id, provider_token, token_fingerprint, platform,
                     app_instance, created_at, updated_at, disabled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (token_fingerprint) DO UPDATE SET
                    platform = EXCLUDED.platform,
                    app_instance = EXCLUDED.app_instance,
                    updated_at = EXCLUDED.updated_at,
                    disabled_at = NULL
                WHERE notification_push_device.tenant_id = EXCLUDED.tenant_id
                  AND notification_push_device.recipient_id = EXCLUDED.recipient_id
                """ + "RETURNING " + COLUMNS;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, device.id());
            statement.setObject(2, device.tenantId());
            statement.setObject(3, device.recipientId());
            statement.setString(4, device.token());
            statement.setString(5, fingerprint(device.token()));
            statement.setString(6, device.platform().name());
            statement.setString(7, device.appInstance());
            statement.setTimestamp(8, Timestamp.from(device.createdAt()));
            statement.setTimestamp(9, Timestamp.from(device.updatedAt()));
            if (device.disabledAt() == null) statement.setNull(10, Types.TIMESTAMP_WITH_TIMEZONE);
            else statement.setTimestamp(10, Timestamp.from(device.disabledAt()));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException(
                            "Push token is already registered to a different tenant or recipient");
                }
                return map(rows);
            }
        } catch (SQLException e) {
            throw persistenceFailure("register push device", e);
        }
    }

    @Override
    public List<PushDevice> findActiveByRecipient(UUID tenantId, UUID recipientId) {
        requireScope(tenantId, recipientId);
        String sql = "SELECT " + COLUMNS + " FROM notification_push_device " +
                "WHERE tenant_id = ? AND recipient_id = ? AND disabled_at IS NULL " +
                "ORDER BY updated_at DESC, id DESC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, recipientId);
            try (ResultSet rows = statement.executeQuery()) {
                List<PushDevice> result = new ArrayList<>();
                while (rows.next()) result.add(map(rows));
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw persistenceFailure("list push devices", e);
        }
    }

    @Override
    public Optional<PushDevice> disable(
            UUID tenantId, UUID recipientId, UUID deviceId, Instant disabledAt) {
        requireScope(tenantId, recipientId);
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(disabledAt, "disabledAt is required");
        String sql = "UPDATE notification_push_device SET disabled_at = COALESCE(disabled_at, ?), " +
                "updated_at = GREATEST(updated_at, ?) " +
                "WHERE tenant_id = ? AND recipient_id = ? AND id = ? RETURNING " + COLUMNS;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(disabledAt));
            statement.setTimestamp(2, Timestamp.from(disabledAt));
            statement.setObject(3, tenantId);
            statement.setObject(4, recipientId);
            statement.setObject(5, deviceId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("disable push device", e);
        }
    }

    private static PushDevice map(ResultSet row) throws SQLException {
        Timestamp disabledAt = row.getTimestamp("disabled_at");
        return new PushDevice(
                row.getObject("id", UUID.class),
                row.getObject("tenant_id", UUID.class),
                row.getObject("recipient_id", UUID.class),
                row.getString("provider_token"),
                PushDevice.Platform.valueOf(row.getString("platform")),
                row.getString("app_instance"),
                row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(),
                disabledAt == null ? null : disabledAt.toInstant());
    }

    private static String fingerprint(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void requireScope(UUID tenantId, UUID recipientId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(recipientId, "recipientId is required");
    }

    private static IllegalStateException persistenceFailure(String operation, SQLException cause) {
        return new IllegalStateException("Failed to " + operation, cause);
    }
}

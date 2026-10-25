package tech.kayys.numou.omnichannel.postgres;

import tech.kayys.numou.omnichannel.core.WebhookReplayReservation;
import tech.kayys.numou.omnichannel.core.WebhookReplayStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * Durable PostgreSQL replay reservation. The unique key is the concurrency
 * boundary; expired keys are reclaimed atomically by the same statement.
 */
public final class PostgresWebhookReplayStore implements WebhookReplayStore {
    private static final String RESERVE_SQL = """
            INSERT INTO numou_omnichannel_webhook_replay (
                provider_id, provider_account_id, replay_key_sha256, reserved_at, expires_at
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (provider_id, provider_account_id, replay_key_sha256)
            DO UPDATE SET reserved_at = EXCLUDED.reserved_at, expires_at = EXCLUDED.expires_at
             WHERE numou_omnichannel_webhook_replay.expires_at <= EXCLUDED.reserved_at
            """;

    private final DataSource dataSource;

    public PostgresWebhookReplayStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public boolean reserve(WebhookReplayReservation reservation) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) {
                throw new IllegalStateException("PostgresWebhookReplayStore requires auto-commit connections");
            }
            try (PreparedStatement statement = connection.prepareStatement(RESERVE_SQL)) {
                statement.setString(1, reservation.providerId());
                statement.setString(2, reservation.providerAccountId());
                statement.setString(3, reservation.replayKeySha256());
                statement.setTimestamp(4, Timestamp.from(reservation.now()));
                statement.setTimestamp(5, Timestamp.from(reservation.expiresAt()));
                return statement.executeUpdate() == 1;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to reserve webhook replay key in PostgreSQL", exception);
        }
    }
}
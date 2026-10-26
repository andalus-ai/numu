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

    private static final String DELETE_EXPIRED_SQL = """
            DELETE FROM numou_omnichannel_webhook_replay
             WHERE ctid IN (
                 SELECT ctid
                   FROM numou_omnichannel_webhook_replay
                  WHERE expires_at <= ?
                  ORDER BY expires_at
                  LIMIT ?
             )
            """;

    private final DataSource dataSource;

    public PostgresWebhookReplayStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    /**
     * Deletes at most {@code batchSize} expired rows. Invoke from a scheduled
     * maintenance task; bounded batches avoid long-running cleanup transactions.
     */
    public int deleteExpired(java.time.Instant cutoff, int batchSize) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        if (batchSize < 1 || batchSize > 10000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 10000");
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) {
                throw new IllegalStateException("PostgresWebhookReplayStore requires auto-commit connections");
            }
            try (PreparedStatement statement = connection.prepareStatement(DELETE_EXPIRED_SQL)) {
                statement.setTimestamp(1, Timestamp.from(cutoff));
                statement.setInt(2, batchSize);
                return statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to clean expired webhook replay keys in PostgreSQL", exception);
        }
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
package tech.kayys.numou.pos.adapter.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.numou.pos.application.port.out.PosOutboxDelivery;
import tech.kayys.numou.pos.application.port.out.PosOutboxDeliveryStore;
import tech.kayys.numou.pos.application.port.out.PosOutboxEvent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** PostgreSQL implementation using SKIP LOCKED and per-claim fencing tokens. */
@ApplicationScoped
public class PostgresPosOutboxDeliveryStore implements PosOutboxDeliveryStore {
    private final DataSource dataSource;

    @Inject
    public PostgresPosOutboxDeliveryStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @Transactional
    public List<PosOutboxDelivery> claimDue(Instant now, Instant leaseUntil, int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        String sql = """
                WITH due AS (
                    SELECT event_id
                      FROM pos_outbox_event
                     WHERE (delivery_status = 'PENDING' AND available_at <= ?)
                        OR (delivery_status = 'IN_FLIGHT' AND lease_until <= ?)
                     ORDER BY available_at, created_at
                     FOR UPDATE SKIP LOCKED
                     LIMIT ?
                )
                UPDATE pos_outbox_event event
                   SET delivery_status = 'IN_FLIGHT',
                       attempt_count = event.attempt_count + 1,
                       locked_at = ?,
                       lease_until = ?,
                       lease_token = gen_random_uuid(),
                       updated_at = ?
                  FROM due
                 WHERE event.event_id = due.event_id
                RETURNING event.event_id, event.tenant_id, event.aggregate_type,
                          event.aggregate_id, event.event_type, event.schema_version,
                          event.occurred_at, event.payload::text, event.attempt_count,
                          event.lease_token
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp instant = Timestamp.from(now);
            statement.setTimestamp(1, instant);
            statement.setTimestamp(2, instant);
            statement.setInt(3, limit);
            statement.setTimestamp(4, instant);
            statement.setTimestamp(5, Timestamp.from(leaseUntil));
            statement.setTimestamp(6, instant);
            try (ResultSet rows = statement.executeQuery()) {
                List<PosOutboxDelivery> claimed = new ArrayList<>();
                while (rows.next()) {
                    PosOutboxEvent event = new PosOutboxEvent(
                            rows.getObject(1, UUID.class),
                            rows.getObject(2, UUID.class),
                            rows.getString(3), rows.getString(4), rows.getString(5),
                            rows.getInt(6), rows.getTimestamp(7).toInstant(), rows.getString(8));
                    claimed.add(new PosOutboxDelivery(event, rows.getInt(9),
                            rows.getObject(10, UUID.class)));
                }
                return List.copyOf(claimed);
            }
        } catch (SQLException exception) {
            throw storageFailure("claim due POS outbox events", exception);
        }
    }

    @Override
    @Transactional
    public boolean markPublished(UUID eventId, UUID leaseToken, Instant publishedAt) {
        String sql = """
                UPDATE pos_outbox_event
                   SET delivery_status = 'PUBLISHED', published_at = ?,
                       locked_at = NULL, lease_until = NULL, lease_token = NULL,
                       last_error = NULL, updated_at = ?
                 WHERE event_id = ? AND delivery_status = 'IN_FLIGHT' AND lease_token = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp timestamp = Timestamp.from(publishedAt);
            statement.setTimestamp(1, timestamp);
            statement.setTimestamp(2, timestamp);
            statement.setObject(3, eventId);
            statement.setObject(4, leaseToken);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw storageFailure("mark POS outbox event published", exception);
        }
    }

    @Override
    @Transactional
    public boolean reschedule(UUID eventId, UUID leaseToken, Instant availableAt,
            String error, boolean dead) {
        String sql = """
                UPDATE pos_outbox_event
                   SET delivery_status = ?, available_at = ?,
                       locked_at = NULL, lease_until = NULL, lease_token = NULL,
                       last_error = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE event_id = ? AND delivery_status = 'IN_FLIGHT' AND lease_token = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dead ? "DEAD" : "PENDING");
            statement.setTimestamp(2, Timestamp.from(availableAt));
            statement.setString(3, error);
            statement.setObject(4, eventId);
            statement.setObject(5, leaseToken);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw storageFailure("reschedule POS outbox event", exception);
        }
    }

    private static RuntimeException storageFailure(String operation, SQLException cause) {
        return new PostgresSaleRepository.PosStorageException("Unable to " + operation, cause);
    }
}

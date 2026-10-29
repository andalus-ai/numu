package tech.kayys.numou.pos.adapter.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.numou.pos.application.port.out.PosOutboxOperations;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** PostgreSQL operational queries; replay mutation and audit append share one transaction. */
@ApplicationScoped
public class PostgresPosOutboxOperations implements PosOutboxOperations {
    private final DataSource dataSource;
    @Inject
    public PostgresPosOutboxOperations(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public Map<String, Long> countByDeliveryStatus() {
        String sql = "SELECT delivery_status, COUNT(*) FROM pos_outbox_event GROUP BY delivery_status";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            Map<String, Long> counts = new LinkedHashMap<>();
            counts.put("PENDING", 0L);
            counts.put("IN_FLIGHT", 0L);
            counts.put("PUBLISHED", 0L);
            counts.put("DEAD", 0L);
            while (rows.next()) counts.put(rows.getString(1), rows.getLong(2));
            return Map.copyOf(counts);
        } catch (SQLException exception) {
            throw storageFailure("count POS outbox delivery statuses", exception);
        }
    }

    @Override
    @Transactional
    public boolean replayDeadLetter(UUID tenantId, UUID eventId, String actor,
            String reason, Instant replayedAt) {
        String update = """
                UPDATE pos_outbox_event
                   SET delivery_status = 'PENDING', available_at = ?, attempt_count = 0,
                       replay_count = replay_count + 1, last_replayed_at = ?,
                       last_error = NULL, locked_at = NULL, lease_until = NULL,
                       lease_token = NULL, updated_at = ?
                 WHERE tenant_id = ? AND event_id = ? AND delivery_status = 'DEAD'
                """;
        String audit = """
                INSERT INTO pos_outbox_replay_audit
                    (tenant_id, event_id, actor, reason, replayed_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement updateStatement = connection.prepareStatement(update)) {
            Timestamp timestamp = Timestamp.from(replayedAt);
            updateStatement.setTimestamp(1, timestamp);
            updateStatement.setTimestamp(2, timestamp);
            updateStatement.setTimestamp(3, timestamp);
            updateStatement.setObject(4, tenantId);
            updateStatement.setObject(5, eventId);
            if (updateStatement.executeUpdate() != 1) return false;
            try (PreparedStatement auditStatement = connection.prepareStatement(audit)) {
                auditStatement.setObject(1, tenantId);
                auditStatement.setObject(2, eventId);
                auditStatement.setString(3, actor);
                auditStatement.setString(4, reason);
                auditStatement.setTimestamp(5, timestamp);
                auditStatement.executeUpdate();
            }
            return true;
        } catch (SQLException exception) {
            throw storageFailure("replay POS dead-letter event", exception);
        }
    }

    private static RuntimeException storageFailure(String operation, SQLException cause) {
        return new PostgresSaleRepository.PosStorageException("Unable to " + operation, cause);
    }
}

package tech.kayys.numou.pos.adapter.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.numou.pos.application.port.out.PosOutboxEvent;
import tech.kayys.numou.pos.application.port.out.PosOutboxWriter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/** PostgreSQL outbox writer. Inserts are enlisted in the active JTA transaction. */
@ApplicationScoped
public class PostgresPosOutboxWriter implements PosOutboxWriter {
    private final DataSource dataSource;

    @Inject
    public PostgresPosOutboxWriter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @Transactional
    public void append(PosOutboxEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        String sql = """
                INSERT INTO pos_outbox_event
                    (event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                     schema_version, occurred_at, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, event.eventId());
            statement.setObject(2, event.tenantId());
            statement.setString(3, event.aggregateType());
            statement.setString(4, event.aggregateId());
            statement.setString(5, event.eventType());
            statement.setInt(6, event.schemaVersion());
            statement.setObject(7, event.occurredAt());
            statement.setString(8, event.payloadJson());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PostgresSaleRepository.PosStorageException(
                    "Unable to append POS outbox event " + event.eventId(), exception);
        }
    }
}

package tech.kayys.numou.pos.adapter.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import tech.kayys.numou.pos.application.port.in.PosEventInbox;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * PostgreSQL inbox adapter. The marker insert and handler execute in one JTA
 * transaction; handler failure rolls back the marker so broker redelivery can retry.
 */
@ApplicationScoped
public class PostgresPosEventInbox implements PosEventInbox {
    private final DataSource dataSource;

    @Inject
    public PostgresPosEventInbox(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @Override
    @Transactional
    public boolean executeOnce(UUID tenantId, String consumerName, UUID eventId, Runnable handler) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName cannot be blank");
        }

        String sql = """
                INSERT INTO pos_consumer_inbox (tenant_id, consumer_name, event_id)
                VALUES (?, ?, ?)
                ON CONFLICT (tenant_id, consumer_name, event_id) DO NOTHING
                RETURNING event_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setString(2, consumerName.trim());
            statement.setObject(3, eventId);
            boolean inserted;
            try (ResultSet result = statement.executeQuery()) {
                inserted = result.next();
            }
            if (!inserted) return false;

            // Must remain inside this method's transaction; do not dispatch asynchronously here.
            handler.run();
            return true;
        } catch (SQLException exception) {
            throw new PostgresSaleRepository.PosStorageException(
                    "Unable to execute POS consumer inbox event " + eventId, exception);
        }
    }
}

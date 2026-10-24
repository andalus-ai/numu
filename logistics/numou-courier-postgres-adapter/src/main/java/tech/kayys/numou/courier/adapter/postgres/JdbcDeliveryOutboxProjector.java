package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Projects delivery outbox rows into append-only M3 tracking events.
 * Each batch claims rows with SKIP LOCKED and marks publication in the same transaction
 * as event insertion, making concurrent workers safe and retries idempotent.
 */
@ApplicationScoped
public class JdbcDeliveryOutboxProjector {
    private final DataSource dataSource;

    @Inject
    public JdbcDeliveryOutboxProjector(DataSource dataSource) { this.dataSource = dataSource; }

    public CompletionStage<Integer> projectBatch(int limit) {
        if (limit < 1 || limit > 500)
            return CompletableFuture.failedFuture(new IllegalArgumentException("limit must be between 1 and 500"));
        try (Connection connection = dataSource.getConnection()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int projected = 0;
                try (PreparedStatement claim = connection.prepareStatement("""
                        SELECT tenant_id, id, shipment_id, event_type, event_key, occurred_at,
                               payload ->> 'courierReference' AS courier_reference,
                               payload ->> 'failureReason' AS failure_reason
                        FROM courier_operational_outbox
                        WHERE published_at IS NULL AND next_attempt_at <= now()
                        ORDER BY created_at, id
                        FOR UPDATE SKIP LOCKED
                        LIMIT ?
                        """)) {
                    claim.setInt(1, limit);
                    try (ResultSet rows = claim.executeQuery()) {
                        while (rows.next()) {
                            Savepoint savepoint = connection.setSavepoint();
                            UUID tenantId = rows.getObject("tenant_id", UUID.class);
                            UUID outboxId = rows.getObject("id", UUID.class);
                            UUID shipmentId = rows.getObject("shipment_id", UUID.class);
                            String eventType = rows.getString("event_type");
                            String eventKey = rows.getString("event_key");
                            Timestamp occurredAt = rows.getTimestamp("occurred_at");
                            String courierReference = rows.getString("courier_reference");
                            String failureReason = rows.getString("failure_reason");
                            try {
                                insertTrackingEvent(connection, tenantId, shipmentId, eventType, eventKey,
                                        occurredAt, courierReference, failureReason);
                                try (PreparedStatement published = connection.prepareStatement("""
                                        UPDATE courier_operational_outbox
                                        SET published_at = now(), attempts = attempts + 1, last_error = NULL
                                        WHERE tenant_id = ? AND id = ? AND published_at IS NULL
                                        """)) {
                                    published.setObject(1, tenantId);
                                    published.setObject(2, outboxId);
                                    published.executeUpdate();
                                }
                                projected++;
                            } catch (SQLException failure) {
                                connection.rollback(savepoint);
                                try (PreparedStatement retry = connection.prepareStatement("""
                                        UPDATE courier_operational_outbox
                                        SET attempts = attempts + 1,
                                            next_attempt_at = now() + interval '60 seconds',
                                            last_error = ?
                                        WHERE tenant_id = ? AND id = ? AND published_at IS NULL
                                        """)) {
                                    String message = failure.getMessage() == null ? "projection failed" : failure.getMessage();
                                    retry.setString(1, message.substring(0, Math.min(1000, message.length())));
                                    retry.setObject(2, tenantId);
                                    retry.setObject(3, outboxId);
                                    retry.executeUpdate();
                                }
                            }
                        }
                    }
                }
                connection.commit();
                return CompletableFuture.completedFuture(projected);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                return CompletableFuture.failedFuture(new IllegalStateException("Unable to project delivery outbox", failure));
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(new IllegalStateException("Unable to open outbox projection transaction", failure));
        }
    }

    private static void insertTrackingEvent(Connection connection, UUID tenantId, UUID shipmentId,
            String outboxType, String eventKey, Timestamp occurredAt, String courierReference,
            String failureReason) throws SQLException {
        String trackingType = switch (outboxType) {
            case "OUT_FOR_DELIVERY" -> "OUT_FOR_DELIVERY";
            case "DELIVERY_ATTEMPTED" -> "DELIVERY_ATTEMPTED";
            case "DELIVERED" -> "DELIVERED";
            case "DELIVERY_FAILED" -> "DELIVERY_FAILED";
            case "DELIVERY_CANCELLED" -> "EXCEPTION_RECORDED";
            default -> throw new IllegalArgumentException("unsupported delivery outbox event type: " + outboxType);
        };
        String note = switch (outboxType) {
            case "DELIVERY_ATTEMPTED", "DELIVERY_FAILED" -> failureReason;
            case "DELIVERY_CANCELLED" -> "Delivery assignment cancelled";
            default -> null;
        };
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO courier_tracking_events
                  (tenant_id, id, shipment_id, parcel_id, event_type, occurred_at, recorded_at,
                   location_code, actor_reference, source, idempotency_key, note)
                VALUES (?, ?, ?, NULL, ?, ?, GREATEST(now(), ?), NULL, ?, 'DELIVERY_TASK', ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, shipmentId);
            statement.setString(4, trackingType);
            statement.setTimestamp(5, occurredAt);
            statement.setTimestamp(6, occurredAt);
            statement.setString(7, courierReference);
            statement.setString(8, eventKey);
            statement.setString(9, note);
            statement.executeUpdate();
        }
    }
}

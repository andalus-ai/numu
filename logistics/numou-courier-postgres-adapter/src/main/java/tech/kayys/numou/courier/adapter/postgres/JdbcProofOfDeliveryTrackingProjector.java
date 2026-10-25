package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Projects committed proof-of-delivery outbox events into the append-only tracking timeline.
 * Claim, timeline insert, and outbox acknowledgement share one PostgreSQL transaction.
 */
@ApplicationScoped
public class JdbcProofOfDeliveryTrackingProjector {
    private static final String EVENT_TYPE = "courier.proof-of-delivery.recorded.v1";

    private final DataSource dataSource;

    @Inject
    public JdbcProofOfDeliveryTrackingProjector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public CompletionStage<Integer> projectBatch(int limit) {
        if (limit < 1 || limit > 500) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("limit must be between 1 and 500"));
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int projected = 0;
                try (PreparedStatement claim = connection.prepareStatement("""
                        SELECT tenant_id, id, occurred_at,
                               payload ->> 'proofId' AS proof_id,
                               payload ->> 'shipmentReference' AS shipment_reference
                        FROM courier_event_outbox
                        WHERE event_type = ?
                          AND published_at IS NULL
                          AND available_at <= now()
                        ORDER BY created_at, id
                        FOR UPDATE SKIP LOCKED
                        LIMIT ?
                        """)) {
                    claim.setString(1, EVENT_TYPE);
                    claim.setInt(2, limit);
                    try (ResultSet rows = claim.executeQuery()) {
                        while (rows.next()) {
                            Savepoint savepoint = connection.setSavepoint();
                            UUID tenantId = rows.getObject("tenant_id", UUID.class);
                            UUID outboxId = rows.getObject("id", UUID.class);
                            Timestamp occurredAt = rows.getTimestamp("occurred_at");
                            String proofId = rows.getString("proof_id");
                            String shipmentReference = rows.getString("shipment_reference");
                            try {
                                projectOne(connection, tenantId, occurredAt, proofId, shipmentReference);
                                try (PreparedStatement acknowledge = connection.prepareStatement("""
                                        UPDATE courier_event_outbox
                                        SET published_at = now(), attempt_count = attempt_count + 1,
                                            last_error = NULL, lease_owner = NULL, lease_until = NULL
                                        WHERE tenant_id = ? AND id = ? AND published_at IS NULL
                                        """)) {
                                    acknowledge.setObject(1, tenantId);
                                    acknowledge.setObject(2, outboxId);
                                    if (acknowledge.executeUpdate() != 1) {
                                        throw new SQLException("outbox acknowledgement lost its row");
                                    }
                                }
                                projected++;
                            } catch (SQLException | RuntimeException failure) {
                                connection.rollback(savepoint);
                                recordRetry(connection, tenantId, outboxId, failure);
                            }
                        }
                    }
                }
                connection.commit();
                return CompletableFuture.completedFuture(projected);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Unable to project proof-of-delivery outbox", failure));
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Unable to open proof-of-delivery projection transaction", failure));
        }
    }

    private static void projectOne(Connection connection, UUID tenantId, Timestamp occurredAt,
                                   String proofId, String shipmentReference) throws SQLException {
        if (proofId == null || proofId.isBlank() || shipmentReference == null || shipmentReference.isBlank()) {
            throw new IllegalArgumentException("POD outbox payload is missing proofId or shipmentReference");
        }

        UUID shipmentId;
        try (PreparedStatement lookup = connection.prepareStatement("""
                SELECT id
                FROM courier_shipments
                WHERE tenant_id = ?
                  AND normalized_reference = upper(trim(?))
                """)) {
            lookup.setObject(1, tenantId);
            lookup.setString(2, shipmentReference);
            try (ResultSet row = lookup.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("POD event references an unknown shipment for its tenant");
                }
                shipmentId = row.getObject("id", UUID.class);
            }
        }

        String idempotencyKey = "POD:" + proofId;
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO courier_tracking_events
                  (tenant_id, id, shipment_id, parcel_id, event_type, occurred_at, recorded_at,
                   location_code, actor_reference, source, idempotency_key, note)
                VALUES (?, ?, ?, NULL, 'DELIVERED', ?, GREATEST(now(), ?),
                        NULL, NULL, 'PROOF_OF_DELIVERY', ?, NULL)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """)) {
            insert.setObject(1, tenantId);
            insert.setObject(2, UUID.randomUUID());
            insert.setObject(3, shipmentId);
            insert.setTimestamp(4, occurredAt);
            insert.setTimestamp(5, occurredAt);
            insert.setString(6, idempotencyKey);
            if (insert.executeUpdate() == 1) {
                return;
            }
        }

        // A replay is safe only if the existing idempotency key describes this same fact.
        try (PreparedStatement verify = connection.prepareStatement("""
                SELECT 1
                FROM courier_tracking_events
                WHERE tenant_id = ? AND idempotency_key = ?
                  AND shipment_id = ? AND event_type = 'DELIVERED'
                  AND occurred_at = ?
                """)) {
            verify.setObject(1, tenantId);
            verify.setString(2, idempotencyKey);
            verify.setObject(3, shipmentId);
            verify.setTimestamp(4, occurredAt);
            try (ResultSet row = verify.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("POD idempotency key already represents a different tracking fact");
                }
            }
        }
    }

    private static void recordRetry(Connection connection, UUID tenantId, UUID outboxId,
                                    Throwable failure) throws SQLException {
        String message = failure.getMessage() == null ? "projection failed" : failure.getMessage();
        message = message.substring(0, Math.min(1000, message.length()));
        try (PreparedStatement retry = connection.prepareStatement("""
                UPDATE courier_event_outbox
                SET attempt_count = attempt_count + 1,
                    available_at = now() + make_interval(secs =>
                        LEAST(3600, 15 * power(2, LEAST(attempt_count, 8)))::integer),
                    last_error = ?, lease_owner = NULL, lease_until = NULL
                WHERE tenant_id = ? AND id = ? AND published_at IS NULL
                """)) {
            retry.setString(1, message);
            retry.setObject(2, tenantId);
            retry.setObject(3, outboxId);
            retry.executeUpdate();
        }
    }
}

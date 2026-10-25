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
                               payload ->> 'shipmentReference' AS shipment_reference,
                               payload ->> 'deliveryAttemptReference' AS attempt_reference,
                               payload ->> 'deliveredAt' AS delivered_at,
                               payload ->> 'recipientLabel' AS recipient_label,
                               payload ->> 'evidenceReference' AS evidence_reference
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
                            String attemptReference = rows.getString("attempt_reference");
                            Timestamp deliveredAt = parseTimestamp(rows.getString("delivered_at"), "deliveredAt");
                            String recipientLabel = rows.getString("recipient_label");
                            String evidenceReference = rows.getString("evidence_reference");
                            try {
                                projectOne(connection, tenantId, occurredAt, proofId, shipmentReference,
                                        attemptReference, deliveredAt, recipientLabel, evidenceReference);
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
                                   String proofId, String shipmentReference, String attemptReference,
                                   Timestamp deliveredAt, String recipientLabel, String evidenceReference)
            throws SQLException {
        if (proofId == null || proofId.isBlank() || shipmentReference == null || shipmentReference.isBlank()
                || attemptReference == null || attemptReference.isBlank() || deliveredAt == null
                || recipientLabel == null || recipientLabel.isBlank()
                || evidenceReference == null || evidenceReference.isBlank()) {
            throw new IllegalArgumentException("POD outbox payload is missing lifecycle fields");
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

        applyDeliveryOutcome(connection, tenantId, shipmentId, attemptReference, proofId,
                deliveredAt, recipientLabel, evidenceReference);

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

    private static void applyDeliveryOutcome(Connection connection, UUID tenantId, UUID shipmentId,
                                              String attemptReference, String proofId, Timestamp deliveredAt,
                                              String recipientLabel, String evidenceReference) throws SQLException {
        String updateSql = """
                UPDATE courier_delivery_tasks
                SET status = 'DELIVERED',
                    delivered_at = ?,
                    recipient_name = ?,
                    proof_reference = ?,
                    failure_reason = NULL,
                    updated_at = GREATEST(now(), updated_at, ?)
                WHERE tenant_id = ? AND shipment_id = ? AND delivery_attempt_reference = ?
                  AND status IN ('OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPTED')
                  AND out_for_delivery_at IS NOT NULL
                  AND ? >= out_for_delivery_at
                """;
        try (PreparedStatement update = connection.prepareStatement(updateSql)) {
            update.setTimestamp(1, deliveredAt);
            update.setString(2, recipientLabel);
            update.setString(3, evidenceReference);
            update.setTimestamp(4, deliveredAt);
            update.setObject(5, tenantId);
            update.setObject(6, shipmentId);
            update.setString(7, attemptReference);
            update.setTimestamp(8, deliveredAt);
            if (update.executeUpdate() == 1) return;
        }

        // Concurrent/replayed POD is accepted only if it is the exact outcome already persisted.
        try (PreparedStatement verify = connection.prepareStatement("""
                SELECT 1 FROM courier_delivery_tasks
                WHERE tenant_id = ? AND shipment_id = ? AND delivery_attempt_reference = ?
                  AND status = 'DELIVERED' AND proof_reference = ?
                  AND recipient_name = ? AND delivered_at = ?
                """)) {
            verify.setObject(1, tenantId);
            verify.setObject(2, shipmentId);
            verify.setString(3, attemptReference);
            verify.setString(4, evidenceReference);
            verify.setString(5, recipientLabel);
            verify.setTimestamp(6, deliveredAt);
            try (ResultSet row = verify.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("POD does not match an active delivery attempt or conflicts with its outcome");
                }
            }
        }
    }

    private static Timestamp parseTimestamp(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("POD outbox payload is missing " + field);
        }
        try {
            return Timestamp.from(java.time.Instant.parse(value));
        } catch (java.time.DateTimeException failure) {
            throw new IllegalArgumentException("POD outbox payload has invalid " + field, failure);
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

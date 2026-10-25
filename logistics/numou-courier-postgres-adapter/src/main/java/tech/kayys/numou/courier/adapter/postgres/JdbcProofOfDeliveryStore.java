package tech.kayys.numou.courier.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.courier.application.port.ProofOfDeliveryStore;
import tech.kayys.numou.courier.application.port.ProofOfDeliveryStore.Command;
import tech.kayys.numou.courier.application.port.ProofOfDeliveryStore.Outcome;
import tech.kayys.numou.courier.application.port.ProofOfDeliveryStore.Result;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * JDBC adapter for append-only POD evidence and its transactional event outbox.
 * A newly accepted evidence row and its outbox event commit or roll back together.
 */
@ApplicationScoped
public class JdbcProofOfDeliveryStore implements ProofOfDeliveryStore {
    private static final String POD_EVENT_TYPE = "courier.proof-of-delivery.recorded.v1";
    private final DataSource dataSource;

    @Inject
    public JdbcProofOfDeliveryStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public CompletionStage<Result> record(Command command) {
        if (command == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("command must not be null"));
        try {
            return CompletableFuture.completedFuture(recordTransactionally(command));
        } catch (SQLException failure) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "could not persist proof-of-delivery evidence", failure));
        }
    }

    private Result recordTransactionally(Command command) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String insertSql = """
                    INSERT INTO courier_proof_of_delivery
                      (tenant_id, id, shipment_reference, delivery_attempt_reference, delivered_at,
                       recipient_label, evidence_reference, evidence_sha256, actor_reference, source,
                       idempotency_key, payload_fingerprint, occurred_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, source, idempotency_key) DO NOTHING
                    """;
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                    int i = 1;
                    statement.setObject(i++, command.tenantId().value());
                    statement.setObject(i++, command.proofId());
                    statement.setString(i++, command.shipmentReference());
                    statement.setString(i++, command.deliveryAttemptReference());
                    statement.setTimestamp(i++, Timestamp.from(command.deliveredAt()));
                    statement.setString(i++, command.recipientLabel());
                    statement.setString(i++, command.evidenceReference());
                    statement.setString(i++, command.evidenceSha256());
                    statement.setString(i++, command.actorReference());
                    statement.setString(i++, command.source());
                    statement.setString(i++, command.idempotencyKey());
                    statement.setString(i++, command.payloadFingerprint());
                    statement.setTimestamp(i, Timestamp.from(command.occurredAt()));
                    inserted = statement.executeUpdate();
                }
                if (inserted == 1) {
                    insertOutboxEvent(connection, command);
                    connection.commit();
                    return new Result(Outcome.ACCEPTED, command.proofId());
                }

                String selectSql = """
                    SELECT id, payload_fingerprint
                    FROM courier_proof_of_delivery
                    WHERE tenant_id = ? AND source = ? AND idempotency_key = ?
                    FOR SHARE
                    """;
                try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                    statement.setObject(1, command.tenantId().value());
                    statement.setString(2, command.source());
                    statement.setString(3, command.idempotencyKey());
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) throw new SQLException("idempotency conflict row disappeared");
                        UUID existingId = rows.getObject("id", UUID.class);
                        String existingFingerprint = rows.getString("payload_fingerprint");
                        connection.commit();
                        if (Objects.equals(existingFingerprint, command.payloadFingerprint())) {
                            return new Result(Outcome.REPLAYED, existingId);
                        }
                        return new Result(Outcome.IDEMPOTENCY_CONFLICT, existingId);
                    }
                }
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                try { connection.setAutoCommit(previousAutoCommit); } catch (SQLException ignored) { }
            }
        }
    }

    private void insertOutboxEvent(Connection connection, Command command) throws SQLException {
        String sql = """
            INSERT INTO courier_event_outbox
              (tenant_id, id, event_type, aggregate_type, aggregate_reference, occurred_at, payload)
            VALUES (?, ?, ?, 'shipment', ?, ?, jsonb_build_object(
              'proofId', ?, 'shipmentReference', ?, 'deliveryAttemptReference', ?, 'deliveredAt', ?))
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, command.tenantId().value());
            statement.setObject(2, UUID.randomUUID());
            statement.setString(3, POD_EVENT_TYPE);
            statement.setString(4, command.shipmentReference());
            statement.setTimestamp(5, Timestamp.from(command.occurredAt()));
            statement.setString(6, command.proofId().toString());
            statement.setString(7, command.shipmentReference());
            statement.setString(8, command.deliveryAttemptReference());
            statement.setString(9, command.deliveredAt().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("proof-of-delivery outbox event was not inserted");
            }
        }
    }
}

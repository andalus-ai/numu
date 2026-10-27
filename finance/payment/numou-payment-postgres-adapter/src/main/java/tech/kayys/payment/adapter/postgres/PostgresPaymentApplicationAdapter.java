package tech.kayys.payment.adapter.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.payment.api.CreatePaymentObligation;
import tech.kayys.payment.api.PaymentApplicationPort;
import tech.kayys.payment.api.PaymentObligation;
import tech.kayys.payment.api.PaymentRequestFingerprint;
import tech.kayys.payment.api.PaymentStatus;
import tech.kayys.payment.api.RefundOperation;
import tech.kayys.payment.api.RefundStatus;
import tech.kayys.payment.api.RequestPaymentRefund;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of the neutral payment application port.
 *
 * <p>Payment/refund records, idempotency fingerprints, and outbox records are
 * committed in one database transaction. This adapter records obligations and
 * refund requests; provider execution and verified provider-state transitions
 * are deliberately performed by separate workers/adapters.</p>
 */
@ApplicationScoped
public class PostgresPaymentApplicationAdapter implements PaymentApplicationPort {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DataSource dataSource;

    @Inject
    public PostgresPaymentApplicationAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public PaymentObligation createOrGet(CreatePaymentObligation command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        String fingerprint = PaymentRequestFingerprint.sha256(command);
        return inTransaction(connection -> {
            ensurePersistableAmount(command.amount());
            UUID candidateId = UUID.randomUUID();
            String insert = """
                    INSERT INTO payment_obligation (
                        payment_id, tenant_id, idempotency_key, request_fingerprint,
                        caller_reference, amount, currency, payment_status, description, metadata
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 'CREATED', ?, CAST(? AS jsonb))
                    ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                    """;
            int inserted;
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setObject(1, candidateId);
                statement.setString(2, command.tenantId());
                statement.setString(3, command.idempotencyKey());
                statement.setString(4, fingerprint);
                statement.setString(5, command.callerReference());
                statement.setBigDecimal(6, command.amount());
                statement.setString(7, command.currency());
                statement.setString(8, command.description());
                statement.setString(9, toJson(command.metadata()));
                inserted = statement.executeUpdate();
            }

            if (inserted == 1) {
                insertOutbox(connection, command.tenantId(), "PAYMENT", candidateId,
                        "PAYMENT_OBLIGATION_CREATED",
                        Map.of("paymentId", candidateId.toString(),
                                "callerReference", command.callerReference(),
                                "currency", command.currency()));
                return findPayment(connection, command.tenantId(), candidateId)
                        .orElseThrow(() -> new IllegalStateException("Created payment could not be reloaded"));
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT payment_id, request_fingerprint
                    FROM payment_obligation
                    WHERE tenant_id = ? AND idempotency_key = ?
                    """)) {
                statement.setString(1, command.tenantId());
                statement.setString(2, command.idempotencyKey());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalStateException("Idempotency conflict row disappeared during lookup");
                    }
                    if (!fingerprint.equals(result.getString("request_fingerprint"))) {
                        throw new IdempotencyConflictException(
                                "Payment idempotency key was already used for a different request");
                    }
                    UUID existingId = result.getObject("payment_id", UUID.class);
                    return findPayment(connection, command.tenantId(), existingId)
                            .orElseThrow(() -> new IllegalStateException("Idempotent payment could not be reloaded"));
                }
            }
        });
    }

    @Override
    public Optional<PaymentObligation> find(String tenantId, String paymentId) {
        String tenant = required(tenantId, "tenantId");
        UUID id = parseUuid(paymentId, "paymentId");
        try (Connection connection = dataSource.getConnection()) {
            return findPayment(connection, tenant, id);
        } catch (SQLException exception) {
            throw persistenceFailure("find payment obligation", exception);
        }
    }

    @Override
    public RefundOperation requestRefund(RequestPaymentRefund command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        String tenant = command.tenantId();
        UUID paymentId = parseUuid(command.paymentId(), "paymentId");
        String fingerprint = refundFingerprint(command);
        ensurePersistableAmount(command.amount());
        return inTransaction(connection -> {
            // Lock the tenant-owned payment row to serialize refund-limit checks.
            BigDecimal paymentAmount;
            String currency;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT amount, currency
                    FROM payment_obligation
                    WHERE tenant_id = ? AND payment_id = ?
                    FOR UPDATE
                    """)) {
                statement.setString(1, tenant);
                statement.setObject(2, paymentId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new PaymentOperationRejectedException(
                                "Payment does not exist within the supplied tenant");
                    }
                    paymentAmount = result.getBigDecimal("amount");
                    currency = result.getString("currency").trim();
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(? || ':' || ?, 0))")) {
                statement.setString(1, tenant);
                statement.setString(2, command.idempotencyKey());
                statement.execute();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT refund_id, payment_id, request_fingerprint, amount, currency,
                           refund_status, updated_at
                    FROM payment_refund_operation
                    WHERE tenant_id = ? AND idempotency_key = ?
                    """)) {
                statement.setString(1, tenant);
                statement.setString(2, command.idempotencyKey());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        if (!fingerprint.equals(result.getString("request_fingerprint"))) {
                            throw new IdempotencyConflictException(
                                    "Refund idempotency key was already used for a different request");
                        }
                        if (!paymentId.equals(result.getObject("payment_id", UUID.class))) {
                            throw new IdempotencyConflictException(
                                    "Refund idempotency key belongs to a different payment");
                        }
                        return new RefundOperation(tenant, result.getObject("refund_id", UUID.class).toString(),
                                paymentId.toString(), result.getBigDecimal("amount"),
                                result.getString("currency").trim(),
                                RefundStatus.valueOf(result.getString("refund_status")),
                                result.getTimestamp("updated_at").toInstant());
                    }
                }
            }

            BigDecimal alreadyCommitted;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT COALESCE(SUM(amount), 0) AS refunded
                    FROM payment_refund_operation
                    WHERE tenant_id = ? AND payment_id = ?
                      AND refund_status IN ('REQUESTED', 'PENDING', 'SUCCEEDED', 'UNKNOWN')
                    """)) {
                statement.setString(1, tenant);
                statement.setObject(2, paymentId);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    alreadyCommitted = result.getBigDecimal("refunded");
                }
            }
            if (alreadyCommitted.add(command.amount()).compareTo(paymentAmount) > 0) {
                throw new PaymentOperationRejectedException("Requested refunds exceed the original payment amount");
            }

            UUID refundId = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO payment_refund_operation (
                        refund_id, tenant_id, payment_id, idempotency_key, request_fingerprint,
                        amount, currency, reason, refund_status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'REQUESTED')
                    """)) {
                statement.setObject(1, refundId);
                statement.setString(2, tenant);
                statement.setObject(3, paymentId);
                statement.setString(4, command.idempotencyKey());
                statement.setString(5, fingerprint);
                statement.setBigDecimal(6, command.amount());
                statement.setString(7, currency);
                statement.setString(8, command.reason());
                statement.executeUpdate();
            }
            insertOutbox(connection, tenant, "REFUND", refundId, "PAYMENT_REFUND_REQUESTED",
                    Map.of("refundId", refundId.toString(), "paymentId", paymentId.toString(),
                            "amount", command.amount().toPlainString(), "currency", currency));
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT updated_at FROM payment_refund_operation
                    WHERE tenant_id = ? AND refund_id = ?
                    """)) {
                statement.setString(1, tenant);
                statement.setObject(2, refundId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalStateException("Created refund could not be reloaded");
                    }
                    return new RefundOperation(tenant, refundId.toString(), paymentId.toString(),
                            command.amount(), currency, RefundStatus.REQUESTED,
                            result.getTimestamp("updated_at").toInstant());
                }
            }
        });
    }

    private Optional<PaymentObligation> findPayment(Connection connection, String tenantId, UUID paymentId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT payment_id, tenant_id, caller_reference, amount, currency, payment_status,
                       provider_reference, action_uri, updated_at
                FROM payment_obligation
                WHERE tenant_id = ? AND payment_id = ?
                """)) {
            statement.setString(1, tenantId);
            statement.setObject(2, paymentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PaymentObligation(
                        result.getString("tenant_id"),
                        result.getObject("payment_id", UUID.class).toString(),
                        result.getString("caller_reference"),
                        result.getBigDecimal("amount"),
                        result.getString("currency").trim(),
                        PaymentStatus.valueOf(result.getString("payment_status")),
                        result.getString("provider_reference"),
                        result.getString("action_uri"),
                        result.getTimestamp("updated_at").toInstant()));
            }
        }
    }

    private void insertOutbox(Connection connection, String tenantId, String aggregateType,
                              UUID aggregateId, String eventType, Map<String, String> payload)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO payment_operation_outbox (
                    event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, tenantId);
            statement.setString(3, aggregateType);
            statement.setObject(4, aggregateId);
            statement.setString(5, eventType);
            statement.setString(6, toJson(payload));
            statement.executeUpdate();
        }
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T value = work.execute(connection);
                connection.commit();
                return value;
            } catch (RuntimeException | SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                if (exception instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw persistenceFailure("execute payment transaction", (SQLException) exception);
            }
        } catch (SQLException exception) {
            throw persistenceFailure("open payment transaction", exception);
        }
    }

    private static String refundFingerprint(RequestPaymentRefund command) {
        String canonical = command.tenantId() + "\u0000" + command.paymentId() + "\u0000"
                + command.amount().stripTrailingZeros().toPlainString() + "\u0000" + command.reason();
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize payment metadata", exception);
        }
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(required(value, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static void ensurePersistableAmount(BigDecimal amount) {
        int fractionalDigits = Math.max(0, amount.stripTrailingZeros().scale());
        int integerDigits = Math.max(0, amount.precision() - amount.scale());
        if (fractionalDigits > 18 || integerDigits > 20) {
            throw new PaymentOperationRejectedException(
                    "Amount exceeds PostgreSQL payment precision (20 integer and 18 fractional digits)");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static IllegalStateException persistenceFailure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation, cause);
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}

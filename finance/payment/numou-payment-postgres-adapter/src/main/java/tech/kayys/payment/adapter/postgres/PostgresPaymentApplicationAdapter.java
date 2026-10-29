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
import tech.kayys.payment.api.VerifiedPaymentOutcomePort;
import tech.kayys.payment.api.RefundOperation;
import tech.kayys.payment.api.RefundStatus;
import tech.kayys.payment.api.VerifiedPaymentOutcome;
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
public class PostgresPaymentApplicationAdapter implements PaymentApplicationPort, VerifiedPaymentOutcomePort {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DataSource dataSource;
    private final PaymentTenantAuthorizationPort tenantAuthorization;

    @Inject
    public PostgresPaymentApplicationAdapter(DataSource dataSource,
                                             PaymentTenantAuthorizationPort tenantAuthorization) {
        this.dataSource = dataSource;
        this.tenantAuthorization = tenantAuthorization;
    }

    @Override
    public PaymentObligation createOrGet(CreatePaymentObligation command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        tenantAuthorization.authorizeTenant(command.tenantId());
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
    public PaymentObligation applyVerifiedOutcome(VerifiedPaymentOutcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        String tenant = required(outcome.tenantId(), "tenantId");
        tenantAuthorization.authorizeTenant(tenant);
        UUID paymentId = parseUuid(outcome.paymentId(), "paymentId");
        String fingerprint = providerOutcomeFingerprint(outcome);
        return inTransaction(connection -> {
            BigDecimal expectedAmount;
            String expectedCurrency;
            PaymentStatus currentStatus;
            String existingProviderReference;
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT amount, currency, payment_status, provider_reference
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
                    expectedAmount = result.getBigDecimal("amount");
                    expectedCurrency = result.getString("currency").trim();
                    currentStatus = PaymentStatus.valueOf(result.getString("payment_status"));
                    existingProviderReference = result.getString("provider_reference");
                }
            }

            if (expectedAmount.compareTo(outcome.amount()) != 0
                    || !expectedCurrency.equals(outcome.currency())) {
                throw new PaymentOperationRejectedException(
                        "Verified provider amount/currency does not match the payment obligation");
            }
            if (existingProviderReference != null
                    && !existingProviderReference.equals(outcome.providerReference())) {
                throw new PaymentOperationRejectedException(
                        "Provider reference does not match the payment obligation");
            }

            int inserted;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO payment_provider_event_inbox (
                        inbox_id, tenant_id, provider_name, provider_event_id, payment_id,
                        request_fingerprint, provider_occurred_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, provider_name, provider_event_id) DO NOTHING
                    """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, tenant);
                statement.setString(3, outcome.providerName());
                statement.setString(4, outcome.providerEventId());
                statement.setObject(5, paymentId);
                statement.setString(6, fingerprint);
                statement.setTimestamp(7, java.sql.Timestamp.from(outcome.occurredAt()));
                inserted = statement.executeUpdate();
            }
            if (inserted == 0) {
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT payment_id, request_fingerprint
                        FROM payment_provider_event_inbox
                        WHERE tenant_id = ? AND provider_name = ? AND provider_event_id = ?
                        """)) {
                    statement.setString(1, tenant);
                    statement.setString(2, outcome.providerName());
                    statement.setString(3, outcome.providerEventId());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException("Provider event conflict row disappeared");
                        }
                        if (!paymentId.equals(result.getObject("payment_id", UUID.class))
                                || !fingerprint.equals(result.getString("request_fingerprint"))) {
                            throw new IdempotencyConflictException(
                                    "Provider event ID was reused with a different payment outcome");
                        }
                    }
                }
                return findPayment(connection, tenant, paymentId)
                        .orElseThrow(() -> new IllegalStateException("Payment disappeared after event replay"));
            }

            PaymentStatus target = outcome.status();
            if (target == currentStatus) {
                return findPayment(connection, tenant, paymentId)
                        .orElseThrow(() -> new IllegalStateException("Payment disappeared after provider event"));
            }
            if (!isAllowedProviderTransition(currentStatus, target)) {
                throw new PaymentOperationRejectedException(
                        "Provider outcome transition is not allowed: " + currentStatus + " -> " + target);
            }

            boolean lateSuccess = target == PaymentStatus.SUCCEEDED
                    && (currentStatus == PaymentStatus.FAILED
                    || currentStatus == PaymentStatus.CANCELLED
                    || currentStatus == PaymentStatus.EXPIRED);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE payment_obligation
                    SET payment_status = ?, provider_reference = COALESCE(provider_reference, ?),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE tenant_id = ? AND payment_id = ?
                    """)) {
                statement.setString(1, target.name());
                statement.setString(2, outcome.providerReference());
                statement.setString(3, tenant);
                statement.setObject(4, paymentId);
                if (statement.executeUpdate() != 1) {
                    throw new IllegalStateException("Payment state update affected an unexpected row count");
                }
            }

            String eventType = lateSuccess
                    ? "PAYMENT_LATE_SUCCESS_REQUIRES_RECONCILIATION"
                    : "PAYMENT_STATUS_CHANGED:" + fingerprint.substring(0, 24);
            insertOutbox(connection, tenant, "PAYMENT", paymentId, eventType,
                    Map.of("paymentId", paymentId.toString(),
                            "providerName", outcome.providerName(),
                            "providerEventId", outcome.providerEventId(),
                            "providerReference", outcome.providerReference(),
                            "previousStatus", currentStatus.name(),
                            "status", target.name(),
                            "amount", outcome.amount().toPlainString(),
                            "currency", outcome.currency(),
                            "occurredAt", outcome.occurredAt().toString()));
            return findPayment(connection, tenant, paymentId)
                    .orElseThrow(() -> new IllegalStateException("Updated payment could not be reloaded"));
        });
    }

    private static boolean isAllowedProviderTransition(PaymentStatus current, PaymentStatus target) {
        if (target == PaymentStatus.PARTIALLY_REFUNDED || target == PaymentStatus.REFUNDED
                || target == PaymentStatus.CREATED) {
            return false;
        }
        return switch (current) {
            case CREATED -> target == PaymentStatus.REQUIRES_CUSTOMER_ACTION
                    || target == PaymentStatus.PENDING || target == PaymentStatus.SUCCEEDED
                    || target == PaymentStatus.FAILED || target == PaymentStatus.CANCELLED
                    || target == PaymentStatus.EXPIRED || target == PaymentStatus.UNKNOWN;
            case REQUIRES_CUSTOMER_ACTION -> target == PaymentStatus.PENDING
                    || target == PaymentStatus.SUCCEEDED || target == PaymentStatus.FAILED
                    || target == PaymentStatus.CANCELLED || target == PaymentStatus.EXPIRED
                    || target == PaymentStatus.UNKNOWN;
            case PENDING -> target == PaymentStatus.SUCCEEDED || target == PaymentStatus.FAILED
                    || target == PaymentStatus.CANCELLED || target == PaymentStatus.EXPIRED
                    || target == PaymentStatus.UNKNOWN;
            case UNKNOWN -> target == PaymentStatus.REQUIRES_CUSTOMER_ACTION
                    || target == PaymentStatus.PENDING || target == PaymentStatus.SUCCEEDED
                    || target == PaymentStatus.FAILED || target == PaymentStatus.CANCELLED
                    || target == PaymentStatus.EXPIRED;
            case FAILED, CANCELLED, EXPIRED -> target == PaymentStatus.SUCCEEDED;
            case SUCCEEDED, PARTIALLY_REFUNDED, REFUNDED -> false;
        };
    }

    private static String providerOutcomeFingerprint(VerifiedPaymentOutcome outcome) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            updateDigest(digest, "numou-verified-payment-outcome-v1");
            updateDigest(digest, outcome.tenantId());
            updateDigest(digest, outcome.paymentId());
            updateDigest(digest, outcome.providerName());
            updateDigest(digest, outcome.providerEventId());
            updateDigest(digest, outcome.providerReference());
            updateDigest(digest, outcome.amount().stripTrailingZeros().toPlainString());
            updateDigest(digest, outcome.currency());
            updateDigest(digest, outcome.status().name());
            updateDigest(digest, outcome.occurredAt().toString());
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Override
    public Optional<PaymentObligation> find(String tenantId, String paymentId) {
        String tenant = required(tenantId, "tenantId");
        tenantAuthorization.authorizeTenant(tenant);
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
        tenantAuthorization.authorizeTenant(tenant);
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
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            updateDigest(digest, "numou-refund-request-v1");
            updateDigest(digest, command.tenantId());
            updateDigest(digest, command.paymentId());
            updateDigest(digest, command.amount().stripTrailingZeros().toPlainString());
            updateDigest(digest, command.reason());
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateDigest(java.security.MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
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

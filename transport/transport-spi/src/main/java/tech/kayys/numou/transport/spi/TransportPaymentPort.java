package tech.kayys.numou.transport.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral bridge to the public Payment capability contract. */
public interface TransportPaymentPort {
    PaymentSnapshot createOrGet(CreatePayment command);
    PaymentSnapshot find(String tenantId, String paymentId);
    RefundSnapshot requestRefund(RefundCommand command);

    record CreatePayment(String tenantId, String idempotencyKey, String callerReference,
                         BigDecimal amount, String currency, String description,
                         Map<String, String> metadata) {
        public CreatePayment {
            required(tenantId, "tenantId");
            required(idempotencyKey, "idempotencyKey");
            required(callerReference, "callerReference");
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
            currency = required(currency, "currency");
            Currency.getInstance(currency);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    record PaymentSnapshot(String tenantId, String paymentId, String callerReference,
                           BigDecimal amount, String currency, PaymentState status,
                           String actionUri, Instant updatedAt) {
        public PaymentSnapshot {
            required(tenantId, "tenantId");
            required(paymentId, "paymentId");
            required(callerReference, "callerReference");
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
            currency = required(currency, "currency");
            Currency.getInstance(currency);
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    record RefundCommand(String tenantId, String paymentId, String idempotencyKey,
                         BigDecimal amount, String reason) {
        public RefundCommand {
            required(tenantId, "tenantId");
            required(paymentId, "paymentId");
            required(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() <= 0) throw new IllegalArgumentException("refund amount must be positive");
            required(reason, "reason");
        }
    }

    record RefundSnapshot(String tenantId, String refundId, String paymentId,
                          BigDecimal amount, String currency, RefundState status, Instant updatedAt) {
        public RefundSnapshot {
            required(tenantId, "tenantId");
            required(refundId, "refundId");
            required(paymentId, "paymentId");
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() <= 0) throw new IllegalArgumentException("refund amount must be positive");
            currency = required(currency, "currency");
            Currency.getInstance(currency);
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    enum PaymentState {
        CREATED, REQUIRES_CUSTOMER_ACTION, PENDING, SUCCEEDED, FAILED, CANCELLED, EXPIRED, UNKNOWN
    }

    enum RefundState { REQUESTED, PENDING, SUCCEEDED, FAILED, UNKNOWN }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}

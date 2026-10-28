package tech.kayys.numou.transport.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral bridge to the public payment capability contract. */
public interface TransportPaymentPort {
    PaymentSnapshot createOrGet(CreatePayment command);
    PaymentSnapshot find(String tenantId, String paymentId);

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

    enum PaymentState {
        CREATED, REQUIRES_CUSTOMER_ACTION, PENDING, SUCCEEDED, FAILED, CANCELLED, EXPIRED, UNKNOWN
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}

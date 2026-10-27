package tech.kayys.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Public payment state snapshot. Provider-specific payloads and credentials
 * are intentionally excluded.
 */
public record PaymentObligation(
        String tenantId,
        String paymentId,
        String callerReference,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String providerReference,
        String actionUri,
        Instant updatedAt) {

    public PaymentObligation {
        tenantId = required(tenantId, "tenantId");
        paymentId = required(paymentId, "paymentId");
        callerReference = required(callerReference, "callerReference");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        currency = required(currency, "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an uppercase ISO-4217 code");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
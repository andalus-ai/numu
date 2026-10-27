package tech.kayys.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/** Snapshot of a refund request; accepted does not imply funds have settled. */
public record RefundOperation(
        String tenantId,
        String refundId,
        String paymentId,
        BigDecimal amount,
        String currency,
        RefundStatus status,
        Instant updatedAt) {

    public RefundOperation {
        tenantId = required(tenantId, "tenantId");
        refundId = required(refundId, "refundId");
        paymentId = required(paymentId, "paymentId");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("refund amount must be positive");
        }
        currency = required(currency, "currency");
        validateCurrency(currency);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static void validateCurrency(String currency) {
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an uppercase ISO-4217 code");
        }
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported ISO-4217 currency: " + currency, exception);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
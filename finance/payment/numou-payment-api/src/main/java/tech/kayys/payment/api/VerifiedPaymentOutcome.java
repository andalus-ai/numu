package tech.kayys.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * Provider outcome that has already passed provider-specific authentication and
 * signature verification. Never construct this command directly from an
 * untrusted HTTP callback or browser redirect.
 */
public record VerifiedPaymentOutcome(
        String tenantId,
        String paymentId,
        String providerName,
        String providerEventId,
        String providerReference,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant occurredAt) {

    public VerifiedPaymentOutcome {
        tenantId = required(tenantId, "tenantId");
        paymentId = required(paymentId, "paymentId");
        providerName = required(providerName, "providerName");
        providerEventId = required(providerEventId, "providerEventId");
        providerReference = required(providerReference, "providerReference");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        currency = required(currency, "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an uppercase ISO-4217 code");
        }
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported ISO-4217 currency: " + currency, exception);
        }
        Objects.requireNonNull(status, "status");
        if (status == PaymentStatus.PARTIALLY_REFUNDED || status == PaymentStatus.REFUNDED
                || status == PaymentStatus.CREATED) {
            throw new IllegalArgumentException("status is not a provider outcome: " + status);
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

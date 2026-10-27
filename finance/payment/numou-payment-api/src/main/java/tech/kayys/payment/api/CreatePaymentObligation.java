package tech.kayys.payment.api;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/**
 * Framework-neutral command to create a payment obligation.
 *
 * @param tenantId tenant that owns the payment
 * @param idempotencyKey caller-generated key, unique within tenant and operation
 * @param callerReference stable reference to the caller's business object
 * @param amount positive payment amount
 * @param currency explicit ISO-4217 currency code; never inferred by the API
 * @param description optional human-readable description
 * @param metadata non-sensitive correlation metadata; never store secrets here
 */
public record CreatePaymentObligation(
        String tenantId,
        String idempotencyKey,
        String callerReference,
        BigDecimal amount,
        String currency,
        String description,
        Map<String, String> metadata) {

    public CreatePaymentObligation {
        tenantId = required(tenantId, "tenantId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        callerReference = required(callerReference, "callerReference");
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
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
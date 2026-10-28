package tech.kayys.numou.affiliate.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * Source-adapter verified commission base tied to the immutable attribution decision.
 * The affiliate engine never accepts client-submitted amounts or payment-state assertions.
 */
public record CommissionConversion(
        String tenantId,
        String sourceType,
        String sourceId,
        String eventId,
        AttributionDecision attributionDecision,
        BigDecimal eligibleAmount,
        String currency,
        Instant occurredAt) {

    public CommissionConversion {
        tenantId = required(tenantId, "tenantId");
        sourceType = required(sourceType, "sourceType");
        sourceId = required(sourceId, "sourceId");
        eventId = required(eventId, "eventId");
        Objects.requireNonNull(attributionDecision, "attributionDecision is required");
        Objects.requireNonNull(eligibleAmount, "eligibleAmount is required");
        if (eligibleAmount.signum() <= 0) throw new IllegalArgumentException("eligibleAmount must be positive");
        currency = required(currency, "currency").toUpperCase(java.util.Locale.ROOT);
        Currency.getInstance(currency);
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (!tenantId.equals(attributionDecision.tenantId())
                || !sourceType.equals(attributionDecision.sourceType())
                || !sourceId.equals(attributionDecision.sourceId())
                || !eventId.equals(attributionDecision.conversionEventId())) {
            throw new IllegalArgumentException("conversion correlation must match attribution decision");
        }
        if (attributionDecision.outcome() != AttributionOutcome.ATTRIBUTED) {
            throw new IllegalArgumentException("commission requires an attributed conversion");
        }
        if (occurredAt.isBefore(attributionDecision.conversionAt())) {
            throw new IllegalArgumentException("conversion cannot predate attribution conversion time");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}

package tech.kayys.numou.affiliate.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** Immutable, versioned commission policy. A policy change creates a new version, never edits history. */
public record CommissionPlan(
        UUID id,
        String tenantId,
        UUID programId,
        long version,
        CommissionType type,
        String currency,
        BigDecimal value,
        Instant effectiveFrom,
        Instant createdAt) {

    public CommissionPlan {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(programId, "programId is required");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        Objects.requireNonNull(type, "type is required");
        currency = required(currency, "currency").toUpperCase(java.util.Locale.ROOT);
        Currency.getInstance(currency);
        Objects.requireNonNull(value, "value is required");
        if (value.signum() <= 0) throw new IllegalArgumentException("value must be positive");
        if (type == CommissionType.PERCENTAGE && value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("percentage cannot exceed 100");
        }
        if (type == CommissionType.FIXED && value.scale() > 8) {
            throw new IllegalArgumentException("fixed value has excessive precision");
        }
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (effectiveFrom.isBefore(createdAt)) {
            throw new IllegalArgumentException("effectiveFrom cannot precede plan creation");
        }
    }

    public boolean isEffectiveAt(Instant at) {
        Objects.requireNonNull(at, "at is required");
        return !at.isBefore(effectiveFrom);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}

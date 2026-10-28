package tech.kayys.numou.affiliate.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/** Immutable calculation snapshot. Reversals and adjustments are separate linked entries. */
public record CommissionEntry(
        UUID id,
        String tenantId,
        UUID programId,
        UUID partnerId,
        UUID attributionDecisionId,
        String sourceType,
        String sourceId,
        String conversionEventId,
        UUID planId,
        long planVersion,
        String currency,
        BigDecimal eligibleAmount,
        BigDecimal commissionAmount,
        CommissionEntryType type,
        CommissionStatus status,
        UUID originalEntryId,
        String idempotencyKey,
        Instant createdAt) {

    public CommissionEntry {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(programId, "programId is required");
        Objects.requireNonNull(partnerId, "partnerId is required");
        Objects.requireNonNull(attributionDecisionId, "attributionDecisionId is required");
        sourceType = required(sourceType, "sourceType");
        sourceId = required(sourceId, "sourceId");
        conversionEventId = required(conversionEventId, "conversionEventId");
        Objects.requireNonNull(planId, "planId is required");
        if (planVersion < 1) throw new IllegalArgumentException("planVersion must be positive");
        currency = required(currency, "currency").toUpperCase(java.util.Locale.ROOT);
        Currency.getInstance(currency);
        Objects.requireNonNull(eligibleAmount, "eligibleAmount is required");
        Objects.requireNonNull(commissionAmount, "commissionAmount is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(status, "status is required");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (eligibleAmount.signum() <= 0) throw new IllegalArgumentException("eligibleAmount must be positive");
        if (commissionAmount.signum() == 0) throw new IllegalArgumentException("commissionAmount cannot be zero");
        if (type == CommissionEntryType.EARNING && (originalEntryId != null || commissionAmount.signum() <= 0)) {
            throw new IllegalArgumentException("earning must be positive and cannot reference an original entry");
        }
        if (type == CommissionEntryType.REVERSAL
                && (originalEntryId == null || commissionAmount.signum() >= 0)) {
            throw new IllegalArgumentException("reversal must be negative and reference its original entry");
        }
        if (type == CommissionEntryType.ADJUSTMENT && originalEntryId == null) {
            throw new IllegalArgumentException("adjustment must reference its original entry");
        }
    }

    public CommissionEntry transitionTo(CommissionStatus next) {
        Objects.requireNonNull(next, "next status is required");
        boolean allowed = switch (status) {
            case PENDING -> next == CommissionStatus.APPROVED || next == CommissionStatus.HELD
                    || next == CommissionStatus.REVERSED;
            case HELD -> next == CommissionStatus.PENDING || next == CommissionStatus.APPROVED
                    || next == CommissionStatus.REVERSED;
            case APPROVED -> next == CommissionStatus.HELD || next == CommissionStatus.REVERSED;
            case REVERSED -> false;
        };
        if (!allowed) throw new IllegalStateException("Invalid commission transition: " + status + " -> " + next);
        return new CommissionEntry(id, tenantId, programId, partnerId, attributionDecisionId, sourceType,
                sourceId, conversionEventId, planId, planVersion, currency, eligibleAmount, commissionAmount,
                type, next, originalEntryId, idempotencyKey, createdAt);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}

package tech.kayys.numou.affiliate.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable outbox payload for posting an approved commission liability or reversal to Finance.
 * A positive amount represents an earning; a negative amount represents an approved reversal.
 */
public record CommissionFinancePosting(
        UUID id,
        String tenantId,
        UUID commissionEntryId,
        UUID programId,
        UUID partnerId,
        String sourceType,
        String sourceId,
        String conversionEventId,
        String currency,
        BigDecimal amount,
        CommissionEntryType entryType,
        String idempotencyKey,
        String correlationId,
        CommissionFinancePostingStatus status,
        int attempts,
        String externalReference,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {

    public CommissionFinancePosting {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(commissionEntryId, "commissionEntryId is required");
        Objects.requireNonNull(programId, "programId is required");
        Objects.requireNonNull(partnerId, "partnerId is required");
        sourceType = required(sourceType, "sourceType");
        sourceId = required(sourceId, "sourceId");
        conversionEventId = required(conversionEventId, "conversionEventId");
        currency = required(currency, "currency").toUpperCase(java.util.Locale.ROOT);
        Currency.getInstance(currency);
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() == 0) throw new IllegalArgumentException("amount cannot be zero");
        Objects.requireNonNull(entryType, "entryType is required");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        correlationId = required(correlationId, "correlationId");
        Objects.requireNonNull(status, "status is required");
        if (attempts < 0) throw new IllegalArgumentException("attempts cannot be negative");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        if (status == CommissionFinancePostingStatus.POSTED && (externalReference == null || externalReference.isBlank())) {
            throw new IllegalArgumentException("posted record requires externalReference");
        }
        if (entryType == CommissionEntryType.EARNING && amount.signum() <= 0) {
            throw new IllegalArgumentException("earning posting amount must be positive");
        }
        if (entryType == CommissionEntryType.REVERSAL && amount.signum() >= 0) {
            throw new IllegalArgumentException("reversal posting amount must be negative");
        }
    }

    public CommissionFinancePosting posted(String reference, Instant at) {
        return new CommissionFinancePosting(id, tenantId, commissionEntryId, programId, partnerId, sourceType,
                sourceId, conversionEventId, currency, amount, entryType, idempotencyKey, correlationId,
                CommissionFinancePostingStatus.POSTED, attempts + 1, required(reference, "externalReference"),
                null, createdAt, at);
    }

    public CommissionFinancePosting retryableFailure(String error, Instant at) {
        return new CommissionFinancePosting(id, tenantId, commissionEntryId, programId, partnerId, sourceType,
                sourceId, conversionEventId, currency, amount, entryType, idempotencyKey, correlationId,
                CommissionFinancePostingStatus.RETRYABLE_FAILURE, attempts + 1, externalReference,
                safeError(error), createdAt, at);
    }

    public CommissionFinancePosting reconciliationRequired(String reason, Instant at) {
        return new CommissionFinancePosting(id, tenantId, commissionEntryId, programId, partnerId, sourceType,
                sourceId, conversionEventId, currency, amount, entryType, idempotencyKey, correlationId,
                CommissionFinancePostingStatus.RECONCILIATION_REQUIRED, attempts + 1, externalReference,
                safeError(reason), createdAt, at);
    }

    private static String safeError(String error) {
        String value = required(error, "error");
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}

package tech.kayys.numou.affiliate.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable payout intent for an already-posted commission liability.
 * Contains only an opaque destination reference, never bank or wallet credentials.
 */
public record PartnerPayout(
        UUID id,
        String tenantId,
        UUID partnerId,
        UUID commissionEntryId,
        String destinationId,
        String currency,
        BigDecimal amount,
        String idempotencyKey,
        String correlationId,
        PartnerPayoutStatus status,
        int attempts,
        String providerReference,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {

    public PartnerPayout {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(partnerId, "partnerId is required");
        Objects.requireNonNull(commissionEntryId, "commissionEntryId is required");
        destinationId = required(destinationId, "destinationId");
        currency = required(currency, "currency").toUpperCase(Locale.ROOT);
        Currency.getInstance(currency);
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) throw new IllegalArgumentException("payout amount must be positive");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        correlationId = required(correlationId, "correlationId");
        Objects.requireNonNull(status, "status is required");
        if (attempts < 0) throw new IllegalArgumentException("attempts cannot be negative");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        if (status == PartnerPayoutStatus.PAID && (providerReference == null || providerReference.isBlank())) {
            throw new IllegalArgumentException("paid payout requires providerReference");
        }
    }

    public PartnerPayout submitted(String reference, Instant at) {
        return transition(PartnerPayoutStatus.SUBMITTED, reference, null, at, true);
    }

    public PartnerPayout paid(String reference, Instant at) {
        return transition(PartnerPayoutStatus.PAID, required(reference, "providerReference"), null, at, true);
    }

    public PartnerPayout held(String reason, Instant at) {
        return transition(PartnerPayoutStatus.HELD, providerReference, safeError(reason), at, false);
    }

    public PartnerPayout retryableFailure(String reason, Instant at) {
        return transition(PartnerPayoutStatus.RETRYABLE_FAILURE, providerReference, safeError(reason), at, true);
    }

    public PartnerPayout reconciliationRequired(String reason, Instant at) {
        return transition(PartnerPayoutStatus.RECONCILIATION_REQUIRED, providerReference, safeError(reason), at, true);
    }

    public PartnerPayout returned(String reference, String reason, Instant at) {
        return transition(PartnerPayoutStatus.RETURNED,
                reference == null || reference.isBlank() ? providerReference : reference,
                safeError(reason), at, true);
    }

    private PartnerPayout transition(PartnerPayoutStatus next, String reference, String error, Instant at,
                                     boolean incrementAttempt) {
        Objects.requireNonNull(at, "transition time is required");
        boolean allowed = switch (status) {
            case REQUESTED -> next == PartnerPayoutStatus.SUBMITTED || next == PartnerPayoutStatus.PAID || next == PartnerPayoutStatus.HELD
                    || next == PartnerPayoutStatus.RETRYABLE_FAILURE
                    || next == PartnerPayoutStatus.RECONCILIATION_REQUIRED;
            case SUBMITTED -> next == PartnerPayoutStatus.PAID || next == PartnerPayoutStatus.RETURNED
                    || next == PartnerPayoutStatus.RECONCILIATION_REQUIRED
                    || next == PartnerPayoutStatus.RETRYABLE_FAILURE;
            case RETRYABLE_FAILURE -> next == PartnerPayoutStatus.SUBMITTED || next == PartnerPayoutStatus.HELD
                    || next == PartnerPayoutStatus.RECONCILIATION_REQUIRED;
            case RECONCILIATION_REQUIRED -> next == PartnerPayoutStatus.PAID
                    || next == PartnerPayoutStatus.RETURNED || next == PartnerPayoutStatus.RETRYABLE_FAILURE
                    || next == PartnerPayoutStatus.HELD;
            case PAID, HELD, RETURNED -> false;
        };
        if (!allowed) throw new IllegalStateException("Invalid payout transition: " + status + " -> " + next);
        return new PartnerPayout(id, tenantId, partnerId, commissionEntryId, destinationId, currency, amount,
                idempotencyKey, correlationId, next, attempts + (incrementAttempt ? 1 : 0), reference, error,
                createdAt, at);
    }

    private static String safeError(String error) {
        String value = required(error, "reason");
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}

package tech.kayys.numou.affiliate.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable approval envelope for a single-currency set of partner payout intents.
 * Actual transfer execution remains owned by Wallet/Payment.
 */
public record SettlementBatch(
        UUID id,
        String tenantId,
        String currency,
        List<SettlementBatchItem> items,
        BigDecimal totalAmount,
        String idempotencyKey,
        SettlementBatchStatus status,
        String createdBy,
        String approvedBy,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public SettlementBatch {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        currency = required(currency, "currency").toUpperCase(Locale.ROOT);
        Currency.getInstance(currency);
        items = List.copyOf(Objects.requireNonNull(items, "items is required"));
        if (items.isEmpty()) throw new IllegalArgumentException("settlement batch must contain at least one payout");
        Set<UUID> payoutIds = new HashSet<>();
        BigDecimal computed = BigDecimal.ZERO;
        for (SettlementBatchItem item : items) {
            Objects.requireNonNull(item, "settlement item cannot be null");
            if (!currency.equals(item.currency())) throw new IllegalArgumentException("all settlement items must use batch currency");
            if (!payoutIds.add(item.payoutId())) throw new IllegalArgumentException("duplicate payout in settlement batch");
            computed = computed.add(item.amount());
        }
        Objects.requireNonNull(totalAmount, "totalAmount is required");
        if (computed.compareTo(totalAmount) != 0) throw new IllegalArgumentException("totalAmount must equal item sum");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(status, "status is required");
        createdBy = required(createdBy, "createdBy");
        if (approvedBy != null && approvedBy.isBlank()) approvedBy = null;
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        if ((status == SettlementBatchStatus.APPROVED || status == SettlementBatchStatus.SUBMITTING
                || status == SettlementBatchStatus.COMPLETED || status == SettlementBatchStatus.PARTIALLY_COMPLETED)
                && (approvedBy == null || approvedBy.isBlank())) {
            throw new IllegalArgumentException("approved settlement batch requires approvedBy");
        }
    }

    public SettlementBatch approve(String actorId, Instant at) {
        if (status != SettlementBatchStatus.READY_FOR_APPROVAL) {
            throw new IllegalStateException("Only a batch awaiting approval can be approved");
        }
        String actor = required(actorId, "actorId");
        if (createdBy.equals(actor)) throw new IllegalArgumentException("maker-checker policy forbids self-approval");
        return copy(SettlementBatchStatus.APPROVED, actor, at);
    }

    public SettlementBatch beginSubmission(Instant at) {
        if (status != SettlementBatchStatus.APPROVED) {
            throw new IllegalStateException("Only an approved batch can be submitted");
        }
        return copy(SettlementBatchStatus.SUBMITTING, approvedBy, at);
    }

    public SettlementBatch completed(Instant at) {
        if (status != SettlementBatchStatus.SUBMITTING) throw new IllegalStateException("Batch is not submitting");
        return copy(SettlementBatchStatus.COMPLETED, approvedBy, at);
    }

    public SettlementBatch partiallyCompleted(Instant at) {
        if (status != SettlementBatchStatus.SUBMITTING) throw new IllegalStateException("Batch is not submitting");
        return copy(SettlementBatchStatus.PARTIALLY_COMPLETED, approvedBy, at);
    }

    public SettlementBatch reconciliationRequired(Instant at) {
        if (status != SettlementBatchStatus.SUBMITTING
                && status != SettlementBatchStatus.PARTIALLY_COMPLETED) {
            throw new IllegalStateException("Batch is not awaiting transfer reconciliation");
        }
        return copy(SettlementBatchStatus.RECONCILIATION_REQUIRED, approvedBy, at);
    }

    public SettlementBatch cancel(String actorId, Instant at) {
        if (status != SettlementBatchStatus.READY_FOR_APPROVAL) {
            throw new IllegalStateException("Only an unapproved batch can be cancelled");
        }
        required(actorId, "actorId");
        return copy(SettlementBatchStatus.CANCELLED, null, at);
    }

    private SettlementBatch copy(SettlementBatchStatus next, String approver, Instant at) {
        Objects.requireNonNull(at, "transition time is required");
        if (at.isBefore(updatedAt)) throw new IllegalArgumentException("transition time cannot move backwards");
        return new SettlementBatch(id, tenantId, currency, items, totalAmount, idempotencyKey, next,
                createdBy, approver, createdAt, at, version + 1);
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}

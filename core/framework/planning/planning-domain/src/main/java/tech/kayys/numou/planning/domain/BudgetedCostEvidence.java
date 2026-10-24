package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Approved budget-at-completion evidence for one tenant-scoped WBS node and baseline.
 * Amounts use the ISO-4217 currency code supplied by the originating finance adapter.
 */
public record BudgetedCostEvidence(
        UUID tenantId,
        UUID structureId,
        UUID nodeId,
        UUID baselineId,
        String currencyCode,
        BigDecimal budgetAtCompletion,
        String sourceReference,
        CostEvidenceStatus status,
        Instant approvedAt) {

    public BudgetedCostEvidence {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(nodeId, "WBS node id cannot be null");
        Objects.requireNonNull(baselineId, "Baseline id cannot be null");
        currencyCode = requireText(currencyCode, "Currency code");
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Currency code must be a supported ISO-4217 code", exception);
        }
        Objects.requireNonNull(budgetAtCompletion, "Budget at completion cannot be null");
        if (budgetAtCompletion.signum() < 0) {
            throw new IllegalArgumentException("Budget at completion cannot be negative");
        }
        sourceReference = requireText(sourceReference, "Source reference");
        Objects.requireNonNull(status, "Evidence status cannot be null");
        if (status == CostEvidenceStatus.APPROVED && approvedAt == null) {
            throw new IllegalArgumentException("Approved budget evidence requires approvedAt");
        }
        if (status != CostEvidenceStatus.APPROVED && approvedAt != null) {
            throw new IllegalArgumentException("Only approved budget evidence may have approvedAt");
        }
    }

    public boolean isApproved() {
        return status == CostEvidenceStatus.APPROVED;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }
}

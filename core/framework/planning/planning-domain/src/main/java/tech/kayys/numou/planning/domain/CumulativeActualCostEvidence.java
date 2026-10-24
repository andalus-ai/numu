package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Approved cumulative actual cost for one tenant-scoped WBS node at a reporting date.
 * A finance/ERP adapter is responsible for reconciling its underlying transactions.
 */
public record CumulativeActualCostEvidence(
        UUID tenantId,
        UUID structureId,
        UUID nodeId,
        LocalDate asOfDate,
        String currencyCode,
        BigDecimal cumulativeActualCost,
        String sourceReference,
        CostEvidenceStatus status,
        Instant approvedAt) {

    public CumulativeActualCostEvidence {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(nodeId, "WBS node id cannot be null");
        Objects.requireNonNull(asOfDate, "Actual-cost as-of date cannot be null");
        currencyCode = requireText(currencyCode, "Currency code");
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Currency code must be a supported ISO-4217 code", exception);
        }
        Objects.requireNonNull(cumulativeActualCost, "Cumulative actual cost cannot be null");
        if (cumulativeActualCost.signum() < 0) {
            throw new IllegalArgumentException("Cumulative actual cost cannot be negative");
        }
        sourceReference = requireText(sourceReference, "Source reference");
        Objects.requireNonNull(status, "Evidence status cannot be null");
        if (status == CostEvidenceStatus.APPROVED && approvedAt == null) {
            throw new IllegalArgumentException("Approved actual-cost evidence requires approvedAt");
        }
        if (status != CostEvidenceStatus.APPROVED && approvedAt != null) {
            throw new IllegalArgumentException("Only approved actual-cost evidence may have approvedAt");
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

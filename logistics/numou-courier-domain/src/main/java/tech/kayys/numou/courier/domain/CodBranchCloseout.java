package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * Immutable branch-level COD reconciliation snapshot.
 * It reports operational facts only; it does not post or mutate accounting ledger entries.
 */
public record CodBranchCloseout(
        TenantId tenantId, String branchCode, Currency currency, Instant fromInclusive, Instant toExclusive,
        int obligationCount, int collectedCount, int settledCount, int varianceCount,
        BigDecimal collectedTotal, BigDecimal settledTotal, BigDecimal varianceTotal,
        List<Line> lines) {

    public record Line(java.util.UUID obligationId, ShipmentId shipmentId, CodObligationStatus status,
                       BigDecimal collectedAmount, BigDecimal settledAmount, BigDecimal variance, Instant collectedAt) {
        public Line {
            Objects.requireNonNull(obligationId); Objects.requireNonNull(shipmentId); Objects.requireNonNull(status);
            Objects.requireNonNull(collectedAmount); Objects.requireNonNull(collectedAt);
        }
    }

    public CodBranchCloseout {
        Objects.requireNonNull(tenantId); branchCode = required(branchCode); Objects.requireNonNull(currency);
        Objects.requireNonNull(fromInclusive); Objects.requireNonNull(toExclusive);
        if (!toExclusive.isAfter(fromInclusive)) throw new IllegalArgumentException("toExclusive must be after fromInclusive");
        if (obligationCount < 0 || collectedCount < 0 || settledCount < 0 || varianceCount < 0)
            throw new IllegalArgumentException("counts must be non-negative");
        Objects.requireNonNull(collectedTotal); Objects.requireNonNull(settledTotal); Objects.requireNonNull(varianceTotal);
        lines = List.copyOf(Objects.requireNonNull(lines));
        if (obligationCount != lines.size()) throw new IllegalArgumentException("obligationCount must match lines");
    }

    public static CodBranchCloseout calculate(TenantId tenantId, String branchCode, Currency currency,
                                               Instant fromInclusive, Instant toExclusive,
                                               List<CodObligation> obligations) {
        Objects.requireNonNull(obligations);
        String branch = required(branchCode);
        BigDecimal collected = BigDecimal.ZERO, settled = BigDecimal.ZERO, variance = BigDecimal.ZERO;
        int collectedCount = 0, settledCount = 0, varianceCount = 0;
        List<Line> lines = new ArrayList<>();
        for (CodObligation cod : obligations) {
            Objects.requireNonNull(cod);
            if (!tenantId.equals(cod.tenantId())) throw new SecurityException("cross-tenant COD obligation in closeout");
            if (!currency.equals(cod.currency())) throw new IllegalArgumentException("mixed currencies require separate closeouts");
            if (!branch.equals(cod.collectionBranchCode())) continue;
            Instant collectedAt = cod.collectedAt();
            if (collectedAt == null || collectedAt.isBefore(fromInclusive) || !collectedAt.isBefore(toExclusive)) continue;
            if (cod.status() == CodObligationStatus.DUE) throw new IllegalStateException("DUE obligation cannot have collection timestamp");
            BigDecimal collectedAmount = cod.collectedAmount();
            BigDecimal settledAmount = cod.settledAmount() == null ? BigDecimal.ZERO : cod.settledAmount();
            BigDecimal lineVariance = cod.variance() == null ? collectedAmount : cod.variance();
            collected = collected.add(collectedAmount); settled = settled.add(settledAmount); variance = variance.add(lineVariance);
            collectedCount++;
            if (cod.status() == CodObligationStatus.SETTLED) settledCount++;
            if (cod.status() == CodObligationStatus.VARIANCE_RECORDED) varianceCount++;
            lines.add(new Line(cod.id(), cod.shipmentId(), cod.status(), collectedAmount, cod.settledAmount(), cod.variance(), collectedAt));
        }
        return new CodBranchCloseout(tenantId, branch, currency, fromInclusive, toExclusive,
                lines.size(), collectedCount, settledCount, varianceCount, collected, settled, variance, lines);
    }

    private static String required(String value) {
        Objects.requireNonNull(value, "branchCode");
        String result = value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException("branchCode must not be blank");
        return result;
    }
}

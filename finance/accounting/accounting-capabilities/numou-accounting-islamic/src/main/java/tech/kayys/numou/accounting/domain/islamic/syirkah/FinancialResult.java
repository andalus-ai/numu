package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Period result sourced from reconciled accounting records. Costs are supplied
 * as non-negative values; net result is revenue minus all cost categories.
 */
public record FinancialResult(
        LocalDate periodStart,
        LocalDate periodEnd,
        String currencyCode,
        int currencyScale,
        BigDecimal revenue,
        BigDecimal directCosts,
        BigDecimal operatingCosts,
        BigDecimal otherCosts
) {
    public FinancialResult {
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (periodEnd.isBefore(periodStart)) throw new IllegalArgumentException("periodEnd must not be before periodStart");
        currencyCode = SyirkahContractVersion.normalizeCurrency(currencyCode);
        SyirkahContractVersion.validateCurrencyScale(currencyScale);
        revenue = normalizeAmount(revenue, currencyScale, "revenue");
        directCosts = normalizeAmount(directCosts, currencyScale, "directCosts");
        operatingCosts = normalizeAmount(operatingCosts, currencyScale, "operatingCosts");
        otherCosts = normalizeAmount(otherCosts, currencyScale, "otherCosts");
    }

    public BigDecimal netResult() {
        return revenue.subtract(directCosts).subtract(operatingCosts).subtract(otherCosts).setScale(scale(), RoundingMode.UNNECESSARY);
    }

    public int scale() { return currencyScale; }

    private static BigDecimal normalizeAmount(BigDecimal value, int scale, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() < 0) throw new IllegalArgumentException(name + " must not be negative");
        try {
            return value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(name + " exceeds currency precision of " + scale + " decimal places", ex);
        }
    }
}

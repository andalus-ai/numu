package tech.kayys.numou.accounting.domain.islamic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.math.RoundingMode;
import java.util.Objects;

/** Auditable business-period result before distribution. */
public record SyirkahBusinessResult(
        String resultId,
        String contractId,
        int contractVersion,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalRevenue,
        BigDecimal directCosts,
        BigDecimal operatingCosts,
        BigDecimal otherCosts,
        String currencyCode,
        String evidenceReference) {

    public SyirkahBusinessResult {
        resultId = text(resultId, "resultId");
        contractId = text(contractId, "contractId");
        currencyCode = text(currencyCode, "currencyCode").toUpperCase(java.util.Locale.ROOT);
        evidenceReference = text(evidenceReference, "evidenceReference");
        if (contractVersion < 1) throw new IllegalArgumentException("contractVersion must be >= 1");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        if (periodEnd.isBefore(periodStart)) throw new IllegalArgumentException("periodEnd precedes periodStart");
        Currency currency = Currency.getInstance(currencyCode);
        int scale = currency.getDefaultFractionDigits();
        if (scale < 0) throw new IllegalArgumentException("currency must have a standard minor unit");
        totalRevenue = money(totalRevenue, "totalRevenue", scale);
        directCosts = money(directCosts, "directCosts", scale);
        operatingCosts = money(operatingCosts, "operatingCosts", scale);
        otherCosts = money(otherCosts, "otherCosts", scale);
    }

    public BigDecimal netResult() {
        return totalRevenue.subtract(directCosts).subtract(operatingCosts).subtract(otherCosts);
    }

    private static BigDecimal money(BigDecimal value, String name, int scale) {
        Objects.requireNonNull(value, name);
        if (value.signum() < 0) throw new IllegalArgumentException(name + " must not be negative");
        try {
            return value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(name + " exceeds the currency's minor-unit precision", ex);
        }
    }

    private static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}

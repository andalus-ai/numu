package tech.kayys.syirkah.accounting.domain.islamic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Result of AAOIFI FAS 9 corporate Zakat determination and calculation.
 */
public record ZakatCalculation(
        BigDecimal zakatBase,
        BigDecimal nisabThreshold,
        boolean nisabMet,
        BigDecimal zakatRate,
        BigDecimal zakatPayable
) {
    public ZakatCalculation {
        Objects.requireNonNull(zakatBase, "zakatBase must not be null");
        Objects.requireNonNull(nisabThreshold, "nisabThreshold must not be null");
        Objects.requireNonNull(zakatRate, "zakatRate must not be null");
        Objects.requireNonNull(zakatPayable, "zakatPayable must not be null");
    }

    public static ZakatCalculation calculate(BigDecimal currentAssets, BigDecimal shortTermLiabilities, BigDecimal nisabThreshold, boolean isSolarCalendar) {
        BigDecimal base = currentAssets.subtract(shortTermLiabilities).max(BigDecimal.ZERO);
        boolean met = base.compareTo(nisabThreshold) >= 0;
        // AAOIFI: 2.5% for lunar (hijri) year, 2.5775% for solar (gregorian) year
        BigDecimal rate = isSolarCalendar ? new BigDecimal("0.025775") : new BigDecimal("0.025000");
        BigDecimal payable = met ? base.multiply(rate).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        return new ZakatCalculation(base, nisabThreshold, met, rate, payable);
    }
}

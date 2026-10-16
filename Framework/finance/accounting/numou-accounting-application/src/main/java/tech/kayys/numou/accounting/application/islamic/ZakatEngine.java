package tech.kayys.syirkah.accounting.application.islamic;

import tech.kayys.syirkah.accounting.domain.islamic.ZakatCalculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calculates corporate Zakat compliance under AAOIFI FAS 9 using Net Working Capital and Net Asset methods.
 */
public final class ZakatEngine {

    // Standard Gold Nisab is 85 grams of 24k gold
    public static final BigDecimal GOLD_NISAB_GRAMS = new BigDecimal("85.0");

    /**
     * Computes the monetary nisab threshold from current market gold price per gram.
     */
    public BigDecimal calculateNisabThreshold(BigDecimal goldPricePerGram) {
        Objects.requireNonNull(goldPricePerGram, "goldPricePerGram must not be null");
        return GOLD_NISAB_GRAMS.multiply(goldPricePerGram).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Executes corporate Zakat calculation under AAOIFI FAS 9.
     */
    public ZakatCalculation calculateZakat(BigDecimal currentAssets, BigDecimal shortTermLiabilities, BigDecimal goldPricePerGram, boolean isSolarCalendar) {
        BigDecimal nisab = calculateNisabThreshold(goldPricePerGram);
        return ZakatCalculation.calculate(currentAssets, shortTermLiabilities, nisab, isSolarCalendar);
    }
}

package tech.kayys.syirkah.accounting.consolidation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Currency translation engine applying IAS 21 / PSAK 10 rules.
 * Translates Balance Sheet at closing rate, P&L at average rate, and computes CTA.
 */
public final class TranslationEngine {

    public record TranslationResult(
            BigDecimal translatedAmount,
            BigDecimal ctaAmount
    ) {}

    /**
     * Translates a monetary amount from functional currency to group presentation currency.
     */
    public BigDecimal translate(BigDecimal amount, BigDecimal rate) {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Calculates Cumulative Translation Adjustment (CTA) on opening net assets.
     * CTA = Net Assets * (Closing Rate - Opening/Historical Rate)
     */
    public BigDecimal calculateCta(BigDecimal netAssets, BigDecimal openingRate, BigDecimal closingRate) {
        Objects.requireNonNull(netAssets, "netAssets must not be null");
        Objects.requireNonNull(openingRate, "openingRate must not be null");
        Objects.requireNonNull(closingRate, "closingRate must not be null");
        BigDecimal rateDelta = closingRate.subtract(openingRate);
        return netAssets.multiply(rateDelta).setScale(4, RoundingMode.HALF_UP);
    }
}

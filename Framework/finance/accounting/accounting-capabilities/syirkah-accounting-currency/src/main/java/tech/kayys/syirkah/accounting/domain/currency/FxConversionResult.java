package tech.kayys.syirkah.accounting.domain.currency;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Result of foreign exchange conversion.
 */
public record FxConversionResult(
        Money sourceAmount,
        Money targetAmount,
        BigDecimal appliedRate,
        ExchangeRateType rateType,
        Instant effectiveAt,
        String rateReference
) {
    public FxConversionResult {
        Objects.requireNonNull(sourceAmount, "sourceAmount cannot be null");
        Objects.requireNonNull(targetAmount, "targetAmount cannot be null");
        Objects.requireNonNull(appliedRate, "appliedRate cannot be null");
        Objects.requireNonNull(rateType, "rateType cannot be null");
        Objects.requireNonNull(effectiveAt, "effectiveAt cannot be null");

        if (appliedRate.signum() <= 0) {
            throw new IllegalArgumentException("Applied rate must be positive: " + appliedRate);
        }

        if (sourceAmount.currency().equals(targetAmount.currency())) {
            throw new IllegalArgumentException("FX conversion result must represent different currencies");
        }
    }
}

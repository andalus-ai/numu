package tech.kayys.numou.accounting.domain.currency;

import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.foundation.domain.valueobject.MonetaryRoundingPolicy;

import java.time.Instant;
import java.util.Objects;

/**
 * Request to perform currency conversion.
 */
public record FxConversionRequest(
        Money sourceAmount,
        Currency targetCurrency,
        ExchangeRateType rateType,
        Instant effectiveAt,
        MonetaryRoundingPolicy roundingPolicy
) {
    public FxConversionRequest {
        Objects.requireNonNull(sourceAmount, "sourceAmount cannot be null");
        Objects.requireNonNull(targetCurrency, "targetCurrency cannot be null");
        Objects.requireNonNull(rateType, "rateType cannot be null");
        Objects.requireNonNull(effectiveAt, "effectiveAt cannot be null");
        roundingPolicy = roundingPolicy == null ? MonetaryRoundingPolicy.STANDARD_TWO_DECIMALS : roundingPolicy;
    }

    public FxConversionRequest(Money sourceAmount, Currency targetCurrency, ExchangeRateType rateType, Instant effectiveAt) {
        this(sourceAmount, targetCurrency, rateType, effectiveAt, MonetaryRoundingPolicy.STANDARD_TWO_DECIMALS);
    }
}

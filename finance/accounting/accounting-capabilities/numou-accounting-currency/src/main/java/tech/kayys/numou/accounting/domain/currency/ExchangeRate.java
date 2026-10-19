package tech.kayys.numou.accounting.domain.currency;

import tech.kayys.numou.foundation.domain.valueobject.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable governed exchange rate record.
 * Direction: quoteAmount = baseAmount * rate.
 */
public record ExchangeRate(
        String id,
        Currency sourceCurrency,
        Currency targetCurrency,
        BigDecimal rate,
        ExchangeRateType rateType,
        ExchangeRateStatus status,
        Instant effectiveAt,
        String sourceProvider
) {
    public ExchangeRate {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(sourceCurrency, "sourceCurrency cannot be null");
        Objects.requireNonNull(targetCurrency, "targetCurrency cannot be null");
        Objects.requireNonNull(rate, "rate cannot be null");
        Objects.requireNonNull(rateType, "rateType cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(effectiveAt, "effectiveAt cannot be null");

        id = id.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id cannot be blank");
        }

        if (sourceCurrency.equals(targetCurrency)) {
            throw new IllegalArgumentException("Source and target currencies must be distinct");
        }

        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive: " + rate);
        }
    }
}

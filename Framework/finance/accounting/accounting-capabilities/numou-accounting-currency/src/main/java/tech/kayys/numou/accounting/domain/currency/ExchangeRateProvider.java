package tech.kayys.syirkah.accounting.domain.currency;

import tech.kayys.syirkah.foundation.domain.valueobject.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * SPI for resolving foreign exchange conversion rates.
 */
public interface ExchangeRateProvider {
    /**
     * Resolves exchange rate from source currency to target currency at given date.
     */
    Optional<BigDecimal> getRate(Currency source, Currency target, LocalDate date);

    /**
     * Resolves current spot exchange rate.
     */
    default Optional<BigDecimal> getSpotRate(Currency source, Currency target) {
        return getRate(source, target, LocalDate.now());
    }
}

package tech.kayys.sy.currency.core.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import tech.kayys.sy.currency.core.domain.ExchangeRate;

public record ExchangeRateDto(
    UUID id,
    String fromCurrencyCode,
    String toCurrencyCode,
    BigDecimal rate,
    LocalDate effectiveDate,
    LocalDate expiryDate,
    boolean active,
    Instant createdAt
) {
    public static ExchangeRateDto from(ExchangeRate exchangeRate) {
        return new ExchangeRateDto(
            exchangeRate.id,
            exchangeRate.fromCurrency.code,
            exchangeRate.toCurrency.code,
            exchangeRate.rate,
            exchangeRate.effectiveDate,
            exchangeRate.expiryDate,
            exchangeRate.active,
            exchangeRate.createdAt
        );
    }
}

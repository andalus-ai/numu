package tech.kayys.sy.currency.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CurrencyConversionResult(
    String fromCurrency,
    String toCurrency,
    BigDecimal originalAmount,
    BigDecimal convertedAmount,
    BigDecimal exchangeRate,
    LocalDate exchangeDate
) {}
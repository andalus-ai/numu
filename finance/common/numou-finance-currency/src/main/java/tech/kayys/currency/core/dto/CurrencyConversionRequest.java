package tech.kayys.sy.currency.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CurrencyConversionRequest(
    String fromCurrency,
    String toCurrency,
    BigDecimal amount,
    LocalDate exchangeDate
) {}

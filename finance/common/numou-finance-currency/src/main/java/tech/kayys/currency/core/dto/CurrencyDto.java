package tech.kayys.sy.currency.core.dto;

import java.time.Instant;
import java.util.UUID;

import tech.kayys.sy.currency.core.domain.Currency;

public record CurrencyDto(
    UUID id,
    String code,
    String name,
    String symbol,
    int decimalPlaces,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static CurrencyDto from(Currency currency) {
        return new CurrencyDto(
            currency.id,
            currency.code,
            currency.name,
            currency.symbol,
            currency.decimalPlaces,
            currency.active,
            currency.createdAt,
            currency.updatedAt
        );
    }
}

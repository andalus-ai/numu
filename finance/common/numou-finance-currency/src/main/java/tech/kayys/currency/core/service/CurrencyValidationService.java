package tech.kayys.sy.currency.core.service;

import java.util.Currency;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.sy.currency.core.dto.CurrencyCreateDto;
import tech.kayys.sy.currency.exception.BusinessException;

@ApplicationScoped
public class CurrencyValidationService {

    private static final Set<Currency> ISO_CURRENCIES = 
        Currency.getAvailableCurrencies();

    public boolean isValidIsoCurrency(String code) {
        try {
            Currency currency = Currency.getInstance(code);
            return ISO_CURRENCIES.contains(currency);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Optional<Currency> getJavaCurrency(String code) {
        try {
            return Optional.of(Currency.getInstance(code));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public void validateCurrencyCreation(CurrencyCreateDto dto) {
        // For ISO currencies, validate against java.util.Currency
        if (isValidIsoCurrency(dto.code)) {
            Currency jdkCurrency = Currency.getInstance(dto.code);
            int expectedDecimals = jdkCurrency.getDefaultFractionDigits();
            
            if (dto.decimalPlaces != expectedDecimals) {
                throw new BusinessException(
                    String.format("ISO currency %s should have %d decimal places, but got %d", 
                        dto.code, expectedDecimals, dto.decimalPlaces));
            }
        }
        // For custom currencies (like "PTS", "CRED"), use the provided decimal places
    }
}
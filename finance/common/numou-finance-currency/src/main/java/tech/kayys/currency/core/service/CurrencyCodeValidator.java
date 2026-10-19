package tech.kayys.sy.currency.core.service;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// First, define the annotation

// Then the validator implementation
@ApplicationScoped
public class CurrencyCodeValidator implements ConstraintValidator<ValidCurrencyCode, String> {
    
    private static final Set<String> COMMON_CURRENCY_CODES = Set.of(
        "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "HKD", "SGD",
        "SEK", "NOK", "DKK", "NZD", "MXN", "INR", "BRL", "RUB", "ZAR", "TRY",
        "KRW", "IDR", "MYR", "PHP", "THB", "VND", "AED", "SAR", "QAR", "EGP"
        // Add your custom codes here as needed
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        
        // Allow only 3-letter uppercase codes
        return value.length() == 3 && 
               value.matches("[A-Z]{3}") && 
               COMMON_CURRENCY_CODES.contains(value);
    }
}
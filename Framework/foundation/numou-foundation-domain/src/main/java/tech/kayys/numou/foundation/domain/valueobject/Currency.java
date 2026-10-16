package tech.kayys.numou.foundation.domain.valueobject;

import java.util.Locale;
import java.util.Objects;

/**
 * ISO-4217 style currency code.
 */
public record Currency(String code)
        implements ValueObject {

    public Currency {
        Objects.requireNonNull(
                code,
                "Currency code cannot be null"
        );

        code = code.trim().toUpperCase(Locale.ROOT);

        if (!code.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Currency code must contain exactly 3 letters"
            );
        }
    }

    public static Currency of(String code) {
        return new Currency(code);
    }

    public static Currency IDR() {
        return new Currency("IDR");
    }

    public static Currency USD() {
        return new Currency("USD");
    }

    public static Currency EUR() {
        return new Currency("EUR");
    }

    public String getCurrencyCode() {
        return code;
    }

}

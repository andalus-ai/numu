package tech.kayys.syirkah.commerce.promotion.domain.context;

import java.util.Objects;

/**
 * Opaque customer reference on the promotion evaluation boundary.
 * Deliberately not the CRM aggregate identity.
 */
public record CustomerId(String value) {

    public CustomerId {
        Objects.requireNonNull(value, "value cannot be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static CustomerId of(String value) {
        return new CustomerId(value);
    }
}

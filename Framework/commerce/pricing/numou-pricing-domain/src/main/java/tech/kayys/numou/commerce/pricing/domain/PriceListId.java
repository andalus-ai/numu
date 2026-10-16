package tech.kayys.syirkah.commerce.pricing.domain;

import java.util.Objects;

/**
 * Stable identity of a price list (e.g. RETAIL-2026, GROCERY-WHOLESALE).
 */
public record PriceListId(String value) {

    public PriceListId {
        Objects.requireNonNull(value, "priceListId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("priceListId cannot be blank");
        }
    }

    public static PriceListId of(String value) {
        return new PriceListId(value);
    }
}

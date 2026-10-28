package tech.kayys.numou.automotiveparts.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * An external or business identifier for a canonical Numou product.
 * Uniqueness is scoped by reference type and issuer/namespace in the persistence/application layer.
 */
public record PartReference(
        PartReferenceType type,
        String value,
        String issuer,
        String market
) {
    public PartReference {
        Objects.requireNonNull(type, "type is required");
        value = required(value, "value").toUpperCase(Locale.ROOT);
        issuer = normalizeOptional(issuer);
        market = normalizeOptional(market);
        if ((type == PartReferenceType.SUPPLIER_SKU || type == PartReferenceType.AFTERMARKET)
                && issuer == null) {
            throw new IllegalArgumentException(type + " reference requires an issuer/brand");
        }
    }

    public PartReference(PartReferenceType type, String value) {
        this(type, value, null, null);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
package tech.kayys.syirkah.product.domain.identifier;

import java.util.Objects;

/**
 * One external identification of a product (product02.md).
 *
 * {@code scope} tells the application/infrastructure layer where
 * uniqueness should be enforced (GLOBAL vs SUPPLIER, etc.).
 */
public record ProductIdentifier(
        ProductIdentifierType type,
        String value,
        ProductIdentifierScope scope
) {

    public ProductIdentifier {
        Objects.requireNonNull(type, "Identifier type cannot be null");
        Objects.requireNonNull(scope, "Identifier scope cannot be null");

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Identifier value cannot be blank"
            );
        }

        value = value.trim();
    }

    /** Defaults scope from type (GTIN/EAN/UPC → GLOBAL, else TENANT). */
    public ProductIdentifier(ProductIdentifierType type, String value) {
        this(type, value, defaultScope(type));
    }

    public static ProductIdentifierScope defaultScope(ProductIdentifierType type) {
        return switch (type) {
            case GTIN, EAN, UPC, ISBN -> ProductIdentifierScope.GLOBAL;
            case SUPPLIER_CODE -> ProductIdentifierScope.SUPPLIER;
            case MANUFACTURER_CODE -> ProductIdentifierScope.MANUFACTURER;
            default -> ProductIdentifierScope.TENANT;
        };
    }
}

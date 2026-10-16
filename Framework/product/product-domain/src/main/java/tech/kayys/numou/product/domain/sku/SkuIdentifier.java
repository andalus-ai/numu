package tech.kayys.syirkah.product.domain.sku;

/**
 * One external identification attached to a SKU (e.g. EAN
 * 8991234567890).
 */
public record SkuIdentifier(
        SkuIdentifierType type,
        String value
) {

    public SkuIdentifier {
        if (type == null) {
            throw new IllegalArgumentException(
                    "Identifier type cannot be null"
            );
        }

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Identifier value cannot be blank"
            );
        }

        value = value.trim();
    }
}
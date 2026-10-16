package tech.kayys.syirkah.product.domain.variant;

/**
 * One selected attribute of a variant (e.g. color=BLACK, size=M).
 */
public record VariantAttribute(
        String code,
        String value
) {

    public VariantAttribute {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "Variant attribute code cannot be blank"
            );
        }

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Variant attribute value cannot be blank"
            );
        }

        code = code.trim();
        value = value.trim();
    }
}
package tech.kayys.syirkah.product.domain.specification;

import java.util.Objects;

/**
 * One objective, typed quality requirement of a product (e.g.
 * sugar=ENUM, ram=NUMBER, weight=MEASUREMENT).
 */
public record AttributeDefinition(
        String code,
        String name,
        AttributeType type,
        boolean required
) {

    public AttributeDefinition {
        Objects.requireNonNull(type, "type cannot be null");

        code = requireText(code, "Attribute code");
        name = requireText(name, "Attribute name");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        return value.trim();
    }
}
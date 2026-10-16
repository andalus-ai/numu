package tech.kayys.syirkah.product.domain.specification;

import java.util.Objects;

/**
 * Identifier for an option within a group (product02.md).
 * Code-backed so existing option codes remain the identity key.
 */
public record OptionId(String value) {

    public OptionId {
        Objects.requireNonNull(value, "Option id cannot be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Option id cannot be blank");
        }
    }

    public static OptionId of(String code) {
        return new OptionId(code);
    }
}

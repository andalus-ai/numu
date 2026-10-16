package tech.kayys.syirkah.product.domain.specification;

import java.util.Objects;

/**
 * Identifier for an option group (product02.md).
 * Code-backed so existing specification codes remain the identity key.
 */
public record OptionGroupId(String value) {

    public OptionGroupId {
        Objects.requireNonNull(value, "Option group id cannot be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Option group id cannot be blank");
        }
    }

    public static OptionGroupId of(String code) {
        return new OptionGroupId(code);
    }
}

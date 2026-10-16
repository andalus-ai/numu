package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import java.util.Objects;

public record TargetType(String value) {
    public TargetType {
        Objects.requireNonNull(value, "value cannot be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static TargetType of(String value) {
        return new TargetType(value);
    }
}

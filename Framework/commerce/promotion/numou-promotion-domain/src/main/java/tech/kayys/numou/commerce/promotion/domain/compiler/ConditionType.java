package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import java.util.Objects;

public record ConditionType(String value) {
    public ConditionType {
        Objects.requireNonNull(value, "value cannot be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static ConditionType of(String value) {
        return new ConditionType(value);
    }
}

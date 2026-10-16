package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import java.util.Objects;

public record EffectType(String value) {
    public EffectType {
        Objects.requireNonNull(value, "value cannot be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static EffectType of(String value) {
        return new EffectType(value);
    }
}

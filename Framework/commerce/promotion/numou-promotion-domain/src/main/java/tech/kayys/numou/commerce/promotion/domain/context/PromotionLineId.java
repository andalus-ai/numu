package tech.kayys.syirkah.commerce.promotion.domain.context;

import java.util.Objects;

/** Identity of a line inside a promotion evaluation snapshot. */
public record PromotionLineId(String value) {

    public PromotionLineId {
        Objects.requireNonNull(value, "value cannot be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static PromotionLineId of(String value) {
        return new PromotionLineId(value);
    }
}

package tech.kayys.syirkah.commerce.promotion.domain;

import java.util.Objects;
import java.util.UUID;

public record PromotionRuleId(String value) {

    public PromotionRuleId {
        Objects.requireNonNull(value, "value cannot be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static PromotionRuleId generate() {
        return new PromotionRuleId(UUID.randomUUID().toString());
    }

    public static PromotionRuleId of(String value) {
        return new PromotionRuleId(value);
    }
}

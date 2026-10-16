package tech.kayys.syirkah.commerce.promotion.domain.benefit;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTarget;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

public record FixedPriceBenefit(
        PromotionId promotionId,
        PromotionTarget target,
        Money price,
        String description
) implements PromotionBenefit {

    public FixedPriceBenefit {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        if (price.isNegative()) {
            throw new IllegalArgumentException("price cannot be negative");
        }
        description = description.trim();
    }
}

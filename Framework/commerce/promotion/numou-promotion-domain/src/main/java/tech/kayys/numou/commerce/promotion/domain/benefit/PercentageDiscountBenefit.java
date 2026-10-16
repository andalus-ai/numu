package tech.kayys.syirkah.commerce.promotion.domain.benefit;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTarget;
import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;

import java.util.Objects;

public record PercentageDiscountBenefit(
        PromotionId promotionId,
        PromotionTarget target,
        Percentage percentage,
        String description
) implements PromotionBenefit {

    public PercentageDiscountBenefit {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(percentage, "percentage cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        if (percentage.value().signum() < 0
                || percentage.value().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("percentage must be 0..100");
        }
        description = description.trim();
    }
}

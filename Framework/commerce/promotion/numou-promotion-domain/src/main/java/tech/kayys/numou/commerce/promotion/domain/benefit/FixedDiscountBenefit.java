package tech.kayys.syirkah.commerce.promotion.domain.benefit;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTarget;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

public record FixedDiscountBenefit(
        PromotionId promotionId,
        PromotionTarget target,
        Money amount,
        String description
) implements PromotionBenefit {

    public FixedDiscountBenefit {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("amount must be positive");
        }
        description = description.trim();
    }
}

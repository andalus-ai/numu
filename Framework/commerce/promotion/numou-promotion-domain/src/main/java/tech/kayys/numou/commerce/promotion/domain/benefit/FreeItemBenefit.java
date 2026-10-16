package tech.kayys.syirkah.commerce.promotion.domain.benefit;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTarget;

import java.util.Objects;

public record FreeItemBenefit(
        PromotionId promotionId,
        PromotionTarget target,
        String skuCode,
        int quantity,
        String description
) implements PromotionBenefit {

    public FreeItemBenefit {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(skuCode, "skuCode cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        skuCode = skuCode.trim();
        description = description.trim();
    }
}

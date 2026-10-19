package tech.kayys.numou.commerce.promotion.domain.result;

import tech.kayys.numou.commerce.promotion.domain.PromotionId;
import tech.kayys.numou.commerce.promotion.domain.target.PromotionTarget;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.Objects;

/** One monetary adjustment produced by composition (product03.md). */
public record PromotionPriceAdjustment(
        PromotionId promotionId,
        PromotionTarget target,
        Money amount
) {

    public PromotionPriceAdjustment {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
    }
}

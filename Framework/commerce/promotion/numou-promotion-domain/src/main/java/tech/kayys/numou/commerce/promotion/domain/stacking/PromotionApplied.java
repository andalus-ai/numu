package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PromotionBenefit;

import java.util.List;
import java.util.Objects;

/** A promotion accepted by the stacking policy (product03.md). */
public record PromotionApplied(
        PromotionId promotionId,
        String promotionCode,
        int priority,
        List<PromotionBenefit> benefits
) {

    public PromotionApplied {
        Objects.requireNonNull(promotionId, "promotionId cannot be null");
        Objects.requireNonNull(promotionCode, "promotionCode cannot be null");
        Objects.requireNonNull(benefits, "benefits cannot be null");
        benefits = List.copyOf(benefits);
    }
}

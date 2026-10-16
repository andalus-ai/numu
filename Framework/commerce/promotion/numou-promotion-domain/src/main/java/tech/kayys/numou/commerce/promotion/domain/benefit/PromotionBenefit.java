package tech.kayys.syirkah.commerce.promotion.domain.benefit;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTarget;

/**
 * Mechanism-preserving benefit (product03.md).
 * Do not convert percentage to money until composition.
 */
public sealed interface PromotionBenefit
        permits PercentageDiscountBenefit,
        FixedDiscountBenefit,
        FixedPriceBenefit,
        FreeItemBenefit,
        ShippingDiscountBenefit {

    PromotionId promotionId();

    PromotionTarget target();

    String description();
}

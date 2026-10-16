package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.benefit.PromotionBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;

/**
 * Decides whether two benefits may coexist under STACK mode
 * (product03.md).
 */
public interface PromotionBenefitCompatibilityPolicy {

    boolean canCombine(
            PromotionBenefit existing,
            PromotionBenefit candidate,
            PromotionEvaluationContext context
    );
}

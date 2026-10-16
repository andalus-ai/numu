package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.benefit.FixedDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.FixedPriceBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.FreeItemBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PercentageDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PromotionBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.ShippingDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;
import tech.kayys.syirkah.commerce.promotion.domain.target.CartTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.LineTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.PromotionTarget;
import tech.kayys.syirkah.commerce.promotion.domain.target.ShippingTarget;

/**
 * Practical default benefit compatibility (product03.md §12):
 * fixed-price overrides are exclusive with other monetary benefits on
 * the same target; dual shipping discounts conflict; free-item and
 * cross-target benefits generally stack.
 */
public final class DefaultPromotionBenefitCompatibilityPolicy
        implements PromotionBenefitCompatibilityPolicy {

    @Override
    public boolean canCombine(
            PromotionBenefit existing,
            PromotionBenefit candidate,
            PromotionEvaluationContext context
    ) {
        if (existing instanceof FixedPriceBenefit
                && candidate instanceof FixedPriceBenefit
                && sameTarget(existing.target(), candidate.target())) {
            return false;
        }

        if ((existing instanceof FixedPriceBenefit || candidate instanceof FixedPriceBenefit)
                && isMonetaryDiscount(existing)
                && isMonetaryDiscount(candidate)
                && sameTarget(existing.target(), candidate.target())) {
            return false;
        }

        if ((existing instanceof FixedPriceBenefit && isMonetaryDiscount(candidate)
                || candidate instanceof FixedPriceBenefit && isMonetaryDiscount(existing))
                && sameTarget(existing.target(), candidate.target())) {
            return false;
        }

        if (existing instanceof ShippingDiscountBenefit
                && candidate instanceof ShippingDiscountBenefit) {
            return false;
        }

        if (existing instanceof FreeItemBenefit || candidate instanceof FreeItemBenefit) {
            return true;
        }

        return true;
    }

    private static boolean isMonetaryDiscount(PromotionBenefit benefit) {
        return benefit instanceof PercentageDiscountBenefit
                || benefit instanceof FixedDiscountBenefit
                || benefit instanceof ShippingDiscountBenefit
                || benefit instanceof FixedPriceBenefit;
    }

    private static boolean sameTarget(PromotionTarget left, PromotionTarget right) {
        if (left instanceof CartTarget && right instanceof CartTarget) {
            return true;
        }
        if (left instanceof ShippingTarget && right instanceof ShippingTarget) {
            return true;
        }
        if (left instanceof LineTarget l && right instanceof LineTarget r) {
            return l.lineKey().equals(r.lineKey());
        }
        return false;
    }
}

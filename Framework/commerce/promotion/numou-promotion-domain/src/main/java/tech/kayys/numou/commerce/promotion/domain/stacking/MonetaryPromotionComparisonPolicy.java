package tech.kayys.syirkah.commerce.promotion.domain.stacking;

import tech.kayys.syirkah.commerce.promotion.domain.benefit.FixedDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.FixedPriceBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.FreeItemBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PercentageDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.PromotionBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.benefit.ShippingDiscountBenefit;
import tech.kayys.syirkah.commerce.promotion.domain.composition.PromotionBenefitComposer;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.List;
import java.util.Objects;

/**
 * Compares evaluations by composed monetary outcome (product03.md).
 */
public final class MonetaryPromotionComparisonPolicy {

    private static final PromotionBenefitComposer COMPOSER =
            new PromotionBenefitComposer();

    private MonetaryPromotionComparisonPolicy() {
    }

    public static int compare(
            PromotionEvaluation left,
            PromotionEvaluation right,
            Money originalAmount
    ) {
        Objects.requireNonNull(left, "left cannot be null");
        Objects.requireNonNull(right, "right cannot be null");
        Objects.requireNonNull(originalAmount, "originalAmount cannot be null");

        Money leftDiscount = totalDiscount(left.benefits(), originalAmount);
        Money rightDiscount = totalDiscount(right.benefits(), originalAmount);
        int byDiscount = leftDiscount.compareTo(rightDiscount);
        if (byDiscount != 0) {
            return byDiscount;
        }
        if (left.priority() != right.priority()) {
            return Integer.compare(right.priority(), left.priority());
        }
        return right.promotionCode().compareTo(left.promotionCode());
    }

    public static Money totalDiscount(
            List<PromotionBenefit> benefits,
            Money originalAmount
    ) {
        var applied = new PromotionApplied(
                benefits.getFirst().promotionId(),
                "TMP",
                0,
                benefits);
        return COMPOSER.compose(originalAmount, List.of(applied)).totalDiscount();
    }

    /** Rough estimate without cart context — fixed amounts only. */
    public static Money roughFixedTotal(List<PromotionBenefit> benefits) {
        Money total = null;
        for (var benefit : benefits) {
            Money part = switch (benefit) {
                case FixedDiscountBenefit f -> f.amount();
                case ShippingDiscountBenefit s -> s.amount();
                case FixedPriceBenefit ignored -> null;
                case PercentageDiscountBenefit ignored -> null;
                case FreeItemBenefit ignored -> null;
            };
            if (part != null) {
                total = total == null ? part : total.add(part);
            }
        }
        return total;
    }
}

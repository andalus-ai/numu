package tech.kayys.syirkah.commerce.promotion.domain.action;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Percentage;

import java.util.Objects;

/**
 * "... GET 10% OFF" / "ANNUAL SaaS PLAN 20% OFF" — percentage off the
 * (already condition-filtered) cart total.
 */
public record PercentOff(
        Percentage percentage
) implements PromotionAction {

    public PercentOff {
        Objects.requireNonNull(percentage, "percentage cannot be null");
    }

    @Override
    public Money computeDiscount(PromotionContext cart) {
        return cart.total().multiply(percentage.factor());
    }
}

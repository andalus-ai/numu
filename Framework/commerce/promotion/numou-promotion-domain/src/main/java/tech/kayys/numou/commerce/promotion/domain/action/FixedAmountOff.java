package tech.kayys.syirkah.commerce.promotion.domain.action;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * Flat voucher-style discount ("Rp 10.000 OFF") on the cart total.
 */
public record FixedAmountOff(
        Money amount
) implements PromotionAction {

    public FixedAmountOff {
        Objects.requireNonNull(amount, "amount cannot be null");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("discount amount cannot be negative");
        }
    }

    @Override
    public Money computeDiscount(PromotionContext cart) {
        return amount;
    }
}

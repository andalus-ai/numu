package tech.kayys.syirkah.commerce.promotion.domain.action;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

/**
 * The "THEN" half of a promotion rule (blueprint Phase F).
 * Returns a positive discount amount; never negative, never more
 * than the cart total. Implementations must keep that contract.
 */
public interface PromotionAction {

    /**
     * Positive discount amount in the cart's currency (zero when the
     * action would have no effect).
     */
    default Money discountFor(PromotionContext cart) {
        Money raw = computeDiscount(cart);
        Money total = cart.total();
        if (raw.isNegative()) {
            return Money.zero(total.currency());
        }
        return raw.greaterThan(total) ? total : raw;
    }

    Money computeDiscount(PromotionContext cart);
}

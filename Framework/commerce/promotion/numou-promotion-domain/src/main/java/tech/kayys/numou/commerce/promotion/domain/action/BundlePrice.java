package tech.kayys.syirkah.commerce.promotion.domain.action;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * "COFFEE + CROISSANT = 40,000" — bundle deal: discount is the gap
 * between the cart total and the agreed bundle price. When the cart
 * is already cheaper than the bundle price the discount is zero
 * (clamped by {@link PromotionAction#discountFor}).
 */
public record BundlePrice(
        Money bundlePrice
) implements PromotionAction {

    public BundlePrice {
        Objects.requireNonNull(bundlePrice, "bundlePrice cannot be null");
        if (bundlePrice.isNegative()) {
            throw new IllegalArgumentException("bundlePrice cannot be negative");
        }
    }

    @Override
    public Money computeDiscount(PromotionContext cart) {
        return cart.total().subtract(bundlePrice);
    }
}

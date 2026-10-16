package tech.kayys.syirkah.commerce.promotion.domain;

import tech.kayys.syirkah.commerce.pricing.domain.PriceResult;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * Which promotion won and what it did to the cart price.
 *
 * The discounted price is itself a valid {@link PriceResult}: the
 * original cart price becomes the base and the discount enters as a
 * negative {@code PriceAdjustment}, so the pricing invariant
 * (final = base + Σ adjustments) still holds for the whole chain
 * product → pricing → promotion.
 */
public record PromotionOutcome(
        Promotion promotion,
        String ruleSummary,
        Money discount,
        PriceResult originalPrice,
        PriceResult discountedPrice
) {

    public PromotionOutcome {
        Objects.requireNonNull(promotion, "promotion cannot be null");
        Objects.requireNonNull(ruleSummary, "ruleSummary cannot be null");
        Objects.requireNonNull(discount, "discount cannot be null");
        Objects.requireNonNull(originalPrice, "originalPrice cannot be null");
        Objects.requireNonNull(discountedPrice, "discountedPrice cannot be null");
        if (!discount.isPositive()) {
            throw new IllegalArgumentException("discount must be positive");
        }
    }
}

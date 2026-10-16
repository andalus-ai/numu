package tech.kayys.syirkah.commerce.promotion.domain.action;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * "BUY 5 GET 1 FREE" — the free units are valued at the cheapest unit
 * price of the referenced lines (classic retail interpretation), so a
 * mixed-price basket never over-discounts.
 */
public record FreeUnits(
        String lineRef,
        long freeQuantity
) implements PromotionAction {

    public FreeUnits {
        Objects.requireNonNull(lineRef, "lineRef cannot be null");
        if (lineRef.isBlank()) {
            throw new IllegalArgumentException("lineRef cannot be blank");
        }
        if (freeQuantity <= 0) {
            throw new IllegalArgumentException("freeQuantity must be positive");
        }
    }

    @Override
    public Money computeDiscount(PromotionContext cart) {
        return cart.cheapestLine(lineRef)
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(freeQuantity)))
                .orElseGet(() -> Money.zero(cart.total().currency()));
    }
}

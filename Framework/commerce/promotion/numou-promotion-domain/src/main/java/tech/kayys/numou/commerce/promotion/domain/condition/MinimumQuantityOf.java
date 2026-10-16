package tech.kayys.syirkah.commerce.promotion.domain.condition;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;

import java.util.Objects;

/**
 * "BUY 2 COFFEE ..." — total quantity across all lines tagged with
 * {@code lineRef} must reach {@code minimum}.
 */
public record MinimumQuantityOf(
        String lineRef,
        long minimum
) implements PromotionCondition {

    public MinimumQuantityOf {
        Objects.requireNonNull(lineRef, "lineRef cannot be null");
        if (lineRef.isBlank()) {
            throw new IllegalArgumentException("lineRef cannot be blank");
        }
        if (minimum <= 0) {
            throw new IllegalArgumentException("minimum must be positive");
        }
    }

    @Override
    public boolean matches(PromotionContext cart) {
        return cart.quantityOf(lineRef) >= minimum;
    }
}

package tech.kayys.syirkah.commerce.promotion.domain.condition;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;

import java.util.List;
import java.util.Objects;

/**
 * "COFFEE + CROISSANT ..." — the cart must contain at least one line
 * for every required tag.
 */
public record AllLinesPresent(
        List<String> requiredRefs
) implements PromotionCondition {

    public AllLinesPresent {
        Objects.requireNonNull(requiredRefs, "requiredRefs cannot be null");
        if (requiredRefs.isEmpty()) {
            throw new IllegalArgumentException("requiredRefs cannot be empty");
        }
        requiredRefs = List.copyOf(requiredRefs);
    }

    @Override
    public boolean matches(PromotionContext cart) {
        return requiredRefs.stream().allMatch(ref -> cart.quantityOf(ref) > 0);
    }
}

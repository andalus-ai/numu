package tech.kayys.syirkah.commerce.promotion.domain.condition;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * "spend at least X" — threshold on the pre-discount cart total.
 */
public record MinimumCartTotal(
        Money minimum
) implements PromotionCondition {

    public MinimumCartTotal {
        Objects.requireNonNull(minimum, "minimum cannot be null");
    }

    @Override
    public boolean matches(PromotionContext cart) {
        return cart.total().greaterThanOrEqual(minimum);
    }
}

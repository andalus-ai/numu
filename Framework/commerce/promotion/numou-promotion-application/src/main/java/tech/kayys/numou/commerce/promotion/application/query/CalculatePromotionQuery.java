package tech.kayys.syirkah.commerce.promotion.application.query;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.foundation.application.query.Query;

import java.util.Objects;

/**
 * Evaluates promotions against a priced cart and returns the full
 * {@code PromotionResult} (applied + rejected + price breakdown).
 */
public record CalculatePromotionQuery(
        PromotionContext cart
) implements Query {

    public CalculatePromotionQuery {
        Objects.requireNonNull(cart, "cart cannot be null");
    }
}

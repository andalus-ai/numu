package tech.kayys.syirkah.commerce.promotion.application.query;

import tech.kayys.syirkah.commerce.promotion.domain.PromotionContext;
import tech.kayys.syirkah.foundation.application.query.Query;

import java.util.Objects;

/**
 * Read-intent message: apply the best promotion to a pre-priced cart.
 * Never changes state (redemptions/quota would be an order-side
 * concern, not a property of evaluation).
 */
public record ApplyPromotionsQuery(
        PromotionContext cart
) implements Query {

    public ApplyPromotionsQuery {
        Objects.requireNonNull(cart, "cart cannot be null");
    }
}

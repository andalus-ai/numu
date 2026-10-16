package tech.kayys.syirkah.commerce.pricing.application.query;

import tech.kayys.syirkah.commerce.pricing.domain.PricingContext;
import tech.kayys.syirkah.foundation.application.query.Query;

import java.util.Objects;

/**
 * Read-intent message: resolve the price for an offering +
 * configuration. Never changes state.
 */
public record ResolvePriceQuery(
        PricingContext context
) implements Query {

    public ResolvePriceQuery {
        Objects.requireNonNull(context, "context cannot be null");
    }
}

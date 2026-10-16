package tech.kayys.syirkah.groceries.application.api.query;

import tech.kayys.syirkah.foundation.application.query.Query;

public record GetExpiringProductsQuery(int daysThreshold) implements Query {
    public GetExpiringProductsQuery {
        if (daysThreshold <= 0) throw new IllegalArgumentException("Days threshold must be positive");
    }
}

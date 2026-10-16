package tech.kayys.numou.groceries.application.api.query;

import tech.kayys.numou.foundation.application.query.Query;

public record GetExpiringProductsQuery(int daysThreshold) implements Query {
    public GetExpiringProductsQuery {
        if (daysThreshold <= 0) throw new IllegalArgumentException("Days threshold must be positive");
    }
}

package tech.kayys.syirkah.commerce.subscription.application.query;

import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.foundation.application.query.Query;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Read-intent message: is this subscription allowed to use the
 * named entitlement on the given date?
 */
public record CheckEntitlementQuery(
        SubscriptionId subscriptionId,
        String code,
        LocalDate at
) implements Query {

    public CheckEntitlementQuery {
        Objects.requireNonNull(subscriptionId, "subscriptionId cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(at, "at cannot be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
    }
}

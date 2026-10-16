package tech.kayys.syirkah.commerce.subscription.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Pure-domain access check (blueprint §10):
 * {@code Subscription -> Entitlement -> Access}.
 *
 * Denies when the subscription is not in force on the date, when the
 * plan grants no such entitlement, or when a metered quota is already
 * exhausted. Unmetered entitlements pass whenever the subscription
 * is in force.
 */
public final class EntitlementAccess {

    public AccessDecision check(
            Subscription subscription,
            List<Entitlement> entitlements,
            String code,
            UsageMeter meter,
            LocalDate date
    ) {
        Objects.requireNonNull(subscription, "subscription cannot be null");
        Objects.requireNonNull(entitlements, "entitlements cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(meter, "meter cannot be null");
        Objects.requireNonNull(date, "date cannot be null");

        if (!subscription.isActiveOn(date)) {
            return AccessDecision.denied(AccessDecision.SUBSCRIPTION_NOT_IN_FORCE);
        }
        var entitlement = entitlements.stream()
                .filter(e -> e.code().equals(code))
                .findFirst();
        if (entitlement.isEmpty()) {
            return AccessDecision.denied(AccessDecision.ENTITLEMENT_NOT_FOUND);
        }
        var consumed = meter.consumedOf(code);
        var remaining = entitlement.get().remaining(consumed);
        if (remaining.isPresent() && remaining.get().signum() <= 0) {
            return AccessDecision.denied(AccessDecision.QUOTA_EXHAUSTED);
        }
        return AccessDecision.granted();
    }
}

package tech.kayys.syirkah.commerce.subscription.domain;

import java.util.Objects;

/**
 * Outcome of an entitlement check: a hard allow/deny plus a stable
 * machine-readable reason ("Subscription -&gt; Entitlement -&gt; Access",
 * blueprint §10).
 */
public record AccessDecision(
        boolean allowed,
        String reason
) {

    public static final String ALLOWED = "ALLOWED";
    public static final String SUBSCRIPTION_NOT_IN_FORCE = "SUBSCRIPTION_NOT_IN_FORCE";
    public static final String ENTITLEMENT_NOT_FOUND = "ENTITLEMENT_NOT_FOUND";
    public static final String QUOTA_EXHAUSTED = "QUOTA_EXHAUSTED";

    public AccessDecision {
        Objects.requireNonNull(reason, "reason cannot be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
    }

    public static AccessDecision granted() {
        return new AccessDecision(true, ALLOWED);
    }

    public static AccessDecision denied(String reason) {
        if (ALLOWED.equals(reason)) {
            throw new IllegalArgumentException(
                    "ALLOWED is not a denial reason");
        }
        return new AccessDecision(false, reason);
    }
}

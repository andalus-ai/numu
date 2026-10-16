package tech.kayys.syirkah.commerce.subscription.domain;

/**
 * Renewal policy (blueprint §9 "Renewal Policy").
 *
 * AUTO_RENEW lets the renewal job roll the period without a manual
 * step; MANUAL requires an explicit renew command. Both funnel
 * through {@link Subscription#renew} — the policy only says who
 * calls it.
 */
public enum RenewalPolicy {
    AUTO_RENEW,
    MANUAL
}

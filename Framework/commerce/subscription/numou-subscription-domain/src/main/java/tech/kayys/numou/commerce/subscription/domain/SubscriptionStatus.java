package tech.kayys.syirkah.commerce.subscription.domain;

/**
 * Lifecycle of a subscription (blueprint §9 "Status").
 *
 * Only ACTIVE and PAST_DUE ever grant entitlements — a cancelled or
 * expired subscription never unlocks access, and a lapsed period
 * denies it even while still ACTIVE.
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    CANCELLED,
    EXPIRED
}

package tech.kayys.numou.affiliate.domain;

/** Affiliate-side view of payout orchestration; provider execution is owned by Wallet/Payment. */
public enum PartnerPayoutStatus {
    REQUESTED,
    SUBMITTED,
    PAID,
    HELD,
    RETRYABLE_FAILURE,
    RECONCILIATION_REQUIRED,
    RETURNED
}

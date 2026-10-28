package tech.kayys.numou.affiliate.domain;

public enum CommissionFinancePostingStatus {
    PENDING,
    POSTED,
    RETRYABLE_FAILURE,
    RECONCILIATION_REQUIRED
}

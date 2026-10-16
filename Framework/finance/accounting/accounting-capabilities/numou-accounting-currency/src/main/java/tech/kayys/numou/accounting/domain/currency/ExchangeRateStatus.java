package tech.kayys.numou.accounting.domain.currency;

/**
 * Lifecycle status of a governed exchange rate record.
 */
public enum ExchangeRateStatus {
    DRAFT,
    APPROVED,
    ACTIVE,
    SUPERSEDED,
    RETIRED
}

package tech.kayys.numou.accounting.domain.islamic.syirkah;

/**
 * Versioned contract policy selection. MANUAL_REVIEW_REQUIRED intentionally
 * prevents automated allocation until an approved policy is implemented.
 */
public enum LossAllocationMode {
    CAPITAL_PROVIDER_ONLY,
    CAPITAL_PROPORTION,
    MANUAL_REVIEW_REQUIRED
}

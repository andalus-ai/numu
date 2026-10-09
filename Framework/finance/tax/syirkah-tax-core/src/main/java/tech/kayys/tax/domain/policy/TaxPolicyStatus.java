package tech.kayys.tax.domain.policy;

/**
 * Status of a tax policy in governance lifecycle.
 */
public enum TaxPolicyStatus {
    DRAFT,
    REVIEW,
    APPROVED,
    PUBLISHED,
    RETIRED
}

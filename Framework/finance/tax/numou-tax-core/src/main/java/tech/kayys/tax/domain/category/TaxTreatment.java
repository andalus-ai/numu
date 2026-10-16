package tech.kayys.tax.domain.category;

/**
 * Tax treatment classification for products, services, and transactions.
 */
public enum TaxTreatment {
    STANDARD_RATED,
    REDUCED_RATED,
    ZERO_RATED,
    EXEMPT,
    OUT_OF_SCOPE,
    REVERSE_CHARGE,
    WITHHOLDING
}

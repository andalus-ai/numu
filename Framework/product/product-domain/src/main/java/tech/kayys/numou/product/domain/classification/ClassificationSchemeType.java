package tech.kayys.syirkah.product.domain.classification;

/**
 * Which taxonomy a classification scheme represents.
 *
 * Kept deliberately small - a tenant-defined taxonomy is expressed
 * through the scheme's own code/name and node tree rather than by
 * growing this enum.
 */
public enum ClassificationSchemeType {

    RETAIL,
    ACCOUNTING,
    TAX,
    ECOMMERCE,
    REPORTING,
    CUSTOM
}

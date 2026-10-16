package tech.kayys.syirkah.product.domain.identifier;

/**
 * Where uniqueness for an identifier is enforced (product02.md).
 */
public enum ProductIdentifierScope {

    GLOBAL,
    TENANT,
    SUPPLIER,
    MANUFACTURER,
    CUSTOM
}

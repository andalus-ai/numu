package tech.kayys.syirkah.product.domain.identifier;

/**
 * Identification schemes a product can carry (product02.md).
 */
public enum ProductIdentifierType {

    INTERNAL_CODE,

    GTIN,
    EAN,
    UPC,

    ISBN,

    MANUFACTURER_CODE,
    SUPPLIER_CODE,

    LEGACY_CODE,

    EXTERNAL_CODE,

    CUSTOM
}

package tech.kayys.syirkah.product.domain.sku;

/**
 * Identification schemes a stock keeping unit can carry.
 *
 * SKU code is not necessarily the barcode - all of these can point
 * at the same SKU.
 */
public enum SkuIdentifierType {

    EAN,

    UPC,

    GTIN,

    ISBN,

    SUPPLIER_CODE,

    MANUFACTURER_CODE,

    LEGACY_CODE,

    CUSTOM
}
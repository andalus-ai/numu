package tech.kayys.syirkah.product.domain.sku;

/**
 * SKU lifecycle - deliberately independent from the product
 * lifecycle: Product can be ACTIVE while SKU B is already
 * DISCONTINUED.
 */
public enum SkuStatus {

    DRAFT,

    ACTIVE,

    DISCONTINUED,

    ARCHIVED
}
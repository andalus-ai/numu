package tech.kayys.syirkah.product.domain.identifier;

import tech.kayys.syirkah.product.domain.sku.SkuIdentifierType;

/**
 * Normalizes identifier values before persistence / uniqueness checks
 * (product02.md).
 */
public interface IdentifierNormalizer {

    String normalize(ProductIdentifierType type, String value);

    /** SKU variant (barcode rules are identical across the two type enums). */
    String normalize(SkuIdentifierType type, String value);
}

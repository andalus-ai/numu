package tech.kayys.syirkah.product.domain.identifier;

import tech.kayys.syirkah.product.domain.sku.SkuIdentifier;

/**
 * Validates identifier shape for a given type (product02.md).
 */
public interface IdentifierValidator {

    void validate(ProductIdentifier identifier);

    /** SKU variant (barcode rules are identical across the two type enums). */
    void validate(SkuIdentifier identifier);
}

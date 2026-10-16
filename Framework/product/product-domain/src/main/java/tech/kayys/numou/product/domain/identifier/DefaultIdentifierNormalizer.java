package tech.kayys.syirkah.product.domain.identifier;

import tech.kayys.syirkah.product.domain.sku.SkuIdentifierType;

/**
 * Default normalizer: trim; strip non-digits for GTIN/EAN/UPC/ISBN.
 */
public final class DefaultIdentifierNormalizer implements IdentifierNormalizer {

    @Override
    public String normalize(ProductIdentifierType type, String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return switch (type) {
            case GTIN, EAN, UPC, ISBN -> trimmed.replaceAll("\\D", "");
            default -> trimmed;
        };
    }

    /** SKU variant (barcode rules are identical across the two type enums). */
    public String normalize(SkuIdentifierType type, String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return switch (type) {
            case GTIN, EAN, UPC, ISBN -> trimmed.replaceAll("\\D", "");
            default -> trimmed;
        };
    }
}

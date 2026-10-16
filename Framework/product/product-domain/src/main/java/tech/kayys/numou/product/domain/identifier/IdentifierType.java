package tech.kayys.syirkah.product.domain.identifier;

/**
 * @deprecated Use {@link ProductIdentifierType}. Kept as a thin alias
 * so older call sites compile during consolidation.
 */
@Deprecated(forRemoval = true)
public enum IdentifierType {

    SKU,
    BARCODE,
    GTIN,
    EAN,
    UPC,
    MANUFACTURER_PART_NUMBER,
    SUPPLIER_CODE,
    EXTERNAL;

    public ProductIdentifierType toProductType() {
        return switch (this) {
            case SKU -> ProductIdentifierType.INTERNAL_CODE;
            case BARCODE, EAN -> ProductIdentifierType.EAN;
            case GTIN -> ProductIdentifierType.GTIN;
            case UPC -> ProductIdentifierType.UPC;
            case MANUFACTURER_PART_NUMBER -> ProductIdentifierType.MANUFACTURER_CODE;
            case SUPPLIER_CODE -> ProductIdentifierType.SUPPLIER_CODE;
            case EXTERNAL -> ProductIdentifierType.EXTERNAL_CODE;
        };
    }
}

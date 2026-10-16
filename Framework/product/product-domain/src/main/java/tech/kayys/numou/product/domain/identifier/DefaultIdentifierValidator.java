package tech.kayys.syirkah.product.domain.identifier;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifier;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifierType;

/**
 * Default structural validator for product identifiers (product02.md).
 */
public final class DefaultIdentifierValidator implements IdentifierValidator {

    @Override
    public void validate(ProductIdentifier identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier cannot be null");
        }
        requireShape(identifier.type(), identifier.value());
    }

    /** SKU variant (barcode rules are identical across the two type enums). */
    public void validate(SkuIdentifier identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier cannot be null");
        }
        requireShape(identifier.type(), identifier.value());
    }

    private static void requireShape(
            ProductIdentifierType type, String value) {
        switch (type) {
            case EAN -> requireDigits(value, 8, 13, "EAN");
            case UPC -> requireDigits(value, 12, 12, "UPC");
            case GTIN -> requireDigits(value, 8, 14, "GTIN");
            case ISBN -> requireDigits(value.replace("-", ""), 10, 13, "ISBN");
            default -> {
                if (value.isBlank()) {
                    throw new BusinessRuleViolation(
                            "Identifier value cannot be blank");
                }
            }
        }
    }

    private static void requireShape(
            SkuIdentifierType type, String value) {
        switch (type) {
            case EAN -> requireDigits(value, 8, 13, "EAN");
            case UPC -> requireDigits(value, 12, 12, "UPC");
            case GTIN -> requireDigits(value, 8, 14, "GTIN");
            case ISBN -> requireDigits(value.replace("-", ""), 10, 13, "ISBN");
            default -> {
                if (value.isBlank()) {
                    throw new BusinessRuleViolation(
                            "Identifier value cannot be blank");
                }
            }
        }
    }

    private static void requireDigits(
            String value, int min, int max, String label) {
        if (!value.matches("\\d{" + min + "," + max + "}")) {
            throw new BusinessRuleViolation(
                    label + " must be " + min + "-" + max + " digits");
        }
    }
}

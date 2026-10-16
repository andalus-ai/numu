package tech.kayys.syirkah.product.domain.specification;

import java.io.Serializable;
import java.util.Objects;

/**
 * Structured validation failure for an attribute value (config03.md §P4-18 #5.1).
 */
public record AttributeViolation(
        String attributeCode,
        String violationCode,
        String message
) implements Serializable {

    public AttributeViolation {
        Objects.requireNonNull(attributeCode, "attributeCode cannot be null");
        Objects.requireNonNull(violationCode, "violationCode cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
    }

    public static AttributeViolation typeMismatch(String attributeCode, String expectedType) {
        return new AttributeViolation(attributeCode, "TYPE_MISMATCH", "Expected value of type " + expectedType);
    }

    public static AttributeViolation required(String attributeCode) {
        return new AttributeViolation(attributeCode, "REQUIRED", "Attribute " + attributeCode + " is required");
    }
}

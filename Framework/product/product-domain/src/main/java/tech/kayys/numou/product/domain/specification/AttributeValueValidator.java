package tech.kayys.numou.product.domain.specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Schema-aware validator validating an {@link AttributeValue} against its {@link AttributeDefinition} (config03.md §P4-18 #5.1).
 */
public class AttributeValueValidator {

    public List<AttributeViolation> validate(AttributeDefinition definition, AttributeValue value) {
        List<AttributeViolation> violations = new ArrayList<>();
        if (definition == null) {
            return violations;
        }

        if (value == null) {
            if (definition.required()) {
                violations.add(AttributeViolation.required(definition.code()));
            }
            return violations;
        }

        // Validate type compatibility
        switch (definition.type()) {
            case TEXT -> {
                if (!(value instanceof AttributeValue.Text)) {
                    violations.add(AttributeViolation.typeMismatch(definition.code(), "TEXT"));
                }
            }
            case NUMBER -> {
                if (!(value instanceof AttributeValue.Decimal) && !(value instanceof AttributeValue.IntegerNumber)) {
                    violations.add(AttributeViolation.typeMismatch(definition.code(), "NUMBER"));
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof AttributeValue.Flag)) {
                    violations.add(AttributeViolation.typeMismatch(definition.code(), "BOOLEAN"));
                }
            }
            case DATE -> {
                if (!(value instanceof AttributeValue.DateValue)) {
                    violations.add(AttributeViolation.typeMismatch(definition.code(), "DATE"));
                }
            }
            case ENUM -> {
                if (!(value instanceof AttributeValue.Enumeration)) {
                    violations.add(AttributeViolation.typeMismatch(definition.code(), "ENUM"));
                }
            }
            default -> {
                // Other types: valid if present
            }
        }

        return violations;
    }
}

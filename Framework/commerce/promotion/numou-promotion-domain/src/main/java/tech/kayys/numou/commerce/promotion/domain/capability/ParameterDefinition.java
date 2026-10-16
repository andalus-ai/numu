package tech.kayys.syirkah.commerce.promotion.domain.capability;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One declared parameter of a capability (product03.md §64).
 *
 * <p>Deliberately not JSON-Schema compatible — enough to validate a tenant
 * definition and to drive a form before the compiler runs.</p>
 */
public record ParameterDefinition(
        String name,
        PromotionValueType type,
        boolean required,
        BigDecimal minimum,
        BigDecimal maximum
) {

    public ParameterDefinition {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        name = name.trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("parameter name cannot be blank");
        }
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("minimum must not exceed maximum");
        }
    }
}
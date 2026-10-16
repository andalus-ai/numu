package tech.kayys.syirkah.commerce.promotion.domain.capability;

import java.util.List;
import java.util.Objects;

/**
 * Condition capabilities carry parameters alongside their compared
 * {@code value} (product03.md §62). Split from
 * {@link PromotionParameterSchema} because a condition's schema governs
 * <em>scoping</em> inputs (which channel, which group), not the comparison
 * value itself.
 */
public record PromotionConditionParameterSchema(List<ParameterDefinition> parameters) {

    public PromotionConditionParameterSchema {
        Objects.requireNonNull(parameters, "parameters cannot be null");
        parameters = List.copyOf(parameters);
    }

    public static PromotionConditionParameterSchema of(ParameterDefinition... parameters) {
        return new PromotionConditionParameterSchema(List.of(parameters));
    }

    public static PromotionConditionParameterSchema none() {
        return new PromotionConditionParameterSchema(List.of());
    }

    public boolean isEmpty() {
        return parameters.isEmpty();
    }
}
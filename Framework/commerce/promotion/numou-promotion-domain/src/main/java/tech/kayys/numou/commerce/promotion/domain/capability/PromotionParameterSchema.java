package tech.kayys.syirkah.commerce.promotion.domain.capability;

import java.util.List;
import java.util.Objects;

/**
 * Declared parameters of an effect or target capability (product03.md §64).
 *
 * <p>The schema describes configuration that must validate <em>before</em>
 * compilation; it is the single source of truth for both validation and the
 * tenant-facing capability API (§71).</p>
 */
public record PromotionParameterSchema(List<ParameterDefinition> parameters) {

    public PromotionParameterSchema {
        Objects.requireNonNull(parameters, "parameters cannot be null");
        parameters = List.copyOf(parameters);
    }

    public static PromotionParameterSchema of(ParameterDefinition... parameters) {
        return new PromotionParameterSchema(List.of(parameters));
    }

    public static PromotionParameterSchema none() {
        return new PromotionParameterSchema(List.of());
    }

    public boolean isEmpty() {
        return parameters.isEmpty();
    }
}
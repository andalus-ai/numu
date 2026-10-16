package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import java.util.Map;
import java.util.Objects;

public record TargetDefinition(
        TargetType type,
        Map<String, Object> parameters
) {
    public TargetDefinition {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(parameters, "parameters cannot be null");
        parameters = Map.copyOf(parameters);
    }
}

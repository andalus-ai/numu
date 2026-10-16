package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Persistable / JSON-friendly condition definition (product03.md). */
public record ConditionDefinition(
        ConditionType type,
        ConditionOperator operator,
        Object value,
        Optional<LogicalOperator> groupOperator,
        List<ConditionDefinition> children,
        Map<String, Object> parameters
) {
    public ConditionDefinition {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(operator, "operator cannot be null");
        Objects.requireNonNull(groupOperator, "groupOperator cannot be null");
        Objects.requireNonNull(children, "children cannot be null");
        Objects.requireNonNull(parameters, "parameters cannot be null");
        children = List.copyOf(children);
        parameters = Map.copyOf(parameters);
    }

    public static ConditionDefinition leaf(
            ConditionType type,
            ConditionOperator operator,
            Object value
    ) {
        return new ConditionDefinition(
                type, operator, value, Optional.empty(), List.of(), Map.of());
    }
}

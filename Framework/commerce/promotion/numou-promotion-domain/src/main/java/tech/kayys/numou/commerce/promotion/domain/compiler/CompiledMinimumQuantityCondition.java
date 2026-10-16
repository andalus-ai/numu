package tech.kayys.syirkah.commerce.promotion.domain.compiler;

import tech.kayys.syirkah.commerce.promotion.domain.context.PromotionEvaluationContext;

import java.math.BigDecimal;
import java.util.Objects;

public record CompiledMinimumQuantityCondition(
        ConditionOperator operator,
        BigDecimal quantity
) implements CompiledCondition {

    public CompiledMinimumQuantityCondition {
        Objects.requireNonNull(operator, "operator cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
    }

    @Override
    public boolean evaluate(PromotionEvaluationContext context) {
        BigDecimal actual = context.lines().stream()
                .map(line -> line.quantity().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int comparison = actual.compareTo(quantity);
        return switch (operator) {
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case GREATER_THAN -> comparison > 0;
            case EQUALS -> comparison == 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            default -> throw new IllegalStateException(
                    "Unsupported quantity operator: " + operator);
        };
    }
}
